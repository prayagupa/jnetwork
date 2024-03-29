jnetwork
======

Java API to mimick linux network tools to debug the network settings on host machine running java or other application.

Getting Started
----------------

```java
java -jar build/libs/jnetwork.jar

    Canonical Host Name: ord38s33-in-f4.1e100.net
    Address: 142.251.32.4
    -----------------
    Non-authoritative answer:
    Host Name: googleapis.com
    Address: 142.251.32.4
    172.17.201:26379 is not reachable
    java.net.SocketTimeoutException: connect timed out
    at java.base/java.net.PlainSocketImpl.socketConnect(Native Method)
    at java.base/java.net.AbstractPlainSocketImpl.doConnect(AbstractPlainSocketImpl.java:412)
    at java.base/java.net.AbstractPlainSocketImpl.connectToAddress(AbstractPlainSocketImpl.java:255)
    at java.base/java.net.AbstractPlainSocketImpl.connect(AbstractPlainSocketImpl.java:237)
    at java.base/java.net.SocksSocketImpl.connect(SocksSocketImpl.java:392)
    at java.base/java.net.Socket.connect(Socket.java:609)
    at com.prayagupa.Ping.ping(Ping.java:16)
    at com.prayagupa.NetworkApp.main(NetworkApp.java:58)
    Reachable 172.17.20.1: false
    apple.com:443 is reachable
    Reachable apple.com: true
    Net interface: eth0 - eth0
    IP address: /11.11.0.2

    Net interface: lo - lo
    IP address: /127.0.0.1

    Gateway: /11.11.0.2
    Gateway interface: name:eth0 (eth0)
    Gateway: B


    ------------------
    Host IP Address: /11.11.0.2
    ------------------
    curl https://apple.com
    javax.net.ssl|DEBUG|01|main|2023-04-27 23:18:12.763 UTC|SSLCipher.java:464|jdk.tls.keyLimits:  entry = AES/GCM/NoPadding KeyUpdate 2^37. AES/GCM/NOPADDING:KEYUPDATE = 137438953472
    log4j:WARN No appenders could be found for logger (org.apache.http.client.protocol.RequestAddCookies).
    log4j:WARN Please initialize the log4j system properly.
    javax.net.ssl|DEBUG|01|main|2023-04-27 23:18:13.967 UTC|SSLCipher.java:1866|KeyLimit read side: algorithm = AES/GCM/NOPADDING:KEYUPDATE
    countdown value = 137438953472
    javax.net.ssl|DEBUG|01|main|2023-04-27 23:18:13.971 UTC|SSLCipher.java:2020|KeyLimit write side: algorithm = AES/GCM/NOPADDING:KEYUPDATE
    countdown value = 137438953472
    javax.net.ssl|DEBUG|01|main|2023-04-27 23:18:14.242 UTC|SSLCipher.java:1866|KeyLimit read side: algorithm = AES/GCM/NOPADDING:KEYUPDATE
    countdown value = 137438953472
    javax.net.ssl|DEBUG|01|main|2023-04-27 23:18:14.246 UTC|SSLCipher.java:2020|KeyLimit write side: algorithm = AES/GCM/NOPADDING:KEYUPDATE
    countdown value = 137438953472
    javax.net.ssl|DEBUG|01|main|2023-04-27 23:18:14.379 UTC|SSLSocketImpl.java:578|duplex close of SSLSocket
    javax.net.ssl|DEBUG|01|main|2023-04-27 23:18:14.382 UTC|SSLSocketImpl.java:1755|close the SSL connection (passive)
    javax.net.ssl|DEBUG|01|main|2023-04-27 23:18:14.644 UTC|SSLCipher.java:1866|KeyLimit read side: algorithm = AES/GCM/NOPADDING:KEYUPDATE
    countdown value = 137438953472
    javax.net.ssl|DEBUG|01|main|2023-04-27 23:18:14.647 UTC|SSLCipher.java:2020|KeyLimit write side: algorithm = AES/GCM/NOPADDING:KEYUPDATE
    countdown value = 137438953472
    javax.net.ssl|DEBUG|01|main|2023-04-27 23:18:14.756 UTC|SSLCipher.java:1866|KeyLimit read side: algorithm = AES/GCM/NOPADDING:KEYUPDATE
    countdown value = 137438953472
    javax.net.ssl|DEBUG|01|main|2023-04-27 23:18:14.759 UTC|SSLCipher.java:2020|KeyLimit write side: algorithm = AES/GCM/NOPADDING:KEYUPDATE
    countdown value = 137438953472
    HTTP/1.1 200 OK

```

Routing table
---

```bash
## https://vitux.com/how-to-view-the-network-routing-table-in-ubuntu/
apt-get update
apt-get install net-tools

## on a default network
root@49c334e09fb3:/# netstat -rn
Kernel IP routing table
Destination     Gateway         Genmask         Flags   MSS Window  irtt Iface
0.0.0.0         172.17.0.1      0.0.0.0         UG        0 0          0 eth0
172.17.0.0      0.0.0.0         255.255.0.0     U         0 0          0 eth0

root@49c334e09fb3:/# route -n
Kernel IP routing table
Destination     Gateway         Genmask         Flags Metric Ref    Use Iface
0.0.0.0         172.17.0.1      0.0.0.0         UG    0      0        0 eth0
172.17.0.0      0.0.0.0         255.255.0.0     U     0      0        0 eth0


## on a custom bridge network
root@lima-docker-lima:/# netstat -rn
Kernel IP routing table
Destination     Gateway         Genmask         Flags   MSS Window  irtt Iface
0.0.0.0         10.0.2.2        0.0.0.0         UG        0 0          0 tap0
10.0.2.0        0.0.0.0         255.255.255.0   U         0 0          0 tap0
11.11.0.0       0.0.0.0         255.255.255.0   U         0 0          0 br-87bbbe792b34
127.0.0.0       0.0.0.0         255.255.0.0     U         0 0          0 br-4504ef4fd65b
172.17.0.0      0.0.0.0         255.255.0.0     U         0 0          0 docker0
172.18.0.0      0.0.0.0         255.255.0.0     U         0 0          0 br-e965599ad943
192.168.80.0    0.0.0.0         255.255.240.0   U         0 0          0 br-1aef6168fa68
```

```bash
root@lima-docker-lima:/# cat /etc/resolv.conf
nameserver 10.0.2.3
```

Nmap
--

- verifies the HOST exists and available ports
- does not mean host machine can reach out to the remote host

```bash
On container
$ apt-get update
$ apt-get install telnet

root@49c334e09fb3:/# telnet 172.17.21.196 26379
Trying 172.17.21.196...
telnet: Unable to connect to remote host: No route to host
```

```bash
$ nmap 443 mydns.com
Starting Nmap 7.92 ( https://nmap.org ) at 2023-04-26 17:37 PDT
Nmap scan report for mydns (54.224.?.?)
Host is up (0.080s latency).
Other addresses for mydns (not scanned): 18.235.?.?
rDNS record for 54.224.?.?: mydns
Not shown: 997 filtered tcp ports (no-response)
PORT    STATE SERVICE
53/tcp  open  domain
80/tcp  open  http
443/tcp open  https

Nmap done: 2 IP addresses (1 host up) scanned in 7.76 seconds
```

- https://learn.microsoft.com/en-us/windows-server/networking/technologies/ipam/ipam-top

Config
--

```bash
## on bridge network
root@lima-docker-lima:/# cat /etc/resolv.conf
nameserver 10.0.2.3


root@lima-docker-lima:/# traceroute -d 172.17.21.196
traceroute to 172.17.21.196 (172.17.21.196), 30 hops max, 60 byte packets
setsockopt SO_DEBUG: Permission denied
```