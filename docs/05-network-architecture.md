# Network Architecture

## Load Balancing

### L4 vs L7 Load Balancing

| Aspect | L4 (Transport) | L7 (Application) |
|--------|---------------|-----------------|
| Operates at | TCP/UDP | HTTP/gRPC/WebSocket |
| Sees | IP + port | Headers, URL, cookies, body |
| TLS | Passthrough or terminate | Terminate (can inspect) |
| Sticky sessions | 4-tuple hash | Cookie/header-based |
| Health checks | TCP connect | HTTP endpoint check |
| Examples | AWS NLB, HAProxy TCP, IPVS | AWS ALB, NGINX, Envoy |
| Overhead | Very low | Higher (HTTP parsing) |

```
Client ──▶ L4 LB ──▶ Backend (TLS passthrough, TCP visible)
Client ──▶ L7 LB ──▶ Backend (TLS terminated, HTTP parsed, routing by path/header)
```

### Load Balancing Algorithms

| Algorithm | How it works | Best for |
|-----------|-------------|---------|
| Round-robin | Rotate through servers | Homogeneous requests |
| Least connections | Route to server with fewest active connections | Variable request duration |
| IP hash | `hash(client_ip) % server_count` | Sticky sessions without cookies |
| Consistent hashing | Ring-based, minimal reshuffling on change | Caches, session affinity |
| Weighted round-robin | Server weight proportional to capacity | Heterogeneous fleet |
| Random with 2 choices (P2C) | Pick min(2 random servers' connections) | Reduces hot spots vs round-robin |

### Consistent Hashing

Used in: distributed caches (Memcached, Redis Cluster), load balancers, DHTs.

```
Virtual ring: 0 ─────────────────────────────── 2^32
Servers placed at multiple points (virtual nodes):
   Server A: hash("A#1"), hash("A#2"), ...
   Server B: hash("B#1"), hash("B#2"), ...

Request key K → hash(K) → walk ring clockwise → first server

Adding/removing a server: only re-routes ~1/N of keys (vs 100% with modulo hash)
```

**Java consistent hashing (Google Guava):**
```java
import com.google.common.hash.*;

HashFunction hf = Hashing.murmur3_128();
int serverCount = 5;

// Assign key to server
String key = "user:12345";
int serverIndex = Hashing.consistentHash(hf.hashString(key, StandardCharsets.UTF_8), serverCount);

// With virtual nodes for better distribution (Guava doesn't do this natively — use own impl or jump hash)
int bucket = Hashing.consistentHash(hf.hashBytes(key.getBytes()), serverCount);
```

---

## Health Checks and Circuit Breakers

### Health Check Strategies

| Type | Implementation | Detects |
|------|---------------|---------|
| TCP connect | `Socket.connect()` to port | Port open/closed |
| HTTP health endpoint | `GET /health` → 200 | App alive, basic dependencies |
| Deep health | `GET /health/ready` → checks DB, cache | Full dependency health |
| gRPC health | `grpc.health.v1.Health/Check` | gRPC service health |

```java
// Health check from SocketPortConnectivity.java pattern
public static boolean tcpHealthCheck(String host, int port, int timeoutMs) {
    try (Socket s = new Socket()) {
        s.connect(new InetSocketAddress(host, port), timeoutMs);
        return true;
    } catch (IOException e) {
        return false;
    }
}
```

### Circuit Breaker States

```
CLOSED ──(failures >= threshold)──▶ OPEN ──(timeout elapsed)──▶ HALF-OPEN
  ▲                                                                    │
  └─────────────────(probe succeeds)──────────────────────────────────┘
                                         (probe fails)
                                              │
                                          OPEN (reset)
```

- **CLOSED:** Normal operation, failures counted.
- **OPEN:** All requests fail fast (no network calls) — avoids cascade.
- **HALF-OPEN:** Allow 1 probe request to test recovery.

Libraries: Resilience4j (preferred in Java), Netflix Hystrix (deprecated).

---

## Service Mesh

A service mesh adds networking concerns as infrastructure, not application code.

```
Microservice A ◀──▶ Sidecar Proxy (Envoy) ◀══ mTLS ══▶ Sidecar Proxy (Envoy) ◀──▶ Microservice B
                          │                                        │
                     Control Plane (Istio / Linkerd)
                     - mTLS cert rotation
                     - traffic policy (retries, timeouts, circuit breaker)
                     - observability (metrics, traces, logs)
                     - traffic splitting (canary, A/B test)
```

### Capabilities

| Feature | Without Mesh | With Mesh |
|---------|-------------|---------|
| mTLS | Manual per-service | Automatic, cert rotated |
| Retries | Per-service code | Config in mesh |
| Circuit breaking | Per-service library | Config in mesh |
| Traffic splitting | Code flag or separate LB | VirtualService/DestinationRule |
| Distributed tracing | Manual instrumentation | Automatic header propagation |
| Observability | Varies | Consistent (Prometheus/Grafana) |

### Key Components (Istio)

```yaml
# Route 10% of traffic to new version
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
spec:
  http:
  - route:
    - destination:
        host: my-service
        subset: v1
      weight: 90
    - destination:
        host: my-service
        subset: v2
      weight: 10
```

---

## CDN — Content Delivery Network

### How CDN Works

```
User (Tokyo) ──DNS──▶ CDN Anycast IP (same IP worldwide)
                              │ BGP routing → nearest PoP
                              ▼
                      CDN PoP (Tokyo)
                              │ cache hit? serve from PoP
                              │ cache miss? origin fetch
                              ▼
                      Origin (us-east-1)
```

### Anycast

Single IP advertised via BGP from many geographic locations. Clients are routed to the nearest one by BGP's shortest path.

Used by: CDNs (Cloudflare, Akamai), DNS (8.8.8.8 is anycast), DDoS scrubbing.

### Cache-Control Headers

```
Cache-Control: max-age=3600, public          # CDN + browser cache 1 hour
Cache-Control: max-age=0, must-revalidate    # Always revalidate
Cache-Control: no-store                       # Never cache (auth tokens, PII)
Cache-Control: s-maxage=86400                 # CDN-specific TTL (overrides max-age for shared caches)
Surrogate-Control: max-age=86400             # Varnish/CDN-specific
ETag: "abc123"                               # Conditional requests (304 Not Modified)
```

### Java HTTP Response with Cache Headers

```java
// Setting cache headers in a Java server response
HttpExchange exchange = ...; // com.sun.net.httpserver
exchange.getResponseHeaders().add("Cache-Control", "public, max-age=3600, s-maxage=86400");
exchange.getResponseHeaders().add("ETag", "\"" + contentHash + "\"");
exchange.getResponseHeaders().add("Vary", "Accept-Encoding");

// Conditional GET — check If-None-Match
String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
if (etag.equals(ifNoneMatch)) {
    exchange.sendResponseHeaders(304, -1); // Not Modified
    return;
}
```

---

## VPN and Tunneling

### Types

| Type | Use case |
|------|---------|
| IPsec | Site-to-site VPN (on-prem ↔ cloud), L3 tunnel |
| WireGuard | Modern, fast, kernel-level, replacing OpenVPN |
| OpenVPN | SSL/TLS-based, client VPN |
| AWS VPN | BGP over IPsec to Direct Connect or internet |
| SSH tunnel | `ssh -L local:remote` — quick ad-hoc tunnels |

### SSH Port Forwarding (Operational)

```bash
# Local forward: local:8080 → remote host's localhost:5432 (PostgreSQL)
ssh -L 8080:localhost:5432 -N user@bastion.host

# SOCKS proxy (dynamic forward)
ssh -D 1080 -N user@bastion.host
# Then: java -DsocksProxyHost=127.0.0.1 -DsocksProxyPort=1080 MyApp
```

---

## Software-Defined Networking (SDN)

```
Traditional:
  Physical switch  → Control plane (routing decisions) embedded in hardware

SDN:
  Data plane (packet forwarding) ← separated → Control plane (SDN controller)
  OpenFlow protocol carries forwarding rules from controller to switch
```

**FAANG relevance:** AWS VPC, GCP VPC, Azure VNET are all SDN — the "switch" is virtual, controlled by the cloud control plane. Understanding this explains:
- Why VPC peering doesn't require new hardware
- Why security group changes propagate in seconds
- Why `traceroute` inside a VPC shows only 1–2 hops regardless of path

### Overlay Networks (Container Networking)

```
Pod A (10.0.1.5) ──VXLAN/Geneve tunnel──▶ Pod B (10.0.2.8)
      │                                          │
  Node 1 (172.16.0.1)                      Node 2 (172.16.0.2)
      │                                          │
      └──────────── Underlay Network ───────────┘
                  (physical/VM network)
```

CNI plugins (Calico, Cilium, Flannel) implement this.

**Cilium uses eBPF** — bypasses iptables entirely for O(1) routing (vs O(N) iptables chain).

---

## Connection Pooling Patterns

Connection establishment is expensive (TCP handshake + TLS handshake = 2–4 RTT).  
Pools amortize this cost:

```java
// Apache HttpClient connection pool
PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
cm.setMaxTotal(500);             // max connections total
cm.setDefaultMaxPerRoute(50);    // max connections per host

// Keep connections alive based on server's Keep-Alive header
ConnectionKeepAliveStrategy keepAlive = (response, context) -> {
    HeaderElementIterator it = new BasicHeaderElementIterator(
        response.headerIterator(HTTP.CONN_KEEP_ALIVE));
    while (it.hasNext()) {
        HeaderElement he = it.nextElement();
        if ("timeout".equalsIgnoreCase(he.getName()) && he.getValue() != null) {
            return Long.parseLong(he.getValue()) * 1000;
        }
    }
    return 30_000; // 30 seconds default
};

CloseableHttpClient client = HttpClients.custom()
    .setConnectionManager(cm)
    .setKeepAliveStrategy(keepAlive)
    .evictExpiredConnections()
    .evictIdleConnections(60, TimeUnit.SECONDS)
    .build();
```

### Idle Connection Eviction

Idle connections silently die at: firewalls, NAT gateways, cloud security groups.  
The client sees `Connection reset` or `Broken pipe` when it tries to reuse a dead connection.

```java
// Evict idle connections proactively
cm.evictExpiredConnections();      // remove connections past their keep-alive
cm.evictIdleConnections(30, TimeUnit.SECONDS); // remove if idle > 30s

// Or use a background thread
ScheduledExecutorService eviction = Executors.newSingleThreadScheduledExecutor();
eviction.scheduleAtFixedRate(() -> {
    cm.evictExpiredConnections();
    cm.evictIdleConnections(30, TimeUnit.SECONDS);
}, 5, 5, TimeUnit.SECONDS);
```

---

## Bandwidth, Throughput, and Latency

### Little's Law

```
L = λ × W
L = avg number of requests in the system
λ = arrival rate (requests/sec)
W = avg time in system (latency)

Example: 1000 req/s, 50ms latency → 50 concurrent requests
→ Connection pool must be ≥ 50 to saturate throughput
```

### TCP Throughput Formula

```
Throughput ≈ RWND / RTT

Example: 65KB window, 100ms RTT → 65KB / 0.1s = 650 KB/s = ~5 Mbps
To saturate 1 Gbps: RWND = 1 Gbps × 100ms = 12.5 MB (needs large buffers)
```

### Bandwidth Delay Product (BDP)

```java
// Compute BDP to size buffers
long bandwidthBps = 1_000_000_000L; // 1 Gbps
double rttSeconds = 0.1;             // 100ms cross-region
long bdpBytes = (long) (bandwidthBps * rttSeconds / 8); // bytes
System.out.println("BDP: " + bdpBytes + " bytes (" + bdpBytes / 1024 / 1024 + " MB)");
// → BDP: 12,500,000 bytes (11 MB)
// → Set SO_RCVBUF and SO_SNDBUF >= 12MB for full throughput utilization
```
