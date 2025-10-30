import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Scanner;

public class ClientTCP {
    private static final String[] OP_NAMES = {
            "subtraction", "addition", "and", "or", "multiplication", "division"
    };

    public static void main(String[] args) {
        if (args.length != 3 || !"ClientTCP".equals(args[0])) {
            System.err.println("Usage: prog ClientTCP <server> <port>");
            System.err.println("Example: java ClientTCP ClientTCP tux055 10023");
            System.exit(1);
        }
        String host = args[1];
        int port = Integer.parseInt(args[2]);

        try (Socket sock = new Socket(host, port)) {
            sock.setTcpNoDelay(true);
            InputStream in = sock.getInputStream();
            OutputStream out = sock.getOutputStream();
            Scanner sc = new Scanner(System.in);

            System.out.printf("Connected to %s:%d%n", host, port);
            System.out.println("Enter requests. Type 'q' to quit.");
            System.out.println("OpCode mapping: 0='-'  1='+'  2='&'  3='|'  4='*'  5='/'");

            Random rand = new Random();
            int reqIdCounter = 1 + rand.nextInt(5000);

            long rttMin = Long.MAX_VALUE, rttMax = Long.MIN_VALUE, rttSum = 0L;
            long rttCount = 0L;

            while (true) {
                System.out.print("OpCode (0..5) or 'q': ");
                String token = sc.next();
                if (token.equalsIgnoreCase("q")) break;

                int opCode;
                try {
                    opCode = Integer.parseInt(token);
                    if (opCode < 0 || opCode > 5) {
                        System.out.println("Invalid opcode. Try again.");
                        continue;
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Try again.");
                    continue;
                }

                System.out.print("Operand1 (int): ");
                int op1 = readInt(sc);
                System.out.print("Operand2 (int): ");
                int op2 = readInt(sc);

                String opName = OP_NAMES[opCode]; // exactly as spec expects

                // Build op name bytes: UTF-16 with BOM
                byte[] opNameBytes = ByteUtils.utf16WithBOM(opName);
                int nameLen = opNameBytes.length;

                // Request ID (2 bytes, wraps at 65535)
                int reqId = reqIdCounter & 0xFFFF;
                reqIdCounter++;

                // TML = 1 +1 +4 +4 +2 +1 + nameLen
                int tml = 1 + 1 + 4 + 4 + 2 + 1 + nameLen;

                ByteArrayOutputStream msg = new ByteArrayOutputStream(tml);
                msg.write(tml & 0xFF);           // 1) TML (1 byte)
                msg.write(opCode & 0xFF);        // 2) Op Code (1 byte)
                ByteUtils.putIntBE(msg, op1);    // 3) Operand1 (4 bytes)
                ByteUtils.putIntBE(msg, op2);    // 4) Operand2 (4 bytes)
                ByteUtils.putShortBE(msg, reqId);// 5) Request ID (2 bytes)
                msg.write(nameLen & 0xFF);       // 6) Op Name Length (1 byte)
                try {
                    msg.write(opNameBytes);      // 7) Op Name (UTF-16 with BOM)
                } catch (IOException ignore) { /* ByteArrayOutputStream won't throw here */ }

                byte[] reqBytes = msg.toByteArray();

                // iii) display request bytes in hex
                System.out.println("Request (hex):");
                ByteUtils.hexdump(reqBytes);

                // iv) send & time RTT
                long t0 = System.nanoTime();
                out.write(reqBytes);
                out.flush();

                // v) read response (exactly 8 bytes)
                int tmlResp = in.read();
                if (tmlResp < 0) {
                    System.out.println("Server closed connection.");
                    break;
                }
                byte[] respRest = ByteUtils.readFully(in, tmlResp - 1);
                byte[] resp = new byte[tmlResp];
                resp[0] = (byte) tmlResp;
                System.arraycopy(respRest, 0, resp, 1, respRest.length);

                long t1 = System.nanoTime();
                long rttMicros = (t1 - t0) / 1000L;

                // v) display response hex
                System.out.println("Response (hex):");
                ByteUtils.hexdump(resp);

                // vi) parse & display response human-friendly
                // Layout: [0]=TML(8), [1..4]=Result, [5]=Error, [6..7]=ReqID
                int result = ((resp[1] & 0xFF) << 24) | ((resp[2] & 0xFF) << 16) | ((resp[3] & 0xFF) << 8) | (resp[4] & 0xFF);
                int err = resp[5] & 0xFF;
                int respReqId = ((resp[6] & 0xFF) << 8) | (resp[7] & 0xFF);

String humanOp; 
switch (opCode) { 
    case 0: humanOp = "-"; break; 
    case 1: humanOp = "+"; break; 
    case 2: humanOp = "&"; break; 
    case 3: humanOp = "|"; break; 
    case 4: humanOp = "*"; break; 
    case 5: humanOp = "/"; break; 
    default: humanOp = "?"; break; 
}
                System.out.printf("ReqID=%d | %d %s %d => %d | Error=%d (%s)%n",
                        respReqId, op1, humanOp, op2, result, err, err == 0 ? "Ok" : "Invalid");

                // vii) show RTT
                System.out.printf("RTT: %d µs%n", rttMicros);

                // aggregate stats
                rttMin = Math.min(rttMin, rttMicros);
                rttMax = Math.max(rttMax, rttMicros);
                rttSum += rttMicros;
                rttCount++;
            }

            if (rttCount > 0) {
                double avg = rttSum / (double) rttCount;
                System.out.printf("RTT stats over %d requests → min=%,d µs | avg=%.1f µs | max=%,d µs%n",
                        rttCount, rttMin, avg, rttMax);
            }

            System.out.println("Bye.");
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
            System.exit(2);
        }
    }

    private static int readInt(Scanner sc) {
        while (true) {
            String s = sc.next();
            try { return Integer.parseInt(s); }
            catch (NumberFormatException e) { System.out.print("Invalid int, try again: "); }
        }
    }
}
