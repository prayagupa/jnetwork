
public class NsLookup {
    public static void nslookup() throws UnknownHostException {
        InetAddress[] allByName = InetAddress.getAllByName("googleapis.com");
        for (var a: allByName) {
            System.out.println("Canonical Host Name: " + new String(a.getCanonicalHostName()));
            byte[] bytes = a.getAddress();
            String ip = "";
            for (byte b : bytes) {
                ip = ip.isBlank() ? ip + (b & 0xFF) : ip + "." + (b & 0xFF);
            }
            System.out.println("Address: " + ip);
            System.out.println("");
            System.out.println("Non-authoritative answer:");
            System.out.println("Host Name: " + a.getHostName());
            System.out.println("Address: " + a.getHostAddress());
        }
    }
}

