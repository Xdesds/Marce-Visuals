package dev.visual.client;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Local desktop IPC only. No OAuth, bot token or application secret is required. */
final class DiscordRpc implements AutoCloseable {
    static final String APPLICATION_ID = "1293983298254671995";
    private final long startedAt = Instant.now().getEpochSecond();
    private final Connector connector;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Marce Discord RPC");
        thread.setDaemon(true);
        return thread;
    });
    private volatile Desired desired = new Desired(false, "В главном меню");
    private volatile Connection connection;
    private volatile boolean closed;
    private long retryAt;

    interface Connector { DiscordIpc.Transport open() throws IOException; }
    private record Desired(boolean enabled, String state) {}

    DiscordRpc() { this(DiscordRpc::openDesktop); }
    DiscordRpc(Connector connector) {
        this.connector = connector;
        worker.scheduleWithFixedDelay(this::poll, 0, 1, TimeUnit.SECONDS);
    }

    void update(boolean enabled, String state) {
        Desired current = desired;
        if (current.enabled != enabled || !current.state.equals(state)) desired = new Desired(enabled, state);
    }

    boolean isReady() {
        Connection active = connection;
        return !closed && active != null && active.ready;
    }

    boolean isPublished() {
        Connection active = connection;
        return isReady() && active != null && active.published;
    }

    private void poll() {
        if (closed) return;
        Desired current = desired;
        Connection active = connection;
        if (!current.enabled) {
            if (active != null) {
                try { if (active.ready) active.activity(null); } catch (IOException ignored) {}
                disconnect(active);
            }
            return;
        }
        long now = System.nanoTime();
        if (active == null) {
            if (now < retryAt) return;
            retryAt = now + TimeUnit.SECONDS.toNanos(10);
            try {
                active = new Connection(connector.open());
                if (!install(active)) { disconnect(active); return; }
                JsonObject handshake = new JsonObject();
                handshake.addProperty("v", 1);
                handshake.addProperty("client_id", APPLICATION_ID);
                active.send(0, handshake.toString().getBytes(StandardCharsets.UTF_8));
                Connection session = active;
                Thread reader = new Thread(() -> receive(session), "Marce Discord IPC reader");
                reader.setDaemon(true);
                reader.start();
            } catch (IOException | RuntimeException ignored) {
                if (active != null) disconnect(active);
            }
            return;
        }
        try {
            if (!active.ready) {
                if (now - active.openedAt > TimeUnit.SECONDS.toNanos(15)) disconnect(active);
            } else if (!current.state.equals(active.sentState)
                && now - active.lastSent >= TimeUnit.SECONDS.toNanos(5)) {
                JsonObject activity = new JsonObject();
                activity.addProperty("type", 0);
                activity.addProperty("details", "Marce Visual • Minecraft 1.21.11");
                activity.addProperty("state", current.state);
                JsonObject timestamps = new JsonObject();
                timestamps.addProperty("start", startedAt);
                activity.add("timestamps", timestamps);
                active.activity(activity);
                active.sentState = current.state;
                active.lastSent = now;
            }
        } catch (IOException | RuntimeException ignored) { disconnect(active); }
    }

    private void receive(Connection session) {
        try {
            while (!closed && connection == session) {
                DiscordIpc.Frame frame = DiscordIpc.read(session.transport);
                if (frame.opcode() == 2) break;
                if (frame.opcode() == 3) {
                    session.send(4, frame.payload());
                } else if (frame.opcode() == 1) {
                    JsonObject json = JsonParser.parseString(frame.text()).getAsJsonObject();
                    String event = json.has("evt") && !json.get("evt").isJsonNull() ? json.get("evt").getAsString() : "";
                    if (event.equals("READY")) session.ready = true;
                    if (event.equals("ERROR")) break;
                    if (json.has("cmd") && json.get("cmd").getAsString().equals("SET_ACTIVITY")) session.published = true;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Discord may close or restart independently of Minecraft.
        } finally { disconnect(session); }
    }

    private synchronized boolean install(Connection session) {
        if (closed) return false;
        connection = session;
        return true;
    }

    private synchronized void disconnect(Connection session) {
        session.ready = false;
        if (connection == session) connection = null;
        if (session.disposing) return;
        session.disposing = true;
        Thread cleanup = new Thread(() -> {
            try { session.transport.close(); } catch (IOException ignored) {}
        }, "Marce Discord IPC cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    @Override public void close() {
        closed = true;
        Connection active = connection;
        // Closing the local session also removes its activity in Discord.
        if (active != null) disconnect(active);
        worker.shutdownNow();
    }

    private static DiscordIpc.Transport openDesktop() throws IOException {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            for (int index = 0; index < 10; index++) {
                try {
                    return new WindowsDiscordPipe(index);
                } catch (IOException ignored) {}
            }
        } else {
            Set<String> roots = new LinkedHashSet<>();
            for (String variable : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
                String value = System.getenv(variable);
                if (value != null && !value.isBlank()) roots.add(value);
            }
            roots.add(System.getProperty("java.io.tmpdir", "/tmp"));
            roots.add("/tmp");
            for (String root : roots) for (String suffix : new String[]{"", "app/com.discordapp.Discord"}) {
                for (int index = 0; index < 10; index++) {
                    SocketChannel socket = SocketChannel.open(StandardProtocolFamily.UNIX);
                    try {
                        socket.connect(UnixDomainSocketAddress.of(Path.of(root, suffix, "discord-ipc-" + index)));
                        return new DiscordIpc.Transport() {
                            public int read(byte[] b, int off, int len) throws IOException { return socket.read(ByteBuffer.wrap(b, off, len)); }
                            public void write(byte[] b) throws IOException {
                                ByteBuffer buffer = ByteBuffer.wrap(b);
                                while (buffer.hasRemaining()) socket.write(buffer);
                            }
                            public void close() throws IOException { socket.close(); }
                        };
                    } catch (IOException error) { socket.close(); }
                }
            }
        }
        throw new IOException("Discord desktop IPC unavailable");
    }

    private static final class Connection {
        final DiscordIpc.Transport transport;
        final long openedAt = System.nanoTime();
        volatile boolean ready;
        volatile boolean published;
        boolean disposing;
        String sentState;
        long lastSent;
        Connection(DiscordIpc.Transport transport) { this.transport = transport; }
        synchronized void send(int opcode, byte[] bytes) throws IOException { DiscordIpc.write(transport, opcode, bytes); }
        void activity(JsonObject activity) throws IOException {
            JsonObject command = new JsonObject(), args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());
            args.add("activity", activity == null ? JsonNull.INSTANCE : activity);
            command.addProperty("cmd", "SET_ACTIVITY");
            command.add("args", args);
            command.addProperty("nonce", UUID.randomUUID().toString());
            send(1, command.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
