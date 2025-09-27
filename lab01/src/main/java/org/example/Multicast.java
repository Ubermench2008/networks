package org.example;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Multicast {
    private static final int PORT = 21212;
    private static final long TIMEOUT_MS = 5000;
    private static final long SEND_PERIOD_MS = 500;

    private static class Peer {
        final String ip;
        volatile long lastSeen;
        Peer(String ip, long ts) { this.ip = ip; this.lastSeen = ts; }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java -cp out org.example.Multicast <multicast-address>");
            System.exit(1);
        }

        String groupStr = args[0];
        String userListFileName = "userList.txt";

        String selfId;
        try {
            selfId = String.valueOf(generateEphemeralPort());
        } catch (IOException e) {
            selfId = String.valueOf(1024 + new Random().nextInt(65535 - 1024));
        }

        try {
            InetAddress group = InetAddress.getByName(groupStr);
            if (!group.isMulticastAddress()) {
                System.out.println("Not a multicast address: " + groupStr);
                System.exit(1);
            }
            System.out.println(group instanceof Inet6Address ? "Used IPv6" : "Used IPv4");

            NetworkInterface[] ifaces = selectNetworkInterfaces();
            if (ifaces.length == 0) {
                System.out.println("There are not enough network interfaces to choose from.");
                System.exit(1);
            }

            System.out.println("\nInterfaces list:");
            for (int i = 0; i < ifaces.length; i++) System.out.println(i + ". " + ifaces[i]);

            NetworkInterface iface = pickInterfaceForGroup(ifaces, group);
            System.err.println("\nSelected:\nInterface: " + iface);

            String selfIp = pickInterfaceAddress(iface, group instanceof Inet6Address).getHostAddress();

            MulticastSocket socket = new MulticastSocket(new InetSocketAddress(anyAddr(group), PORT));
            socket.setReuseAddress(true);
            socket.setNetworkInterface(iface);
            socket.setSoTimeout(1000);
            InetSocketAddress mcstAddress = new InetSocketAddress(group, PORT);
            socket.joinGroup(mcstAddress, iface);

            System.out.println("\nConnected to group " + groupStr + ":" + PORT);

            Map<String, Peer> peers = new HashMap<>();
            String lastSignature = "";

            String finalSelfId = selfId;
            Thread senderThread = new Thread(() -> {
                try {
                    while (true) {
                        String msg = "HELLO " + finalSelfId;
                        byte[] data = msg.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket p = new DatagramPacket(data, data.length, group, PORT);
                        socket.send(p);
                        Thread.sleep(SEND_PERIOD_MS);
                    }
                } catch (IOException | InterruptedException ignored) {}
            });
            senderThread.setDaemon(true);
            senderThread.start();

            byte[] buffer = new byte[1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            long lastTick = 0;

            while (true) {
                try {
                    socket.receive(packet);
                    String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8).trim();
                    String[] parts = msg.split("\\s+");
                    if (parts.length == 2 && "HELLO".equals(parts[0])) {
                        String peerId = parts[1];
                        if (!peerId.equals(selfId)) {
                            String ip = packet.getAddress().getHostAddress();
                            peers.put(peerId, new Peer(ip, System.currentTimeMillis()));
                        }
                    }
                } catch (SocketTimeoutException ignore) {

                }

                long now = System.currentTimeMillis();
                if (now - lastTick >= 500) {
                    peers.entrySet().removeIf(e -> now - e.getValue().lastSeen > TIMEOUT_MS);

                    Map<String, Integer> byIp = new TreeMap<>(ipComparator());
                    byIp.merge(selfIp, 1, Integer::sum);
                    for (Peer p : peers.values()) byIp.merge(p.ip, 1, Integer::sum);

                    String signature = buildSignature(byIp);
                    if (!signature.equals(lastSignature)) {
                        System.out.println("Current users: " + outList(byIp));
                        updateUserList(userListFileName, byIp.keySet());
                        lastSignature = signature;
                    }
                    lastTick = now;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int generateEphemeralPort() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static InetAddress anyAddr(InetAddress group) throws UnknownHostException {
        return (group instanceof Inet6Address)
                ? InetAddress.getByName("::")
                : InetAddress.getByName("0.0.0.0");
    }

    private static NetworkInterface[] selectNetworkInterfaces() {
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            List<NetworkInterface> list = new ArrayList<>();
            while (en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                if (ni.isUp() && ni.supportsMulticast()) list.add(ni);
            }
            return list.toArray(new NetworkInterface[0]);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }

    private static NetworkInterface pickInterfaceForGroup(NetworkInterface[] ifaces, InetAddress group) throws SocketException {
        boolean needV6 = group instanceof Inet6Address;
        for (NetworkInterface ni : ifaces) {
            for (Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements();) {
                InetAddress addr = a.nextElement();
                if (needV6 && addr instanceof Inet6Address) return ni;
                if (!needV6 && addr instanceof Inet4Address) return ni;
            }
        }
        return ifaces[0];
    }

    private static InetAddress pickInterfaceAddress(NetworkInterface ni, boolean v6) throws SocketException {
        InetAddress best = null;
        for (Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements();) {
            InetAddress addr = a.nextElement();
            if (v6 && addr instanceof Inet6Address) {
                if (best == null || ((Inet6Address) best).isLinkLocalAddress())
                    best = addr;
                if (!((Inet6Address) addr).isLinkLocalAddress()) return addr;
            } else if (!v6 && addr instanceof Inet4Address) {
                return addr;
            }
        }
        if (best != null) return best;
        throw new SocketException("No address on interface " + ni.getName());
    }

    private static String buildSignature(Map<String, Integer> byIp) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> e : byIp.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append(':').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    private static String outList(Map<String, Integer> byIp) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : byIp.entrySet()) {
            out.add(e.getValue() == 1 ? e.getKey() : e.getKey() + " x" + e.getValue());
        }
        return out.toString();
    }

    private static Comparator<String> ipComparator() {
        return (a, b) -> {
            boolean av6 = a.contains(":"), bv6 = b.contains(":");
            if (av6 != bv6) return av6 ? 1 : -1;
            return a.compareTo(b);
        };
    }

    private static void updateUserList(String fileName, Collection<String> ips) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(fileName))) {
            for (String ip : ips) { bw.write(ip); bw.newLine(); }
        } catch (IOException e) { e.printStackTrace(); }
    }
}
