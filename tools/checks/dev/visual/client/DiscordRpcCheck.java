package dev.visual.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class DiscordRpcCheck {
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--live")) {
            try (var rpc = new DiscordRpc()) {
                rpc.update(true, "В главном меню");
                Thread.sleep(6500);
                if (!rpc.isPublished()) throw new AssertionError("Discord did not acknowledge the activity");
                System.out.println("Live Discord IPC handshake and activity update accepted.");
            }
            return;
        }
        FakeTransport peer = new FakeTransport();
        try (var rpc = new DiscordRpc(() -> peer)) {
            rpc.update(true, "В главном меню");
            var handshake = peer.next();
            if (handshake.opcode() != 0) throw new AssertionError("Handshake opcode");
            JsonObject hello = json(handshake);
            if (!hello.get("client_id").getAsString().equals(DiscordRpc.APPLICATION_ID) || hello.get("v").getAsInt() != 1)
                throw new AssertionError("Handshake application ID/version");
            peer.feed(1, "{\"cmd\":\"DISPATCH\",\"evt\":\"READY\",\"data\":{}}");
            JsonObject first = activity(peer.next());
            if (!first.get("state").getAsString().equals("В главном меню")) throw new AssertionError("UTF-8 state");
            long start = first.getAsJsonObject("timestamps").get("start").getAsLong();
            peer.feed(3, "{\"ping\":1}");
            var pong = peer.next();
            if (pong.opcode() != 4 || !pong.text().equals("{\"ping\":1}")) throw new AssertionError("Ping/pong payload");
            rpc.update(true, "Одиночная игра");
            JsonObject second = activity(peer.next());
            if (!second.get("state").getAsString().equals("Одиночная игра")
                || second.getAsJsonObject("timestamps").get("start").getAsLong() != start)
                throw new AssertionError("State transition/session timer");
            rpc.update(false, "Одиночная игра");
            if (!json(peer.next()).getAsJsonObject("args").get("activity").isJsonNull()) throw new AssertionError("Activity clear");
        }
        FakeTransport invalid = new FakeTransport();
        invalid.incoming.add(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(1).putInt(DiscordIpc.MAX_PAYLOAD + 1).array());
        try { DiscordIpc.read(invalid); throw new AssertionError("Oversized frame accepted"); }
        catch (IOException expected) {}
        for (int i = 0; i < 50 && !peer.closed; i++) Thread.sleep(10);
        if (!peer.closed) throw new AssertionError("IPC connection leaked");
        System.out.println("Discord RPC checks passed: framing, fragmented UTF-8, handshake, activity, ping/pong, state changes, timer, disable and cleanup.");
    }

    private static JsonObject json(DiscordIpc.Frame frame) { return JsonParser.parseString(frame.text()).getAsJsonObject(); }
    private static JsonObject activity(DiscordIpc.Frame frame) {
        JsonObject command = json(frame), arguments = command.getAsJsonObject("args");
        if (frame.opcode() != 1 || !command.get("cmd").getAsString().equals("SET_ACTIVITY")
            || arguments.get("pid").getAsLong() != ProcessHandle.current().pid() || !command.has("nonce"))
            throw new AssertionError("SET_ACTIVITY envelope");
        return arguments.getAsJsonObject("activity");
    }

    private static final class FakeTransport implements DiscordIpc.Transport {
        final LinkedBlockingQueue<byte[]> incoming = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<DiscordIpc.Frame> outgoing = new LinkedBlockingQueue<>();
        byte[] chunk;
        int offset;
        volatile boolean closed;
        void feed(int opcode, String json) {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            byte[] packet = ByteBuffer.allocate(8 + bytes.length).order(ByteOrder.LITTLE_ENDIAN).putInt(opcode).putInt(bytes.length).put(bytes).array();
            // Режем заголовок и UTF-8-символы, как при чтении из настоящего pipe.
            for (int start = 0; start < packet.length; start += 3) incoming.add(Arrays.copyOfRange(packet, start, Math.min(start + 3, packet.length)));
        }
        DiscordIpc.Frame next() throws InterruptedException {
            var frame = outgoing.poll(8, TimeUnit.SECONDS);
            if (frame == null) throw new AssertionError("RPC response timeout");
            return frame;
        }
        public int read(byte[] bytes, int off, int len) throws IOException {
            if (closed) return -1;
            if (chunk == null || offset == chunk.length) {
                try { chunk = incoming.poll(30, TimeUnit.SECONDS); offset = 0; }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
                if (chunk == null || chunk.length == 0) return -1;
            }
            int count = Math.min(len, chunk.length - offset);
            System.arraycopy(chunk, offset, bytes, off, count); offset += count;
            return count;
        }
        public void write(byte[] bytes) {
            ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            int opcode = header.getInt(), length = header.getInt();
            outgoing.add(new DiscordIpc.Frame(opcode, Arrays.copyOfRange(bytes, 8, 8 + length)));
        }
        public void close() { closed = true; incoming.add(new byte[0]); }
    }
}
