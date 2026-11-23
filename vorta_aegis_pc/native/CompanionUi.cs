using System;
using System.Collections.Generic;
using System.ComponentModel;
using System.Globalization;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Markup;
using System.Windows.Media;
using System.Windows.Shapes;
using System.Windows.Shell;
using System.Windows.Threading;

namespace VortaAegisPC
{
    public interface IAppHostController
    {
        IList<PresetItem> Presets { get; }
        AegisDspEngine Dsp { get; }
        WasapiAudioEngine Audio { get; }
        AppStateData State { get; }
        bool AllowExit { get; }
        int SleepTimerRemainingSeconds { get; }
        int SleepTimerPresetMinutes { get; }

        void PlayPreset(PresetItem preset);
        void PlayAdjacentPreset(int delta);
        void StopPlayback();
        void ApplyVolume(int percent, bool muted);
        void ApplyOutputProfile(PcOutputProfile profile);
        void ApplyDevice(string deviceId);
        void ApplyUltrasonicGuard(bool enabled);
        void SaveLastTab(int tabIndex);
        void SwitchToMiniMode(bool mini);
        void HideToTray();
        void ShutdownCompletely();
        bool IsFavorite(int presetId);
        void ToggleFavorite(int presetId);
        void SetSleepTimerMinutes(int minutes);
    }

    internal static class ThemePalette
    {
        public static readonly Color Bg = Color.FromRgb(0x0B, 0x0D, 0x11);
        public static readonly Color Surface = Color.FromRgb(0x12, 0x15, 0x1C);
        public static readonly Color ScopeBg = Color.FromRgb(0x07, 0x0A, 0x0F);
        public static readonly Color ScopeBorder = Color.FromRgb(0x20, 0x28, 0x38);
        public static readonly Color SurfaceCard = Color.FromRgb(0x16, 0x1A, 0x23);
        public static readonly Color SurfaceHigh = Color.FromRgb(0x1E, 0x23, 0x30);
        public static readonly Color SurfaceHover = Color.FromRgb(0x26, 0x2C, 0x3B);
        public static readonly Color Outline = Color.FromRgb(0x27, 0x2D, 0x3B);
        public static readonly Color OutlineHover = Color.FromRgb(0x3B, 0x44, 0x59);
        public static readonly Color Amber = Color.FromRgb(0xEA, 0xB3, 0x48);
        public static readonly Color AmberBg = Color.FromRgb(0x29, 0x21, 0x14);
        public static readonly Color Active = Color.FromRgb(0x00, 0xE6, 0x76);
        public static readonly Color ActiveBg = Color.FromRgb(0x0E, 0x23, 0x1B);
        public static readonly Color ActiveOnLight = Color.FromRgb(0x00, 0xA8, 0x54);
        public static readonly Color Cyan = Color.FromRgb(0x38, 0xBD, 0xF8);
        public static readonly Color CyanBg = Color.FromRgb(0x0E, 0x22, 0x33);
        public static readonly Color Stopped = Color.FromRgb(0xFF, 0x4D, 0x6A);
        public static readonly Color StopBtn = Color.FromRgb(0xC9, 0x2A, 0x42);
        public static readonly Color StopBtnHover = Color.FromRgb(0xE0, 0x35, 0x4F);
        public static readonly Color CloseHover = Color.FromRgb(0x3D, 0x1C, 0x24);
        public static readonly Color TextPrimary = Color.FromRgb(0xF3, 0xF5, 0xF8);
        public static readonly Color TextSecondary = Color.FromRgb(0x9C, 0xA3, 0xB0);
        public static readonly Color TextTertiary = Color.FromRgb(0x64, 0x6C, 0x7C);

        public static Brush BrBg { get { return Freeze(new SolidColorBrush(Bg)); } }
        public static Brush BrSurface { get { return Freeze(new SolidColorBrush(Surface)); } }
        public static Brush BrScopeBg { get { return Freeze(new SolidColorBrush(ScopeBg)); } }
        public static Brush BrScopeBorder { get { return Freeze(new SolidColorBrush(ScopeBorder)); } }
        public static Brush BrSurfaceCard { get { return Freeze(new SolidColorBrush(SurfaceCard)); } }
        public static Brush BrSurfaceHigh { get { return Freeze(new SolidColorBrush(SurfaceHigh)); } }
        public static Brush BrSurfaceHover { get { return Freeze(new SolidColorBrush(SurfaceHover)); } }
        public static Brush BrOutline { get { return Freeze(new SolidColorBrush(Outline)); } }
        public static Brush BrOutlineHover { get { return Freeze(new SolidColorBrush(OutlineHover)); } }
        public static Brush BrAmber { get { return Freeze(new SolidColorBrush(Amber)); } }
        public static Brush BrAmberBg { get { return Freeze(new SolidColorBrush(AmberBg)); } }
        public static Brush BrActive { get { return Freeze(new SolidColorBrush(Active)); } }
        public static Brush BrActiveBg { get { return Freeze(new SolidColorBrush(ActiveBg)); } }
        public static Brush BrActiveOnLight { get { return Freeze(new SolidColorBrush(ActiveOnLight)); } }
        public static Brush BrCyan { get { return Freeze(new SolidColorBrush(Cyan)); } }
        public static Brush BrCyanBg { get { return Freeze(new SolidColorBrush(CyanBg)); } }
        public static Brush BrStopped { get { return Freeze(new SolidColorBrush(Stopped)); } }
        public static Brush BrStopBtn { get { return Freeze(new SolidColorBrush(StopBtn)); } }
        public static Brush BrStopBtnHover { get { return Freeze(new SolidColorBrush(StopBtnHover)); } }
        public static Brush BrCloseHover { get { return Freeze(new SolidColorBrush(CloseHover)); } }
        public static Brush BrTextPrimary { get { return Freeze(new SolidColorBrush(TextPrimary)); } }
        public static Brush BrTextSecondary { get { return Freeze(new SolidColorBrush(TextSecondary)); } }
        public static Brush BrTextTertiary { get { return Freeze(new SolidColorBrush(TextTertiary)); } }

        public static readonly FontFamily FontUi = new FontFamily("Malgun Gothic, Segoe UI");
        public static readonly FontFamily FontMono = new FontFamily("Consolas, Cascadia Mono");

        private static Brush Freeze(SolidColorBrush b)
        {
            b.Freeze();
            return b;
        }

        public static Canvas MakeIcon(string pathData, double size, Brush brush, bool isStroke, double strokeWidth)
        {
            Canvas cv = new Canvas();
            cv.Width = size;
            cv.Height = size;
            cv.HorizontalAlignment = HorizontalAlignment.Center;
            cv.VerticalAlignment = VerticalAlignment.Center;
            cv.SnapsToDevicePixels = true;

            System.Windows.Shapes.Path p = new System.Windows.Shapes.Path();
            p.Data = Geometry.Parse(pathData);
            if (isStroke)
            {
                p.Stroke = brush;
                p.StrokeThickness = strokeWidth;
                p.StrokeStartLineCap = PenLineCap.Round;
                p.StrokeEndLineCap = PenLineCap.Round;
                p.StrokeLineJoin = PenLineJoin.Round;
            }
            else
            {
                p.Fill = brush;
            }
            cv.Children.Add(p);
            return cv;
        }

        public static LinearGradientBrush MakeVerticalFillBrush(Color baseColor, byte alphaEdge)
        {
            LinearGradientBrush lgb = new LinearGradientBrush();
            lgb.StartPoint = new Point(0, 0);
            lgb.EndPoint = new Point(0, 1);
            lgb.GradientStops.Add(new GradientStop(Color.FromArgb(alphaEdge, baseColor.R, baseColor.G, baseColor.B), 0.0));
            lgb.GradientStops.Add(new GradientStop(Color.FromArgb(0x06, baseColor.R, baseColor.G, baseColor.B), 0.5));
            lgb.GradientStops.Add(new GradientStop(Color.FromArgb(alphaEdge, baseColor.R, baseColor.G, baseColor.B), 1.0));
            lgb.Freeze();
            return lgb;
        }

        public static Style CreateDarkSliderStyle()
        {
            string xaml =
                "<Style xmlns='http://schemas.microsoft.com/winfx/2006/xaml/presentation' xmlns:x='http://schemas.microsoft.com/winfx/2006/xaml' TargetType='Slider'>" +
                "<Setter Property='Template'><Setter.Value><ControlTemplate TargetType='Slider'>" +
                "<Grid VerticalAlignment='Center' Height='22'>" +
                "<Border Height='4' CornerRadius='2' Background='#272D3B' VerticalAlignment='Center'/>" +
                "<Track x:Name='PART_Track'>" +
                "<Track.DecreaseRepeatButton><RepeatButton Command='Slider.DecreaseLarge'>" +
                "<RepeatButton.Template><ControlTemplate TargetType='RepeatButton'>" +
                "<Border Height='4' CornerRadius='2' Background='#EAB348' VerticalAlignment='Center'/>" +
                "</ControlTemplate></RepeatButton.Template></RepeatButton></Track.DecreaseRepeatButton>" +
                "<Track.IncreaseRepeatButton><RepeatButton Command='Slider.IncreaseLarge'>" +
                "<RepeatButton.Template><ControlTemplate TargetType='RepeatButton'>" +
                "<Border Height='4' Background='Transparent'/>" +
                "</ControlTemplate></RepeatButton.Template></RepeatButton></Track.IncreaseRepeatButton>" +
                "<Track.Thumb><Thumb Width='15' Height='15'>" +
                "<Thumb.Template><ControlTemplate TargetType='Thumb'>" +
                "<Ellipse Fill='#F3F5F8' Stroke='#EAB348' StrokeThickness='2'/>" +
                "</ControlTemplate></Thumb.Template></Thumb></Track.Thumb>" +
                "</Track></Grid></ControlTemplate></Setter.Value></Setter></Style>";
            return (Style)XamlReader.Parse(xaml);
        }

        public static Style CreateDarkComboStyle()
        {
            string xaml =
                "<Style xmlns='http://schemas.microsoft.com/winfx/2006/xaml/presentation' xmlns:x='http://schemas.microsoft.com/winfx/2006/xaml' TargetType='ComboBox'>" +
                "<Setter Property='Foreground' Value='#F3F5F8'/>" +
                "<Setter Property='Background' Value='#1E2330'/>" +
                "<Setter Property='BorderBrush' Value='#272D3B'/>" +
                "<Setter Property='BorderThickness' Value='1'/>" +
                "<Setter Property='Padding' Value='8,4'/>" +
                "<Setter Property='FontSize' Value='11'/>" +
                "<Setter Property='Template'><Setter.Value><ControlTemplate TargetType='ComboBox'>" +
                "<Grid>" +
                "<ToggleButton x:Name='ToggleButton' Focusable='false' ClickMode='Press' " +
                "IsChecked='{Binding IsDropDownOpen, Mode=TwoWay, RelativeSource={RelativeSource TemplatedParent}}'>" +
                "<ToggleButton.Template><ControlTemplate TargetType='ToggleButton'>" +
                "<Border Background='#1E2330' BorderBrush='#272D3B' BorderThickness='1' CornerRadius='6'>" +
                "<Grid><Grid.ColumnDefinitions><ColumnDefinition Width='*'/><ColumnDefinition Width='22'/></Grid.ColumnDefinitions>" +
                "<Path Grid.Column='1' HorizontalAlignment='Center' VerticalAlignment='Center' Data='M0,0 L4,4 L8,0 Z' Fill='#9CA3B0'/>" +
                "</Grid></Border></ControlTemplate></ToggleButton.Template></ToggleButton>" +
                "<ContentPresenter Margin='9,4,24,4' IsHitTestVisible='False' VerticalAlignment='Center' " +
                "Content='{TemplateBinding SelectionBoxItem}' ContentTemplate='{TemplateBinding SelectionBoxItemTemplate}'/>" +
                "<Popup x:Name='Popup' Placement='Bottom' IsOpen='{TemplateBinding IsDropDownOpen}' AllowsTransparency='True' Focusable='False' PopupAnimation='Fade'>" +
                "<Border Background='#12151C' BorderBrush='#272D3B' BorderThickness='1' CornerRadius='6' MinWidth='{TemplateBinding ActualWidth}' MaxHeight='220' SnapsToDevicePixels='True'>" +
                "<ScrollViewer VerticalScrollBarVisibility='Auto'><StackPanel IsItemsHost='True'/></ScrollViewer>" +
                "</Border></Popup></Grid></ControlTemplate></Setter.Value></Setter>" +
                "<Setter Property='ItemContainerStyle'><Setter.Value><Style TargetType='ComboBoxItem'>" +
                "<Setter Property='Foreground' Value='#F3F5F8'/>" +
                "<Setter Property='Background' Value='#12151C'/>" +
                "<Setter Property='Padding' Value='8,6'/>" +
                "<Setter Property='Template'><Setter.Value><ControlTemplate TargetType='ComboBoxItem'>" +
                "<Border Background='{TemplateBinding Background}' Padding='{TemplateBinding Padding}'><ContentPresenter/></Border>" +
                "</ControlTemplate></Setter.Value></Setter>" +
                "<Style.Triggers><Trigger Property='IsHighlighted' Value='True'>" +
                "<Setter Property='Background' Value='#1E2330'/>" +
                "<Setter Property='Foreground' Value='#EAB348'/>" +
                "</Trigger></Style.Triggers></Style></Setter.Value></Setter></Style>";
            return (Style)XamlReader.Parse(xaml);
        }

        public static Style CreateDarkScrollBarStyle()
        {
            const string xaml =
                "<Style xmlns='http://schemas.microsoft.com/winfx/2006/xaml/presentation' " +
                "xmlns:x='http://schemas.microsoft.com/winfx/2006/xaml' TargetType='ScrollBar'>" +
                "<Setter Property='OverridesDefaultStyle' Value='True'/>" +
                "<Setter Property='Width' Value='6'/>" +
                "<Setter Property='MinWidth' Value='6'/>" +
                "<Setter Property='Background' Value='Transparent'/>" +
                "<Setter Property='Template'><Setter.Value>" +
                "<ControlTemplate TargetType='ScrollBar'>" +
                "<Grid x:Name='Bg' Width='6' SnapsToDevicePixels='True' Background='{TemplateBinding Background}'>" +
                "<Track x:Name='PART_Track' IsDirectionReversed='True'>" +
                "<Track.DecreaseRepeatButton><RepeatButton Opacity='0' Focusable='False' Command='ScrollBar.PageUpCommand'/></Track.DecreaseRepeatButton>" +
                "<Track.IncreaseRepeatButton><RepeatButton Opacity='0' Focusable='False' Command='ScrollBar.PageDownCommand'/></Track.IncreaseRepeatButton>" +
                "<Track.Thumb><Thumb x:Name='Thumb'>" +
                "<Thumb.Style><Style TargetType='Thumb'>" +
                "<Setter Property='OverridesDefaultStyle' Value='True'/>" +
                "<Setter Property='IsTabStop' Value='False'/>" +
                "<Setter Property='Template'><Setter.Value><ControlTemplate TargetType='Thumb'>" +
                "<Border x:Name='ThumbRect' Background='#2D3444' CornerRadius='3' Margin='1,0,1,0'/>" +
                "<ControlTemplate.Triggers>" +
                "<Trigger Property='IsMouseOver' Value='True'><Setter TargetName='ThumbRect' Property='Background' Value='#EAB348'/></Trigger>" +
                "<Trigger Property='IsDragging' Value='True'><Setter TargetName='ThumbRect' Property='Background' Value='#EAB348'/></Trigger>" +
                "</ControlTemplate.Triggers>" +
                "</ControlTemplate></Setter.Value></Setter>" +
                "</Style></Thumb.Style>" +
                "</Thumb></Track.Thumb>" +
                "</Track></Grid>" +
                "</ControlTemplate></Setter.Value></Setter></Style>";
            return (Style)XamlReader.Parse(xaml);
        }
    }

    internal sealed class LiveWaveformScope : Border
    {
        private const int DRAW_POINTS = 220;

        private readonly float[] mSnapL = new float[AegisDspEngine.SCOPE_RING_SIZE];
        private readonly float[] mSnapR = new float[AegisDspEngine.SCOPE_RING_SIZE];
        private readonly Grid mRootGrid;
        private readonly Canvas mGridCanvas;
        private readonly System.Windows.Shapes.Path mWaveFillL;
        private readonly System.Windows.Shapes.Path mWavePathR;
        private readonly System.Windows.Shapes.Path mWavePathL;
        private readonly StreamGeometry mGeoFillL;
        private readonly StreamGeometry mGeoR;
        private readonly StreamGeometry mGeoL;

        private readonly Line mAxisH;
        private readonly Line mCeilTop;
        private readonly Line mCeilBot;
        private readonly Line mGridV1;
        private readonly Line mGridV2;
        private readonly Line mGridV3;

        private readonly TextBlock mTxtLegend;
        private readonly TextBlock mTxtCeiling;
        private readonly TextBlock mTxtTimebase;
        private AegisDspEngine mLastDsp;
        private Brush mFillEmerald;
        private Brush mFillAmber;
        private Brush mFillRose;

        public LiveWaveformScope()
        {
            Background = ThemePalette.BrScopeBg;
            BorderBrush = ThemePalette.BrScopeBorder;
            BorderThickness = new Thickness(1);
            CornerRadius = new CornerRadius(10);
            ClipToBounds = true;
            Height = 104;

            mFillEmerald = ThemePalette.MakeVerticalFillBrush(ThemePalette.Active, 0x26);
            mFillAmber = ThemePalette.MakeVerticalFillBrush(ThemePalette.Amber, 0x24);
            mFillRose = ThemePalette.MakeVerticalFillBrush(ThemePalette.Stopped, 0x26);

            mRootGrid = new Grid();
            mGridCanvas = new Canvas();
            mGridCanvas.IsHitTestVisible = false;

            Brush railBr = new SolidColorBrush(Color.FromRgb(0x1C, 0x22, 0x30));
            railBr.Freeze();
            Brush ceilBr = new SolidColorBrush(Color.FromRgb(0x28, 0x32, 0x46));
            ceilBr.Freeze();
            DoubleCollection dash = new DoubleCollection(new double[] { 3.0, 3.0 });
            dash.Freeze();

            mAxisH = new Line { Stroke = railBr, StrokeThickness = 1 };
            mCeilTop = new Line { Stroke = ceilBr, StrokeThickness = 1, StrokeDashArray = dash };
            mCeilBot = new Line { Stroke = ceilBr, StrokeThickness = 1, StrokeDashArray = dash };
            mGridV1 = new Line { Stroke = railBr, StrokeThickness = 0.8 };
            mGridV2 = new Line { Stroke = railBr, StrokeThickness = 0.8 };
            mGridV3 = new Line { Stroke = railBr, StrokeThickness = 0.8 };

            mGridCanvas.Children.Add(mGridV1);
            mGridCanvas.Children.Add(mGridV2);
            mGridCanvas.Children.Add(mGridV3);
            mGridCanvas.Children.Add(mCeilTop);
            mGridCanvas.Children.Add(mCeilBot);
            mGridCanvas.Children.Add(mAxisH);
            mRootGrid.Children.Add(mGridCanvas);

            mGeoFillL = new StreamGeometry();
            mWaveFillL = new System.Windows.Shapes.Path();
            mWaveFillL.Data = mGeoFillL;
            mWaveFillL.Fill = mFillEmerald;
            mWaveFillL.IsHitTestVisible = false;
            mRootGrid.Children.Add(mWaveFillL);

            mGeoR = new StreamGeometry();
            mWavePathR = new System.Windows.Shapes.Path();
            mWavePathR.Data = mGeoR;
            Brush cyanStroke = new SolidColorBrush(Color.FromArgb(0xB8, ThemePalette.Cyan.R, ThemePalette.Cyan.G, ThemePalette.Cyan.B));
            cyanStroke.Freeze();
            mWavePathR.Stroke = cyanStroke;
            mWavePathR.StrokeThickness = 1.25;
            mWavePathR.StrokeLineJoin = PenLineJoin.Round;
            mWavePathR.Visibility = Visibility.Collapsed;
            mWavePathR.IsHitTestVisible = false;
            mRootGrid.Children.Add(mWavePathR);

            mGeoL = new StreamGeometry();
            mWavePathL = new System.Windows.Shapes.Path();
            mWavePathL.Data = mGeoL;
            mWavePathL.Stroke = ThemePalette.BrTextTertiary;
            mWavePathL.StrokeThickness = 1.85;
            mWavePathL.StrokeLineJoin = PenLineJoin.Round;
            mWavePathL.StrokeStartLineCap = PenLineCap.Round;
            mWavePathL.StrokeEndLineCap = PenLineCap.Round;
            mWavePathL.IsHitTestVisible = false;
            mRootGrid.Children.Add(mWavePathL);

            // Micro HUD Overlays
            mTxtLegend = new TextBlock();
            mTxtLegend.Text = "대기 중 · 무신호 기준선";
            mTxtLegend.FontFamily = ThemePalette.FontMono;
            mTxtLegend.FontSize = 9.5;
            mTxtLegend.FontWeight = FontWeights.Bold;
            mTxtLegend.Foreground = ThemePalette.BrTextTertiary;
            mTxtLegend.Margin = new Thickness(8, 5, 0, 0);
            mTxtLegend.HorizontalAlignment = HorizontalAlignment.Left;
            mTxtLegend.VerticalAlignment = VerticalAlignment.Top;
            mRootGrid.Children.Add(mTxtLegend);

            mTxtCeiling = new TextBlock();
            mTxtCeiling.Text = "-0.5 dBTP 클린 천장";
            mTxtCeiling.FontFamily = ThemePalette.FontMono;
            mTxtCeiling.FontSize = 9;
            mTxtCeiling.Foreground = ThemePalette.BrTextTertiary;
            mTxtCeiling.Margin = new Thickness(0, 5, 8, 0);
            mTxtCeiling.HorizontalAlignment = HorizontalAlignment.Right;
            mTxtCeiling.VerticalAlignment = VerticalAlignment.Top;
            mRootGrid.Children.Add(mTxtCeiling);

            mTxtTimebase = new TextBlock();
            mTxtTimebase.Text = "48kHz IEEE Float";
            mTxtTimebase.FontFamily = ThemePalette.FontMono;
            mTxtTimebase.FontSize = 9;
            mTxtTimebase.Foreground = ThemePalette.BrTextTertiary;
            mTxtTimebase.Margin = new Thickness(0, 0, 8, 4);
            mTxtTimebase.HorizontalAlignment = HorizontalAlignment.Right;
            mTxtTimebase.VerticalAlignment = VerticalAlignment.Bottom;
            mRootGrid.Children.Add(mTxtTimebase);

            Child = mRootGrid;

            SizeChanged += delegate
            {
                LayoutGraticule();
                UpdateFrame(mLastDsp);
            };
        }

        private void LayoutGraticule()
        {
            double w = mRootGrid.ActualWidth;
            double h = mRootGrid.ActualHeight;
            if (w <= 4 || h <= 4) return;

            double padY = 12.0;
            double midY = h * 0.5;
            double ampY = Math.Max(4.0, (h * 0.5) - padY);
            double ceilYTop = midY - ampY * AegisDspEngine.CLEAN_BOOST_CEILING;
            double ceilYBot = midY + ampY * AegisDspEngine.CLEAN_BOOST_CEILING;

            mAxisH.X1 = 0; mAxisH.Y1 = midY; mAxisH.X2 = w; mAxisH.Y2 = midY;
            mCeilTop.X1 = 0; mCeilTop.Y1 = ceilYTop; mCeilTop.X2 = w; mCeilTop.Y2 = ceilYTop;
            mCeilBot.X1 = 0; mCeilBot.Y1 = ceilYBot; mCeilBot.X2 = w; mCeilBot.Y2 = ceilYBot;

            mGridV1.X1 = w * 0.25; mGridV1.Y1 = 0; mGridV1.X2 = w * 0.25; mGridV1.Y2 = h;
            mGridV2.X1 = w * 0.50; mGridV2.Y1 = 0; mGridV2.X2 = w * 0.50; mGridV2.Y2 = h;
            mGridV3.X1 = w * 0.75; mGridV3.Y1 = 0; mGridV3.X2 = w * 0.75; mGridV3.Y2 = h;
        }

        public void UpdateFrame(AegisDspEngine dsp)
        {
            mLastDsp = dsp;
            double w = mRootGrid.ActualWidth;
            double h = mRootGrid.ActualHeight;
            if (w <= 8 || h <= 8) return;

            double padY = 12.0;
            double midY = h * 0.5;
            double ampY = Math.Max(4.0, (h * 0.5) - padY);

            bool playing = (dsp != null) && dsp.IsPlaying && dsp.CurrentPreset != null;
            if (!playing)
            {
                mWaveFillL.Visibility = Visibility.Collapsed;
                mWavePathR.Visibility = Visibility.Collapsed;
                mWavePathL.Stroke = ThemePalette.BrTextTertiary;
                mWavePathL.StrokeThickness = 1.4;
                using (StreamGeometryContext ctx = mGeoL.Open())
                {
                    ctx.BeginFigure(new Point(0, midY), false, false);
                    ctx.LineTo(new Point(w, midY), true, false);
                }
                mTxtLegend.Text = "대기 중 · 무신호 기준선";
                mTxtLegend.Foreground = ThemePalette.BrTextTertiary;
                mTxtCeiling.Text = "-0.5 dBTP 클린 천장";
                mTxtTimebase.Text = (dsp != null)
                    ? string.Format(CultureInfo.InvariantCulture, "{0:F0}kHz IEEE Float", dsp.SampleRate / 1000.0)
                    : "48kHz IEEE Float";
                return;
            }

            dsp.CopyScopeSnapshot(mSnapL, mSnapR);
            PresetItem p = dsp.CurrentPreset;

            // Choose auto-timebase (~3.5 cycles visible, clamped within ring buffer)
            double liveHz = Math.Max(4.0, dsp.LiveFreqHz > 0f ? dsp.LiveFreqHz : p.Freq1);
            int targetSpan = (int)Math.Round((dsp.SampleRate / liveHz) * 3.5);
            if (targetSpan < 160) targetSpan = 160;
            int maxSpan = AegisDspEngine.SCOPE_RING_SIZE - 512;
            if (targetSpan > maxSpan) targetSpan = maxSpan;

            int maxTrig = AegisDspEngine.SCOPE_RING_SIZE - targetSpan - 1;
            int start = 0;
            for (int i = 1; i < maxTrig; i++)
            {
                if (mSnapL[i - 1] <= 0.0f && mSnapL[i] > 0.002f)
                {
                    start = i;
                    break;
                }
            }

            // Detect L/R stereo phase separation (binaural or dual distinct channels)
            double maxDiffLR = 0.0;
            int checkSamples = Math.Min(targetSpan, 256);
            int diffStep = Math.Max(1, targetSpan / checkSamples);
            for (int i = 0; i < targetSpan; i += diffStep)
            {
                int idx = start + i;
                if (idx >= AegisDspEngine.SCOPE_RING_SIZE) break;
                double d = Math.Abs(mSnapL[idx] - mSnapR[idx]);
                if (d > maxDiffLR) maxDiffLR = d;
            }
            bool showR = maxDiffLR > 0.03;

            bool guarded = (p.NoiseType == 2 && dsp.IsUltrasonicGuarded);
            bool antiOrHarmonic = p.IsAntiPhase || p.Freq2 > 0f || (dsp.OutputProfile == PcOutputProfile.PcSpeaker && p.Freq1 < 85.0f && p.NoiseType != 2);

            Brush primaryStroke;
            Brush fillBrush;
            if (guarded)
            {
                primaryStroke = ThemePalette.BrStopped;
                fillBrush = mFillRose;
            }
            else if (antiOrHarmonic)
            {
                primaryStroke = ThemePalette.BrAmber;
                fillBrush = mFillAmber;
            }
            else
            {
                primaryStroke = ThemePalette.BrActive;
                fillBrush = mFillEmerald;
            }

            mWavePathL.Stroke = primaryStroke;
            mWavePathL.StrokeThickness = 1.85;
            mWaveFillL.Fill = fillBrush;
            mWaveFillL.Visibility = Visibility.Visible;

            double xStep = w / (double)(DRAW_POINTS - 1);
            double sampleStep = (double)targetSpan / (double)(DRAW_POINTS - 1);

            using (StreamGeometryContext ctxL = mGeoL.Open())
            using (StreamGeometryContext ctxFill = mGeoFillL.Open())
            {
                ctxFill.BeginFigure(new Point(0, midY), true, true);
                for (int pt = 0; pt < DRAW_POINTS; pt++)
                {
                    int idx = start + (int)Math.Round(pt * sampleStep);
                    if (idx >= AegisDspEngine.SCOPE_RING_SIZE) idx = AegisDspEngine.SCOPE_RING_SIZE - 1;
                    float s = mSnapL[idx];
                    if (float.IsNaN(s) || float.IsInfinity(s)) s = 0.0f;
                    if (s > 1.0f) s = 1.0f; else if (s < -1.0f) s = -1.0f;
                    double x = pt * xStep;
                    double y = midY - (s * ampY);
                    Point pnt = new Point(x, y);
                    if (pt == 0) ctxL.BeginFigure(pnt, false, false);
                    else ctxL.LineTo(pnt, true, false);
                    ctxFill.LineTo(pnt, false, false);
                }
                ctxFill.LineTo(new Point(w, midY), false, false);
            }

            if (showR)
            {
                mWavePathR.Visibility = Visibility.Visible;
                using (StreamGeometryContext ctxR = mGeoR.Open())
                {
                    for (int pt = 0; pt < DRAW_POINTS; pt++)
                    {
                        int idx = start + (int)Math.Round(pt * sampleStep);
                        if (idx >= AegisDspEngine.SCOPE_RING_SIZE) idx = AegisDspEngine.SCOPE_RING_SIZE - 1;
                        float s = mSnapR[idx];
                        if (float.IsNaN(s) || float.IsInfinity(s)) s = 0.0f;
                        if (s > 1.0f) s = 1.0f; else if (s < -1.0f) s = -1.0f;
                        Point pnt = new Point(pt * xStep, midY - (s * ampY));
                        if (pt == 0) ctxR.BeginFigure(pnt, false, false);
                        else ctxR.LineTo(pnt, true, false);
                    }
                }
            }
            else
            {
                mWavePathR.Visibility = Visibility.Collapsed;
            }

            if (dsp.IsMuted)
            {
                mTxtLegend.Text = "음소거 · 무신호 출력";
                mTxtLegend.Foreground = ThemePalette.BrStopped;
            }
            else if (guarded)
            {
                mTxtLegend.Text = "초음파 -12dB 감쇄 파형";
                mTxtLegend.Foreground = ThemePalette.BrStopped;
            }
            else if (showR)
            {
                mTxtLegend.Text = p.BinauralBeat > 0f ? "L/R 바이노럴 위상 파형 (L+R)" : "듀얼 채널 스테레오 파형";
                mTxtLegend.Foreground = ThemePalette.BrCyan;
            }
            else if (p.IsAntiPhase)
            {
                mTxtLegend.Text = "180° 역위상 상쇄 합성 파형";
                mTxtLegend.Foreground = ThemePalette.BrAmber;
            }
            else if (dsp.OutputProfile == PcOutputProfile.PcSpeaker && p.Freq1 < 85.0f)
            {
                mTxtLegend.Text = p.Freq1 < 32.0f ? "저역 + 144Hz 반송 합성 파형" : "저역 + 배음 보정 합성 파형";
                mTxtLegend.Foreground = ThemePalette.BrAmber;
            }
            else
            {
                mTxtLegend.Text = "64-bit 무왜곡 정현파 출력";
                mTxtLegend.Foreground = ThemePalette.BrActive;
            }

            mTxtCeiling.Text = dsp.VolumePercent > VolumeCurve.UNITY_PERCENT
                ? "-0.5 dBTP 클린 증폭 보호"
                : "-0.5 dBTP 트루피크 천장";

            double windowMs = (targetSpan * 1000.0) / dsp.SampleRate;
            mTxtTimebase.Text = string.Format(CultureInfo.InvariantCulture, "{0:F1} ms 창 · {1:F0}kHz Float", windowMs, dsp.SampleRate / 1000.0);
        }
    }

    public sealed class CompanionWindow : Window
    {
        public const double MIN_WINDOW_WIDTH = 400.0;
        public const double MIN_WINDOW_HEIGHT = 540.0;
        public const double WIDE_BREAKPOINT = 760.0;
        public const double GRID2_BREAKPOINT = 1080.0;

        private static readonly int[] DETENT_VALUES = new int[] { 15, 30, 50, 80, 100, 120 };
        private static readonly string[] DETENT_PCTS = new string[] { "15%", "30%", "50%", "80%", "100%", "120%" };
        private static readonly string[] DETENT_TAGS = new string[] { "영상", "작업", "병행", "단독", "최대", "증폭" };
        private static readonly string[] PROFILE_LABELS = new string[] { "PC 스피커 보정", "이어폰·헤드폰", "순수 원음·DAC" };
        private static readonly int[] TIMER_VALUES = new int[] { 0, 15, 30, 60, 90 };
        private static readonly string[] TIMER_LABELS = new string[] { "끄기", "15분", "30분", "60분", "90분" };

        private readonly IAppHostController mCtrl;
        private readonly DispatcherTimer mScopeTimer;
        private readonly DispatcherTimer mTelemetryTimer;

        private Border mOuterBorder;
        private Grid mBodyGrid;
        private ScrollViewer mConsoleScroll;
        private Grid mBrowserPanel;

        // Titlebar controls
        private Border mLayoutBtn;
        private TextBlock mTxtLayoutBtn;
        private Border mTopmostBtn;
        private TextBlock mTxtTopmost;
        private Border mMaxBtn;
        private System.Windows.Shapes.Path mMaxIconPath;

        // Console Card controls
        private Border mAccentBar;
        private Ellipse mStatusLed;
        private TextBlock mTxtStatus;
        private readonly List<Border> mVuBars = new List<Border>();
        private TextBlock mTxtHeroFreq;
        private Border mHeroSubBadge;
        private TextBlock mTxtHeroSubText;
        private Border mTimerBadge;
        private TextBlock mTxtTimerBadge;

        private LiveWaveformScope mScope;
        private Grid mSweepPanel;
        private TextBlock mTxtSweepLeft;
        private TextBlock mTxtSweepRight;
        private Border mSweepTrack;
        private Border mSweepFill;

        // 4 Telemetry Chips
        private TextBlock mTelPhase;
        private TextBlock mTelMod;
        private TextBlock mTelProfile;
        private TextBlock mTelLevel;

        // Structured Scenario Info
        private TextBlock mTxtScenarioTitle;
        private Border mJumpCueBtn;
        private TextBlock mTxtJumpCue;
        private TextBlock mTxtActionDetail;
        private TextBlock mTxtUsageDetail;
        private Grid mPcCompRow;
        private TextBlock mTxtPcCompDetail;

        // Volume & Amplifier Detents
        private Border mBtnMute;
        private System.Windows.Shapes.Path mMuteBodyPath;
        private System.Windows.Shapes.Path mMuteWavePath;
        private System.Windows.Shapes.Path mMuteCrossPath;
        private TextBlock mTxtVolumeReadout;
        private TextBlock mTxtHeadroomNote;
        private Slider mSeekVolume;
        private bool mSuppressSeekCallback;
        private readonly List<Border> mDetentChips = new List<Border>();
        private readonly List<TextBlock> mDetentPcts = new List<TextBlock>();
        private readonly List<TextBlock> mDetentTags = new List<TextBlock>();

        // Device & Profiles & Timer
        private ComboBox mComboDevice;
        private bool mSuppressDeviceCallback;
        private readonly List<Border> mProfileChips = new List<Border>();
        private readonly List<TextBlock> mProfileLabels = new List<TextBlock>();
        private Border mGuardChip;
        private TextBlock mTxtGuard;
        private readonly List<Border> mTimerChips = new List<Border>();
        private readonly List<TextBlock> mTimerLabels = new List<TextBlock>();

        // Transport
        private Border mActionBtn;
        private System.Windows.Shapes.Path mActionIconPath;
        private TextBlock mTxtActionBtn;

        // Browser pane
        private TextBox mSearchBox;
        private TextBlock mSearchPlaceholder;
        private Border mClearSearchBtn;
        private TextBlock mTxtMatchCount;
        private Border mFavFilterBtn;
        private System.Windows.Shapes.Path mFavFilterStar;
        private TextBlock mTxtFavFilter;
        private bool mFavoritesOnly = false;
        private WrapPanel mTabWrap;
        private readonly List<Border> mTabChips = new List<Border>();
        private ScrollViewer mPresetScroll;
        private UniformGrid mPresetGrid;

        private TextBlock mTxtFooterHint;

        private int mSelectedTab;
        private int mLastHighlightedPresetId = -2;
        private bool mLastRunning = false;
        private Border mActiveCardBorder;
        private string mSearchQuery = "";
        private bool mIsWideMode = false;
        private int mPresetCols = -1;

        public CompanionWindow(IAppHostController ctrl)
        {
            mCtrl = ctrl;
            mSelectedTab = TabSyncPolicy.ClampTabIndex(ctrl.State.LastTab);

            Title = "보르타 이지스";
            WindowStyle = WindowStyle.None;
            ResizeMode = ResizeMode.CanResize;
            MinWidth = MIN_WINDOW_WIDTH;
            MinHeight = MIN_WINDOW_HEIGHT;
            Background = ThemePalette.BrBg;
            FontFamily = ThemePalette.FontUi;
            UseLayoutRounding = true;
            TextOptions.SetTextFormattingMode(this, TextFormattingMode.Display);

            WindowChrome chrome = new WindowChrome();
            chrome.CaptionHeight = 40;
            chrome.ResizeBorderThickness = new Thickness(7);
            chrome.CornerRadius = new CornerRadius(10);
            chrome.GlassFrameThickness = new Thickness(0);
            chrome.UseAeroCaptionButtons = false;
            WindowChrome.SetWindowChrome(this, chrome);

            Rect wa = SystemParameters.WorkArea;
            double initW = ctrl.State.WindowWidth;
            double initH = ctrl.State.WindowHeight;
            if (initW > wa.Width - 24) initW = Math.Max(MIN_WINDOW_WIDTH, wa.Width - 24);
            if (initH > wa.Height - 24) initH = Math.Max(MIN_WINDOW_HEIGHT, wa.Height - 24);
            Width = initW;
            Height = initH;

            double vsLeft = SystemParameters.VirtualScreenLeft;
            double vsTop = SystemParameters.VirtualScreenTop;
            double vsRight = vsLeft + SystemParameters.VirtualScreenWidth;
            double vsBottom = vsTop + SystemParameters.VirtualScreenHeight;
            if (!double.IsNaN(ctrl.State.WindowLeft) && !double.IsNaN(ctrl.State.WindowTop))
            {
                WindowStartupLocation = WindowStartupLocation.Manual;
                Left = Math.Max(vsLeft, Math.Min(vsRight - Width, ctrl.State.WindowLeft));
                Top = Math.Max(vsTop, Math.Min(vsBottom - Height, ctrl.State.WindowTop));
            }
            else
            {
                WindowStartupLocation = WindowStartupLocation.Manual;
                Left = wa.Left + Math.Max(0, (wa.Width - Width) * 0.5);
                Top = wa.Top + Math.Max(0, (wa.Height - Height) * 0.5);
            }
            Topmost = ctrl.State.Topmost;
            if (ctrl.State.WindowMaximized)
            {
                WindowState = WindowState.Maximized;
            }

            Content = BuildLayout();
            ApplyResponsiveLayout(Width);

            mScopeTimer = new DispatcherTimer(DispatcherPriority.Render);
            mScopeTimer.Interval = TimeSpan.FromMilliseconds(33);
            mScopeTimer.Tick += OnScopeTick;

            mTelemetryTimer = new DispatcherTimer();
            mTelemetryTimer.Interval = TimeSpan.FromMilliseconds(220);
            mTelemetryTimer.Tick += OnTelemetryTick;

            SizeChanged += delegate
            {
                double curW = ActualWidth > 0 ? ActualWidth : Width;
                ApplyResponsiveLayout(curW);
                if (WindowState == WindowState.Normal && IsVisible)
                {
                    mCtrl.State.WindowWidth = Width;
                    mCtrl.State.WindowHeight = Height;
                }
            };
            LocationChanged += OnLocationChanged;
            IsVisibleChanged += OnVisibilityChanged;
            ContentRendered += delegate { InvalidateVisual(); };
            Deactivated += delegate { AppStateStore.Save(mCtrl.State); };
            PreviewKeyDown += OnPreviewKeyDown;

            SyncAllFromController(true);
        }

        public void ApplyResponsiveLayoutNow()
        {
            ApplyResponsiveLayout(ActualWidth > 0 ? ActualWidth : Width);
        }

        public void UpdateScopeVisualsNow()
        {
            OnScopeTick(null, EventArgs.Empty);
        }

        private UIElement BuildLayout()
        {
            mOuterBorder = new Border();
            mOuterBorder.Background = ThemePalette.BrBg;
            mOuterBorder.BorderBrush = ThemePalette.BrOutline;
            mOuterBorder.BorderThickness = new Thickness(1);
            mOuterBorder.CornerRadius = new CornerRadius(10);

            Grid root = new Grid();
            root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(40) });
            root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
            root.RowDefinitions.Add(new RowDefinition { Height = new GridLength(30) });

            Border titleBar = BuildTitleBar();
            Grid.SetRow(titleBar, 0);
            root.Children.Add(titleBar);

            mBodyGrid = new Grid();
            Grid.SetRow(mBodyGrid, 1);

            mConsoleScroll = new ScrollViewer();
            mConsoleScroll.VerticalScrollBarVisibility = ScrollBarVisibility.Auto;
            mConsoleScroll.HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled;
            mConsoleScroll.Resources.Add(typeof(ScrollBar), ThemePalette.CreateDarkScrollBarStyle());
            mConsoleScroll.Content = BuildConsoleCard();

            mBrowserPanel = BuildBrowserPanel();

            mBodyGrid.Children.Add(mConsoleScroll);
            mBodyGrid.Children.Add(mBrowserPanel);
            root.Children.Add(mBodyGrid);

            Border footer = BuildFooter();
            Grid.SetRow(footer, 2);
            root.Children.Add(footer);

            mOuterBorder.Child = root;
            return mOuterBorder;
        }

        private void ApplyResponsiveLayout(double width)
        {
            if (mBodyGrid == null || double.IsNaN(width) || width <= 0) return;

            bool wide = width >= WIDE_BREAKPOINT;
            int cols = (width >= GRID2_BREAKPOINT) ? 2 : 1;

            if (wide != mIsWideMode || mBodyGrid.ColumnDefinitions.Count == 0)
            {
                mIsWideMode = wide;
                mBodyGrid.ColumnDefinitions.Clear();
                mBodyGrid.RowDefinitions.Clear();

                if (wide)
                {
                    mBodyGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(396) });
                    mBodyGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                    mBodyGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

                    Grid.SetRow(mConsoleScroll, 0);
                    Grid.SetColumn(mConsoleScroll, 0);
                    mConsoleScroll.Margin = new Thickness(12, 10, 6, 8);
                    mConsoleScroll.MaxHeight = double.PositiveInfinity;

                    Grid.SetRow(mBrowserPanel, 0);
                    Grid.SetColumn(mBrowserPanel, 1);
                    mBrowserPanel.Margin = new Thickness(6, 10, 12, 8);

                    mScope.Height = 104;
                    if (mTxtLayoutBtn != null) mTxtLayoutBtn.Text = "슬림";
                }
                else
                {
                    mBodyGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                    mBodyGrid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
                    mBodyGrid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

                    double h = ActualHeight > 0 ? ActualHeight : Height;
                    Grid.SetRow(mConsoleScroll, 0);
                    Grid.SetColumn(mConsoleScroll, 0);
                    mConsoleScroll.Margin = new Thickness(12, 8, 12, 6);
                    mConsoleScroll.MaxHeight = Math.Max(260.0, h * 0.56);

                    Grid.SetRow(mBrowserPanel, 1);
                    Grid.SetColumn(mBrowserPanel, 0);
                    mBrowserPanel.Margin = new Thickness(12, 0, 12, 8);

                    mScope.Height = 82;
                    if (mTxtLayoutBtn != null) mTxtLayoutBtn.Text = "확장";
                }
            }
            else if (!wide)
            {
                double h = ActualHeight > 0 ? ActualHeight : Height;
                mConsoleScroll.MaxHeight = Math.Max(260.0, h * 0.56);
            }

            if (cols != mPresetCols)
            {
                mPresetCols = cols;
                mPresetGrid.Columns = cols;
                RenderPresetCards();
            }

            if (mTxtFooterHint != null)
            {
                mTxtFooterHint.Text = width >= 540
                    ? "Space 재생/정지 · 위/아래 볼륨 · M 음소거 · Ctrl+F 검색 · × 트레이 유지"
                    : "× 닫기 시 트레이 백그라운드 유지 · Space 재생/정지";
            }
        }

        private Border BuildTitleBar()
        {
            Border bar = new Border();
            bar.Background = ThemePalette.BrSurface;
            bar.BorderBrush = ThemePalette.BrOutline;
            bar.BorderThickness = new Thickness(0, 0, 0, 1);
            bar.Padding = new Thickness(12, 0, 8, 0);

            Grid g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            StackPanel left = new StackPanel();
            left.Orientation = Orientation.Horizontal;
            left.VerticalAlignment = VerticalAlignment.Center;

            Canvas waveMark = ThemePalette.MakeIcon(
                "M 1,8 Q 3.5,2 6,8 T 11,8 T 15,8",
                16,
                ThemePalette.BrAmber,
                true,
                1.8);
            waveMark.Margin = new Thickness(0, 0, 8, 0);
            left.Children.Add(waveMark);

            TextBlock title = new TextBlock();
            title.Text = "보르타 이지스";
            title.Foreground = ThemePalette.BrTextPrimary;
            title.FontSize = 13;
            title.FontWeight = FontWeights.Bold;
            title.VerticalAlignment = VerticalAlignment.Center;
            left.Children.Add(title);

            Border verBadge = new Border();
            verBadge.Background = ThemePalette.BrSurfaceHigh;
            verBadge.BorderBrush = ThemePalette.BrOutline;
            verBadge.BorderThickness = new Thickness(1);
            verBadge.CornerRadius = new CornerRadius(5);
            verBadge.Padding = new Thickness(6, 1, 6, 1);
            verBadge.Margin = new Thickness(8, 0, 0, 0);
            verBadge.VerticalAlignment = VerticalAlignment.Center;
            TextBlock verTxt = new TextBlock();
            verTxt.Text = "v3.1 PC";
            verTxt.FontFamily = ThemePalette.FontMono;
            verTxt.FontSize = 10;
            verTxt.FontWeight = FontWeights.Bold;
            verTxt.Foreground = ThemePalette.BrAmber;
            verBadge.Child = verTxt;
            left.Children.Add(verBadge);

            g.Children.Add(left);

            StackPanel right = new StackPanel();
            right.Orientation = Orientation.Horizontal;
            right.VerticalAlignment = VerticalAlignment.Center;
            Grid.SetColumn(right, 1);

            Border btnMini = CreatePillChip("미니 바", delegate { mCtrl.SwitchToMiniMode(true); });
            btnMini.Height = 26;
            btnMini.Margin = new Thickness(0, 0, 5, 0);
            WindowChrome.SetIsHitTestVisibleInChrome(btnMini, true);
            right.Children.Add(btnMini);

            mLayoutBtn = CreatePillChipWithLabel("슬림", out mTxtLayoutBtn, delegate
            {
                if (WindowState == WindowState.Maximized) WindowState = WindowState.Normal;
                if (ActualWidth >= WIDE_BREAKPOINT)
                {
                    Width = 440;
                    Height = 740;
                }
                else
                {
                    Width = 920;
                    Height = 680;
                }
                Rect wa = SystemParameters.WorkArea;
                if (Left + Width > wa.Right) Left = Math.Max(wa.Left, wa.Right - Width - 12);
                if (Top + Height > wa.Bottom) Top = Math.Max(wa.Top, wa.Bottom - Height - 12);
                mCtrl.State.WindowWidth = Width;
                mCtrl.State.WindowHeight = Height;
                AppStateStore.Save(mCtrl.State);
            });
            mLayoutBtn.Height = 26;
            mLayoutBtn.Margin = new Thickness(0, 0, 5, 0);
            mLayoutBtn.ToolTip = "패널 크기 빠른 전환 (2단 스튜디오 / 1단 슬림)";
            WindowChrome.SetIsHitTestVisibleInChrome(mLayoutBtn, true);
            right.Children.Add(mLayoutBtn);

            mTopmostBtn = CreatePillChipWithLabel("고정", out mTxtTopmost, delegate
            {
                Topmost = !Topmost;
                mCtrl.State.Topmost = Topmost;
                AppStateStore.Save(mCtrl.State);
                RefreshTopmostButton();
            });
            mTopmostBtn.Height = 26;
            mTopmostBtn.Margin = new Thickness(0, 0, 6, 0);
            mTopmostBtn.ToolTip = "창 항상 위에 고정";
            WindowChrome.SetIsHitTestVisibleInChrome(mTopmostBtn, true);
            right.Children.Add(mTopmostBtn);

            Border btnMin = MakeCaptionButton(
                ThemePalette.MakeIcon("M 2,6 L 10,6", 12, ThemePalette.BrTextSecondary, true, 1.5),
                false,
                "최소화",
                delegate { WindowState = WindowState.Minimized; });
            btnMin.Margin = new Thickness(0, 0, 3, 0);
            WindowChrome.SetIsHitTestVisibleInChrome(btnMin, true);
            right.Children.Add(btnMin);

            Canvas maxCanvas = ThemePalette.MakeIcon("M 2,2 L 10,2 L 10,10 L 2,10 Z", 12, ThemePalette.BrTextSecondary, true, 1.3);
            mMaxIconPath = (System.Windows.Shapes.Path)maxCanvas.Children[0];
            mMaxBtn = MakeCaptionButton(maxCanvas, false, "최대화 / 복원", delegate
            {
                WindowState = (WindowState == WindowState.Maximized) ? WindowState.Normal : WindowState.Maximized;
            });
            mMaxBtn.Margin = new Thickness(0, 0, 3, 0);
            WindowChrome.SetIsHitTestVisibleInChrome(mMaxBtn, true);
            right.Children.Add(mMaxBtn);

            Border btnHide = MakeCaptionButton(
                ThemePalette.MakeIcon("M 2.5,2.5 L 9.5,9.5 M 9.5,2.5 L 2.5,9.5", 12, ThemePalette.BrTextSecondary, true, 1.5),
                true,
                "트레이로 숨기기 (재생 유지)",
                delegate { mCtrl.HideToTray(); });
            WindowChrome.SetIsHitTestVisibleInChrome(btnHide, true);
            right.Children.Add(btnHide);

            g.Children.Add(right);
            bar.Child = g;
            return bar;
        }

        private Border MakeCaptionButton(UIElement icon, bool isClose, string tip, Action onClick)
        {
            Border b = new Border();
            b.Width = 32;
            b.Height = 26;
            b.CornerRadius = new CornerRadius(6);
            b.Background = Brushes.Transparent;
            b.Cursor = Cursors.Hand;
            b.ToolTip = tip;
            b.Child = icon;
            b.MouseEnter += delegate
            {
                b.Background = isClose ? ThemePalette.BrCloseHover : ThemePalette.BrSurfaceHover;
            };
            b.MouseLeave += delegate
            {
                b.Background = Brushes.Transparent;
            };
            b.MouseLeftButtonUp += delegate(object s, MouseButtonEventArgs e)
            {
                e.Handled = true;
                if (onClick != null) onClick();
            };
            return b;
        }

        private Border BuildConsoleCard()
        {
            Border card = new Border();
            card.Background = ThemePalette.BrSurface;
            card.BorderBrush = ThemePalette.BrOutline;
            card.BorderThickness = new Thickness(1);
            card.CornerRadius = new CornerRadius(12);
            card.ClipToBounds = true;

            StackPanel outerStack = new StackPanel();

            mAccentBar = new Border();
            mAccentBar.Height = 2.5;
            mAccentBar.Background = ThemePalette.BrOutline;
            outerStack.Children.Add(mAccentBar);

            StackPanel col = new StackPanel();
            col.Margin = new Thickness(13, 10, 13, 12);

            // Clickable jump zone (status + hero freq)
            StackPanel jumpZone = new StackPanel();
            jumpZone.Cursor = Cursors.Hand;
            jumpZone.MouseLeftButtonUp += delegate
            {
                if (mCtrl.Dsp.IsPlaying && mCtrl.Dsp.CurrentPreset != null)
                {
                    JumpToPlayingPreset();
                }
            };

            // Row 1: LED + Status + 12-Bar LED VU Meter + WASAPI Badge
            Grid statusRow = new Grid();
            statusRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            statusRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            statusRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            statusRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            mStatusLed = new Ellipse();
            mStatusLed.Width = 8.5;
            mStatusLed.Height = 8.5;
            mStatusLed.Fill = ThemePalette.BrTextTertiary;
            mStatusLed.VerticalAlignment = VerticalAlignment.Center;
            mStatusLed.Margin = new Thickness(0, 0, 7, 0);
            statusRow.Children.Add(mStatusLed);

            mTxtStatus = new TextBlock();
            mTxtStatus.Text = "대기 중";
            mTxtStatus.FontSize = 12;
            mTxtStatus.FontWeight = FontWeights.Bold;
            mTxtStatus.Foreground = ThemePalette.BrTextSecondary;
            mTxtStatus.VerticalAlignment = VerticalAlignment.Center;
            Grid.SetColumn(mTxtStatus, 1);
            statusRow.Children.Add(mTxtStatus);

            StackPanel vuStack = new StackPanel();
            vuStack.Orientation = Orientation.Horizontal;
            vuStack.VerticalAlignment = VerticalAlignment.Center;
            vuStack.Margin = new Thickness(0, 0, 8, 0);
            mVuBars.Clear();
            for (int i = 0; i < 12; i++)
            {
                Border bar = new Border();
                bar.Width = 3;
                bar.Height = 10;
                bar.CornerRadius = new CornerRadius(1);
                bar.Background = ThemePalette.BrSurfaceHigh;
                bar.Margin = new Thickness(0, 0, i < 11 ? 2 : 0, 0);
                mVuBars.Add(bar);
                vuStack.Children.Add(bar);
            }
            Grid.SetColumn(vuStack, 2);
            statusRow.Children.Add(vuStack);

            Border tagBox = new Border();
            tagBox.Background = ThemePalette.BrSurfaceHigh;
            tagBox.BorderBrush = ThemePalette.BrOutline;
            tagBox.BorderThickness = new Thickness(1);
            tagBox.CornerRadius = new CornerRadius(5);
            tagBox.Padding = new Thickness(6, 2, 6, 2);
            TextBlock tagTxt = new TextBlock();
            tagTxt.Text = "WASAPI 48k";
            tagTxt.FontFamily = ThemePalette.FontMono;
            tagTxt.FontSize = 9.5;
            tagTxt.FontWeight = FontWeights.Bold;
            tagTxt.Foreground = ThemePalette.BrTextSecondary;
            tagBox.Child = tagTxt;
            Grid.SetColumn(tagBox, 3);
            statusRow.Children.Add(tagBox);

            jumpZone.Children.Add(statusRow);

            // Row 2: Hero Hz + SubBadge + Timer Badge
            Grid heroRow = new Grid();
            heroRow.Margin = new Thickness(0, 4, 0, 6);
            heroRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            heroRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            heroRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            heroRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            mTxtHeroFreq = new TextBlock();
            mTxtHeroFreq.Text = "0.00 Hz";
            mTxtHeroFreq.FontFamily = ThemePalette.FontMono;
            mTxtHeroFreq.FontSize = 27;
            mTxtHeroFreq.FontWeight = FontWeights.Bold;
            mTxtHeroFreq.Foreground = ThemePalette.BrTextTertiary;
            mTxtHeroFreq.VerticalAlignment = VerticalAlignment.Center;
            heroRow.Children.Add(mTxtHeroFreq);

            mHeroSubBadge = new Border();
            mHeroSubBadge.Background = ThemePalette.BrAmberBg;
            mHeroSubBadge.BorderBrush = ThemePalette.BrAmber;
            mHeroSubBadge.BorderThickness = new Thickness(1);
            mHeroSubBadge.CornerRadius = new CornerRadius(6);
            mHeroSubBadge.Padding = new Thickness(7, 2, 7, 2);
            mHeroSubBadge.Margin = new Thickness(9, 0, 0, 0);
            mHeroSubBadge.VerticalAlignment = VerticalAlignment.Center;
            mHeroSubBadge.Visibility = Visibility.Collapsed;
            mTxtHeroSubText = new TextBlock();
            mTxtHeroSubText.FontFamily = ThemePalette.FontMono;
            mTxtHeroSubText.FontSize = 11;
            mTxtHeroSubText.FontWeight = FontWeights.Bold;
            mTxtHeroSubText.Foreground = ThemePalette.BrAmber;
            mHeroSubBadge.Child = mTxtHeroSubText;
            Grid.SetColumn(mHeroSubBadge, 1);
            heroRow.Children.Add(mHeroSubBadge);

            mTimerBadge = new Border();
            mTimerBadge.Background = ThemePalette.BrAmberBg;
            mTimerBadge.BorderBrush = ThemePalette.BrAmber;
            mTimerBadge.BorderThickness = new Thickness(1);
            mTimerBadge.CornerRadius = new CornerRadius(6);
            mTimerBadge.Padding = new Thickness(7, 2, 7, 2);
            mTimerBadge.VerticalAlignment = VerticalAlignment.Center;
            mTimerBadge.Cursor = Cursors.Hand;
            mTimerBadge.ToolTip = "클릭 시 자동 정지 타이머 해제";
            mTimerBadge.Visibility = Visibility.Collapsed;
            mTimerBadge.MouseLeftButtonUp += delegate(object s, MouseButtonEventArgs e)
            {
                e.Handled = true;
                mCtrl.SetSleepTimerMinutes(0);
            };
            mTxtTimerBadge = new TextBlock();
            mTxtTimerBadge.FontFamily = ThemePalette.FontMono;
            mTxtTimerBadge.FontSize = 10.5;
            mTxtTimerBadge.FontWeight = FontWeights.Bold;
            mTxtTimerBadge.Foreground = ThemePalette.BrAmber;
            mTimerBadge.Child = mTxtTimerBadge;
            Grid.SetColumn(mTimerBadge, 3);
            heroRow.Children.Add(mTimerBadge);

            jumpZone.Children.Add(heroRow);
            col.Children.Add(jumpZone);

            // Row 3: Live Oscilloscope Viewport + Sweep Bar
            mScope = new LiveWaveformScope();
            mScope.Margin = new Thickness(0, 0, 0, 6);
            col.Children.Add(mScope);

            mSweepPanel = new Grid();
            mSweepPanel.Margin = new Thickness(0, 0, 0, 6);
            mSweepPanel.Visibility = Visibility.Collapsed;
            mSweepPanel.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            mSweepPanel.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            mSweepPanel.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            mTxtSweepLeft = new TextBlock { FontFamily = ThemePalette.FontMono, FontSize = 9.5, Foreground = ThemePalette.BrAmber };
            mSweepPanel.Children.Add(mTxtSweepLeft);

            mSweepTrack = new Border();
            mSweepTrack.Height = 5;
            mSweepTrack.CornerRadius = new CornerRadius(2.5);
            mSweepTrack.Background = ThemePalette.BrSurfaceHigh;
            mSweepTrack.Margin = new Thickness(6, 0, 6, 0);
            mSweepTrack.VerticalAlignment = VerticalAlignment.Center;
            mSweepFill = new Border();
            mSweepFill.Height = 5;
            mSweepFill.HorizontalAlignment = HorizontalAlignment.Left;
            mSweepFill.CornerRadius = new CornerRadius(2.5);
            mSweepFill.Background = ThemePalette.BrActive;
            mSweepFill.Width = 0;
            mSweepTrack.Child = mSweepFill;
            Grid.SetColumn(mSweepTrack, 1);
            mSweepPanel.Children.Add(mSweepTrack);

            mTxtSweepRight = new TextBlock { FontFamily = ThemePalette.FontMono, FontSize = 9.5, Foreground = ThemePalette.BrAmber };
            Grid.SetColumn(mTxtSweepRight, 2);
            mSweepPanel.Children.Add(mTxtSweepRight);
            col.Children.Add(mSweepPanel);

            // Row 4: 4-Chip Structured Signal Telemetry Strip
            Grid telGrid = new Grid();
            telGrid.Margin = new Thickness(0, 0, 0, 8);
            for (int c = 0; c < 4; c++)
            {
                telGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            }
            telGrid.Children.Add(BuildTelemetryCell("위상·채널", out mTelPhase, 0, true));
            telGrid.Children.Add(BuildTelemetryCell("변조·보호", out mTelMod, 1, true));
            telGrid.Children.Add(BuildTelemetryCell("PC 음향", out mTelProfile, 2, true));
            telGrid.Children.Add(BuildTelemetryCell("실출력 레벨", out mTelLevel, 3, false));
            col.Children.Add(telGrid);

            // Row 5: Scenario Title + Jump Cue + Structured Action / Usage Info
            Border infoBox = new Border();
            infoBox.Background = ThemePalette.BrSurfaceCard;
            infoBox.BorderBrush = ThemePalette.BrOutline;
            infoBox.BorderThickness = new Thickness(1);
            infoBox.CornerRadius = new CornerRadius(8);
            infoBox.Padding = new Thickness(10, 7, 10, 8);
            infoBox.Margin = new Thickness(0, 0, 0, 8);

            StackPanel infoCol = new StackPanel();

            Grid titleRow = new Grid();
            titleRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            titleRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            mTxtScenarioTitle = new TextBlock();
            mTxtScenarioTitle.Text = "프리셋을 선택하면 즉시 재생됩니다";
            mTxtScenarioTitle.FontSize = 13.5;
            mTxtScenarioTitle.FontWeight = FontWeights.Bold;
            mTxtScenarioTitle.Foreground = ThemePalette.BrTextPrimary;
            mTxtScenarioTitle.TextWrapping = TextWrapping.Wrap;
            mTxtScenarioTitle.Margin = new Thickness(0, 0, 6, 0);
            titleRow.Children.Add(mTxtScenarioTitle);

            mTxtJumpCue = new TextBlock();
            mJumpCueBtn = CreatePillChipWithLabel("재생 탭 ›", out mTxtJumpCue, delegate
            {
                JumpToPlayingPreset();
            });
            mJumpCueBtn.Visibility = Visibility.Collapsed;
            mJumpCueBtn.Height = 22;
            mJumpCueBtn.Padding = new Thickness(7, 0, 7, 0);
            mJumpCueBtn.VerticalAlignment = VerticalAlignment.Center;
            Grid.SetColumn(mJumpCueBtn, 1);
            titleRow.Children.Add(mJumpCueBtn);
            infoCol.Children.Add(titleRow);

            infoCol.Children.Add(BuildTaggedInfoRow("작용", ThemePalette.BrAmberBg, ThemePalette.BrAmber, out mTxtActionDetail));
            infoCol.Children.Add(BuildTaggedInfoRow("상황", ThemePalette.BrActiveBg, ThemePalette.BrActive, out mTxtUsageDetail));

            mPcCompRow = BuildTaggedInfoRow("보정", ThemePalette.BrCyanBg, ThemePalette.BrCyan, out mTxtPcCompDetail);
            mPcCompRow.Visibility = Visibility.Collapsed;
            infoCol.Children.Add(mPcCompRow);

            infoBox.Child = infoCol;
            col.Children.Add(infoBox);

            // Row 6: Independent Volume Section (0..120% with Wheel)
            StackPanel volSection = new StackPanel();
            volSection.Background = Brushes.Transparent;
            volSection.PreviewMouseWheel += delegate(object s, MouseWheelEventArgs e)
            {
                int step = e.Delta > 0 ? 2 : -2;
                int basePct = mCtrl.Dsp.IsMuted ? 0 : mCtrl.Dsp.VolumePercent;
                int next = VolumeCurve.ClampPercent(basePct + step);
                mCtrl.ApplyVolume(next, next == 0);
                e.Handled = true;
            };

            Grid volHdr = new Grid();
            volHdr.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            volHdr.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            volHdr.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            TextBlock volLbl = new TextBlock();
            volLbl.Text = "독립 출력 볼륨";
            volLbl.FontSize = 11.5;
            volLbl.FontWeight = FontWeights.Bold;
            volLbl.Foreground = ThemePalette.BrTextSecondary;
            volHdr.Children.Add(volLbl);

            mTxtVolumeReadout = new TextBlock();
            mTxtVolumeReadout.FontFamily = ThemePalette.FontMono;
            mTxtVolumeReadout.FontSize = 11.5;
            mTxtVolumeReadout.FontWeight = FontWeights.Bold;
            mTxtVolumeReadout.Foreground = ThemePalette.BrAmber;
            mTxtVolumeReadout.Margin = new Thickness(7, 0, 0, 0);
            Grid.SetColumn(mTxtVolumeReadout, 1);
            volHdr.Children.Add(mTxtVolumeReadout);

            mTxtHeadroomNote = new TextBlock();
            mTxtHeadroomNote.Text = "무왜곡 -0.5dBTP";
            mTxtHeadroomNote.FontFamily = ThemePalette.FontMono;
            mTxtHeadroomNote.FontSize = 9.5;
            mTxtHeadroomNote.Foreground = ThemePalette.BrTextTertiary;
            mTxtHeadroomNote.VerticalAlignment = VerticalAlignment.Center;
            Grid.SetColumn(mTxtHeadroomNote, 2);
            volHdr.Children.Add(mTxtHeadroomNote);
            volSection.Children.Add(volHdr);

            Grid faderRow = new Grid();
            faderRow.Margin = new Thickness(0, 5, 0, 5);
            faderRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(36) });
            faderRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(28) });
            faderRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            faderRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(28) });

            Canvas muteCanvas = new Canvas();
            muteCanvas.Width = 16;
            muteCanvas.Height = 16;
            muteCanvas.HorizontalAlignment = HorizontalAlignment.Center;
            muteCanvas.VerticalAlignment = VerticalAlignment.Center;

            mMuteBodyPath = new System.Windows.Shapes.Path();
            mMuteBodyPath.Data = Geometry.Parse("M 2,5 L 5,5 L 9,2 L 9,14 L 5,11 L 2,11 Z");
            mMuteBodyPath.Fill = ThemePalette.BrTextPrimary;
            muteCanvas.Children.Add(mMuteBodyPath);

            mMuteWavePath = new System.Windows.Shapes.Path();
            mMuteWavePath.Data = Geometry.Parse("M 11.2,5.3 Q 12.9,8 11.2,10.7 M 13.1,3.3 Q 15.6,8 13.1,12.7");
            mMuteWavePath.Stroke = ThemePalette.BrAmber;
            mMuteWavePath.StrokeThickness = 1.5;
            mMuteWavePath.StrokeStartLineCap = PenLineCap.Round;
            mMuteWavePath.StrokeEndLineCap = PenLineCap.Round;
            muteCanvas.Children.Add(mMuteWavePath);

            mMuteCrossPath = new System.Windows.Shapes.Path();
            mMuteCrossPath.Data = Geometry.Parse("M 11.2,5.5 L 14.6,10.5 M 14.6,5.5 L 11.2,10.5");
            mMuteCrossPath.Stroke = ThemePalette.BrStopped;
            mMuteCrossPath.StrokeThickness = 1.6;
            mMuteCrossPath.StrokeStartLineCap = PenLineCap.Round;
            mMuteCrossPath.StrokeEndLineCap = PenLineCap.Round;
            mMuteCrossPath.Visibility = Visibility.Collapsed;
            muteCanvas.Children.Add(mMuteCrossPath);

            mBtnMute = new Border();
            mBtnMute.Background = ThemePalette.BrSurfaceHigh;
            mBtnMute.BorderBrush = ThemePalette.BrOutline;
            mBtnMute.BorderThickness = new Thickness(1);
            mBtnMute.CornerRadius = new CornerRadius(6);
            mBtnMute.Width = 36;
            mBtnMute.Height = 28;
            mBtnMute.Margin = new Thickness(0, 0, 4, 0);
            mBtnMute.Cursor = Cursors.Hand;
            mBtnMute.ToolTip = "음소거 전환 (M)";
            mBtnMute.Child = muteCanvas;
            mBtnMute.MouseEnter += delegate
            {
                if (!mCtrl.Dsp.IsMuted) mBtnMute.Background = ThemePalette.BrSurfaceHover;
            };
            mBtnMute.MouseLeave += delegate
            {
                mBtnMute.Background = ThemePalette.BrSurfaceHigh;
            };
            mBtnMute.MouseLeftButtonUp += delegate(object s, MouseButtonEventArgs e)
            {
                e.Handled = true;
                if (mCtrl.Dsp.IsMuted)
                {
                    int pct = mCtrl.Dsp.VolumePercent == 0 ? VolumeCurve.DEFAULT_PERCENT : mCtrl.Dsp.VolumePercent;
                    mCtrl.ApplyVolume(pct, false);
                }
                else
                {
                    mCtrl.ApplyVolume(mCtrl.Dsp.VolumePercent, true);
                }
            };
            Grid.SetColumn(mBtnMute, 0);
            faderRow.Children.Add(mBtnMute);

            Border btnMinus = CreatePillChip("-", delegate
            {
                int next = VolumeCurve.ClampPercent(mCtrl.Dsp.VolumePercent - 5);
                mCtrl.ApplyVolume(next, next == 0);
            });
            btnMinus.Height = 28;
            btnMinus.Margin = new Thickness(0, 0, 6, 0);
            Grid.SetColumn(btnMinus, 1);
            faderRow.Children.Add(btnMinus);

            mSeekVolume = new Slider();
            mSeekVolume.Minimum = 0;
            mSeekVolume.Maximum = VolumeCurve.MAX_PERCENT;
            mSeekVolume.IsSnapToTickEnabled = true;
            mSeekVolume.TickFrequency = 1;
            mSeekVolume.Style = ThemePalette.CreateDarkSliderStyle();
            mSeekVolume.VerticalAlignment = VerticalAlignment.Center;
            mSeekVolume.ValueChanged += delegate(object s, RoutedPropertyChangedEventArgs<double> e)
            {
                if (mSuppressSeekCallback) return;
                int v = (int)Math.Round(e.NewValue);
                mCtrl.ApplyVolume(v, v == 0);
            };
            Grid.SetColumn(mSeekVolume, 2);
            faderRow.Children.Add(mSeekVolume);

            Border btnPlus = CreatePillChip("+", delegate
            {
                int next = VolumeCurve.ClampPercent(mCtrl.Dsp.VolumePercent + 5);
                mCtrl.ApplyVolume(next, next == 0);
            });
            btnPlus.Height = 28;
            btnPlus.Margin = new Thickness(6, 0, 0, 0);
            Grid.SetColumn(btnPlus, 3);
            faderRow.Children.Add(btnPlus);
            volSection.Children.Add(faderRow);

            // 6 Two-Line Detent Presets: 15%..100%..120%
            Grid detentGrid = new Grid();
            detentGrid.Margin = new Thickness(0, 0, 0, 8);
            mDetentChips.Clear();
            mDetentPcts.Clear();
            mDetentTags.Clear();
            for (int i = 0; i < DETENT_VALUES.Length; i++)
            {
                detentGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                int pct = DETENT_VALUES[i];

                Border chip = new Border();
                chip.Background = ThemePalette.BrSurfaceHigh;
                chip.BorderBrush = ThemePalette.BrOutline;
                chip.BorderThickness = new Thickness(1);
                chip.CornerRadius = new CornerRadius(6);
                chip.Height = 33;
                chip.Padding = new Thickness(1, 2, 1, 2);
                chip.Cursor = Cursors.Hand;
                if (i < DETENT_VALUES.Length - 1) chip.Margin = new Thickness(0, 0, 4, 0);
                chip.ToolTip = pct > 100 ? "120% 무왜곡 클린 증폭 (+1.6 dB)" : (DETENT_PCTS[i] + " " + DETENT_TAGS[i]);

                StackPanel st = new StackPanel();
                st.VerticalAlignment = VerticalAlignment.Center;
                st.HorizontalAlignment = HorizontalAlignment.Center;

                TextBlock tPct = new TextBlock();
                tPct.Text = DETENT_PCTS[i];
                tPct.FontFamily = ThemePalette.FontMono;
                tPct.FontSize = 10;
                tPct.FontWeight = FontWeights.Bold;
                tPct.HorizontalAlignment = HorizontalAlignment.Center;
                st.Children.Add(tPct);

                TextBlock tTag = new TextBlock();
                tTag.Text = DETENT_TAGS[i];
                tTag.FontSize = 9;
                tTag.HorizontalAlignment = HorizontalAlignment.Center;
                st.Children.Add(tTag);

                chip.Child = st;
                chip.MouseLeftButtonUp += delegate(object s, MouseButtonEventArgs e)
                {
                    e.Handled = true;
                    mCtrl.ApplyVolume(pct, false);
                };
                chip.MouseEnter += delegate
                {
                    bool act = !mCtrl.Dsp.IsMuted && (mCtrl.Dsp.VolumePercent == pct);
                    if (!act) chip.Background = ThemePalette.BrSurfaceHover;
                };
                chip.MouseLeave += delegate
                {
                    RefreshVolumeControls();
                };

                Grid.SetColumn(chip, i);
                detentGrid.Children.Add(chip);
                mDetentChips.Add(chip);
                mDetentPcts.Add(tPct);
                mDetentTags.Add(tTag);
            }
            volSection.Children.Add(detentGrid);
            col.Children.Add(volSection);

            // Row 7: Output device + PC Acoustics profile
            mComboDevice = new ComboBox();
            mComboDevice.Style = ThemePalette.CreateDarkComboStyle();
            mComboDevice.Margin = new Thickness(0, 0, 0, 6);
            mComboDevice.DropDownOpened += delegate { RefreshDeviceList(); };
            mComboDevice.SelectionChanged += delegate
            {
                if (mSuppressDeviceCallback) return;
                AudioEndpointInfo ep = mComboDevice.SelectedItem as AudioEndpointInfo;
                if (ep != null)
                {
                    mCtrl.ApplyDevice(ep.Id);
                }
            };
            RefreshDeviceList();
            col.Children.Add(mComboDevice);

            Grid profileRow = new Grid();
            profileRow.Margin = new Thickness(0, 0, 0, 6);
            mProfileChips.Clear();
            mProfileLabels.Clear();
            for (int i = 0; i < 3; i++)
            {
                profileRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                PcOutputProfile prof = (PcOutputProfile)i;
                TextBlock plbl;
                Border pchip = CreatePillChipWithLabel(PROFILE_LABELS[i], out plbl, delegate
                {
                    mCtrl.ApplyOutputProfile(prof);
                });
                pchip.Height = 25;
                pchip.Padding = new Thickness(3, 1, 3, 1);
                plbl.FontSize = 10;
                if (i < 2) pchip.Margin = new Thickness(0, 0, 4, 0);
                Grid.SetColumn(pchip, i);
                profileRow.Children.Add(pchip);
                mProfileChips.Add(pchip);
                mProfileLabels.Add(plbl);
            }
            profileRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            mGuardChip = CreatePillChipWithLabel("초음파 -12dB", out mTxtGuard, delegate
            {
                mCtrl.ApplyUltrasonicGuard(!mCtrl.Dsp.UltrasonicGuardEnabled);
            });
            mGuardChip.Height = 25;
            mGuardChip.Padding = new Thickness(7, 0, 7, 0);
            mGuardChip.Margin = new Thickness(4, 0, 0, 0);
            mTxtGuard.FontFamily = ThemePalette.FontMono;
            mTxtGuard.FontSize = 9.5;
            Grid.SetColumn(mGuardChip, 3);
            profileRow.Children.Add(mGuardChip);
            col.Children.Add(profileRow);

            // Row 8: Sleep Timer Strip
            Grid timerRow = new Grid();
            timerRow.Margin = new Thickness(0, 0, 0, 8);
            timerRow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            TextBlock tHdr = new TextBlock();
            tHdr.Text = "타이머";
            tHdr.FontSize = 10.5;
            tHdr.FontWeight = FontWeights.Bold;
            tHdr.Foreground = ThemePalette.BrTextTertiary;
            tHdr.VerticalAlignment = VerticalAlignment.Center;
            tHdr.Margin = new Thickness(0, 0, 7, 0);
            timerRow.Children.Add(tHdr);

            mTimerChips.Clear();
            mTimerLabels.Clear();
            for (int i = 0; i < TIMER_VALUES.Length; i++)
            {
                timerRow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
                int mins = TIMER_VALUES[i];
                TextBlock tlbl;
                Border tchip = CreatePillChipWithLabel(TIMER_LABELS[i], out tlbl, delegate
                {
                    mCtrl.SetSleepTimerMinutes(mins);
                });
                tchip.Height = 23;
                tchip.Padding = new Thickness(2, 1, 2, 1);
                tlbl.FontSize = 10;
                if (i < TIMER_VALUES.Length - 1) tchip.Margin = new Thickness(0, 0, 4, 0);
                Grid.SetColumn(tchip, i + 1);
                timerRow.Children.Add(tchip);
                mTimerChips.Add(tchip);
                mTimerLabels.Add(tlbl);
            }
            col.Children.Add(timerRow);

            // Row 9: Transport CTA Row ([Prev] [Play/Stop] [Next])
            Grid transportGrid = new Grid();
            transportGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(36) });
            transportGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            transportGrid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(36) });

            Border btnPrev = CreateVectorButton("M 9,3 L 4,8 L 9,13", 16, true, "이전 프리셋 (Alt+Left)", delegate
            {
                mCtrl.PlayAdjacentPreset(-1);
            });
            btnPrev.Height = 35;
            btnPrev.Margin = new Thickness(0, 0, 5, 0);
            transportGrid.Children.Add(btnPrev);

            mActionBtn = new Border();
            mActionBtn.Height = 35;
            mActionBtn.CornerRadius = new CornerRadius(8);
            mActionBtn.BorderThickness = new Thickness(1);
            mActionBtn.Background = ThemePalette.BrSurfaceHigh;
            mActionBtn.BorderBrush = ThemePalette.BrAmber;
            mActionBtn.Padding = new Thickness(10, 0, 10, 0);
            mActionBtn.Cursor = Cursors.Hand;

            StackPanel actStack = new StackPanel();
            actStack.Orientation = Orientation.Horizontal;
            actStack.HorizontalAlignment = HorizontalAlignment.Center;
            actStack.VerticalAlignment = VerticalAlignment.Center;

            Canvas actIconCanvas = ThemePalette.MakeIcon("M 3,2 L 11,7 L 3,12 Z", 14, ThemePalette.BrAmber, false, 0);
            actIconCanvas.Margin = new Thickness(0, 0, 7, 0);
            mActionIconPath = (System.Windows.Shapes.Path)actIconCanvas.Children[0];
            actStack.Children.Add(actIconCanvas);

            mTxtActionBtn = new TextBlock();
            mTxtActionBtn.FontSize = 12;
            mTxtActionBtn.FontWeight = FontWeights.Bold;
            mTxtActionBtn.Foreground = ThemePalette.BrAmber;
            mTxtActionBtn.TextTrimming = TextTrimming.CharacterEllipsis;
            mTxtActionBtn.VerticalAlignment = VerticalAlignment.Center;
            actStack.Children.Add(mTxtActionBtn);

            mActionBtn.Child = actStack;
            mActionBtn.MouseEnter += delegate
            {
                mActionBtn.Background = mCtrl.Dsp.IsPlaying ? ThemePalette.BrStopBtnHover : ThemePalette.BrSurfaceHover;
            };
            mActionBtn.MouseLeave += delegate
            {
                mActionBtn.Background = mCtrl.Dsp.IsPlaying ? ThemePalette.BrStopBtn : ThemePalette.BrSurfaceHigh;
            };
            mActionBtn.MouseLeftButtonUp += delegate(object s, MouseButtonEventArgs e)
            {
                e.Handled = true;
                if (mCtrl.Dsp.IsPlaying)
                {
                    mCtrl.StopPlayback();
                }
                else
                {
                    PresetItem last = PresetCatalog.FindById(mCtrl.Presets, mCtrl.State.LastPresetId);
                    if (last != null) mCtrl.PlayPreset(last);
                }
            };
            Grid.SetColumn(mActionBtn, 1);
            transportGrid.Children.Add(mActionBtn);

            Border btnNext = CreateVectorButton("M 7,3 L 12,8 L 7,13", 16, true, "다음 프리셋 (Alt+Right)", delegate
            {
                mCtrl.PlayAdjacentPreset(1);
            });
            btnNext.Height = 35;
            btnNext.Margin = new Thickness(5, 0, 0, 0);
            Grid.SetColumn(btnNext, 2);
            transportGrid.Children.Add(btnNext);

            col.Children.Add(transportGrid);
            outerStack.Children.Add(col);
            card.Child = outerStack;
            return card;
        }

        private Border BuildTelemetryCell(string caption, out TextBlock valueOut, int colIndex, bool rightMargin)
        {
            Border b = new Border();
            b.Background = ThemePalette.BrBg;
            b.BorderBrush = ThemePalette.BrOutline;
            b.BorderThickness = new Thickness(1);
            b.CornerRadius = new CornerRadius(6);
            b.Padding = new Thickness(6, 4, 6, 4);
            if (rightMargin) b.Margin = new Thickness(0, 0, 4, 0);

            StackPanel st = new StackPanel();
            TextBlock cap = new TextBlock();
            cap.Text = caption;
            cap.FontSize = 9;
            cap.Foreground = ThemePalette.BrTextTertiary;
            st.Children.Add(cap);

            valueOut = new TextBlock();
            valueOut.Text = "-";
            valueOut.FontFamily = ThemePalette.FontMono;
            valueOut.FontSize = 10;
            valueOut.FontWeight = FontWeights.Bold;
            valueOut.Foreground = ThemePalette.BrTextPrimary;
            valueOut.TextTrimming = TextTrimming.CharacterEllipsis;
            valueOut.Margin = new Thickness(0, 1, 0, 0);
            st.Children.Add(valueOut);

            b.Child = st;
            Grid.SetColumn(b, colIndex);
            return b;
        }

        private Grid BuildTaggedInfoRow(string tagText, Brush tagBg, Brush tagFg, out TextBlock textOut)
        {
            Grid g = new Grid();
            g.Margin = new Thickness(0, 4, 0, 0);
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });

            Border pill = new Border();
            pill.Background = tagBg;
            pill.CornerRadius = new CornerRadius(4);
            pill.Padding = new Thickness(5, 1, 5, 1);
            pill.VerticalAlignment = VerticalAlignment.Top;
            pill.Margin = new Thickness(0, 1, 6, 0);
            TextBlock pillTxt = new TextBlock();
            pillTxt.Text = tagText;
            pillTxt.FontSize = 9.5;
            pillTxt.FontWeight = FontWeights.Bold;
            pillTxt.Foreground = tagFg;
            pill.Child = pillTxt;
            g.Children.Add(pill);

            textOut = new TextBlock();
            textOut.FontSize = 11;
            textOut.Foreground = ThemePalette.BrTextSecondary;
            textOut.TextWrapping = TextWrapping.Wrap;
            textOut.LineHeight = 15;
            Grid.SetColumn(textOut, 1);
            g.Children.Add(textOut);
            return g;
        }

        private Border CreateVectorButton(string pathData, double iconSize, bool isStroke, string tip, Action onClick)
        {
            Border b = new Border();
            b.Background = ThemePalette.BrSurfaceHigh;
            b.BorderBrush = ThemePalette.BrOutline;
            b.BorderThickness = new Thickness(1);
            b.CornerRadius = new CornerRadius(8);
            b.Cursor = Cursors.Hand;
            b.ToolTip = tip;
            b.Child = ThemePalette.MakeIcon(pathData, iconSize, ThemePalette.BrTextSecondary, isStroke, 1.8);
            b.MouseEnter += delegate { b.Background = ThemePalette.BrSurfaceHover; };
            b.MouseLeave += delegate { b.Background = ThemePalette.BrSurfaceHigh; };
            b.MouseLeftButtonUp += delegate(object s, MouseButtonEventArgs e)
            {
                e.Handled = true;
                if (onClick != null) onClick();
            };
            return b;
        }

        private Grid BuildBrowserPanel()
        {
            Grid panel = new Grid();
            panel.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            panel.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
            panel.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

            // Row 0: Search + Count + Favorites Filter
            Grid topBar = new Grid();
            topBar.Margin = new Thickness(0, 0, 0, 8);
            topBar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            topBar.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            Border searchBorder = new Border();
            searchBorder.Background = ThemePalette.BrSurface;
            searchBorder.BorderBrush = ThemePalette.BrOutline;
            searchBorder.BorderThickness = new Thickness(1);
            searchBorder.CornerRadius = new CornerRadius(8);
            searchBorder.Height = 32;

            Grid sg = new Grid();
            sg.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            sg.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            sg.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            sg.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            Canvas magIcon = ThemePalette.MakeIcon(
                "M 6.5,2 A 4.5,4.5 0 1 1 6.49,2 Z M 10,10 L 13.5,13.5",
                15,
                ThemePalette.BrTextTertiary,
                true,
                1.5);
            magIcon.Margin = new Thickness(9, 0, 2, 0);
            sg.Children.Add(magIcon);

            mSearchPlaceholder = new TextBlock();
            mSearchPlaceholder.Text = "프리셋 검색 (예: 집중, 눈, 두통, 수면, 운동, 모기)";
            mSearchPlaceholder.FontSize = 11.5;
            mSearchPlaceholder.Foreground = ThemePalette.BrTextTertiary;
            mSearchPlaceholder.VerticalAlignment = VerticalAlignment.Center;
            mSearchPlaceholder.Margin = new Thickness(6, 0, 0, 0);
            mSearchPlaceholder.IsHitTestVisible = false;
            Grid.SetColumn(mSearchPlaceholder, 1);
            sg.Children.Add(mSearchPlaceholder);

            mSearchBox = new TextBox();
            mSearchBox.Background = Brushes.Transparent;
            mSearchBox.BorderThickness = new Thickness(0);
            mSearchBox.Foreground = ThemePalette.BrTextPrimary;
            mSearchBox.CaretBrush = ThemePalette.BrAmber;
            mSearchBox.FontSize = 11.5;
            mSearchBox.Padding = new Thickness(6, 6, 4, 5);
            mSearchBox.VerticalAlignment = VerticalAlignment.Center;
            mSearchBox.TextChanged += delegate
            {
                mSearchQuery = (mSearchBox.Text ?? "").Trim();
                bool empty = string.IsNullOrEmpty(mSearchQuery);
                mSearchPlaceholder.Visibility = empty ? Visibility.Visible : Visibility.Collapsed;
                mClearSearchBtn.Visibility = empty ? Visibility.Collapsed : Visibility.Visible;
                RenderPresetCards();
            };
            Grid.SetColumn(mSearchBox, 1);
            sg.Children.Add(mSearchBox);

            TextBlock clrTxt;
            mClearSearchBtn = CreatePillChipWithLabel("지우기", out clrTxt, delegate
            {
                mSearchBox.Text = "";
            });
            mClearSearchBtn.Height = 22;
            mClearSearchBtn.Margin = new Thickness(0, 0, 4, 0);
            clrTxt.FontSize = 10;
            mClearSearchBtn.Visibility = Visibility.Collapsed;
            Grid.SetColumn(mClearSearchBtn, 2);
            sg.Children.Add(mClearSearchBtn);

            mTxtMatchCount = new TextBlock();
            mTxtMatchCount.Text = "12종";
            mTxtMatchCount.FontFamily = ThemePalette.FontMono;
            mTxtMatchCount.FontSize = 10;
            mTxtMatchCount.Foreground = ThemePalette.BrTextTertiary;
            mTxtMatchCount.VerticalAlignment = VerticalAlignment.Center;
            mTxtMatchCount.Margin = new Thickness(2, 0, 9, 0);
            Grid.SetColumn(mTxtMatchCount, 3);
            sg.Children.Add(mTxtMatchCount);

            searchBorder.Child = sg;
            topBar.Children.Add(searchBorder);

            mFavFilterBtn = new Border();
            mFavFilterBtn.Background = ThemePalette.BrSurface;
            mFavFilterBtn.BorderBrush = ThemePalette.BrOutline;
            mFavFilterBtn.BorderThickness = new Thickness(1);
            mFavFilterBtn.CornerRadius = new CornerRadius(8);
            mFavFilterBtn.Height = 32;
            mFavFilterBtn.Padding = new Thickness(9, 0, 9, 0);
            mFavFilterBtn.Margin = new Thickness(6, 0, 0, 0);
            mFavFilterBtn.Cursor = Cursors.Hand;
            mFavFilterBtn.ToolTip = "즐겨찾기 프리셋만 보기 (Ctrl+0)";

            StackPanel favStack = new StackPanel();
            favStack.Orientation = Orientation.Horizontal;
            favStack.VerticalAlignment = VerticalAlignment.Center;
            Canvas starCv = ThemePalette.MakeIcon(
                "M 7,1 L 8.8,4.9 L 13,5.5 L 10,8.4 L 10.7,12.6 L 7,10.6 L 3.3,12.6 L 4,8.4 L 1,5.5 L 5.2,4.9 Z",
                14,
                ThemePalette.BrTextSecondary,
                true,
                1.3);
            starCv.Margin = new Thickness(0, 0, 5, 0);
            mFavFilterStar = (System.Windows.Shapes.Path)starCv.Children[0];
            favStack.Children.Add(starCv);

            mTxtFavFilter = new TextBlock();
            mTxtFavFilter.Text = "즐겨찾기";
            mTxtFavFilter.FontSize = 11;
            mTxtFavFilter.FontWeight = FontWeights.Bold;
            mTxtFavFilter.Foreground = ThemePalette.BrTextSecondary;
            mTxtFavFilter.VerticalAlignment = VerticalAlignment.Center;
            favStack.Children.Add(mTxtFavFilter);

            mFavFilterBtn.Child = favStack;
            mFavFilterBtn.MouseLeftButtonUp += delegate(object s, MouseButtonEventArgs e)
            {
                e.Handled = true;
                mFavoritesOnly = !mFavoritesOnly;
                RefreshFavoritesUi();
            };
            Grid.SetColumn(mFavFilterBtn, 1);
            topBar.Children.Add(mFavFilterBtn);
            Grid.SetRow(topBar, 0);
            panel.Children.Add(topBar);

            // Row 1: Wrapped Category Chips (ALL 8 always visible without clipping!)
            mTabWrap = new WrapPanel();
            mTabWrap.Orientation = Orientation.Horizontal;
            mTabWrap.Margin = new Thickness(0, 0, 0, 6);
            mTabChips.Clear();
            for (int i = 0; i < TabSyncPolicy.TAB_COUNT; i++)
            {
                int idx = i;
                Border tab = new Border();
                tab.CornerRadius = new CornerRadius(14);
                tab.Height = 28;
                tab.Padding = new Thickness(11, 0, 11, 0);
                tab.Margin = new Thickness(0, 0, 5, 5);
                tab.BorderThickness = new Thickness(1);
                tab.Cursor = Cursors.Hand;
                tab.MouseLeftButtonUp += delegate
                {
                    if (mFavoritesOnly) mFavoritesOnly = false;
                    SelectTab(idx, true, true);
                    RefreshFavoriteFilterChipStyle();
                };
                mTabChips.Add(tab);
                mTabWrap.Children.Add(tab);
            }
            Grid.SetRow(mTabWrap, 1);
            panel.Children.Add(mTabWrap);

            // Row 2: Responsive Preset Cards List (1 or 2 columns + dark slim scrollbar)
            mPresetScroll = new ScrollViewer();
            mPresetScroll.VerticalScrollBarVisibility = ScrollBarVisibility.Auto;
            mPresetScroll.HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled;
            mPresetScroll.Resources.Add(typeof(ScrollBar), ThemePalette.CreateDarkScrollBarStyle());

            mPresetGrid = new UniformGrid();
            mPresetGrid.Columns = 1;
            mPresetGrid.VerticalAlignment = VerticalAlignment.Top;
            mPresetGrid.Margin = new Thickness(0, 0, 6, 0);
            mPresetScroll.Content = mPresetGrid;

            Grid.SetRow(mPresetScroll, 2);
            panel.Children.Add(mPresetScroll);
            return panel;
        }

        private Border BuildFooter()
        {
            Border footer = new Border();
            footer.Background = ThemePalette.BrSurface;
            footer.BorderBrush = ThemePalette.BrOutline;
            footer.BorderThickness = new Thickness(0, 1, 0, 0);
            footer.Padding = new Thickness(14, 0, 6, 0);

            Grid g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            mTxtFooterHint = new TextBlock();
            mTxtFooterHint.Text = "Space 재생/정지 · 위/아래 볼륨 · M 음소거 · Ctrl+F 검색 · × 트레이 유지";
            mTxtFooterHint.FontSize = 10;
            mTxtFooterHint.Foreground = ThemePalette.BrTextTertiary;
            mTxtFooterHint.VerticalAlignment = VerticalAlignment.Center;
            mTxtFooterHint.TextTrimming = TextTrimming.CharacterEllipsis;
            g.Children.Add(mTxtFooterHint);

            Border exitPill = new Border();
            exitPill.CornerRadius = new CornerRadius(5);
            exitPill.Padding = new Thickness(7, 2, 7, 2);
            exitPill.Margin = new Thickness(8, 0, 6, 0);
            exitPill.VerticalAlignment = VerticalAlignment.Center;
            exitPill.Cursor = Cursors.Hand;
            exitPill.Background = Brushes.Transparent;
            TextBlock exitTxt = new TextBlock();
            exitTxt.Text = "완전 종료";
            exitTxt.FontSize = 10;
            exitTxt.FontWeight = FontWeights.Bold;
            exitTxt.Foreground = ThemePalette.BrStopped;
            exitPill.Child = exitTxt;
            exitPill.MouseEnter += delegate { exitPill.Background = ThemePalette.BrCloseHover; };
            exitPill.MouseLeave += delegate { exitPill.Background = Brushes.Transparent; };
            exitPill.MouseLeftButtonUp += delegate { mCtrl.ShutdownCompletely(); };
            Grid.SetColumn(exitPill, 1);
            g.Children.Add(exitPill);

            Canvas grip = ThemePalette.MakeIcon(
                "M 5,11 L 11,5 M 8.5,11 L 11,8.5",
                12,
                ThemePalette.BrTextTertiary,
                true,
                1.3);
            grip.IsHitTestVisible = false;
            grip.VerticalAlignment = VerticalAlignment.Bottom;
            grip.Margin = new Thickness(0, 0, 2, 3);
            Grid.SetColumn(grip, 2);
            g.Children.Add(grip);

            footer.Child = g;
            return footer;
        }

        private Border CreatePillChip(string text, Action onClick)
        {
            TextBlock dummy;
            return CreatePillChipWithLabel(text, out dummy, onClick);
        }

        private Border CreatePillChipWithLabel(string text, out TextBlock label, Action onClick)
        {
            Border b = new Border();
            b.Background = ThemePalette.BrSurfaceHigh;
            b.BorderBrush = ThemePalette.BrOutline;
            b.BorderThickness = new Thickness(1);
            b.CornerRadius = new CornerRadius(6);
            b.Padding = new Thickness(8, 2, 8, 2);
            b.Cursor = Cursors.Hand;

            TextBlock tb = new TextBlock();
            tb.Text = text;
            tb.FontSize = 11;
            tb.FontWeight = FontWeights.Bold;
            tb.Foreground = ThemePalette.BrTextSecondary;
            tb.HorizontalAlignment = HorizontalAlignment.Center;
            tb.VerticalAlignment = VerticalAlignment.Center;
            b.Child = tb;

            b.MouseLeftButtonUp += delegate(object s, MouseButtonEventArgs e)
            {
                e.Handled = true;
                if (onClick != null) onClick();
            };
            label = tb;
            return b;
        }

        public void SyncAllFromController(bool allowEntryTabSync)
        {
            RefreshTopmostButton();
            RefreshVolumeControls();
            RefreshProfileControls();
            RefreshSleepTimerUi();
            RefreshFavoritesUi();
            RefreshDashboard(allowEntryTabSync);
        }

        public void RefreshFavoritesUi()
        {
            RefreshFavoriteFilterChipStyle();
            RenderPresetCards();
        }

        private void RefreshFavoriteFilterChipStyle()
        {
            int favCount = (mCtrl.State.FavoriteIds != null) ? mCtrl.State.FavoriteIds.Count : 0;
            mTxtFavFilter.Text = favCount > 0
                ? string.Format(CultureInfo.InvariantCulture, "즐겨찾기 {0}", favCount)
                : "즐겨찾기";

            if (mFavoritesOnly)
            {
                mFavFilterBtn.Background = ThemePalette.BrAmberBg;
                mFavFilterBtn.BorderBrush = ThemePalette.BrAmber;
                mTxtFavFilter.Foreground = ThemePalette.BrAmber;
                mFavFilterStar.Fill = ThemePalette.BrAmber;
                mFavFilterStar.Stroke = ThemePalette.BrAmber;
            }
            else
            {
                mFavFilterBtn.Background = ThemePalette.BrSurface;
                mFavFilterBtn.BorderBrush = ThemePalette.BrOutline;
                mTxtFavFilter.Foreground = ThemePalette.BrTextSecondary;
                mFavFilterStar.Fill = favCount > 0 ? ThemePalette.BrAmber : Brushes.Transparent;
                mFavFilterStar.Stroke = favCount > 0 ? ThemePalette.BrAmber : ThemePalette.BrTextSecondary;
            }
        }

        public void RefreshSleepTimerUi()
        {
            int presetMin = mCtrl.SleepTimerPresetMinutes;
            int remSec = mCtrl.SleepTimerRemainingSeconds;
            for (int i = 0; i < TIMER_VALUES.Length; i++)
            {
                bool active = (presetMin == TIMER_VALUES[i]);
                bool timerOn = (presetMin > 0 && active);
                mTimerChips[i].Background = timerOn ? ThemePalette.BrAmberBg : (active ? ThemePalette.BrSurfaceCard : ThemePalette.BrSurfaceHigh);
                mTimerChips[i].BorderBrush = timerOn ? ThemePalette.BrAmber : ThemePalette.BrOutline;
                mTimerLabels[i].Foreground = timerOn ? ThemePalette.BrAmber : (active ? ThemePalette.BrTextPrimary : ThemePalette.BrTextSecondary);
            }

            if (remSec > 0)
            {
                mTxtTimerBadge.Text = string.Format(CultureInfo.InvariantCulture, "{0:D2}:{1:D2} 자동정지", remSec / 60, remSec % 60);
                mTimerBadge.Visibility = Visibility.Visible;
            }
            else
            {
                mTimerBadge.Visibility = Visibility.Collapsed;
            }
        }

        public void SelectTab(int tabIndex, bool smoothScroll, bool persistIfIdle)
        {
            mSelectedTab = TabSyncPolicy.ClampTabIndex(tabIndex);
            if (persistIfIdle && !mCtrl.Dsp.IsPlaying)
            {
                mCtrl.SaveLastTab(mSelectedTab);
            }
            if (!string.IsNullOrEmpty(mSearchQuery) && persistIfIdle)
            {
                mSearchBox.Text = "";
            }
            RefreshTabStyles();
            RenderPresetCards();
            UpdateJumpCue();
        }

        public void JumpToPlayingPreset()
        {
            PresetItem p = mCtrl.Dsp.CurrentPreset;
            if (!mCtrl.Dsp.IsPlaying || p == null) return;
            if (mFavoritesOnly)
            {
                mFavoritesOnly = false;
                RefreshFavoriteFilterChipStyle();
            }
            if (!string.IsNullOrEmpty(mSearchQuery)) mSearchBox.Text = "";
            if (mSelectedTab != p.TabIndex)
            {
                SelectTab(p.TabIndex, true, false);
            }
            Dispatcher.BeginInvoke(DispatcherPriority.Loaded, new Action(delegate
            {
                if (mActiveCardBorder != null)
                {
                    mActiveCardBorder.BringIntoView();
                }
            }));
        }

        private void RefreshDeviceList()
        {
            mSuppressDeviceCallback = true;
            try
            {
                List<AudioEndpointInfo> endpoints = WasapiAudioEngine.EnumerateOutputDevices();
                mComboDevice.Items.Clear();
                int selIdx = 0;
                string wantId = mCtrl.State.DeviceId ?? "";
                for (int i = 0; i < endpoints.Count; i++)
                {
                    mComboDevice.Items.Add(endpoints[i]);
                    if (string.Equals(endpoints[i].Id, wantId, StringComparison.OrdinalIgnoreCase))
                    {
                        selIdx = i;
                    }
                }
                if (mComboDevice.Items.Count > 0)
                {
                    mComboDevice.SelectedIndex = selIdx;
                }
            }
            finally
            {
                mSuppressDeviceCallback = false;
            }
        }

        private void RefreshTopmostButton()
        {
            if (Topmost)
            {
                mTopmostBtn.Background = ThemePalette.BrAmberBg;
                mTopmostBtn.BorderBrush = ThemePalette.BrAmber;
                mTxtTopmost.Foreground = ThemePalette.BrAmber;
            }
            else
            {
                mTopmostBtn.Background = ThemePalette.BrSurfaceHigh;
                mTopmostBtn.BorderBrush = ThemePalette.BrOutline;
                mTxtTopmost.Foreground = ThemePalette.BrTextSecondary;
            }
        }

        public void RefreshVolumeControls()
        {
            int pct = mCtrl.Dsp.VolumePercent;
            bool muted = mCtrl.Dsp.IsMuted;

            mSuppressSeekCallback = true;
            mSeekVolume.Value = muted ? 0 : pct;
            mSuppressSeekCallback = false;

            string dbStr = VolumeCurve.FormatDb(pct, muted);
            if (muted)
            {
                mTxtVolumeReadout.Text = "음소거 · " + dbStr;
                mTxtVolumeReadout.Foreground = ThemePalette.BrStopped;
                mMuteBodyPath.Fill = ThemePalette.BrStopped;
                mMuteWavePath.Visibility = Visibility.Collapsed;
                mMuteCrossPath.Visibility = Visibility.Visible;
                mBtnMute.BorderBrush = ThemePalette.BrStopped;
                mTxtHeadroomNote.Text = "출력 무음 상태";
                mTxtHeadroomNote.Foreground = ThemePalette.BrTextTertiary;
            }
            else if (pct > VolumeCurve.UNITY_PERCENT)
            {
                mTxtVolumeReadout.Text = string.Format(CultureInfo.InvariantCulture, "{0}% · {1} 클린 증폭", pct, dbStr);
                mTxtVolumeReadout.Foreground = ThemePalette.BrCyan;
                mMuteBodyPath.Fill = ThemePalette.BrTextPrimary;
                mMuteWavePath.Stroke = ThemePalette.BrCyan;
                mMuteWavePath.Visibility = Visibility.Visible;
                mMuteCrossPath.Visibility = Visibility.Collapsed;
                mBtnMute.BorderBrush = ThemePalette.BrCyan;
                mTxtHeadroomNote.Text = "무왜곡 -0.5dBTP 리미터";
                mTxtHeadroomNote.Foreground = ThemePalette.BrCyan;
            }
            else
            {
                mTxtVolumeReadout.Text = string.Format(CultureInfo.InvariantCulture, "{0}% · {1}", pct, dbStr);
                mTxtVolumeReadout.Foreground = ThemePalette.BrAmber;
                mMuteBodyPath.Fill = ThemePalette.BrTextPrimary;
                mMuteWavePath.Stroke = ThemePalette.BrAmber;
                mMuteWavePath.Visibility = Visibility.Visible;
                mMuteCrossPath.Visibility = Visibility.Collapsed;
                mBtnMute.BorderBrush = ThemePalette.BrOutline;
                mTxtHeadroomNote.Text = "창 닫아도 계속 재생";
                mTxtHeadroomNote.Foreground = ThemePalette.BrTextTertiary;
            }

            for (int i = 0; i < DETENT_VALUES.Length; i++)
            {
                int val = DETENT_VALUES[i];
                bool active = !muted && (pct == val);
                bool isBoost = (val > VolumeCurve.UNITY_PERCENT);
                if (active)
                {
                    mDetentChips[i].Background = isBoost ? ThemePalette.BrCyanBg : ThemePalette.BrAmberBg;
                    mDetentChips[i].BorderBrush = isBoost ? ThemePalette.BrCyan : ThemePalette.BrAmber;
                    mDetentPcts[i].Foreground = isBoost ? ThemePalette.BrCyan : ThemePalette.BrAmber;
                    mDetentTags[i].Foreground = isBoost ? ThemePalette.BrCyan : ThemePalette.BrAmber;
                }
                else
                {
                    mDetentChips[i].Background = ThemePalette.BrSurfaceHigh;
                    mDetentChips[i].BorderBrush = isBoost ? ThemePalette.BrOutlineHover : ThemePalette.BrOutline;
                    mDetentPcts[i].Foreground = isBoost ? ThemePalette.BrCyan : ThemePalette.BrTextPrimary;
                    mDetentTags[i].Foreground = ThemePalette.BrTextTertiary;
                }
            }
        }

        public void RefreshProfileControls()
        {
            PcOutputProfile prof = mCtrl.Dsp.OutputProfile;
            for (int i = 0; i < 3; i++)
            {
                bool sel = (i == (int)prof);
                mProfileChips[i].Background = sel ? ThemePalette.BrAmberBg : ThemePalette.BrSurfaceHigh;
                mProfileChips[i].BorderBrush = sel ? ThemePalette.BrAmber : ThemePalette.BrOutline;
                mProfileLabels[i].Foreground = sel ? ThemePalette.BrAmber : ThemePalette.BrTextSecondary;
            }

            PresetItem cur = mCtrl.Dsp.CurrentPreset;
            bool showGuard = (mSelectedTab == 7) || (mCtrl.Dsp.IsPlaying && cur != null && cur.NoiseType == 2);
            mGuardChip.Visibility = showGuard ? Visibility.Visible : Visibility.Collapsed;
            bool effectiveOn = (prof == PcOutputProfile.Headphone) || mCtrl.Dsp.UltrasonicGuardEnabled;
            mTxtGuard.Text = effectiveOn ? "초음파 -12dB ON" : "초음파 보호 OFF";
            mGuardChip.BorderBrush = effectiveOn ? ThemePalette.BrActive : ThemePalette.BrAmber;
            mTxtGuard.Foreground = effectiveOn ? ThemePalette.BrActive : ThemePalette.BrAmber;
        }

        public void RefreshDashboard(bool allowEntryTabSync)
        {
            bool running = mCtrl.Dsp.IsPlaying;
            PresetItem p = running ? mCtrl.Dsp.CurrentPreset : null;
            mLastRunning = running && p != null;

            if (mLastRunning)
            {
                LinearGradientBrush accGrad = new LinearGradientBrush(
                    ThemePalette.Active, ThemePalette.Amber, new Point(0, 0), new Point(1, 0));
                accGrad.Freeze();
                mAccentBar.Background = accGrad;
                mStatusLed.Fill = ThemePalette.BrActive;
                mTxtStatus.Text = "재생 중 · " + TabSyncPolicy.TAB_SHORT_NAMES[p.TabIndex];
                mTxtStatus.Foreground = ThemePalette.BrActive;

                float hz = mCtrl.Dsp.LiveFreqHz > 0f ? mCtrl.Dsp.LiveFreqHz : (p.IsSweep ? p.SweepStart : p.Freq1);
                UpdateHeroViews(hz, p, true);
                mTxtScenarioTitle.Text = p.Title;
                UpdateMechanismStructured(p);
                UpdateTelemetryStrip(p, true);
                UpdateSweepBar(p, true);

                mActionBtn.Background = ThemePalette.BrStopBtn;
                mActionBtn.BorderBrush = ThemePalette.BrStopped;
                mActionIconPath.Data = Geometry.Parse("M 3,3 L 11,3 L 11,11 L 3,11 Z");
                mActionIconPath.Fill = ThemePalette.BrTextPrimary;
                mTxtActionBtn.Text = "재생 중지";
                mTxtActionBtn.Foreground = ThemePalette.BrTextPrimary;

                if (IsVisible && WindowState != WindowState.Minimized)
                {
                    mScopeTimer.Start();
                }
            }
            else
            {
                mScopeTimer.Stop();
                mAccentBar.Background = ThemePalette.BrOutline;
                mStatusLed.Fill = ThemePalette.BrTextTertiary;
                mTxtStatus.Text = "대기 중";
                mTxtStatus.Foreground = ThemePalette.BrTextSecondary;

                UpdateHeroViews(0f, null, false);
                mTxtScenarioTitle.Text = "프리셋을 선택하면 즉시 재생됩니다";
                UpdateMechanismStructured(null);
                UpdateTelemetryStrip(null, false);
                UpdateSweepBar(null, false);
                UpdateVuMeter(0f);

                PresetItem last = PresetCatalog.FindById(mCtrl.Presets, mCtrl.State.LastPresetId);
                mActionBtn.Background = ThemePalette.BrSurfaceHigh;
                mActionBtn.BorderBrush = ThemePalette.BrAmber;
                mActionIconPath.Data = Geometry.Parse("M 3,2 L 11,7 L 3,12 Z");
                mActionIconPath.Fill = ThemePalette.BrAmber;
                mTxtActionBtn.Text = last != null
                    ? ("바로 재생 (" + last.ShortCode + " · " + last.Title + ")")
                    : "프리셋 선택 시 재생";
                mTxtActionBtn.Foreground = ThemePalette.BrAmber;
            }

            mScope.UpdateFrame(mCtrl.Dsp);

            int targetTab = TabSyncPolicy.ResolveTabOnEntry(
                mLastRunning,
                mLastRunning ? p.TabIndex : -1,
                mCtrl.State.LastTab,
                mSelectedTab,
                allowEntryTabSync);

            if (targetTab != mSelectedTab)
            {
                SelectTab(targetTab, true, false);
            }
            else
            {
                int activeId = mLastRunning ? p.Id : -1;
                if (activeId != mLastHighlightedPresetId)
                {
                    RenderPresetCards();
                }
                RefreshTabStyles();
                UpdateJumpCue();
                RefreshProfileControls();
            }
        }

        private void UpdateHeroViews(float hz, PresetItem p, bool running)
        {
            if (!running || p == null)
            {
                mTxtHeroFreq.Text = "0.00 Hz";
                mTxtHeroFreq.Foreground = ThemePalette.BrTextTertiary;
                mHeroSubBadge.Visibility = Visibility.Collapsed;
                return;
            }

            mTxtHeroFreq.Text = hz >= 1000.0f
                ? string.Format(CultureInfo.InvariantCulture, "{0:F1} Hz", hz)
                : string.Format(CultureInfo.InvariantCulture, "{0:F2} Hz", hz);
            mTxtHeroFreq.Foreground = ThemePalette.BrAmber;

            string badge = "";
            if (p.BinauralBeat > 0f) badge = string.Format(CultureInfo.InvariantCulture, "L/R 차 {0:F2} Hz", p.BinauralBeat);
            else if (p.IsSweep) badge = "왕복 스윕";
            else if (p.Freq2 > 0f) badge = string.Format(CultureInfo.InvariantCulture, "+ {0:F2} Hz", p.Freq2);
            else if (p.FmMod) badge = "FM 변조";
            else if (p.AmMod) badge = "AM 변조";

            if (!string.IsNullOrEmpty(badge))
            {
                mTxtHeroSubText.Text = badge;
                mHeroSubBadge.Visibility = Visibility.Visible;
            }
            else
            {
                mHeroSubBadge.Visibility = Visibility.Collapsed;
            }
        }

        private void UpdateMechanismStructured(PresetItem p)
        {
            if (p == null)
            {
                mTxtActionDetail.Text = "64-bit 위상 연속 정현파 + -0.5 dBTP 무왜곡 헤드룸 대기";
                mTxtUsageDetail.Text = "유튜브·작업·AI 툴과 간섭 없이 트레이 백그라운드 동시 재생";
                mPcCompRow.Visibility = Visibility.Collapsed;
                return;
            }

            string raw = (p.Desc ?? "").Replace("\r", "");
            string[] lines = raw.Split('\n');
            List<string> cleaned = new List<string>();
            for (int i = 0; i < lines.Length; i++)
            {
                string s = lines[i].Trim();
                if (s.StartsWith("•")) s = s.Substring(1).Trim();
                if (!string.IsNullOrEmpty(s)) cleaned.Add(s);
            }

            mTxtActionDetail.Text = cleaned.Count > 0 ? cleaned[0] : p.BuildSpecLine();
            mTxtUsageDetail.Text = cleaned.Count > 1 ? cleaned[1] : (TabSyncPolicy.TAB_SHORT_NAMES[p.TabIndex] + " 권장 시나리오");

            string compNote = null;
            if (mCtrl.Dsp.IsUltrasonicGuarded)
            {
                compNote = "고주파 이어폰·스피커 보호용 -12 dB 안전 감쇄 적용 중";
            }
            else if (p.NoiseType != 2 && mCtrl.Dsp.OutputProfile == PcOutputProfile.PcSpeaker && p.Freq1 < 85.0f)
            {
                compNote = p.Freq1 < 32.0f
                    ? "소형 PC 스피커용 144 Hz 반송파 + 기본음 동시 출력 중"
                    : "소형 PC 스피커용 2·3차 배음(누락 기본음) 보정 중";
            }
            else if (mCtrl.Dsp.VolumePercent > VolumeCurve.UNITY_PERCENT)
            {
                compNote = "100% 초과 클린 증폭 활성화 — 클리핑 0% 무왜곡 리미터 동작 중";
            }

            if (!string.IsNullOrEmpty(compNote))
            {
                mTxtPcCompDetail.Text = compNote;
                mPcCompRow.Visibility = Visibility.Visible;
            }
            else
            {
                mPcCompRow.Visibility = Visibility.Collapsed;
            }
        }

        private void UpdateTelemetryStrip(PresetItem p, bool running)
        {
            PcOutputProfile prof = mCtrl.Dsp.OutputProfile;
            string profTxt = prof == PcOutputProfile.PcSpeaker
                ? "스피커 보정"
                : (prof == PcOutputProfile.Headphone ? "이어폰 보호" : "순수 원음");
            if (!running || p == null)
            {
                mTelPhase.Text = "대기 상태";
                mTelPhase.Foreground = ThemePalette.BrTextTertiary;
                mTelMod.Text = "변조 없음";
                mTelMod.Foreground = ThemePalette.BrTextTertiary;
                mTelProfile.Text = profTxt;
                mTelProfile.Foreground = ThemePalette.BrTextSecondary;
                mTelLevel.Text = "-inf dBFS";
                mTelLevel.Foreground = ThemePalette.BrTextTertiary;
                return;
            }

            if (p.IsAntiPhase)
            {
                mTelPhase.Text = "180° 역위상";
                mTelPhase.Foreground = ThemePalette.BrAmber;
            }
            else if (p.BinauralBeat > 0f)
            {
                mTelPhase.Text = "바이노럴 L/R";
                mTelPhase.Foreground = ThemePalette.BrCyan;
            }
            else if (p.IsSweep)
            {
                mTelPhase.Text = "삼각 왕복 스윕";
                mTelPhase.Foreground = ThemePalette.BrActive;
            }
            else if (p.Freq2 > 0f)
            {
                mTelPhase.Text = "듀얼 합성파";
                mTelPhase.Foreground = ThemePalette.BrAmber;
            }
            else
            {
                mTelPhase.Text = "순수 정현파";
                mTelPhase.Foreground = ThemePalette.BrActive;
            }

            if (p.NoiseType == 2)
            {
                mTelMod.Text = mCtrl.Dsp.IsUltrasonicGuarded ? "초음파 -12dB" : "초음파 직접";
                mTelMod.Foreground = mCtrl.Dsp.IsUltrasonicGuarded ? ThemePalette.BrStopped : ThemePalette.BrAmber;
            }
            else if (p.NoiseType == 3)
            {
                mTelMod.Text = "날갯짓 38.5Hz";
                mTelMod.Foreground = ThemePalette.BrAmber;
            }
            else if (p.NoiseType == 1)
            {
                mTelMod.Text = "핑크노이즈 15%";
                mTelMod.Foreground = ThemePalette.BrTextPrimary;
            }
            else if (p.FmMod && p.AmMod)
            {
                mTelMod.Text = "FM+AM 복합";
                mTelMod.Foreground = ThemePalette.BrAmber;
            }
            else if (p.FmMod)
            {
                mTelMod.Text = string.Format(CultureInfo.InvariantCulture, "FM ±{0:F0}%", p.FmDepth * 100.0f);
                mTelMod.Foreground = ThemePalette.BrAmber;
            }
            else if (p.AmMod)
            {
                mTelMod.Text = string.Format(CultureInfo.InvariantCulture, "AM {0:F0}%", p.AmDepth * 100.0f);
                mTelMod.Foreground = ThemePalette.BrAmber;
            }
            else
            {
                mTelMod.Text = "무변조 순수";
                mTelMod.Foreground = ThemePalette.BrTextSecondary;
            }

            if (prof == PcOutputProfile.PcSpeaker && p.NoiseType != 2 && p.Freq1 < 85.0f)
            {
                mTelProfile.Text = p.Freq1 < 32.0f ? "144Hz 반송" : "배음 보강";
                mTelProfile.Foreground = ThemePalette.BrAmber;
            }
            else
            {
                mTelProfile.Text = profTxt;
                mTelProfile.Foreground = ThemePalette.BrTextPrimary;
            }

            UpdateLiveLevelCell();
        }

        private void UpdateLiveLevelCell()
        {
            if (mCtrl.Dsp.IsMuted)
            {
                mTelLevel.Text = "음소거";
                mTelLevel.Foreground = ThemePalette.BrStopped;
                return;
            }
            float dbfs = mCtrl.Dsp.LiveRmsDbfs;
            float gr = mCtrl.Dsp.LimiterGainReductionDb;
            if (gr < -0.1f)
            {
                mTelLevel.Text = string.Format(CultureInfo.InvariantCulture, "{0:F1} (GR {1:F1})", dbfs, gr);
                mTelLevel.Foreground = ThemePalette.BrAmber;
            }
            else if (dbfs > -90.0f)
            {
                mTelLevel.Text = string.Format(CultureInfo.InvariantCulture, "{0:F1} dBFS", dbfs);
                mTelLevel.Foreground = mCtrl.Dsp.VolumePercent > VolumeCurve.UNITY_PERCENT
                    ? ThemePalette.BrCyan
                    : ThemePalette.BrActive;
            }
            else
            {
                mTelLevel.Text = "페이드 인";
                mTelLevel.Foreground = ThemePalette.BrTextSecondary;
            }
        }

        private void UpdateSweepBar(PresetItem p, bool running)
        {
            if (!running || p == null || !p.IsSweep)
            {
                mSweepPanel.Visibility = Visibility.Collapsed;
                return;
            }
            mSweepPanel.Visibility = Visibility.Visible;
            mTxtSweepLeft.Text = string.Format(CultureInfo.InvariantCulture, "{0:F1} Hz", p.SweepStart);
            mTxtSweepRight.Text = string.Format(CultureInfo.InvariantCulture, "{0:F1} Hz", p.SweepEnd);
            double trackW = mSweepTrack.ActualWidth;
            if (trackW > 4)
            {
                double prog = Math.Max(0.0, Math.Min(1.0, mCtrl.Dsp.SweepProgress));
                mSweepFill.Width = Math.Round(trackW * prog);
            }
        }

        private void UpdateVuMeter(double normLevel)
        {
            int litCount = (int)Math.Round(normLevel * mVuBars.Count);
            if (normLevel > 0.01 && litCount < 1) litCount = 1;
            for (int i = 0; i < mVuBars.Count; i++)
            {
                if (i < litCount)
                {
                    if (i >= 11) mVuBars[i].Background = ThemePalette.BrStopped;
                    else if (i >= 8) mVuBars[i].Background = ThemePalette.BrAmber;
                    else mVuBars[i].Background = ThemePalette.BrActive;
                }
                else
                {
                    mVuBars[i].Background = ThemePalette.BrSurfaceHigh;
                }
            }
        }

        private void UpdateJumpCue()
        {
            PresetItem p = mCtrl.Dsp.CurrentPreset;
            if (!mCtrl.Dsp.IsPlaying || p == null)
            {
                mJumpCueBtn.Visibility = Visibility.Collapsed;
                return;
            }
            mJumpCueBtn.Visibility = Visibility.Visible;
            bool diff = TabSyncPolicy.IsViewingDifferentTab(true, p.TabIndex, mSelectedTab)
                || !string.IsNullOrEmpty(mSearchQuery)
                || mFavoritesOnly;
            if (diff)
            {
                mTxtJumpCue.Text = "재생 탭 ›";
                mTxtJumpCue.Foreground = ThemePalette.BrActive;
                mJumpCueBtn.BorderBrush = ThemePalette.BrActive;
            }
            else
            {
                mTxtJumpCue.Text = "위치 보기 ›";
                mTxtJumpCue.Foreground = ThemePalette.BrTextSecondary;
                mJumpCueBtn.BorderBrush = ThemePalette.BrOutline;
            }
        }

        private void RefreshTabStyles()
        {
            PresetItem p = (mCtrl.Dsp.IsPlaying ? mCtrl.Dsp.CurrentPreset : null);
            int playingTab = p != null ? p.TabIndex : -1;

            for (int i = 0; i < mTabChips.Count; i++)
            {
                bool isSel = (i == mSelectedTab) && string.IsNullOrEmpty(mSearchQuery) && !mFavoritesOnly;
                bool isOwner = (i == playingTab);

                StackPanel content = new StackPanel();
                content.Orientation = Orientation.Horizontal;
                content.VerticalAlignment = VerticalAlignment.Center;

                if (isOwner)
                {
                    Ellipse dot = new Ellipse();
                    dot.Width = 6.5;
                    dot.Height = 6.5;
                    dot.Fill = isSel ? ThemePalette.BrActiveOnLight : ThemePalette.BrActive;
                    dot.Margin = new Thickness(0, 0, 5, 0);
                    dot.VerticalAlignment = VerticalAlignment.Center;
                    content.Children.Add(dot);
                }

                TextBlock lbl = new TextBlock();
                lbl.Text = TabSyncPolicy.TAB_NAMES[i];
                lbl.FontSize = 11;
                lbl.FontWeight = FontWeights.Bold;
                lbl.VerticalAlignment = VerticalAlignment.Center;
                lbl.Foreground = isSel ? ThemePalette.BrBg : (isOwner ? ThemePalette.BrTextPrimary : ThemePalette.BrTextSecondary);
                content.Children.Add(lbl);

                Border chip = mTabChips[i];
                chip.Child = content;
                if (isSel)
                {
                    chip.Background = ThemePalette.BrTextPrimary;
                    chip.BorderBrush = ThemePalette.BrTextPrimary;
                }
                else if (isOwner)
                {
                    chip.Background = ThemePalette.BrActiveBg;
                    chip.BorderBrush = ThemePalette.BrActive;
                }
                else
                {
                    chip.Background = ThemePalette.BrSurface;
                    chip.BorderBrush = ThemePalette.BrOutline;
                }
            }
        }

        private void RenderPresetCards()
        {
            if (mPresetGrid == null) return;
            mPresetGrid.Children.Clear();
            mActiveCardBorder = null;
            int activeId = (mCtrl.Dsp.IsPlaying && mCtrl.Dsp.CurrentPreset != null) ? mCtrl.Dsp.CurrentPreset.Id : -1;
            mLastHighlightedPresetId = activeId;

            string q = mSearchQuery.ToLowerInvariant();
            bool hasQuery = !string.IsNullOrEmpty(q);
            int cols = mPresetCols > 0 ? mPresetCols : 1;

            for (int i = 0; i < mCtrl.Presets.Count; i++)
            {
                PresetItem preset = mCtrl.Presets[i];
                if (mFavoritesOnly && !mCtrl.IsFavorite(preset.Id))
                {
                    continue;
                }
                if (hasQuery)
                {
                    bool hit = (preset.Title != null && preset.Title.ToLowerInvariant().Contains(q))
                        || (preset.Badge != null && preset.Badge.ToLowerInvariant().Contains(q))
                        || (preset.Desc != null && preset.Desc.ToLowerInvariant().Contains(q))
                        || (preset.ShortCode != null && preset.ShortCode.ToLowerInvariant().Contains(q))
                        || TabSyncPolicy.TAB_SHORT_NAMES[preset.TabIndex].Contains(mSearchQuery);
                    if (!hit) continue;
                }
                else if (!mFavoritesOnly && preset.TabIndex != mSelectedTab)
                {
                    continue;
                }

                Border card = BuildPresetCard(preset, preset.Id == activeId, cols);
                if (preset.Id == activeId) mActiveCardBorder = card;
                mPresetGrid.Children.Add(card);
            }

            if (mTxtMatchCount != null)
            {
                mTxtMatchCount.Text = string.Format(CultureInfo.InvariantCulture, "{0}종", mPresetGrid.Children.Count);
            }

            if (mPresetGrid.Children.Count == 0)
            {
                Border emptyWrap = new Border();
                emptyWrap.Padding = new Thickness(12, 24, 12, 24);
                TextBlock empty = new TextBlock();
                empty.Text = mFavoritesOnly
                    ? "즐겨찾기에 추가된 프리셋이 없습니다. 카드 우측 상단 별 아이콘을 눌러 추가하세요."
                    : "일치하는 프리셋이 없습니다.";
                empty.Foreground = ThemePalette.BrTextTertiary;
                empty.FontSize = 11.5;
                empty.TextWrapping = TextWrapping.Wrap;
                empty.TextAlignment = TextAlignment.Center;
                empty.HorizontalAlignment = HorizontalAlignment.Center;
                emptyWrap.Child = empty;
                mPresetGrid.Children.Add(emptyWrap);
            }
        }

        private Border BuildPresetCard(PresetItem preset, bool isActive, int cols)
        {
            bool isFav = mCtrl.IsFavorite(preset.Id);

            Border card = new Border();
            card.Background = isActive ? ThemePalette.BrActiveBg : ThemePalette.BrSurfaceCard;
            card.BorderBrush = isActive ? ThemePalette.BrActive : ThemePalette.BrOutline;
            card.BorderThickness = new Thickness(isActive ? 1.5 : 1);
            card.CornerRadius = new CornerRadius(10);
            card.Padding = new Thickness(12, 10, 12, 10);
            card.Margin = (cols >= 2) ? new Thickness(0, 0, 8, 8) : new Thickness(0, 0, 2, 8);
            card.Cursor = Cursors.Hand;

            StackPanel col = new StackPanel();

            Grid eyebrow = new Grid();
            eyebrow.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            eyebrow.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            TextBlock badge = new TextBlock();
            badge.Text = preset.ShortCode + " · " + preset.Badge;
            badge.FontFamily = ThemePalette.FontMono;
            badge.FontSize = 10;
            badge.Foreground = ThemePalette.BrTextSecondary;
            badge.VerticalAlignment = VerticalAlignment.Center;
            badge.TextTrimming = TextTrimming.CharacterEllipsis;
            eyebrow.Children.Add(badge);

            StackPanel rightMeta = new StackPanel();
            rightMeta.Orientation = Orientation.Horizontal;
            rightMeta.VerticalAlignment = VerticalAlignment.Center;
            Grid.SetColumn(rightMeta, 1);

            if (isActive)
            {
                Border activePill = new Border();
                activePill.Background = ThemePalette.BrActive;
                activePill.CornerRadius = new CornerRadius(4);
                activePill.Padding = new Thickness(5, 1, 5, 1);
                activePill.Margin = new Thickness(4, 0, 6, 0);
                TextBlock flag = new TextBlock();
                flag.Text = "재생 중";
                flag.FontSize = 9.5;
                flag.FontWeight = FontWeights.Bold;
                flag.Foreground = ThemePalette.BrBg;
                activePill.Child = flag;
                rightMeta.Children.Add(activePill);
            }

            Border favBtn = new Border();
            favBtn.Width = 22;
            favBtn.Height = 22;
            favBtn.CornerRadius = new CornerRadius(5);
            favBtn.Background = Brushes.Transparent;
            favBtn.Cursor = Cursors.Hand;
            favBtn.ToolTip = isFav ? "즐겨찾기 해제" : "즐겨찾기 추가";
            Canvas starCv = ThemePalette.MakeIcon(
                "M 6.5,1 L 8.1,4.5 L 12,5 L 9.2,7.7 L 9.9,11.6 L 6.5,9.8 L 3.1,11.6 L 3.8,7.7 L 1,5 L 4.9,4.5 Z",
                13,
                isFav ? ThemePalette.BrAmber : ThemePalette.BrTextTertiary,
                true,
                1.25);
            System.Windows.Shapes.Path starPath = (System.Windows.Shapes.Path)starCv.Children[0];
            starPath.Fill = isFav ? ThemePalette.BrAmber : Brushes.Transparent;
            favBtn.Child = starCv;
            favBtn.MouseEnter += delegate { favBtn.Background = ThemePalette.BrSurfaceHover; };
            favBtn.MouseLeave += delegate { favBtn.Background = Brushes.Transparent; };
            favBtn.MouseLeftButtonDown += delegate(object s, MouseButtonEventArgs e) { e.Handled = true; };
            favBtn.MouseLeftButtonUp += delegate(object s, MouseButtonEventArgs e)
            {
                e.Handled = true;
                mCtrl.ToggleFavorite(preset.Id);
            };
            rightMeta.Children.Add(favBtn);
            eyebrow.Children.Add(rightMeta);
            col.Children.Add(eyebrow);

            TextBlock title = new TextBlock();
            title.Text = preset.Title;
            title.FontSize = 13.5;
            title.FontWeight = FontWeights.Bold;
            title.Foreground = ThemePalette.BrTextPrimary;
            title.TextWrapping = TextWrapping.Wrap;
            title.Margin = new Thickness(0, 3, 0, 3);
            col.Children.Add(title);

            TextBlock spec = new TextBlock();
            spec.Text = preset.BuildSpecLine();
            spec.FontFamily = ThemePalette.FontMono;
            spec.FontSize = 10.5;
            spec.FontWeight = FontWeights.Bold;
            spec.Foreground = ThemePalette.BrAmber;
            spec.TextWrapping = TextWrapping.Wrap;
            spec.Margin = new Thickness(0, 0, 0, 4);
            col.Children.Add(spec);

            TextBlock desc = new TextBlock();
            desc.Text = preset.Desc;
            desc.FontSize = 11;
            desc.Foreground = ThemePalette.BrTextSecondary;
            desc.TextWrapping = TextWrapping.Wrap;
            desc.LineHeight = 15.5;
            col.Children.Add(desc);

            card.Child = col;
            card.MouseEnter += delegate
            {
                if (!isActive)
                {
                    card.Background = ThemePalette.BrSurfaceHigh;
                    card.BorderBrush = ThemePalette.BrAmber;
                }
            };
            card.MouseLeave += delegate
            {
                if (!isActive)
                {
                    card.Background = ThemePalette.BrSurfaceCard;
                    card.BorderBrush = ThemePalette.BrOutline;
                }
            };
            card.MouseLeftButtonUp += delegate { mCtrl.PlayPreset(preset); };
            return card;
        }

        private void OnScopeTick(object sender, EventArgs e)
        {
            if (!mCtrl.Dsp.IsPlaying || mCtrl.Dsp.CurrentPreset == null)
            {
                mScopeTimer.Stop();
                mScope.UpdateFrame(mCtrl.Dsp);
                return;
            }
            PresetItem p = mCtrl.Dsp.CurrentPreset;
            float hz = mCtrl.Dsp.LiveFreqHz > 0f ? mCtrl.Dsp.LiveFreqHz : (p.IsSweep ? p.SweepStart : p.Freq1);
            UpdateHeroViews(hz, p, true);
            double norm = Math.Max(0.0, Math.Min(1.0, mCtrl.Dsp.PeakLevel / AegisDspEngine.CLEAN_BOOST_CEILING));
            UpdateVuMeter(norm);
            UpdateLiveLevelCell();
            if (p.IsSweep) UpdateSweepBar(p, true);
            mScope.UpdateFrame(mCtrl.Dsp);
        }

        private void OnTelemetryTick(object sender, EventArgs e)
        {
            bool running = mCtrl.Dsp.IsPlaying;
            PresetItem p = running ? mCtrl.Dsp.CurrentPreset : null;

            if (running != mLastRunning || (running && p != null && p.Id != mLastHighlightedPresetId))
            {
                RefreshDashboard(false);
            }
        }

        private void OnPreviewKeyDown(object sender, KeyEventArgs e)
        {
            if (mSearchBox != null && mSearchBox.IsKeyboardFocused)
            {
                if (e.Key == Key.Escape)
                {
                    if (!string.IsNullOrEmpty(mSearchBox.Text)) mSearchBox.Text = "";
                    Keyboard.ClearFocus();
                    e.Handled = true;
                }
                return;
            }

            ModifierKeys mods = Keyboard.Modifiers;
            if ((mods & ModifierKeys.Control) != 0)
            {
                if (e.Key == Key.F)
                {
                    mSearchBox.Focus();
                    mSearchBox.SelectAll();
                    e.Handled = true;
                    return;
                }
                if (e.Key == Key.D0 || e.Key == Key.NumPad0)
                {
                    mFavoritesOnly = !mFavoritesOnly;
                    RefreshFavoritesUi();
                    e.Handled = true;
                    return;
                }
                int numKey = -1;
                if (e.Key >= Key.D1 && e.Key <= Key.D8) numKey = (int)(e.Key - Key.D1);
                else if (e.Key >= Key.NumPad1 && e.Key <= Key.NumPad8) numKey = (int)(e.Key - Key.NumPad1);
                if (numKey >= 0 && numKey < TabSyncPolicy.TAB_COUNT)
                {
                    if (mFavoritesOnly) mFavoritesOnly = false;
                    SelectTab(numKey, true, true);
                    RefreshFavoriteFilterChipStyle();
                    e.Handled = true;
                    return;
                }
            }

            if ((mods & ModifierKeys.Alt) != 0)
            {
                if (e.Key == Key.Left)
                {
                    mCtrl.PlayAdjacentPreset(-1);
                    e.Handled = true;
                    return;
                }
                if (e.Key == Key.Right)
                {
                    mCtrl.PlayAdjacentPreset(1);
                    e.Handled = true;
                    return;
                }
            }

            if (e.Key == Key.Space)
            {
                if (mCtrl.Dsp.IsPlaying) mCtrl.StopPlayback();
                else
                {
                    PresetItem last = PresetCatalog.FindById(mCtrl.Presets, mCtrl.State.LastPresetId);
                    if (last != null) mCtrl.PlayPreset(last);
                }
                e.Handled = true;
            }
            else if (e.Key == Key.Up)
            {
                int next = VolumeCurve.ClampPercent((mCtrl.Dsp.IsMuted ? 0 : mCtrl.Dsp.VolumePercent) + 5);
                mCtrl.ApplyVolume(next, next == 0);
                e.Handled = true;
            }
            else if (e.Key == Key.Down)
            {
                int next = VolumeCurve.ClampPercent((mCtrl.Dsp.IsMuted ? 0 : mCtrl.Dsp.VolumePercent) - 5);
                mCtrl.ApplyVolume(next, next == 0);
                e.Handled = true;
            }
            else if (e.Key == Key.M)
            {
                if (mCtrl.Dsp.IsMuted)
                {
                    int pct = mCtrl.Dsp.VolumePercent == 0 ? VolumeCurve.DEFAULT_PERCENT : mCtrl.Dsp.VolumePercent;
                    mCtrl.ApplyVolume(pct, false);
                }
                else
                {
                    mCtrl.ApplyVolume(mCtrl.Dsp.VolumePercent, true);
                }
                e.Handled = true;
            }
            else if (e.Key == Key.Escape)
            {
                if (!string.IsNullOrEmpty(mSearchQuery))
                {
                    mSearchBox.Text = "";
                }
                else if (mFavoritesOnly)
                {
                    mFavoritesOnly = false;
                    RefreshFavoritesUi();
                }
                else
                {
                    mCtrl.HideToTray();
                }
                e.Handled = true;
            }
        }

        private void OnVisibilityChanged(object sender, DependencyPropertyChangedEventArgs e)
        {
            if (IsVisible && WindowState != WindowState.Minimized)
            {
                mTelemetryTimer.Start();
                if (mCtrl.Dsp.IsPlaying) mScopeTimer.Start();
            }
            else
            {
                mScopeTimer.Stop();
                mTelemetryTimer.Stop();
            }
        }

        private void OnLocationChanged(object sender, EventArgs e)
        {
            if (WindowState == WindowState.Normal && IsVisible)
            {
                mCtrl.State.WindowLeft = Left;
                mCtrl.State.WindowTop = Top;
            }
        }

        protected override void OnStateChanged(EventArgs e)
        {
            base.OnStateChanged(e);
            mCtrl.State.WindowMaximized = (WindowState == WindowState.Maximized);
            if (mOuterBorder != null)
            {
                if (WindowState == WindowState.Maximized)
                {
                    mOuterBorder.Padding = new Thickness(7);
                    mOuterBorder.BorderThickness = new Thickness(0);
                    mOuterBorder.CornerRadius = new CornerRadius(0);
                    if (mMaxIconPath != null) mMaxIconPath.Data = Geometry.Parse("M 4,2 L 10.5,2 L 10.5,8.5 M 1.5,4 L 8.5,4 L 8.5,10.5 L 1.5,10.5 Z");
                }
                else
                {
                    mOuterBorder.Padding = new Thickness(0);
                    mOuterBorder.BorderThickness = new Thickness(1);
                    mOuterBorder.CornerRadius = new CornerRadius(10);
                    if (mMaxIconPath != null) mMaxIconPath.Data = Geometry.Parse("M 2,2 L 10,2 L 10,10 L 2,10 Z");
                }
            }

            if (WindowState == WindowState.Minimized || !IsVisible)
            {
                mScopeTimer.Stop();
                mTelemetryTimer.Stop();
            }
            else
            {
                mTelemetryTimer.Start();
                if (mCtrl.Dsp.IsPlaying) mScopeTimer.Start();
            }
        }

        protected override void OnClosing(CancelEventArgs e)
        {
            if (!mCtrl.AllowExit)
            {
                e.Cancel = true;
                mCtrl.HideToTray();
                return;
            }
            mScopeTimer.Stop();
            mTelemetryTimer.Stop();
            base.OnClosing(e);
        }
    }

    public sealed class MiniBarWindow : Window
    {
        private readonly IAppHostController mCtrl;
        private readonly DispatcherTimer mTimer;
        private Ellipse mLed;
        private TextBlock mTitle;
        private TextBlock mFreqVol;
        private Border mMuteBtn;
        private System.Windows.Shapes.Path mMuteWave;
        private System.Windows.Shapes.Path mMuteCross;
        private Slider mVolSlider;
        private bool mSuppress;
        private Border mToggleBtn;
        private System.Windows.Shapes.Path mToggleIcon;

        public MiniBarWindow(IAppHostController ctrl)
        {
            mCtrl = ctrl;
            Title = "보르타 이지스 미니";
            WindowStyle = WindowStyle.None;
            ResizeMode = ResizeMode.CanResize;
            MinWidth = 340;
            MaxWidth = 720;
            MinHeight = 64;
            MaxHeight = 64;
            Width = ctrl.State.MiniWidth;
            Height = 64;
            ShowInTaskbar = false;
            Topmost = true;
            Background = ThemePalette.BrBg;
            FontFamily = ThemePalette.FontUi;
            UseLayoutRounding = true;

            WindowChrome chrome = new WindowChrome();
            chrome.CaptionHeight = 0;
            chrome.ResizeBorderThickness = new Thickness(5);
            chrome.CornerRadius = new CornerRadius(12);
            chrome.GlassFrameThickness = new Thickness(0);
            chrome.UseAeroCaptionButtons = false;
            WindowChrome.SetWindowChrome(this, chrome);

            Rect wa = SystemParameters.WorkArea;
            double vsLeft = SystemParameters.VirtualScreenLeft;
            double vsTop = SystemParameters.VirtualScreenTop;
            double vsRight = vsLeft + SystemParameters.VirtualScreenWidth;
            double vsBottom = vsTop + SystemParameters.VirtualScreenHeight;
            WindowStartupLocation = WindowStartupLocation.Manual;
            if (!double.IsNaN(ctrl.State.MiniLeft) && !double.IsNaN(ctrl.State.MiniTop))
            {
                Left = Math.Max(vsLeft, Math.Min(vsRight - Width, ctrl.State.MiniLeft));
                Top = Math.Max(vsTop, Math.Min(vsBottom - Height, ctrl.State.MiniTop));
            }
            else
            {
                Left = Math.Max(0, wa.Right - Width - 18);
                Top = Math.Max(0, wa.Bottom - Height - 18);
            }

            Border outer = new Border();
            outer.Background = ThemePalette.BrSurface;
            outer.BorderBrush = ThemePalette.BrOutline;
            outer.BorderThickness = new Thickness(1);
            outer.CornerRadius = new CornerRadius(12);
            outer.Padding = new Thickness(10, 6, 8, 6);
            outer.MouseLeftButtonDown += delegate(object s, MouseButtonEventArgs e)
            {
                if (e.ButtonState == MouseButtonState.Pressed && !(e.OriginalSource is Thumb))
                {
                    try { DragMove(); } catch { }
                }
            };
            outer.PreviewMouseWheel += delegate(object s, MouseWheelEventArgs e)
            {
                int next = VolumeCurve.ClampPercent((mCtrl.Dsp.IsMuted ? 0 : mCtrl.Dsp.VolumePercent) + (e.Delta > 0 ? 2 : -2));
                mCtrl.ApplyVolume(next, next == 0);
                e.Handled = true;
            };

            Grid g = new Grid();
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(86) });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            g.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

            mLed = new Ellipse();
            mLed.Width = 8.5;
            mLed.Height = 8.5;
            mLed.Fill = ThemePalette.BrTextTertiary;
            mLed.VerticalAlignment = VerticalAlignment.Center;
            mLed.Margin = new Thickness(0, 0, 8, 0);
            g.Children.Add(mLed);

            StackPanel info = new StackPanel();
            info.VerticalAlignment = VerticalAlignment.Center;
            info.Margin = new Thickness(0, 0, 8, 0);
            mTitle = new TextBlock();
            mTitle.FontSize = 11.5;
            mTitle.FontWeight = FontWeights.Bold;
            mTitle.Foreground = ThemePalette.BrTextPrimary;
            mTitle.TextTrimming = TextTrimming.CharacterEllipsis;
            info.Children.Add(mTitle);

            mFreqVol = new TextBlock();
            mFreqVol.FontFamily = ThemePalette.FontMono;
            mFreqVol.FontSize = 10;
            mFreqVol.Foreground = ThemePalette.BrAmber;
            mFreqVol.TextTrimming = TextTrimming.CharacterEllipsis;
            info.Children.Add(mFreqVol);
            Grid.SetColumn(info, 1);
            g.Children.Add(info);

            Border prevBtn = MakeMiniIconChip("M 8,3 L 4,7 L 8,11", 13, true, "이전 프리셋", delegate
            {
                mCtrl.PlayAdjacentPreset(-1);
            });
            prevBtn.Margin = new Thickness(0, 0, 4, 0);
            Grid.SetColumn(prevBtn, 2);
            g.Children.Add(prevBtn);

            Canvas tglCv = ThemePalette.MakeIcon("M 3,2 L 10,6.5 L 3,11 Z", 13, ThemePalette.BrAmber, false, 0);
            mToggleIcon = (System.Windows.Shapes.Path)tglCv.Children[0];
            mToggleBtn = new Border();
            mToggleBtn.Width = 28;
            mToggleBtn.Height = 28;
            mToggleBtn.Background = ThemePalette.BrAmberBg;
            mToggleBtn.BorderBrush = ThemePalette.BrAmber;
            mToggleBtn.BorderThickness = new Thickness(1);
            mToggleBtn.CornerRadius = new CornerRadius(6);
            mToggleBtn.Cursor = Cursors.Hand;
            mToggleBtn.Child = tglCv;
            mToggleBtn.Margin = new Thickness(0, 0, 4, 0);
            mToggleBtn.MouseLeftButtonUp += delegate(object s, MouseButtonEventArgs e)
            {
                e.Handled = true;
                if (mCtrl.Dsp.IsPlaying) mCtrl.StopPlayback();
                else
                {
                    PresetItem last = PresetCatalog.FindById(mCtrl.Presets, mCtrl.State.LastPresetId);
                    if (last != null) mCtrl.PlayPreset(last);
                }
            };
            Grid.SetColumn(mToggleBtn, 3);
            g.Children.Add(mToggleBtn);

            Border nextBtn = MakeMiniIconChip("M 5,3 L 9,7 L 5,11", 13, true, "다음 프리셋", delegate
            {
                mCtrl.PlayAdjacentPreset(1);
            });
            nextBtn.Margin = new Thickness(0, 0, 6, 0);
            Grid.SetColumn(nextBtn, 4);
            g.Children.Add(nextBtn);

            Canvas muteCv = new Canvas { Width = 14, Height = 14, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
            System.Windows.Shapes.Path mBody = new System.Windows.Shapes.Path { Data = Geometry.Parse("M 1.5,4.5 L 4,4.5 L 7.5,2 L 7.5,12 L 4,9.5 L 1.5,9.5 Z"), Fill = ThemePalette.BrTextPrimary };
            mMuteWave = new System.Windows.Shapes.Path { Data = Geometry.Parse("M 9.5,4.5 Q 11,7 9.5,9.5"), Stroke = ThemePalette.BrAmber, StrokeThickness = 1.4, StrokeStartLineCap = PenLineCap.Round, StrokeEndLineCap = PenLineCap.Round };
            mMuteCross = new System.Windows.Shapes.Path { Data = Geometry.Parse("M 9.5,5 L 12.5,9 M 12.5,5 L 9.5,9"), Stroke = ThemePalette.BrStopped, StrokeThickness = 1.4, StrokeStartLineCap = PenLineCap.Round, StrokeEndLineCap = PenLineCap.Round, Visibility = Visibility.Collapsed };
            muteCv.Children.Add(mBody);
            muteCv.Children.Add(mMuteWave);
            muteCv.Children.Add(mMuteCross);
            mMuteBtn = new Border();
            mMuteBtn.Width = 26;
            mMuteBtn.Height = 26;
            mMuteBtn.Background = ThemePalette.BrSurfaceHigh;
            mMuteBtn.BorderBrush = ThemePalette.BrOutline;
            mMuteBtn.BorderThickness = new Thickness(1);
            mMuteBtn.CornerRadius = new CornerRadius(6);
            mMuteBtn.Cursor = Cursors.Hand;
            mMuteBtn.Margin = new Thickness(0, 0, 5, 0);
            mMuteBtn.ToolTip = "음소거 전환";
            mMuteBtn.Child = muteCv;
            mMuteBtn.MouseLeftButtonUp += delegate(object s, MouseButtonEventArgs e)
            {
                e.Handled = true;
                if (mCtrl.Dsp.IsMuted)
                {
                    int pct = mCtrl.Dsp.VolumePercent == 0 ? VolumeCurve.DEFAULT_PERCENT : mCtrl.Dsp.VolumePercent;
                    mCtrl.ApplyVolume(pct, false);
                }
                else
                {
                    mCtrl.ApplyVolume(mCtrl.Dsp.VolumePercent, true);
                }
            };
            Grid.SetColumn(mMuteBtn, 5);
            g.Children.Add(mMuteBtn);

            mVolSlider = new Slider();
            mVolSlider.Minimum = 0;
            mVolSlider.Maximum = VolumeCurve.MAX_PERCENT;
            mVolSlider.Style = ThemePalette.CreateDarkSliderStyle();
            mVolSlider.VerticalAlignment = VerticalAlignment.Center;
            mVolSlider.ValueChanged += delegate(object s, RoutedPropertyChangedEventArgs<double> e)
            {
                if (mSuppress) return;
                int v = (int)Math.Round(e.NewValue);
                mCtrl.ApplyVolume(v, v == 0);
            };
            Grid.SetColumn(mVolSlider, 6);
            g.Children.Add(mVolSlider);

            TextBlock expTxt;
            Border expBtn = MakeMiniChip("전체", out expTxt, delegate { mCtrl.SwitchToMiniMode(false); });
            expBtn.Margin = new Thickness(6, 0, 4, 0);
            Grid.SetColumn(expBtn, 7);
            g.Children.Add(expBtn);

            Border hideBtn = MakeMiniIconChip("M 3,3 L 9,9 M 9,3 L 3,9", 12, true, "트레이로 숨기기", delegate { mCtrl.HideToTray(); });
            hideBtn.Width = 24;
            hideBtn.Height = 26;
            Grid.SetColumn(hideBtn, 8);
            g.Children.Add(hideBtn);

            outer.Child = g;
            Content = outer;

            mTimer = new DispatcherTimer();
            mTimer.Interval = TimeSpan.FromMilliseconds(220);
            mTimer.Tick += delegate { SyncMini(); };

            SizeChanged += delegate
            {
                if (IsVisible) mCtrl.State.MiniWidth = Width;
            };
            LocationChanged += delegate
            {
                if (IsVisible)
                {
                    mCtrl.State.MiniLeft = Left;
                    mCtrl.State.MiniTop = Top;
                }
            };
            Deactivated += delegate { AppStateStore.Save(mCtrl.State); };
            IsVisibleChanged += delegate
            {
                if (IsVisible) mTimer.Start();
                else mTimer.Stop();
            };
            SyncMini();
        }

        private static Border MakeMiniIconChip(string pathData, double size, bool isStroke, string tip, Action act)
        {
            Border b = new Border();
            b.Width = 26;
            b.Height = 26;
            b.Background = ThemePalette.BrSurfaceHigh;
            b.BorderBrush = ThemePalette.BrOutline;
            b.BorderThickness = new Thickness(1);
            b.CornerRadius = new CornerRadius(6);
            b.Cursor = Cursors.Hand;
            b.ToolTip = tip;
            b.VerticalAlignment = VerticalAlignment.Center;
            b.Child = ThemePalette.MakeIcon(pathData, size, ThemePalette.BrTextPrimary, isStroke, 1.5);
            b.MouseEnter += delegate { b.Background = ThemePalette.BrSurfaceHover; };
            b.MouseLeave += delegate { b.Background = ThemePalette.BrSurfaceHigh; };
            b.MouseLeftButtonUp += delegate(object s, MouseButtonEventArgs e)
            {
                e.Handled = true;
                if (act != null) act();
            };
            return b;
        }

        private static Border MakeMiniChip(string txt, out TextBlock lbl, Action act)
        {
            Border b = new Border();
            b.Height = 26;
            b.Background = ThemePalette.BrSurfaceHigh;
            b.BorderBrush = ThemePalette.BrOutline;
            b.BorderThickness = new Thickness(1);
            b.CornerRadius = new CornerRadius(6);
            b.Padding = new Thickness(7, 0, 7, 0);
            b.Cursor = Cursors.Hand;
            b.VerticalAlignment = VerticalAlignment.Center;
            TextBlock tb = new TextBlock();
            tb.Text = txt;
            tb.FontSize = 10.5;
            tb.FontWeight = FontWeights.Bold;
            tb.Foreground = ThemePalette.BrTextPrimary;
            tb.HorizontalAlignment = HorizontalAlignment.Center;
            tb.VerticalAlignment = VerticalAlignment.Center;
            b.Child = tb;
            b.MouseEnter += delegate { b.Background = ThemePalette.BrSurfaceHover; };
            b.MouseLeave += delegate { b.Background = ThemePalette.BrSurfaceHigh; };
            b.MouseLeftButtonUp += delegate(object s, MouseButtonEventArgs e)
            {
                e.Handled = true;
                if (act != null) act();
            };
            lbl = tb;
            return b;
        }

        public void SyncMini()
        {
            bool playing = mCtrl.Dsp.IsPlaying;
            PresetItem p = playing ? mCtrl.Dsp.CurrentPreset : PresetCatalog.FindById(mCtrl.Presets, mCtrl.State.LastPresetId);
            mLed.Fill = playing ? ThemePalette.BrActive : ThemePalette.BrTextTertiary;
            mTitle.Text = p != null ? p.Title : "보르타 이지스";

            string hz = playing
                ? string.Format(CultureInfo.InvariantCulture, "{0:F1} Hz", mCtrl.Dsp.LiveFreqHz > 0f ? mCtrl.Dsp.LiveFreqHz : (p != null ? p.Freq1 : 0f))
                : "대기 중";
            string vol = mCtrl.Dsp.IsMuted
                ? "음소거"
                : (mCtrl.Dsp.VolumePercent > VolumeCurve.UNITY_PERCENT
                    ? string.Format(CultureInfo.InvariantCulture, "{0}% 증폭", mCtrl.Dsp.VolumePercent)
                    : string.Format(CultureInfo.InvariantCulture, "{0}%", mCtrl.Dsp.VolumePercent));
            int rem = mCtrl.SleepTimerRemainingSeconds;
            mFreqVol.Text = rem > 0
                ? string.Format(CultureInfo.InvariantCulture, "{0} · {1} · {2:D2}:{3:D2}", hz, vol, rem / 60, rem % 60)
                : (hz + " · " + vol);

            mSuppress = true;
            mVolSlider.Value = mCtrl.Dsp.IsMuted ? 0 : mCtrl.Dsp.VolumePercent;
            mSuppress = false;

            mMuteWave.Visibility = mCtrl.Dsp.IsMuted ? Visibility.Collapsed : Visibility.Visible;
            mMuteCross.Visibility = mCtrl.Dsp.IsMuted ? Visibility.Visible : Visibility.Collapsed;
            mMuteBtn.BorderBrush = mCtrl.Dsp.IsMuted ? ThemePalette.BrStopped : ThemePalette.BrOutline;

            if (playing)
            {
                mToggleIcon.Data = Geometry.Parse("M 3,3 L 10,3 L 10,10 L 3,10 Z");
                mToggleIcon.Fill = ThemePalette.BrTextPrimary;
                mToggleBtn.Background = ThemePalette.BrStopBtn;
                mToggleBtn.BorderBrush = ThemePalette.BrStopped;
            }
            else
            {
                mToggleIcon.Data = Geometry.Parse("M 3,2 L 10,6.5 L 3,11 Z");
                mToggleIcon.Fill = ThemePalette.BrAmber;
                mToggleBtn.Background = ThemePalette.BrAmberBg;
                mToggleBtn.BorderBrush = ThemePalette.BrAmber;
            }
        }

        protected override void OnClosing(CancelEventArgs e)
        {
            if (!mCtrl.AllowExit)
            {
                e.Cancel = true;
                mCtrl.HideToTray();
                return;
            }
            mTimer.Stop();
            base.OnClosing(e);
        }
    }
}
