# Network Debugging

## Linux Tools

### `ss` — Socket Statistics (preferred over `netstat`)

```bash
ss -s                          # summary: total, TCP/UDP/UNIX counts
ss -tan                        # TCP all, numeric (no DNS lookup)
ss -tlnp                       # TCP listening, numeric, with process
ss -o state established        # all established TCP connections
ss -tan | grep CLOSE_WAIT      # detect leaked connections
ss -tan | grep TIME_WAIT | wc -l  # count TIME_WAIT sockets
ss -tnp src :8080              # connections from port 8080
ss -tnp dst :443               # connections to port 443

# Per-connection TCP internals (RTT, congestion, window)
ss -ti dst api.example.com     # -i = info (CWND, RTT, retransmits)
```

`ss -ti` output:
```
ESTAB 0  0  10.0.1.5:43210  52.1.2.3:443  users:(("java",pid=1234,fd=12))
         cubic wscale:7,7 rto:220 rtt:18.5/2.3 ato:40
         mss:1448 pmtu:1500 rcvmss:1448 advmss:1448
         cwnd:10 bytes_sent:14480 retrans:0/0 segs_out:11 segs_in:8
```

### `netstat` (legacy, still common)

```bash
netstat -an                    # all sockets, numeric
netstat -tlnp                  # listening ports + process
netstat -rn                    # routing table (numeric)
netstat -s                     # protocol statistics (retransmits, errors)
netstat -i                     # interface statistics (errors, drops)
```

### `ip` — Modern Routing and Interface Tool

```bash
ip addr show                   # all interfaces and IPs
ip addr show eth0              # specific interface
ip route show                  # routing table
ip route get 8.8.8.8           # which route/interface will reach 8.8.8.8
ip link show                   # interface statistics
ip neigh show                  # ARP table
```

### `tcpdump` — Packet Capture

```bash
# Capture all traffic on eth0
tcpdump -i eth0

# Capture to file for Wireshark analysis
tcpdump -i eth0 -w capture.pcap

# Filter by host
tcpdump -i eth0 host api.example.com

# Filter by port
tcpdump -i eth0 port 443
tcpdump -i eth0 tcp port 8080

# Filter by direction
tcpdump -i eth0 src host 10.0.1.5
tcpdump -i eth0 dst port 5432

# Show contents (ASCII)
tcpdump -i eth0 -A port 8080

# Combined: capture HTTP on port 8080, full packets
tcpdump -i eth0 -s 0 -A 'tcp port 8080 and (((ip[2:2] - ((ip[0]&0xf)<<2)) - ((tcp[12]&0xf0)>>2)) != 0)'

# Capture DNS queries
tcpdump -i eth0 udp port 53

# Capture and show timing
tcpdump -i eth0 -tttt port 443
```

### `traceroute` / `tracepath`

```bash
traceroute api.example.com          # ICMP/UDP, shows each hop + RTT
traceroute -T -p 443 api.example.com  # TCP traceroute on port 443 (bypasses ICMP blocks)
tracepath api.example.com           # also discovers MTU per hop
mtr api.example.com                 # interactive, continuous traceroute + ping
```

Useful for:
- Finding where packet loss occurs in a path.
- Identifying asymmetric routing (outbound ≠ inbound path).
- Detecting ICMP rate limiting at intermediate hops.

### `nmap` — Port and Service Scanning

```bash
nmap api.example.com                  # scan common ports
nmap -p 443,80,8080 api.example.com   # specific ports
nmap -sV api.example.com              # version detection
nmap -p- api.example.com              # all 65535 ports
nmap --script ssl-cert api.example.com  # inspect TLS cert
```

> ⚠️ Get permission before running nmap against production or external hosts.

### `curl` — HTTP Debugging

```bash
# Verbose (show headers, TLS details)
curl -v https://api.example.com/health

# Show only response code
curl -o /dev/null -s -w "%{http_code}" https://api.example.com

# Timing breakdown
curl -o /dev/null -s -w "
  dns_resolution:  %{time_namelookup}s
  tcp_connect:     %{time_connect}s
  tls_handshake:   %{time_appconnect}s
  ttfb:            %{time_starttransfer}s
  total:           %{time_total}s\n" https://api.example.com

# Custom headers, POST, follow redirects
curl -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"key":"value"}' \
     -L -X POST https://api.example.com/resource

# Test via proxy
curl -x http://proxy:8080 https://api.example.com
```

### `telnet` / `nc` (netcat)

```bash
# Test TCP port connectivity (from README.md in this repo)
telnet api.example.com 443

# nc — more flexible
nc -zv api.example.com 443        # zero-I/O test
nc -zv api.example.com 8080-8090  # port range scan
nc -l 9999                         # listen on 9999
echo "hello" | nc api.example.com 9999  # send data

# UDP test
nc -u -zv api.example.com 53
```

### `dig` — DNS Debugging

```bash
dig api.example.com                    # A record
dig api.example.com AAAA               # IPv6
dig api.example.com MX                 # mail
dig +trace api.example.com             # full recursive resolution
dig @8.8.8.8 api.example.com           # use specific resolver
dig -x 52.1.2.3                        # reverse DNS (PTR)
dig api.example.com +short             # IP only
dig +nocmd +noall +answer api.example.com  # minimal output
```

---

## Java-Level Debugging

### JVM Network Properties

```bash
# TLS/SSL debug (from NetworkApp.java)
java -Djavax.net.debug=ssl           # all SSL debug
java -Djavax.net.debug=ssl:handshake # just handshake
java -Djavax.net.debug=ssl:record    # record layer
java -Djavax.net.debug=ssl:keymanager,trustmanager # cert selection

# DNS debug
java -Dsun.net.spi.nameservice.debug=true

# HTTP client debug (Java 11+)
java -Djdk.httpclient.HttpClient.log=all
java -Djdk.httpclient.HttpClient.log=requests,responses,frames

# Proxy debug
java -Djava.net.useSystemProxies=true
java -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080

# IPv4 / IPv6 preference
java -Djava.net.preferIPv4Stack=true    # force IPv4
java -Djava.net.preferIPv6Addresses=true
```

### Java Socket Diagnostics

```java
// Check socket state after connection error
Socket socket = new Socket();
try {
    socket.connect(new InetSocketAddress("api.example.com", 443), 3000);
} catch (SocketTimeoutException e) {
    // Connection timeout — host likely down or firewall blocking
    System.err.println("Connection timed out: " + e.getMessage());
} catch (ConnectException e) {
    // Connection refused — host up but port closed / process not listening
    System.err.println("Connection refused: " + e.getMessage());
} catch (UnknownHostException e) {
    // DNS resolution failed
    System.err.println("DNS lookup failed: " + e.getMessage());
} catch (NoRouteToHostException e) {
    // Routing failure — no route to host
    System.err.println("No route to host: " + e.getMessage());
} finally {
    System.out.println("isClosed:     " + socket.isClosed());
    System.out.println("isConnected:  " + socket.isConnected());
    System.out.println("isInputShutdown:  " + socket.isInputShutdown());
    System.out.println("isOutputShutdown: " + socket.isOutputShutdown());
}
```

### Java Exception Types → Root Causes

| Exception | Root Cause |
|-----------|-----------|
| `SocketTimeoutException` | `connect()` exceeded timeout — firewall silently dropping, or host down |
| `ConnectException: Connection refused` | Host up, but port closed or process not listening |
| `UnknownHostException` | DNS resolution failed — check `/etc/resolv.conf`, DNS server reachability |
| `NoRouteToHostException` | Routing failure — no path to destination subnet |
| `SocketException: Connection reset` | Remote peer sent RST — usually crash, restart, or kept-alive socket was dead |
| `SocketException: Broken pipe` | Write to closed connection — remote closed before we wrote |
| `SocketException: Too many open files` | File descriptor limit (`ulimit -n`) exceeded — leak or limit too low |
| `BindException: Address already in use` | Port conflict — another process on same port |
| `SSLHandshakeException` | TLS failure — cert mismatch, expired cert, cipher mismatch, hostname verification |
| `SSLPeerUnverifiedException` | Server cert not trusted — missing CA in truststore |

---

## Common Production Incidents

### 1. `CLOSE_WAIT` Accumulation

**Symptom:** `ss -tan | grep CLOSE_WAIT | wc -l` growing continuously.  
**Cause:** Application receives FIN from peer but never calls `socket.close()`.  
**Fix:** Ensure `try-with-resources` wraps every `Socket`, `InputStream`, `OutputStream`. Check connection pool eviction.

```java
// BAD: socket may not be closed on exception
Socket s = pool.borrow();
s.getOutputStream().write(data);
pool.release(s);

// GOOD: auto-close on exception
try (Socket s = pool.borrow()) {
    s.getOutputStream().write(data);
}
```

### 2. DNS Caching Staleness

**Symptom:** Service continues routing to old/dead IPs after DNS update.  
**Cause:** JVM indefinite positive DNS cache.  
**Fix:**
```java
java.security.Security.setProperty("networkaddress.cache.ttl", "30");
// or restart JVM, or call InetAddressCachePolicy reflectively (hacky)
```

### 3. Connection Pool Exhaustion

**Symptom:** Requests hang, eventually time out. Thread dumps show `pool.borrow()` waiting.  
**Cause:** Pool size too small, or connections not returned (leaked), or downstream slow causing back-pressure.  
**Fix:**
- Size pool per Little's Law: `pool_size = throughput × latency`
- Set borrow timeout to fail fast
- Use metrics: `pool.active`, `pool.pending`, `pool.idle`

### 4. Ephemeral Port Exhaustion

**Symptom:** `java.net.BindException: Cannot assign requested address`, or connection failures at high rate.  
**Cause:** High connection churn consuming all ephemeral ports (default range 32768–60999).  
**Fix:**
```bash
# Expand port range
sysctl -w net.ipv4.ip_local_port_range="1024 65535"
# Enable reuse of TIME_WAIT ports
sysctl -w net.ipv4.tcp_tw_reuse=1
# Connection pool to avoid per-request TCP connections
```

### 5. Half-Open Connections (No Keep-Alive Probes)

**Symptom:** Connections appear `ESTABLISHED` on both sides but traffic never flows; requests eventually time out.  
**Cause:** NAT/firewall silently drops idle connections after timeout (e.g., AWS security groups: 350–900s).  
**Fix:**
```java
socket.setKeepAlive(true);         // SO_KEEPALIVE (OS probes after 2h by default)
socket.setSoTimeout(30_000);       // application-level read timeout
// Better: use application-level heartbeats (HTTP keep-alive, gRPC ping frames)
```

### 6. TLS Certificate Expiry

**Symptom:** `SSLHandshakeException: PKIX path validation failed: timestamp check failed`.  
**Fix:**
- Automate renewal (ACME, Vault PKI, AWS ACM auto-renewal).
- Alert at cert_expiry - 30 days.
- In Java, reload `SSLContext` without restart using custom `X509TrustManager`.

```java
// Reload certs without restart
KeyStore ks = KeyStore.getInstance("JKS");
ks.load(new FileInputStream("keystore.jks"), password);
KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
kmf.init(ks, password);
SSLContext ctx = SSLContext.getInstance("TLS");
ctx.init(kmf.getKeyManagers(), null, null);
// Rebuild HttpClient with new ctx
```

---

## Performance Analysis

### Read TCP retransmits (kernel counters)

```bash
# Retransmit rate indicates congestion or loss
netstat -s | grep -i retransmit
# or
ss -s   # shows retransmits

# Per-connection RTT and retransmits
ss -i -t state established
```

### Measure RTT from Java

```java
long start = System.nanoTime();
try (Socket s = new Socket()) {
    s.connect(new InetSocketAddress("api.example.com", 443), 5000);
}
long rttMicros = (System.nanoTime() - start) / 1000;
System.out.println("TCP handshake RTT: " + rttMicros + " µs");
```

### Thread Dump for Network Issues

```bash
# Send SIGQUIT to Java process
kill -3 <pid>

# Or JDK tool
jstack <pid> | grep -A 5 "java.net\|sun.nio"

# Look for threads blocked in:
# java.net.SocketInputStream.read0   → waiting for data (SO_TIMEOUT not set)
# java.net.PlainSocketImpl.socketConnect → stuck in connect (no timeout)
# sun.nio.ch.EPollSelectorImpl.epollWait → NIO selector (normal, waiting for events)
```

### JVM Network Metrics (Micrometer / JMX)

```java
// Monitor HttpClient pool (Java 11+ HttpClient uses internal executor)
// For Apache HttpClient, expose pool stats via JMX:
MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
// Or use Micrometer with HttpClient metrics binder

// Custom gauge for connection pool depth
Gauge.builder("http.pool.active", cm, PoolingHttpClientConnectionManager::getTotalStats)
     .register(registry);
```
