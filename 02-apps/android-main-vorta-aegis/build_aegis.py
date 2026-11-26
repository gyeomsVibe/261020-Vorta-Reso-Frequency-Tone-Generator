import os
import shutil
import subprocess
import sys
import zipfile

APP_DIR = os.path.abspath(os.path.dirname(__file__))
BUILD_DIR = os.path.join(APP_DIR, "build")
GEN_DIR = os.path.join(BUILD_DIR, "gen")
CLASSES_DIR = os.path.join(BUILD_DIR, "classes")

SDK_DIR = (
    os.environ.get("ANDROID_HOME")
    or os.environ.get("ANDROID_SDK_ROOT")
    or os.path.join(os.environ.get("LOCALAPPDATA", os.path.expanduser("~")), "Android", "Sdk")
)
BUILD_TOOLS_DIR = os.path.join(SDK_DIR, r"build-tools\35.0.0")
AAPT2 = os.path.join(BUILD_TOOLS_DIR, "aapt2.exe")
D8 = os.path.join(BUILD_TOOLS_DIR, "d8.bat")
ZIPALIGN = os.path.join(BUILD_TOOLS_DIR, "zipalign.exe")
APKSIGNER = os.path.join(BUILD_TOOLS_DIR, "apksigner.bat")
ANDROID_JAR = os.path.join(SDK_DIR, r"platforms\android-36\android.jar")

KEYSTORE = os.path.join(os.path.dirname(APP_DIR), "smato_a30_app", "debug.keystore")
if not os.path.exists(KEYSTORE):
    KEYSTORE = os.path.join(os.path.expanduser("~"), ".android", "debug.keystore")

JAVA_HOME = r"C:\Program Files\Android\Android Studio\jbr"
if not os.path.isdir(JAVA_HOME):
    JAVA_HOME = os.environ.get("JAVA_HOME", "")
JAVAC = os.path.join(JAVA_HOME, "bin", "javac.exe") if JAVA_HOME else "javac"

custom_env = os.environ.copy()
custom_env["JAVA_HOME"] = JAVA_HOME
custom_env["PATH"] = os.path.join(JAVA_HOME, "bin") + os.path.pathsep + custom_env.get("PATH", "")


def run_cmd(cmd, desc):
    print(f"[{desc}] Running: {' '.join(cmd) if isinstance(cmd, list) else cmd}")
    res = subprocess.run(cmd, cwd=APP_DIR, shell=(True if isinstance(cmd, str) else False),
                         env=custom_env, capture_output=True, text=True, errors="replace")
    if res.returncode != 0:
        print(f"FAILED: {desc}\nSTDOUT: {res.stdout}\nSTDERR: {res.stderr}")
        sys.exit(res.returncode)
    if res.stdout.strip():
        print(res.stdout.strip())
    return res.stdout


def read_manifest_version():
    manifest_path = os.path.join(APP_DIR, "AndroidManifest.xml")
    with open(manifest_path, "r", encoding="utf-8") as f:
        text = f.read()
    import re
    m = re.search(r'android:versionName="([^"]+)"', text)
    return m.group(1) if m else "unknown"


def main():
    version_name = read_manifest_version()
    print("==================================================")
    print(f"Building Vorta Aegis Pro v{version_name} APK (API 21..33)")
    print("==================================================")

    print("[1/8] Cleaning and preparing build directory...")
    if os.path.exists(BUILD_DIR):
        shutil.rmtree(BUILD_DIR)
    os.makedirs(GEN_DIR, exist_ok=True)
    os.makedirs(CLASSES_DIR, exist_ok=True)

    print("[2/8] Compiling resources with AAPT2...")
    flata = os.path.join(BUILD_DIR, "compiled_res.flata")
    run_cmd([AAPT2, "compile", "--dir", "res", "-o", flata], "AAPT2 Compile")

    print("[3/8] Linking APK and generating R.java...")
    base_unaligned = os.path.join(BUILD_DIR, "base_unaligned.apk")
    manifest = os.path.join(APP_DIR, "AndroidManifest.xml")
    run_cmd([AAPT2, "link", "-I", ANDROID_JAR, "--manifest", manifest, "-o", base_unaligned,
             "--java", GEN_DIR, "--auto-add-overlay", flata], "AAPT2 Link")

    print("[4/8] Compiling Java source files with javac...")
    src_files = []
    src_root = os.path.join(APP_DIR, "src", "com", "vorta", "aegis")
    for fn in sorted(os.listdir(src_root)):
        if fn.endswith(".java"):
            src_files.append(os.path.join(src_root, fn))
    src_files.append(os.path.join(GEN_DIR, "com", "vorta", "aegis", "R.java"))

    run_cmd([JAVAC, "-encoding", "UTF-8", "-source", "1.8", "-target", "1.8",
             "-cp", ANDROID_JAR, "-d", CLASSES_DIR] + src_files, "Javac Compilation")

    print("[5/8] Converting bytecode to classes.dex with d8...")
    class_files = []
    for root, _, files in os.walk(CLASSES_DIR):
        for f in files:
            if f.endswith(".class"):
                class_files.append(os.path.join(root, f))

    jar_file = os.path.join(BUILD_DIR, "classes_input.jar")
    with zipfile.ZipFile(jar_file, 'w') as zf:
        for cf in class_files:
            rel = os.path.relpath(cf, CLASSES_DIR)
            zf.write(cf, rel)

    run_cmd([D8, "--min-api", "21", "--output", BUILD_DIR, jar_file], "D8 dex conversion")

    print("[6/8] Packaging classes.dex into base APK...")
    classes_dex = os.path.join(BUILD_DIR, "classes.dex")
    with zipfile.ZipFile(base_unaligned, 'a') as zf:
        zf.write(classes_dex, "classes.dex")

    print("[7/8] Zipaligning and signing APK...")
    aligned_apk = os.path.join(BUILD_DIR, "aligned.apk")
    run_cmd([ZIPALIGN, "-f", "4", base_unaligned, aligned_apk], "zipalign")

    signed_apk = os.path.join(BUILD_DIR, "vorta_aegis_pro.apk")
    run_cmd([APKSIGNER, "sign", "--ks", KEYSTORE, "--ks-pass", "pass:android",
             "--out", signed_apk, aligned_apk], "apksigner sign")

    run_cmd([APKSIGNER, "verify", signed_apk], "apksigner verify")

    final_apk = os.path.join(APP_DIR, "vorta_aegis_pro.apk")
    shutil.copyfile(signed_apk, final_apk)
    print("==================================================")
    print(f"[SUCCESS] Built and signed: {final_apk}")
    print(f"File Size: {os.path.getsize(final_apk):,} bytes")
    print("==================================================")


if __name__ == "__main__":
    main()
