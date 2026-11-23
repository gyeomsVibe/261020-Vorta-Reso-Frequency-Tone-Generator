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

        private static readonly int[] QUICK_PRESET_IDS = new int[] { 1, 2, 19, 13, 25, 35, 45 };

        private readonly string mBaseDir;
        private readonly IList<PresetItem> mPresets;
        private readonly AppStateData mState;
        private readonly AegisDspEngine mDsp;
        private readonly WasapiAudioEngine mAudio;

        private Application mWpfApp;
        private CompanionWindow mMainWin;
        private MiniBarWindow mMiniWin;
        private Forms.NotifyIcon mTrayIcon;
        private Forms.ContextMenuStrip mTrayMenu;
        private Forms.ToolStripMenuItem mMenuStatusItem;
        private Forms.ToolStripMenuItem mMenuPlayStopItem;
        private Thread mIpcThread;
        private volatile bool mAllowExit;
        private System.Windows.Threading.DispatcherTimer mSleepTimer;
        private int mSleepRemainingSec;
        private int mSleepPresetMin;

        public IList<PresetItem> Presets { get { return mPresets; } }
        public AegisDspEngine Dsp { get { return mDsp; } }
        public WasapiAudioEngine Audio { get { return mAudio; } }
        public AppStateData State { get { return mState; } }
        public bool AllowExit { get { return mAllowExit; } }
        public int SleepTimerRemainingSeconds { get { return mSleepRemainingSec; } }
        public int SleepTimerPresetMinutes { get { return mSleepPresetMin; } }

        private VortaAegisHostApp(string baseDir)
        {
            mBaseDir = baseDir;
            mPresets = PresetCatalog.LoadAll(baseDir);
            mState = AppStateStore.Load();
            mDsp = new AegisDspEngine(48000);
            mDsp.SetVolume(mState.VolumePercent, mState.IsMuted);
            mDsp.SetOutputProfile((PcOutputProfile)mState.OutputProfile);
            mDsp.SetUltrasonicGuardEnabled(mState.UltrasonicGuard);

            mAudio = new WasapiAudioEngine(mDsp);
            mAudio.SetOutputDevice(mState.DeviceId);
        }

        [STAThread]
        public static int Main(string[] args)
        {
            string exeDir = AppDomain.CurrentDomain.BaseDirectory;
            bool selfTest = false;
            bool startMinimized = false;
            string captureLivePath = null;
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
                if (!string.IsNullOrEmpty(captureLivePath))
                {
                    msg = string.Format(CultureInfo.InvariantCulture, "CAPTURE_EX:{0}:{1}:{2}:{3}\n", capW, capH, capPlayId, captureLivePath);
                }
                SignalExistingInstance(msg);
                singleMutex.Dispose();
                return 0;
            }
            if (!string.IsNullOrEmpty(captureLivePath))
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
                if (qp == null) continue;
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
                                if (cmd.StartsWith("CAPTURE_EX:", StringComparison.Ordinal))
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
                    capTimer.Interval = TimeSpan.FromMilliseconds(460);
                    capTimer.Tick += delegate
                    {
                        capTimer.Stop();
                        try
                        {
                            mMainWin.UpdateScopeVisualsNow();
                            mMainWin.UpdateLayout();
                            RenderWindowToPng(outPath);
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
                    RenderWindowToPng(outPath);
                }
            }
            catch
            {
            }
        }

        private void RenderWindowToPng(string outPath)
        {
            int w = Math.Max(1, (int)Math.Ceiling(mMainWin.ActualWidth));
            int h = Math.Max(1, (int)Math.Ceiling(mMainWin.ActualHeight));
            RenderTargetBitmap rtb = new RenderTargetBitmap(w, h, 96, 96, System.Windows.Media.PixelFormats.Pbgra32);
            rtb.Render(mMainWin);
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
            mState.LastTab = preset.TabIndex;
            mState.LastPresetId = preset.Id;
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
            if (presetId < 1 || presetId > 48) return;
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
            IList<PresetItem> all = PresetCatalog.LoadAll(baseDir);
            Assert(all.Count == 48, "48 presets loaded");

            // VolumeCurve checks (0..120% with unity 100% + clean boost up to 120%)
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

            // TabSyncPolicy checks
            Assert(TabSyncPolicy.ClampTabIndex(-1) == 0 && TabSyncPolicy.ClampTabIndex(9) == 0, "tab clamp bounds");
            Assert(TabSyncPolicy.ResolveTabOnEntry(true, 5, 0, 0, true) == 5, "playing tab wins on entry");
            Assert(TabSyncPolicy.ResolveTabOnEntry(true, 5, 5, 0, false) == 0, "no foreground hijack");
            Assert(TabSyncPolicy.ResolveTabOnEntry(false, -1, 5, 0, true) == 5, "idle restores last tab");

            // DSP checks across all 3 PC output profiles & 120% clean boost
            PresetItem p1 = PresetCatalog.FindById(all, 1);   // 18.98 Hz infrasound + FM
            PresetItem p2 = PresetCatalog.FindById(all, 2);   // 7.00 Hz + 7.83 Hz
            PresetItem p35 = PresetCatalog.FindById(all, 35); // Binaural 200/220
            PresetItem pUltra = null;
            for (int i = 0; i < all.Count; i++) { if (all[i].NoiseType == 2) { pUltra = all[i]; break; } }
            Assert(p1 != null && p2 != null && p35 != null && pUltra != null, "key presets located");

            // PureRaw vs PcSpeaker infrasound (18.98 Hz) & 120% boost checks
            double rmsPureP1 = RenderSecond(PcOutputProfile.PureRaw, p1, true, 100);
            double rmsPcP1 = RenderSecond(PcOutputProfile.PcSpeaker, p1, true, 120);
            double rmsPcP2 = RenderSecond(PcOutputProfile.PcSpeaker, p2, true, 120);
            Assert(rmsPureP1 > 0.1 && rmsPcP1 > 0.1 && rmsPcP2 > 0.1, "P01/P02 finite non-zero at 120% boost");

            double rmsPure100 = RenderSecond(PcOutputProfile.PureRaw, p35, true, 100);
            double rmsPure120 = RenderSecond(PcOutputProfile.PureRaw, p35, true, 120);
            Assert(rmsPure120 > rmsPure100 * 1.18, "120% boost yields >=18% higher RMS than 100%");
            VerifyZeroDistortionDft120(p35);

            // Ultrasonic -12 dB guard check in Headphone vs unguarded PureRaw at 120%
            double rmsUltraOff = RenderSecond(PcOutputProfile.PureRaw, pUltra, false, 120);
            double rmsUltraGuard = RenderSecond(PcOutputProfile.Headphone, pUltra, false, 120);
            double ultraRatio = rmsUltraGuard / rmsUltraOff;
            AssertNear(ultraRatio, 0.25, 0.03, "Headphone forces -12 dB ultrasonic protection at 120%");

            // De-clicker switch continuity + oscilloscope snapshot + fade-out clear
            AegisDspEngine sw = new AegisDspEngine(48000);
            sw.SetVolume(120, false);
            sw.SetOutputProfile(PcOutputProfile.PcSpeaker);
            sw.PlayPreset(p1);
            float[] buf = new float[512 * 2];
            for (int b = 0; b < 40; b++) sw.RenderStereoFloat(buf, 512);
            AssertNear(sw.LiveFreqHz, 18.98, 1e-3, "LiveFreqHz captures nominal carrier before FM");
            float prevEndL = buf[(512 - 1) * 2];
            sw.PlayPreset(p35);
            sw.RenderStereoFloat(buf, 512);
            Assert(Math.Abs(buf[0] - prevEndL) < 0.025, "De-clicker keeps transition continuous");
            for (int b = 0; b < 12; b++) sw.RenderStereoFloat(buf, 512);

            float[] snapL = new float[AegisDspEngine.SCOPE_RING_SIZE];
            float[] snapR = new float[AegisDspEngine.SCOPE_RING_SIZE];
            sw.CopyScopeSnapshot(snapL, snapR);
            float maxScopeL = 0.0f;
            float maxDiffLR = 0.0f;
            for (int i = 0; i < AegisDspEngine.SCOPE_RING_SIZE; i++)
            {
                Assert(!float.IsNaN(snapL[i]) && !float.IsInfinity(snapL[i]), "finite scope L");
                Assert(!float.IsNaN(snapR[i]) && !float.IsInfinity(snapR[i]), "finite scope R");
                float aL = Math.Abs(snapL[i]);
                if (aL > maxScopeL) maxScopeL = aL;
                float dLR = Math.Abs(snapL[i] - snapR[i]);
                if (dLR > maxDiffLR) maxDiffLR = dLR;
            }
            Assert(maxScopeL > 0.1f, "Oscilloscope snapshot captures active wave");
            Assert(maxDiffLR > 0.05f, "Oscilloscope distinguishes L/R binaural channels");
            Assert(sw.LiveRmsDbfs >= -96.0f && sw.LiveRmsDbfs <= 0.0f, "LiveRmsDbfs within valid dBFS range");

            sw.RequestStop();
            for (int b = 0; b < 80; b++) sw.RenderStereoFloat(buf, 512);
            Assert(!sw.IsActive && Math.Abs(buf[(512 - 1) * 2]) < 1e-6f, "Fade-out stops cleanly to silence");
            sw.CopyScopeSnapshot(snapL, snapR);
            float postStopPeak = 0.0f;
            for (int i = 0; i < AegisDspEngine.SCOPE_RING_SIZE; i++)
            {
                postStopPeak = Math.Max(postStopPeak, Math.Max(Math.Abs(snapL[i]), Math.Abs(snapR[i])));
            }
            Assert(postStopPeak < 1e-6f, "Scope ring clears cleanly on stop");

            // Live Windows WASAPI Shared Mode probe
            AegisDspEngine probeDsp = new AegisDspEngine(48000);
            string probeInfo = WasapiAudioEngine.RunSelfTestProbe(probeDsp);
            Assert(probeInfo.Contains("devices=") && probeInfo.Contains("bufFrames="), "WASAPI live endpoint verified");

            // AppStateStore round-trip & sanitization
            AppStateData st = new AppStateData();
            st.VolumePercent = 150;
            st.LastTab = 9;
            st.WindowWidth = 0;
            st.WindowHeight = 200;
            st.MiniWidth = 100;
            st.FavoriteIds = new List<int>(new int[] { 1, 1, 99, 19, -5 });
            AppStateStore.Sanitize(st);
            Assert(st.VolumePercent == 120 && st.LastTab == 0, "AppStateStore sanitizes volume & tab");
            AssertNear(st.WindowWidth, 920.0, 1e-6, "WindowWidth defaults safely from 0 to 920");
            AssertNear(st.WindowHeight, 540.0, 1e-6, "WindowHeight clamps minimum 540");
            AssertNear(st.MiniWidth, 340.0, 1e-6, "MiniWidth clamps minimum 340");
            Assert(st.FavoriteIds.Count == 2 && st.FavoriteIds[0] == 1 && st.FavoriteIds[1] == 19, "FavoriteIds deduplicated & bounded");

            st.WindowWidth = 200;
            AppStateStore.Sanitize(st);
            AssertNear(st.WindowWidth, 400.0, 1e-6, "WindowWidth clamps minimum 400");

            // WPF custom dark XAML templates & layout breakpoints
            Assert(ThemePalette.CreateDarkSliderStyle() != null, "DarkSliderStyle parses");
            Assert(ThemePalette.CreateDarkComboStyle() != null, "DarkComboStyle parses");
            Assert(ThemePalette.CreateDarkScrollBarStyle() != null, "DarkScrollBarStyle parses");
            AssertNear(CompanionWindow.WIDE_BREAKPOINT, 760.0, 1e-6, "WIDE_BREAKPOINT == 760");
            AssertNear(CompanionWindow.MIN_WINDOW_WIDTH, 400.0, 1e-6, "MIN_WINDOW_WIDTH == 400");

            Console.WriteLine(string.Format(CultureInfo.InvariantCulture,
                "PASS: {0}/0 native checks verified ({1})", sSelfPassed, probeInfo));
            return 0;
        }

        private static void VerifyZeroDistortionDft120(PresetItem p35)
        {
            const int SR = 48000;
            AegisDspEngine eng = new AegisDspEngine(SR);
            eng.SetOutputProfile(PcOutputProfile.PureRaw);
            eng.SetVolume(120, false);
            eng.PlayPreset(p35);

            float[] block = new float[480 * 2];
            for (int b = 0; b < 115; b++)
            {
                eng.RenderStereoFloat(block, 480);
            }

            // Capture N=2400 steady-state frames (10 exact cycles of 200 Hz at 48 kHz -> k=10 DFT bin)
            const int N = 2400;
            double re = 0.0;
            double im = 0.0;
            double totalSq = 0.0;
            double peak = 0.0;
            int idx = 0;
            for (int b = 0; b < 5; b++)
            {
                eng.RenderStereoFloat(block, 480);
                for (int i = 0; i < 480; i++)
                {
                    double x = block[i * 2];
                    double absX = Math.Abs(x);
                    if (absX > peak) peak = absX;
                    totalSq += x * x;
                    double angle = (2.0 * Math.PI * 10.0 * idx) / N;
                    re += x * Math.Cos(angle);
                    im += x * Math.Sin(angle);
                    idx++;
                }
            }
            Assert(peak <= AegisDspEngine.CLEAN_BOOST_CEILING + 1e-5, "120% steady-state peak within -0.50 dBTP clean ceiling");
            double meanSq = totalSq / N;
            double fundMeanSq = (2.0 * (re * re + im * im)) / ((double)N * (double)N);
            double distMeanSq = Math.Max(0.0, meanSq - fundMeanSq);
            double thd = Math.Sqrt(distMeanSq / Math.Max(1e-18, fundMeanSq));
            Assert(thd < 0.001, "120% boost THD+N < 0.1% (" + thd.ToString("F6", CultureInfo.InvariantCulture) + ")");
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
                    Assert(Math.Abs(v) <= (float)(AegisDspEngine.CLEAN_BOOST_CEILING + 1e-5), "-0.50 dBTP clean boost ceiling");
                    sumSq += v * v;
                }
            }
            return Math.Sqrt(sumSq / (100 * 480 * 2));
        }
    }
}
