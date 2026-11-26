import os
import shutil
import subprocess
import sys
import zipfile

APP_DIR = os.path.abspath(os.path.dirname(__file__))
SDK_DIR = r"C:\Users\Kimyoongyeom\AppData\Local\Android\Sdk"
BUILD_TOOLS_DIR = os.path.join(SDK_DIR, r"build-tools\35.0.0")
AAPT2 = os.path.join(BUILD_TOOLS_DIR, "aapt2.exe")
D8 = os.path.join(BUILD_TOOLS_DIR, "d8.bat")
ZIPALIGN = os.path.join(BUILD_TOOLS_DIR, "zipalign.exe")
APKSIGNER = os.path.join(BUILD_TOOLS_DIR, "apksigner.bat")
ANDROID_JAR = os.path.join(SDK_DIR, r"platforms\android-36\android.jar")
KEYSTORE = os.path.join(os.path.dirname(APP_DIR), "smato_a30_app", "debug.keystore")
if not os.path.exists(KEYSTORE):
    KEYSTORE = r"C:\Users\Kimyoongyeom\.android\debug.keystore"
JAVA_HOME = r"C:\Program Files\Android\Android Studio\jbr"
JAVAC = os.path.join(JAVA_HOME, "bin", "javac.exe")

custom_env = os.environ.copy()
custom_env["JAVA_HOME"] = JAVA_HOME
custom_env["PATH"] = os.path.join(JAVA_HOME, "bin") + os.path.pathsep + custom_env.get("PATH", "")

FLAVORS = [
    ("s6_api24", "21", "vorta_unified_s6_api24.apk"),
    ("s7_api26", "24", "vorta_unified_s7_api26.apk"),
    ("a30_api30", "26", "vorta_unified_a30_api30.apk"),
]


def run_cmd(cmd, desc, cwd=APP_DIR):
    print(f"[{desc}] Running...")
    res = subprocess.run(cmd, cwd=cwd, env=custom_env, capture_output=True, text=True, errors="replace")
    if res.returncode != 0:
        print(f"FAILED: {desc}\nSTDOUT: {res.stdout}\nSTDERR: {res.stderr}")
        sys.exit(res.returncode)
    return res.stdout


def build_flavor(flavor_name, min_api, apk_name):
    print(f"\n=== Building Unified Flavor: {flavor_name} (minApi={min_api}) -> {apk_name} ===")
    bdir = os.path.join(APP_DIR, "build", flavor_name)
    gen_dir = os.path.join(bdir, "gen")
    classes_dir = os.path.join(bdir, "classes")
    if os.path.exists(bdir):
        shutil.rmtree(bdir)
    os.makedirs(gen_dir, exist_ok=True)
    os.makedirs(classes_dir, exist_ok=True)

    flata = os.path.join(bdir, "compiled_res.flata")
    run_cmd([AAPT2, "compile", "--dir", "res", "-o", flata], f"{flavor_name} aapt2 compile")

    manifest = os.path.join(APP_DIR, "flavors", flavor_name, "AndroidManifest.xml")
    base_unaligned = os.path.join(bdir, "base_unaligned.apk")
    run_cmd(
        [AAPT2, "link", "-I", ANDROID_JAR, "--manifest", manifest, "-o", base_unaligned, "--java", gen_dir, "--auto-add-overlay", flata],
        f"{flavor_name} aapt2 link",
    )

    src_files = []
    shared_src = os.path.join(APP_DIR, "src", "com", "vorta", "unified")
    for fn in sorted(os.listdir(shared_src)):
        if fn.endswith(".java") and fn != "BuildProfile.java":
            src_files.append(os.path.join(shared_src, fn))
    src_files.append(os.path.join(APP_DIR, "flavors", flavor_name, "BuildProfile.java"))
    src_files.append(os.path.join(gen_dir, "com", "vorta", "unified", "R.java"))

    run_cmd(
        [JAVAC, "-encoding", "UTF-8", "-source", "1.8", "-target", "1.8", "-cp", ANDROID_JAR, "-d", classes_dir] + src_files,
        f"{flavor_name} javac",
    )

    classes_jar = os.path.join(bdir, "classes_input.jar")
    with zipfile.ZipFile(classes_jar, "w", zipfile.ZIP_DEFLATED) as zf:
        for root_dir, _, files in os.walk(classes_dir):
            for f in files:
                if f.endswith(".class"):
                    fp = os.path.join(root_dir, f)
                    zf.write(fp, os.path.relpath(fp, classes_dir))

    run_cmd([D8, "--min-api", min_api, "--output", bdir, classes_jar], f"{flavor_name} d8")
    with zipfile.ZipFile(base_unaligned, "a") as zf:
        zf.write(os.path.join(bdir, "classes.dex"), "classes.dex")

    aligned_apk = os.path.join(bdir, "aligned.apk")
    run_cmd([ZIPALIGN, "-f", "4", base_unaligned, aligned_apk], f"{flavor_name} zipalign")

    out_apk = os.path.join(APP_DIR, apk_name)
    run_cmd(
        [APKSIGNER, "sign", "--ks", KEYSTORE, "--ks-pass", "pass:android", "--out", out_apk, aligned_apk],
        f"{flavor_name} sign",
    )
    run_cmd([APKSIGNER, "verify", out_apk], f"{flavor_name} verify")
    badging = run_cmd([AAPT2, "dump", "badging", out_apk], f"{flavor_name} badging")
    for line in badging.splitlines():
        if line.startswith("package:") or line.startswith("sdkVersion:") or line.startswith("targetSdkVersion:"):
            print("  " + line)
    print(f"[SUCCESS] {out_apk}")


def main():
    for flavor_name, min_api, apk_name in FLAVORS:
        build_flavor(flavor_name, min_api, apk_name)


if __name__ == "__main__":
    main()
