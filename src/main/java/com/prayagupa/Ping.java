package com.prayagupa;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Ping {

        public static boolean ping(String url, int port) {
                boolean reachable = false;
                try {
                        try(Socket socket = new Socket()) {
                                InetSocketAddress socketAddress = new InetSocketAddress(url, port);
                                socket.connect(socketAddress, 1000);
                                reachable = true;
                                System.out.println(url + ":" + port + " is reachable");
                        }
                } catch (IOException e) {
                        e.printStackTrace();
                }
                return reachable;
        }
}
