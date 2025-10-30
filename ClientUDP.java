import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Scanner;

public class ClientUDP {
    private static String toHex(byte[] arr, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02X", arr[i]));
            if (i < len - 1) sb.append(' ');
        }
        return sb.toString();
    }

    private static String opTable() {
        return
            "Operation table:\n" +
            "  0 -> subtraction (-)\n" +
            "  1 -> addition (+)\n" +
            "  2 -> and (&)\n" +
            "  3 -> or  (|)\n" +
            "  4 -> multiplication (*)\n" +
            "  5 -> division (/)\n";
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !args[0].equalsIgnoreCase("ClientUDP")) {
            System.out.println("Usage: java ClientUDP ClientUDP <serverName> <port>");
            return;
        }
        String host = args[1];
        int port = Integer.parseInt(args[2]);

        InetAddress addr = InetAddress.getByName(host);

        try (DatagramSocket socket = new DatagramSocket();
             Scanner sc = new Scanner(System.in)) {

            short reqId = 1;
            long minRttNs = Long.MAX_VALUE, maxRttNs = Long.MIN_VALUE, sumRttNs = 0;
            long count = 0;

            System.out.println("Connected to " + host + ":" + port);
            System.out.println(opTable());
            System.out.println("Enter q to quit at any prompt.\n");

            while (true) {
                System.out.print("OpCode (0..5): ");
                String s = sc.next();
                if (s.equalsIgnoreCase("q")) break;
                int opCode = Integer.parseInt(s);
                if (opCode < 0 || opCode > 5) {
                    System.out.println("Invalid opcode.");
                    continue;
                }

                System.out.print("Operand1 (int): ");
                s = sc.next();
                if (s.equalsIgnoreCase("q")) break;
                int op1 = Integer.parseInt(s);

                System.out.print("Operand2 (int): ");
                s = sc.next();
                if (s.equalsIgnoreCase("q")) break;
                int op2 = Integer.parseInt(s);

                Request req = Request.build((byte)opCode, op1, op2, reqId);
                byte[] out = req.toByteArray();

                // (iii) hex dump of request
                System.out.println("TX " + out.length + " bytes:");
                System.out.println(toHex(out, out.length));

                DatagramPacket pkt = new DatagramPacket(out, out.length, addr, port);

                long t0 = System.nanoTime();
                socket.send(pkt);

                byte[] buf = new byte[1024];
                DatagramPacket reply = new DatagramPacket(buf, buf.length);
                socket.receive(reply);
                long t1 = System.nanoTime();

                int rlen = reply.getLength();
                byte[] rdat = new byte[rlen];
                System.arraycopy(reply.getData(), reply.getOffset(), rdat, 0, rlen);

                // (v) hex dump response
                System.out.println("RX " + rlen + " bytes:");
                System.out.println(toHex(rdat, rlen));

                Response resp;
                try {
                    resp = Response.parse(rdat, rlen);
                } catch (IllegalArgumentException ex) {
                    System.out.println("Failed to parse response: " + ex.getMessage());
                    continue;
                }

                // (vi) human-readable
                String errText = (resp.errorCode == 0) ? "Ok" : ("Error " + (resp.errorCode & 0xFF));
                System.out.printf(Locale.US, "RequestID=%d  Result=%d  Error=%s%n",
                        Short.toUnsignedInt(resp.requestId), resp.result, errText);

                long rtt = t1 - t0;
                count++;
                minRttNs = Math.min(minRttNs, rtt);
                maxRttNs = Math.max(maxRttNs, rtt);
                sumRttNs += rtt;

                double rttMs = rtt / 1_000_000.0;
                System.out.printf(Locale.US, "RTT: %.3f ms%n%n", rttMs);

                reqId = (short)((reqId + 1) & 0xFFFF);
            }

            if (count > 0) {
                double minMs = minRttNs / 1_000_000.0;
                double maxMs = maxRttNs / 1_000_000.0;
                double avgMs = (sumRttNs / (double)count) / 1_000_000.0;
                System.out.printf(Locale.US, "RTT summary over %d requests: min=%.3f ms  avg=%.3f ms  max=%.3f ms%n",
                        count, minMs, avgMs, maxMs);
            }
            System.out.println("Bye.");
        }
    }
}
