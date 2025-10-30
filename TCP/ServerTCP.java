import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class ServerTCP {
    // Error code per spec: 0 = OK, 127 = invalid message length (and we also use 127 for div-by-zero)
    private static final int ERR_OK = 0;
    private static final int ERR_BADLEN = 127;

    public static void main(String[] args) {
        if (args.length != 2 || !"ServerTCP".equals(args[0])) {
            System.err.println("Usage: prog ServerTCP <port>");
            System.err.println("Example: java ServerTCP ServerTCP 10023");
            System.exit(1);
        }
        int port = Integer.parseInt(args[1]);

        try (ServerSocket server = new ServerSocket(port)) {
            System.out.printf("Server listening on port %d ...%n", port);
            while (true) {
                try (Socket sock = server.accept()) {
                    sock.setTcpNoDelay(true);
                    System.out.println("Accepted connection from " + sock.getRemoteSocketAddress());
                    handleClient(sock);
                } catch (IOException e) {
                    System.err.println("Client handling error: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Bind/listen failed: " + e.getMessage());
        }
    }

    private static void handleClient(Socket sock) throws IOException {
        InputStream in = sock.getInputStream();
        OutputStream out = sock.getOutputStream();

        while (true) {
            // Read 1 byte TML; if client closed, we’re done
            int tml = in.read();
            if (tml < 0) break; // connection closed

            // Read the rest of the request
            byte[] rest = ByteUtils.readFully(in, tml - 1);
            byte[] full = new byte[tml];
            full[0] = (byte) tml;
            System.arraycopy(rest, 0, full, 1, rest.length);

            // i) display request bytes in hex
            System.out.println("Request (hex):");
            ByteUtils.hexdump(full);

            int error = ERR_OK;
            int result = 0;
            int reqId = 0;
            try {
                // Parse per spec
                // Layout: [0]=TML
                // [1]=OpCode
                // [2..5]=Operand1 (int)
                // [6..9]=Operand2 (int)
                // [10..11]=RequestID (unsigned short)
                // [12]=OpNameLen (L)
                // [13..(13+L-1)]=OpName bytes (UTF-16 with BOM)
                if (tml < 13) throw new IllegalArgumentException("TML too short");

                int opCode = full[1] & 0xFF;

                int op1 = ((full[2] & 0xFF) << 24) | ((full[3] & 0xFF) << 16) | ((full[4] & 0xFF) << 8) | (full[5] & 0xFF);
                int op2 = ((full[6] & 0xFF) << 24) | ((full[7] & 0xFF) << 16) | ((full[8] & 0xFF) << 8) | (full[9] & 0xFF);

                reqId = ((full[10] & 0xFF) << 8) | (full[11] & 0xFF);

                int nameLen = full[12] & 0xFF;
                int nameStart = 13;
                int nameEnd = nameStart + nameLen; // exclusive

                if (nameEnd != tml) {
                    // length mismatch against TML
                    error = ERR_BADLEN;
                }

                String opName = "";
                if (error == ERR_OK) {
                    byte[] nameBytes = new byte[nameLen];
                    System.arraycopy(full, nameStart, nameBytes, 0, nameLen);
                    // Expect BOM FE FF at start:
                    if (nameLen >= 2 && (nameBytes[0] == (byte)0xFE) && (nameBytes[1] == (byte)0xFF)) {
                        byte[] be = new byte[nameLen - 2];
                        System.arraycopy(nameBytes, 2, be, 0, nameLen - 2);
                        opName = new String(be, StandardCharsets.UTF_16BE);
                    } else {
                        // If no BOM, still try to decode as UTF-16BE (not expected by spec)
                        opName = new String(nameBytes, StandardCharsets.UTF_16BE);
                    }
                }

                // ii) display request in a user-friendly way
                System.out.printf("RequestID=%d | OpCode=%d | OpName=\"%s\" | Operands: %d ? %d%n",
                        reqId, opCode, opName, op1, op2);

                // Compute result if length OK
                if (error == ERR_OK) {
                    switch (opCode) {
                        case 0: // -
                            result = op1 - op2;
                            break;
                        case 1: // +
                            result = op1 + op2;
                            break;
                        case 2: // &
                            result = op1 & op2;
                            break;
                        case 3: // |
                            result = op1 | op2;
                            break;
                        case 4: // *
                            result = op1 * op2;
                            break;
                        case 5: // /
                            if (op2 == 0) {
                                error = ERR_BADLEN; // use 127 for invalid
                                result = 0;
                            } else {
                                result = op1 / op2;
                            }
                            break;
                        default:
                            error = ERR_BADLEN;
                            result = 0;
                    }
                }

            } catch (Exception ex) {
                error = ERR_BADLEN;
                result = 0;
                System.err.println("Parse/compute error: " + ex.getMessage());
            }

            // Build response: TML(1)=8, Result(4), Error(1), ReqID(2)
            ByteArrayOutputStream resp = new ByteArrayOutputStream();
            resp.write(8);
            ByteUtils.putIntBE(resp, result);
            resp.write(error & 0xFF);
            ByteUtils.putShortBE(resp, reqId & 0xFFFF);
            byte[] respBytes = resp.toByteArray();

            // Send back & also display hex
            out.write(respBytes);
            out.flush();

            System.out.println("Response (hex):");
            ByteUtils.hexdump(respBytes);
            System.out.printf("Responded: RequestID=%d | Result=%d | Error=%d%n", reqId, result, error);
        }
    }
}
