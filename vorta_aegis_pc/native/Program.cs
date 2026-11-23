using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Globalization;
using System.IO;
using System.IO.Pipes;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows;
using System.Windows.Media.Imaging;
using Forms = System.Windows.Forms;

namespace VortaAegisPC
{
    public sealed class VortaAegisHostApp : IAppHostController
    {
        private const string MUTEX_NAME = "Local\\VortaAegisProPC_v3_Mutex";
        private const string PIPE_NAME = "VortaAegisProPC_v3_Pipe";

        private static readonly int[] QUICK_PRESET_IDS = new int[] { 1, 2, 101, 19, 102, 13, 25, 45 };

        private readonly string mBaseDir;
        private IList<PresetItem> mPresets;
        private IList<PresetPackInfo> mPackInfos;
        private int mSkippedPackCount;
        private string mUserPacksDir;
        private readonly AppStateData mState;
        private readonly AegisDspEngine mDsp;
        private readonly WasapiAudioEngine mAudio;

        private Application mWpfApp;
        private CompanionWindow mMainWin;
        private MiniBarWindow mMiniWin;
        private AboutLicenseWindow mAboutWin;
        private Forms.NotifyIcon mTrayIcon;
        private Forms.ContextMenuStrip mTrayMenu;
        private Forms.ToolStripMenuItem mMenuStatusItem;
        private Forms.ToolStripMenuItem mMenuPlayStopItem;
        private Thread mIpcThread;
        private volatile bool mAllowExit;
        private System.Windows.Threading.DispatcherTimer mSleepTimer;
        private int mSleepRemainingSec;
        private int mSleepPresetMin;
        private DateTime mPlaybackStartedUtc = DateTime.MinValue;
        private string mCachedEndpointName = "시스템 기본 출력";

        public IList<PresetItem> Presets { get { return mPresets; } }
        public IList<PresetPackInfo> PackInfos { get { return mPackInfos; } }
        public string UserPacksDir { get { return mUserPacksDir; } }
        public int SkippedPackCount { get { return mSkippedPackCount; } }
        public AegisDspEngine Dsp { get { return mDsp; } }
        public WasapiAudioEngine Audio { get { return mAudio; } }
        public AppStateData State { get { return mState; } }
        public bool AllowExit { get { return mAllowExit; } }
        public int SleepTimerRemainingSeconds { get { return mSleepRemainingSec; } }
        public int SleepTimerPresetMinutes { get { return mSleepPresetMin; } }
        public string ActiveEndpointDisplayName { get { return mCachedEndpointName; } }

        public long PlaybackElapsedSeconds
        {
            get
            {
                if (!mDsp.IsPlaying || mPlaybackStartedUtc == DateTime.MinValue) return 0;
                return (long)Math.Max(0.0, (DateTime.UtcNow - mPlaybackStartedUtc).TotalSeconds);
            }
        }

        private VortaAegisHostApp(string baseDir)
        {
            mBaseDir = baseDir;
            mUserPacksDir = AppStateStore.GetUserPacksDirectory();
            LoadCatalogInternal();
            mState = AppStateStore.Load();
            mDsp = new AegisDspEngine(48000);
            mDsp.SetVolume(mState.VolumePercent, mState.IsMuted);
            mDsp.SetOutputProfile((PcOutputProfile)mState.OutputProfile);
            mDsp.SetUltrasonicGuardEnabled(mState.UltrasonicGuard);

            mAudio = new WasapiAudioEngine(mDsp);
            mAudio.SetOutputDevice(mState.DeviceId);
            RefreshCachedEndpointName();
        }

        private void LoadCatalogInternal()
        {
            try
            {
                if (!Directory.Exists(mUserPacksDir))
                {
                    Directory.CreateDirectory(mUserPacksDir);
                }
            }
            catch
            {
            }
            CatalogLoadResult res = PresetCatalog.LoadAllWithPacks(mBaseDir, mUserPacksDir);
            mPresets = res.Presets;
            mPackInfos = res.Packs;
            mSkippedPackCount = (res.SkippedFiles != null) ? res.SkippedFiles.Count : 0;
        }

        private void RefreshCachedEndpointName()
        {
            try
            {
                List<AudioEndpointInfo> eps = WasapiAudioEngine.EnumerateOutputDevices();
                string wantId = mState.DeviceId ?? "";
                for (int i = 0; i < eps.Count; i++)
                {
                    if (string.Equals(eps[i].Id, wantId, StringComparison.OrdinalIgnoreCase))
                    {
                        mCachedEndpointName = eps[i].Name;
                        return;
                    }
                }
                if (eps.Count > 0)
                {
                    mCachedEndpointName = eps[0].Name;
                    return;
                }
            }
            catch
            {
            }
            mCachedEndpointName = "시스템 기본 출력";
        }

        [STAThread]
        public static int Main(string[] args)
        {
            string exeDir = AppDomain.CurrentDomain.BaseDirectory;
            bool selfTest = false;
            bool startMinimized = false;
            string captureLivePath = null;
            string captureAboutPath = null;
            int capW = 0;
            int capH = 0;
            int capPlayId = 0;
            if (args != null)
            {
                for (int i = 0; i < args.Length; i++)
                {
                    if (string.Equals(args[i], "--self-test", StringComparison.OrdinalIgnoreCase)) selfTest = true;
                    if (string.Equals(args[i], "--start-minimized", StringComparison.OrdinalIgnoreCase)) startMinimized = true;
                    if (string.Equals(args[i], "--capture-live", StringComparison.OrdinalIgnoreCase) && i + 1 < args.Length)
                    {
                        captureLivePath = args[i + 1];
                        i++;
                    }
                    else if (string.Equals(args[i], "--capture-about", StringComparison.OrdinalIgnoreCase) && i + 1 < args.Length)
                    {
                        captureAboutPath = args[i + 1];
                        i++;
                    }
                    else if (string.Equals(args[i], "--capture-size", StringComparison.OrdinalIgnoreCase) && i + 1 < args.Length)
                    {
                        string[] parts = args[i + 1].ToLowerInvariant().Split('x');
                        if (parts.Length == 2)
                        {
                            int wTmp;
                            int hTmp;
                            if (int.TryParse(parts[0], NumberStyles.Integer, CultureInfo.InvariantCulture, out wTmp)
                                && int.TryParse(parts[1], NumberStyles.Integer, CultureInfo.InvariantCulture, out hTmp))
                            {
                                capW = wTmp;
                                capH = hTmp;
                            }
                        }
                        i++;
                    }
                    else if (string.Equals(args[i], "--capture-play", StringComparison.OrdinalIgnoreCase) && i + 1 < args.Length)
                    {
                        int pTmp;
                        if (int.TryParse(args[i + 1], NumberStyles.Integer, CultureInfo.InvariantCulture, out pTmp))
                        {
                            capPlayId = pTmp;
                        }
                        i++;
                    }
                }
            }

            if (selfTest)
            {
                return RunSelfTest(exeDir);
            }

            bool createdNew;
            Mutex singleMutex = new Mutex(true, MUTEX_NAME, out createdNew);
            if (!createdNew)
            {
                string msg = "SHOW\n";
                if (!string.IsNullOrEmpty(captureAboutPath))
                {
                    msg = "CAPTURE_ABOUT:" + captureAboutPath + "\n";
                }
                else if (!string.IsNullOrEmpty(captureLivePath))
                {
                    msg = string.Format(CultureInfo.InvariantCulture, "CAPTURE_EX:{0}:{1}:{2}:{3}\n", capW, capH, capPlayId, captureLivePath);
                }
                SignalExistingInstance(msg);
                singleMutex.Dispose();
                return 0;
            }
            if (!string.IsNullOrEmpty(captureLivePath) || !string.IsNullOrEmpty(captureAboutPath))
            {
                singleMutex.Dispose();
                return 2;
            }

            try
            {
                VortaAegisHostApp host = new VortaAegisHostApp(exeDir);
                host.RunInteractive(startMinimized);
                return 0;
            }
            finally
            {
                try { singleMutex.ReleaseMutex(); } catch { }
                singleMutex.Dispose();
            }
        }

        private static void SignalExistingInstance(string message)
        {
            try
            {
                using (NamedPipeClientStream client = new NamedPipeClientStream(".", PIPE_NAME, PipeDirection.Out))
                {
                    client.Connect(1000);
                    byte[] payload = Encoding.UTF8.GetBytes(message ?? "SHOW\n");
                    client.Write(payload, 0, payload.Length);
                    client.Flush();
                }
            }
            catch
            {
            }
        }

        private void RunInteractive(bool startMinimized)
        {
            mWpfApp = new Application();
            mWpfApp.ShutdownMode = ShutdownMode.OnExplicitShutdown;

            mMainWin = new CompanionWindow(this);
            mMiniWin = new MiniBarWindow(this);

            SetupTrayIcon();
            StartIpcServer();

            if (!startMinimized)
            {
                ShowActiveWindow();
            }

            mWpfApp.Run();
        }

        private void SetupTrayIcon()
        {
            mTrayMenu = new Forms.ContextMenuStrip();
            mTrayMenu.Renderer = new DarkMenuRenderer();
            mTrayMenu.ForeColor = Color.FromArgb(242, 243, 245);
            mTrayMenu.BackColor = Color.FromArgb(23, 24, 28);
            mTrayMenu.Opening += delegate { RebuildDynamicTrayMenu(); };

            mTrayIcon = new Forms.NotifyIcon();
            string icoPath = Path.Combine(mBaseDir, "assets", "vorta_aegis.ico");
            if (File.Exists(icoPath))
            {
                mTrayIcon.Icon = new Icon(icoPath);
            }
            else
            {
                mTrayIcon.Icon = SystemIcons.Application;
            }
            mTrayIcon.Text = "보르타 이지스 · 대기 중";
            mTrayIcon.ContextMenuStrip = mTrayMenu;
            mTrayIcon.MouseClick += delegate(object s, Forms.MouseEventArgs e)
            {
                if (e.Button == Forms.MouseButtons.Left)
                {
                    ToggleVisibleFromTray();
                }
            };
            RebuildDynamicTrayMenu();
            mTrayIcon.Visible = true;
        }

        private void RebuildDynamicTrayMenu()
        {
            mTrayMenu.Items.Clear();

            bool playing = mDsp.IsPlaying;
            PresetItem cur = playing ? mDsp.CurrentPreset : null;

            mMenuStatusItem = new Forms.ToolStripMenuItem();
            mMenuStatusItem.Enabled = false;
            mMenuStatusItem.Text = (playing && cur != null)
                ? string.Format(CultureInfo.InvariantCulture, "재생 중 · {0} ({1:F1} Hz)", cur.Title, mDsp.LiveFreqHz > 0f ? mDsp.LiveFreqHz : cur.Freq1)
                : string.Format(CultureInfo.InvariantCulture, "대기 중 · 볼륨 {0}%", mDsp.VolumePercent);
            mTrayMenu.Items.Add(mMenuStatusItem);

            mTrayMenu.Items.Add(new Forms.ToolStripSeparator());

            mMenuPlayStopItem = new Forms.ToolStripMenuItem(playing ? "재생 중지" : "마지막 프리셋 재생");
            mMenuPlayStopItem.Click += delegate
            {
                if (mDsp.IsPlaying) StopPlayback();
                else
                {
                    PresetItem p = PresetCatalog.FindById(mPresets, mState.LastPresetId);
                    if (p != null) PlayPreset(p);
                }
            };
            mTrayMenu.Items.Add(mMenuPlayStopItem);

            Forms.ToolStripMenuItem showItem = new Forms.ToolStripMenuItem(IsAnyWindowVisible() ? "트레이로 숨기기" : "컨트롤 패널 열기");
            showItem.Click += delegate { ToggleVisibleFromTray(); };
            mTrayMenu.Items.Add(showItem);

            Forms.ToolStripMenuItem miniItem = new Forms.ToolStripMenuItem(mState.MiniMode ? "전체 패널 보기" : "미니 플로팅 바");
            miniItem.Click += delegate
            {
                SwitchToMiniMode(!mState.MiniMode);
                ShowActiveWindow();
            };
            mTrayMenu.Items.Add(miniItem);

            Forms.ToolStripMenuItem quickMenu = new Forms.ToolStripMenuItem("빠른 프리셋");
            for (int i = 0; i < QUICK_PRESET_IDS.Length; i++)
            {
                PresetItem qp = PresetCatalog.FindById(mPresets, QUICK_PRESET_IDS[i]);
                if (qp == null || qp.Id != QUICK_PRESET_IDS[i]) continue;
                Forms.ToolStripMenuItem qi = new Forms.ToolStripMenuItem(qp.ShortCode + " · " + qp.Title);
                qi.Checked = (cur != null && cur.Id == qp.Id);
                PresetItem captured = qp;
                qi.Click += delegate { PlayPreset(captured); };
                quickMenu.DropDownItems.Add(qi);
            }
            mTrayMenu.Items.Add(quickMenu);

            Forms.ToolStripMenuItem favMenu = new Forms.ToolStripMenuItem("즐겨찾기");
            if (mState.FavoriteIds == null || mState.FavoriteIds.Count == 0)
            {
                Forms.ToolStripMenuItem emptyFav = new Forms.ToolStripMenuItem("(카드 별 아이콘으로 추가)");
                emptyFav.Enabled = false;
                favMenu.DropDownItems.Add(emptyFav);
            }
            else
            {
                for (int i = 0; i < mState.FavoriteIds.Count; i++)
                {
                    PresetItem fp = PresetCatalog.FindById(mPresets, mState.FavoriteIds[i]);
                    if (fp == null || fp.Id != mState.FavoriteIds[i]) continue;
                    Forms.ToolStripMenuItem fi = new Forms.ToolStripMenuItem(fp.ShortCode + " · " + fp.Title);
                    fi.Checked = (cur != null && cur.Id == fp.Id);
                    PresetItem capturedFav = fp;
                    fi.Click += delegate { PlayPreset(capturedFav); };
                    favMenu.DropDownItems.Add(fi);
                }
            }
            mTrayMenu.Items.Add(favMenu);

            Forms.ToolStripMenuItem volMenu = new Forms.ToolStripMenuItem("볼륨 (" + (mDsp.IsMuted ? "음소거" : mDsp.VolumePercent + "%") + ")");
            Forms.ToolStripMenuItem muteSub = new Forms.ToolStripMenuItem(mDsp.IsMuted ? "음소거 해제" : "음소거");
            muteSub.Click += delegate
            {
                if (mDsp.IsMuted)
                {
                    int pct = mDsp.VolumePercent == 0 ? VolumeCurve.DEFAULT_PERCENT : mDsp.VolumePercent;
                    ApplyVolume(pct, false);
                }
                else
                {
                    ApplyVolume(mDsp.VolumePercent, true);
                }
            };
            volMenu.DropDownItems.Add(muteSub);
            int[] trayVols = new int[] { 15, 30, 50, 80, 100, 120 };
            for (int i = 0; i < trayVols.Length; i++)
            {
                int v = trayVols[i];
                Forms.ToolStripMenuItem vi = new Forms.ToolStripMenuItem(v == 120 ? "120% (클린 증폭)" : (v + "%"));
                vi.Checked = (!mDsp.IsMuted && mDsp.VolumePercent == v);
                vi.Click += delegate { ApplyVolume(v, false); };
                volMenu.DropDownItems.Add(vi);
            }
            mTrayMenu.Items.Add(volMenu);

            Forms.ToolStripMenuItem timerMenu = new Forms.ToolStripMenuItem(
                mSleepRemainingSec > 0
                    ? string.Format(CultureInfo.InvariantCulture, "자동 정지 타이머 ({0:D2}:{1:D2})", mSleepRemainingSec / 60, mSleepRemainingSec % 60)
                    : "자동 정지 타이머");
            int[] timerOpts = new int[] { 0, 15, 30, 60, 90 };
            for (int i = 0; i < timerOpts.Length; i++)
            {
                int mins = timerOpts[i];
                Forms.ToolStripMenuItem ti = new Forms.ToolStripMenuItem(mins == 0 ? "끄기" : (mins + "분 후 자동 정지"));
                ti.Checked = (mSleepPresetMin == mins);
                ti.Click += delegate { SetSleepTimerMinutes(mins); };
                timerMenu.DropDownItems.Add(ti);
            }
            mTrayMenu.Items.Add(timerMenu);

            Forms.ToolStripMenuItem profMenu = new Forms.ToolStripMenuItem("PC 음향 보정");
            string[] profNames = new string[] { "PC 스피커 보정 (권장)", "이어폰·헤드폰", "순수 원음·DAC" };
            for (int i = 0; i < 3; i++)
            {
                PcOutputProfile prof = (PcOutputProfile)i;
                Forms.ToolStripMenuItem pi = new Forms.ToolStripMenuItem(profNames[i]);
                pi.Checked = (mDsp.OutputProfile == prof);
                pi.Click += delegate { ApplyOutputProfile(prof); };
                profMenu.DropDownItems.Add(pi);
            }
            mTrayMenu.Items.Add(profMenu);

            mTrayMenu.Items.Add(new Forms.ToolStripSeparator());

            Forms.ToolStripMenuItem packMenu = new Forms.ToolStripMenuItem("확장 팩 관리");
            Forms.ToolStripMenuItem packOpenItem = new Forms.ToolStripMenuItem("확장 팩 폴더 열기");
            packOpenItem.Click += delegate { OpenPacksFolder(); };
            packMenu.DropDownItems.Add(packOpenItem);
            Forms.ToolStripMenuItem packReloadItem = new Forms.ToolStripMenuItem("확장 팩 새로고침");
            packReloadItem.Click += delegate { ReloadPacks(); };
            packMenu.DropDownItems.Add(packReloadItem);
            mTrayMenu.Items.Add(packMenu);

            Forms.ToolStripMenuItem aboutItem = new Forms.ToolStripMenuItem("보르타 정보 · 업데이트 · 라이선스");
            aboutItem.Click += delegate { ShowAboutDialog(); };
            mTrayMenu.Items.Add(aboutItem);

            mTrayMenu.Items.Add(new Forms.ToolStripSeparator());

            Forms.ToolStripMenuItem exitItem = new Forms.ToolStripMenuItem("완전 종료");
            exitItem.Click += delegate { ShutdownCompletely(); };
            mTrayMenu.Items.Add(exitItem);
        }

        private void StartIpcServer()
        {
            mIpcThread = new Thread(delegate()
            {
                while (!mAllowExit)
                {
                    try
                    {
                        using (NamedPipeServerStream server = new NamedPipeServerStream(PIPE_NAME, PipeDirection.In, 1))
                        {
                            server.WaitForConnection();
                            if (mAllowExit) break;
                            byte[] buf = new byte[1024];
                            int read = server.Read(buf, 0, buf.Length);
                            if (read > 0 && mWpfApp != null)
                            {
                                string cmd = Encoding.UTF8.GetString(buf, 0, read).Trim();
                                if (cmd.StartsWith("CAPTURE_ABOUT:", StringComparison.Ordinal))
                                {
                                    string outPath = cmd.Substring("CAPTURE_ABOUT:".Length).Trim();
                                    mWpfApp.Dispatcher.BeginInvoke(new Action(delegate { CaptureAboutBitmap(outPath); }));
                                }
                                else if (cmd.StartsWith("CAPTURE_EX:", StringComparison.Ordinal))
                                {
                                    string rest = cmd.Substring("CAPTURE_EX:".Length);
                                    int c1 = rest.IndexOf(':');
                                    int c2 = (c1 >= 0) ? rest.IndexOf(':', c1 + 1) : -1;
                                    int c3 = (c2 >= 0) ? rest.IndexOf(':', c2 + 1) : -1;
                                    if (c1 > 0 && c2 > c1 && c3 > c2)
                                    {
                                        int reqW;
                                        int reqH;
                                        int playId;
                                        int.TryParse(rest.Substring(0, c1), NumberStyles.Integer, CultureInfo.InvariantCulture, out reqW);
                                        int.TryParse(rest.Substring(c1 + 1, c2 - c1 - 1), NumberStyles.Integer, CultureInfo.InvariantCulture, out reqH);
                                        int.TryParse(rest.Substring(c2 + 1, c3 - c2 - 1), NumberStyles.Integer, CultureInfo.InvariantCulture, out playId);
                                        string outPath = rest.Substring(c3 + 1).Trim();
                                        mWpfApp.Dispatcher.BeginInvoke(new Action(delegate { CapturePanelBitmapEx(outPath, reqW, reqH, playId); }));
                                    }
                                }
                                else if (cmd.StartsWith("CAPTURE:", StringComparison.Ordinal))
                                {
                                    string outPath = cmd.Substring("CAPTURE:".Length).Trim();
                                    mWpfApp.Dispatcher.BeginInvoke(new Action(delegate { CapturePanelBitmapEx(outPath, 0, 0, 0); }));
                                }
                                else
                                {
                                    mWpfApp.Dispatcher.BeginInvoke(new Action(ShowActiveWindow));
                                }
                            }
                        }
                    }
                    catch
                    {
                        if (mAllowExit) break;
                        Thread.Sleep(200);
                    }
                }
            });
            mIpcThread.IsBackground = true;
            mIpcThread.Start();
        }

        private void CaptureAboutBitmap(string outPath)
        {
            if (string.IsNullOrEmpty(outPath)) return;
            try
            {
                ShowActiveWindow();
                ShowAboutDialog();
                System.Windows.Threading.DispatcherTimer t = new System.Windows.Threading.DispatcherTimer();
                t.Interval = TimeSpan.FromMilliseconds(260);
                t.Tick += delegate
                {
                    t.Stop();
                    try
                    {
                        if (mAboutWin != null && mAboutWin.IsVisible)
                        {
                            mAboutWin.UpdateLayout();
                            RenderAnyWindowToPng(mAboutWin, outPath);
                        }
                    }
                    finally
                    {
                        if (mAboutWin != null)
                        {
                            mAboutWin.Close();
                            mAboutWin = null;
                        }
                    }
                };
                t.Start();
            }
            catch
            {
            }
        }

        private void CapturePanelBitmapEx(string outPath, int reqW, int reqH, int playPresetId)
        {
            if (string.IsNullOrEmpty(outPath) || mMainWin == null) return;
            try
            {
                mState.MiniMode = false;
                ShowActiveWindow();
                if (reqW > 0 && reqH > 0)
                {
                    mMainWin.WindowState = WindowState.Normal;
                    mMainWin.Width = reqW;
                    mMainWin.Height = reqH;
                    Rect wa = SystemParameters.WorkArea;
                    if (mMainWin.Left + reqW > wa.Right) mMainWin.Left = Math.Max(wa.Left, wa.Right - reqW - 12);
                    if (mMainWin.Top + reqH > wa.Bottom) mMainWin.Top = Math.Max(wa.Top, wa.Bottom - reqH - 12);
                    mMainWin.ApplyResponsiveLayoutNow();
                }
                mMainWin.UpdateLayout();

                if (playPresetId > 0)
                {
                    int savedLastId = mState.LastPresetId;
                    PresetItem target = PresetCatalog.FindById(mPresets, playPresetId);
                    if (target != null)
                    {
                        PlayPreset(target);
                        mMainWin.JumpToPlayingPreset();
                    }
                    System.Windows.Threading.DispatcherTimer capTimer = new System.Windows.Threading.DispatcherTimer();
                    capTimer.Interval = TimeSpan.FromMilliseconds(1650);
                    capTimer.Tick += delegate
                    {
                        capTimer.Stop();
                        try
                        {
                            mMainWin.UpdateScopeVisualsNow();
                            mMainWin.UpdateLayout();
                            RenderAnyWindowToPng(mMainWin, outPath);
                        }
                        finally
                        {
                            StopPlayback();
                            mState.LastPresetId = savedLastId;
                            AppStateStore.Save(mState);
                            if (mMainWin.IsVisible) mMainWin.RefreshDashboard(false);
                        }
                    };
                    capTimer.Start();
                }
                else
                {
                    RenderAnyWindowToPng(mMainWin, outPath);
                }
            }
            catch
            {
            }
        }

        private static void RenderAnyWindowToPng(Window win, string outPath)
        {
            int w = Math.Max(1, (int)Math.Ceiling(win.ActualWidth));
            int h = Math.Max(1, (int)Math.Ceiling(win.ActualHeight));
            RenderTargetBitmap rtb = new RenderTargetBitmap(w, h, 96, 96, System.Windows.Media.PixelFormats.Pbgra32);
            rtb.Render(win);
            PngBitmapEncoder enc = new PngBitmapEncoder();
            enc.Frames.Add(BitmapFrame.Create(rtb));
            string tmp = outPath + ".tmp";
            using (FileStream fs = File.Create(tmp))
            {
                enc.Save(fs);
            }
            if (File.Exists(outPath)) File.Delete(outPath);
            File.Move(tmp, outPath);
        }

        private bool IsAnyWindowVisible()
        {
            return (mMainWin != null && mMainWin.IsVisible) || (mMiniWin != null && mMiniWin.IsVisible);
        }

        private void ShowActiveWindow()
        {
            if (mState.MiniMode)
            {
                if (mMainWin.IsVisible) mMainWin.Hide();
                mMiniWin.Show();
                mMiniWin.Activate();
                mMiniWin.SyncMini();
            }
            else
            {
                if (mMiniWin.IsVisible) mMiniWin.Hide();
                mMainWin.ShowInTaskbar = true;
                mMainWin.Show();
                if (mMainWin.WindowState == WindowState.Minimized)
                {
                    mMainWin.WindowState = WindowState.Normal;
                }
                mMainWin.Activate();
                mMainWin.SyncAllFromController(true);
            }
        }

        private void ToggleVisibleFromTray()
        {
            if (IsAnyWindowVisible())
            {
                HideToTray();
            }
            else
            {
                ShowActiveWindow();
            }
        }

        private void UpdateTrayTooltip()
        {
            if (mTrayIcon == null) return;
            string tip;
            if (mDsp.IsPlaying && mDsp.CurrentPreset != null)
            {
                if (mSleepRemainingSec > 0)
                {
                    tip = string.Format(CultureInfo.InvariantCulture, "보르타 · {0} ({1}%) {2:D2}:{3:D2}",
                        mDsp.CurrentPreset.ShortCode, mDsp.VolumePercent, mSleepRemainingSec / 60, mSleepRemainingSec % 60);
                }
                else
                {
                    tip = string.Format(CultureInfo.InvariantCulture, "보르타 이지스 · {0} ({1}%)", mDsp.CurrentPreset.ShortCode, mDsp.VolumePercent);
                }
            }
            else
            {
                tip = "보르타 이지스 · 대기 중";
            }
            if (tip.Length >= 63) tip = tip.Substring(0, 62);
            mTrayIcon.Text = tip;
        }

        public void PlayPreset(PresetItem preset)
        {
            if (preset == null) return;
            mPlaybackStartedUtc = DateTime.UtcNow;
            mState.LastTab = preset.TabIndex;
            mState.LastPresetId = preset.Id;
            if (mState.RecentPresetIds == null) mState.RecentPresetIds = new List<int>();
            mState.RecentPresetIds.Remove(preset.Id);
            mState.RecentPresetIds.Insert(0, preset.Id);
            if (mState.RecentPresetIds.Count > 8)
            {
                mState.RecentPresetIds.RemoveRange(8, mState.RecentPresetIds.Count - 8);
            }
            AppStateStore.Save(mState);
            mAudio.PlayPreset(preset);
            UpdateTrayTooltip();
            if (mMainWin != null && mMainWin.IsVisible) mMainWin.RefreshDashboard(false);
            if (mMiniWin != null && mMiniWin.IsVisible) mMiniWin.SyncMini();
        }

        public void PlayAdjacentPreset(int delta)
        {
            if (mPresets == null || mPresets.Count == 0) return;
            int refId = (mDsp.IsPlaying && mDsp.CurrentPreset != null) ? mDsp.CurrentPreset.Id : mState.LastPresetId;
            int idx = 0;
            for (int i = 0; i < mPresets.Count; i++)
            {
                if (mPresets[i].Id == refId)
                {
                    idx = i;
                    break;
                }
            }
            int count = mPresets.Count;
            int nextIdx = ((idx + delta) % count + count) % count;
            PlayPreset(mPresets[nextIdx]);
        }

        public bool IsFavorite(int presetId)
        {
            return mState.FavoriteIds != null && mState.FavoriteIds.Contains(presetId);
        }

        public void ToggleFavorite(int presetId)
        {
            if (presetId < 1 || presetId > 99999) return;
            if (mState.FavoriteIds == null) mState.FavoriteIds = new List<int>();
            if (mState.FavoriteIds.Contains(presetId)) mState.FavoriteIds.Remove(presetId);
            else mState.FavoriteIds.Add(presetId);
            AppStateStore.Save(mState);
            if (mMainWin != null && mMainWin.IsVisible) mMainWin.RefreshFavoritesUi();
        }

        public void SetSleepTimerMinutes(int minutes)
        {
            if (mSleepTimer != null) mSleepTimer.Stop();
            if (minutes <= 0)
            {
                mSleepPresetMin = 0;
                mSleepRemainingSec = 0;
            }
            else
            {
                mSleepPresetMin = minutes;
                mSleepRemainingSec = minutes * 60;
                if (mSleepTimer == null)
                {
                    mSleepTimer = new System.Windows.Threading.DispatcherTimer();
                    mSleepTimer.Interval = TimeSpan.FromSeconds(1);
                    mSleepTimer.Tick += delegate
                    {
                        if (mSleepRemainingSec > 0) mSleepRemainingSec--;
                        if (mSleepRemainingSec <= 0)
                        {
                            if (mSleepTimer != null) mSleepTimer.Stop();
                            mSleepPresetMin = 0;
                            mSleepRemainingSec = 0;
                            StopPlayback();
                            return;
                        }
                        UpdateTrayTooltip();
                        if (mMainWin != null && mMainWin.IsVisible) mMainWin.RefreshSleepTimerUi();
                        if (mMiniWin != null && mMiniWin.IsVisible) mMiniWin.SyncMini();
                    };
                }
                mSleepTimer.Start();
            }
            UpdateTrayTooltip();
            if (mMainWin != null && mMainWin.IsVisible) mMainWin.RefreshSleepTimerUi();
            if (mMiniWin != null && mMiniWin.IsVisible) mMiniWin.SyncMini();
        }

        public void StopPlayback()
        {
            mPlaybackStartedUtc = DateTime.MinValue;
            if (mSleepTimer != null) mSleepTimer.Stop();
            mSleepPresetMin = 0;
            mSleepRemainingSec = 0;
            mAudio.StopPlayback();
            UpdateTrayTooltip();
            if (mMainWin != null && mMainWin.IsVisible)
            {
                mMainWin.RefreshSleepTimerUi();
                mMainWin.RefreshDashboard(false);
            }
            if (mMiniWin != null && mMiniWin.IsVisible) mMiniWin.SyncMini();
        }

        public void ApplyVolume(int percent, bool muted)
        {
            int clamped = VolumeCurve.ClampPercent(percent);
            mState.VolumePercent = clamped;
            mState.IsMuted = muted;
            mDsp.SetVolume(clamped, muted);
            AppStateStore.Save(mState);
            UpdateTrayTooltip();
            if (mMainWin != null && mMainWin.IsVisible) mMainWin.RefreshVolumeControls();
            if (mMiniWin != null && mMiniWin.IsVisible) mMiniWin.SyncMini();
        }

        public void ApplyOutputProfile(PcOutputProfile profile)
        {
            mState.OutputProfile = (int)profile;
            mDsp.SetOutputProfile(profile);
            AppStateStore.Save(mState);
            if (mMainWin != null && mMainWin.IsVisible)
            {
                mMainWin.RefreshProfileControls();
                mMainWin.RefreshDashboard(false);
            }
        }

        public void ApplyDevice(string deviceId)
        {
            mState.DeviceId = deviceId ?? "";
            AppStateStore.Save(mState);
            mAudio.SetOutputDevice(mState.DeviceId);
            RefreshCachedEndpointName();
            if (mMainWin != null && mMainWin.IsVisible)
            {
                mMainWin.RefreshDashboard(false);
            }
        }

        public void ApplyUltrasonicGuard(bool enabled)
        {
            mState.UltrasonicGuard = enabled;
            mDsp.SetUltrasonicGuardEnabled(enabled);
            AppStateStore.Save(mState);
            if (mMainWin != null && mMainWin.IsVisible)
            {
                mMainWin.RefreshProfileControls();
                mMainWin.RefreshDashboard(false);
            }
        }

        public void SetScopeShowComposite(bool showComposite)
        {
            mState.ScopeShowComposite = showComposite;
            AppStateStore.Save(mState);
        }

        public void SetPackFilter(string packId)
        {
            mState.PackFilterId = packId ?? "";
            AppStateStore.Save(mState);
        }

        public void ReloadPacks()
        {
            LoadCatalogInternal();
            if (mMainWin != null && mMainWin.IsVisible)
            {
                mMainWin.RefreshPackCatalogUi();
            }
        }

        public void OpenPacksFolder()
        {
            try
            {
                if (!Directory.Exists(mUserPacksDir)) Directory.CreateDirectory(mUserPacksDir);
                Process.Start("explorer.exe", mUserPacksDir);
            }
            catch
            {
            }
        }

        public void ShowAboutDialog()
        {
            try
            {
                if (mAboutWin != null && mAboutWin.IsVisible)
                {
                    mAboutWin.Activate();
                    return;
                }
                Window owner = (mMainWin != null && mMainWin.IsVisible) ? (Window)mMainWin : null;
                mAboutWin = new AboutLicenseWindow(this, owner);
                mAboutWin.Closed += delegate { mAboutWin = null; };
                mAboutWin.Show();
                mAboutWin.Activate();
            }
            catch
            {
            }
        }

        public void OpenDocumentationFile(string kind)
        {
            try
            {
                string target = null;
                if (string.Equals(kind, "license", StringComparison.OrdinalIgnoreCase))
                {
                    target = Path.Combine(mBaseDir, "LICENSE");
                }
                else if (string.Equals(kind, "notice", StringComparison.OrdinalIgnoreCase))
                {
                    target = Path.Combine(mBaseDir, "NOTICE");
                }
                else if (string.Equals(kind, "roadmap", StringComparison.OrdinalIgnoreCase))
                {
                    string doc = Path.GetFullPath(Path.Combine(mBaseDir, "..", "docs", "보르타_상용화_라이선스_및_정식인증_가이드.md"));
                    target = File.Exists(doc) ? doc : AppProductInfo.GITHUB_REPO_URL;
                }
                else if (string.Equals(kind, "repo", StringComparison.OrdinalIgnoreCase))
                {
                    target = AppProductInfo.GITHUB_REPO_URL;
                }
                else if (!string.IsNullOrEmpty(kind) && (kind.StartsWith("https://", StringComparison.OrdinalIgnoreCase) || kind.StartsWith("http://", StringComparison.OrdinalIgnoreCase)))
                {
                    target = kind;
                }

                if (!string.IsNullOrEmpty(target))
                {
                    ProcessStartInfo psi = new ProcessStartInfo(target);
                    psi.UseShellExecute = true;
                    Process.Start(psi);
                }
            }
            catch
            {
            }
        }

        public void SaveLastTab(int tabIndex)
        {
            mState.LastTab = TabSyncPolicy.ClampTabIndex(tabIndex);
            AppStateStore.Save(mState);
        }

        public void SwitchToMiniMode(bool mini)
        {
            mState.MiniMode = mini;
            AppStateStore.Save(mState);
            ShowActiveWindow();
        }

        public void HideToTray()
        {
            if (mAboutWin != null && mAboutWin.IsVisible)
            {
                mAboutWin.Close();
                mAboutWin = null;
            }
            if (mMainWin != null)
            {
                mMainWin.ShowInTaskbar = false;
                mMainWin.Hide();
            }
            if (mMiniWin != null)
            {
                mMiniWin.Hide();
            }
            AppStateStore.Save(mState);
            TrimWorkingSet();
        }

        public void ShutdownCompletely()
        {
            mAllowExit = true;
            if (mSleepTimer != null) mSleepTimer.Stop();
            AppStateStore.Save(mState);
            mAudio.StopAndWaitFadeOut(600);
            mAudio.Dispose();

            if (mTrayIcon != null)
            {
                mTrayIcon.Visible = false;
                mTrayIcon.Dispose();
                mTrayIcon = null;
            }
            if (mAboutWin != null) mAboutWin.Close();
            if (mMainWin != null) mMainWin.Close();
            if (mMiniWin != null) mMiniWin.Close();
            if (mWpfApp != null) mWpfApp.Shutdown(0);
        }

        [DllImport("kernel32.dll")]
        private static extern bool SetProcessWorkingSetSize(IntPtr proc, IntPtr min, IntPtr max);

        private static void TrimWorkingSet()
        {
            try
            {
                GC.Collect(GC.MaxGeneration, GCCollectionMode.Optimized, false);
                SetProcessWorkingSetSize(Process.GetCurrentProcess().Handle, (IntPtr)(-1), (IntPtr)(-1));
            }
            catch
            {
            }
        }

        private sealed class DarkMenuRenderer : Forms.ToolStripProfessionalRenderer
        {
            public DarkMenuRenderer() : base(new DarkColorTable()) { }
        }

        private sealed class DarkColorTable : Forms.ProfessionalColorTable
        {
            public override Color ToolStripDropDownBackground { get { return Color.FromArgb(23, 24, 28); } }
            public override Color ImageMarginGradientBegin { get { return Color.FromArgb(23, 24, 28); } }
            public override Color ImageMarginGradientMiddle { get { return Color.FromArgb(23, 24, 28); } }
            public override Color ImageMarginGradientEnd { get { return Color.FromArgb(23, 24, 28); } }
            public override Color MenuBorder { get { return Color.FromArgb(42, 45, 53); } }
            public override Color MenuItemBorder { get { return Color.FromArgb(229, 169, 60); } }
            public override Color MenuItemSelected { get { return Color.FromArgb(38, 32, 21); } }
            public override Color SeparatorDark { get { return Color.FromArgb(42, 45, 53); } }
            public override Color CheckBackground { get { return Color.FromArgb(38, 32, 21); } }
            public override Color CheckSelectedBackground { get { return Color.FromArgb(38, 32, 21); } }
        }

        // ==============================
        // Headless --self-test harness
        // ==============================
        private static int sSelfPassed = 0;

        private static void Assert(bool cond, string msg)
        {
            if (!cond) throw new InvalidOperationException("SELFTEST FAILED: " + msg);
            sSelfPassed++;
        }

        private static void AssertNear(double a, double b, double eps, string msg)
        {
            if (Math.Abs(a - b) > eps)
            {
                throw new InvalidOperationException(string.Format(CultureInfo.InvariantCulture,
                    "SELFTEST FAILED: {0} expected={1} actual={2}", msg, b, a));
            }
            sSelfPassed++;
        }

        private static int RunSelfTest(string baseDir)
        {
            sSelfPassed = 0;

            // 1. Core 48 + Bundled Commercial Expansion Pack Schema Loader
            IList<PresetItem> core = PresetCatalog.LoadAll(baseDir);
            Assert(core.Count == 48, "48 core presets preserved for Android parity");

            CatalogLoadResult cat = PresetCatalog.LoadAllWithPacks(baseDir, null);
            Assert(cat.Presets.Count == 64, "Core 48 + Pro Pack 16 == 64 scenarios");
            Assert(cat.Packs.Count >= 2, "Core + Pro pack metadata loaded");
            Assert(cat.SkippedFiles.Count == 0, "Bundled packs validated cleanly with zero skipped files");
            IList<PresetItem> all = cat.Presets;

            // Test external pack hot-loading + corrupted/incompatible schema graceful skip completely read-only in memory
            List<KeyValuePair<string, string>> memPacks = new List<KeyValuePair<string, string>>();
            string bundledProPath = Path.Combine(baseDir, "packs", "vorta_pc_pro_pack_v1.json");
            if (File.Exists(bundledProPath))
            {
                memPacks.Add(new KeyValuePair<string, string>(bundledProPath, File.ReadAllText(bundledProPath, Encoding.UTF8)));
            }
            memPacks.Add(new KeyValuePair<string, string>("valid_tmp.json",
                "{\"schemaVersion\":1,\"packId\":\"tmp-test-pack\",\"packName\":\"임시 테스트 팩\",\"packVersion\":\"1.0.0\",\"presets\":[{\"id\":9001,\"shortCode\":\"T01\",\"tabIndex\":2,\"title\":\"임시 집중 팩\",\"badge\":\"테스트\",\"desc\":\"테스트\",\"freq1\":220,\"freq2\":230,\"isSweep\":false,\"sweepStart\":0,\"sweepEnd\":0,\"sweepDurationSec\":0,\"isAntiPhase\":false,\"binauralBeat\":10,\"noiseType\":0,\"fmMod\":false,\"fmDepth\":0,\"fmRate\":0,\"amMod\":false,\"amDepth\":0,\"amRate\":0}]}"));
            memPacks.Add(new KeyValuePair<string, string>("bad_corrupt.json", "{ invalid json !!!"));
            memPacks.Add(new KeyValuePair<string, string>("bad_schema.json", "{\"schemaVersion\":99,\"packId\":\"bad\",\"packName\":\"Bad\",\"presets\":[]}"));

            CatalogLoadResult tmpRes = PresetCatalog.LoadAllWithPackPayloads(baseDir, memPacks);
            Assert(tmpRes.Presets.Count == 65, "Valid external pack loaded cleanly alongside core+pro without filesystem writes");
            Assert(tmpRes.SkippedFiles.Count == 2, "Corrupted and future-schema packs skipped gracefully without crash");

            // 2. VolumeCurve checks (0..120% with unity 100% + clean boost up to 120%)
            AssertNear(VolumeCurve.MIN_DB, -48.0, 1e-12, "MIN_DB == -48.0");
            Assert(VolumeCurve.DEFAULT_PERCENT == 30, "DEFAULT_PERCENT == 30 for PC companion");
            Assert(VolumeCurve.MAX_PERCENT == 120, "MAX_PERCENT == 120");
            Assert(BitConverter.DoubleToInt64Bits(VolumeCurve.PercentToDb(100)) == BitConverter.DoubleToInt64Bits(0.0d), "100% positive zero");
            Assert("0.0 dB".Equals(VolumeCurve.FormatDb(100, false)), "no -0.0 dB");
            Assert(VolumeCurve.FormatDb(120, false).StartsWith("+1.6"), "120% boost dB string");
            AssertNear(VolumeCurve.PercentToLinearGain(120), 1.20, 1e-12, "120% linear gain == 1.20");
            Assert("-\u221e dB".Equals(VolumeCurve.FormatDb(30, true)), "muted format");
            double prevGain = -1.0;
            for (int p = 0; p <= VolumeCurve.MAX_PERCENT; p++)
            {
                double g = VolumeCurve.PercentToLinearGain(p);
                Assert(g > prevGain, "monotonic gain " + p);
                prevGain = g;
            }

            // 3. Exhaustive All-Preset x All-Profile 120% Clean Boost & Unconditional 4x True-Peak / Zero-Overshoot Matrix (192/192)
            PcOutputProfile[] allProfiles = new PcOutputProfile[] { PcOutputProfile.PcSpeaker, PcOutputProfile.Headphone, PcOutputProfile.PureRaw };
            for (int pi = 0; pi < all.Count; pi++)
            {
                PresetItem preset = all[pi];
                for (int pr = 0; pr < allProfiles.Length; pr++)
                {
                    double rms = VerifyZeroOvershootAt120(allProfiles[pr], preset, true);
                    Assert(!double.IsNaN(rms) && rms > 0.005, "Finite non-zero RMS for preset " + preset.Id + " prof=" + pr);
                }
            }

            // 4. Multi-Frequency Coherent DFT THD+N < 0.1% at 120% Clean Boost (40 Hz, 200 Hz, 528 Hz, 1000 Hz)
            VerifyPureSineDft120(40.0f, 2400, 2);     // Low-freq 40 Hz: exact k=2
            VerifyPureSineDft120(200.0f, 2400, 10);   // Mid-low 200 Hz: exact k=10
            VerifyPureSineDft120(528.0f, 1000, 11);   // Solfeggio 528 Hz: exact k=11
            VerifyPureSineDft120(1000.0f, 2400, 50);  // Audible 1000 Hz: exact k=50

            // Dynamic 80% -> 120% boost ramp overshoot check
            {
                AegisDspEngine ramp = new AegisDspEngine(48000);
                ramp.SetOutputProfile(PcOutputProfile.PcSpeaker);
                ramp.SetVolume(80, false);
                ramp.PlayPreset(PresetCatalog.FindById(all, 2));
                float[] tmpBuf = new float[480 * 2];
                for (int b = 0; b < 50; b++) ramp.RenderStereoFloat(tmpBuf, 480);
                ramp.SetVolume(120, false);
                for (int b = 0; b < 60; b++) ramp.RenderStereoFloat(tmpBuf, 480);
                Assert(ramp.OvershootCount == 0, "Zero hard-clamp overshoots across 80%->120% ramp");
            }

            // 5. Ultrasonic -12 dB guard check in Headphone vs unguarded PureRaw at 120% (+ full true-peak check with guard OFF)
            PresetItem pUltra = null;
            for (int i = 0; i < all.Count; i++) { if (all[i].NoiseType == 2) { pUltra = all[i]; break; } }
            Assert(pUltra != null, "ultrasonic preset found");
            VerifyZeroOvershootAt120(PcOutputProfile.PureRaw, pUltra, false);
            double rmsUltraOff = RenderSecond(PcOutputProfile.PureRaw, pUltra, false, 120);
            double rmsUltraGuard = RenderSecond(PcOutputProfile.Headphone, pUltra, false, 120);
            AssertNear(rmsUltraGuard / rmsUltraOff, 0.25, 0.03, "Headphone forces -12 dB ultrasonic protection at 120%");

            // 6. Dual-Rate 1500 Hz Decimated Low-Freq Scope + Fundamental vs 144Hz Composite Isolation (P02 7.00 Hz)
            PresetItem p2 = PresetCatalog.FindById(all, 2);
            PresetItem p1 = PresetCatalog.FindById(all, 1);
            PresetItem p35 = PresetCatalog.FindById(all, 35);
            AegisDspEngine lfScope = new AegisDspEngine(48000);
            lfScope.SetOutputProfile(PcOutputProfile.PcSpeaker);
            lfScope.SetVolume(100, false);
            lfScope.PlayPreset(p2);
            float[] lfBuf = new float[512 * 2];
            for (int b = 0; b < 260; b++) lfScope.RenderStereoFloat(lfBuf, 512); // ~2.77s fills 4096 @ 1500Hz ring

            float[] lowFundL = new float[AegisDspEngine.SCOPE_LOW_RING_SIZE];
            float[] lowCompL = new float[AegisDspEngine.SCOPE_LOW_RING_SIZE];
            float[] fastFundL = new float[AegisDspEngine.SCOPE_RING_SIZE];
            float[] fastCompL = new float[AegisDspEngine.SCOPE_RING_SIZE];
            double srLow;
            double srFast;
            lfScope.CopyScopeSnapshotEx(true, true, lowFundL, null, out srLow);
            lfScope.CopyScopeSnapshotEx(false, true, lowCompL, null, out srLow);
            lfScope.CopyScopeSnapshotEx(true, false, fastFundL, null, out srFast);
            lfScope.CopyScopeSnapshotEx(false, false, fastCompL, null, out srFast);
            AssertNear(srLow, 1500.0, 1e-6, "Low-rate decimated scope rate == 1500 Hz");
            AssertNear(srFast, 48000.0, 1e-6, "Fast scope rate == 48000 Hz");

            int zeroCrossCount = 0;
            bool armed = false;
            for (int i = 1; i < lowFundL.Length; i++)
            {
                if (!armed)
                {
                    if (lowFundL[i] <= -0.05f) armed = true;
                }
                else if (lowFundL[i - 1] <= 0.0f && lowFundL[i] > 0.0f)
                {
                    zeroCrossCount++;
                    armed = false;
                }
            }
            Assert(zeroCrossCount >= 10, "7Hz low-rate ring holds >= 10 hysteresis zero-crossings in 2.73s (" + zeroCrossCount + ")");

            double maxCarrierSep = 0.0;
            for (int i = 0; i < fastFundL.Length; i++)
            {
                double d = Math.Abs(fastCompL[i] - fastFundL[i]);
                if (d > maxCarrierSep) maxCarrierSep = d;
            }
            Assert(maxCarrierSep > 0.08, "Fundamental tap cleanly isolates 7Hz from 144Hz PcSpeaker carrier (" + maxCarrierSep.ToString("F3", CultureInfo.InvariantCulture) + ")");

            // 7. De-clicker switch continuity & fade-out clean scope clear
            AegisDspEngine sw = new AegisDspEngine(48000);
            sw.SetVolume(120, false);
            sw.SetOutputProfile(PcOutputProfile.PcSpeaker);
            sw.PlayPreset(p1);
            float[] buf = new float[512 * 2];
            for (int b = 0; b < 40; b++) sw.RenderStereoFloat(buf, 512);
            float prevEndL = buf[(512 - 1) * 2];
            sw.PlayPreset(p35);
            sw.RenderStereoFloat(buf, 512);
            Assert(Math.Abs(buf[0] - prevEndL) < 0.025, "De-clicker keeps transition continuous");
            Assert(sw.OvershootCount == 0, "De-click transition has zero overshoots");
            sw.RequestStop();
            for (int b = 0; b < 80; b++) sw.RenderStereoFloat(buf, 512);
            Assert(!sw.IsActive && Math.Abs(buf[(512 - 1) * 2]) < 1e-6f, "Fade-out stops cleanly to silence");
            sw.CopyScopeSnapshot(fastCompL, null);
            float postStopPeak = 0.0f;
            for (int i = 0; i < fastCompL.Length; i++) postStopPeak = Math.Max(postStopPeak, Math.Abs(fastCompL[i]));
            Assert(postStopPeak < 1e-6f, "Scope ring clears cleanly on stop");

            // 8. Live Windows WASAPI Shared Mode probe
            AegisDspEngine probeDsp = new AegisDspEngine(48000);
            string probeInfo = WasapiAudioEngine.RunSelfTestProbe(probeDsp);
            Assert(probeInfo.Contains("devices=") && probeInfo.Contains("bufFrames="), "WASAPI live endpoint verified");

            // 9. AppStateStore round-trip, ConsolePaneWidth sentinel & Pro preset IDs
            AppStateData st = new AppStateData();
            st.VolumePercent = 150;
            st.LastTab = 9;
            st.LastPresetId = 102;
            st.WindowWidth = 0;
            st.WindowHeight = 200;
            st.MiniWidth = 100;
            st.ConsolePaneWidth = -99999.0;
            st.FavoriteIds = new List<int>(new int[] { 1, 1, 102, 102, 200000, -5 });
            st.RecentPresetIds = new List<int>(new int[] { 2, 101, 2, 19, 102, 25, 13, 1, 3, 4, 5 });
            AppStateStore.Sanitize(st);
            Assert(st.VolumePercent == 120 && st.LastTab == 0 && st.LastPresetId == 102, "AppStateStore sanitizes bounds + Pro IDs");
            Assert(double.IsNaN(st.ConsolePaneWidth), "ConsolePaneWidth sentinel -99999 restores Auto NaN");
            Assert(st.FavoriteIds.Count == 2 && st.FavoriteIds[1] == 102, "FavoriteIds supports Pro Pack IDs");
            Assert(st.RecentPresetIds.Count == 8, "RecentPresetIds deduplicated and capped at 8");
            st.ConsolePaneWidth = 120.0;
            AppStateStore.Sanitize(st);
            AssertNear(st.ConsolePaneWidth, 340.0, 1e-6, "ConsolePaneWidth clamps min 340");

            // 10. UpdateChecker SemVer 2.0 (incl. pre-release precedence) & WPF dark XAML styles
            Assert(UpdateChecker.CompareSemVer("3.2.0", "3.2.0") == 0, "SemVer equal");
            Assert(UpdateChecker.CompareSemVer("3.2.0", "3.3.0") < 0, "SemVer upgrade detected");
            Assert(UpdateChecker.CompareSemVer("3.10.0", "3.2.9") > 0, "SemVer numeric comparison");
            Assert(UpdateChecker.CompareSemVer("3.2.0-rc1", "3.2.0") < 0, "SemVer pre-release lower than release");
            Assert(UpdateChecker.CompareSemVer("3.2.0-beta.2", "3.2.0-beta.10") < 0, "SemVer pre-release numeric precedence");
            Assert(UpdateChecker.CompareSemVer("1.0.0--1", "1.0.0-0") > 0, "SemVer non-digit identifier higher than numeric 0");
            Assert(UpdateChecker.CompareSemVer("1.0.0-alpha", "1.0.0-1") > 0, "SemVer alpha higher than numeric");
            Assert(ThemePalette.CreateDarkSliderStyle() != null, "DarkSliderStyle parses");
            Assert(ThemePalette.CreateDarkComboStyle() != null, "DarkComboStyle parses");
            Assert(ThemePalette.CreateDarkScrollBarStyle() != null, "DarkScrollBarStyle parses");
            Assert(ThemePalette.CreateDarkSplitterStyle() != null, "DarkSplitterStyle parses");

            Console.WriteLine(string.Format(CultureInfo.InvariantCulture,
                "PASS: {0}/0 native checks verified ({1})", sSelfPassed, probeInfo));
            return 0;
        }

        private static double CatmullRomMax(double p0, double p1, double p2, double p3)
        {
            double maxTp = 0.0;
            for (int sub = 1; sub <= 3; sub++)
            {
                double t = sub * 0.25;
                double t2 = t * t;
                double t3 = t2 * t;
                double interp = 0.5 * ((2.0 * p1)
                    + (-p0 + p2) * t
                    + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
                    + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
                double abs = Math.Abs(interp);
                if (abs > maxTp) maxTp = abs;
            }
            return maxTp;
        }

        private static double VerifyZeroOvershootAt120(PcOutputProfile profile, PresetItem preset, bool guard)
        {
            AegisDspEngine eng = new AegisDspEngine(48000);
            eng.SetOutputProfile(profile);
            eng.SetUltrasonicGuardEnabled(guard);
            eng.SetVolume(120, false);
            eng.PlayPreset(preset);

            const int BLK = 480;
            float[] buf = new float[BLK * 2];
            double sumSq = 0.0;
            double maxAbs = 0.0;
            // Seed pre-roll s[-1] = 0.0 (silence before start) so every interval [s0..s1]..[s_{N-2}..s_{N-1}] has full 4-tap support
            double pL0 = 0.0, pL1 = 0.0, pL2 = 0.0, pL3 = 0.0;
            double pR0 = 0.0, pR1 = 0.0, pR2 = 0.0, pR3 = 0.0;
            double maxTp = 0.0;
            int totalFrames = 0;

            for (int b = 0; b < 110; b++)
            {
                eng.RenderStereoFloat(buf, BLK);
                for (int i = 0; i < BLK; i++)
                {
                    float l = buf[i * 2];
                    float r = buf[i * 2 + 1];
                    Assert(!float.IsNaN(l) && !float.IsInfinity(l), "finite L " + preset.Id);
                    Assert(!float.IsNaN(r) && !float.IsInfinity(r), "finite R " + preset.Id);
                    double aL = Math.Abs((double)l);
                    double aR = Math.Abs((double)r);
                    if (aL > maxAbs) maxAbs = aL;
                    if (aR > maxAbs) maxAbs = aR;
                    sumSq += (l * l) + (r * r);

                    pL0 = pL1; pL1 = pL2; pL2 = pL3; pL3 = l;
                    pR0 = pR1; pR1 = pR2; pR2 = pR3; pR3 = r;
                    totalFrames++;
                    // Starts evaluating [s0..s1] once s2 is pushed (totalFrames >= 3) since pL0=0 pre-roll at frame 3
                    if (totalFrames >= 3)
                    {
                        double tp = Math.Max(CatmullRomMax(pL0, pL1, pL2, pL3), CatmullRomMax(pR0, pR1, pR2, pR3));
                        if (tp > maxTp) maxTp = tp;
                    }
                }
            }

            // Render 1 lookahead sample s[N] so final interval [s_{N-2}..s_{N-1}] also has full 4-tap Catmull-Rom support
            float[] tail = new float[2];
            eng.RenderStereoFloat(tail, 1);
            pL0 = pL1; pL1 = pL2; pL2 = pL3; pL3 = tail[0];
            pR0 = pR1; pR1 = pR2; pR2 = pR3; pR3 = tail[1];
            double tailTp = Math.Max(CatmullRomMax(pL0, pL1, pL2, pL3), CatmullRomMax(pR0, pR1, pR2, pR3));
            if (tailTp > maxTp) maxTp = tailTp;

            Assert(eng.OvershootCount == 0, "Zero overshoot (no hard clamp) preset=" + preset.Id + " profile=" + profile);
            Assert(maxAbs <= AegisDspEngine.CLEAN_BOOST_CEILING + 1e-6, "Sample peak L/R <= -0.50 dBTP preset=" + preset.Id);
            Assert(maxTp <= AegisDspEngine.CLEAN_BOOST_CEILING + 5e-4, "Unconditional L+R 4x True-peak (all 192 combos, all intervals) <= -0.50 dBTP preset=" + preset.Id);
            return Math.Sqrt(sumSq / (110 * BLK * 2));
        }

        private static void VerifyPureSineDft120(float freqHz, int dftN, int kBin)
        {
            PresetItem synth = new PresetItem();
            synth.Id = 99990 + (int)freqHz;
            synth.ShortCode = "DFT";
            synth.TabIndex = 2;
            synth.Title = "DFT Tone";
            synth.Freq1 = freqHz;
            synth.Freq2 = 0f;
            synth.NoiseType = 0;

            AegisDspEngine eng = new AegisDspEngine(48000);
            eng.SetOutputProfile(PcOutputProfile.PureRaw);
            eng.SetVolume(120, false);
            eng.PlayPreset(synth);

            float[] block = new float[200 * 2];
            for (int b = 0; b < 260; b++) eng.RenderStereoFloat(block, 200); // > 1.08s steady state past 1s fade-in

            double re = 0.0;
            double im = 0.0;
            double totalSq = 0.0;
            double peak = 0.0;
            int idx = 0;
            int neededBlocks = (dftN + 199) / 200;
            for (int b = 0; b < neededBlocks; b++)
            {
                eng.RenderStereoFloat(block, 200);
                for (int i = 0; i < 200 && idx < dftN; i++)
                {
                    double x = block[i * 2];
                    double absX = Math.Abs(x);
                    if (absX > peak) peak = absX;
                    totalSq += x * x;
                    double angle = (2.0 * Math.PI * kBin * idx) / dftN;
                    re += x * Math.Cos(angle);
                    im += x * Math.Sin(angle);
                    idx++;
                }
            }

            Assert(eng.OvershootCount == 0, "Multi-freq DFT zero overshoot @ " + freqHz + " Hz");
            Assert(peak <= AegisDspEngine.CLEAN_BOOST_CEILING + 1e-6, "Multi-freq peak <= -0.50 dBTP @ " + freqHz + " Hz");
            double meanSq = totalSq / dftN;
            double fundMeanSq = (2.0 * (re * re + im * im)) / ((double)dftN * (double)dftN);
            double distMeanSq = Math.Max(0.0, meanSq - fundMeanSq);
            double thd = Math.Sqrt(distMeanSq / Math.Max(1e-18, fundMeanSq));
            Assert(thd < 0.001, "120% boost THD+N < 0.1% @ " + freqHz + "Hz (" + thd.ToString("F6", CultureInfo.InvariantCulture) + ")");
        }

        private static double RenderSecond(PcOutputProfile profile, PresetItem preset, bool guard, int vol)
        {
            AegisDspEngine eng = new AegisDspEngine(48000);
            eng.SetOutputProfile(profile);
            eng.SetUltrasonicGuardEnabled(guard);
            eng.SetVolume(vol, false);
            eng.PlayPreset(preset);

            float[] buf = new float[480 * 2];
            double sumSq = 0.0;
            for (int b = 0; b < 100; b++)
            {
                eng.RenderStereoFloat(buf, 480);
                for (int i = 0; i < buf.Length; i++)
                {
                    float v = buf[i];
                    Assert(!float.IsNaN(v) && !float.IsInfinity(v), "finite sample");
                    Assert(Math.Abs(v) <= (float)(AegisDspEngine.CLEAN_BOOST_CEILING + 1e-6), "-0.50 dBTP clean boost ceiling");
                    sumSq += v * v;
                }
            }
            Assert(eng.OvershootCount == 0, "Zero overshoots in RenderSecond");
            return Math.Sqrt(sumSq / (100 * 480 * 2));
        }
    }
}
