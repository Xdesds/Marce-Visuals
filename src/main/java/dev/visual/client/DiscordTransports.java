package dev.visual.client;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

final class DiscordTransports {
    private static final int PIPE_COUNT = 10;

    private DiscordTransports() {}

    static DiscordIpc.Transport open() throws IOException {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            for (int index = 0; index < PIPE_COUNT; index++) {
                try {
                    return new WindowsDiscordPipe(index);
                } catch (IOException ignored) {}
            }
        } else {
            for (String root : socketRoots()) {
                for (String suffix : new String[]{"", "app/com.discordapp.Discord"}) {
                    for (int index = 0; index < PIPE_COUNT; index++) {
                        Path path = Path.of(root, suffix, "discord-ipc-" + index);
                        try {
                            return new UnixSocket(path);
                        } catch (IOException ignored) {}
                    }
                }
            }
        }
        throw new IOException("Локальный Discord недоступен");
    }

    private static Set<String> socketRoots() {
        Set<String> roots = new LinkedHashSet<>();
        for (String variable : new String[]{"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            String value = System.getenv(variable);
            if (value != null && !value.isBlank()) roots.add(value);
        }
        roots.add(System.getProperty("java.io.tmpdir", "/tmp"));
        roots.add("/tmp");
        return roots;
    }

    private static final class UnixSocket implements DiscordIpc.Transport {
        private final SocketChannel socket;

        private UnixSocket(Path path) throws IOException {
            socket = SocketChannel.open(StandardProtocolFamily.UNIX);
            try {
                socket.connect(UnixDomainSocketAddress.of(path));
            } catch (IOException | RuntimeException error) {
                socket.close();
                throw error;
            }
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            return socket.read(ByteBuffer.wrap(bytes, offset, length));
        }

        @Override
        public void write(byte[] bytes) throws IOException {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) socket.write(buffer);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
