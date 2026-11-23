import os
import re
import subprocess
import sys
import time

ROOT_DIR = os.path.abspath(os.path.dirname(__file__))
WORK_DIR = os.path.join(ROOT_DIR, ".work")
os.makedirs(WORK_DIR, exist_ok=True)

ADB = r"C:\Platform-Tools\adb.exe"
if not os.path.exists(ADB):
    ADB = r"C:\Users\Kimyoongyeom\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if not os.path.exists(ADB):
    ADB = "adb"

PRO_APK = os.path.join(ROOT_DIR, "vorta_freqgen_app", "vorta_freqgen_pro.apk")
FLAVOR_APKS = {
    "S6": os.path.join(ROOT_DIR, "vorta_unified_app", "vorta_unified_s6_api24.apk"),
    "S7": os.path.join(ROOT_DIR, "vorta_unified_app", "vorta_unified_s7_api26.apk"),
    "A30": os.path.join(ROOT_DIR, "vorta_unified_app", "vorta_unified_a30_api30.apk"),
}
SPARE_APKS = {
    "S6": ("com.vorta.s6frequency", os.path.join(ROOT_DIR, "vorta_s6_app", "vorta_s6_pro.apk")),
    "S7": ("com.vorta.s7frequency", os.path.join(ROOT_DIR, "vorta_s7_app", "vorta_s7_pro.apk")),
    "A30": ("com.smato.a30frequency", os.path.join(ROOT_DIR, "smato_a30_app", "smato_a30_pro.apk")),
}


def run_adb(serial, *args, check=False):
    cmd = [ADB] + (["-s", serial] if serial else []) + list(args)
    res = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and res.returncode != 0:
        raise RuntimeError(f"ADB failed ({res.returncode}): {' '.join(cmd)}\n{res.stderr}")
    return res


def classify_device(model):
    up = (model or "").upper()
    if any(k in up for k in ("G920", "G925", "G928", "ZEROFLTE", "ZEROLTE")):
        return "S6"
    if any(k in up for k in ("G930", "G935", "HEROLTE", "HERO2LTE")):
        return "S7"
    if "A30" in up:
        return "A30"
    if "N986" in up or "NOTE20" in up:
        return "PERSONAL_NOTE20U"
    return "PERSONAL_OTHER"


def start_service_compat(serial, sdk_int, component, extra_args=()):
    verb = "start-foreground-service" if sdk_int >= 26 else "startservice"
    return run_adb(serial, "shell", "am", verb, "-n", component, *extra_args)


def capture_snapshot(serial, dev_type, model, sdk):
    safe_model = re.sub(r"[^A-Za-z0-9._-]", "_", model or serial)
    snap_path = os.path.join(WORK_DIR, f"device_snapshot_{safe_model}_{serial}.txt")
    sections = []
    cmds = [
        ("PROPS", ("getprop",)),
        ("SETTINGS_SYSTEM", ("settings", "list", "system")),
        ("SETTINGS_GLOBAL", ("settings", "list", "global")),
        ("SETTINGS_SECURE", ("settings", "list", "secure")),
        ("DUMPSYS_AUDIO", ("dumpsys", "audio")),
        ("DUMPSYS_AUDIO_FLINGER", ("dumpsys", "media.audio_flinger")),
        ("DUMPSYS_POWER", ("dumpsys", "power")),
        ("MIXER_PATHS_HEAD", ("head", "-n", "120", "/system/etc/mixer_paths.xml")),
        ("MIXER_GAINS_HEAD", ("head", "-n", "120", "/vendor/etc/mixer_gains.xml")),
    ]
    for title, args in cmds:
        out = run_adb(serial, "shell", *args).stdout
        if title == "PROPS":
            out = "\n".join(
                ln for ln in out.splitlines()
                if any(k in ln.lower() for k in ("model", "device", "board", "hardware", "bootloader", "version", "csc", "warranty", "audio", "chipname", "abi"))
            )
        sections.append(f"=== {title} ===\n{out.strip()}\n")
    with open(snap_path, "w", encoding="utf-8") as fp:
        fp.write(f"# Snapshot: {serial} | model={model} | sdk={sdk} | role={dev_type}\n\n")
        fp.write("\n".join(sections))
    print(f"  [Snapshot] Saved -> {snap_path}")
    return snap_path


def verify_dedicated_device(serial, dev_type, model, sdk):
    print(f"\n============================================================")
    print(f" HARSH HARDWARE AUDIT: {dev_type} | {model} ({serial}, API {sdk})")
    print(f"============================================================")

    apk_path = FLAVOR_APKS[dev_type]
    if not os.path.exists(apk_path):
        raise FileNotFoundError(f"Missing APK: {apk_path}")

    print("[1/5] Capturing hardware/firmware/PowerManager snapshot...")
    capture_snapshot(serial, dev_type, model, sdk)

    print(f"[2/5] Installing {os.path.basename(apk_path)}, Pro APK, and preserving device Spare APK...")
    res_uni = run_adb(serial, "install", "-r", "-d", apk_path)
    if res_uni.returncode != 0 or "Success" not in res_uni.stdout:
        raise RuntimeError(f"Unified APK install failed: {res_uni.stdout} {res_uni.stderr}")
    if os.path.exists(PRO_APK):
        res_pro = run_adb(serial, "install", "-r", "-d", PRO_APK)
        if res_pro.returncode != 0 or "Success" not in res_pro.stdout:
            raise RuntimeError(f"Pro APK install failed: {res_pro.stdout} {res_pro.stderr}")
    spare_pkg, spare_apk = SPARE_APKS.get(dev_type, (None, None))
    if spare_apk and os.path.exists(spare_apk):
        res_spare = run_adb(serial, "install", "-r", "-d", spare_apk)
        if res_spare.returncode != 0 or "Success" not in res_spare.stdout:
            raise RuntimeError(f"Spare APK ({spare_pkg}) install failed: {res_spare.stdout} {res_spare.stderr}")

    installed_pkgs = {ln.strip() for ln in run_adb(serial, "shell", "pm", "list", "packages").stdout.splitlines() if ln.strip()}
    extra_spares = []
    for other_pkg, other_apk in SPARE_APKS.values():
        if other_pkg != spare_pkg and other_apk and os.path.exists(other_apk) and f"package:{other_pkg}" in installed_pkgs:
            print(f"  [Spare Upgrade] Upgrading pre-installed {other_pkg}...")
            res_other = run_adb(serial, "install", "-r", "-d", other_apk)
            if res_other.returncode != 0 or "Success" not in res_other.stdout:
                raise RuntimeError(f"Pre-installed spare APK ({other_pkg}) upgrade failed: {res_other.stdout} {res_other.stderr}")
            extra_spares.append(other_pkg)

    print("[3/5] Applying OS-aware permissions, Doze exemption, AOT speed compile...")
    pkgs_to_prep = ["com.vorta.unified", "com.vorta.freqgen"]
    if spare_pkg:
        pkgs_to_prep.append(spare_pkg)
    for ep in extra_spares:
        if ep not in pkgs_to_prep:
            pkgs_to_prep.append(ep)
    for pkg in pkgs_to_prep:
        run_adb(serial, "shell", "pm", "grant", pkg, "android.permission.WRITE_SECURE_SETTINGS")
        run_adb(serial, "shell", "cmd", "appops", "set", pkg, "WRITE_SETTINGS", "allow")
        run_adb(serial, "shell", "cmd", "appops", "set", pkg, "RUN_IN_BACKGROUND", "allow")
        run_adb(serial, "shell", "cmd", "appops", "set", pkg, "WAKE_LOCK", "allow")
        run_adb(serial, "shell", "dumpsys", "deviceidle", "whitelist", f"+{pkg}")
        run_adb(serial, "shell", "cmd", "package", "compile", "-m", "speed", "-f", pkg)

    if dev_type in ("S6", "S7"):
        run_adb(serial, "shell", "settings", "put", "secure", "accessibility_enabled", "1")
        run_adb(serial, "shell", "settings", "put", "secure", "enabled_accessibility_services",
                "com.vorta.unified/com.vorta.unified.VortaAccessibilityService")
        run_adb(serial, "shell", "am", "broadcast", "-a", "com.vorta.unified.ACTION_SAFE_VOLUME_CHECK",
                "-p", "com.vorta.unified", "--include-stopped-packages")
        time.sleep(2.0)
    elif dev_type == "A30":
        for ns, key, val in [
            ("system", "master_mono", "0"),
            ("system", "mono_audio", "0"),
            ("system", "sound_alive_effect", "0"),
            ("system", "uhqa_effect", "0"),
            ("system", "tube_amp_effect", "0"),
            ("system", "k2hd_effect", "0"),
            ("system", "concert_hall_effect", "0"),
            ("system", "dolby_atmos_mode", "0"),
            ("global", "dolby_atmos_mode", "0"),
            ("global", "multisound_state", "0"),
        ]:
            run_adb(serial, "shell", "settings", "put", ns, key, val)

    print("[4/5] Running silent harsh audio & memory-trim torture (0.0f digital silence)...")
    run_adb(serial, "logcat", "-c")

    # 5x rapid start/stop churn cycles (tests service lifecycle, thread cleanup, no stale tracks)
    for cyc in range(5):
        start_service_compat(serial, sdk, "com.vorta.unified/.VortaAudioService")
        time.sleep(0.45)
        start_service_compat(serial, sdk, "com.vorta.unified/.VortaAudioService", ("--es", "action", "stop"))
        time.sleep(0.25)

    # Sustained silent play + RUNNING_CRITICAL memory trim + live AudioFlinger check
    start_service_compat(serial, sdk, "com.vorta.unified/.VortaAudioService")
    time.sleep(2.0)
    run_adb(serial, "shell", "am", "send-trim-memory", "com.vorta.unified", "RUNNING_CRITICAL")
    time.sleep(0.8)

    pids_out = run_adb(serial, "shell", "pidof", "com.vorta.unified").stdout.split()
    af_out = run_adb(serial, "shell", "dumpsys", "media.audio_flinger").stdout
    af_has_pkg = "com.vorta.unified" in af_out
    if not af_has_pkg and pids_out:
        af_has_pkg = any(
            re.search(rf"(?:\byes\s+{re.escape(p)}\b|^\s*\d+\s+{re.escape(p)}\s)", af_out, re.M)
            for p in pids_out
        )
    af_48k = "48000 Hz" in af_out or "48000" in af_out
    logs_audio = run_adb(serial, "shell", "logcat", "-d", "-v", "time", "-s", "VortaUnifiedAudio", "VortaAudioAudit").stdout
    float_ok = "ENCODING_PCM_FLOAT" in logs_audio or "ENCODING_PCM_16BIT_TPDF" in logs_audio
    print(f"  AudioFlinger active={af_has_pkg} 48kHz={af_48k} initLogOK={float_ok}")
    for ln in logs_audio.splitlines():
        if "AudioTrack initialized" in ln:
            print("   ", ln.strip())

    start_service_compat(serial, sdk, "com.vorta.unified/.VortaAudioService", ("--es", "action", "stop"))
    time.sleep(0.8)

    # Verify versionCode == 520
    pkg_dump = run_adb(serial, "shell", "dumpsys", "package", "com.vorta.unified").stdout
    ver_ok = "versionCode=520" in pkg_dump
    print(f"  Package versionCode=520 check: {ver_ok}")

    # OS-specific safety & free-volume verification
    if dev_type == "S6":
        print("[5/5] Testing S6 screen-off Direct-Binder safe-volume disarm (3 -> 2)...")
        run_adb(serial, "logcat", "-c")
        pwr_pre = [l.strip() for l in run_adb(serial, "shell", "dumpsys", "power").stdout.splitlines() if "mWakefulness=" in l]
        run_adb(serial, "shell", "settings", "put", "global", "audio_safe_volume_state", "3")
        time.sleep(2.5)
        post_state = run_adb(serial, "shell", "settings", "get", "global", "audio_safe_volume_state").stdout.strip()
        post_ctrl = run_adb(serial, "shell", "settings", "get", "secure", "volume_controller_service_component").stdout.strip()
        pwr_post = [l.strip() for l in run_adb(serial, "shell", "dumpsys", "power").stdout.splitlines() if "mWakefulness=" in l]
        print(f"  S6 Disarm state={post_state} vol_ctrl={post_ctrl} wake_before={pwr_pre} wake_after={pwr_post}")
        if post_state != "2" or post_ctrl != "null":
            raise AssertionError(f"S6 Direct-Binder check failed: state={post_state}, ctrl={post_ctrl}")
    elif dev_type == "S7":
        print("[5/5] Checking S7 Accessibility binding, WRITE_SETTINGS, safe-volume & DSP flags...")
        acc = run_adb(serial, "shell", "dumpsys", "accessibility").stdout
        bound = "com.vorta.unified/.VortaAccessibilityService" in acc
        ws_out = run_adb(serial, "shell", "cmd", "appops", "get", "com.vorta.unified", "WRITE_SETTINGS").stdout.strip()
        ws_allow = "WRITE_SETTINGS: allow" in ws_out
        mono = run_adb(serial, "shell", "settings", "get", "system", "mono_audio").stdout.strip()
        sa = run_adb(serial, "shell", "settings", "get", "system", "sound_alive_effect").stdout.strip()
        uhqa = run_adb(serial, "shell", "settings", "get", "system", "uhqa_effect").stdout.strip()
        tube = run_adb(serial, "shell", "settings", "get", "system", "tube_amp_effect").stdout.strip()
        k2hd = run_adb(serial, "shell", "settings", "get", "system", "k2hd_effect").stdout.strip()
        dolby = run_adb(serial, "shell", "settings", "get", "system", "dolby_atmos_mode").stdout.strip()
        vol_lim = run_adb(serial, "shell", "settings", "get", "system", "volume_limiter").stdout.strip()
        active_ms = run_adb(serial, "shell", "settings", "get", "secure", "unsafe_volume_music_active_ms").stdout.strip()
        safe_st = run_adb(serial, "shell", "settings", "get", "global", "audio_safe_volume_state").stdout.strip()
        print(
            f"  S7 bound={bound} write_settings={ws_allow} safe_vol={safe_st} active_ms={active_ms} "
            f"vol_lim={vol_lim} mono={mono} sound_alive={sa} uhqa={uhqa} tube={tube} k2hd={k2hd} dolby={dolby}"
        )
        ok = (
            bound
            and ws_allow
            and safe_st == "2"
            and active_ms == "0"
            and vol_lim == "0"
            and mono == "0"
            and sa == "0"
            and uhqa == "0"
            and tube == "0"
            and k2hd == "0"
            and dolby in ("0", "null")
        )
        if not ok:
            raise AssertionError(
                f"S7 safety/DSP check failed: bound={bound} ws={ws_out} safe_vol={safe_st} active_ms={active_ms} "
                f"vol_lim={vol_lim} mono={mono} sa={sa} uhqa={uhqa} tube={tube} k2hd={k2hd} dolby={dolby}"
            )
    else:
        print("[5/5] Checking A30 clean state (WRITE_SETTINGS, safe_vol=1, pure stereo & Dolby/DSP=0)...")
        ws_out = run_adb(serial, "shell", "cmd", "appops", "get", "com.vorta.unified", "WRITE_SETTINGS").stdout.strip()
        ws_allow = "WRITE_SETTINGS: allow" in ws_out
        safe_st = run_adb(serial, "shell", "settings", "get", "global", "audio_safe_volume_state").stdout.strip()
        master_mono = run_adb(serial, "shell", "settings", "get", "system", "master_mono").stdout.strip()
        mono = run_adb(serial, "shell", "settings", "get", "system", "mono_audio").stdout.strip()
        sa = run_adb(serial, "shell", "settings", "get", "system", "sound_alive_effect").stdout.strip()
        uhqa = run_adb(serial, "shell", "settings", "get", "system", "uhqa_effect").stdout.strip()
        tube = run_adb(serial, "shell", "settings", "get", "system", "tube_amp_effect").stdout.strip()
        k2hd = run_adb(serial, "shell", "settings", "get", "system", "k2hd_effect").stdout.strip()
        concert = run_adb(serial, "shell", "settings", "get", "system", "concert_hall_effect").stdout.strip()
        dolby = run_adb(serial, "shell", "settings", "get", "system", "dolby_atmos_mode").stdout.strip()
        dolby_global = run_adb(serial, "shell", "settings", "get", "global", "dolby_atmos_mode").stdout.strip()
        multisound = run_adb(serial, "shell", "settings", "get", "global", "multisound_state").stdout.strip()
        print(
            f"  A30 write_settings={ws_allow} safe_vol={safe_st} master_mono={master_mono} mono={mono} "
            f"sound_alive={sa} uhqa={uhqa} tube={tube} k2hd={k2hd} concert={concert} "
            f"dolby_sys={dolby} dolby_global={dolby_global} multisound={multisound}"
        )
        ok = (
            ws_allow
            and safe_st == "1"
            and master_mono == "0"
            and mono == "0"
            and sa == "0"
            and uhqa == "0"
            and tube == "0"
            and k2hd == "0"
            and concert == "0"
            and dolby == "0"
            and dolby_global == "0"
            and multisound == "0"
        )
        if not ok:
            raise AssertionError(
                f"A30 clean-audio check failed: ws={ws_out} safe_st={safe_st} master_mono={master_mono} mono={mono} "
                f"sa={sa} uhqa={uhqa} tube={tube} k2hd={k2hd} concert={concert} "
                f"dolby={dolby} dolby_global={dolby_global} multisound={multisound}"
            )

    crash_out = run_adb(serial, "shell", "logcat", "-d", "-b", "crash").stdout
    crash_hits = [l for l in crash_out.splitlines() if "com.vorta" in l or "FATAL EXCEPTION" in l]
    svcs = run_adb(serial, "shell", "dumpsys", "activity", "services", "com.vorta.unified").stdout
    audio_left = [l.strip() for l in svcs.splitlines() if "VortaAudioService" in l]
    print(f"  Final check: crash_hits={len(crash_hits)} remaining_audio_services={audio_left}")
    if not (af_has_pkg and af_48k and float_ok and ver_ok and len(crash_hits) == 0 and len(audio_left) == 0):
        raise AssertionError(
            f"Verification failed on {dev_type}: af={af_has_pkg} 48k={af_48k} float={float_ok} ver520={ver_ok} crashes={len(crash_hits)} leftover={audio_left}"
        )
    print(f"[PASS] {dev_type} ({serial}) passed ALL harsh hardware gates!")


def main():
    dev_res = subprocess.run([ADB, "devices", "-l"], capture_output=True, text=True, encoding="utf-8", errors="replace")
    print("=== CONNECTED ADB DEVICES ===")
    print(dev_res.stdout.strip())

    serials = []
    for line in dev_res.stdout.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            serials.append(parts[0])

    if not serials:
        print("[ERROR] No ADB device connected in 'device' state.")
        sys.exit(1)

    tested = 0
    for ser in serials:
        model = run_adb(ser, "shell", "getprop", "ro.product.model").stdout.strip()
        sdk_str = run_adb(ser, "shell", "getprop", "ro.build.version.sdk").stdout.strip()
        sdk = int(sdk_str) if sdk_str.isdigit() else 24
        role = classify_device(model)
        if role in ("S6", "S7", "A30"):
            verify_dedicated_device(ser, role, model, sdk)
            tested += 1
        elif role == "PERSONAL_NOTE20U":
            print(f"\n[PERSONAL DEVICE DETECTED: {model} ({ser}, API {sdk})] Snapshot only; skipping unified volume/guard modification (Aegis untouched).")
            capture_snapshot(ser, role, model, sdk)
        else:
            print(f"\n[UNREGISTERED DEVICE SKIPPED: {model} ({ser}, API {sdk})]")

    print(f"\nALL-DEVICE HARSH HARNESS DONE. Dedicated generator devices verified: {tested}")


if __name__ == "__main__":
    main()
