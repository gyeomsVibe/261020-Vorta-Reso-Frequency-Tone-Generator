using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Threading;

namespace VortaAegisPC
{
    public sealed class AudioEndpointInfo
    {
        public string Id;
        public string Name;
        public bool IsDefault;

        public override string ToString()
        {
            return Name ?? "";
        }
    }

    public sealed class WasapiAudioEngine : IDisposable
    {
        private const int CLSCTX_ALL = 23;
        private const int eRender = 0;
        private const int eMultimedia = 1;
        private const int DEVICE_STATE_ACTIVE = 0x00000001;
        private const int STGM_READ = 0x00000000;

        private const int AUDCLNT_SHAREMODE_SHARED = 0;
        private const int AUDCLNT_STREAMFLAGS_EVENTCALLBACK = 0x00040000;
        private const int AUDCLNT_STREAMFLAGS_NOPERSIST = 0x00080000;
        private const int AUDCLNT_E_DEVICE_INVALIDATED = unchecked((int)0x88890004);
        private const uint WAIT_OBJECT_0 = 0;

        private static readonly Guid CLSID_MMDeviceEnumerator = new Guid("BCDE0395-E52F-467C-8E3D-C4579291692E");
        private static readonly Guid IID_IAudioClient = new Guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2");
        private static readonly Guid IID_IAudioRenderClient = new Guid("F294ACFC-3146-4483-A7BF-ADDCA7C260E2");
        private static readonly Guid IID_IAudioSessionControl = new Guid("F4B1A599-7266-4319-A8CA-E70ACB11E8CD");
        private static readonly Guid KSDATAFORMAT_SUBTYPE_IEEE_FLOAT = new Guid("00000003-0000-0010-8000-00AA00389B71");

        private static PROPERTYKEY PKEY_Device_FriendlyName = new PROPERTYKEY
        {
            fmtid = new Guid("A45C254E-DF1C-4EFD-8020-67D146A850E0"),
            pid = 14
        };

        private readonly AegisDspEngine mDsp;
        private readonly AutoResetEvent mWakeSignal = new AutoResetEvent(false);
        private readonly Thread mAudioThread;

        private volatile bool mDisposed;
        private volatile string mSelectedDeviceId = "";
        private volatile bool mReconnectRequested;
        private volatile IntPtr mCurrentAudioEvent = IntPtr.Zero;
        private volatile string mActiveEndpointName = "시스템 기본 출력";
        private volatile string mLastError = "";

        public WasapiAudioEngine(AegisDspEngine dsp)
        {
            mDsp = dsp;
            mAudioThread = new Thread(AudioThreadLoop);
            mAudioThread.Name = "VortaAegisWasapi";
            mAudioThread.IsBackground = true;
            mAudioThread.Priority = ThreadPriority.AboveNormal;
            mAudioThread.SetApartmentState(ApartmentState.MTA);
            mAudioThread.Start();
        }

        public string ActiveEndpointName { get { return mActiveEndpointName; } }
        public string LastError { get { return mLastError; } }

        public void SetOutputDevice(string deviceId)
        {
            string next = deviceId ?? "";
            if (!string.Equals(mSelectedDeviceId, next, StringComparison.Ordinal))
            {
                mSelectedDeviceId = next;
                mReconnectRequested = true;
                PulseAudioWake();
            }
        }

        public void PlayPreset(PresetItem preset)
        {
            mDsp.PlayPreset(preset);
            PulseAudioWake();
        }

        public void StopPlayback()
        {
            mDsp.RequestStop();
            PulseAudioWake();
        }

        public void StopAndWaitFadeOut(int timeoutMs)
        {
            mDsp.RequestStop();
            PulseAudioWake();
            long deadline = Environment.TickCount + timeoutMs;
            while (mDsp.IsActive && Environment.TickCount < deadline)
            {
                Thread.Sleep(25);
            }
        }

        private void PulseAudioWake()
        {
            IntPtr hEvt = mCurrentAudioEvent;
            if (hEvt != IntPtr.Zero)
            {
                SetEvent(hEvt);
            }
            mWakeSignal.Set();
        }

        public static List<AudioEndpointInfo> EnumerateOutputDevices()
        {
            List<AudioEndpointInfo> list = new List<AudioEndpointInfo>();
            AudioEndpointInfo autoEntry = new AudioEndpointInfo();
            autoEntry.Id = "";
            autoEntry.Name = "시스템 기본 출력 (자동 추종)";
            autoEntry.IsDefault = true;
            list.Add(autoEntry);

            IMMDeviceEnumerator enumerator = null;
            IMMDevice defDev = null;
            IMMDeviceCollection coll = null;
            try
            {
                Type t = Type.GetTypeFromCLSID(CLSID_MMDeviceEnumerator);
                enumerator = (IMMDeviceEnumerator)Activator.CreateInstance(t);

                string defId = "";
                if (enumerator.GetDefaultAudioEndpoint(eRender, eMultimedia, out defDev) == 0 && defDev != null)
                {
                    defDev.GetId(out defId);
                }

                if (enumerator.EnumAudioEndpoints(eRender, DEVICE_STATE_ACTIVE, out coll) == 0 && coll != null)
                {
                    uint count = 0;
                    if (coll.GetCount(out count) == 0)
                    {
                        for (uint i = 0; i < count; i++)
                        {
                            IMMDevice dev = null;
                            try
                            {
                                if (coll.Item(i, out dev) == 0 && dev != null)
                                {
                                    string id;
                                    dev.GetId(out id);
                                    string friendly = ReadFriendlyName(dev);
                                    AudioEndpointInfo info = new AudioEndpointInfo();
                                    info.Id = id ?? "";
                                    info.IsDefault = string.Equals(id, defId, StringComparison.OrdinalIgnoreCase);
                                    info.Name = info.IsDefault ? (friendly + " (기본)") : friendly;
                                    list.Add(info);
                                }
                            }
                            finally
                            {
                                SafeReleaseCom(dev);
                            }
                        }
                    }
                }
            }
            catch
            {
                // Keep at least auto-follow item
            }
            finally
            {
                SafeReleaseCom(coll);
                SafeReleaseCom(defDev);
                SafeReleaseCom(enumerator);
            }
            return list;
        }

        private static string ReadFriendlyName(IMMDevice dev)
        {
            IPropertyStore store = null;
            PROPVARIANT pv = new PROPVARIANT();
            try
            {
                if (dev.OpenPropertyStore(STGM_READ, out store) == 0 && store != null)
                {
                    if (store.GetValue(ref PKEY_Device_FriendlyName, out pv) == 0 && pv.vt == 31 && pv.p != IntPtr.Zero)
                    {
                        string val = Marshal.PtrToStringUni(pv.p);
                        if (!string.IsNullOrEmpty(val)) return val;
                    }
                }
            }
            catch
            {
            }
            finally
            {
                PropVariantClear(ref pv);
                SafeReleaseCom(store);
            }
            return "Windows 오디오 장치";
        }

        private static string QueryDefaultEndpointId(IMMDeviceEnumerator enumerator)
        {
            IMMDevice dev = null;
            try
            {
                if (enumerator.GetDefaultAudioEndpoint(eRender, eMultimedia, out dev) == 0 && dev != null)
                {
                    string id;
                    if (dev.GetId(out id) == 0 && id != null) return id;
                }
            }
            catch
            {
            }
            finally
            {
                SafeReleaseCom(dev);
            }
            return "";
        }

        public static string RunSelfTestProbe(AegisDspEngine dsp)
        {
            List<AudioEndpointInfo> devices = EnumerateOutputDevices();
            if (devices.Count < 2)
            {
                throw new InvalidOperationException("No active Windows audio render endpoints found");
            }

            IMMDeviceEnumerator enumerator = null;
            IMMDevice dev = null;
            IAudioClient client = null;
            IAudioRenderClient render = null;
            IntPtr pMix = IntPtr.Zero;
            IntPtr hEvent = IntPtr.Zero;
            try
            {
                Type t = Type.GetTypeFromCLSID(CLSID_MMDeviceEnumerator);
                enumerator = (IMMDeviceEnumerator)Activator.CreateInstance(t);
                int hr = enumerator.GetDefaultAudioEndpoint(eRender, eMultimedia, out dev);
                if (hr != 0 || dev == null) throw new COMException("GetDefaultAudioEndpoint failed", hr);

                string devName = ReadFriendlyName(dev);
                object clientObj;
                Guid iidClient = IID_IAudioClient;
                hr = dev.Activate(ref iidClient, CLSCTX_ALL, IntPtr.Zero, out clientObj);
                if (hr != 0) throw new COMException("Activate IAudioClient failed", hr);
                client = (IAudioClient)clientObj;

                hr = client.GetMixFormat(out pMix);
                if (hr != 0 || pMix == IntPtr.Zero) throw new COMException("GetMixFormat failed", hr);
                WaveFormatInfo fmt = ParseWaveFormat(pMix);

                hr = client.Initialize(AUDCLNT_SHAREMODE_SHARED,
                    AUDCLNT_STREAMFLAGS_EVENTCALLBACK | AUDCLNT_STREAMFLAGS_NOPERSIST,
                    0, 0, pMix, IntPtr.Zero);
                if (hr != 0) throw new COMException("IAudioClient.Initialize failed", hr);

                hEvent = CreateEvent(IntPtr.Zero, false, false, null);
                hr = client.SetEventHandle(hEvent);
                if (hr != 0) throw new COMException("SetEventHandle failed", hr);

                uint bufFrames;
                hr = client.GetBufferSize(out bufFrames);
                if (hr != 0 || bufFrames == 0) throw new COMException("GetBufferSize failed", hr);

                object renderObj;
                Guid iidRender = IID_IAudioRenderClient;
                hr = client.GetService(ref iidRender, out renderObj);
                if (hr != 0) throw new COMException("GetService IAudioRenderClient failed", hr);
                render = (IAudioRenderClient)renderObj;

                TrySetSessionName(client);

                // Verify writing silent render buffer in shared mode without audible pop
                IntPtr pBuf;
                hr = render.GetBuffer(bufFrames, out pBuf);
                if (hr != 0 || pBuf == IntPtr.Zero) throw new COMException("GetBuffer failed", hr);
                float[] scratch = new float[bufFrames * 2];
                WriteInterleavedBuffer(pBuf, scratch, (int)bufFrames, fmt);
                hr = render.ReleaseBuffer(bufFrames, 2); // AUDCLNT_BUFFERFLAGS_SILENT = 2
                if (hr != 0) throw new COMException("ReleaseBuffer failed", hr);

                return string.Format("devices={0} default='{1}' sr={2} ch={3} bits={4} float={5} bufFrames={6}",
                    devices.Count - 1, devName, fmt.SampleRate, fmt.Channels, fmt.BitsPerSample, fmt.IsFloat, bufFrames);
            }
            finally
            {
                if (pMix != IntPtr.Zero) Marshal.FreeCoTaskMem(pMix);
                if (hEvent != IntPtr.Zero) CloseHandle(hEvent);
                SafeReleaseCom(render);
                SafeReleaseCom(client);
                SafeReleaseCom(dev);
                SafeReleaseCom(enumerator);
            }
        }

        private void AudioThreadLoop()
        {
            float[] stereoScratch = new float[8192];

            while (!mDisposed)
            {
                if (!mDsp.IsActive)
                {
                    mWakeSignal.WaitOne(250);
                    continue;
                }

                IMMDeviceEnumerator enumerator = null;
                IMMDevice device = null;
                IAudioClient audioClient = null;
                IAudioRenderClient renderClient = null;
                IntPtr pMixFormat = IntPtr.Zero;
                IntPtr hEvent = IntPtr.Zero;

                try
                {
                    Type enumType = Type.GetTypeFromCLSID(CLSID_MMDeviceEnumerator);
                    enumerator = (IMMDeviceEnumerator)Activator.CreateInstance(enumType);

                    string targetId = mSelectedDeviceId;
                    string openedId = "";
                    int hr;
                    if (!string.IsNullOrEmpty(targetId))
                    {
                        hr = enumerator.GetDevice(targetId, out device);
                        if (hr != 0 || device == null)
                        {
                            hr = enumerator.GetDefaultAudioEndpoint(eRender, eMultimedia, out device);
                        }
                    }
                    else
                    {
                        hr = enumerator.GetDefaultAudioEndpoint(eRender, eMultimedia, out device);
                    }

                    if (hr != 0 || device == null)
                    {
                        mLastError = "Audio endpoint unavailable";
                        Thread.Sleep(500);
                        continue;
                    }

                    device.GetId(out openedId);
                    mActiveEndpointName = ReadFriendlyName(device);

                    object clientObj;
                    Guid iidClient = IID_IAudioClient;
                    hr = device.Activate(ref iidClient, CLSCTX_ALL, IntPtr.Zero, out clientObj);
                    if (hr != 0 || clientObj == null)
                    {
                        mLastError = "IAudioClient activate failed: 0x" + hr.ToString("X");
                        Thread.Sleep(500);
                        continue;
                    }
                    audioClient = (IAudioClient)clientObj;

                    hr = audioClient.GetMixFormat(out pMixFormat);
                    if (hr != 0 || pMixFormat == IntPtr.Zero)
                    {
                        mLastError = "GetMixFormat failed: 0x" + hr.ToString("X");
                        Thread.Sleep(500);
                        continue;
                    }

                    WaveFormatInfo fmt = ParseWaveFormat(pMixFormat);
                    mDsp.SetSampleRate(fmt.SampleRate);

                    hr = audioClient.Initialize(
                        AUDCLNT_SHAREMODE_SHARED,
                        AUDCLNT_STREAMFLAGS_EVENTCALLBACK | AUDCLNT_STREAMFLAGS_NOPERSIST,
                        0, 0, pMixFormat, IntPtr.Zero);
                    if (hr != 0)
                    {
                        mLastError = "IAudioClient.Initialize failed: 0x" + hr.ToString("X");
                        Thread.Sleep(500);
                        continue;
                    }

                    hEvent = CreateEvent(IntPtr.Zero, false, false, null);
                    hr = audioClient.SetEventHandle(hEvent);
                    if (hr != 0)
                    {
                        mLastError = "SetEventHandle failed: 0x" + hr.ToString("X");
                        Thread.Sleep(500);
                        continue;
                    }

                    uint bufferFrames;
                    hr = audioClient.GetBufferSize(out bufferFrames);
                    if (hr != 0 || bufferFrames == 0)
                    {
                        Thread.Sleep(500);
                        continue;
                    }

                    object renderObj;
                    Guid iidRender = IID_IAudioRenderClient;
                    hr = audioClient.GetService(ref iidRender, out renderObj);
                    if (hr != 0 || renderObj == null)
                    {
                        Thread.Sleep(500);
                        continue;
                    }
                    renderClient = (IAudioRenderClient)renderObj;
                    TrySetSessionName(audioClient);

                    if (stereoScratch.Length < (int)bufferFrames * 2)
                    {
                        stereoScratch = new float[bufferFrames * 2];
                    }

                    // Fill initial frame buffer before Start()
                    IntPtr pInitBuf;
                    if (renderClient.GetBuffer(bufferFrames, out pInitBuf) == 0 && pInitBuf != IntPtr.Zero)
                    {
                        mDsp.RenderStereoFloat(stereoScratch, (int)bufferFrames);
                        WriteInterleavedBuffer(pInitBuf, stereoScratch, (int)bufferFrames, fmt);
                        renderClient.ReleaseBuffer(bufferFrames, 0);
                    }

                    mLastError = "";
                    mReconnectRequested = false;
                    mCurrentAudioEvent = hEvent;
                    hr = audioClient.Start();
                    if (hr != 0)
                    {
                        mCurrentAudioEvent = IntPtr.Zero;
                        Thread.Sleep(500);
                        continue;
                    }

                    long nextDefaultCheckTick = Environment.TickCount + 1500;
                    while (!mDisposed && mDsp.IsActive && !mReconnectRequested)
                    {
                        uint waitRes = WaitForSingleObject(hEvent, 300);
                        if (mDisposed || mReconnectRequested) break;
                        if (waitRes != WAIT_OBJECT_0)
                        {
                            // Periodic check for system default device change when in auto-follow mode
                            if (string.IsNullOrEmpty(mSelectedDeviceId) && Environment.TickCount >= nextDefaultCheckTick)
                            {
                                nextDefaultCheckTick = Environment.TickCount + 1500;
                                string curDef = QueryDefaultEndpointId(enumerator);
                                if (!string.IsNullOrEmpty(curDef) && !string.Equals(curDef, openedId, StringComparison.OrdinalIgnoreCase))
                                {
                                    break;
                                }
                            }
                            continue;
                        }

                        uint padding;
                        hr = audioClient.GetCurrentPadding(out padding);
                        if (hr == AUDCLNT_E_DEVICE_INVALIDATED || hr < 0)
                        {
                            break;
                        }

                        uint avail = bufferFrames > padding ? (bufferFrames - padding) : 0;
                        if (avail == 0) continue;

                        if (stereoScratch.Length < (int)avail * 2)
                        {
                            stereoScratch = new float[avail * 2];
                        }

                        IntPtr pData;
                        hr = renderClient.GetBuffer(avail, out pData);
                        if (hr == AUDCLNT_E_DEVICE_INVALIDATED || hr < 0 || pData == IntPtr.Zero)
                        {
                            break;
                        }

                        mDsp.RenderStereoFloat(stereoScratch, (int)avail);
                        WriteInterleavedBuffer(pData, stereoScratch, (int)avail, fmt);
                        hr = renderClient.ReleaseBuffer(avail, 0);
                        if (hr == AUDCLNT_E_DEVICE_INVALIDATED || hr < 0)
                        {
                            break;
                        }

                        if (string.IsNullOrEmpty(mSelectedDeviceId) && Environment.TickCount >= nextDefaultCheckTick)
                        {
                            nextDefaultCheckTick = Environment.TickCount + 1500;
                            string curDef = QueryDefaultEndpointId(enumerator);
                            if (!string.IsNullOrEmpty(curDef) && !string.Equals(curDef, openedId, StringComparison.OrdinalIgnoreCase))
                            {
                                break;
                            }
                        }
                    }

                    mCurrentAudioEvent = IntPtr.Zero;
                    audioClient.Stop();
                }
                catch (Exception ex)
                {
                    mLastError = ex.Message;
                    Thread.Sleep(400);
                }
                finally
                {
                    mCurrentAudioEvent = IntPtr.Zero;
                    if (pMixFormat != IntPtr.Zero) Marshal.FreeCoTaskMem(pMixFormat);
                    if (hEvent != IntPtr.Zero) CloseHandle(hEvent);
                    SafeReleaseCom(renderClient);
                    SafeReleaseCom(audioClient);
                    SafeReleaseCom(device);
                    SafeReleaseCom(enumerator);
                }
            }
        }

        private static void TrySetSessionName(IAudioClient client)
        {
            IAudioSessionControl ctrl = null;
            try
            {
                object obj;
                Guid iid = IID_IAudioSessionControl;
                if (client.GetService(ref iid, out obj) == 0 && obj != null)
                {
                    ctrl = (IAudioSessionControl)obj;
                    ctrl.SetDisplayName("보르타 이지스", IntPtr.Zero);
                }
            }
            catch
            {
            }
            finally
            {
                SafeReleaseCom(ctrl);
            }
        }

        private unsafe static void WriteInterleavedBuffer(IntPtr pData, float[] stereoInterleaved, int frameCount, WaveFormatInfo fmt)
        {
            int ch = fmt.Channels > 0 ? fmt.Channels : 2;
            if (fmt.IsFloat && fmt.BitsPerSample == 32)
            {
                float* dst = (float*)pData.ToPointer();
                if (ch == 2)
                {
                    int sampleCount = frameCount * 2;
                    for (int i = 0; i < sampleCount; i++)
                    {
                        dst[i] = stereoInterleaved[i];
                    }
                }
                else if (ch == 1)
                {
                    for (int f = 0; f < frameCount; f++)
                    {
                        dst[f] = 0.5f * (stereoInterleaved[f * 2] + stereoInterleaved[f * 2 + 1]);
                    }
                }
                else
                {
                    for (int f = 0; f < frameCount; f++)
                    {
                        int baseIdx = f * ch;
                        dst[baseIdx] = stereoInterleaved[f * 2];
                        dst[baseIdx + 1] = stereoInterleaved[f * 2 + 1];
                        for (int c = 2; c < ch; c++)
                        {
                            dst[baseIdx + c] = 0.0f;
                        }
                    }
                }
            }
            else if (fmt.BitsPerSample == 32 && !fmt.IsFloat)
            {
                int* dst = (int*)pData.ToPointer();
                for (int f = 0; f < frameCount; f++)
                {
                    float l = stereoInterleaved[f * 2];
                    float r = stereoInterleaved[f * 2 + 1];
                    if (ch == 1)
                    {
                        dst[f] = (int)(Math.Max(-1.0f, Math.Min(1.0f, 0.5f * (l + r))) * 2147483647.0);
                    }
                    else
                    {
                        int baseIdx = f * ch;
                        dst[baseIdx] = (int)(Math.Max(-1.0f, Math.Min(1.0f, l)) * 2147483647.0);
                        dst[baseIdx + 1] = (int)(Math.Max(-1.0f, Math.Min(1.0f, r)) * 2147483647.0);
                        for (int c = 2; c < ch; c++) dst[baseIdx + c] = 0;
                    }
                }
            }
            else if (fmt.BitsPerSample == 16)
            {
                short* dst = (short*)pData.ToPointer();
                for (int f = 0; f < frameCount; f++)
                {
                    float l = stereoInterleaved[f * 2];
                    float r = stereoInterleaved[f * 2 + 1];
                    if (ch == 1)
                    {
                        dst[f] = (short)Math.Max(-32767, Math.Min(32767, (int)(0.5f * (l + r) * 32767.0f)));
                    }
                    else
                    {
                        int baseIdx = f * ch;
                        dst[baseIdx] = (short)Math.Max(-32767, Math.Min(32767, (int)(l * 32767.0f)));
                        dst[baseIdx + 1] = (short)Math.Max(-32767, Math.Min(32767, (int)(r * 32767.0f)));
                        for (int c = 2; c < ch; c++) dst[baseIdx + c] = 0;
                    }
                }
            }
        }

        private struct WaveFormatInfo
        {
            public int Channels;
            public int SampleRate;
            public int BitsPerSample;
            public bool IsFloat;
        }

        private static WaveFormatInfo ParseWaveFormat(IntPtr pFmt)
        {
            WAVEFORMATEX wf = (WAVEFORMATEX)Marshal.PtrToStructure(pFmt, typeof(WAVEFORMATEX));
            WaveFormatInfo info = new WaveFormatInfo();
            info.Channels = wf.nChannels;
            info.SampleRate = wf.nSamplesPerSec;
            info.BitsPerSample = wf.wBitsPerSample;
            info.IsFloat = (wf.wFormatTag == 3);
            if (wf.wFormatTag == 0xFFFE && wf.cbSize >= 22)
            {
                WAVEFORMATEXTENSIBLE wfx = (WAVEFORMATEXTENSIBLE)Marshal.PtrToStructure(pFmt, typeof(WAVEFORMATEXTENSIBLE));
                info.IsFloat = wfx.SubFormat == KSDATAFORMAT_SUBTYPE_IEEE_FLOAT;
            }
            return info;
        }

        private static void SafeReleaseCom(object obj)
        {
            if (obj != null && Marshal.IsComObject(obj))
            {
                try { Marshal.FinalReleaseComObject(obj); } catch { }
            }
        }

        public void Dispose()
        {
            if (mDisposed) return;
            mDisposed = true;
            mDsp.RequestStop();
            PulseAudioWake();
            if (mAudioThread != null && mAudioThread.IsAlive)
            {
                mAudioThread.Join(1200);
            }
            mWakeSignal.Dispose();
        }

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern IntPtr CreateEvent(IntPtr lpEventAttributes, bool bManualReset, bool bInitialState, string lpName);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern uint WaitForSingleObject(IntPtr hHandle, uint dwMilliseconds);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool SetEvent(IntPtr hHandle);

        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern bool CloseHandle(IntPtr hObject);

        [DllImport("ole32.dll")]
        private static extern int PropVariantClear(ref PROPVARIANT pvar);

        [StructLayout(LayoutKind.Sequential, Pack = 1)]
        private struct WAVEFORMATEX
        {
            public ushort wFormatTag;
            public ushort nChannels;
            public int nSamplesPerSec;
            public int nAvgBytesPerSec;
            public ushort nBlockAlign;
            public ushort wBitsPerSample;
            public ushort cbSize;
        }

        [StructLayout(LayoutKind.Sequential, Pack = 1)]
        private struct WAVEFORMATEXTENSIBLE
        {
            public WAVEFORMATEX Format;
            public ushort wValidBitsPerSample;
            public uint dwChannelMask;
            public Guid SubFormat;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct PROPERTYKEY
        {
            public Guid fmtid;
            public uint pid;
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct PROPVARIANT
        {
            public ushort vt;
            public ushort wReserved1;
            public ushort wReserved2;
            public ushort wReserved3;
            public IntPtr p;
            public int p2;
        }

        [ComImport]
        [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6")]
        [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IMMDeviceEnumerator
        {
            [PreserveSig] int EnumAudioEndpoints(int dataFlow, int dwStateMask, out IMMDeviceCollection ppDevices);
            [PreserveSig] int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice ppEndpoint);
            [PreserveSig] int GetDevice([MarshalAs(UnmanagedType.LPWStr)] string pwstrId, out IMMDevice ppDevice);
            [PreserveSig] int RegisterEndpointNotificationCallback(IntPtr pClient);
            [PreserveSig] int UnregisterEndpointNotificationCallback(IntPtr pClient);
        }

        [ComImport]
        [Guid("0BD7A1BE-7A1A-44DB-8397-CC5392387B5E")]
        [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IMMDeviceCollection
        {
            [PreserveSig] int GetCount(out uint pcDevices);
            [PreserveSig] int Item(uint nDevice, out IMMDevice ppDevice);
        }

        [ComImport]
        [Guid("D666063F-1587-4E43-81F1-B948E807363F")]
        [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IMMDevice
        {
            [PreserveSig] int Activate(ref Guid iid, int dwClsCtx, IntPtr pActivationParams, [MarshalAs(UnmanagedType.IUnknown)] out object ppInterface);
            [PreserveSig] int OpenPropertyStore(int stgmAccess, out IPropertyStore ppProperties);
            [PreserveSig] int GetId([MarshalAs(UnmanagedType.LPWStr)] out string ppstrId);
            [PreserveSig] int GetState(out int pdwState);
        }

        [ComImport]
        [Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99")]
        [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IPropertyStore
        {
            [PreserveSig] int GetCount(out uint cProps);
            [PreserveSig] int GetAt(uint iProp, out PROPERTYKEY pkey);
            [PreserveSig] int GetValue(ref PROPERTYKEY key, out PROPVARIANT pv);
            [PreserveSig] int SetValue(ref PROPERTYKEY key, ref PROPVARIANT propvar);
            [PreserveSig] int Commit();
        }

        [ComImport]
        [Guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2")]
        [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IAudioClient
        {
            [PreserveSig] int Initialize(int ShareMode, int StreamFlags, long hnsBufferDuration, long hnsPeriodicity, IntPtr pFormat, IntPtr AudioSessionGuid);
            [PreserveSig] int GetBufferSize(out uint pNumBufferFrames);
            [PreserveSig] int GetStreamLatency(out long phnsLatency);
            [PreserveSig] int GetCurrentPadding(out uint pNumPaddingFrames);
            [PreserveSig] int IsFormatSupported(int ShareMode, IntPtr pFormat, out IntPtr ppClosestMatch);
            [PreserveSig] int GetMixFormat(out IntPtr ppDeviceFormat);
            [PreserveSig] int GetDevicePeriod(out long phnsDefaultDevicePeriod, out long phnsMinimumDevicePeriod);
            [PreserveSig] int Start();
            [PreserveSig] int Stop();
            [PreserveSig] int Reset();
            [PreserveSig] int SetEventHandle(IntPtr eventHandle);
            [PreserveSig] int GetService(ref Guid riid, [MarshalAs(UnmanagedType.IUnknown)] out object ppv);
        }

        [ComImport]
        [Guid("F294ACFC-3146-4483-A7BF-ADDCA7C260E2")]
        [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IAudioRenderClient
        {
            [PreserveSig] int GetBuffer(uint NumFramesRequested, out IntPtr ppData);
            [PreserveSig] int ReleaseBuffer(uint NumFramesWritten, uint dwFlags);
        }

        [ComImport]
        [Guid("F4B1A599-7266-4319-A8CA-E70ACB11E8CD")]
        [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        private interface IAudioSessionControl
        {
            [PreserveSig] int GetState(out int pRetVal);
            [PreserveSig] int GetDisplayName([MarshalAs(UnmanagedType.LPWStr)] out string pRetVal);
            [PreserveSig] int SetDisplayName([MarshalAs(UnmanagedType.LPWStr)] string Value, IntPtr EventContext);
        }
    }
}
