# Java Network APIs

Complete reference for Java's networking stack from low-level sockets to high-level HTTP clients.

---

## Package Overview

| Package | Contents |
|---------|---------|
| `java.net` | `Socket`, `ServerSocket`, `URL`, `InetAddress`, `DatagramSocket`, `Proxy`, `NetworkInterface` |
| `java.nio` | `SocketChannel`, `ServerSocketChannel`, `Selector` — non-blocking I/O |
| `java.nio.channels` | `AsynchronousSocketChannel`, `AsynchronousServerSocketChannel` — async I/O |
| `java.net.http` | `HttpClient`, `HttpRequest`, `HttpResponse` — Java 11+ (HTTP/1.1 + HTTP/2) |
| `javax.net.ssl` | `SSLContext`, `SSLSocket`, `SSLEngine` — TLS layer |
| `java.net` | `NetworkInterface`, `InetAddress`, `Inet4Address`, `Inet6Address` |

---

## InetAddress — DNS Resolution

```java
import java.net.InetAddress;

// Single lookup (uses OS resolver + JVM DNS cache)
InetAddress addr = InetAddress.getByName("api.example.com");
System.out.println(addr.getHostAddress());       // "52.1.2.3"
System.out.println(addr.getHostName());           // forward name
System.out.println(addr.getCanonicalHostName());  // PTR lookup (reverse DNS)
System.out.println(addr.isReachable(2000));       // ICMP or TCP port 7

// All addresses (round-robin DNS / multiple A records)
InetAddress[] all = InetAddress.getAllByName("googleapis.com"); // from NsLookup.java

// Loopback / any
InetAddress loopback = InetAddress.getLoopbackAddress(); // 127.0.0.1
InetAddress any      = InetAddress.getByName("0.0.0.0"); // all interfaces

// Reverse lookup (IP → hostname)
InetAddress byIp = InetAddress.getByAddress(new byte[]{8, 8, 8, 8});
System.out.println(byIp.getHostName()); // dns.google
```

**JVM DNS cache control:**
```java
// Positive cache (successful lookups) — default: -1 (forever) for security
java.security.Security.setProperty("networkaddress.cache.ttl", "30");

// Negative cache (failed lookups) — default: 10s
java.security.Security.setProperty("networkaddress.cache.negative.ttl", "5");
```

---

## Socket — TCP Client

```java
import java.net.*;
import java.io.*;

// Basic connect (from Ping.java)
try (Socket socket = new Socket()) {
    socket.connect(new InetSocketAddress("api.example.com", 443), 3000); // connect timeout

    socket.setSoTimeout(5000);       // read timeout (SO_RCVTIMEO)
    socket.setTcpNoDelay(true);      // disable Nagle (important for request-response)
    socket.setKeepAlive(true);       // SO_KEEPALIVE
    socket.setReceiveBufferSize(64 * 1024);
    socket.setSendBufferSize(64 * 1024);

    OutputStream out = socket.getOutputStream();
    InputStream in   = socket.getInputStream();

    out.write("GET / HTTP/1.0\r\nHost: api.example.com\r\n\r\n".getBytes());
    out.flush();

    byte[] buf = in.readAllBytes();
    System.out.println(new String(buf));
} // auto-close
```

**Socket option reference:**
```java
socket.setTcpNoDelay(true);          // TCP_NODELAY — disable Nagle
socket.setKeepAlive(true);           // SO_KEEPALIVE
socket.setSoTimeout(millis);         // SO_RCVTIMEO — read timeout
socket.setSoLinger(true, 0);         // SO_LINGER=0 — RST on close (skip TIME_WAIT for servers)
socket.setReuseAddress(true);        // SO_REUSEADDR — bind to port in TIME_WAIT
socket.setReceiveBufferSize(n);      // SO_RCVBUF
socket.setSendBufferSize(n);         // SO_SNDBUF
socket.setOOBInline(true);           // SO_OOBINLINE — receive urgent data inline
socket.setTrafficClass(0x10);        // IP_TOS — DSCP/QoS marking
```

---

## ServerSocket — TCP Server

```java
import java.net.*;
import java.io.*;
import java.util.concurrent.*;

// Basic server
try (ServerSocket server = new ServerSocket()) {
    server.setReuseAddress(true);
    server.setReceiveBufferSize(256 * 1024);
    server.bind(new InetSocketAddress("0.0.0.0", 8080), 1024); // addr, backlog

    ExecutorService pool = Executors.newFixedThreadPool(100);

    while (!Thread.currentThread().isInterrupted()) {
        Socket client = server.accept(); // blocks until connection
        pool.submit(() -> handleClient(client));
    }
}

static void handleClient(Socket socket) {
    try (socket) {
        socket.setSoTimeout(30_000);
        // read/write...
    } catch (IOException e) {
        e.printStackTrace();
    }
}
```

**Backlog sizing:** `new ServerSocket(port, backlog)` — sets the OS accept queue depth.  
Kernel limits this to `net.core.somaxconn` (default 128; set 4096+ for production).

---

## Java NIO — Non-Blocking I/O

Java NIO allows a **single thread to manage thousands of connections** via `Selector`.  
This is the foundation of Netty, Vert.x, and all high-performance Java servers.

### NIO Concepts

```
Channel     — non-blocking socket I/O (SocketChannel, ServerSocketChannel)
Buffer      — ByteBuffer holds data for reading/writing
Selector    — monitors multiple channels for readiness events
SelectionKey — registered channel + interest ops (OP_READ, OP_WRITE, OP_CONNECT, OP_ACCEPT)
```

### NIO TCP Server (Selector Pattern)

```java
import java.nio.*;
import java.nio.channels.*;
import java.net.*;

Selector selector = Selector.open();

ServerSocketChannel serverChannel = ServerSocketChannel.open();
serverChannel.configureBlocking(false);
serverChannel.bind(new InetSocketAddress(8080));
serverChannel.register(selector, SelectionKey.OP_ACCEPT);

while (true) {
    selector.select(); // blocks until at least one channel is ready

    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
    while (keys.hasNext()) {
        SelectionKey key = keys.next();
        keys.remove();

        if (key.isAcceptable()) {
            ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
            SocketChannel client = ssc.accept();
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);

        } else if (key.isReadable()) {
            SocketChannel channel = (SocketChannel) key.channel();
            ByteBuffer buf = ByteBuffer.allocate(1024);
            int bytesRead = channel.read(buf);
            if (bytesRead == -1) {
                channel.close(); // peer closed connection
            } else {
                buf.flip();
                channel.write(buf); // echo back
            }
        }
    }
}
```

### Async I/O (NIO.2 — Java 7+)

```java
import java.nio.channels.*;

AsynchronousServerSocketChannel server =
    AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(8080));

server.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
    @Override
    public void completed(AsynchronousSocketChannel client, Void attachment) {
        server.accept(null, this); // accept next

        ByteBuffer buf = ByteBuffer.allocate(1024);
        client.read(buf, buf, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer bytes, ByteBuffer buf) {
                buf.flip();
                client.write(buf, null, null);
            }

            @Override public void failed(Throwable ex, ByteBuffer buf) {}
        });
    }

    @Override public void failed(Throwable ex, Void attachment) {}
});
```

---

## Java 11+ HttpClient

The modern replacement for `HttpURLConnection` and `Apache HttpClient` for simple use cases.

### Setup

```java
import java.net.http.*;
import java.net.URI;

HttpClient client = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_2)          // prefer HTTP/2, fallback to 1.1
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .executor(Executors.newFixedThreadPool(10))  // custom thread pool for async
    .proxy(ProxySelector.of(new InetSocketAddress("proxy.corp", 8080)))
    .sslContext(sslContext)
    .build();
```

### Synchronous GET

```java
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users"))
    .header("Authorization", "Bearer " + token)
    .header("Accept", "application/json")
    .timeout(Duration.ofSeconds(5))   // per-request timeout
    .GET()
    .build();

HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
int status = response.statusCode();
String body = response.body();
HttpHeaders headers = response.headers();
```

### Async POST with JSON body

```java
HttpRequest post = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/users"))
    .header("Content-Type", "application/json")
    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Alice\"}"))
    .build();

CompletableFuture<HttpResponse<String>> future =
    client.sendAsync(post, HttpResponse.BodyHandlers.ofString());

// Non-blocking pipeline
future
    .thenApply(HttpResponse::body)
    .thenAccept(System.out::println)
    .exceptionally(ex -> { ex.printStackTrace(); return null; });
```

### Parallel Requests

```java
List<URI> uris = List.of(
    URI.create("https://api.example.com/users/1"),
    URI.create("https://api.example.com/users/2"),
    URI.create("https://api.example.com/users/3")
);

List<CompletableFuture<String>> futures = uris.stream()
    .map(uri -> HttpRequest.newBuilder().uri(uri).GET().build())
    .map(req -> client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                      .thenApply(HttpResponse::body))
    .toList();

List<String> results = futures.stream()
    .map(CompletableFuture::join)
    .toList();
```

### Streaming Response Body

```java
HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create("https://api.example.com/events"))
    .build();

HttpResponse<Stream<String>> response =
    client.send(request, HttpResponse.BodyHandlers.ofLines());

response.body().forEach(System.out::println); // process line by line
```

---

## URL and URI

```java
import java.net.*;

// URI (structural representation)
URI uri = new URI("https", "api.example.com", "/users", "page=1", null);
System.out.println(uri.getScheme());   // https
System.out.println(uri.getHost());     // api.example.com
System.out.println(uri.getPath());     // /users
System.out.println(uri.getQuery());    // page=1

// URL (legacy — can open streams)
URL url = new URL("https://api.example.com/users");
URLConnection conn = url.openConnection();
conn.setConnectTimeout(5000);
conn.setReadTimeout(5000);
try (InputStream is = conn.getInputStream()) {
    System.out.println(new String(is.readAllBytes()));
}
```

**Prefer `HttpClient` over `URL.openConnection()`** — the latter uses `HttpURLConnection` which lacks HTTP/2, is hard to configure, and has known bugs with connection pooling.

---

## Proxy Support

```java
// System proxy (from JVM args or OS)
System.setProperty("java.net.useSystemProxies", "true");
// or: -Djava.net.useSystemProxies=true

// Explicit HTTP proxy via JVM system properties
System.setProperty("http.proxyHost", "proxy.corp.com");
System.setProperty("http.proxyPort", "8080");
System.setProperty("https.proxyHost", "proxy.corp.com");
System.setProperty("https.proxyPort", "8080");
System.setProperty("http.nonProxyHosts", "localhost|*.internal");

// Programmatic — from NetworkApp.java
ProxySelector.getDefault().select(new URI("http://www.example.com/"))
    .stream()
    .findFirst()
    .map(proxy -> (InetSocketAddress) proxy.address())
    .ifPresent(addr -> System.out.println(addr.getHostName() + ":" + addr.getPort()));

// Custom ProxySelector
HttpClient client = HttpClient.newBuilder()
    .proxy(ProxySelector.of(new InetSocketAddress("proxy.corp.com", 8080)))
    .build();

// SOCKS5 proxy
Proxy socks = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("socks.corp.com", 1080));
Socket socket = new Socket(socks);
socket.connect(new InetSocketAddress("api.example.com", 443));
```

---

## NetworkInterface — Host Introspection

```java
import java.net.*;

// Enumerate all network interfaces (from Network.java)
Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
while (interfaces.hasMoreElements()) {
    NetworkInterface ni = interfaces.nextElement();

    System.out.println("Name:        " + ni.getName());
    System.out.println("Display:     " + ni.getDisplayName());
    System.out.println("MTU:         " + ni.getMTU());
    System.out.println("Loopback:    " + ni.isLoopback());
    System.out.println("Up:          " + ni.isUp());
    System.out.println("Virtual:     " + ni.isVirtual());
    System.out.println("Multicast:   " + ni.supportsMulticast());

    byte[] mac = ni.getHardwareAddress();
    if (mac != null) {
        StringBuilder sb = new StringBuilder();
        for (byte b : mac) sb.append(String.format("%02X:", b));
        System.out.println("MAC:         " + sb.deleteCharAt(sb.length()-1));
    }

    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
        System.out.println("  Address:   " + ia.getAddress().getHostAddress());
        System.out.println("  Prefix:    " + ia.getNetworkPrefixLength()); // CIDR /N
        System.out.println("  Broadcast: " + ia.getBroadcast());
    }
}
```

### Get Outbound IP Address (from Network.java)

```java
// Uses UDP connect trick — doesn't actually send packets
// Just queries the OS routing table
try (DatagramSocket socket = new DatagramSocket()) {
    socket.connect(InetAddress.getByAddress(new byte[]{8, 8, 8, 8}), 0);
    System.out.println("Outbound IP: " + socket.getLocalAddress().getHostAddress());
}
```

---

## SSL / TLS Configuration

```java
import javax.net.ssl.*;

// Disable hostname verification (NEVER in production)
HttpsURLConnection.setDefaultHostnameVerifier((host, session) -> true); // ⚠️ DANGEROUS

// Custom SSLContext with specific protocol versions
SSLContext ctx = SSLContext.getInstance("TLSv1.3");
ctx.init(null, null, null); // null = use default key/trust managers

// Restrict to TLS 1.2+ and specific cipher suites
SSLParameters params = new SSLParameters();
params.setProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
params.setCipherSuites(new String[]{
    "TLS_AES_128_GCM_SHA256",
    "TLS_AES_256_GCM_SHA384",
    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
});

HttpClient client = HttpClient.newBuilder()
    .sslContext(ctx)
    .sslParameters(params)
    .build();

// List supported cipher suites
SSLSocket ssl = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
System.out.println(Arrays.toString(ssl.getSupportedCipherSuites()));
System.out.println(Arrays.toString(ssl.getEnabledCipherSuites()));

// Debug TLS handshake (also from NetworkApp.java)
System.setProperty("javax.net.debug", "ssl:handshake");
```

---

## Virtual Threads (Java 21+) — Impact on Network I/O

Project Loom virtual threads transform Java's I/O model:

```java
// Before Loom: each blocking call ties up a platform (OS) thread
// Platform thread pool of 200 → max 200 concurrent blocking I/O calls

// With Loom: blocking I/O unmounts virtual thread from platform thread
// 1 platform thread can serve thousands of virtual threads

try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 10_000; i++) {
        executor.submit(() -> {
            // Blocking socket I/O — virtual thread suspends, doesn't block platform thread
            try (Socket socket = new Socket("api.example.com", 443)) {
                socket.setSoTimeout(5000);
                // read/write...
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}

// HttpClient with virtual threads
HttpClient client = HttpClient.newBuilder()
    .executor(Executors.newVirtualThreadPerTaskExecutor())
    .build();
```

**Principal implication:** With Loom, traditional NIO-based non-blocking patterns become less necessary for throughput — blocking code with virtual threads achieves similar scalability with simpler code.
