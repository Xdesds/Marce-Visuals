package dev.visual.client;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

final class DiscordIpc {
    static final int HANDSHAKE = 0;
    static final int FRAME = 1;
    static final int CLOSE = 2;
    static final int PING = 3;
    static final int PONG = 4;
    static final int MAX_PAYLOAD = 64 * 1024;
    interface Transport extends AutoCloseable {
        int read(byte[] bytes, int offset, int length) throws IOException;
        void write(byte[] bytes) throws IOException;
        void close() throws IOException;
    }
    record Frame(int opcode, byte[] payload) {
        String text() { return new String(payload, StandardCharsets.UTF_8); }
    }

    static Frame read(Transport transport) throws IOException {
        // В заголовке два числа little-endian: код команды и длина JSON в байтах.
        byte[] header = new byte[8];
        fully(transport, header);
        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int opcode = buffer.getInt(), length = buffer.getInt();
        if (opcode < 0 || opcode > 4 || length < 0 || length > MAX_PAYLOAD) throw new IOException("Invalid IPC frame");
        byte[] payload = new byte[length];
        fully(transport, payload);
        return new Frame(opcode, payload);
    }

    static void write(Transport transport, int opcode, byte[] payload) throws IOException {
        if (payload.length > MAX_PAYLOAD) throw new IOException("IPC payload too large");
        ByteBuffer frame = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        frame.putInt(opcode).putInt(payload.length).put(payload);
        transport.write(frame.array());
    }

    private static void fully(Transport transport, byte[] target) throws IOException {
        int offset = 0;
        while (offset < target.length) {
            int read = transport.read(target, offset, target.length - offset);
            if (read < 0) throw new EOFException("Discord disconnected");
            if (read == 0) throw new IOException("Empty IPC read");
            offset += read;
        }
    }
}
