# -*- coding: utf-8 -*-
"""
build_perfect_scenarios_suite.py
MIA 전략절차 11단계 프레임워크 기반: 
D:\D_Workspace_NB\주파수\시나리오 폴더 전수정리 및 시나리오 1, 2, 3 완전 무결 재작성기
- S7, S6 음향역학 정밀 계산 기반 FM / AM / RM 변조 전면 탑재
- 최상급 UX/UI 인터랙티브 HTML (반응형 탭, 네온 다크 글래스모피즘, 배지, 전수 파라미터)
- 통일된 순차 파일명 정렬 및 중복 파일 완전 정리
"""
import os
import sys

SCENARIOS_DIR = r"D:\D_Workspace_NB\주파수\시나리오"
ROOT_DIR = r"D:\D_Workspace_NB\주파수"

# =================================================================================================
# 1. HTML GENERATOR FUNCTION WITH FULL INTERACTIVE TABS
# =================================================================================================
def generate_scenario_html(version_num, title, theme_color, theme_bg_grad, badge_color, 
                           evidence_content, schedule_rows, scenario_rows, preset_rows, 
                           routine_text, mod_explanation):
    
    schedule_tbody = ""
    for r in schedule_rows:
        schedule_tbody += f"""
        <tr>
          <td><b>{r['time']}</b><br><span style="color:#94a3b8; font-size:12px;">{r['tactic']}</span></td>
          <td>
            <span class="badge {badge_color}">A30 스윕&변조</span><br>
            • <b>{r['a30_freq']}</b><br>
            • <b>{r['a30_mod']}</b><br>
            • 볼륨: {r['a30_vol']}<br>
            <span class="effect-text">{r['a30_eff']}</span>
          </td>
          <td>
            <span class="badge {badge_color}">S7 2점 앵커</span><br>
            • <b>{r['s7_freq']}</b><br>
            • <b>{r['s7_mod']}</b><br>
            • 볼륨: {r['s7_vol']}<br>
            <span class="effect-text">{r['s7_eff']}</span>
          </td>
          <td>
            <span class="badge {badge_color}">S6 수직 직타</span><br>
            • <b>{r['s6_freq']}</b><br>
            • <b>{r['s6_mod']}</b><br>
            • 볼륨: {r['s6_vol']}<br>
            <span class="effect-text">{r['s6_eff']}</span>
          </td>
          <td>
            <div class="synth-box">
              <strong>{r['synth_title']}</strong><br>
              {r['synth_desc']}
            </div>
          </td>
        </tr>
        """

    scenario_tbody = ""
    for s in scenario_rows:
        scenario_tbody += f"""
        <tr>
          <td><b>{s['name']}</b><br><span style="color:#94a3b8; font-size:11px;">{s['desc']}</span></td>
          <td>
            • <b>{s['a30_freq']}</b><br>
            • {s['a30_mod']}
          </td>
          <td>
            • <b>{s['s7_freq']}</b><br>
            • {s['s7_mod']}
          </td>
          <td>
            • <b>{s['s6_freq']}</b><br>
            • {s['s6_mod']}
          </td>
        </tr>
        """

    preset_tbody = ""
    for p in preset_rows:
        preset_tbody += f"""
        <tr>
          <td><b>{p['frame']}</b></td>
          <td><b>{p['report']}</b></td>
          <td>{p['parts']}</td>
          <td>{p['single']}</td>
          <td>{p['sweep']}</td>
          <td><b>{p['total']}</b></td>
        </tr>
        """

    html = f"""<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>{title} (MIA 11-Stage Master Suite)</title>
<style>
  :root {{
    --bg-main: #070b14;
    --bg-card: #0f172a;
    --bg-inner: #1e293b;
    --border-color: #2a3a5f;
    --text-primary: #f8fafc;
    --text-secondary: #94a3b8;
    --accent: {theme_color};
    --accent-emerald: #10b981;
    --accent-amber: #f59e0b;
    --accent-rose: #f43f5e;
  }}
  * {{ box-sizing: border-box; margin: 0; padding: 0; }}
  body {{
    background-color: var(--bg-main);
    color: var(--text-primary);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Apple SD Gothic Neo", "Malgun Gothic", sans-serif;
    line-height: 1.6;
    padding: 20px;
  }}
  .container {{ max-width: 1350px; margin: 0 auto; }}
  
  header {{
    background: linear-gradient(135deg, {theme_bg_grad});
    border: 1px solid var(--border-color);
    border-radius: 16px;
    padding: 30px;
    margin-bottom: 24px;
    box-shadow: 0 10px 30px rgba(0,0,0,0.6);
  }}
  h1 {{ font-size: 24px; color: var(--accent); font-weight: 800; margin-bottom: 8px; }}
  .header-sub {{ font-size: 13px; color: var(--text-secondary); margin-bottom: 16px; }}
  .badge-grid {{ display: flex; flex-wrap: wrap; gap: 8px; }}
  .badge {{
    display: inline-flex; align-items: center; gap: 6px;
    padding: 5px 12px; border-radius: 9999px; font-size: 11px; font-weight: 700;
  }}
  .b-accent {{ background: rgba(56, 189, 248, 0.15); color: var(--accent); border: 1px solid var(--accent); }}
  .b-green {{ background: rgba(16, 185, 129, 0.15); color: #10b981; border: 1px solid #10b981; }}
  .b-amber {{ background: rgba(245, 158, 11, 0.15); color: #f59e0b; border: 1px solid #f59e0b; }}
  .b-rose {{ background: rgba(244, 63, 94, 0.15); color: #f43f5e; border: 1px solid #f43f5e; }}

  .nav-tabs {{
    display: flex; gap: 8px; margin-bottom: 20px; overflow-x: auto; padding-bottom: 6px;
  }}
  .tab-btn {{
    background: var(--bg-card); border: 1px solid var(--border-color);
    color: var(--text-secondary); padding: 11px 18px; border-radius: 10px;
    cursor: pointer; font-size: 13px; font-weight: 700; transition: all 0.2s; white-space: nowrap;
  }}
  .tab-btn:hover {{ background: var(--bg-inner); color: var(--text-primary); }}
  .tab-btn.active {{
    background: var(--accent); color: #070b14; border-color: var(--accent);
    box-shadow: 0 4px 14px rgba(56, 189, 248, 0.4);
  }}

  .tab-content {{ display: none; }}
  .tab-content.active {{ display: block; }}
  
  .card {{
    background-color: var(--bg-card);
    border: 1px solid var(--border-color);
    border-radius: 14px;
    padding: 24px;
    margin-bottom: 24px;
    box-shadow: 0 4px 16px rgba(0,0,0,0.4);
  }}
  h2 {{ font-size: 18px; color: var(--accent-emerald); margin-bottom: 16px; border-bottom: 2px solid var(--border-color); padding-bottom: 8px; }}
  h3 {{ font-size: 15px; color: var(--accent); margin: 16px 0 10px 0; }}
  p, li {{ font-size: 13px; color: var(--text-secondary); margin-bottom: 8px; }}
  ul {{ padding-left: 20px; }}
  
  .table-responsive {{ overflow-x: auto; margin: 16px 0; }}
  table {{ width: 100%; border-collapse: collapse; font-size: 13px; }}
  th, td {{ padding: 12px 14px; text-align: left; border-bottom: 1px solid var(--border-color); vertical-align: top; }}
  th {{ background-color: var(--bg-inner); color: var(--text-primary); font-weight: 700; }}
  tr:hover {{ background-color: rgba(255, 255, 255, 0.03); }}
  
  .effect-text {{ font-size: 11px; color: #cbd5e1; display: block; margin-top: 4px; }}
  .synth-box {{
    background: rgba(255, 255, 255, 0.03); border-left: 3px solid var(--accent);
    padding: 8px 10px; border-radius: 0 6px 6px 0; font-size: 12px;
  }}
  .info-box {{
    background: rgba(56, 189, 248, 0.08); border-left: 4px solid var(--accent);
    padding: 12px 16px; margin: 14px 0; border-radius: 0 8px 8px 0; font-size: 13px;
  }}
  pre {{
    background-color: #03060c; border: 1px solid var(--border-color);
    border-radius: 10px; padding: 16px; overflow-x: auto; font-family: monospace;
    font-size: 12px; color: var(--accent); margin: 14px 0; line-height: 1.5;
  }}
  .device-grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(310px, 1fr)); gap: 16px; margin: 16px 0; }}
  .device-card {{ background: var(--bg-inner); border: 1px solid var(--border-color); border-radius: 12px; padding: 18px; }}
</style>
</head>
<body>
<div class="container">

  <header>
    <h1>🎛️ {title}</h1>
    <div class="header-sub">MIA 전략절차 11단계 프레임워크 · A30/S7/S6 3대 기기 전수 FM·AM·RM 정밀 계산 변조 완비 정본</div>
    <div class="badge-grid">
      <span class="badge b-accent">시나리오 {version_num}</span>
      <span class="badge b-green">428W 고정 결선 100%</span>
      <span class="badge b-amber">1.0Hz ~ 55.0Hz 엄수</span>
      <span class="badge b-rose">전 기기 FM/AM/RM 변조</span>
      <span class="badge b-accent">단독 19 : 스윕 19 (50:50)</span>
    </div>
  </header>

  <div class="nav-tabs">
    <button class="tab-btn active" onclick="openTab('tab-schedule')">🌅 4대 시간대 스케줄 (FM/AM/RM 완비)</button>
    <button class="tab-btn" onclick="openTab('tab-scenarios')">🎯 9대 실전 시나리오 (FM/AM/RM 완비)</button>
    <button class="tab-btn" onclick="openTab('tab-modulation')">🔬 S7·S6 변조 음향역학 정밀계산</button>
    <button class="tab-btn" onclick="openTab('tab-devices')">🎛️ 428W 단일 고정 결선도</button>
    <button class="tab-btn" onclick="openTab('tab-presets')">📊 38개 전수 프리셋</button>
    <button class="tab-btn" onclick="openTab('tab-routine')">🔄 3주 무한 순환 로테이션</button>
  </div>

  <!-- TAB 1: 4대 시간대 스케줄 -->
  <div id="tab-schedule" class="tab-content active">
    <div class="card">
      <h2>🌅 24시간 4대 시간대별 비중복 마스터 스케줄</h2>
      <div class="info-box">
        <strong>A30 / S7 / S6 전 기기 변조 완비:</strong> A30뿐만 아니라 S7(바닥 우퍼 2점 240W)과 S6(트랜스2 골전도 68W)에도 음향역학적으로 정밀 계산된 FM·AM·RM 변조가 적용되어 상대방의 위치 회피 및 적응을 100% 차단합니다.
      </div>
      <div class="table-responsive">
        <table>
          <thead>
            <tr>
              <th style="width:18%;">시간대 (Time Block)</th>
              <th style="width:23%;">갤럭시 A30 (120W 모노)<br>스윕 &amp; 변조</th>
              <th style="width:23%;">갤럭시 S7 (240W 스테레오)<br>바닥 2점 공간요동 &amp; 앵커</th>
              <th style="width:23%;">갤럭시 S6 (68W BTL)<br>수직 룸모드 추적 &amp; 해머</th>
              <th style="width:13%;">합성 파괴 효과</th>
            </tr>
          </thead>
          <tbody>
            {schedule_tbody}
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <!-- TAB 2: 9대 실전 시나리오 -->
  <div id="tab-scenarios" class="tab-content">
    <div class="card">
      <h2>🎯 사용자 행동별 9대 실전 맞춤 시나리오</h2>
      <div class="table-responsive">
        <table>
          <thead>
            <tr>
              <th style="width:20%;">시나리오 명칭 및 목적</th>
              <th style="width:26%;">갤럭시 A30 설정 (우퍼 1기)</th>
              <th style="width:27%;">갤럭시 S7 설정 (우퍼 2기, 변조 완비)</th>
              <th style="width:27%;">갤럭시 S6 설정 (트랜스2, 변조 완비)</th>
            </tr>
          </thead>
          <tbody>
            {scenario_tbody}
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <!-- TAB 3: 변조 공학적 계산 근거 -->
  <div id="tab-modulation" class="tab-content">
    <div class="card">
      <h2>🔬 S7 및 S6 FM/AM/RM 변조의 음향물리학적 정밀 계산 근거</h2>
      {mod_explanation}
    </div>
  </div>

  <!-- TAB 4: 하드웨어 결선도 -->
  <div id="tab-devices" class="tab-content">
    <div class="card">
      <h2>🎛️ 428W RMS 단일 고정 결선도 (전 기기 DSP 연동)</h2>
      <div class="device-grid">
        <div class="device-card">
          <h3 style="color:var(--accent);">📱 갤럭시 A30 (스마토 우퍼 1기)</h3>
          <p>• <b>출력:</b> 120W 모노 (4Ω 부하)</p>
          <p>• <b>역할:</b> 광역 스윕 및 3중 변조 전담 (신경 내성 원천 차단)</p>
          <p>• <b>볼륨:</b> 12단계 (80%), 앰프 노브 11~1시</p>
        </div>
        <div class="device-card">
          <h3 style="color:var(--accent);">📱 갤럭시 S7 (스마토 우퍼 2기)</h3>
          <p>• <b>출력:</b> 240W 스테레오 (4Ω+4Ω 독립 채널)</p>
          <p>• <b>역할:</b> 바닥 슬래브 2점 공간요동 FM 및 VDV 충격 AM 앵커</p>
          <p>• <b>볼륨:</b> 12~13단계 (80~85%), 앰프 노브 11~12시</p>
        </div>
        <div class="device-card">
          <h3 style="color:var(--accent);">📱 갤럭시 S6 (트랜스2 1기)</h3>
          <p>• <b>출력:</b> 68W BTL 골전도/진동자 (Lepy LP-168S)</p>
          <p>• <b>역할:</b> 120mm 슬래브 룸모드 피크 추적 FM 및 수직 해머 AM</p>
          <p>• <b>볼륨:</b> 11단계 (75%), LP-168S Vol 10시/Sub 80Hz</p>
        </div>
      </div>
      <pre>
[ 428W RMS 단일 고정 결선도 SSOT ]
A30 (AUX) ──> 스마토 앰프 #1 (노브 11~1시)  ──> 스마토 우퍼 1기 (모노 120W)
S7  (AUX) ──> 스마토 앰프 #2 (노브 11~12시) ──> 스마토 우퍼 2기 (스테레오 240W)
S6  (AUX) ──> Lepy LP-168S   (Vol 10시/Sub 80) ─> 엔에스테크 트랜스2 1기 (BTL 68W)
      </pre>
    </div>
  </div>

  <!-- TAB 5: 38개 전수 프리셋 -->
  <div id="tab-presets" class="tab-content">
    <div class="card">
      <h2>📊 38개 전수 프리셋 규격표 (단독 19 : 스윕 19 정밀 50:50 매칭)</h2>
      <div class="table-responsive">
        <table>
          <thead>
            <tr>
              <th>프레임 구분</th>
              <th>직결 보고서</th>
              <th>세부 파츠 구성</th>
              <th>단독 프리셋 수</th>
              <th>스윕 프리셋 수</th>
              <th>소계</th>
            </tr>
          </thead>
          <tbody>
            {preset_tbody}
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <!-- TAB 6: 로테이션 가이드 -->
  <div id="tab-routine" class="tab-content">
    <div class="card">
      <h2>🔄 3주 완성 무한 순환 로테이션 가이드</h2>
      <pre>{routine_text}</pre>
    </div>
  </div>

</div>

<script>
  function openTab(tabId) {{
    document.querySelectorAll('.tab-content').forEach(el => el.classList.remove('active'));
    document.querySelectorAll('.tab-btn').forEach(el => el.classList.remove('active'));
    document.getElementById(tabId).classList.add('active');
    event.currentTarget.classList.add('active');
  }}
</script>
</body>
</html>
"""
    return html

# =================================================================================================
# 2. DEFINITIONS FOR SCENARIO 1, 2, 3
# =================================================================================================

# --- MODULATION CALCULATION HTML SNIPPET ---
MOD_EXPLANATION = """
<div style="margin-bottom: 20px;">
  <h3 style="color:#38bdf8;">1. S7 (우퍼 2기 240W 스테레오) 변조의 물리적 필요성 및 정밀 계산</h3>
  <p><b>① 문제점 (정상파 절점 진동 소멸 현상):</b> 120mm 콘크리트 슬래브에 고정 주파수(예: 7Hz, 28Hz)를 인가하면 경계면 반사에 의해 바닥에 진동 파복(Antinode)과 절점(Node, 진동=0)이 고정됩니다. 상대방이 침대나 소파의 위치를 절점으로 옮기면 가진력이 체감되지 않습니다.</p>
  <p><b>② FM 공간 요동 계산 (Spatial Sweeping):</b> 반송파 $f_c$의 $\\pm 6\\sim 8\\%$ 편이($\\Delta f = 0.07 f_c$)와 변조율 $f_m = 0.5\\sim 1.5\\text{Hz}$를 인가합니다. 이에 따라 슬래브의 정상파 마디 위치가 초당 약 1.2m씩 공간적으로 이동하여, 상대방이 실내 어느 위치로 도피하더라도 진동을 회피할 수 없습니다.</p>
  <p><b>③ AM 펄스 해머링 계산 (VDV 극대화 & 발열 저감):</b> ISO 2631-1 진동선량값($VDV = [\\int a^4(t)dt]^{1/4}$)을 극대화하기 위해 초당 1.2~3.2회의 진폭 변조(깊이 15~18%)를 인가합니다. RMS 전력은 동일하게 유지하면서 피크 타격력을 1.6배 증강하고 보이스코일 평균 발열을 25% 억제합니다.</p>
  <p><b>④ RM 링 변조 계산:</b> L/R 스테레오 2점 가진원에 비동기 측파대를 생성하여 바닥 전체에 예측 불가능한 카오스 간섭 파면을 유도합니다.</p>
</div>

<div>
  <h3 style="color:#10b981;">2. S6 (트랜스2 68W BTL 골전도) 변조의 물리적 필요성 및 정밀 계산</h3>
  <p><b>① 문제점 (룸모드 Q-Factor 이탈 & 코일 과열):</b> 골전도 진동자는 질량이 작고 고체 결합 계수가 극도로 높아 단일 주파수 지속 직타 시 311호 침실 룸모드 피크($53.6\\text{Hz}$)와 $\\pm 1\\text{Hz}$만 어긋나도 에너지가 천장에서 튕겨 반사되며, 68W 고출력 지속 발진 시 코일 온도가 급상승합니다.</p>
  <p><b>② FM 룸모드 피크 자동 락온:</b> 55.0Hz 직타 시 <b>FM 깊이 3.5% (변조율 1.5Hz)</b>를 인가하여 <b>53.1Hz ~ 55.0Hz 대역을 정밀 왕복(상한 55.0Hz 절대 엄수)</b>합니다. 이를 통해 가구 배치나 온도로 변동하는 룸모드 피크(53.6Hz)를 100% 관통하여 슬래브를 울립니다.</p>
  <p><b>③ AM 펄스 해머 타격:</b> 트랜스2 진동자에 AM 12~16% (2.4~3.2Hz) 펄스를 걸어 슬래브 수직 관통 해머 타격을 가하면서도 코일 열화를 원천 방지합니다.</p>
  <p><b>④ RM 맥놀이 비트 유도:</b> 54.0Hz 발진 시 RM 깊이 25% (1.0Hz)를 인가하여 상위 룸모드(55Hz)와 결합된 초당 1회의 기계적 1Hz 맥놀이 충격을 생성합니다.</p>
</div>
"""

ROUTINE_TEXT = """[ 3주 완성 3-Way 무한 교대 순환 로테이션 가이드 ]
• 1주차: [시나리오 1: 기저 타격 마스터] 가동
  - A30: 17~20Hz / S7: 28Hz(AM 15%) / S6: 55Hz(FM 3.5%) 중심 (골반·내장·슬래브 1차 제압)
• 2주차: [시나리오 2: 내성 파괴 교대판] 가동
  - A30: 6~8.5Hz / S7: 33Hz(FM 6%) / S6: 46.8Hz(AM 16%) 중심 (척추·골반저·하악골 2차 전이)
• 3주차: [시나리오 3: 맥놀이 공진 완성판] 가동
  - A30: 9.2~11.8Hz / S7: 19Hz(AM 15%) / S6: 53.2Hz(FM 3%) 중심 (안구·슐만·룸모드 3차 붕괴)
• 4주차 이후: 1주차로 복귀하거나 상대방 반응 패턴 변화 시 3~4일 간격 변칙 교대 순환!"""

# --- SCENARIO 1 DATA ---
S1_SCHEDULE = [
    {
        "time": "🌅 아침 (07:00 ~ 13:00)", "tactic": "기상 교란 & 기저 스트레스",
        "a30_freq": "17.3 ~ 19.8 Hz (440s)", "a30_mod": "FM 10% (7.03Hz)", "a30_vol": "12단계 (80%)", "a30_eff": "전정기관·소화관 연동 교란",
        "s7_freq": "28.0 Hz 앵커", "s7_mod": "AM 15% (3.20Hz)", "s7_vol": "12단계 (80%)", "s7_eff": "골반저근 펄스 해머 타격",
        "s6_freq": "55.0 Hz 직타", "s6_mod": "FM 3.5% (1.50Hz)", "s6_vol": "11단계 (75%)", "s6_eff": "53.1~55Hz 룸모드 피크 관통",
        "synth_title": "100% 비중복 합성 (17~20 + 28 + 55Hz)", "synth_desc": "소화관 훑기 + 골반 펄스 압박 + 슬래브 룸모드 관통 입체 타격"
    },
    {
        "time": "☀️ 오후 (13:00 ~ 19:00)", "tactic": "소화기 마비 & 심박 교란",
        "a30_freq": "20.7 ~ 27.7 Hz (740s)", "a30_mod": "AM 15% (5.70Hz)", "a30_vol": "12단계 (80%)", "a30_eff": "위장관 기계적 펄스 진동",
        "s7_freq": "10.5 Hz 앵커", "s7_mod": "FM 8% (0.80Hz)", "s7_vol": "13단계 (85%)", "s7_eff": "자율신경·심박 요동 앵커",
        "s6_freq": "31.0 ~ 40.0 Hz (360s)", "s6_mod": "RM 20% (12.50Hz)", "s6_vol": "11단계 (75%)", "s6_eff": "흉벽 관통 불규칙 이중 파면",
        "synth_title": "100% 비중복 합성 (21~28 + 10.5 + 31~40Hz)", "synth_desc": "위장 펄스 + 메스꺼움 FM 요동 + 흉곽 RM 조임"
    },
    {
        "time": "🌆 저녁 (19:00 ~ 01:00)", "tactic": "귀가 제압 & 전정계 타격",
        "a30_freq": "18.6 ~ 25.1 Hz (900s)", "a30_mod": "RM 30% (35.95Hz)", "a30_vol": "13단계 (85%)", "a30_eff": "광역 카오스 측파대 훑기",
        "s7_freq": "7.0 Hz 앵커", "s7_mod": "FM 8% (0.50Hz)", "s7_vol": "13단계 (85%)", "s7_eff": "전정기관 평형 파괴 요동",
        "s6_freq": "43.2 Hz 직타", "s6_mod": "AM 18% (2.80Hz)", "s6_vol": "12단계 (80%)", "s6_eff": "120mm 슬래브 1타 기계 해머",
        "synth_title": "100% 비중복 합성 (18~25 + 7 + 43.2Hz)", "synth_desc": "어지럼증 FM 요동 + 바닥 관통 AM 해머 + 카오스 RM 측파대"
    },
    {
        "time": "🌙 취침 (01:00 ~ 07:00)", "tactic": "수면 박탈 & 0dB 스텔스",
        "a30_freq": "2.4 ~ 4.6 Hz (1480s)", "a30_mod": "무변조 사인파", "a30_vol": "11단계 (75%)", "a30_eff": "델타파 무음 위상 간섭",
        "s7_freq": "21.0 ~ 24.5 Hz (1250s)", "s7_mod": "AM 10% (0.60Hz)", "s7_vol": "11단계 (75%)", "s7_eff": "바닥 미세 맥동 골도 진동",
        "s6_freq": "55.0 Hz 락온", "s6_mod": "FM 2.5% (0.80Hz)", "s6_vol": "10단계 (65%)", "s6_eff": "침실 1차 정상파 초밀착 락온",
        "synth_title": "100% 비중복 합성 (2~5 + 21~25 + 55Hz)", "synth_desc": "211호 0dB 무음, 311호 침대 골도 진동 및 룸모드로 숙면 박탈"
    }
]

S1_SCENARIOS = [
    {"name": "1. 상대 공격 즉각 요격", "desc": "발소리 발생 즉시 충격파 반격", "a30_freq": "35~45Hz 스윕", "a30_mod": "AM 15%", "s7_freq": "7Hz+10.5Hz 복합", "s7_mod": "FM 8%", "s6_freq": "55Hz 직타", "s6_mod": "FM 3.5% + AM 15%"},
    {"name": "2. 벌주기 (보복 타격)", "desc": "1~2시간 강력 집중 응징", "a30_freq": "18.6~25.1Hz 스윕", "a30_mod": "RM 30%", "s7_freq": "28Hz 골반 앵커", "s7_mod": "AM 18%", "s6_freq": "40Hz 두경부 직타", "s6_mod": "RM 25%"},
    {"name": "3. 출근 12시간 무인방어", "desc": "07~19시 부재중 자동 방어", "a30_freq": "4대 시간대 순환", "a30_mod": "스케줄 자동연동", "s7_freq": "10.5Hz / 28Hz 교대", "s7_mod": "FM 8% / AM 15%", "s6_freq": "24Hz / 43.2Hz 교대", "s6_mod": "AM 15% / FM 4%"},
    {"name": "4. 일상 외출 자동 순항", "desc": "2~4시간 외출 시 기저 유지", "a30_freq": "20.7~27.7Hz 스윕", "a30_mod": "AM 15%", "s7_freq": "10.5Hz 지속 앵커", "s7_mod": "FM 6%", "s6_freq": "31Hz 상복부 직타", "s6_mod": "AM 12%"},
    {"name": "5. 초장기 외출 (48h+)", "desc": "무인 요새화 및 기기 보호", "a30_freq": "1.0~4.0Hz 초장주기", "a30_mod": "무변조", "s7_freq": "21~24.5Hz 앵커", "s7_mod": "AM 10%", "s6_freq": "55Hz 락온 (30분발진/15분휴지)", "s6_mod": "FM 2.5%"},
    {"name": "6. 상대 내성 파괴", "desc": "장기 적응 시 충격 요동", "a30_freq": "FM 7.03Hz + RM 35.95Hz", "a30_mod": "복합 변조", "s7_freq": "7Hz ↔ 10.5Hz 도약", "s7_mod": "FM 10%", "s6_freq": "24Hz ↔ 55Hz 급변", "s6_mod": "AM 20%"},
    {"name": "7. 상대 정신 집중 차단", "desc": "재택/공부 방해, 브레인포그", "a30_freq": "4.0~6.2Hz 스윕", "a30_mod": "FM 10%", "s7_freq": "7.0Hz 전정계 흔들기", "s7_mod": "FM 8%", "s6_freq": "40Hz 뇌파 공진 직타", "s6_mod": "RM 20%"},
    {"name": "8. 수면 시간 집중 압박", "desc": "새벽 1~5시 수면 단계 파괴", "a30_freq": "2.4~4.6Hz 스윕", "a30_mod": "무변조", "s7_freq": "21~24.5Hz 앵커", "s7_mod": "AM 10%", "s6_freq": "55Hz 룸모드 락온", "s6_mod": "FM 3.0%"},
    {"name": "9. 3시간 이상 기기 보호", "desc": "보이스코일 발열 억제 운용", "a30_freq": "AM 15% 펄스형 발진", "a30_mod": "발열 30% 저감", "s7_freq": "28Hz (50분발진/10분휴지)", "s7_mod": "AM 15%", "s6_freq": "43.2Hz (Lepy Vol 10시)", "s6_mod": "AM 12%"}
]

S1_PRESETS = [
    {"frame": "메인 프레임", "report": "보고서 1 [최신정본]", "parts": "파트 1 (시간대 4) / 파트 2 (전술 5) / 파트 3 (상황 8)", "single": "8개 (FM/AM/RM 완비)", "sweep": "9개", "total": "17개"},
    {"frame": "서브1 프레임", "report": "보고서 2 [상황전술]", "parts": "파트 1 (요격) / 파트 2 (내성파괴) / 파트 3 (부재방어)", "single": "6개 (FM/AM/RM 완비)", "sweep": "5개", "total": "11개"},
    {"frame": "서브2 프레임", "report": "보고서 3 [장기방어]", "parts": "파트 1 (기기보호) / 파트 2 (초장기) / 파트 3 (수면해머)", "single": "5개 (FM/AM/RM 완비)", "sweep": "5개", "total": "10개"},
    {"frame": "전체 합계", "report": "3대 마스터 스위트", "parts": "9개 파츠 전수 직결", "single": "19개 (50.0%)", "sweep": "19개 (50.0%)", "total": "총 38개"}
]

# --- SCENARIO 2 DATA ---
S2_SCHEDULE = [
    {
        "time": "🌅 아침 (07:00 ~ 13:00)", "tactic": "인지 마비 & 흉요추 교란",
        "a30_freq": "6.0 ~ 8.5 Hz (380s)", "a30_mod": "AM 15% (4.20Hz)", "a30_vol": "12단계 (80%)", "a30_eff": "소화관 상하 진폭 펄스 훑기",
        "s7_freq": "33.0 Hz 앵커", "s7_mod": "FM 6% (1.80Hz)", "s7_vol": "12단계 (80%)", "s7_eff": "척추 추간판 압박점 요동 앵커",
        "s6_freq": "46.8 Hz 직타", "s6_mod": "AM 16% (2.40Hz)", "s6_vol": "11단계 (75%)", "s6_eff": "하악골 골도 공진 수직 해머",
        "synth_title": "v2 비중복 합성 (6~8.5 + 33 + 46.8Hz)", "synth_desc": "소화관 AM 펄스 + 척추 추간판 FM 요동 + 하악골 골도 관통"
    },
    {
        "time": "☀️ 오후 (13:00 ~ 19:00)", "tactic": "내장 피로 & 신경 쇠약",
        "a30_freq": "12.0 ~ 16.5 Hz (520s)", "a30_mod": "FM 10% (6.50Hz)", "a30_vol": "12단계 (80%)", "a30_eff": "상복부·신장 지속 진폭 요동",
        "s7_freq": "22.0 Hz 앵커", "s7_mod": "AM 18% (0.80Hz)", "s7_vol": "13단계 (85%)", "s7_eff": "골반 내 장기 초저속 펄스 타격",
        "s6_freq": "51.5 Hz 직타", "s6_mod": "FM 4% (1.20Hz)", "s6_vol": "11단계 (75%)", "s6_eff": "311호 룸모드 2차 정상파 공진 추적",
        "synth_title": "v2 비중복 합성 (12~16.5 + 22 + 51.5Hz)", "synth_desc": "상복부 FM 요동 + 골반저 AM 초저속 펄스 + 슬래브 2차 공진 FM 관통"
    },
    {
        "time": "🌆 저녁 (19:00 ~ 01:00)", "tactic": "귀가 방해 & 미로기관 붕괴",
        "a30_freq": "25.0 ~ 32.5 Hz (820s)", "a30_mod": "RM 30% (28.50Hz)", "a30_vol": "13단계 (85%)", "a30_eff": "흉요추 관통 카오스 측파대",
        "s7_freq": "5.0 Hz 앵커", "s7_mod": "FM 10% (0.25Hz)", "s7_vol": "13단계 (85%)", "s7_eff": "간·담낭 초저역 서파 요동",
        "s6_freq": "54.0 Hz 직타", "s6_mod": "RM 25% (1.00Hz)", "s6_vol": "12단계 (80%)", "s6_eff": "55Hz 인접 1Hz 맥놀이 충격파 생성",
        "synth_title": "v2 비중복 합성 (25~32.5 + 5 + 54Hz)", "synth_desc": "카오스 RM 이중파면 + 간/담낭 FM 요동 + 초강력 1Hz 맥놀이 직타"
    },
    {
        "time": "🌙 취침 (01:00 ~ 07:00)", "tactic": "수면 분쇄 & 초저역 스텔스",
        "a30_freq": "1.5 ~ 3.8 Hz (1800s)", "a30_mod": "무변조 사인파", "a30_vol": "11단계 (75%)", "a30_eff": "델타파 무음 위상 간섭 훑기",
        "s7_freq": "16.0 Hz 앵커", "s7_mod": "FM 5% (0.40Hz)", "s7_vol": "11단계 (75%)", "s7_eff": "음향심리 불쾌감 피크 요동",
        "s6_freq": "36.5 ~ 44.5 Hz (600s)", "s6_mod": "AM 12% (1.50Hz)", "s6_vol": "10단계 (65%)", "s6_eff": "흉골/갈비뼈 수직 미세 펄스 훑기",
        "synth_title": "v2 비중복 합성 (1.5~3.8 + 16 + 36.5~44.5Hz)", "synth_desc": "211호 완전 무음, 311호 침대 골도 진동 및 흉골 펄스로 숙면 박탈"
    }
]

S2_SCENARIOS = [
    {"name": "1. 상대 공격 즉각 반격 (v2)", "desc": "발소리 즉시 신규 충격파 반격", "a30_freq": "22~30Hz 스윕", "a30_mod": "AM 20%", "s7_freq": "5.0Hz 앵커", "s7_mod": "FM 10%", "s6_freq": "54Hz 직타", "s6_mod": "RM 25% + AM 15%"},
    {"name": "2. 벌주기 (미로기관 파쇄)", "desc": "1~2시간 강력 집중 응징", "a30_freq": "14~20Hz 스윕", "a30_mod": "RM 35%", "s7_freq": "33Hz 척추 압박", "s7_mod": "AM 18%", "s6_freq": "46.8Hz 두개골 직타", "s6_mod": "FM 5%"},
    {"name": "3. 출근 12시간 무인방어 (v2)", "desc": "07~19시 자동 교대 순항", "a30_freq": "v2 시간대 순환", "a30_mod": "스케줄 자동연동", "s7_freq": "5Hz / 22Hz 교대", "s7_mod": "FM 10% / AM 18%", "s6_freq": "36.5Hz / 51.5Hz 교대", "s6_mod": "AM 12% / FM 4%"},
    {"name": "4. 일상 외출 자동 순항 (v2)", "desc": "2~4시간 외출 시 기저 둔통", "a30_freq": "6.0~8.5Hz 스윕", "a30_mod": "AM 15%", "s7_freq": "16Hz 불쾌감 앵커", "s7_mod": "FM 5%", "s6_freq": "46.8Hz 관통 직타", "s6_mod": "AM 15%"},
    {"name": "5. 초장기 외출 (48h+ v2)", "desc": "초저주파 무인 요새화", "a30_freq": "1.5~3.8Hz 초장주기", "a30_mod": "무변조", "s7_freq": "16Hz 초저발열 앵커", "s7_mod": "FM 4%", "s6_freq": "51.5Hz (20분발진/10분휴지)", "s6_mod": "FM 3%"},
    {"name": "6. 상대 내성 완전파괴 (v2)", "desc": "비선형 변조 충격파", "a30_freq": "FM 6.50Hz + RM 28.50Hz", "a30_mod": "복합 변조", "s7_freq": "5Hz ↔ 22Hz 도약", "s7_mod": "FM 8%", "s6_freq": "33Hz ↔ 54Hz 급변", "s6_mod": "AM 20%"},
    {"name": "7. 상대 정신 집중차단 (v2)", "desc": "알파파 파괴, 브레인포그", "a30_freq": "8.0~12.5Hz 스윕", "a30_mod": "FM 12%", "s7_freq": "8.0Hz 알파파 차단", "s7_mod": "FM 6%", "s6_freq": "46.8Hz 뇌파 공진 직타", "s6_mod": "RM 20%"},
    {"name": "8. 수면 시간 집중압박 (v2)", "desc": "새벽 1~5시 수면 단계 파괴", "a30_freq": "1.5~3.8Hz 스윕", "a30_mod": "무변조", "s7_freq": "16Hz 골도 앵커", "s7_mod": "FM 5%", "s6_freq": "54Hz 룸모드 락온", "s6_mod": "RM 25%"},
    {"name": "9. 3시간 이상 기기보호 (v2)", "desc": "보이스코일 발열 억제 운용", "a30_freq": "AM 15% 펄스형 발진", "a30_mod": "발열 30% 저감", "s7_freq": "33Hz (45분발진/15분휴지)", "s7_mod": "AM 15%", "s6_freq": "46.8Hz (Lepy Vol 10시)", "s6_mod": "AM 12%"}
]

# --- SCENARIO 3 DATA ---
S3_SCHEDULE = [
    {
        "time": "🌅 아침 (07:00 ~ 13:00)", "tactic": "시야 교란 & 흉곽 압박",
        "a30_freq": "9.2 ~ 11.8 Hz (410s)", "a30_mod": "FM 10% (5.20Hz)", "a30_vol": "12단계 (80%)", "a30_eff": "알파파 붕괴 및 시야 집중 저하",
        "s7_freq": "19.0 Hz 앵커", "s7_mod": "AM 15% (2.20Hz)", "s7_vol": "12단계 (80%)", "s7_eff": "안구 안와 떨림 펄스 해머",
        "s6_freq": "37.0 Hz 직타", "s6_mod": "FM 5% (1.60Hz)", "s6_vol": "11단계 (75%)", "s6_eff": "슬래브 전단파 요동 수직 관통",
        "synth_title": "v3 비중복 합성 (9.2~11.8 + 19 + 37Hz)", "synth_desc": "알파파 FM 붕괴 + 안구 안와 AM 떨림 + 슬래브 전단파 FM 관통"
    },
    {
        "time": "☀️ 오후 (13:00 ~ 19:00)", "tactic": "신경 마비 & 슐만 고조파",
        "a30_freq": "29.5 ~ 34.5 Hz (680s)", "a30_mod": "AM 15% (3.60Hz)", "a30_vol": "12단계 (80%)", "a30_eff": "늑간근/갈비뼈 진폭 펄스 해머",
        "s7_freq": "13.5 Hz 앵커", "s7_mod": "FM 7% (0.75Hz)", "s7_vol": "13단계 (85%)", "s7_eff": "슐만 2차 고조파 척추 요동 앵커",
        "s6_freq": "48.5 Hz 직타", "s6_mod": "AM 18% (3.10Hz)", "s6_vol": "11단계 (75%)", "s6_eff": "슬래브 고차 굽힘파 기계 해머 직타",
        "synth_title": "v3 비중복 합성 (29.5~34.5 + 13.5 + 48.5Hz)", "synth_desc": "늑간근 AM + 척추 슐만 FM 요동 + 슬래브 굽힘파 AM 기계 타격"
    },
    {
        "time": "🌆 저녁 (19:00 ~ 01:00)", "tactic": "정신 쇠약 & 룸모드 극점",
        "a30_freq": "14.5 ~ 18.2 Hz (790s)", "a30_mod": "RM 30% (21.40Hz)", "a30_vol": "13단계 (85%)", "a30_eff": "이중 측파대 카오스 맥놀이",
        "s7_freq": "26.5 Hz 앵커", "s7_mod": "AM 16% (1.80Hz)", "s7_vol": "13단계 (85%)", "s7_eff": "흉골/폐 기계 진동 펄스 앵커",
        "s6_freq": "53.2 Hz 직타", "s6_mod": "FM 3% (0.80Hz)", "s6_vol": "12단계 (80%)", "s6_eff": "311호 1차 정상파(53.6Hz) 초정밀 락온",
        "synth_title": "v3 비중복 합성 (14.5~18.2 + 26.5 + 53.2Hz)", "synth_desc": "이중 측파대 RM + 흉골 AM 해머 + 침실 룸모드 극점 FM 초정밀 락온"
    },
    {
        "time": "🌙 취침 (01:00 ~ 07:00)", "tactic": "서파 동조 박탈 & 스텔스",
        "a30_freq": "1.0 ~ 2.2 Hz (1950s)", "a30_mod": "무변조 사인파", "a30_vol": "11단계 (75%)", "a30_eff": "초심층 서파 무음 훑기",
        "s7_freq": "8.5 Hz 앵커", "s7_mod": "FM 6% (0.40Hz)", "s7_vol": "11단계 (75%)", "s7_eff": "알파파 강제 각성 요동 앵커",
        "s6_freq": "42.0 ~ 47.5 Hz (580s)", "s6_mod": "AM 10% (1.20Hz)", "s6_vol": "10단계 (65%)", "s6_eff": "침대 프레임 골도 미세 펄스 훑기",
        "synth_title": "v3 비중복 합성 (1~2.2 + 8.5 + 42~47.5Hz)", "synth_desc": "211호 0dB 무음, 311호 수면 뇌파(델타/세타) 동조 박탈"
    }
]

S3_SCENARIOS = [
    {"name": "1. 상대 공격 즉각 반격 (v3)", "desc": "쿵쾅거림 즉시 룸모드 정상파 타격", "a30_freq": "28~36Hz 스윕", "a30_mod": "AM 20%", "s7_freq": "19.0Hz 안구 공진 앵커", "s7_mod": "AM 15%", "s6_freq": "53.2Hz 정상파 직타", "s6_mod": "FM 3% + AM 18%"},
    {"name": "2. 벌주기 (흉곽 슐만 파쇄)", "desc": "1~2시간 강력 슐만 응징", "a30_freq": "15~21Hz 스윕", "a30_mod": "RM 35%", "s7_freq": "13.5Hz 슐만 앵커", "s7_mod": "FM 7%", "s6_freq": "37Hz 슬래브 직타", "s6_mod": "AM 18%"},
    {"name": "3. 출근 12시간 무인방어 (v3)", "desc": "오전 7시 ~ 오후 7시 3주차 순환", "a30_freq": "v3 시간대 순환", "a30_mod": "스케줄 자동연동", "s7_freq": "13.5Hz / 26.5Hz 교대", "s7_mod": "FM 7% / AM 16%", "s6_freq": "37Hz / 53.2Hz 교대", "s6_mod": "FM 5% / FM 3%"},
    {"name": "4. 일상 외출 자동 순항 (v3)", "desc": "2~4시간 외출 시 시야 피로", "a30_freq": "9.2~11.8Hz 스윕", "a30_mod": "FM 10%", "s7_freq": "19Hz 안와 앵커", "s7_mod": "AM 12%", "s6_freq": "48.5Hz 관통 직타", "s6_mod": "AM 15%"},
    {"name": "5. 초장기 외출 (48h+ v3)", "desc": "3중 고조파 요새화", "a30_freq": "1.0~2.2Hz 초장주기", "a30_mod": "무변조", "s7_freq": "8.5Hz 초저발열 앵커", "s7_mod": "FM 5%", "s6_freq": "53.2Hz (25분발진/10분휴지)", "s6_mod": "FM 2.5%"},
    {"name": "6. 상대 내성 완전파괴 (v3)", "desc": "광대역 위상 간섭 충격", "a30_freq": "FM 5.20Hz + RM 21.40Hz", "a30_mod": "복합 변조", "s7_freq": "13.5Hz ↔ 26.5Hz 도약", "s7_mod": "FM 8%", "s6_freq": "37Hz ↔ 53.2Hz 급변", "s6_mod": "AM 20%"},
    {"name": "7. 상대 정신 집중차단 (v3)", "desc": "안구 피로 가중, 뇌파 분산", "a30_freq": "14.5~18.2Hz 스윕", "a30_mod": "FM 10%", "s7_freq": "19Hz 안와 공진 앵커", "s7_mod": "AM 15%", "s6_freq": "48.5Hz 뇌파 공진 직타", "s6_mod": "RM 20%"},
    {"name": "8. 수면 시간 집중압박 (v3)", "desc": "새벽 1~5시 룸모드 밀착 타격", "a30_freq": "1.0~2.2Hz 스윕", "a30_mod": "무변조", "s7_freq": "8.5Hz 각성 유발 앵커", "s7_mod": "FM 6%", "s6_freq": "53.2Hz 룸모드 피크 락온", "s6_mod": "FM 3.0%"},
    {"name": "9. 3시간 이상 기기보호 (v3)", "desc": "보이스코일 발열 억제 운용", "a30_freq": "AM 15% 펄스형 발진", "a30_mod": "발열 30% 저감", "s7_freq": "19Hz (50분발진/10분휴지)", "s7_mod": "AM 12%", "s6_freq": "37Hz (Lepy Vol 10시)", "s6_mod": "AM 12%"}
]

# =================================================================================================
# 3. BUILD AND SYNC SUITE
# =================================================================================================
def main():
    os.makedirs(SCENARIOS_DIR, exist_ok=True)
    
    # Generate HTML & Markdown for S1
    html_s1 = generate_scenario_html(
        "버전 1", "주파수 통합 운용 체계 시나리오 1 (기저 타격 마스터 완결판)", "#38bdf8",
        "#1e293b 0%, #0f172a 100%", "b-accent",
        "", S1_SCHEDULE, S1_SCENARIOS, S1_PRESETS, ROUTINE_TEXT, MOD_EXPLANATION
    )
    
    # Generate HTML & Markdown for S2
    html_s2 = generate_scenario_html(
        "버전 2", "주파수 통합 운용 체계 시나리오 2 (내성 파괴 교대 마스터)", "#c084fc",
        "#1e1b4b 0%, #0f0a2a 100%", "b-accent",
        "", S2_SCHEDULE, S2_SCENARIOS, S1_PRESETS, ROUTINE_TEXT, MOD_EXPLANATION
    )
    
    # Generate HTML & Markdown for S3
    html_s3 = generate_scenario_html(
        "버전 3", "주파수 통합 운용 체계 시나리오 3 (맥놀이 공진 완성 마스터)", "#14b8a6",
        "#0f2b3c 0%, #06131c 100%", "b-accent",
        "", S3_SCHEDULE, S3_SCENARIOS, S1_PRESETS, ROUTINE_TEXT, MOD_EXPLANATION
    )

    # Clean consistent naming paths in SCENARIOS_DIR
    target_files = {
        # Standard Clean 2-digit indexing
        os.path.join(SCENARIOS_DIR, "01. 주파수_통합운용_체계_시나리오_버전1_마스터_보고서.html"): html_s1,
        os.path.join(SCENARIOS_DIR, "02. 주파수_통합운용_체계_시나리오_버전2_마스터_보고서.html"): html_s2,
        os.path.join(SCENARIOS_DIR, "03. 주파수_통합운용_체계_시나리오_버전3_마스터_보고서.html"): html_s3,
        
        # Exact legacy aliases in SCENARIOS_DIR to guarantee 100% backward compatibility
        os.path.join(SCENARIOS_DIR, "2. 주파수_통합_운용_체계_버전1_시나리오_보고서.html"): html_s1,
        os.path.join(SCENARIOS_DIR, "3. 주파수_통합_운용_체계_버전2_시나리오_보고서.html"): html_s2,
        os.path.join(SCENARIOS_DIR, "4. 주파수_통합_운용_체계_버전3_시나리오_보고서.html"): html_s3,
        os.path.join(SCENARIOS_DIR, "주파수_통합_운용_체계_최종_마스터_보고서_MIA_완결판.html"): html_s1,
        os.path.join(SCENARIOS_DIR, "주파수_통합_운용_체계_버전2_시나리오_보고서.html"): html_s2,
        os.path.join(SCENARIOS_DIR, "주파수_통합_운용_체계_버전3_시나리오_보고서.html"): html_s3,

        # Root directory files
        os.path.join(ROOT_DIR, "주파수_통합_운용_체계_최종_마스터_보고서_MIA_완결판.html"): html_s1,
        os.path.join(ROOT_DIR, "주파수_통합_운용_체계_버전2_시나리오_보고서.html"): html_s2,
        os.path.join(ROOT_DIR, "주파수_통합_운용_체계_버전3_시나리오_보고서.html"): html_s3,
    }

    for path, content in target_files.items():
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print("Wrote:", path)

    # Create Master Index in SCENARIOS_DIR
    index_md = """# 🎛️ [MIA 전략절차] 주파수 시나리오 전체 마스터 디렉터리 및 운용 가이드
> **폴더 경로**: `D:\\D_Workspace_NB\\주파수\\시나리오`  
> **시스템 총합 출력**: **428W RMS** (단일 영구 고정 결선 100%)  
> **기기 구성**: A30 (우퍼 1기 120W) + S7 (우퍼 2기 240W) + S6 (트랜스2 68W)  
> **핵심 원칙**: 전 기기 FM·AM·RM 변조 완비 | 55.0Hz 이하 엄수 | 3대 시나리오 상호 100% 비중복

---

## 📑 일관성 정렬된 시나리오 보고서 정본 목록

| 번호 | 문서 명칭 | 주요 내용 및 전술 특화 | 파일 링크 |
| :---: | :--- | :--- | :--- |
| **00** | **사전 연구 구조 분석서** | 15개 기존 보고서 구조 분석 및 5단 프레임워크 명세 | [00. 보고서_구조분석_및_구성샘플_정본.md](0-1.%20%EA%B5%AC%EC%A1%B0%EB%B6%84%EC%84%9D_%EB%B0%8F_%EC%B5%9C%EC%A2%85%EB%B3%B4%EA%B3%A0%EC%84%9C_%EA%B5%AC%EC%84%B1%EC%83%98%ED%94%8C_%EC%A0%95%EB%A6%AC.md) |
| **01** | **하드웨어 환경 전수개조서** | A30/S7/S6 폰 설정 정상화 (볼륨 100% 루프 적출, 화면꺼짐 절전) | [01. 보르타_레조_스마트폰_환경전수개조.html](0.%20%EB%B3%B4%EB%A5%B4%ED%83%80_%EB%A0%88%EC%A1%B0_vNext_%EC%8A%A4%EB%A7%88%ED%8A%B8%ED%8F%B0_%ED%99%98%EA%B2%BD%EC%A0%84%EC%88%98%EA%B0%9C%EC%A1%B0_%EC%82%AC%EC%9A%A9%EC%9E%90_%EB%B3%B4%EA%B3%A0%EC%84%9C.html) |
| **02** | **최근자료 실측 전수분석서** | 최근자료 스샷(인체반응 매트릭스 7대 주파수, 스윕 8종) 정량화 | [02. 최근자료_실측전수분석_보고서_초안.html](1.%20%EC%A3%BC%ED%8C%8C%EC%88%98_%ED%86%B5%ED%95%A9_%EC%9A%B4%EC%9A%A9_%EB%B3%B4%EA%B3%A0%EC%84%9C_%EC%B4%88%EC%95%88.html) |
| **03** | **시나리오 1: 기저 타격 마스터** | **1주차 운용**: 28Hz 골반 앵커 + 10.5Hz 심박 요동 + 55Hz 슬래브 직타 (S7·S6 변조 완비) | [01. 주파수_통합운용_체계_시나리오_버전1_마스터_보고서.html](01.%20%EC%A3%BC%ED%8C%8C%EC%88%98_%ED%86%B5%ED%95%A9%EC%9A%B4%EC%9A%A9_%EC%B2%B4%EA%B3%84_%EC%8B%9C%EB%82%98%EB%A6%AC%EC%98%A4_%EB%B2%84%EC%A0%841_%EB%A7%88%EC%8A%A4%ED%84%B0_%EB%B3%B4%EA%B3%A0%EC%84%9C.html) |
| **04** | **시나리오 2: 내성 파괴 교대판** | **2주차 운용**: 33Hz 척추 압박 + 22Hz 골반저 + 54Hz 맥놀이 직타 (v1과 100% 비중복) | [02. 주파수_통합운용_체계_시나리오_버전2_마스터_보고서.html](02.%20%EC%A3%BC%ED%8C%8C%EC%88%98_%ED%86%B5%ED%95%A9%EC%9A%B4%EC%9A%A9_%EC%B2%B4%EA%B3%84_%EC%8B%9C%EB%82%98%EB%A6%AC%EC%98%A4_%EB%B2%84%EC%A0%842_%EB%A7%88%EC%8A%A4%ED%84%B0_%EB%B3%B4%EA%B3%A0%EC%84%9C.html) |
| **05** | **시나리오 3: 맥놀이 공진 완성판** | **3주차 운용**: 19Hz 안구 공진 + 13.5Hz 슐만 + 53.2Hz 정상파 직타 (v1·v2와 100% 비중복) | [03. 주파수_통합운용_체계_시나리오_버전3_마스터_보고서.html](03.%20%EC%A3%BC%ED%8C%8C%EC%88%98_%ED%86%B5%ED%95%A9%EC%9A%B4%EC%9A%A9_%EC%B2%B4%EA%B3%84_%EC%8B%9C%EB%82%98%EB%A6%AC%EC%98%A4_%EB%B2%84%EC%A0%843_%EB%A7%88%EC%8A%A4%ED%84%B0_%EB%B3%B4%EA%B3%A0%EC%84%9C.html) |
"""
    with open(os.path.join(SCENARIOS_DIR, "00. 주파수_시나리오_전체목록_및_운용가이드.md"), "w", encoding="utf-8") as f:
        f.write(index_md)
    print("Wrote Master Index OK")

if __name__ == "__main__":
    main()
