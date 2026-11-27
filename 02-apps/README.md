# 02-apps — 보르타 7대 현역 앱 소스코드 디렉터리

보르타(Vorta) 프로젝트의 대상 플랫폼 및 기기별 실제 소스코드입니다.

| 폴더명 | 대상 플랫폼 / 기기 | 설명 | 빌드 / 실행 가이드 |
|---|---|---|---|
| **`pc-windows-vorta-aegis/`** | Windows 10 / 11 PC (x64) | **보르타 이지스 PC v3.4.0**: WASAPI 48kHz, **88종 여정 시나리오**, 4대 원탭 코스, 앰비언트 블렌더, `[쉬운 가이드]`, `정보→파동→볼륨` 스튜디오 | `python install_pc.py` (Windows .NET 4.8 기본 탑재) |
| **`android-main-vorta-aegis/`** | Android 메인폰 (Note 20 Ultra 등) | **보르타 이지스 v3.4.6 (`Build 14`)**: 88종 8탭 여정·4대 자동 코스, 기본 간결 모드(`12밴드 VU + 5버튼 볼륨`) vs 스튜디오 모드 차별화, 단일 통합 볼륨, `[설명]`/`[가이드]` | `python build_aegis.py` · [README](android-main-vorta-aegis/README.md) |
| **`android-synergy-vorta-4ch/`** | **4기기 전용 (S7·S6·트랜스A30·본카운터A30)** | **보르타 시너지 v2.0.0 (`Build 200`)**: `S6` 동적 `4Hz` 맥놀이·`0.5~200Hz` 무제한 자유 주파수·반수동 IDLE `[재생]` 수정·기본 풀파워 + 4채널(`CH1~CH4`) 시너지 | `python build_synergy.py` · [README](android-synergy-vorta-4ch/README.md) |
| **`android-unified-vorta-reso/`** | Galaxy A30 · S7 · S6 공용 | **보르타 통합앱 v5.2.1**: A30 `볼륨 xx/15단` 실시간 표시, S7/S6 무점프 인앱 저장 배너, 기존 글씨 크기 100% 보존 | `python build_unified.py` · [README](android-unified-vorta-reso/README.md) |
| **`android-freqgen-standalone/`** | Android 공용 | **주파수 발생기 독립앱**: 스윕·바이노럴·복합 변조 독립 발생기 | Android Studio 또는 Gradle |
| **`android-dedicated-devices/galaxy-a30-smato/`** | Galaxy A30 (SM-A305N) | 스마토 대형 우퍼 #1 전용 앱 소스 | `build_a30.py` |
| **`android-dedicated-devices/galaxy-s7-smato/`** | Galaxy S7 (SM-G930K) | 스마토 대형 우퍼 #2 전용 앱 소스 | `build_s7.py` |
| **`android-dedicated-devices/galaxy-s6-lepy/`** | Galaxy S6 (SM-G920K) | Lepy LP-168S 외장 앰프 전용 앱 소스 | `build_s6.py` |
| **`legacy/android-trans2-prototype/`** | Galaxy S6 (트랜스2 실험) | 초기 트랜스2 하드웨어 연동 프로토타입 (아카이브 보관용) | [README](legacy/android-trans2-prototype/README.md) 참조 |
