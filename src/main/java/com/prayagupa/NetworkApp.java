package com.prayagupa;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;

public class NetworkApp {
    private static final String HOST = "googleapis.com";
    private static final int PORT = 443;

    /**
     * set ProxyHost and proxyPort
     */
    public static void setProxy() {
        System.getProperties().put("http.proxySet", "true");
        System.getProperties().put("http.proxyHost", "10.13.222.215");
        System.getProperties().put("http.proxyPort", "8080");
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("javax.net.debug", "ssl");

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
        String internalHostToPing1 = "172.17.21.196"; //
        String internalHostToPing2 = "apple.com";

        SocketPortConnectivity.isHostAvailable(HOST, PORT);

        /**
         * Canonical Host Name: sea30s08-in-f4.1e100.net
         * Address: 142.250.69.196
         *
         * Non-authoritative answer:
         * Host Name: googleapis.com
         * Address: 142.250.69.196
         * Canonical Host Name: sea30s10-in-x04.1e100.net
         * Address: 38.7.248.176.64.10.8.7.0.0.0.0.0.0.32.4
         */
        NsLookup.nslookup(HOST);

        System.out.println("Reachable " + internalHostToPing1 + ": " + Ping.ping(internalHostToPing1, 26379));
        System.out.println("Reachable " + internalHostToPing2 + ": " + Ping.ping(internalHostToPing2, 443));

        Network.gatewayDetails();
        Network.gateway();

        IpAddress.getIpAddress();

        Curl.curl("https://" + internalHostToPing2);
    }

}
