jnetwork
======

Getting Started
----------------

`javac com.prayagupa.Ping.java`


Change IP Address to com.prayagupa.Ping


Then `java com.prayagupa.Ping`


Nmap example: 
- verifies the HOST exists and available ports
- does not mean host machine can reach out to the remote host

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