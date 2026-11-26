# Vorta Aegis Pro (Android v3.4.4 / Build 12)

- Target: Android 5.0~13 (`minSdk=21`, `targetSdk=33`), Galaxy Note 20 Ultra (`SM-N986N`) 검증 완료
- 핵심 구성: 88종 여정 시나리오(코어 48 + PC Pro 16 + 여정 24), 8탭 `1단계→4단계` 순서, 4대 원탭 자동 코스, 출력 음향 보정 4모드, 스테레오 앰비언트 블렌더, `[설명]`/`[가이드]` 쉬운 용어집, 미니 모드(`정보→단일 볼륨`, 파동 숨김) vs 스튜디오 모드(`정보→자동 파동→단일 볼륨`)
- 빌드: `python build_aegis.py` (JDK + Android SDK `build-tools/35.0.0`)
- 테스트: `javac -encoding UTF-8 ... tests/TestU338Aegis.java && java TestU338Aegis`
