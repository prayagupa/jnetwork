package com.prayagupa;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class Ping {

        private static final int TIMEOUT = 500;

        public static boolean ping(String host, int port) {
                boolean reachable = false;
                try {
                        try(Socket socket = new Socket()) {
                                InetSocketAddress socketAddress = new InetSocketAddress(host, port);
                                socket.connect(socketAddress, TIMEOUT);
                                reachable = true;
                                System.out.println(host + ":" + port + " is reachable");
                        }
                } catch (IOException e) {
                        System.out.println(host + ":" + port + " is not reachable");
                        e.printStackTrace();
                }
                return reachable;
        }
}
