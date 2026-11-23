# Security & Distribution Policy

## Official Distribution Channels
Official releases and source manifests are published exclusively under:
- Repository: `https://github.com/gyeomsVibe/261020-Vorta-Reso--Frequency-Tone-Generator-`
- Live Version Manifest: `vorta_aegis_pc/version.json`
- Contact: `yoongyeomkim@naver.com`

## Telemetry & Privacy
- `Vorta Aegis Pro PC` performs **zero background telemetry**, captures **zero microphone audio**, and makes **no automatic outbound connections**.
- Outbound HTTPS traffic (`GET`) occurs **only** when the user explicitly clicks **"업데이트 확인"** inside the Information / About window (`UpdateChecker.cs`, querying GitHub manifest/releases over TLS 1.2).

## Reporting Security Issues
Please report vulnerabilities or unauthorized binary redistribution directly to `yoongyeomkim@naver.com` rather than opening public exploit issues.
