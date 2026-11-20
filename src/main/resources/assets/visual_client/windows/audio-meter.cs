using System;
using System.Diagnostics;
using System.Runtime.InteropServices;

public static class MarceAudioMeter
{
    // Core Audio COM vtable declarations. Unused slots precede the methods we call.
    [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IDeviceEnumerator
    {
        void UnusedEnum();
        void GetDefaultAudioEndpoint(int flow, int role, out IDevice device);
    }

    [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IDevice
    {
        void Activate(ref Guid iid, int context, IntPtr parameters, [MarshalAs(UnmanagedType.IUnknown)] out object result);
    }

    [ComImport, Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface ISessionManager
    {
        void UnusedControl();
        void UnusedVolume();
        void GetSessionEnumerator(out ISessionEnumerator sessions);
    }

    [ComImport, Guid("E2F5BB11-0570-40CA-ACDD-3AA01277DEE8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface ISessionEnumerator
    {
        void GetCount(out int count);
        void GetSession(int index, [MarshalAs(UnmanagedType.IUnknown)] out object session);
    }

    [ComImport, Guid("BFB7FF88-7239-4FC9-8FA2-07C950BE9C6D"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface ISessionControl
    {
        void GetState(out int state);
        void UnusedDisplayName();
        void UnusedSetDisplayName();
        void UnusedIconPath();
        void UnusedSetIconPath();
        void UnusedGrouping();
        void UnusedSetGrouping();
        void UnusedRegister();
        void UnusedUnregister();
        void UnusedIdentifier();
        void UnusedInstanceIdentifier();
        [PreserveSig] int GetProcessId(out uint processId);
    }

    [ComImport, Guid("C02216F6-8C67-4B5B-9D00-D008E73E0064"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMeter
    {
        void GetPeakValue(out float peak);
    }

    private static readonly Guid EnumeratorClass = new Guid("BCDE0395-E52F-467C-8E3D-C4579291692E");
    private static readonly System.Collections.Generic.List<object> meters = new System.Collections.Generic.List<object>();
    public static int ProcessId { get; private set; }

    public static bool Refresh(string[] processNames)
    {
        int previous = ProcessId;
        Clear();
        float best = -1;
        object enumerator = null, manager = null;
        IDevice device = null;
        ISessionEnumerator sessions = null;
        try
        {
            enumerator = Activator.CreateInstance(Type.GetTypeFromCLSID(EnumeratorClass));
            ((IDeviceEnumerator)enumerator).GetDefaultAudioEndpoint(0, 1, out device);
            Guid iid = typeof(ISessionManager).GUID;
            device.Activate(ref iid, 23, IntPtr.Zero, out manager);
            ((ISessionManager)manager).GetSessionEnumerator(out sessions);
            int count;
            sessions.GetCount(out count);
            for (int index = 0; index < count; index++)
            {
                object session = null;
                try
                {
                    sessions.GetSession(index, out session);
                    ISessionControl control = session as ISessionControl;
                    uint pid;
                    if (control == null || control.GetProcessId(out pid) < 0 || pid == 0 || !(session is IMeter))
                        continue;
                    using (Process process = Process.GetProcessById((int)pid))
                    {
                        if (Array.Exists(processNames, name => String.Equals(name, process.ProcessName, StringComparison.OrdinalIgnoreCase)))
                        {
                            int state;
                            float peak;
                            control.GetState(out state);
                            ((IMeter)session).GetPeakValue(out peak);
                            float score = (state == 1 ? 1 : 0) + peak + ((int)pid == previous ? 0.35f : 0);
                            if (score > best)
                            {
                                best = score;
                                ProcessId = (int)pid;
                            }
                            meters.Add(session);
                            session = null;
                        }
                    }
                }
                catch (ArgumentException) { }
                catch (COMException) { }
                finally { Release(session); }
            }
        }
        catch (COMException) { }
        finally
        {
            Release(sessions);
            Release(manager);
            Release(device);
            Release(enumerator);
        }
        return meters.Count != 0;
    }

    public static float Peak()
    {
        float peak = 0;
        foreach (object session in meters)
        {
            try
            {
                float value;
                ((IMeter)session).GetPeakValue(out value);
                peak = Math.Max(peak, value);
            }
            catch (COMException) { }
        }
        return peak;
    }

    public static void Clear()
    {
        foreach (object session in meters) Release(session);
        meters.Clear();
        ProcessId = 0;
    }

    private static void Release(object item)
    {
        if (item != null) Marshal.ReleaseComObject(item);
    }
}
