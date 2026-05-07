# Network Engineering — Principal Engineer Reference

> Deep-dive reference for network-level knowledge expected at Principal Engineer / Staff Engineer level in FAANG-tier companies.  
> All Java examples reference standard library (`java.net`, `java.nio`, Java 11+ `java.net.http`) unless an external library is noted.

---

## Contents

| Doc | Topics |
|-----|--------|
| [01 — Network Fundamentals](01-network-fundamentals.md) | OSI model, TCP/IP stack, IP addressing, CIDR, subnetting, ARP, ICMP, routing |
| [02 — TCP & UDP Internals](02-tcp-udp-internals.md) | 3-way handshake, TCP state machine, flow/congestion control, TIME_WAIT, Nagle, UDP multicast |
| [03 — Application Protocols](03-application-protocols.md) | DNS, HTTP/1.1, HTTP/2, HTTP/3 (QUIC), WebSocket, gRPC, TLS/mTLS |
| [04 — Java Network APIs](04-java-network-api.md) | `java.net`, `java.nio` (NIO), Java 11 `HttpClient`, async patterns, proxy, SSL context |
| [05 — Network Architecture](05-network-architecture.md) | Load balancing (L4/L7), service mesh, CDN, BGP anycast, NAT, VPN, SDN |
| [06 — Network Debugging](06-network-debugging.md) | `tcpdump`, `wireshark`, `ss`, `netstat`, `traceroute`, Java JVM flags, common failure modes |

---

## Why This Matters at Principal Level

At L6/L7 (FAANG Principal/Staff), you are expected to:

- **Design systems** that handle millions of connections — you must know how TCP backlog, socket options, and OS limits constrain throughput.
- **Debug production incidents** that manifest as network failures — `CLOSE_WAIT` accumulation, DNS NXDOMAIN storms, half-open connections.
- **Evaluate protocol trade-offs** — when to use gRPC vs REST vs WebSocket vs raw TCP; when HTTP/2 multiplexing hurts instead of helps.
- **Reason about security boundaries** — TLS cipher selection, certificate rotation, mTLS for zero-trust service meshes.
- **Capacity plan** — understand how TCP window size, RTT, and BDP affect throughput on high-latency links (e.g., cross-region replication).
