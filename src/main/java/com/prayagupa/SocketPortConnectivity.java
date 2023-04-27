package com.prayagupa;

import java.net.Socket;
import java.io.IOException;

public class SocketPortConnectivity {

    /**
     * verifies the SERVER_HOST exists and port is available
     * but does not mean REACHABLE from this host machine
     */
    public static boolean isHostAvailable(String host, int port) throws IOException {
        var serverSocket = new Socket(host, port);
        boolean available = true;
        try {
            if (serverSocket.isConnected()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            available = false;
        }
        return available;
    }

}
