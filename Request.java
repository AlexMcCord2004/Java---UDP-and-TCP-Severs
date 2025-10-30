import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Request {
    public final byte tml;        // 1 byte total message length
    public final byte opCode;     // 1 byte
    public final int operand1;    // 4 bytes
    public final int operand2;    // 4 bytes
    public final short requestId; // 2 bytes
    public final byte opNameLen;  // 1 byte (# of bytes of op name, includes BOM)
    public final byte[] opName;   // variable, UTF-16 with BOM

    private static final Charset UTF16BE = StandardCharsets.UTF_16BE;
    private static final byte[] UTF16BE_BOM = new byte[]{(byte)0xFE, (byte)0xFF};

    private Request(byte tml, byte opCode, int operand1, int operand2, short requestId, byte opNameLen, byte[] opName) {
        this.tml = tml;
        this.opCode = opCode;
        this.operand1 = operand1;
        this.operand2 = operand2;
        this.requestId = requestId;
        this.opNameLen = opNameLen;
        this.opName = opName;
    }

    public static String opNameForCode(int code) {
        switch (code) {
            case 0: return "subtraction";
            case 1: return "addition";
            case 2: return "and";
            case 3: return "or";
            case 4: return "multiplication";
            case 5: return "division";
            default: return "unknown";
        }
    }

    public static Request build(byte opCode, int operand1, int operand2, short requestId) {
        String name = opNameForCode(opCode);
        // Encode as UTF-16BE + BOM (as shown in the assignment examples).
        byte[] nameBytes = name.getBytes(UTF16BE);
        byte[] withBom = new byte[2 + nameBytes.length];
        System.arraycopy(UTF16BE_BOM, 0, withBom, 0, 2);
        System.arraycopy(nameBytes, 0, withBom, 2, nameBytes.length);

        // 1 + 1 + 4 + 4 + 2 + 1 + (opName bytes)
        int totalLen = 1 + 1 + 4 + 4 + 2 + 1 + withBom.length;
        if (totalLen > 255) throw new IllegalArgumentException("Message too long for 1-byte TML");
        byte tml = (byte) totalLen;
        byte opNameLen = (byte) withBom.length;

        return new Request(tml, opCode, operand1, operand2, requestId, opNameLen, withBom);
    }

    public byte[] toByteArray() {
        ByteBuffer buf = ByteBuffer.allocate(Byte.toUnsignedInt(tml)).order(ByteOrder.BIG_ENDIAN);
        buf.put(tml);
        buf.put(opCode);
        buf.putInt(operand1);
        buf.putInt(operand2);
        buf.putShort(requestId);
        buf.put(opNameLen);
        buf.put(opName);
        return buf.array();
    }

    public static Request parse(byte[] dat, int length) throws IllegalArgumentException {
        if (length < 1) throw new IllegalArgumentException("Empty datagram");
        ByteBuffer buf = ByteBuffer.wrap(dat, 0, length).order(ByteOrder.BIG_ENDIAN);
        int tmlUnsigned = Byte.toUnsignedInt(buf.get());
        if (tmlUnsigned != length) throw new IllegalArgumentException("TML mismatch: " + tmlUnsigned + " vs " + length);
        byte opCode = buf.get();
        int op1 = buf.getInt();
        int op2 = buf.getInt();
        short reqId = buf.getShort();
        byte nameLen = buf.get();
        int nLen = Byte.toUnsignedInt(nameLen);
        if (buf.remaining() != nLen) throw new IllegalArgumentException("Op name length mismatch");
        byte[] name = new byte[nLen];
        buf.get(name);
        return new Request((byte)tmlUnsigned, opCode, op1, op2, reqId, nameLen, name);
    }

    public String opNameString() {
        // Expect BOM then UTF-16BE bytes
        if (opName.length >= 2 && (opName[0] == (byte)0xFE && opName[1] == (byte)0xFF)) {
            return new String(Arrays.copyOfRange(opName, 2, opName.length), UTF16BE);
        }
        // Fallback: interpret as UTF-16BE even without BOM
        return new String(opName, UTF16BE);
    }
}
