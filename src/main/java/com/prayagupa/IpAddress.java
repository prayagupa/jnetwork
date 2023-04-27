package com.prayagupa;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class IpAddress {

    public static String getIpAddress() throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress("apple.com", 80));
        System.out.println("------------------");
        System.out.println("Host IP Address: " + socket.getLocalAddress());
        System.out.println("------------------");
        String hostAddress = socket.getLocalAddress().getHostAddress();
        socket.close();
        return hostAddress;
    }
}
