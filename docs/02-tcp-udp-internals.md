# TCP & UDP Internals

## TCP — Transmission Control Protocol

### Characteristics

| Property | Value |
|----------|-------|
| Connection-oriented | Yes — 3-way handshake |
| Reliable delivery | Yes — ACKs, retransmission |
| Ordered delivery | Yes — sequence numbers |
| Flow control | Yes — receive window |
| Congestion control | Yes — slow start, CWND |
| Full duplex | Yes |

---

## TCP Connection Lifecycle

### Three-Way Handshake (Connection Setup)

```
Client                          Server
  │──── SYN (seq=x) ──────────────▶│   SYN_SENT
  │◀─── SYN-ACK (seq=y, ack=x+1) ──│   SYN_RECEIVED
  │──── ACK (ack=y+1) ─────────────▶│
ESTABLISHED                     ESTABLISHED
```

**SYN flood attack:** Attacker sends many SYN packets without completing handshake → fills SYN backlog.  
Mitigation: **SYN cookies** (kernel generates stateless tokens, `net.ipv4.tcp_syncookies=1`).

### Four-Way Handshake (Connection Teardown)

```
Active Closer                   Passive Closer
  │──── FIN ────────────────────────▶│   FIN_WAIT_1
  │◀─── ACK ────────────────────────│   FIN_WAIT_2
  │◀─── FIN ────────────────────────│   CLOSE_WAIT
  │──── ACK ────────────────────────▶│
TIME_WAIT (2*MSL)               CLOSED
```

---

## TCP State Machine

```
                 ┌─────────────────────────────────────────────┐
                 ▼                                             │
CLOSED ──SYN──▶ SYN_SENT ──SYN-ACK──▶ ESTABLISHED            │
                                           │                   │
                                    FIN (active close)         │
                                           ▼                   │
                                       FIN_WAIT_1              │
                                           │ ACK               │
                                           ▼                   │
                                       FIN_WAIT_2              │
                                           │ FIN               │
                                           ▼                   │
                                       TIME_WAIT ──2MSL────────┘
```

### Critical States

| State | Meaning | Common Cause |
|-------|---------|--------------|
| `ESTABLISHED` | Active data transfer | Normal |
| `CLOSE_WAIT` | Remote closed, local hasn't called close() | **Bug: socket not closed in code** |
| `TIME_WAIT` | Waiting 2×MSL after active close | Normal after client initiates close |
| `SYN_RECEIVED` | Server received SYN, sent SYN-ACK | Normal / SYN flood |
| `FIN_WAIT_2` | Waiting for remote FIN | Remote is slow to close |

**`CLOSE_WAIT` accumulation** is a common FAANG production incident: the server receives FIN from the client but the Java application never calls `socket.close()` (e.g., leaked connection in a pool).

```bash
# Check socket states
ss -s          # summary
ss -tan | grep CLOSE_WAIT | wc -l
netstat -an | grep CLOSE_WAIT
```

---

## TIME_WAIT

- Lasts `2 × MSL` (Maximum Segment Lifetime) — typically **60–120 seconds** on Linux.
- Purpose: Ensures stray packets from old connection don't corrupt new connection with same 4-tuple.
- Under high connection churn (short-lived HTTP/1.0 connections), TIME_WAIT sockets can exhaust ephemeral ports.

**Mitigations:**
```bash
# Allow reuse of TIME_WAIT sockets for new outgoing connections
sysctl -w net.ipv4.tcp_tw_reuse=1

# Keep-alive persistent connections (HTTP/1.1 default) — avoids frequent teardown
# Use connection pooling in Java (Apache HttpClient, OkHttp)
```

---

## TCP Flow Control — Receive Window (rwnd)

The receiver advertises how much buffer it has:

```
Sender can have at most min(cwnd, rwnd) unacknowledged bytes in flight
```

- **rwnd** (receiver window): Advertised by receiver, limits sender to prevent buffer overflow.
- **Zero window**: Receiver's buffer is full → sender pauses → potential deadlock if ACK is lost.

### Java Socket Buffer Sizes

```java
Socket socket = new Socket();
socket.setReceiveBufferSize(256 * 1024);   // SO_RCVBUF
socket.setSendBufferSize(256 * 1024);       // SO_SNDBUF

ServerSocket server = new ServerSocket();
server.setReceiveBufferSize(256 * 1024);
```

---

## TCP Congestion Control

Controls sender rate to avoid overwhelming the network.

### Phases

```
1. Slow Start       CWND doubles each RTT (exponential) until ssthresh
2. Congestion Avoidance  CWND grows linearly (+1 MSS per RTT)
3. Fast Retransmit  3 duplicate ACKs → retransmit immediately (no timeout wait)
4. Fast Recovery    ssthresh = CWND/2, CWND = ssthresh (Reno) or CWND = ssthresh+3 (Reno)
```

### Algorithms

| Algorithm | Used By | Key Behavior |
|-----------|---------|--------------|
| Reno | Classic | Cuts CWND in half on loss |
| CUBIC | Linux default | Cubic function; better for high-BDP links |
| BBR (Google) | GCP, YouTube | Model-based; probes bandwidth/RTT |

**Bandwidth-Delay Product (BDP):**
```
BDP = bandwidth × RTT
# Example: 1 Gbps × 100ms = 12.5 MB in-flight to saturate link
# TCP window must be ≥ BDP; default kernel buffers often too small
```

---

## Nagle's Algorithm

Buffers small writes to coalesce into full-sized segments (reduces small-packet overhead).

- **Enabled by default.**
- Adds up to 200ms latency for interactive/request-response protocols.
- Disable for low-latency protocols (Redis, database drivers, gRPC):

```java
Socket socket = new Socket();
socket.setTcpNoDelay(true);   // Disable Nagle — TCP_NODELAY
```

---

## TCP Keep-Alive

Detects dead connections (peer crashed, NAT entry expired):

```java
Socket socket = new Socket();
socket.setKeepAlive(true);    // SO_KEEPALIVE
// OS controls interval (typically 2h by default — too long for most services)
```

For application-level keep-alive control (interval, retries), use Java NIO or Netty:
```java
// Netty example
bootstrap.childOption(ChannelOption.SO_KEEPALIVE, true);
// Fine-grained: TCP_KEEPIDLE, TCP_KEEPINTVL, TCP_KEEPCNT via EpollChannelOption
```

---

## Socket Options Summary (Java)

```java
Socket socket = new Socket();
socket.setTcpNoDelay(true);             // TCP_NODELAY — disable Nagle
socket.setKeepAlive(true);              // SO_KEEPALIVE
socket.setSoTimeout(5000);              // SO_RCVTIMEO — read timeout ms
socket.setSoLinger(true, 0);            // SO_LINGER — RST on close (skip TIME_WAIT)
socket.setReceiveBufferSize(64 * 1024); // SO_RCVBUF
socket.setSendBufferSize(64 * 1024);    // SO_SNDBUF
socket.setReuseAddress(true);           // SO_REUSEADDR — bind to TIME_WAIT port

ServerSocket server = new ServerSocket(8080, 128); // 128 = listen backlog
```

---

## TCP Listen Backlog

```
Client SYN ──▶ SYN queue (half-open, SYN_RECEIVED)
               ──▶ Accept queue (fully established, waiting for accept())
                   ──▶ Application calls accept()
```

- If accept queue is full → new connections are dropped (or SYN-ACK not sent).
- Default backlog is often 128 — insufficient for high-traffic servers.

```java
// Java server socket
new ServerSocket(8080, 1024);  // backlog=1024

// Kernel limits this to net.core.somaxconn
// sysctl -w net.core.somaxconn=4096
```

---

## UDP — User Datagram Protocol

### Characteristics

| Property | Value |
|----------|-------|
| Connection | Connectionless |
| Reliability | None — fire and forget |
| Ordering | Not guaranteed |
| Overhead | Minimal (8-byte header vs TCP 20+) |
| Use cases | DNS, DHCP, QUIC, game state, streaming, multicast |

### When to Use UDP

- **Latency > reliability:** Video streaming, gaming, VoIP — a lost frame is worse than a late frame.
- **Request-response at scale:** DNS — retransmit at application layer if needed.
- **Multicast / broadcast:** Cannot use TCP (point-to-point only).
- **QUIC (HTTP/3):** Implements its own reliability on top of UDP to avoid HOL blocking.

### Java UDP Socket

```java
import java.net.*;

// Sender
DatagramSocket sender = new DatagramSocket();
byte[] data = "hello".getBytes();
InetAddress dest = InetAddress.getByName("192.168.1.10");
DatagramPacket packet = new DatagramPacket(data, data.length, dest, 9999);
sender.send(packet);
sender.close();

// Receiver
DatagramSocket receiver = new DatagramSocket(9999);
byte[] buf = new byte[1024];
DatagramPacket incoming = new DatagramPacket(buf, buf.length);
receiver.receive(incoming);   // blocks
String message = new String(incoming.getData(), 0, incoming.getLength());
receiver.close();
```

### UDP Multicast

```java
MulticastSocket multicast = new MulticastSocket(5000);
InetAddress group = InetAddress.getByName("224.0.0.1"); // Class D: 224–239.x.x.x
multicast.joinGroup(group);

// Receive
DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
multicast.receive(packet);

multicast.leaveGroup(group);
multicast.close();
```

---

## RTT and Latency Reference

| Hop | Typical RTT |
|-----|-------------|
| Loopback (`127.0.0.1`) | < 0.1 ms |
| Same host, different process | < 0.1 ms |
| Same rack (datacenter) | 0.1–0.5 ms |
| Same datacenter, different rack | 0.5–2 ms |
| Same region, different AZ | 1–5 ms |
| Cross-region (US East ↔ West) | 50–80 ms |
| US ↔ Europe | 80–120 ms |
| US ↔ Asia | 150–200 ms |

**Principal implication:** A synchronous call chain with 5 serial hops at 2 ms each = 10 ms minimum latency _before any code runs_. Use async, batching, or co-location.

---

## Sequence Number Wrap-Around

TCP sequence numbers are 32-bit → wrap around at ~4 GB.  
At 10 Gbps, this happens in **~3 seconds** — handled by PAWS (Protection Against Wrapped Sequence numbers) using TCP timestamps.

This is why TCP timestamps (`net.ipv4.tcp_timestamps`) should remain enabled on high-speed links.
