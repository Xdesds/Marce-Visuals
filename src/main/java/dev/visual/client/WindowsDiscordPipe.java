package dev.visual.client;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.IntByReference;
import java.io.IOException;

final class WindowsDiscordPipe implements DiscordIpc.Transport {
    private final WinNT.HANDLE handle;
    private final Object io = new Object();
    private volatile boolean closed;

    WindowsDiscordPipe(int index) throws IOException {
        handle = Kernel32.INSTANCE.CreateFile("\\\\.\\pipe\\discord-ipc-" + index,
            WinNT.GENERIC_READ | WinNT.GENERIC_WRITE, 0, null, WinNT.OPEN_EXISTING, 0, null);
        if (WinBase.INVALID_HANDLE_VALUE.equals(handle)) throw new IOException("Discord pipe unavailable");
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        while (!closed) {
            synchronized (io) {
                if (closed) return -1;
                IntByReference available = new IntByReference();
                // Блокирующее чтение мешает записи в тот же pipe. Сначала проверяем, есть ли данные.
                if (!Kernel32.INSTANCE.PeekNamedPipe(handle, null, 0, null, available, null))
                    throw new IOException("Discord pipe disconnected");
                if (available.getValue() > 0) {
                    byte[] buffer = new byte[Math.min(length, available.getValue())];
                    IntByReference received = new IntByReference();
                    if (!Kernel32.INSTANCE.ReadFile(handle, buffer, buffer.length, received, null))
                        throw new IOException("Discord pipe read failed");
                    System.arraycopy(buffer, 0, bytes, offset, received.getValue());
                    return received.getValue();
                }
            }
            try { Thread.sleep(20); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException("IPC read interrupted", error); }
        }
        return -1;
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        synchronized (io) {
            if (closed) throw new IOException("Discord pipe closed");
            IntByReference written = new IntByReference();
            if (!Kernel32.INSTANCE.WriteFile(handle, bytes, bytes.length, written, null) || written.getValue() != bytes.length)
                throw new IOException("Discord pipe write failed");
        }
    }

    @Override
    public void close() {
        synchronized (io) {
            if (closed) return;
            closed = true;
            Kernel32.INSTANCE.CloseHandle(handle);
        }
    }
}
