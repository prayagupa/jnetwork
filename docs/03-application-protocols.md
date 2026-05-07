# Application Protocols

## DNS — Domain Name System

### Resolution Chain

```
Application
    │  gethostbyname("api.example.com")  [blocks calling thread!]
    ▼
OS Resolver (nsswitch.conf: files → dns)
    │  check /etc/hosts first
    ▼
Local DNS Cache / systemd-resolved / dnsmasq
    │  cache miss
    ▼
Recursive Resolver (from /etc/resolv.conf: nameserver 10.0.2.3)
    │  iterative queries
    ▼
Root NS → TLD NS (.com) → Authoritative NS for example.com
    │  answer with A/AAAA records
    ▼
Response cached at each layer per TTL
```

### Record Types

| Type | Purpose | Example |
|------|---------|---------|
| `A` | IPv4 address | `api.example.com → 52.1.2.3` |
| `AAAA` | IPv6 address | `api.example.com → 2001:db8::1` |
| `CNAME` | Alias to another name | `www → api.example.com` |
| `MX` | Mail server | priority + hostname |
| `TXT` | Arbitrary text | SPF, DKIM, verification |
| `NS` | Authoritative nameserver | |
| `SRV` | Service location | `_grpc._tcp.svc → host:port` (used by Kubernetes) |
| `PTR` | Reverse lookup (IP → name) | Used by `Ping.getCanonicalHostName()` |

### TTL Strategy

| TTL | Use case |
|-----|---------|
| 30–60s | Fast failover, blue/green deployments |
| 300s (5min) | Standard microservices |
| 3600s+ | CDN origins, rarely changed infra |

**Thundering herd on TTL expiry:** All pods/instances with the same DNS cache entry expire simultaneously → spike of resolver queries. Solution: randomize TTL jitter, use connection pooling.

### Java DNS

```java
// From NsLookup.java in this repo
InetAddress[] all = InetAddress.getAllByName("googleapis.com");
for (InetAddress addr : all) {
    System.out.println(addr.getHostName());          // DNS name
    System.out.println(addr.getHostAddress());        // IP string
    System.out.println(addr.getCanonicalHostName());  // PTR lookup
}
```

**⚠️ JVM DNS caching:** The JVM caches DNS results indefinitely by default for security (positive cache) and 10s for negative (NXDOMAIN).  
In containerized services with dynamic IPs, this causes stale routing:

```java
// In code or JVM args
java.security.Security.setProperty("networkaddress.cache.ttl", "30");
java.security.Security.setProperty("networkaddress.cache.negative.ttl", "5");
// Or JVM flag: -Dsun.net.inetaddr.ttl=30
```

### DNS Negative Caching Storm

A misconfigured service making DNS lookups for non-existent hostnames can saturate your resolver.  
Monitor with: `ss -u -a | grep :53` and resolver query rate metrics.

---

## HTTP/1.1

```
GET /api/users HTTP/1.1
Host: api.example.com
Connection: keep-alive
Accept: application/json

HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 42
```

### Key Properties

- **Persistent connections (keep-alive):** Default in HTTP/1.1 — reuses TCP connection for multiple requests.
- **Pipelining:** Client can send multiple requests without waiting for each response — but responses must arrive in order (**Head-of-Line blocking at HTTP layer**).
- **Text-based headers:** Verbose, no compression by default.

### Connection Pooling in Java (Apache HttpClient)

```java
// From Curl.java in this repo (basic version)
CloseableHttpClient client = HttpClients.createDefault();

// Production: configure pool
PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
cm.setMaxTotal(200);              // total pool size
cm.setDefaultMaxPerRoute(20);     // per-host limit

CloseableHttpClient pooledClient = HttpClients.custom()
    .setConnectionManager(cm)
    .setKeepAliveStrategy((response, context) -> 30_000) // 30s keep-alive
    .build();
```

---

## HTTP/2

### Key Improvements over HTTP/1.1

| Feature | HTTP/1.1 | HTTP/2 |
|---------|---------|--------|
| Transport | Text | Binary framing |
| Multiplexing | No (1 request/connection, or pipelining with HOL) | Yes — multiple streams per TCP connection |
| Header compression | None | HPACK (static + dynamic table) |
| Server push | No | Yes (preemptive resource push) |
| Stream priority | No | Yes |

### Multiplexing & HOL Blocking

```
HTTP/1.1 with pipelining:
TCP: [Req1][Req2][Req3] → must receive [Resp1][Resp2][Resp3] in order
     If Resp1 is large → Resp2, Resp3 are blocked (L7 HOL)

HTTP/2:
TCP: [Stream1-frame][Stream3-frame][Stream2-frame][Stream1-frame]...
     Interleaved — no L7 HOL blocking
     BUT: TCP packet loss → all streams stall (L4 HOL blocking remains)
```

**HTTP/3 (QUIC) solves L4 HOL blocking** — each stream is independent at the transport layer.

### Java 11+ HttpClient (HTTP/2 support)

```java
import java.net.http.*;
import java.net.URI;

HttpClient client = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_2)
    .connectTimeout(Duration.ofSeconds(10))
    .build();

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users"))
    .header("Accept", "application/json")
    .GET()
    .build();

// Synchronous
HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
System.out.println(response.statusCode());
System.out.println(response.body());

// Asynchronous
CompletableFuture<HttpResponse<String>> future =
    client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
future.thenAccept(r -> System.out.println(r.statusCode()));
```

---

## HTTP/3 and QUIC

### Key Properties

- Runs over **UDP** (not TCP).
- Implements its own **reliability, ordering, and congestion control** per stream.
- **0-RTT and 1-RTT connection setup** (vs TCP + TLS = 2–3 RTT).
- **No HOL blocking** — stream loss only blocks that stream.
- **Connection migration** — connection ID not tied to IP:port, survives mobile network switches.

```
HTTP/2 over TCP + TLS:       HTTP/3 over QUIC (UDP):
TCP SYN ─────────────        QUIC Initial (with TLS 1.3 ClientHello)
TCP SYN-ACK ─────────         ↓ (combined — 1 RTT to establish + TLS)
TCP ACK ─────────────
TLS ClientHello ──────
TLS ServerHello ──────
TLS Finished ─────────
HTTP Request ─────────        HTTP Request (0-RTT possible)
= 3 RTT minimum               = 1 RTT (or 0-RTT for resumption)
```

---

## WebSocket

Full-duplex persistent connection over a single TCP connection, initiated via HTTP Upgrade:

```
GET /ws HTTP/1.1
Host: example.com
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13

HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
```

**Use cases:** Real-time: chat, live dashboards, collaborative editing, stock tickers.

### Java WebSocket (JSR 356 / javax.websocket)

```java
import jakarta.websocket.*;

@ClientEndpoint
public class WSClient {
    @OnOpen
    public void onOpen(Session session) throws IOException {
        session.getBasicRemote().sendText("hello");
    }

    @OnMessage
    public void onMessage(String message) {
        System.out.println("Received: " + message);
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        System.out.println("Closed: " + reason);
    }
}

WebSocketContainer container = ContainerProvider.getWebSocketContainer();
Session session = container.connectToServer(WSClient.class,
    URI.create("ws://example.com/ws"));
```

---

## gRPC

- Built on **HTTP/2** — binary (Protocol Buffers), multiplexed, bidirectional streaming.
- **4 call types:** Unary, Server streaming, Client streaming, Bidirectional streaming.
- Strong typed contracts via `.proto` files.

```protobuf
service UserService {
  rpc GetUser (GetUserRequest) returns (User);                    // Unary
  rpc ListUsers (ListRequest) returns (stream User);              // Server stream
  rpc BatchCreateUsers (stream CreateUserRequest) returns (BatchResult); // Client stream
  rpc Chat (stream ChatMessage) returns (stream ChatMessage);     // Bidi stream
}
```

### gRPC vs REST trade-offs (Principal-level)

| Aspect | REST/HTTP | gRPC |
|--------|-----------|------|
| Payload | JSON (verbose) | Protobuf (3–10× smaller) |
| Contract | OpenAPI (optional) | `.proto` (required) |
| Browser support | Native | Requires grpc-web proxy |
| Streaming | Limited (SSE, chunked) | First-class |
| Interoperability | Universal | Ecosystem (Java, Go, Python…) |
| Observability | Easy (human-readable) | Needs tooling |

---

## TLS / SSL

### TLS 1.3 Handshake

```
Client                              Server
│──── ClientHello ──────────────────▶│
│     (supported ciphers, key_share)  │
│◀─── ServerHello ───────────────────│
│     (chosen cipher, key_share,      │
│      Certificate, CertVerify,       │
│      Finished)                      │
│──── Finished ──────────────────────▶│
═══════ ENCRYPTED DATA ══════════════
```

TLS 1.3: **1 RTT** (vs TLS 1.2 which required 2 RTT).  
**0-RTT resumption** possible with session tickets (but replay risk).

### Cipher Suites (TLS 1.3)

```
TLS_AES_128_GCM_SHA256      ← preferred, fast (AES-NI hardware)
TLS_AES_256_GCM_SHA384
TLS_CHACHA20_POLY1305_SHA256  ← preferred on mobile (no AES-NI)
```

### Certificate Chain

```
Root CA (self-signed, in OS/JVM trust store)
  └── Intermediate CA (cross-signed)
       └── Leaf Certificate (your service: api.example.com)
```

**Certificate pinning:** Client checks cert fingerprint/public key hash — defeats rogue CAs but makes rotation hard.

### Mutual TLS (mTLS)

Both client and server present certificates — used in **zero-trust service meshes**:

```
Client presents cert → Server validates against trusted CA
Server presents cert → Client validates
Both authenticated — no network-level trust needed
```

### Java SSL/TLS

```java
import javax.net.ssl.*;
import java.security.KeyStore;

// Custom TrustStore (non-default certs, self-signed)
KeyStore trustStore = KeyStore.getInstance("JKS");
trustStore.load(new FileInputStream("truststore.jks"), "changeit".toCharArray());

TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
tmf.init(trustStore);

// mTLS: KeyStore for client cert
KeyStore keyStore = KeyStore.getInstance("PKCS12");
keyStore.load(new FileInputStream("client.p12"), "password".toCharArray());
KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
kmf.init(keyStore, "password".toCharArray());

SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

// Use with HttpClient
HttpClient client = HttpClient.newBuilder()
    .sslContext(sslContext)
    .build();

// Debug TLS handshake
System.setProperty("javax.net.debug", "ssl:handshake");
// or JVM arg: -Djavax.net.debug=ssl:handshake
```

### Certificate Rotation (Operational)

- Automate with ACME (Let's Encrypt) or internal PKI (Vault, AWS ACM).
- Overlap validity windows — new cert deployed before old expires.
- Watch for: JVM caching old cert after rotation (reload `SSLContext` or restart).

---

## HTTP Status Codes — Principal-Level Nuance

| Code | Meaning | Retry-safe? |
|------|---------|-------------|
| 429 | Too Many Requests | Yes — back off |
| 499 | Client closed connection (nginx custom) | — |
| 502 | Bad Gateway (upstream failed) | Yes |
| 503 | Service Unavailable | Yes — back off |
| 504 | Gateway Timeout | Yes |
| 200 | OK | N/A |
| 201 | Created | No — idempotency needed |
| 204 | No Content (DELETE) | Yes if idempotent |

**Idempotency keys:** For non-idempotent 5xx retries, use a client-supplied idempotency key header.
