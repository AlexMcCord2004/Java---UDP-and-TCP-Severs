import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Response {
    public final byte tml;        // 1 byte
    public final int result;      // 4 bytes
    public final byte errorCode;  // 1 byte (0 ok, 127 length error)
    public final short requestId; // 2 bytes

    public Response(byte tml, int result, byte errorCode, short requestId) {
        this.tml = tml;
        this.result = result;
        this.errorCode = errorCode;
        this.requestId = requestId;
    }

    public static Response ok(int result, short requestId) {
        // Fixed size: 1 + 4 + 1 + 2 = 8
        return new Response((byte)8, result, (byte)0, requestId);
    }

    public static Response lengthError(short requestId) {
        return new Response((byte)8, 0, (byte)127, requestId);
    }

    public byte[] toByteArray() {
        ByteBuffer buf = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        buf.put(tml);
        buf.putInt(result);
        buf.put(errorCode);
        buf.putShort(requestId);
        return buf.array();
    }

    public static Response parse(byte[] dat, int length) {
        if (length < 8) throw new IllegalArgumentException("Response too short");
        ByteBuffer buf = ByteBuffer.wrap(dat, 0, length).order(ByteOrder.BIG_ENDIAN);
        int tmlUnsigned = Byte.toUnsignedInt(buf.get());
        if (tmlUnsigned != length) throw new IllegalArgumentException("TML mismatch in response");
        int res = buf.getInt();
        byte err = buf.get();
        short rid = buf.getShort();
        return new Response((byte)tmlUnsigned, res, err, rid);
    }
}
