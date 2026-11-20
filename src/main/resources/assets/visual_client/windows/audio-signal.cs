using System;
using System.Globalization;
using System.Runtime.InteropServices;
using System.Threading;
using System.Threading.Tasks;

public static class MarceAudioSignal
{
    private const int Rate = 44100;
    private const int FramesPerUpdate = 441;
    private static int target;
    private static Capture capture;

    public static bool Available { get { return capture != null && capture.Available; } }

    public static void SetTarget(int pid)
    {
        if (pid == target && (capture == null || capture.Running)) return;
        target = pid;
        if (capture != null) capture.Cancel();
        capture = pid > 0 ? new Capture(pid) : null;
    }

    // Activation and capture run away from the metadata/cover reader.
    private sealed class Capture
    {
        private readonly ManualResetEvent stop = new ManualResetEvent(false);
        private readonly object gate = new object();
        private readonly int pid;
        public volatile bool Available;
        public volatile bool Running = true;

        public Capture(int pid)
        {
            this.pid = pid;
            Thread thread = new Thread(Run);
            thread.IsBackground = true;
            thread.SetApartmentState(ApartmentState.MTA);
            thread.Start();
        }

        public void Cancel()
        {
            lock (gate) { if (Running) stop.Set(); }
        }

        private void Run()
        {
            IAudioClient client = null;
            ICaptureClient samples = null;
            IAsyncOperation operation = null;
            IntPtr arguments = IntPtr.Zero;
            bool started = false;
            using (var dataReady = new AutoResetEvent(false))
            {
                try
                {
                    arguments = Marshal.AllocHGlobal(12);
                    Marshal.WriteInt32(arguments, 0, 1); // Process loopback activation
                    Marshal.WriteInt32(arguments, 4, pid);
                    Marshal.WriteInt32(arguments, 8, 0); // Include the process tree

                    var properties = new ActivationProperty { Kind = 65, Size = 12, Data = arguments }; // VT_BLOB
                    Guid audioInterface = typeof(IAudioClient).GUID;
                    var completion = new ActivationHandler();
                    Check(ActivateAudioInterfaceAsync("VAD\\Process_Loopback", ref audioInterface,
                        ref properties, completion, out operation));
                    if (!completion.Result.Task.Wait(1500)) return;
                    client = completion.Result.Task.Result as IAudioClient;
                    if (client == null || stop.WaitOne(0)) return;

                    var format = new WaveFormat {
                        Tag = 1, Channels = 2, SamplesPerSecond = Rate, BytesPerSecond = Rate * 4,
                        BlockAlign = 4, BitsPerSample = 16
                    };
                    const uint flags = 0x80000000 | 0x40000 | 0x20000; // Convert PCM, events, loopback
                    client.Initialize(0, flags, 0, 0, ref format, IntPtr.Zero);
                    client.SetEventHandle(dataReady.SafeWaitHandle.DangerousGetHandle());
                    Guid sampleInterface = typeof(ICaptureClient).GUID;
                    object service;
                    client.GetService(ref sampleInterface, out service);
                    samples = (ICaptureClient)service;
                    client.Start();
                    started = true;
                    var handles = new WaitHandle[] { stop, dataReady };
                    var signal = new Analyzer();
                    var buffer = new short[FramesPerUpdate * 2];
                    while (WaitHandle.WaitAny(handles, 150) != 0)
                    {
                        uint packet;
                        samples.GetNextPacketSize(out packet);
                        while (packet != 0 && !stop.WaitOne(0))
                        {
                            IntPtr data;
                            uint frames, state;
                            ulong position, timestamp;
                            samples.GetBuffer(out data, out frames, out state, out position, out timestamp);
                            try
                            {
                                for (int offset = 0; offset < frames; )
                                {
                                    int count = Math.Min(FramesPerUpdate, (int)frames - offset);
                                    if ((state & 2) != 0 || data == IntPtr.Zero)
                                        Array.Clear(buffer, 0, count * 2);
                                    else
                                        Marshal.Copy(IntPtr.Add(data, offset * 4), buffer, 0, count * 2);
                                    if (signal.Add(buffer, count))
                                    {
                                        Available |= signal.HasAudio;
                                        if (Available)
                                        {
                                            Console.WriteLine(String.Format(CultureInfo.InvariantCulture,
                                                "A{0:0.0000},{1:0.0000}", signal.Bass, signal.Beat));
                                        }
                                    }
                                    offset += count;
                                }
                            }
                            finally { samples.ReleaseBuffer(frames); }
                            samples.GetNextPacketSize(out packet);
                        }
                    }
                }
                catch (COMException) { }
                catch (EntryPointNotFoundException) { }
                catch (InvalidCastException) { }
                finally
                {
                    Available = false;
                    if (started)
                    {
                        try { client.Stop(); } catch (COMException) { }
                    }
                    Release(samples);
                    Release(client);
                    Release(operation);
                    if (arguments != IntPtr.Zero) Marshal.FreeHGlobal(arguments);
                    lock (gate)
                    {
                        Running = false;
                        stop.Dispose();
                    }
                }
            }
        }
    }

    private sealed class Analyzer
    {
        private readonly Biquad leftLow = new Biquad(150, false);
        private readonly Biquad rightLow = new Biquad(150, false);
        private readonly Biquad leftHigh = new Biquad(32, true);
        private readonly Biquad rightHigh = new Biquad(32, true);
        private double bassEnergy, energy, bassMean, beatMean;
        private int samples;
        public double Bass, Beat;
        public bool HasAudio;

        public bool Add(short[] signal, int frames)
        {
            bool output = false;
            for (int i = 0; i < frames * 2; i += 2)
            {
                double left = signal[i] / 32768.0;
                double right = signal[i + 1] / 32768.0;
                double bassL = leftLow.Filter(leftHigh.Filter(left));
                double bassR = rightLow.Filter(rightHigh.Filter(right));
                bassEnergy += (bassL * bassL + bassR * bassR) * 0.5;
                energy += (left * left + right * right) * 0.5;
                if (++samples < FramesPerUpdate) continue;

                double low = Math.Sqrt(bassEnergy / samples);
                double all = Math.Sqrt(energy / samples);
                HasAudio = all > 0.001;
                double lowHit = Math.Max(0, low - bassMean - 0.003);
                double beatHit = Math.Max(0, all - beatMean - 0.008);
                bassMean += (low - bassMean) * 0.077;
                beatMean += (all - beatMean) * 0.10;
                Bass = Math.Min(2.4, Math.Sqrt(Math.Max(0, low - 0.0025)) * 2.1 + lowHit * 5.0);
                Beat = Math.Min(1.8, beatHit * 8.0 + lowHit * 2.1);
                bassEnergy = energy = 0;
                samples = 0;
                output = true;
            }
            return output;
        }
    }

    private sealed class Biquad
    {
        private readonly double b0, b1, b2, a1, a2;
        private double z1, z2;

        public Biquad(double frequency, bool highPass)
        {
            double angle = 2 * Math.PI * frequency / Rate;
            double cosine = Math.Cos(angle);
            double alpha = Math.Sin(angle) / Math.Sqrt(2);
            double norm = 1 / (1 + alpha);
            b0 = 0.5 * (highPass ? 1 + cosine : 1 - cosine) * norm;
            b1 = (highPass ? -2 : 2) * b0;
            b2 = b0;
            a1 = -2 * cosine * norm;
            a2 = (1 - alpha) * norm;
        }

        public double Filter(double input)
        {
            double output = b0 * input + z1;
            z1 = b1 * input - a1 * output + z2;
            z2 = b2 * input - a2 * output;
            return output;
        }
    }

    private static void Check(int result)
    {
        if (result < 0) Marshal.ThrowExceptionForHR(result);
    }

    private static void Release(object item)
    {
        if (item != null) Marshal.ReleaseComObject(item);
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct ActivationProperty
    {
        public ushort Kind, Reserved1, Reserved2, Reserved3;
        public int Size;
        public IntPtr Data;
    }

    [StructLayout(LayoutKind.Sequential, Pack = 2)]
    private struct WaveFormat
    {
        public ushort Tag, Channels;
        public uint SamplesPerSecond, BytesPerSecond;
        public ushort BlockAlign, BitsPerSample, ExtraSize;
    }

    [DllImport("Mmdevapi.dll", ExactSpelling = true, CharSet = CharSet.Unicode)]
    private static extern int ActivateAudioInterfaceAsync(string path, ref Guid iid,
        ref ActivationProperty properties, IActivationHandler handler, out IAsyncOperation operation);

    [ComImport, Guid("72A22D78-CDE4-431D-B8CC-843A71199B6D"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAsyncOperation
    {
        void GetActivateResult(out int result, [MarshalAs(UnmanagedType.IUnknown)] out object audio);
    }

    [ComImport, Guid("41D949AB-9862-444A-80F6-C261334DA5EB"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IActivationHandler
    {
        void ActivateCompleted(IAsyncOperation operation);
    }

    [ComImport, Guid("94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAgileObject { }

    [ComVisible(true), ClassInterface(ClassInterfaceType.None)]
    private sealed class ActivationHandler : IActivationHandler, IAgileObject
    {
        public readonly TaskCompletionSource<object> Result = new TaskCompletionSource<object>();

        public void ActivateCompleted(IAsyncOperation operation)
        {
            try
            {
                int code;
                object audio;
                operation.GetActivateResult(out code, out audio);
                Result.TrySetResult(code >= 0 ? audio : null);
            }
            catch (COMException) { Result.TrySetResult(null); }
        }
    }

    [ComImport, Guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAudioClient
    {
        void Initialize(int mode, uint flags, long duration, long periodicity, ref WaveFormat format, IntPtr session);
        void UnusedBufferSize();
        void UnusedLatency();
        void UnusedPadding();
        void UnusedIsFormatSupported();
        void UnusedMixFormat();
        void UnusedDevicePeriod();
        void Start();
        void Stop();
        void UnusedReset();
        void SetEventHandle(IntPtr signal);
        void GetService(ref Guid iid, [MarshalAs(UnmanagedType.IUnknown)] out object service);
    }

    [ComImport, Guid("C8ADBD64-E71E-48A0-A4DE-185C395CD317"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface ICaptureClient
    {
        void GetBuffer(out IntPtr data, out uint frames, out uint flags, out ulong position, out ulong timestamp);
        void ReleaseBuffer(uint frames);
        void GetNextPacketSize(out uint frames);
    }
}
