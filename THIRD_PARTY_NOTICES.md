# Third-Party Software & Dependency Notices (`Vorta Aegis Pro PC`)

> **Clean-Room Verification Result**: **0 Third-Party / 0 GPL / 0 LGPL Runtime Dependencies**.

`Vorta Aegis Pro PC` (`VortaAegisPC.exe`) is authored 100% from original source files (`vorta_aegis_pc/native/*.cs`) and compiles exclusively against standard operating system components supplied with Microsoft Windows 10/11:

| Layer | Component | Origin / License | Bundled in Repo? |
|---|---|---|---|
| Runtime & UI | Microsoft .NET Framework 4.8 (`PresentationFramework`, `PresentationCore`, `WindowsBase`, `System.Xaml`, `System.Windows.Forms`, `System.Drawing`, `System.Web.Extensions`) | Microsoft Windows OS Component | No (system-provided) |
| Audio Engine | Windows Core Audio (`WASAPI` COM vtable interfaces: `IMMDeviceEnumerator`, `IMMDevice`, `IAudioClient`, `IAudioRenderClient` in `WasapiAudioEngine.cs`) | Microsoft Windows SDK COM ABI | Original P/Invoke / COM declarations |
| Compiler / Build | `csc.exe` (.NET Framework 4.8 C# compiler) & Python 3 Standard Library (`build_pc_native.py`, `make_icon.py`, `sync_presets.py`, `install_pc.py`) | OS / Python PSF stdlib | Build scripts only |
| Presets & Packs | `web/presets.json`, `packs/*.json`, `assets/vorta_aegis.ico`, `assets/icon.svg` | Original work by `gyeomsVibe (김윤겸)` | Proprietary |

## Why This Matters for Commercialization
Because there are **zero GPL/LGPL libraries** and zero third-party closed SDK blobs embedded inside `VortaAegisPC.exe`, `gyeomsVibe (김윤겸)` holds **100% clean-title commercial copyright** with zero copyleft contagion or third-party royalty obligations.
