package dev.visual.client;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class DiscordRpc implements AutoCloseable {
    static final String APPLICATION_ID = "1293983298254671995";
    private static final long RETRY_DELAY = TimeUnit.SECONDS.toNanos(10);
    private static final long HANDSHAKE_TIMEOUT = TimeUnit.SECONDS.toNanos(15);
    private static final long UPDATE_INTERVAL = TimeUnit.SECONDS.toNanos(5);
    private final long startedAt = Instant.now().getEpochSecond();
    private final Connector connector;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().name("Marce Discord RPC").daemon().factory());
    private volatile Desired desired = new Desired(false, "В главном меню");
    private volatile Connection connection;
    private volatile boolean closed;
    private long retryAt;

    interface Connector { DiscordIpc.Transport open() throws IOException; }
    private record Desired(boolean enabled, String state) {}

    DiscordRpc() { this(DiscordTransports::open); }
    DiscordRpc(Connector connector) {
        this.connector = connector;
        worker.scheduleWithFixedDelay(this::poll, 0, 1, TimeUnit.SECONDS);
    }

    void update(boolean enabled, String state) {
        Desired current = desired;
        if (current.enabled != enabled || !current.state.equals(state)) desired = new Desired(enabled, state);
    }

    boolean isPublished() {
        Connection active = connection;
        return !closed && active != null && active.ready && active.published;
    }

    private void poll() {
        if (closed) return;
        Desired current = desired;
        Connection active = connection;
        if (!current.enabled) {
            if (active != null) clearActivity(active);
            return;
        }
        long now = System.nanoTime();
        if (active == null) {
            if (now < retryAt) return;
            retryAt = now + RETRY_DELAY;
            connect();
            return;
        }
        try {
            if (!active.ready) {
                if (now - active.openedAt > HANDSHAKE_TIMEOUT) disconnect(active);
            } else if (!current.state.equals(active.sentState)
                && now - active.lastSent >= UPDATE_INTERVAL) {
                active.activity(activity(current.state));
                active.sentState = current.state;
                active.lastSent = now;
            }
        } catch (IOException | RuntimeException ignored) {
            disconnect(active);
        }
    }

    private void connect() {
        Connection session = null;
        try {
            session = new Connection(connector.open());
            if (!install(session)) {
                disconnect(session);
                return;
            }
            JsonObject handshake = new JsonObject();
            handshake.addProperty("v", 1);
            handshake.addProperty("client_id", APPLICATION_ID);
            session.send(DiscordIpc.HANDSHAKE, handshake.toString().getBytes(StandardCharsets.UTF_8));
            Connection connected = session;
            Thread.ofPlatform().name("Marce Discord IPC reader").daemon().start(() -> receive(connected));
        } catch (IOException | RuntimeException ignored) {
            if (session != null) disconnect(session);
        }
    }

    private JsonObject activity(String state) {
        JsonObject activity = new JsonObject();
        activity.addProperty("type", 0);
        activity.addProperty("details", "Marce Visual • Minecraft 1.21.11");
        activity.addProperty("state", state);
        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", startedAt);
        activity.add("timestamps", timestamps);
        return activity;
    }

    private void clearActivity(Connection session) {
        try {
            if (session.ready) session.activity(null);
        } catch (IOException ignored) {
        } finally {
            disconnect(session);
        }
    }

    private void receive(Connection session) {
        try {
            while (!closed && connection == session) {
                DiscordIpc.Frame frame = DiscordIpc.read(session.transport);
                if (frame.opcode() == DiscordIpc.CLOSE) break;
                if (frame.opcode() == DiscordIpc.PING) {
                    session.send(DiscordIpc.PONG, frame.payload());
                } else if (frame.opcode() == DiscordIpc.FRAME) {
                    JsonObject json = JsonParser.parseString(frame.text()).getAsJsonObject();
                    String event = json.has("evt") && !json.get("evt").isJsonNull() ? json.get("evt").getAsString() : "";
                    if (event.equals("READY")) session.ready = true;
                    if (event.equals("ERROR")) break;
                    if (json.has("cmd") && json.get("cmd").getAsString().equals("SET_ACTIVITY")) session.published = true;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Discord закрыли или перезапустили. Подключимся заново в фоне.
        } finally {
            disconnect(session);
        }
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
        // Закрытие pipe может ждать ввод-вывод Windows. Игровой поток ждать не должен.
        Thread.ofPlatform().name("Marce Discord IPC cleanup").daemon().start(() -> {
            try {
                session.transport.close();
            } catch (IOException ignored) {}
        });
    }

    @Override
    public void close() {
        closed = true;
        Connection active = connection;
        if (active != null) disconnect(active);
        worker.shutdownNow();
    }

    private static final class Connection {
        final DiscordIpc.Transport transport;
        final long openedAt = System.nanoTime();
        volatile boolean ready;
        volatile boolean published;
        boolean disposing;
        String sentState;
        long lastSent;
        Connection(DiscordIpc.Transport transport) {
            this.transport = transport;
        }

        synchronized void send(int opcode, byte[] bytes) throws IOException {
            DiscordIpc.write(transport, opcode, bytes);
        }

        void activity(JsonObject activity) throws IOException {
            JsonObject command = new JsonObject();
            JsonObject args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());
            args.add("activity", activity == null ? JsonNull.INSTANCE : activity);
            command.addProperty("cmd", "SET_ACTIVITY");
            command.add("args", args);
            command.addProperty("nonce", UUID.randomUUID().toString());
            send(DiscordIpc.FRAME, command.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
