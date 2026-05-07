# Network Fundamentals

## OSI Model (7 Layers)

```
Layer 7  Application    HTTP, gRPC, DNS, SMTP, FTP
Layer 6  Presentation   TLS/SSL, compression, encoding
Layer 5  Session        TCP sessions, RPC sessions
Layer 4  Transport      TCP, UDP — port numbers, reliability
Layer 3  Network        IP, ICMP, routing — logical addressing
Layer 2  Data Link      Ethernet, ARP, MAC addressing
Layer 1  Physical       Cables, fiber, radio waves
```

**Principal-level insight:** Most distributed systems failures live at L3–L7.  
L4 (Transport) is the boundary where cloud load balancers split into "L4 LB" (TCP passthrough) vs "L7 LB" (HTTP-aware).  
The kernel TCP stack is L4; your application code sits at L5–L7.

---

## TCP/IP Stack (Internet Model)

```
Application Layer   → HTTP, gRPC, DNS
Transport Layer     → TCP (reliable), UDP (unreliable/fast)
Internet Layer      → IPv4 / IPv6, ICMP, routing
Link Layer          → Ethernet, Wi-Fi, loopback
```

---

## IP Addressing

### IPv4

- 32-bit address, dotted-decimal: `192.168.1.1`
- Special ranges:
  - `127.0.0.0/8` — loopback
  - `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` — RFC 1918 private
  - `169.254.0.0/16` — link-local (APIPA)
  - `0.0.0.0` — unspecified / "all interfaces" in bind

### IPv6

- 128-bit, colon-hex: `2001:db8::1`
- `::1` — loopback
- `fe80::/10` — link-local

### CIDR Notation

```
10.0.0.0/24  → 256 addresses, mask 255.255.255.0
10.0.0.0/16  → 65,536 addresses
10.0.0.0/8   → 16,777,216 addresses
```

**Quick formula:** `/N` gives `2^(32-N)` addresses.

---

## Subnetting

Used in VPCs (AWS/GCP/Azure) to partition network space:

```
VPC CIDR:        10.0.0.0/16    (65,536 IPs)
  Subnet A (us-east-1a): 10.0.1.0/24   (256 IPs)
  Subnet B (us-east-1b): 10.0.2.0/24   (256 IPs)
  Private subnet:         10.0.10.0/24  (no IGW route)
```

**Reserved per AWS subnet:** First 4 + last 1 addresses = 5 reserved (e.g., .0, .1, .2, .3, .255).

---

## ARP — Address Resolution Protocol

Resolves IP → MAC on the same L2 segment.

```
Host A wants to send to 192.168.1.5
→ ARP broadcast: "Who has 192.168.1.5?"
→ Host B replies: "I have it, my MAC is aa:bb:cc:dd:ee:ff"
→ Host A caches the mapping (ARP cache)
```

**ARP poisoning** is a classic MITM attack; mitigated by Dynamic ARP Inspection on managed switches.

### Java — Get MAC Address

```java
import java.net.NetworkInterface;

NetworkInterface ni = NetworkInterface.getByName("eth0");
byte[] mac = ni.getHardwareAddress();  // 6-byte MAC
// Example from Network.java in this repo — uses getByInetAddress()
```

---

## ICMP — Internet Control Message Protocol

Used for diagnostics and error reporting:

| Type | Code | Meaning |
|------|------|---------|
| 0    | 0    | Echo Reply (`ping` response) |
| 3    | 1    | Dest Unreachable — Host Unreachable |
| 3    | 3    | Dest Unreachable — Port Unreachable (UDP) |
| 8    | 0    | Echo Request (`ping`) |
| 11   | 0    | TTL Exceeded (`traceroute` mechanism) |

**`ping` uses ICMP Echo Request/Reply.**  
**`traceroute` sends packets with TTL=1,2,3,… and reads ICMP TTL Exceeded responses from each hop.**

### Java — InetAddress.isReachable

```java
InetAddress address = InetAddress.getByName("8.8.8.8");
// Tries ICMP echo; falls back to TCP port 7 if no permission
boolean reachable = address.isReachable(2000); // timeout ms
```

> ⚠️ `isReachable()` requires root/admin for raw ICMP on Linux; in containers it often falls back to TCP ping on port 7 which is usually closed — use `Socket.connect()` for reliable connectivity checks (see `Ping.java`).

---

## Routing

### Routing Table

```bash
# Linux
ip route show
```

```bash
# or legacy
route -n
netstat -rn
```

```
Destination     Gateway         Genmask         Flags  Iface
0.0.0.0         172.17.0.1      0.0.0.0         UG     eth0   ← default route
172.17.0.0      0.0.0.0         255.255.0.0     U      eth0   ← local subnet
```

**Longest prefix match:** The kernel picks the most specific (longest `/N`) matching route.  
`172.17.0.5` matches `172.17.0.0/16` before `0.0.0.0/0`.

### BGP — Border Gateway Protocol

- The routing protocol of the Internet (AS-to-AS).
- Cloud providers (AWS, GCP, Azure) use BGP for:
  - **Anycast** (CDN / DNS) — same IP announced from many PoPs, nearest wins.
  - **AWS Direct Connect / VPN** — BGP session between VPC and on-prem.
- Principal-level: Understand that BGP route propagation latency (minutes) is why DNS propagation takes time.

---

## NAT — Network Address Translation

```
Private: 10.0.1.5:54321 → NAT Gateway → Public: 52.1.2.3:54321
                                       ↓
                               External server sees 52.1.2.3
```

- **SNAT (Source NAT):** Changes source IP — used for outbound internet from private subnets.
- **DNAT (Destination NAT):** Changes dest IP — used in port forwarding / load balancers.
- **Connection tracking** maintained in kernel `conntrack` table.
- Ports are limited (ephemeral range typically `32768–60999`), causing **SNAT port exhaustion** under high connection rates.

**FAANG relevance:** Microservices hitting a NAT gateway can exhaust ephemeral ports → `EADDRNOTAVAIL` errors. Fix: connection pooling, or multiple NAT IPs, or IPv6.

---

## Key Kernel Tuning (Linux)

```bash
# Max socket receive buffer
sysctl net.core.rmem_max

# TCP SYN backlog (per socket listen queue)
sysctl net.ipv4.tcp_max_syn_backlog

# Ephemeral port range
sysctl net.ipv4.ip_local_port_range

# TIME_WAIT recycling
sysctl net.ipv4.tcp_tw_reuse

# Max open file descriptors (each socket is an fd)
ulimit -n
```

These are the knobs you tune when running high-throughput Java services.

### Java — Querying Network Interfaces

```java
// From Network.java in this repo
Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
while (interfaces.hasMoreElements()) {
    NetworkInterface ni = interfaces.nextElement();
    System.out.println(ni.getName() + " — " + ni.getDisplayName());
    Enumeration<InetAddress> addrs = ni.getInetAddresses();
    while (addrs.hasMoreElements()) {
        System.out.println("  " + addrs.nextElement());
    }
}
```
