# -*- coding: utf-8 -*-
"""
A30 / S7 / S6 공통 불필요 앱 전수 동결 및 주파수 전용기기화 최적화 스크립트
(이메일, SmartThings, 삼성페이, 인터넷 브라우저, 미디어, 음성인식 등 전수 포함)
"""
import subprocess
import sys

DEBLOAT_PACKAGES = [
    # 1. 사용자가 특별 지시한 항목
    "com.samsung.android.email.provider",      # 삼성 이메일
    "com.google.android.gm",                   # 지메일(Gmail)
    "com.samsung.android.easysetup",           # SmartThings 간편설정
    "com.samsung.android.beaconmanager",       # SmartThings 비콘매니저
    "com.samsung.android.ststub",              # SmartThings 스텁
    "com.samsung.android.oneconnect",          # SmartThings 원커넥트
    "com.samsung.android.allshare.service.fileshare",  # 파일공유
    "com.samsung.android.allshare.service.mediashare", # 미디어공유
    "com.samsung.android.spay",                # 삼성페이
    "com.samsung.android.spayfw",              # 삼성페이 프레임워크
    "com.sec.android.app.sbrowser",            # 삼성 인터넷 브라우저
    "com.android.chrome",                      # 구글 크롬 브라우저
    "com.sec.android.app.chromecustomizations",# 크롬 커스터마이징
    
    # 2. 미디어 / 동영상 / 음악 / 사진 (주파수 재생과 충돌 방지)
    "com.google.android.youtube",              # 유튜브
    "com.google.android.music",                # 구글 뮤직
    "com.google.android.videos",               # 구글 플레이 무비
    "com.google.android.apps.photos",          # 구글 포토
    "com.samsung.android.video",               # 삼성 비디오
    "com.sec.android.gallery3d",               # 삼성 갤러리
    
    # 3. 음성 비서 / 생체인식 / 클라우드 동기화 (백그라운드 리소스 소모)
    "com.samsung.android.samsungpass",         # 삼성 패스
    "com.samsung.android.samsungpassautofill", # 삼성 패스 자동완성
    "com.samsung.android.svoice",              # S보이스
    "com.samsung.svoice.sync",                 # S보이스 동기화
    "com.sec.svoice.lang.ko_KR",               # S보이스 한국어
    "com.sec.svoice.lang.en_US",               # S보이스 영어
    "com.samsung.android.voicewakeup",         # 보이스 웨이크업
    "com.samsung.android.scloud",              # 삼성 클라우드
    "com.samsung.android.fmm",                 # 내 디바이스 찾기 (배터리 소모)
    "com.samsung.android.authfw",              # 인증 프레임워크
    
    # 4. 게임 / 뉴스 / 날씨 / 타사 주파수 앱
    "com.samsung.android.game.gamehome",       # 게임홈
    "com.enhance.gameservice",                 # 게임서비스
    "flipboard.boxer.app",                     # 브리핑 / 플립보드 뉴스
    "com.sec.android.service.health",          # 삼성 헬스
    "com.sec.android.daemonapp",               # 날씨 데몬
    "com.google.android.googlequicksearchbox", # 구글 검색
    "com.google.android.talk",                 # 구글 행아웃/토크
    "com.boedec.hoel.frequencygenerator",      # 타사 주파수발생기 앱 (중복 방지)
    
    # 5. 통신사 / 전화 / 문자 / DMB / 라디오 (영구불변원칙 8항, 9-8항)
    "com.skt.prod.dialer",                     # SKT T전화 / 에이닷
    "com.skt.t_smart_charge",                  # T스마트충전 (배터리 발열 유발)
    "com.samsung.android.app.contacts",        # 삼성 연락처
    "com.samsung.android.contacts",            # 연락처 프로바이더
    "com.sec.phone",                           # 통화
    "com.android.phone",                       # 폰 서비스
    "com.samsung.android.dialer",              # 다이얼러
    "com.samsung.android.incallui",            # 통화화면 UI
    "com.samsung.android.messaging",           # 메시지
    "com.samsung.android.app.dtv.dmb",         # DMB
    "com.sec.android.app.fm",                  # FM 라디오
    "com.sec.android.app.popupcalculator",      # 계산기
    "com.sec.android.app.soundalive",          # SoundAlive 음향왜곡 (DRC 유발)
    "com.sec.hearingadjust",                   # HearingAdjust
    "Com.sktelecom.minit",                     # SKT 모바일 Tworld
    "com.skt.skaf.OA00018282",                 # SKT 서비스 1
    "com.skt.skaf.OA00199800",                 # SKT 서비스 2
    "com.skt.skaf.OA00412131",                 # SKT 서비스 3
    "com.skt.skaf.A000Z00040",                 # SKT 서비스 4
    "com.sktelecom.smartcard.SmartcardService",# 스마트카드
    "com.kt.olleh.customercare",               # KT 마이KT
    "com.kt.show.cs",                          # KT 고객센터
    "com.kt.service.cs",                       # KT 서비스
    "uplus.membership",                        # LGU+ 멤버십
    "com.lguplus.mobile.cs",                   # LGU+ 고객센터
    "com.lguplus.smartpay"                     # LGU+ 스마트페이
]

def run_debloat(device_id=None):
    adb = r"C:\Users\Kimyoongyeom\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    base_cmd = [adb]
    if device_id:
        base_cmd.extend(["-s", device_id])
    
    print(f"[*] 디블로트 대상 기기: {device_id if device_id else 'Default'}")
    
    success_count = 0
    for pkg in DEBLOAT_PACKAGES:
        # 1. 0번 사용자 언인스톨
        subprocess.run(base_cmd + ["shell", "pm", "uninstall", "-k", "--user", "0", pkg],
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        # 2. 비활성화 (disabled-user)
        r = subprocess.run(base_cmd + ["shell", "pm", "disable-user", "--user", "0", pkg],
                           capture_output=True, text=True)
        if "disabled-user" in r.stdout or "new state" in r.stdout:
            success_count += 1
            print(f"  [+] 동결 성공: {pkg}")
    
    print(f"[*] 총 {success_count}개 앱 동결 완료.")

if __name__ == "__main__":
    dev = sys.argv[1] if len(sys.argv) > 1 else None
    run_debloat(dev)
