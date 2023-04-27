package com.prayagupa;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

public class Network {

    /**
     * Because of using datagram (UDP), it isn't connecting anywhere,
     * so port number may be meaningless and
     * remote address (1.1.1.1) needn't be reachable, just routable.
     */
    private static final byte[] ROUTABLE_ADDRESS = {8, 8, 8, 8};
    private static final int PORT = 0;

    public static String gateway() throws SocketException, UnknownHostException {
        try(DatagramSocket socket=new DatagramSocket()) {
            socket.connect(InetAddress.getByAddress(ROUTABLE_ADDRESS), PORT);
            InetAddress localAddress = socket.getLocalAddress();
            System.out.println("Gateway: " + localAddress);
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(localAddress);
            System.out.println("Gateway interface: " + networkInterface);
            String gateway = new String(networkInterface.getHardwareAddress());
            System.out.println("Gateway: " + gateway);
            return gateway;
        }
    }

    /**
     * https://www.admfactory.com/how-to-get-network-interfaces-in-java/
     * Net interface: veth5afd9f5 - veth5afd9f5
     * IP address: /fe80:0:0:0:1445:6cff:fe66:e6be%veth5afd9f5
     *
     * Net interface: docker0 - docker0
     * IP address: /fe80:0:0:0:42:b2ff:fec1:7218%docker0
     * IP address: /172.17.0.1
     *
     * Net interface: br-1aef6168fa68 - br-1aef6168fa68
     * IP address: /192.168.80.1
     *
     * Net interface: br-e965599ad943 - br-e965599ad943
     * IP address: /172.18.0.1
     *
     * Net interface: br-4504ef4fd65b - br-4504ef4fd65b
     * IP address: /127.0.0.1
     *
     * Net interface: tap0 - tap0
     * IP address: /fe80:0:0:0:943b:a9ff:fe83:453b%tap0
     * IP address: /10.0.2.100
     *
     * Net interface: lo - lo
     * IP address: /0:0:0:0:0:0:0:1%lo
     * IP address: /127.0.0.1
     */
    public static void gatewayDetails() throws SocketException, UnknownHostException {
        try {
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();

            while (e.hasMoreElements()) {
                NetworkInterface ni = e.nextElement();
                System.out.println("Net interface: " + ni.getName() + " - " + ni.getDisplayName());

                Enumeration<InetAddress> e2 = ni.getInetAddresses();

                while (e2.hasMoreElements()) {
                    InetAddress ip = e2.nextElement();
                    System.out.println("IP address: " + ip.toString());
                }
                System.out.println();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
