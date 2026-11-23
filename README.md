# Vorta Reso & Aegis Suite — 보르타 주파수 통합 운용·방어 시스템

전용 Android 기기(Galaxy S6·S7·A30) 및 메인 단말(Note 20 Ultra)·Windows PC 환경에서 저주파·고주파 공명, 우퍼 보호 DSP, 바이노럴·안티위상 시나리오를 운용하기 위한 보르타 프로젝트 전체 소스, 일괄 환경설정 스크립트, 연구 문서 모음입니다.

> **브랜드 의미**
> - **Vorta (보르타)**: Vortex(파동 에너지) + Volta(정밀 주파수 전위)의 합성어로, 흩어진 환경음과 작업 리듬을 정밀한 음향 파동으로 정렬하는 독자 브랜드입니다.
> - **Aegis (이지스)**: 수호 방패를 뜻하며, 동영상·작업·AI 툴과 충돌 없이 곁을 지키는 초경량 백그라운드 음향 방패입니다.

---

## 1. 구성 프로젝트 (7종 앱 & 도구)

| 폴더 | 대상 / 플랫폼 | 핵심 기능 및 상태 |
|---|---|---|
| `vorta_unified_app/` | Galaxy A30·S7·S6 공용 (`v5.2`) | **Pro 순수 변조(FM/AM/RM) 3채널 + 168+12 시나리오·실시간 4096 링버퍼 파동 스코프** (`s6_api24` / `s7_api26` / `a30_api30`) |
| `vorta_aegis_pc/` | **Windows 10/11 PC (`v3.2.0`)** | **네이티브 x64 WASAPI · 코어 48 + PC Pro 팩 16(64종)·이중링/히스테리시스 오실로스코프·좌우 `GridSplitter`·120% 무클리핑 증폭·업데이트/라이선스 허브** |
| `vorta_aegis_app/` | Android 메인폰 (Note 20 Ultra 등 `v2.3`) | **48종 안티방어·수면·집중·명상·모기퇴치 프리셋·음악 동시재생(Duck/Blend)·독립 볼륨** |
| `vorta_freqgen_app/` | 공용 다기능 정밀 발생기 | 스윕·바이노럴·복합 변조 독립 발생기 |
| `smato_a30_app/` | Galaxy A30 (`SM-A305N`) | 스마토 우퍼 #1 전용 앱 (`build_a30.py`) |
| `vorta_s7_app/` | Galaxy S7 (`SM-G930K`) | 스마토 우퍼 #2 전용 앱 (`build_s7.py`) |
| `vorta_s6_app/` | Galaxy S6 (`SM-G920K`) | Lepy LP-168S / 트랜스2 전용 앱 (`build_s6.py`) |

---

## 2. 빠른 시작 (빌드 및 설치)

> 바이너리(`.apk`, `.exe`)와 서명 키(`.keystore`)는 보안·용량 정책상 저장소에 포함되지 않습니다.

### A. Windows PC 네이티브 앱 (`vorta_aegis_pc/` v3.2.0 PC)
Windows 10/11(`.NET Framework 4.8` 기본 내장) + Python 3 환경에서 별도 외부 SDK 없이 1초 만에 네이티브 x64 실행 파일을 빌드하고 바탕화면·시작메뉴 바로가기를 생성합니다.
```powershell
python vorta_aegis_pc/install_pc.py
```
- **창 닫기(`×`)**: 재생을 멈추지 않고 트레이 백그라운드(`~5.3 MB`)로 전환
- **모듈형 시나리오 팩**: `web/presets.json`(코어 48종) + `packs/*.json`(PC Pro 16종) + `%LOCALAPPDATA%\VortaAegis\packs\*.json` 핫로드
- **PC 음향 보정 & 스코프**: `PC 스피커 보정`(144Hz 반송/배음) · `이어폰·헤드폰`(`-12dB` 초음파 가드) · `순수 원음·DAC` · `기본 파동 | 실출력 보기` 전환
- **청정 증폭**: `100% (0.0 dB)` ~ `120% (+1.6 dB)` `-0.50 dBTP` 선형 헤드룸 (`OvershootCount == 0`, `THD+N < 0.1%`)

### B. Android 통합앱 / 전용 기기 일괄 환경설정
1. 폰 **개발자 옵션 → USB 디버깅** 활성화 후 PC 연결
2. 원클릭 배치 실행:
   - 통합앱 설치: `보르타_통합앱_OS버전별_설치_원클릭.bat`
   - 전용 기기 최적화 + 앱 설치: `A30_S7_S6_보르타_레조_환경설정_원클릭.bat` 또는 `A30_S7_S6_주파수발생기_전수개조_원클릭.bat`

---

## 3. 문서·시나리오·라이선스 안내

| 경로 | 내용 |
|---|---|
| `docs/보르타_이지스_비판감사_및_비AI_상용화_디자인_설계서.md` | 보르타 이지스(Android v2.3 & PC v3.2) 디자인·DSP·스코프 설계 정본 |
| `docs/보르타_상용화_라이선스_및_정식인증_가이드.md` | **저작권·상표(CROS/KIPRIS)·Windows 코드 서명·결제/정품키 취득 가이드** |
| `docs/보르타_레조_앱_UIUX_음향제어_영구불변원칙.md` | 보르타 레조 계열 영구 불변 음향·안전 원칙 |
| `docs/Result/`, `docs/작업기록/` | 통합 운용 체계 마스터 보고서 및 기기별 작업 로그 |
| `시나리오/`, `구버전 시나리오/` | 시간대·상황·전술별 주파수 시나리오 보고서 일체 |
| `.coord/PLAN.md`, `.coord/results/` | UAOS 프로젝트 진행 계획 및 검증 카드 결과서 |

---

## 4. 주의사항

- 이 소프트웨어 모음은 **의료기기가 아닙니다.** 질병의 진단·치료·예방 효과를 주장하지 않습니다.
- 우퍼·앰프 장시간 구동 시 발열, 전원, 유선 연결 상태를 주기적으로 점검하세요.

---

## 5. 라이선스 및 권리 고지

**All Rights Reserved (모든 권리 보유).** 오픈소스가 아닙니다. 비영리 개인 평가 열람 외 저작권자의 명시적 서면 허락 또는 상용 라이선스 없는 복제·수정·배포·상업적 이용을 금합니다. 외부 GPL/LGPL 의존성은 **0건**입니다.  
세부 조항: [LICENSE](LICENSE) · [NOTICE](NOTICE) · [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) · [SECURITY.md](SECURITY.md)

문의: yoongyeomkim@naver.com · GitHub [@gyeomsVibe](https://github.com/gyeomsVibe)
