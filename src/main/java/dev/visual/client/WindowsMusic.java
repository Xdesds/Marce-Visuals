package dev.visual.client;

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

final class WindowsMusic implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger("Marce Visual");
    private static final Gson GSON = new Gson();
    private static final String RESOURCES = "/assets/visual_client/windows/";

    private final Path powershell = findPowerShell();
    private Reader reader;

    void setEnabled(boolean enabled) {
        if (enabled && powershell != null) {
            if (reader == null) {
                reader = new Reader(powershell);
                Thread.ofVirtual().name("Marce Windows music").start(reader);
            }
        } else {
            close();
        }
    }

    Track playing() {
        return reader == null ? null : reader.track;
    }

    Signal signal(long now) {
        AudioSample sample = reader == null ? null : reader.sample;
        return sample == null || now - sample.time() > 120_000_000L ? Signal.SILENT : sample.signal();
    }

    @Override
    public void close() {
        if (reader != null) {
            reader.stop();
            reader = null;
        }
    }

    private static Path findPowerShell() {
        String root = System.getenv("SystemRoot");
        if (root == null || !System.getProperty("os.name", "").startsWith("Windows")) {
            return null;
        }
        Path path = Path.of(root, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        return Files.isRegularFile(path) ? path : null;
    }

    record Track(String title, String artist, BufferedImage cover) {}
    record Signal(float bass, float beat) {
        static final Signal SILENT = new Signal(0, 0);
    }
    private record Metadata(String title, String artist, String cover) {}
    private record AudioSample(Signal signal, long time) {}

    private static final class Reader implements Runnable {
        private final Path powershell;
        private volatile Track track;
        private volatile AudioSample sample;
        private volatile boolean stopped;
        private Process process;
        private float average;

        Reader(Path powershell) {
            this.powershell = powershell;
        }

        @Override
        public void run() {
            try {
                String script = "$meterSource = @'\n" + resource("audio-meter.cs") + "\n'@\n"
                    + "$signalSource = @'\n" + resource("audio-signal.cs") + "\n'@\n"
                    + resource("media-session.ps1");
                String bootstrap = "[Console]::InputEncoding = [System.Text.Encoding]::UTF8; "
                    + "& ([ScriptBlock]::Create([Console]::In.ReadToEnd()))";
                String encoded = Base64.getEncoder().encodeToString(bootstrap.getBytes(StandardCharsets.UTF_16LE));
                var builder = new ProcessBuilder(powershell.toString(), "-NoLogo", "-NoProfile", "-NonInteractive",
                    "-WindowStyle", "Hidden", "-EncodedCommand", encoded);
                builder.environment().put("MARCE_MEDIA_PARENT", Long.toString(ProcessHandle.current().pid()));
                builder.redirectError(ProcessBuilder.Redirect.DISCARD);
                Process helper = builder.start();
                synchronized (this) {
                    if (stopped) {
                        helper.destroyForcibly();
                        return;
                    }
                    process = helper;
                }

                try (var commandInput = helper.outputWriter(StandardCharsets.UTF_8)) {
                    commandInput.write(script);
                }
                try (var output = helper.inputReader(StandardCharsets.UTF_8)) {
                    String line;
                    while (!stopped && (line = output.readLine()) != null) {
                        if (line.startsWith("T")) {
                            updateTrack(line.substring(1));
                        } else if (line.startsWith("A") && track != null) {
                            int separator = line.indexOf(',');
                            float bass = Float.parseFloat(line.substring(1, separator));
                            float beat = Float.parseFloat(line.substring(separator + 1));
                            publish(bass, beat);
                        } else if (line.startsWith("P") && track != null) {
                            float peak = Math.clamp(Float.parseFloat(line.substring(1)), 0, 1);
                            average += (peak - average) * 0.065f;
                            float transientPeak = Math.max(0, peak - average);
                            float level = Math.min(1, 0.48f * peak + 2.3f * transientPeak);
                            float impact = Math.clamp((peak - 0.46f) / 0.43f, 0, 1);
                            float punch = Math.clamp(transientPeak / 0.45f, 0, 1);
                            level += impact * impact * (0.75f + 0.65f * punch);
                            publish(level, Math.min(1.05f, transientPeak));
                        }
                    }
                }
            } catch (Exception error) {
                if (!stopped) {
                    LOGGER.debug("Windows music metadata is unavailable", error);
                }
            } finally {
                stop();
            }
        }

        private void publish(float bass, float beat) {
            long now = System.nanoTime();
            AudioSample previous = sample;
            if (previous != null) {
                float decay = (float) Math.exp(-(now - previous.time()) / 26_000_000.0);
                bass = Math.max(bass, previous.signal().bass() * decay);
                beat = Math.max(beat, previous.signal().beat() * decay);
            }
            sample = new AudioSample(new Signal(bass, beat), now);
        }

        private void updateTrack(String json) {
            Metadata metadata = GSON.fromJson(json, Metadata.class);
            String title = metadata == null ? "" : clean(metadata.title());
            track = title.isBlank() ? null : new Track(title, clean(metadata.artist()), decodeCover(metadata.cover()));
            sample = null;
            average = 0;
        }

        private static BufferedImage decodeCover(String cover) {
            if (cover == null || cover.isBlank() || cover.length() > 1_400_000) {
                return null;
            }
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(cover)))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    return null;
                }
                var imageReader = readers.next();
                try {
                    imageReader.setInput(input, true, true);
                    int size = Math.max(imageReader.getWidth(0), imageReader.getHeight(0));
                    if (size > 4096) {
                        return null;
                    }
                    int stride = Math.max(1, Math.ceilDiv(size, 128));
                    var parameters = imageReader.getDefaultReadParam();
                    parameters.setSourceSubsampling(stride, stride, 0, 0);
                    return imageReader.read(0, parameters);
                } finally {
                    imageReader.dispose();
                }
            } catch (IOException | IllegalArgumentException error) {
                return null;
            }
        }

        private static String resource(String name) throws IOException {
            try (var input = WindowsMusic.class.getResourceAsStream(RESOURCES + name)) {
                if (input == null) {
                    throw new IOException("Missing music resource: " + name);
                }
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        private static String clean(String text) {
            return text == null ? "" : text.replaceAll("\\p{Cntrl}", " ").strip();
        }

        synchronized void stop() {
            stopped = true;
            track = null;
            sample = null;
            if (process != null) {
                process.destroyForcibly();
                process = null;
            }
        }
    }
}
