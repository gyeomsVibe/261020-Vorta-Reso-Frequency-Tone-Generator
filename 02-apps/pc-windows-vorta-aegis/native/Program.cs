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

        private static readonly int[] QUICK_PRESET_IDS = new int[] { 201, 35, 205, 102, 101, 208, 107, 1 };

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
        private BeginnerGuideWindow mGuideWin;
        private PresetDetailWindow mDetailWin;
        private Forms.NotifyIcon mTrayIcon;
        private Forms.ContextMenuStrip mTrayMenu;
        private Forms.ToolStripMenuItem mMenuStatusItem;
        private Forms.ToolStripMenuItem mMenuPlayStopItem;
        private Thread mIpcThread;
        private volatile bool mAllowExit;
        private System.Windows.Threading.DispatcherTimer mSleepTimer;
        private int mSleepRemainingSec;
        private int mSleepPresetMin;
        private System.Windows.Threading.DispatcherTimer mRoutineTimer;
        private RoutineCourse mActiveRoutine;
        private int mRoutineStepIndex;
        private int mRoutineStepRemainingSec;
        private string mLastSoftenNotice = "";
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
        public string ActiveRoutineId { get { return mActiveRoutine != null ? mActiveRoutine.Id : ""; } }
        public string ActiveRoutineTitle { get { return mActiveRoutine != null ? mActiveRoutine.Title : ""; } }
        public int ActiveRoutineStepIndex { get { return mActiveRoutine != null ? mRoutineStepIndex : 0; } }
        public int ActiveRoutineStepCount { get { return mActiveRoutine != null ? mActiveRoutine.Steps.Length : 0; } }
        public string ActiveRoutineStepLabel
        {
            get
            {
                if (mActiveRoutine == null || mActiveRoutine.Steps == null || mActiveRoutine.Steps.Length == 0) return "";
                int idx = Math.Max(0, Math.Min(mActiveRoutine.Steps.Length - 1, mRoutineStepIndex));
                return mActiveRoutine.Steps[idx].StageLabel;
            }
        }
        public int RoutineStepRemainingSeconds { get { return mActiveRoutine != null ? mRoutineStepRemainingSec : 0; } }
        public int RoutineTotalRemainingSeconds
        {
            get
            {
                if (mActiveRoutine == null || mActiveRoutine.Steps == null) return 0;
                int sum = mRoutineStepRemainingSec;
                for (int i = mRoutineStepIndex + 1; i < mActiveRoutine.Steps.Length; i++)
                {
                    sum += mActiveRoutine.Steps[i].DurationMinutes * 60;
                }
                return sum;
            }
        }
        public string LastSoftenNotice { get { return mLastSoftenNotice; } }
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
            mDsp.SetAmbientBed(mState.AmbientType, mState.AmbientMixPercent);

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
            string captureRoutineId = null;
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
                    else if (string.Equals(args[i], "--capture-routine", StringComparison.OrdinalIgnoreCase) && i + 1 < args.Length)
                    {
                        captureRoutineId = args[i + 1];
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
                else if (!string.IsNullOrEmpty(captureLivePath) && !string.IsNullOrEmpty(captureRoutineId))
                {
                    msg = string.Format(CultureInfo.InvariantCulture, "CAPTURE_ROUTINE:{0}:{1}:{2}:{3}\n", capW, capH, captureRoutineId, captureLivePath);
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
            if (mActiveRoutine != null && playing && cur != null)
            {
                mMenuStatusItem.Text = string.Format(CultureInfo.InvariantCulture,
                    "코스 {0}/{1} · {2} ({3:D2}:{4:D2})",
                    mRoutineStepIndex + 1, mActiveRoutine.Steps.Length, mActiveRoutine.Title,
                    mRoutineStepRemainingSec / 60, mRoutineStepRemainingSec % 60);
            }
            else
            {
                mMenuStatusItem.Text = (playing && cur != null)
                    ? string.Format(CultureInfo.InvariantCulture, "재생 중 · {0} ({1:F1} Hz)", cur.Title, mDsp.LiveFreqHz > 0f ? mDsp.LiveFreqHz : cur.Freq1)
                    : string.Format(CultureInfo.InvariantCulture, "대기 중 · 볼륨 {0}%", mDsp.VolumePercent);
            }
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

            Forms.ToolStripMenuItem courseMenu = new Forms.ToolStripMenuItem("원탭 자동 코스");
            IList<RoutineCourse> allCourses = JourneyOrderPolicy.GetAllRoutines();
            for (int c = 0; c < allCourses.Count; c++)
            {
                RoutineCourse rc = allCourses[c];
                Forms.ToolStripMenuItem ci = new Forms.ToolStripMenuItem(rc.Title + " (" + rc.GetTotalMinutes() + "분)");
                ci.Checked = (mActiveRoutine != null && string.Equals(mActiveRoutine.Id, rc.Id, StringComparison.Ordinal));
                string capturedId = rc.Id;
                ci.Click += delegate { StartRoutine(capturedId); };
                courseMenu.DropDownItems.Add(ci);
            }
            if (mActiveRoutine != null)
            {
                courseMenu.DropDownItems.Add(new Forms.ToolStripSeparator());
                Forms.ToolStripMenuItem nextCourseStep = new Forms.ToolStripMenuItem("현재 코스 다음 단계");
                nextCourseStep.Click += delegate { AdvanceRoutineStep(); };
                courseMenu.DropDownItems.Add(nextCourseStep);
            }
            mTrayMenu.Items.Add(courseMenu);

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
                                else if (cmd.StartsWith("CAPTURE_ROUTINE:", StringComparison.Ordinal))
                                {
                                    string rest = cmd.Substring("CAPTURE_ROUTINE:".Length);
                                    int c1 = rest.IndexOf(':');
                                    int c2 = (c1 >= 0) ? rest.IndexOf(':', c1 + 1) : -1;
                                    int c3 = (c2 >= 0) ? rest.IndexOf(':', c2 + 1) : -1;
                                    if (c1 > 0 && c2 > c1 && c3 > c2)
                                    {
                                        int reqW;
                                        int reqH;
                                        int.TryParse(rest.Substring(0, c1), NumberStyles.Integer, CultureInfo.InvariantCulture, out reqW);
                                        int.TryParse(rest.Substring(c1 + 1, c2 - c1 - 1), NumberStyles.Integer, CultureInfo.InvariantCulture, out reqH);
                                        string rId = rest.Substring(c2 + 1, c3 - c2 - 1);
                                        string outPath = rest.Substring(c3 + 1).Trim();
                                        mWpfApp.Dispatcher.BeginInvoke(new Action(delegate { CaptureRoutineBitmapEx(outPath, reqW, reqH, rId); }));
                                    }
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

        private void CaptureRoutineBitmapEx(string outPath, int reqW, int reqH, string routineId)
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

                int savedLastId = mState.LastPresetId;
                int savedLastTab = mState.LastTab;
                StartRoutine(routineId);
                mMainWin.JumpToPlayingPreset();

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
                        mState.LastTab = savedLastTab;
                        AppStateStore.Save(mState);
                        if (mMainWin.IsVisible) mMainWin.RefreshDashboard(true);
                    }
                };
                capTimer.Start();
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
            ClearRoutineStateOnly();
            PlayPresetInternal(preset, false);
        }

        private void PlayPresetInternal(PresetItem preset, bool isRoutineStep)
        {
            if (preset == null) return;
            PresetItem prev = mDsp.CurrentPreset;
            mLastSoftenNotice = "";

            string key = preset.Id.ToString(CultureInfo.InvariantCulture);
            if (mState.RememberPerPresetVolume && mState.PerPresetVolumes != null && mState.PerPresetVolumes.ContainsKey(key))
            {
                int savedVol = VolumeCurve.ClampPercent(mState.PerPresetVolumes[key]);
                mState.VolumePercent = savedVol;
                mDsp.SetVolume(savedVol, mState.IsMuted);
            }
            else if (PresetAdvisor.ShouldWarnHighVolumeSwitch(prev, preset, mState.VolumePercent))
            {
                int recVol = PresetAdvisor.GetRecommendedVolumePercent(preset);
                mState.VolumePercent = recVol;
                mDsp.SetVolume(recVol, mState.IsMuted);
                mLastSoftenNotice = string.Format(CultureInfo.InvariantCulture,
                    "고볼륨 보호: 야간·휴식 프리셋 전환으로 안전 볼륨({0}%)으로 자동 조절됨", recVol);
            }

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
            if (mMainWin != null && mMainWin.IsVisible)
            {
                mMainWin.RefreshVolumeControls();
                mMainWin.RefreshDashboard(false);
            }
            if (mMiniWin != null && mMiniWin.IsVisible) mMiniWin.SyncMini();
        }

        public void PlayAdjacentPreset(int delta)
        {
            if (mPresets == null || mPresets.Count == 0) return;
            int refId = (mDsp.IsPlaying && mDsp.CurrentPreset != null) ? mDsp.CurrentPreset.Id : mState.LastPresetId;
            int nextId = JourneyOrderPolicy.GetNextOrderedPresetId(mPresets, refId, delta);
            PresetItem nextPreset = PresetCatalog.FindById(mPresets, nextId);
            if (nextPreset != null) PlayPreset(nextPreset);
        }

        private void ClearRoutineStateOnly()
        {
            if (mRoutineTimer != null) mRoutineTimer.Stop();
            mActiveRoutine = null;
            mRoutineStepIndex = 0;
            mRoutineStepRemainingSec = 0;
        }

        public void StopRoutineOnly()
        {
            ClearRoutineStateOnly();
            UpdateTrayTooltip();
            if (mMainWin != null && mMainWin.IsVisible) mMainWin.RefreshDashboard(false);
            if (mMiniWin != null && mMiniWin.IsVisible) mMiniWin.SyncMini();
        }

        public void StartRoutine(string routineId)
        {
            RoutineCourse rc = JourneyOrderPolicy.GetRoutineById(routineId);
            if (rc == null || rc.Steps == null || rc.Steps.Length == 0) return;

            if (mSleepTimer != null) mSleepTimer.Stop();
            mSleepPresetMin = 0;
            mSleepRemainingSec = 0;

            mActiveRoutine = rc;
            mRoutineStepIndex = 0;
            mRoutineStepRemainingSec = rc.Steps[0].DurationMinutes * 60;

            if (mRoutineTimer == null)
            {
                mRoutineTimer = new System.Windows.Threading.DispatcherTimer();
                mRoutineTimer.Interval = TimeSpan.FromSeconds(1);
                mRoutineTimer.Tick += OnRoutineTick;
            }
            mRoutineTimer.Start();

            PresetItem firstPreset = PresetCatalog.FindById(mPresets, rc.Steps[0].PresetId);
            PlayPresetInternal(firstPreset, true);
            if (mMainWin != null && mMainWin.IsVisible) mMainWin.RefreshSleepTimerUi();
        }

        public void AdvanceRoutineStep()
        {
            if (mActiveRoutine == null || mActiveRoutine.Steps == null) return;
            int nextStep = mRoutineStepIndex + 1;
            if (nextStep >= mActiveRoutine.Steps.Length)
            {
                StopPlayback();
                return;
            }
            mRoutineStepIndex = nextStep;
            mRoutineStepRemainingSec = mActiveRoutine.Steps[nextStep].DurationMinutes * 60;
            PresetItem nextPreset = PresetCatalog.FindById(mPresets, mActiveRoutine.Steps[nextStep].PresetId);
            PlayPresetInternal(nextPreset, true);
        }

        private void OnRoutineTick(object sender, EventArgs e)
        {
            if (mActiveRoutine == null)
            {
                if (mRoutineTimer != null) mRoutineTimer.Stop();
                return;
            }
            if (mRoutineStepRemainingSec > 0) mRoutineStepRemainingSec--;
            if (mRoutineStepRemainingSec <= 0)
            {
                int nextStep = mRoutineStepIndex + 1;
                if (nextStep < mActiveRoutine.Steps.Length)
                {
                    mRoutineStepIndex = nextStep;
                    mRoutineStepRemainingSec = mActiveRoutine.Steps[nextStep].DurationMinutes * 60;
                    PresetItem nextPreset = PresetCatalog.FindById(mPresets, mActiveRoutine.Steps[nextStep].PresetId);
                    PlayPresetInternal(nextPreset, true);
                }
                else
                {
                    ClearRoutineStateOnly();
                    if (mState.GentleSleepFade)
                    {
                        mAudio.GentleStopPlayback(20.0);
                        UpdateTrayTooltip();
                        if (mMainWin != null && mMainWin.IsVisible) mMainWin.RefreshDashboard(false);
                        if (mMiniWin != null && mMiniWin.IsVisible) mMiniWin.SyncMini();
                    }
                    else
                    {
                        StopPlayback();
                    }
                }
                return;
            }
            UpdateTrayTooltip();
            if (mMainWin != null && mMainWin.IsVisible) mMainWin.RefreshRoutineBannerUi();
            if (mMiniWin != null && mMiniWin.IsVisible) mMiniWin.SyncMini();
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
                            if (mState.GentleSleepFade)
                            {
                                ClearRoutineStateOnly();
                                mAudio.GentleStopPlayback(20.0);
                                UpdateTrayTooltip();
                                if (mMainWin != null && mMainWin.IsVisible)
                                {
                                    mMainWin.RefreshSleepTimerUi();
                                    mMainWin.RefreshDashboard(false);
                                }
                                if (mMiniWin != null && mMiniWin.IsVisible) mMiniWin.SyncMini();
                            }
                            else
                            {
                                StopPlayback();
                            }
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
            mLastSoftenNotice = "";
            ClearRoutineStateOnly();
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
            mLastSoftenNotice = "";
            if (mState.RememberPerPresetVolume && !muted && mDsp.IsPlaying && mDsp.CurrentPreset != null)
            {
                if (mState.PerPresetVolumes == null) mState.PerPresetVolumes = new Dictionary<string, int>();
                mState.PerPresetVolumes[mDsp.CurrentPreset.Id.ToString(CultureInfo.InvariantCulture)] = clamped;
            }
            mDsp.SetVolume(clamped, muted);
            AppStateStore.Save(mState);
            UpdateTrayTooltip();
            if (mMainWin != null && mMainWin.IsVisible) mMainWin.RefreshVolumeControls();
            if (mMiniWin != null && mMiniWin.IsVisible) mMiniWin.SyncMini();
        }

        public void ApplyAmbientBed(int ambientType, int mixPercent)
        {
            int t = ambientType < AegisDspEngine.AMBIENT_OFF ? AegisDspEngine.AMBIENT_OFF : (ambientType > AegisDspEngine.AMBIENT_RAIN ? AegisDspEngine.AMBIENT_RAIN : ambientType);
            int mix = mixPercent < 0 ? 0 : (mixPercent > AegisDspEngine.AMBIENT_MAX_MIX ? AegisDspEngine.AMBIENT_MAX_MIX : mixPercent);
            mState.AmbientType = t;
            mState.AmbientMixPercent = mix;
            mDsp.SetAmbientBed(t, mix);
            AppStateStore.Save(mState);
            if (mMainWin != null && mMainWin.IsVisible) mMainWin.RefreshAmbientControls();
        }

        public void ApplyRememberPerPresetVolume(bool enabled)
        {
            mState.RememberPerPresetVolume = enabled;
            AppStateStore.Save(mState);
        }

        public void ApplyGentleSleepFade(bool enabled)
        {
            mState.GentleSleepFade = enabled;
            AppStateStore.Save(mState);
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

        public void ShowBeginnerGuideDialog()
        {
            try
            {
                if (mGuideWin != null && mGuideWin.IsVisible)
                {
                    mGuideWin.Activate();
                    return;
                }
                Window owner = (mMainWin != null && mMainWin.IsVisible) ? (Window)mMainWin : null;
                mGuideWin = new BeginnerGuideWindow(owner);
                mGuideWin.Closed += delegate { mGuideWin = null; };
                mGuideWin.Show();
                mGuideWin.Activate();
            }
            catch
            {
            }
        }

        public void ShowPresetDetailDialog(PresetItem preset)
        {
            if (preset == null) return;
            try
            {
                if (mDetailWin != null && mDetailWin.IsVisible)
                {
                    mDetailWin.Close();
                    mDetailWin = null;
                }
                Window owner = (mMainWin != null && mMainWin.IsVisible) ? (Window)mMainWin : null;
                mDetailWin = new PresetDetailWindow(this, preset, owner);
                mDetailWin.Closed += delegate { mDetailWin = null; };
                mDetailWin.Show();
                mDetailWin.Activate();
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
                    string localDoc = Path.GetFullPath(Path.Combine(mBaseDir, "..", "docs", "보르타_상용화_라이선스_및_정식인증_가이드.md"));
                    string repoDoc = Path.GetFullPath(Path.Combine(mBaseDir, "..", "..", "04-docs", "02-commercial-licensing", "보르타_상용화_라이선스_및_정식인증_가이드.md"));
                    target = File.Exists(localDoc) ? localDoc : (File.Exists(repoDoc) ? repoDoc : AppProductInfo.GITHUB_REPO_URL);
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
            if (mGuideWin != null && mGuideWin.IsVisible)
            {
                mGuideWin.Close();
                mGuideWin = null;
            }
            if (mDetailWin != null && mDetailWin.IsVisible)
            {
                mDetailWin.Close();
                mDetailWin = null;
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
            if (mRoutineTimer != null) mRoutineTimer.Stop();
            AppStateStore.Save(mState);
            mAudio.StopAndWaitFadeOut(600);
            mAudio.Dispose();

            if (mTrayIcon != null)
            {
                mTrayIcon.Visible = false;
                mTrayIcon.Dispose();
                mTrayIcon = null;
            }
            if (mDetailWin != null) mDetailWin.Close();
            if (mGuideWin != null) mGuideWin.Close();
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

        private sealed class MiniBarTestController : IAppHostController
        {
            private readonly IList<PresetItem> mPresets;
            private readonly IList<PresetPackInfo> mPacks;
            private readonly AegisDspEngine mDsp;
            private readonly AppStateData mState;

            public int SwitchToFullCount;
            public bool LastMiniFlag = true;
            public bool AllowExitFlag;

            public MiniBarTestController(IList<PresetItem> presets)
            {
                mPresets = presets ?? new List<PresetItem>();
                mPacks = new List<PresetPackInfo>();
                mDsp = new AegisDspEngine(48000);
                mState = new AppStateData();
            }

            public IList<PresetItem> Presets { get { return mPresets; } }
            public IList<PresetPackInfo> PackInfos { get { return mPacks; } }
            public string UserPacksDir { get { return ""; } }
            public int SkippedPackCount { get { return 0; } }
            public AegisDspEngine Dsp { get { return mDsp; } }
            public WasapiAudioEngine Audio { get { return null; } }
            public AppStateData State { get { return mState; } }
            public bool AllowExit { get { return AllowExitFlag; } }
            public int SleepTimerRemainingSeconds { get { return 0; } }
            public int SleepTimerPresetMinutes { get { return 0; } }
            public string ActiveRoutineId { get { return ""; } }
            public string ActiveRoutineTitle { get { return ""; } }
            public int ActiveRoutineStepIndex { get { return 0; } }
            public int ActiveRoutineStepCount { get { return 0; } }
            public string ActiveRoutineStepLabel { get { return ""; } }
            public int RoutineStepRemainingSeconds { get { return 0; } }
            public int RoutineTotalRemainingSeconds { get { return 0; } }
            public string LastSoftenNotice { get { return ""; } }
            public long PlaybackElapsedSeconds { get { return 0; } }
            public string ActiveEndpointDisplayName { get { return "Test"; } }

            public void PlayPreset(PresetItem preset) { }
            public void PlayAdjacentPreset(int delta) { }
            public void StopPlayback() { }
            public void ApplyVolume(int percent, bool muted) { }
            public void ApplyAmbientBed(int ambientType, int mixPercent) { }
            public void ApplyRememberPerPresetVolume(bool enabled) { }
            public void ApplyGentleSleepFade(bool enabled) { }
            public void StartRoutine(string routineId) { }
            public void AdvanceRoutineStep() { }
            public void StopRoutineOnly() { }
            public void ApplyOutputProfile(PcOutputProfile profile) { }
            public void ApplyDevice(string deviceId) { }
            public void ApplyUltrasonicGuard(bool enabled) { }
            public void SetScopeShowComposite(bool showComposite) { }
            public void SetPackFilter(string packId) { }
            public void ReloadPacks() { }
            public void OpenPacksFolder() { }
            public void ShowAboutDialog() { }
            public void ShowBeginnerGuideDialog() { }
            public void ShowPresetDetailDialog(PresetItem preset) { }
            public void OpenDocumentationFile(string kind) { }
            public void SaveLastTab(int tabIndex) { }
            public void SwitchToMiniMode(bool mini)
            {
                LastMiniFlag = mini;
                if (!mini) SwitchToFullCount++;
            }
            public void HideToTray() { }
            public void ShutdownCompletely() { }
            public bool IsFavorite(int presetId) { return false; }
            public void ToggleFavorite(int presetId) { }
            public void SetSleepTimerMinutes(int minutes) { }
        }

        private static int RunSelfTest(string baseDir)
        {
            sSelfPassed = 0;

            // 1. Core 48 + PC Pro 16 + Journey 24 == 88 presets
            IList<PresetItem> core = PresetCatalog.LoadAll(baseDir);
            Assert(core.Count == 48, "48 core presets preserved for Android parity");

            CatalogLoadResult cat = PresetCatalog.LoadAllWithPacks(baseDir, null);
            Assert(cat.Presets.Count == 88, "Core 48 + Pro 16 + Journey 24 == 88 scenarios");
            Assert(cat.Packs.Count >= 3, "Core + Pro + Journey pack metadata loaded");
            Assert(cat.SkippedFiles.Count == 0, "Bundled packs validated cleanly with zero skipped files");
            IList<PresetItem> all = cat.Presets;

            // Test external pack hot-loading + corrupted/incompatible schema graceful skip completely read-only in memory
            List<KeyValuePair<string, string>> memPacks = new List<KeyValuePair<string, string>>();
            string bundledProPath = Path.Combine(baseDir, "packs", "vorta_pc_pro_pack_v1.json");
            if (File.Exists(bundledProPath))
            {
                memPacks.Add(new KeyValuePair<string, string>(bundledProPath, File.ReadAllText(bundledProPath, Encoding.UTF8)));
            }
            string bundledJourneyPath = Path.Combine(baseDir, "packs", "vorta_journey_pack_v1.json");
            if (File.Exists(bundledJourneyPath))
            {
                memPacks.Add(new KeyValuePair<string, string>(bundledJourneyPath, File.ReadAllText(bundledJourneyPath, Encoding.UTF8)));
            }
            memPacks.Add(new KeyValuePair<string, string>("valid_tmp.json",
                "{\"schemaVersion\":1,\"packId\":\"tmp-test-pack\",\"packName\":\"임시 테스트 팩\",\"packVersion\":\"1.0.0\",\"presets\":[{\"id\":9001,\"shortCode\":\"T01\",\"tabIndex\":2,\"title\":\"임시 집중 팩\",\"badge\":\"테스트\",\"desc\":\"테스트\",\"freq1\":220,\"freq2\":230,\"isSweep\":false,\"sweepStart\":0,\"sweepEnd\":0,\"sweepDurationSec\":0,\"isAntiPhase\":false,\"binauralBeat\":10,\"noiseType\":0,\"fmMod\":false,\"fmDepth\":0,\"fmRate\":0,\"amMod\":false,\"amDepth\":0,\"amRate\":0}]}"));
            memPacks.Add(new KeyValuePair<string, string>("bad_corrupt.json", "{ invalid json !!!"));
            memPacks.Add(new KeyValuePair<string, string>("bad_schema.json", "{\"schemaVersion\":99,\"packId\":\"bad\",\"packName\":\"Bad\",\"presets\":[]}"));

            CatalogLoadResult tmpRes = PresetCatalog.LoadAllWithPackPayloads(baseDir, memPacks);
            Assert(tmpRes.Presets.Count == 89, "Valid external pack loaded cleanly alongside core+pro+journey without filesystem writes");
            Assert(tmpRes.SkippedFiles.Count == 2, "Corrupted and future-schema packs skipped gracefully without crash");

            // 1B. JourneyOrderPolicy 88/88 Stage Coverage, Tab 5 Order, 4 Auto-Courses & PresetAdvisor Clarity Checks
            HashSet<int> allIdSet = new HashSet<int>();
            for (int i = 0; i < all.Count; i++) allIdSet.Add(all[i].Id);
            HashSet<int> stagedSet = new HashSet<int>();
            for (int t = 0; t < TabSyncPolicy.TAB_COUNT; t++)
            {
                StageGroup[] groups = JourneyOrderPolicy.GetStagesForTab(t);
                Assert(groups.Length >= 3, "At least 3 stages in tab=" + t);
                int prevStageNum = 0;
                for (int g = 0; g < groups.Length; g++)
                {
                    Assert(groups[g].TabIndex == t, "Stage TabIndex matches " + t);
                    Assert(groups[g].StageNum > prevStageNum, "Stage numbers strictly ascending");
                    prevStageNum = groups[g].StageNum;
                    Assert(groups[g].PresetIds.Length > 0, "Non-empty stage " + groups[g].Title);
                    for (int k = 0; k < groups[g].PresetIds.Length; k++)
                    {
                        int pid = groups[g].PresetIds[k];
                        Assert(stagedSet.Add(pid), "No duplicate staged ID=" + pid);
                        PresetItem pm = PresetCatalog.FindById(all, pid);
                        Assert(pm != null && pm.Id == pid && pm.TabIndex == t, "Staged preset valid for tab " + t + " id=" + pid);
                    }
                }
            }
            Assert(stagedSet.Count == 88 && stagedSet.SetEquals(allIdSet), "StageGroups cover 100% of 88 presets");
            Assert(JourneyOrderPolicy.GetNextOrderedPresetId(all, 201, 1) == 202, "Workout chronological next 201->202");
            Assert(JourneyOrderPolicy.GetNextOrderedPresetId(all, 202, 1) == 35, "Workout chronological next 202->35");
            Assert(JourneyOrderPolicy.GetNextOrderedPresetId(all, 39, 1) == 219, "Tab 5 -> Tab 6 wrap");

            IList<RoutineCourse> courses = JourneyOrderPolicy.GetAllRoutines();
            Assert(courses.Count == 4, "4 Auto-Courses present");
            for (int c = 0; c < courses.Count; c++)
            {
                Assert(courses[c].Steps.Length >= 3 && courses[c].GetTotalMinutes() >= 55, "Course valid duration: " + courses[c].Id);
                for (int stIdx = 0; stIdx < courses[c].Steps.Length; stIdx++)
                {
                    PresetItem stepPm = PresetCatalog.FindById(all, courses[c].Steps[stIdx].PresetId);
                    Assert(stepPm != null && stepPm.Id == courses[c].Steps[stIdx].PresetId, "Course step valid ID");
                }
            }

            string glossaryTxt = PresetAdvisor.GetBeginnerGlossaryText();
            Assert(glossaryTxt.Contains("포모도로") && glossaryTxt.Contains("4-6 호흡") && glossaryTxt.Contains("딥워크"), "Glossary contains plain terms");
            string[] synQueries = new string[] { "포모도로", "잠", "헬스", "워밍업", "쿨다운", "근육통", "거북목", "층간", "40hz" };
            for (int qIdx = 0; qIdx < synQueries.Length; qIdx++)
            {
                int hits = 0;
                for (int i = 0; i < all.Count; i++)
                {
                    if (PresetAdvisor.MatchesQuery(all[i], TabSyncPolicy.TAB_SHORT_NAMES[all[i].TabIndex], synQueries[qIdx])) hits++;
                }
                Assert(hits >= 1, "Search synonym hits >= 1 for " + synQueries[qIdx]);
            }
            for (int i = 0; i < all.Count; i++)
            {
                PresetItem ap = all[i];
                string tb = ap.Title + " " + ap.Badge;
                Assert(!tb.Contains("CNS") && !tb.Contains("DOMS") && !tb.Contains("VDT") && !tb.Contains("HIIT"), "No unexplained jargon acronym in id=" + ap.Id);
                string exp = PresetAdvisor.BuildPlainExplanation(ap);
                Assert(!string.IsNullOrEmpty(exp) && exp.Contains("[이게 뭔가요?]") && exp.Contains("[참고 원리]"), "Plain explanation built for id=" + ap.Id);
            }
            Assert(PresetAdvisor.ShouldWarnHighVolumeSwitch(PresetCatalog.FindById(all, 45), PresetCatalog.FindById(all, 13), 85), "High volume safe switch warns for calm preset");

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

            // 7B. Binaural Progression Ramp (J01/J08), Stereo Pink (J07), Ambient Bed 60%@120%, Profile De-clicker & 20s Gentle Stop
            {
                PresetItem j01 = PresetCatalog.FindById(all, 201);
                AegisDspEngine rampDsp = new AegisDspEngine(48000);
                rampDsp.SetOutputProfile(PcOutputProfile.PcSpeaker);
                rampDsp.SetVolume(100, false);
                rampDsp.PlayPreset(j01);
                Assert(rampDsp.IsPcCompensationActive, "PcSpeaker compensation active on J01 binaural ramp");
                float[] sBuf = new float[48000 * 2];
                rampDsp.RenderStereoFloat(sBuf, 48000);
                float beatStart = rampDsp.LiveBeatHz;
                for (int sec = 1; sec < 150; sec++) rampDsp.RenderStereoFloat(sBuf, 48000);
                float beatMid = rampDsp.LiveBeatHz;
                for (int sec = 150; sec < 310; sec++) rampDsp.RenderStereoFloat(sBuf, 48000);
                float beatEndHold = rampDsp.LiveBeatHz;
                Assert(beatStart >= 9.9f && beatStart < 10.5f, "J01 ramp starts near 10Hz");
                Assert(beatMid > beatStart + 2.0f && beatMid < 17.5f, "J01 ramp progresses upward");
                AssertNear(beatEndHold, 18.0, 0.05, "J01 ramp clamps & holds at 18.0 Hz past 300s");
                Assert(rampDsp.OvershootCount == 0, "J01 binaural ramp zero overshoot");

                // True stereo decorrelated Pink noise (J07 id=207, steady state after fade-in)
                PresetItem j07 = PresetCatalog.FindById(all, 207);
                AegisDspEngine pinkDsp = new AegisDspEngine(48000);
                pinkDsp.SetOutputProfile(PcOutputProfile.PureRaw);
                pinkDsp.SetVolume(100, false);
                pinkDsp.PlayPreset(j07);
                float[] pBuf = new float[4096 * 2];
                for (int w = 0; w < 6; w++) pinkDsp.RenderStereoFloat(pBuf, 4096);
                double maxLrPink = 0.0;
                for (int k = 0; k < 4096; k++)
                {
                    double d = Math.Abs(pBuf[k * 2] - pBuf[k * 2 + 1]);
                    if (d > maxLrPink) maxLrPink = d;
                }
                Assert(maxLrPink > 0.01, "True stereo decorrelated Pink noise L != R (" + maxLrPink.ToString("F4", CultureInfo.InvariantCulture) + ")");

                // Ambient bed 60% @ 120% boost + ultrasonic bypass
                int[] ambModes = new int[] { AegisDspEngine.AMBIENT_BROWN, AegisDspEngine.AMBIENT_PINK, AegisDspEngine.AMBIENT_RAIN };
                for (int am = 0; am < ambModes.Length; am++)
                {
                    AegisDspEngine ambDsp = new AegisDspEngine(48000);
                    ambDsp.SetOutputProfile(PcOutputProfile.PcSpeaker);
                    ambDsp.SetVolume(120, false);
                    ambDsp.SetAmbientBed(ambModes[am], 60);
                    ambDsp.PlayPreset(PresetCatalog.FindById(all, 102));
                    float[] aBuf = new float[480 * 2];
                    for (int b = 0; b < 60; b++)
                    {
                        ambDsp.RenderStereoFloat(aBuf, 480);
                        for (int k = 0; k < aBuf.Length; k++)
                        {
                            Assert(Math.Abs(aBuf[k]) <= (float)(AegisDspEngine.CLEAN_BOOST_CEILING + 1e-6), "Ambient 60% @ 120% within -0.50 dBTP");
                        }
                    }
                    Assert(ambDsp.OvershootCount == 0, "Ambient bed mode " + ambModes[am] + " zero overshoots");
                }

                // Post-AM preset switch + profile-switch de-clicker
                AegisDspEngine profSw = new AegisDspEngine(48000);
                profSw.SetVolume(100, false);
                profSw.SetOutputProfile(PcOutputProfile.PcSpeaker);
                profSw.PlayPreset(PresetCatalog.FindById(all, 109));
                float[] swBuf = new float[512 * 2];
                for (int b = 0; b < 30; b++) profSw.RenderStereoFloat(swBuf, 512);
                float endAm = swBuf[(512 - 1) * 2];
                profSw.PlayPreset(PresetCatalog.FindById(all, 35));
                profSw.RenderStereoFloat(swBuf, 512);
                Assert(Math.Abs(swBuf[0] - endAm) < 0.025, "Post-AM de-clicker keeps transition continuous");
                profSw.SetOutputProfile(PcOutputProfile.PureRaw);
                float beforeProf = swBuf[(512 - 1) * 2];
                profSw.RenderStereoFloat(swBuf, 512);
                Assert(Math.Abs(swBuf[0] - beforeProf) < 0.025, "Profile switch de-clicker keeps transition continuous");
                Assert(profSw.OvershootCount == 0, "Profile switch zero overshoots");

                // RequestGentleStop squared-envelope fade-out
                profSw.RequestGentleStop(2.0);
                Assert(profSw.IsGentleFadingOut, "Gentle fade-out active flag set");
                for (int b = 0; b < 200; b++) profSw.RenderStereoFloat(swBuf, 512);
                Assert(!profSw.IsActive && Math.Abs(swBuf[(512 - 1) * 2]) < 1e-6f, "Gentle fade-out completes cleanly to silence");
            }

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
            st.AmbientType = AegisDspEngine.AMBIENT_RAIN;
            st.AmbientMixPercent = 90;
            st.PerPresetVolumes["102"] = 150;
            st.PerPresetVolumes["badkey"] = 50;
            AppStateStore.Sanitize(st);
            AssertNear(st.ConsolePaneWidth, 340.0, 1e-6, "ConsolePaneWidth clamps min 340");
            Assert(st.AmbientType == AegisDspEngine.AMBIENT_RAIN && st.AmbientMixPercent == 60, "Ambient mix clamps max 60");
            Assert(st.PerPresetVolumes.Count == 1 && st.PerPresetVolumes["102"] == 120, "PerPresetVolumes sanitized with string keys");
            System.Web.Script.Serialization.JavaScriptSerializer stateSer = new System.Web.Script.Serialization.JavaScriptSerializer();
            string rtJson = stateSer.Serialize(st);
            AppStateData rtObj = stateSer.Deserialize<AppStateData>(rtJson);
            Assert(rtObj != null && rtObj.PerPresetVolumes != null && rtObj.PerPresetVolumes["102"] == 120, "AppStateData JSON round-trip succeeds");

            // 10. UpdateChecker SemVer 2.0 (incl. pre-release precedence) & WPF dark XAML styles
            Assert(UpdateChecker.CompareSemVer("3.2.0", "3.2.0") == 0, "SemVer equal");
            Assert(UpdateChecker.CompareSemVer("3.2.0", "3.3.0") < 0, "SemVer upgrade detected");
            Assert(UpdateChecker.CompareSemVer("3.10.0", "3.2.9") > 0, "SemVer numeric comparison");
            Assert(UpdateChecker.CompareSemVer("3.2.0-rc1", "3.2.0") < 0, "SemVer pre-release lower than release");
            Assert(UpdateChecker.CompareSemVer("3.2.0-beta.2", "3.2.0-beta.10") < 0, "SemVer pre-release numeric precedence");
            Assert(UpdateChecker.CompareSemVer("1.0.0--1", "1.0.0-0") > 0, "SemVer non-digit identifier higher than numeric 0");
            Assert(UpdateChecker.CompareSemVer("1.0.0-alpha", "1.0.0-1") > 0, "SemVer alpha higher than numeric");
            Assert(UpdateChecker.CompareSemVer("1.0.0-100000000000000000000", "1.0.0-99999999999999999999") > 0, "SemVer arbitrary-length integer pre-release comparison");
            Assert(UpdateChecker.CompareSemVer("1.0.0-99999999999999999999", "1.0.0-100000000000000000000") < 0, "SemVer arbitrary-length integer pre-release reverse comparison");
            Assert(ThemePalette.CreateDarkSliderStyle() != null, "DarkSliderStyle parses");
            Assert(ThemePalette.CreateDarkComboStyle() != null, "DarkComboStyle parses");
            Assert(ThemePalette.CreateDarkScrollBarStyle() != null, "DarkScrollBarStyle parses");
            Assert(ThemePalette.CreateDarkSplitterStyle() != null, "DarkSplitterStyle parses");

            // 11. MiniBarWindow "전체" restore regression check (MouseLeftButtonDown must stop before DragMove)
            MiniBarTestController miniStub = new MiniBarTestController(all);
            MiniBarWindow miniWin = new MiniBarWindow(miniStub);
            try
            {
                Assert(miniWin.RestoreFullButton != null, "MiniBar RestoreFullButton exposed");
                System.Windows.Input.MouseButtonEventArgs downArgs = new System.Windows.Input.MouseButtonEventArgs(
                    System.Windows.Input.Mouse.PrimaryDevice,
                    Environment.TickCount,
                    System.Windows.Input.MouseButton.Left);
                downArgs.RoutedEvent = UIElement.MouseLeftButtonDownEvent;
                miniWin.RestoreFullButton.RaiseEvent(downArgs);
                Assert(downArgs.Handled, "MiniBar RestoreFullButton marks MouseLeftButtonDown handled before DragMove");

                System.Windows.Input.MouseButtonEventArgs upArgs = new System.Windows.Input.MouseButtonEventArgs(
                    System.Windows.Input.Mouse.PrimaryDevice,
                    Environment.TickCount,
                    System.Windows.Input.MouseButton.Left);
                upArgs.RoutedEvent = UIElement.MouseLeftButtonUpEvent;
                miniWin.RestoreFullButton.RaiseEvent(upArgs);
                Assert(upArgs.Handled, "MiniBar RestoreFullButton marks MouseLeftButtonUp handled");
                Assert(miniStub.SwitchToFullCount == 1, "MiniBar 전체 button switches back to full panel");
                Assert(!miniStub.LastMiniFlag, "MiniBar 전체 button passes mini=false");
            }
            finally
            {
                miniStub.AllowExitFlag = true;
                miniWin.Close();
            }

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
