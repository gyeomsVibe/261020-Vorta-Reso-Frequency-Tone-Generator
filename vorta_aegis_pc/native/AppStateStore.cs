using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Web.Script.Serialization;

namespace VortaAegisPC
{
    public sealed class AppStateData
    {
        public int VolumePercent = VolumeCurve.DEFAULT_PERCENT;
        public bool IsMuted = false;
        public int LastTab = 0;
        public int LastPresetId = 1;
        public int OutputProfile = (int)PcOutputProfile.PcSpeaker;
        public string DeviceId = "";
        public bool UltrasonicGuard = true;
        public bool MiniMode = false;
        public bool Topmost = false;
        public double WindowLeft = double.NaN;
        public double WindowTop = double.NaN;
        public double WindowWidth = 920;
        public double WindowHeight = 680;
        public bool WindowMaximized = false;
        public double MiniLeft = double.NaN;
        public double MiniTop = double.NaN;
        public double MiniWidth = 436;
        public List<int> FavoriteIds = new List<int>();
    }

    public static class AppStateStore
    {
        private static readonly object sIoLock = new object();
        private const double UNSET_POS_SENTINEL = -99999.0;

        public static string GetStateDirectory()
        {
            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            return Path.Combine(localAppData, "VortaAegis");
        }

        public static string GetStatePath()
        {
            return Path.Combine(GetStateDirectory(), "state_v3.json");
        }

        public static AppStateData Load()
        {
            AppStateData data = new AppStateData();
            try
            {
                string path = GetStatePath();
                if (!File.Exists(path)) return data;
                string raw = File.ReadAllText(path, Encoding.UTF8);
                JavaScriptSerializer ser = new JavaScriptSerializer();
                AppStateData parsed = ser.Deserialize<AppStateData>(raw);
                if (parsed != null)
                {
                    data = parsed;
                }
            }
            catch
            {
                // Corrupt file falls back safely to defaults
            }
            Sanitize(data);
            return data;
        }

        public static void Sanitize(AppStateData data)
        {
            if (data == null) return;
            data.VolumePercent = VolumeCurve.ClampPercent(data.VolumePercent);
            data.LastTab = TabSyncPolicy.ClampTabIndex(data.LastTab);
            if (data.LastPresetId < 1 || data.LastPresetId > 48) data.LastPresetId = 1;
            if (data.OutputProfile < 0 || data.OutputProfile > 2) data.OutputProfile = (int)PcOutputProfile.PcSpeaker;
            if (data.DeviceId == null) data.DeviceId = "";

            if (double.IsNaN(data.WindowLeft) || double.IsInfinity(data.WindowLeft) || data.WindowLeft <= -90000.0)
                data.WindowLeft = double.NaN;
            if (double.IsNaN(data.WindowTop) || double.IsInfinity(data.WindowTop) || data.WindowTop <= -90000.0)
                data.WindowTop = double.NaN;
            if (double.IsNaN(data.MiniLeft) || double.IsInfinity(data.MiniLeft) || data.MiniLeft <= -90000.0)
                data.MiniLeft = double.NaN;
            if (double.IsNaN(data.MiniTop) || double.IsInfinity(data.MiniTop) || data.MiniTop <= -90000.0)
                data.MiniTop = double.NaN;

            if (double.IsNaN(data.WindowWidth) || double.IsInfinity(data.WindowWidth) || data.WindowWidth <= 0.0)
                data.WindowWidth = 920.0;
            else if (data.WindowWidth < 400.0) data.WindowWidth = 400.0;
            else if (data.WindowWidth > 3840.0) data.WindowWidth = 3840.0;

            if (double.IsNaN(data.WindowHeight) || double.IsInfinity(data.WindowHeight) || data.WindowHeight <= 0.0)
                data.WindowHeight = 680.0;
            else if (data.WindowHeight < 540.0) data.WindowHeight = 540.0;
            else if (data.WindowHeight > 2160.0) data.WindowHeight = 2160.0;

            if (double.IsNaN(data.MiniWidth) || double.IsInfinity(data.MiniWidth) || data.MiniWidth <= 0.0)
                data.MiniWidth = 436.0;
            else if (data.MiniWidth < 340.0) data.MiniWidth = 340.0;
            else if (data.MiniWidth > 720.0) data.MiniWidth = 720.0;

            List<int> cleanFav = new List<int>();
            if (data.FavoriteIds != null)
            {
                for (int i = 0; i < data.FavoriteIds.Count; i++)
                {
                    int id = data.FavoriteIds[i];
                    if (id >= 1 && id <= 48 && !cleanFav.Contains(id))
                    {
                        cleanFav.Add(id);
                    }
                }
            }
            data.FavoriteIds = cleanFav;
        }

        private static double EncodePos(double v)
        {
            return (double.IsNaN(v) || double.IsInfinity(v)) ? UNSET_POS_SENTINEL : v;
        }

        public static void Save(AppStateData data)
        {
            if (data == null) return;
            Sanitize(data);
            lock (sIoLock)
            {
                try
                {
                    AppStateData dto = new AppStateData();
                    dto.VolumePercent = data.VolumePercent;
                    dto.IsMuted = data.IsMuted;
                    dto.LastTab = data.LastTab;
                    dto.LastPresetId = data.LastPresetId;
                    dto.OutputProfile = data.OutputProfile;
                    dto.DeviceId = data.DeviceId;
                    dto.UltrasonicGuard = data.UltrasonicGuard;
                    dto.MiniMode = data.MiniMode;
                    dto.Topmost = data.Topmost;
                    dto.WindowLeft = EncodePos(data.WindowLeft);
                    dto.WindowTop = EncodePos(data.WindowTop);
                    dto.WindowWidth = data.WindowWidth;
                    dto.WindowHeight = data.WindowHeight;
                    dto.WindowMaximized = data.WindowMaximized;
                    dto.MiniLeft = EncodePos(data.MiniLeft);
                    dto.MiniTop = EncodePos(data.MiniTop);
                    dto.MiniWidth = data.MiniWidth;
                    dto.FavoriteIds = new List<int>(data.FavoriteIds);

                    string dir = GetStateDirectory();
                    if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);
                    string path = GetStatePath();
                    string tmp = path + ".tmp";
                    JavaScriptSerializer ser = new JavaScriptSerializer();
                    string json = ser.Serialize(dto);
                    File.WriteAllText(tmp, json, Encoding.UTF8);
                    if (File.Exists(path))
                    {
                        File.Copy(tmp, path, true);
                        File.Delete(tmp);
                    }
                    else
                    {
                        File.Move(tmp, path);
                    }
                }
                catch
                {
                    // Non-fatal persistence failure
                }
            }
        }
    }
}
