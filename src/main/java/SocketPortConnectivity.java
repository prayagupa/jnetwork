import java.net.Socket;
import java.io.IOException;

public class SocketPortConnectivity {

    private static final String HOST = "googleapis.com";
    private static final int PORT = 443;

    public static boolean isHostAvailable() throws IOException {
        var serverSocket = new Socket(HOST, PORT);
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
