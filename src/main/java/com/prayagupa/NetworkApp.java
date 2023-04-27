package com.prayagupa;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;

public class NetworkApp {

    /**
     * set ProxyHost and proxyPort
     */
    public static void setProxy() {
        System.getProperties().put("http.proxySet", "true");
        System.getProperties().put("http.proxyHost", "10.13.222.215");
        System.getProperties().put("http.proxyPort", "8080");
    }

    public static void main(String[] args) {
        // setProxy();
        System.setProperty("java.net.useSystemProxies", "true");
        try {
            Proxy proxy = (Proxy) ProxySelector.getDefault().select(new URI("http://www.yahoo.com/"))
                .iterator().next();
            // socketAddress is null if setProxy is not invoked
            InetSocketAddress iSAddress = (InetSocketAddress) proxy.address();
            if (null != iSAddress) {
                System.out.println("HTTP_PROXY      : " + iSAddress.getHostName());
                System.out.println("HTTP_PROXY_PORT : " + iSAddress.getPort());
            }
        } catch (Exception ee) {
            ee.printStackTrace();
        }

        //should not have protocal in hostname
        String hostToPing = "172.17.20.185";
        String hostToPing2 = "127.0.0.1";

        System.out.println("Reachable " + hostToPing + ": " + Ping.ping(hostToPing, 26379));
        System.out.println("Reachable " + hostToPing2 + ": " + Ping.ping(hostToPing2, 8080));
    }

}
