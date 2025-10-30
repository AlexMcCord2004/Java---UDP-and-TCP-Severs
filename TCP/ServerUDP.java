import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class ServerUDP {
    private static String toHex(byte[] arr, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02X", arr[i]));
            if (i < len - 1) sb.append(' ');
        }
        return sb.toString();
    }

    private static int compute(byte opCode, int a, int b) {
        switch (opCode) {
            case 0: return a - b;           // subtraction
            case 1: return a + b;           // addition
            case 2: return a & b;           // bitwise AND
            case 3: return a | b;           // bitwise OR
            case 4: return a * b;           // multiplication
            case 5: return a / b;           // integer division (caller beware of /0)
            default: return 0;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[0].equalsIgnoreCase("ServerUDP")) {
            System.out.println("Usage: java ServerUDP ServerUDP <port>");
            return;
        }
        int port = Integer.parseInt(args[1]);
        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Server listening on port " + port);
            byte[] buf = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                byte[] data = new byte[packet.getLength()];
                System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

                // (i) hex dump
                System.out.println("RX (" + packet.getAddress() + ":" + packet.getPort() + ") "
                        + packet.getLength() + " bytes");
                System.out.println(toHex(data, data.length));

                Response resp;
                short ridEcho = 0;
                try {
                    Request req = Request.parse(data, data.length);
                    ridEcho = req.requestId;

                    // (ii) human-friendly
                    String opWord = Request.opNameForCode(req.opCode);
                    System.out.println("RequestID=" + Short.toUnsignedInt(req.requestId) + " :: "
                            + req.operand1 + " " + opWord + " " + req.operand2
                            + "   (name field: \"" + req.opNameString() + "\")");

                    int result;
                    if (req.opCode == 5 && req.operand2 == 0) {
                        // Spec only defines 127 for TML mismatch; for /0 we still return 0 with error=0 per spec.
                        result = 0;
                    } else {
                        result = compute(req.opCode, req.operand1, req.operand2);
                    }
                    resp = Response.ok(result, req.requestId);
                } catch (IllegalArgumentException ex) {
                    // TML mismatch or malformed → error 127; echo whatever requestId we could parse (0 if none)
                    System.out.println("Parse error: " + ex.getMessage());
                    resp = Response.lengthError(ridEcho);
                }

                byte[] out = resp.toByteArray();
                // Debug: hex dump of TX
                System.out.println("TX " + out.length + " bytes");
                System.out.println(toHex(out, out.length));

                DatagramPacket reply = new DatagramPacket(out, out.length,
                        packet.getAddress(), packet.getPort());
                socket.send(reply);
            }
        }
    }
}
