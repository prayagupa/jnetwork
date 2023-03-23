
public class SocketPortConnectivity {

    private static final String HOST = "googleapis.com";

    public static boolean hostAvailabilityCheck() throws IOException {
        var serverSocket = new Socket(HOST, 443);
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
