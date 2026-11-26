# Vorta Aegis Pro (Android v3.4.6 / Build 14)

- Target: Android 5.0~13 (`minSdk=21`, `targetSdk=33`), Galaxy Note 20 Ultra (`SM-N986N`) 검증 완료
- 핵심 구성: 88종 여정 시나리오(코어 48 + PC Pro 16 + 여정 24), 8탭 `1단계→4단계` 순서, 4대 원탭 자동 코스, 출력 음향 보정 4모드, 스테레오 앰비언트 블렌더, `[설명]`/`[가이드]` 쉬운 용어집, 기본 간결 모드(`정보+VU→간결 버튼 볼륨→원탭 코스→8탭·카드`, 파동/추천/검색 숨김) vs 스튜디오(`정보→자동 파동→전체 볼륨→추천·검색`), 상단 12단 20fps VU 파동
- 빌드: `python build_aegis.py` (JDK + Android SDK `build-tools/35.0.0`)
- 테스트: `javac -encoding UTF-8 ... tests/TestU338Aegis.java && java TestU338Aegis`
