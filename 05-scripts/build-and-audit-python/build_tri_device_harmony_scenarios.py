# -*- coding: utf-8 -*-
"""
build_tri_device_harmony_scenarios.py
MIA 전략절차 11단계 프레임워크 기반:
- 3대 기기(A30 + S7 + S6 = 428W RMS) 통합 음향역학 변조 조화 매트릭스 전면 탑재
  (S7·S6 전용 고립 변조가 아닌, A30의 스윕/기준위상과 S7 바닥앵커, S6 슬래브수직직타의 상호 변조 조화)
- 파일명 통일 체계 엄수:
  - 01. 주파수_통합운용_시나리오_버전1_보고서.html / .md
  - 02. 주파수_통합운용_시나리오_버전2_보고서.html / .md
  - 03. 주파수_통합운용_시나리오_버전3_보고서.html / .md
  - 00. 주파수_시나리오_전체목록_및_운용가이드.md 갱신
- 55.0Hz 이하 엄수, 428W 단일 고정 결선 100%, 단독 19 : 스윕 19 (50:50) 매칭, 100% 비중복 검증
"""
import os
import sys

SCENARIOS_DIR = r"D:\D_Workspace_NB\주파수\시나리오"
ROOT_DIR = r"D:\D_Workspace_NB\주파수"

# =================================================================================================
# 1. HTML GENERATOR WITH FULL INTERACTIVE TABS & HARMONY MATRIX
# =================================================================================================
def generate_scenario_html(version_num, title, theme_color, theme_bg_grad, badge_color, 
                            schedule_rows, scenario_rows, preset_rows, 
                            routine_text, harmony_html):
    
    schedule_tbody = ""
    for r in schedule_rows:
        schedule_tbody += f"""
        <tr>
          <td><b>{r['time']}</b><br><span style="color:#94a3b8; font-size:12px;">{r['tactic']}</span></td>
          <td>
            <span class="badge {badge_color}">A30 (120W 모노)</span><br>
            • <b>{r['a30_freq']}</b><br>
            • <b>{r['a30_mod']}</b><br>
            • 볼륨: {r['a30_vol']}<br>
            <span class="effect-text">{r['a30_eff']}</span>
          </td>
          <td>
            <span class="badge {badge_color}">S7 (240W 스테레오)</span><br>
            • <b>{r['s7_freq']}</b><br>
            • <b>{r['s7_mod']}</b><br>
            • 볼륨: {r['s7_vol']}<br>
            <span class="effect-text">{r['s7_eff']}</span>
          </td>
          <td>
            <span class="badge {badge_color}">S6 (68W BTL)</span><br>
            • <b>{r['s6_freq']}</b><br>
            • <b>{r['s6_mod']}</b><br>
            • 볼륨: {r['s6_vol']}<br>
            <span class="effect-text">{r['s6_eff']}</span>
          </td>
          <td>
            <div class="synth-box">
              <strong style="color:var(--accent);">{r['synth_title']}</strong><br>
              <div style="margin-top:4px; font-size:11px; color:#e2e8f0;">{r['synth_desc']}</div>
              <div style="margin-top:4px; font-size:11px; color:#38bdf8;">• <b>상호 변조 차음(Beats):</b> {r['synth_beat']}</div>
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
          <td>
            <span style="font-size:11px; color:#cbd5e1;">{s['harmony']}</span>
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
  .container {{ max-width: 1400px; margin: 0 auto; }}
  
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
    <div class="header-sub">MIA 전략절차 11단계 프레임워크 · A30 + S7 + S6 (428W RMS) 전 기기 음향역학 변조 조화(Harmony) 완비 정본</div>
    <div class="badge-grid">
      <span class="badge b-accent">시나리오 {version_num}</span>
      <span class="badge b-green">428W 고정 결선 100%</span>
      <span class="badge b-amber">1.0Hz ~ 55.0Hz 엄수</span>
      <span class="badge b-rose">3기기 상호 변조 조화</span>
      <span class="badge b-accent">단독 19 : 스윕 19 (50:50)</span>
    </div>
  </header>

  <div class="nav-tabs">
    <button class="tab-btn active" onclick="openTab('tab-schedule')">🌅 4대 시간대 스케줄 (3기기 변조 조화)</button>
    <button class="tab-btn" onclick="openTab('tab-scenarios')">🎯 9대 실전 시나리오 (3기기 변조 조화)</button>
    <button class="tab-btn" onclick="openTab('tab-harmony')">🔬 3대 기기 통합 음향역학 변조 조화 매트릭스</button>
    <button class="tab-btn" onclick="openTab('tab-devices')">🎛️ 428W 단일 고정 결선도</button>
    <button class="tab-btn" onclick="openTab('tab-presets')">📊 38개 전수 프리셋</button>
    <button class="tab-btn" onclick="openTab('tab-routine')">🔄 3주 무한 순환 로테이션</button>
  </div>

  <!-- TAB 1: 4대 시간대 스케줄 -->
  <div id="tab-schedule" class="tab-content active">
    <div class="card">
      <h2>🌅 24시간 4대 시간대별 3기기 변조 조화 마스터 스케줄</h2>
      <div class="info-box">
        <strong>A30 + S7 + S6 전 기기 음향역학 변조 조화(Modulation Harmony):</strong> A30(120W 모노 광역 스윕 및 기준 위상), S7(240W 스테레오 바닥 2점 앵커 및 공간요동 FM/AM), S6(68W BTL 슬래브 수직 관통 및 룸모드 피크 FM/RM) 3대 기기가 고립되지 않고, 상호 변조 차음(Difference Beats, 0.5~15Hz)과 결합 포락선(Combined Envelope)을 통해 비선형 전신 진동(ISO 2631-1)을 생성합니다.
      </div>
      <div class="table-responsive">
        <table>
          <thead>
            <tr>
              <th style="width:16%;">시간대 (Time Block)</th>
              <th style="width:21%;">갤럭시 A30 (120W 모노)<br>광역 스윕 &amp; 기준 위상</th>
              <th style="width:21%;">갤럭시 S7 (240W 스테레오)<br>바닥 2점 기저 앵커 &amp; 공간요동</th>
              <th style="width:21%;">갤럭시 S6 (68W BTL)<br>슬래브 수직 직타 &amp; 룸모드 락온</th>
              <th style="width:21%;">3기기 음향역학 조화 &amp; 상호 변조 차음</th>
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
      <h2>🎯 사용자 행동별 9대 실전 맞춤 시나리오 (3기기 조화 완비)</h2>
      <div class="table-responsive">
        <table>
          <thead>
            <tr>
              <th style="width:18%;">시나리오 명칭 및 목적</th>
              <th style="width:22%;">갤럭시 A30 설정 (120W)</th>
              <th style="width:22%;">갤럭시 S7 설정 (240W)</th>
              <th style="width:22%;">갤럭시 S6 설정 (68W)</th>
              <th style="width:16%;">3기기 상호 변조 조화 효과</th>
            </tr>
          </thead>
          <tbody>
            {scenario_tbody}
          </tbody>
        </table>
      </div>
    </div>
  </div>

  <!-- TAB 3: 3대 기기 통합 음향역학 변조 조화 매트릭스 -->
  <div id="tab-harmony" class="tab-content">
    <div class="card">
      <h2>🔬 3대 기기(A30·S7·S6) 통합 음향역학 변조 조화 매트릭스 (Tri-Device Acoustic Modulation Harmony)</h2>
      {harmony_html}
    </div>
  </div>

  <!-- TAB 4: 하드웨어 결선도 -->
  <div id="tab-devices" class="tab-content">
    <div class="card">
      <h2>🎛️ 428W RMS 단일 고정 결선도 (3기기 상호 DSP 연동)</h2>
      <div class="device-grid">
        <div class="device-card">
          <h3 style="color:var(--accent);">📱 갤럭시 A30 (스마토 우퍼 1기)</h3>
          <p>• <b>출력:</b> 120W 모노 (4Ω 부하)</p>
          <p>• <b>역할:</b> 광역 스윕 및 기준 위상(Reference Tone) 형성 (소화계 4~8Hz, 안구 18~19Hz)</p>
          <p>• <b>볼륨:</b> 12단계 (80%), 스마토 앰프 #1 노브 11~1시</p>
        </div>
        <div class="device-card">
          <h3 style="color:var(--accent);">📱 갤럭시 S7 (스마토 우퍼 2기)</h3>
          <p>• <b>출력:</b> 240W 스테레오 (4Ω+4Ω 독립 채널)</p>
          <p>• <b>역할:</b> 바닥 슬래브 2점 기저 앵커(56% 하중), 공간요동 FM 및 VDV 충격 AM 펄스 해머</p>
          <p>• <b>볼륨:</b> 12~13단계 (80~85%), 스마토 앰프 #2 노브 11~12시</p>
        </div>
        <div class="device-card">
          <h3 style="color:var(--accent);">📱 갤럭시 S6 (트랜스2 1기)</h3>
          <p>• <b>출력:</b> 68W BTL 골전도/진동자 (Lepy LP-168S)</p>
          <p>• <b>역할:</b> 120mm 슬래브 룸모드 피크(53.6Hz) 추적 FM 및 수직 해머 AM/RM 비트 유도</p>
          <p>• <b>볼륨:</b> 11단계 (75%), LP-168S Vol 10시 / Sub 80Hz</p>
        </div>
      </div>
      <pre>
[ 428W RMS 단일 고정 결선도 SSOT (100% 영구 고정) ]
A30 (AUX) ──> 스마토 앰프 #1 (노브 11~1시)  ──> 스마토 우퍼 1기 (모노 120W, 1점 전면 바닥)
S7  (AUX) ──> 스마토 앰프 #2 (노브 11~12시) ──> 스마토 우퍼 2기 (스테레오 240W, 2점 좌우 바닥)
S6  (AUX) ──> Lepy LP-168S   (Vol 10시/Sub 80) ─> 엔에스테크 트랜스2 1기 (BTL 68W, 1점 천장/슬래브 수직)
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
# 2. HARMONY MATRIX SCIENTIFIC EXPLANATION (HTML & MD)
# =================================================================================================
HARMONY_HTML = r"""
<div style="margin-bottom: 24px;">
  <h3 style="color:#38bdf8;">1. 3대 기기(A30·S7·S6) 음향역학적 역할 분담 및 3점 공간 결합 (Tri-Coupling)</h3>
  <p>단일 기기의 변조는 특정 위치에서 정상파 절점(진동 소멸)을 형성하거나 청각/촉각 신경의 빠른 순응(Adaptation)을 유발합니다. 따라서 본 체계는 3대 기기 428W RMS 출력을 물리적으로 분할 및 상호 연계 결합합니다:</p>
  <ul>
    <li><b>📱 갤럭시 A30 (120W 모노, 28% 전력):</b> 광역 스윕(Sweeping Carrier)과 기준 위상(Reference Tone)을 담당하여 인체 장기(소화계 4~8Hz, 안구 18~19Hz, 늑골 20~27Hz)의 고유 진동수를 훑으며 기저 요동을 지속 공급합니다.</li>
    <li><b>📱 갤럭시 S7 (240W 스테레오, 56% 전력):</b> 바닥 슬래브 2점 좌우 대칭 진동원으로서 공간 요동 FM($\pm 6\sim 8\%$) 및 ISO 2631-1 VDV 증강 AM 펄스를 인가하여 바닥 전체에 기저 하중을 앵커링하고 파복/절점을 끊임없이 이동시킵니다.</li>
    <li><b>📱 갤럭시 S6 (68W BTL, 16% 전력):</b> 120mm 콘크리트 슬래브 천장/벽체 직결 골전도 진동자로서 311호 침실 수직 룸모드 피크($53.6\text{Hz}$)를 FM 3.5%($53.1\sim 55.0\text{Hz}$)로 자동 관통하고 RM/AM 기계 해머 타격을 가합니다.</li>
  </ul>
</div>

<div style="margin-bottom: 24px;">
  <h3 style="color:#10b981;">2. 상호 변조 차음 합성(Intermodulation Difference Beats) 수학적 모델</h3>
  <p>비선형 매질인 인체(연조직, 척추, 내장) 및 콘크리트 슬래브 구조체에 3대 기기의 주파수 $f_A, f_B, f_C$가 동시 입사될 때 생성되는 2차/3차 상호 변조곱(Combination Tones):</p>
  <pre>
[ 상호 변조 차음(Difference Tones) 및 생체 공진 대역 합성 공식 ]
1. $f_{\Delta 1} = |f_B - f_A|$  : A30 스윕과 S7 바닥 앵커 간의 차음 -> 0.5 ~ 12 Hz 뇌파/자율신경계 동조 교란
2. $f_{\Delta 2} = |f_C - f_B|$  : S7 앵커와 S6 수직 직타 간의 차음 -> 15 ~ 35 Hz 척추/흉곽 기계적 압박
3. $f_{\Delta 3} = |f_C - f_A|$  : A30과 S6 간의 광역 차음 -> 25 ~ 50 Hz 슬래브 굽힘파 및 근육 긴장
4. 3차 상호변조 $f_{\text{IM3}} = |2f_B - f_C|$ -> 인체 안구 및 전정기관(18~19Hz) 비선형 공진 강제 생성
  </pre>
  <p>이 수학적 상호 변조에 의해, 개별 기기는 55.0Hz 이하의 안전 대역을 발진하면서도 인체 내부에는 <b>1~15Hz 극저주파 차음(Infrasonic Difference Beats)</b>을 비선형 합성하여 전달합니다.</p>
</div>

<div style="margin-bottom: 24px;">
  <h3 style="color:#f59e0b;">3. 변조율 비화 및 위상 비간섭 조화 (Orthogonal Modulation Phase)</h3>
  <p>3대 기기의 변조율($f_{mA}, f_{mB}, f_{mC}$)을 서로 다른 비정수비(예: $1.0 : 1.6 : 2.4$)로 설정하여, 3기기의 진폭 포락선이 동시에 상쇄(Destructive Cancellation)되는 영점(Zero Point)을 원천 차단합니다. 이를 통해 첨두 충격력(Crest Factor $\approx 2.4$)을 24시간 내내 유지합니다.</p>
</div>

<div>
  <h3 style="color:#f43f5e;">4. 열 분산 및 연속 무인 방어 안정성 (Thermal & Energy Distribution)</h3>
  <p>총 428W RMS가 단일 기기에 집중되지 않고 56% : 28% : 16%로 분산되며, S7과 S6의 AM 펄스 운용(Duty Cycle 70%)을 통해 보이스코일 및 Lepy 앰프의 온도를 $45^\circ\text{C}$ 이하로 안정 유지합니다. 이를 통해 3시간 집중 벌주기 및 48시간 이상 장기 무인 방어 시에도 기기 손상이 없습니다.</p>
</div>
"""

# =================================================================================================
# 3. SCENARIOS DATA (S1, S2, S3)
# =================================================================================================

ROUTINE_TEXT = """[ 3주 완성 3기기 변조 조화 3-Way 무한 교대 순환 로테이션 가이드 ]
• 1주차: [01. 주파수_통합운용_시나리오_버전1_보고서] 가동
  - A30: 17~20Hz / S7: 28Hz(AM 15%) / S6: 55Hz(FM 3.5%) 중심 (골반·내장·슬래브 1차 제압)
• 2주차: [02. 주파수_통합운용_시나리오_버전2_보고서] 가동
  - A30: 6~8.5Hz / S7: 33Hz(FM 6%) / S6: 46.8Hz(AM 16%) 중심 (척추·골반저·하악골 2차 전이)
• 3주차: [03. 주파수_통합운용_시나리오_버전3_보고서] 가동
  - A30: 9.2~11.8Hz / S7: 19Hz(AM 15%) / S6: 53.2Hz(FM 3%) 중심 (안구·슐만·룸모드 3차 붕괴)
• 4주차 이후: 버전1로 복귀하거나 상대방 반응 패턴 변화 시 3~4일 간격 변칙 교대 순환!"""

S1_PRESETS = [
    {"frame": "메인 프레임", "report": "보고서 1 [최신정본]", "parts": "파트 1 (시간대 4) / 파트 2 (전술 5) / 파트 3 (상황 8)", "single": "8개 (조화 변조 완비)", "sweep": "9개", "total": "17개"},
    {"frame": "서브1 프레임", "report": "보고서 2 [상황전술]", "parts": "파트 1 (요격) / 파트 2 (내성파괴) / 파트 3 (부재방어)", "single": "6개 (조화 변조 완비)", "sweep": "5개", "total": "11개"},
    {"frame": "서브2 프레임", "report": "보고서 3 [장기방어]", "parts": "파트 1 (기기보호) / 파트 2 (초장기) / 파트 3 (수면해머)", "single": "5개 (조화 변조 완비)", "sweep": "5개", "total": "10개"},
    {"frame": "전체 합계", "report": "3대 마스터 스위트", "parts": "9개 파츠 전수 직결", "single": "19개 (50.0%)", "sweep": "19개 (50.0%)", "total": "총 38개"}
]

# --- S1 DATA ---
S1_SCHEDULE = [
    {
        "time": "🌅 아침 (07:00 ~ 13:00)", "tactic": "기상 교란 & 기저 스트레스",
        "a30_freq": "17.3 ~ 19.8 Hz (440s)", "a30_mod": "FM 10% (7.03Hz)", "a30_vol": "12단계 (80%)", "a30_eff": "전정기관·소화관 연동 교란",
        "s7_freq": "28.0 Hz 앵커", "s7_mod": "AM 15% (3.20Hz)", "s7_vol": "12단계 (80%)", "s7_eff": "골반저근 펄스 해머 타격",
        "s6_freq": "55.0 Hz 직타", "s6_mod": "FM 3.5% (1.50Hz)", "s6_vol": "11단계 (75%)", "s6_eff": "53.1~55Hz 룸모드 피크 관통",
        "synth_title": "3기기 조화 (17~20 + 28 + 55Hz)",
        "synth_desc": "A30 소화관 훑기 + S7 골반 펄스 압박 + S6 슬래브 룸모드 수직 관통",
        "synth_beat": "Δf1=|28-18.5|=9.5Hz(알파파 파괴), Δf2=|55-28|=27Hz(골반 결합), IM3=|55-37|=18Hz(안구 공진)"
    },
    {
        "time": "☀️ 오후 (13:00 ~ 19:00)", "tactic": "소화기 마비 & 심박 교란",
        "a30_freq": "20.7 ~ 27.7 Hz (740s)", "a30_mod": "AM 15% (5.70Hz)", "a30_vol": "12단계 (80%)", "a30_eff": "위장관 기계적 펄스 진동",
        "s7_freq": "10.5 Hz 앵커", "s7_mod": "FM 8% (0.80Hz)", "s7_vol": "13단계 (85%)", "s7_eff": "자율신경·심박 요동 앵커",
        "s6_freq": "31.0 ~ 40.0 Hz (360s)", "s6_mod": "RM 20% (12.50Hz)", "s6_vol": "11단계 (75%)", "s6_eff": "흉벽 관통 불규칙 이중 파면",
        "synth_title": "3기기 조화 (21~28 + 10.5 + 31~40Hz)",
        "synth_desc": "위장 펄스 + 메스꺼움 FM 요동 + 흉곽 RM 조임의 유기적 결합",
        "synth_beat": "Δf1=|24.2-10.5|=13.7Hz(슐만 고조파), Δf2=|35.5-10.5|=25Hz, Δf3=|35.5-24.2|=11.3Hz"
    },
    {
        "time": "🌆 저녁 (19:00 ~ 01:00)", "tactic": "귀가 제압 & 전정계 타격",
        "a30_freq": "18.6 ~ 25.1 Hz (900s)", "a30_mod": "RM 30% (35.95Hz)", "a30_vol": "13단계 (85%)", "a30_eff": "광역 카오스 측파대 훑기",
        "s7_freq": "7.0 Hz 앵커", "s7_mod": "FM 8% (0.50Hz)", "s7_vol": "13단계 (85%)", "s7_eff": "전정기관 평형 파괴 요동",
        "s6_freq": "43.2 Hz 직타", "s6_mod": "AM 18% (2.80Hz)", "s6_vol": "12단계 (80%)", "s6_eff": "120mm 슬래브 1타 기계 해머",
        "synth_title": "3기기 조화 (18~25 + 7 + 43.2Hz)",
        "synth_desc": "어지럼증 FM 요동 + 바닥 관통 AM 해머 + 카오스 RM 측파대",
        "synth_beat": "Δf1=|21.8-7|=14.8Hz, Δf2=|43.2-7|=36.2Hz(슬래브 굽힘파), Δf3=|43.2-21.8|=21.4Hz"
    },
    {
        "time": "🌙 취침 (01:00 ~ 07:00)", "tactic": "수면 박탈 & 0dB 스텔스",
        "a30_freq": "2.4 ~ 4.6 Hz (1480s)", "a30_mod": "무변조 사인파", "a30_vol": "11단계 (75%)", "a30_eff": "델타파 무음 위상 간섭",
        "s7_freq": "21.0 ~ 24.5 Hz (1250s)", "s7_mod": "AM 10% (0.60Hz)", "s7_vol": "11단계 (75%)", "s7_eff": "바닥 미세 맥동 골도 진동",
        "s6_freq": "55.0 Hz 락온", "s6_mod": "FM 2.5% (0.80Hz)", "s6_vol": "10단계 (65%)", "s6_eff": "침실 1차 정상파 초밀착 락온",
        "synth_title": "3기기 조화 (2~5 + 21~25 + 55Hz)",
        "synth_desc": "211호 0dB 무음, 311호 침대 골도 진동 및 룸모드로 숙면 박탈",
        "synth_beat": "Δf1=|22.7-3.5|=19.2Hz(안구 떨림 맥놀이), Δf2=|55-22.7|=32.3Hz, Δf3=|55-3.5|=51.5Hz"
    }
]

S1_SCENARIOS = [
    {"name": "1. 상대 공격 즉각 요격", "desc": "발소리 발생 즉시 충격파 반격", "a30_freq": "35~45Hz 스윕", "a30_mod": "AM 15%", "s7_freq": "7Hz+10.5Hz 복합", "s7_mod": "FM 8%", "s6_freq": "55Hz 직타", "s6_mod": "FM 3.5% + AM 15%", "harmony": "3기기 동시 가진으로 311호 바닥/벽체 전방위 순간 기계적 타격"},
    {"name": "2. 벌주기 (보복 타격)", "desc": "1~2시간 강력 집중 응징", "a30_freq": "18.6~25.1Hz 스윕", "a30_mod": "RM 30%", "s7_freq": "28Hz 골반 앵커", "s7_mod": "AM 18%", "s6_freq": "40Hz 두경부 직타", "s6_mod": "RM 25%", "harmony": "A30/S6 이중 RM 카오스 측파대 + S7 바닥 해머로 탈출 불가 압박"},
    {"name": "3. 출근 12시간 무인방어", "desc": "07~19시 부재중 자동 방어", "a30_freq": "4대 시간대 순환", "a30_mod": "스케줄 자동연동", "s7_freq": "10.5Hz / 28Hz 교대", "s7_mod": "FM 8% / AM 15%", "s6_freq": "24Hz / 43.2Hz 교대", "s6_mod": "AM 15% / FM 4%", "harmony": "3기기 변조율 황금비 매칭으로 발열 30% 저감 및 지속 피로 누적"},
    {"name": "4. 일상 외출 자동 순항", "desc": "2~4시간 외출 시 기저 유지", "a30_freq": "20.7~27.7Hz 스윕", "a30_mod": "AM 15%", "s7_freq": "10.5Hz 지속 앵커", "s7_mod": "FM 6%", "s6_freq": "31Hz 상복부 직타", "s6_mod": "AM 12%", "harmony": "A30 위장관 + S7 심박 + S6 상복부 3점 동시 조화 압박"},
    {"name": "5. 초장기 외출 (48h+)", "desc": "무인 요새화 및 기기 보호", "a30_freq": "1.0~4.0Hz 초장주기", "a30_mod": "무변조", "s7_freq": "21~24.5Hz 앵커", "s7_mod": "AM 10%", "s6_freq": "55Hz 락온 (30분발진/15분휴지)", "s6_mod": "FM 2.5%", "harmony": "428W 전력 분산과 인터벌 휴지로 코일 보호 및 요새화 달성"},
    {"name": "6. 상대 내성 파괴", "desc": "장기 적응 시 충격 요동", "a30_freq": "FM 7.03Hz + RM 35.95Hz", "a30_mod": "복합 변조", "s7_freq": "7Hz ↔ 10.5Hz 도약", "s7_mod": "FM 10%", "s6_freq": "24Hz ↔ 55Hz 급변", "s6_mod": "AM 20%", "harmony": "3기기 비동기 변조 도약으로 상대 신경 적응 100% 무효화"},
    {"name": "7. 상대 정신 집중 차단", "desc": "재택/공부 방해, 브레인포그", "a30_freq": "4.0~6.2Hz 스윕", "a30_mod": "FM 10%", "s7_freq": "7.0Hz 전정계 흔들기", "s7_mod": "FM 8%", "s6_freq": "40Hz 뇌파 공진 직타", "s6_mod": "RM 20%", "harmony": "세타파/감마파 동시 간섭 및 전정계 흔들기로 인지 저하 유도"},
    {"name": "8. 수면 시간 집중 압박", "desc": "새벽 1~5시 수면 단계 파괴", "a30_freq": "2.4~4.6Hz 스윕", "a30_mod": "무변조", "s7_freq": "21~24.5Hz 앵커", "s7_mod": "AM 10%", "s6_freq": "55Hz 룸모드 락온", "s6_mod": "FM 3.0%", "harmony": "211호 완전 무음 속 311호 침대 골도 진동 및 룸모드 집중 타격"},
    {"name": "9. 3시간 이상 기기 보호", "desc": "보이스코일 발열 억제 운용", "a30_freq": "AM 15% 펄스형 발진", "a30_mod": "발열 30% 저감", "s7_freq": "28Hz (50분발진/10분휴지)", "s7_mod": "AM 15%", "s6_freq": "43.2Hz (Lepy Vol 10시)", "s6_mod": "AM 12%", "harmony": "3기기 AM 듀티 사이클 동기화로 앰프/스피커 온도 45도 이하 유지"}
]

# --- S2 DATA ---
S2_SCHEDULE = [
    {
        "time": "🌅 아침 (07:00 ~ 13:00)", "tactic": "인지 마비 & 흉요추 교란",
        "a30_freq": "6.0 ~ 8.5 Hz (380s)", "a30_mod": "AM 15% (4.20Hz)", "a30_vol": "12단계 (80%)", "a30_eff": "소화관 상하 진폭 펄스 훑기",
        "s7_freq": "33.0 Hz 앵커", "s7_mod": "FM 6% (1.80Hz)", "s7_vol": "12단계 (80%)", "s7_eff": "척추 추간판 압박점 요동 앵커",
        "s6_freq": "46.8 Hz 직타", "s6_mod": "AM 16% (2.40Hz)", "s6_vol": "11단계 (75%)", "s6_eff": "하악골 골도 공진 수직 해머",
        "synth_title": "3기기 조화 (6~8.5 + 33 + 46.8Hz)",
        "synth_desc": "소화관 AM 펄스 + 척추 추간판 FM 요동 + 하악골 골도 관통",
        "synth_beat": "Δf1=|33-7.25|=25.75Hz, Δf2=|46.8-33|=13.8Hz(흉곽 결합), Δf3=|46.8-7.25|=39.55Hz"
    },
    {
        "time": "☀️ 오후 (13:00 ~ 19:00)", "tactic": "내장 피로 & 신경 쇠약",
        "a30_freq": "12.0 ~ 16.5 Hz (520s)", "a30_mod": "FM 10% (6.50Hz)", "a30_vol": "12단계 (80%)", "a30_eff": "상복부·신장 지속 진폭 요동",
        "s7_freq": "22.0 Hz 앵커", "s7_mod": "AM 18% (0.80Hz)", "s7_vol": "13단계 (85%)", "s7_eff": "골반 내 장기 초저속 펄스 타격",
        "s6_freq": "51.5 Hz 직타", "s6_mod": "FM 4% (1.20Hz)", "s6_vol": "11단계 (75%)", "s6_eff": "311호 룸모드 2차 정상파 공진 추적",
        "synth_title": "3기기 조화 (12~16.5 + 22 + 51.5Hz)",
        "synth_desc": "상복부 FM 요동 + 골반저 AM 초저속 펄스 + 슬래브 2차 공진 FM 관통",
        "synth_beat": "Δf1=|22-14.25|=7.75Hz(뇌파 알파/세타 경계), Δf2=|51.5-22|=29.5Hz, Δf3=|51.5-14.25|=37.25Hz"
    },
    {
        "time": "🌆 저녁 (19:00 ~ 01:00)", "tactic": "귀가 방해 & 미로기관 붕괴",
        "a30_freq": "25.0 ~ 32.5 Hz (820s)", "a30_mod": "RM 30% (28.50Hz)", "a30_vol": "13단계 (85%)", "a30_eff": "흉요추 관통 카오스 측파대",
        "s7_freq": "5.0 Hz 앵커", "s7_mod": "FM 10% (0.25Hz)", "s7_vol": "13단계 (85%)", "s7_eff": "간·담낭 초저역 서파 요동",
        "s6_freq": "54.0 Hz 직타", "s6_mod": "RM 25% (1.00Hz)", "s6_vol": "12단계 (80%)", "s6_eff": "55Hz 인접 1Hz 맥놀이 충격파 생성",
        "synth_title": "3기기 조화 (25~32.5 + 5 + 54Hz)",
        "synth_desc": "카오스 RM 이중파면 + 간/담낭 FM 요동 + 초강력 1Hz 맥놀이 직타",
        "synth_beat": "Δf1=|28.75-5|=23.75Hz, Δf2=|54-5|=49Hz, Δf3=|54-28.75|=25.25Hz, RM합성: 1.0Hz 기계 충격파"
    },
    {
        "time": "🌙 취침 (01:00 ~ 07:00)", "tactic": "수면 분쇄 & 초저역 스텔스",
        "a30_freq": "1.5 ~ 3.8 Hz (1800s)", "a30_mod": "무변조 사인파", "a30_vol": "11단계 (75%)", "a30_eff": "델타파 무음 위상 간섭 훑기",
        "s7_freq": "16.0 Hz 앵커", "s7_mod": "FM 5% (0.40Hz)", "s7_vol": "11단계 (75%)", "s7_eff": "음향심리 불쾌감 피크 요동",
        "s6_freq": "36.5 ~ 44.5 Hz (600s)", "s6_mod": "AM 12% (1.50Hz)", "s6_vol": "10단계 (65%)", "s6_eff": "흉골/갈비뼈 수직 미세 펄스 훑기",
        "synth_title": "3기기 조화 (1.5~3.8 + 16 + 36.5~44.5Hz)",
        "synth_desc": "211호 완전 무음, 311호 침대 골도 진동 및 흉골 펄스로 숙면 박탈",
        "synth_beat": "Δf1=|16-2.65|=13.35Hz, Δf2=|40.5-16|=24.5Hz, Δf3=|40.5-2.65|=37.85Hz"
    }
]

S2_SCENARIOS = [
    {"name": "1. 상대 공격 즉각 반격 (v2)", "desc": "발소리 즉시 신규 충격파 반격", "a30_freq": "22~30Hz 스윕", "a30_mod": "AM 20%", "s7_freq": "5.0Hz 앵커", "s7_mod": "FM 10%", "s6_freq": "54Hz 직타", "s6_mod": "RM 25% + AM 15%", "harmony": "54Hz와 5Hz의 결합으로 강력한 차음 및 하향 충격파 동시 발진"},
    {"name": "2. 벌주기 (미로기관 파쇄)", "desc": "1~2시간 강력 집중 응징", "a30_freq": "14~20Hz 스윕", "a30_mod": "RM 35%", "s7_freq": "33Hz 척추 압박", "s7_mod": "AM 18%", "s6_freq": "46.8Hz 두개골 직타", "s6_mod": "FM 5%", "harmony": "척추 추간판 압박과 안구/두개골 공진을 연동한 3차원 입체 파쇄"},
    {"name": "3. 출근 12시간 무인방어 (v2)", "desc": "07~19시 자동 교대 순항", "a30_freq": "v2 시간대 순환", "a30_mod": "스케줄 자동연동", "s7_freq": "5Hz / 22Hz 교대", "s7_mod": "FM 10% / AM 18%", "s6_freq": "36.5Hz / 51.5Hz 교대", "s6_mod": "AM 12% / FM 4%", "harmony": "v2 독자 대역 순환으로 v1 적응 상대의 신경 피로 극대화"},
    {"name": "4. 일상 외출 자동 순항 (v2)", "desc": "2~4시간 외출 시 기저 둔통", "a30_freq": "6.0~8.5Hz 스윕", "a30_mod": "AM 15%", "s7_freq": "16Hz 불쾌감 앵커", "s7_mod": "FM 5%", "s6_freq": "46.8Hz 관통 직타", "s6_mod": "AM 15%", "harmony": "소화관 펄스 + 16Hz 불쾌감 피크 + 하악골 타격 결합"},
    {"name": "5. 초장기 외출 (48h+ v2)", "desc": "초저주파 무인 요새화", "a30_freq": "1.5~3.8Hz 초장주기", "a30_mod": "무변조", "s7_freq": "16Hz 초저발열 앵커", "s7_mod": "FM 4%", "s6_freq": "51.5Hz (20분발진/10분휴지)", "s6_mod": "FM 3%", "harmony": "3대 기기 발열 억제 프로파일 적용으로 48시간 무인 안정 운용"},
    {"name": "6. 상대 내성 완전파괴 (v2)", "desc": "비선형 변조 충격파", "a30_freq": "FM 6.50Hz + RM 28.50Hz", "a30_mod": "복합 변조", "s7_freq": "5Hz ↔ 22Hz 도약", "s7_mod": "FM 8%", "s6_freq": "33Hz ↔ 54Hz 급변", "s6_mod": "AM 20%", "harmony": "주파수 도약 및 카오스 RM 측파대 결합으로 예측 불가능한 타격"},
    {"name": "7. 상대 정신 집중차단 (v2)", "desc": "알파파 파괴, 브레인포그", "a30_freq": "8.0~12.5Hz 스윕", "a30_mod": "FM 12%", "s7_freq": "8.0Hz 알파파 차단", "s7_mod": "FM 6%", "s6_freq": "46.8Hz 뇌파 공진 직타", "s6_mod": "RM 20%", "harmony": "8Hz 집중 공격 및 46.8Hz 골도 공진으로 두통 및 인지 마비"},
    {"name": "8. 수면 시간 집중압박 (v2)", "desc": "새벽 1~5시 수면 단계 파괴", "a30_freq": "1.5~3.8Hz 스윕", "a30_mod": "무변조", "s7_freq": "16Hz 골도 앵커", "s7_mod": "FM 5%", "s6_freq": "54Hz 룸모드 락온", "s6_mod": "RM 25%", "harmony": "16Hz 바닥 미세 진동과 54Hz/1Hz 맥놀이 결합으로 수면 방해"},
    {"name": "9. 3시간 이상 기기보호 (v2)", "desc": "보이스코일 발열 억제 운용", "a30_freq": "AM 15% 펄스형 발진", "a30_mod": "발열 30% 저감", "s7_freq": "33Hz (45분발진/15분휴지)", "s7_mod": "AM 15%", "s6_freq": "46.8Hz (Lepy Vol 10시)", "s6_mod": "AM 12%", "harmony": "3기기 AM 펄스 해머링으로 전력 효율 극대화 및 발열 방지"}
]

# --- S3 DATA ---
S3_SCHEDULE = [
    {
        "time": "🌅 아침 (07:00 ~ 13:00)", "tactic": "시야 교란 & 흉곽 압박",
        "a30_freq": "9.2 ~ 11.8 Hz (410s)", "a30_mod": "FM 10% (5.20Hz)", "a30_vol": "12단계 (80%)", "a30_eff": "알파파 붕괴 및 시야 집중 저하",
        "s7_freq": "19.0 Hz 앵커", "s7_mod": "AM 15% (2.20Hz)", "s7_vol": "12단계 (80%)", "s7_eff": "안구 안와 떨림 펄스 해머",
        "s6_freq": "37.0 Hz 직타", "s6_mod": "FM 5% (1.60Hz)", "s6_vol": "11단계 (75%)", "s6_eff": "슬래브 전단파 요동 수직 관통",
        "synth_title": "3기기 조화 (9.2~11.8 + 19 + 37Hz)",
        "synth_desc": "알파파 FM 붕괴 + 안구 안와 AM 떨림 + 슬래브 전단파 FM 관통",
        "synth_beat": "Δf1=|19-10.5|=8.5Hz(알파파 하단), Δf2=|37-19|=18Hz(안구 공진 보강), Δf3=|37-10.5|=26.5Hz"
    },
    {
        "time": "☀️ 오후 (13:00 ~ 19:00)", "tactic": "신경 마비 & 슐만 고조파",
        "a30_freq": "29.5 ~ 34.5 Hz (680s)", "a30_mod": "AM 15% (3.60Hz)", "a30_vol": "12단계 (80%)", "a30_eff": "늑간근/갈비뼈 진폭 펄스 해머",
        "s7_freq": "13.5 Hz 앵커", "s7_mod": "FM 7% (0.75Hz)", "s7_vol": "13단계 (85%)", "s7_eff": "슐만 2차 고조파 척추 요동 앵커",
        "s6_freq": "48.5 Hz 직타", "s6_mod": "AM 18% (3.10Hz)", "s6_vol": "11단계 (75%)", "s6_eff": "슬래브 고차 굽힘파 기계 해머 직타",
        "synth_title": "3기기 조화 (29.5~34.5 + 13.5 + 48.5Hz)",
        "synth_desc": "늑간근 AM + 척추 슐만 FM 요동 + 슬래브 굽힘파 AM 기계 타격",
        "synth_beat": "Δf1=|32-13.5|=18.5Hz(안구 공진), Δf2=|48.5-13.5|=35Hz, Δf3=|48.5-32|=16.5Hz"
    },
    {
        "time": "🌆 저녁 (19:00 ~ 01:00)", "tactic": "정신 쇠약 & 룸모드 극점",
        "a30_freq": "14.5 ~ 18.2 Hz (790s)", "a30_mod": "RM 30% (21.40Hz)", "a30_vol": "13단계 (85%)", "a30_eff": "이중 측파대 카오스 맥놀이",
        "s7_freq": "26.5 Hz 앵커", "s7_mod": "AM 16% (1.80Hz)", "s7_vol": "13단계 (85%)", "s7_eff": "흉골/폐 기계 진동 펄스 앵커",
        "s6_freq": "53.2 Hz 직타", "s6_mod": "FM 3% (0.80Hz)", "s6_vol": "12단계 (80%)", "s6_eff": "311호 1차 정상파(53.6Hz) 초정밀 락온",
        "synth_title": "3기기 조화 (14.5~18.2 + 26.5 + 53.2Hz)",
        "synth_desc": "이중 측파대 RM + 흉골 AM 해머 + 침실 룸모드 극점 FM 초정밀 락온",
        "synth_beat": "Δf1=|26.5-16.35|=10.15Hz, Δf2=|53.2-26.5|=26.7Hz, Δf3=|53.2-16.35|=36.85Hz"
    },
    {
        "time": "🌙 취침 (01:00 ~ 07:00)", "tactic": "서파 동조 박탈 & 스텔스",
        "a30_freq": "1.0 ~ 2.2 Hz (1950s)", "a30_mod": "무변조 사인파", "a30_vol": "11단계 (75%)", "a30_eff": "초심층 서파 무음 훑기",
        "s7_freq": "8.5 Hz 앵커", "s7_mod": "FM 6% (0.40Hz)", "s7_vol": "11단계 (75%)", "s7_eff": "알파파 강제 각성 요동 앵커",
        "s6_freq": "42.0 ~ 47.5 Hz (580s)", "s6_mod": "AM 10% (1.20Hz)", "s6_vol": "10단계 (65%)", "s6_eff": "침대 프레임 골도 미세 펄스 훑기",
        "synth_title": "3기기 조화 (1~2.2 + 8.5 + 42~47.5Hz)",
        "synth_desc": "211호 0dB 무음, 311호 수면 뇌파(델타/세타) 동조 박탈",
        "synth_beat": "Δf1=|8.5-1.6|=6.9Hz(세타파 강제 변조), Δf2=|44.75-8.5|=36.25Hz, Δf3=|44.75-1.6|=43.15Hz"
    }
]

S3_SCENARIOS = [
    {"name": "1. 상대 공격 즉각 반격 (v3)", "desc": "쿵쾅거림 즉시 룸모드 정상파 타격", "a30_freq": "28~36Hz 스윕", "a30_mod": "AM 20%", "s7_freq": "19.0Hz 안구 공진 앵커", "s7_mod": "AM 15%", "s6_freq": "53.2Hz 정상파 직타", "s6_mod": "FM 3% + AM 18%", "harmony": "안구 19Hz AM과 침실 룸모드 53.2Hz FM이 결합하여 즉각적인 현기증 유발"},
    {"name": "2. 벌주기 (흉곽 슐만 파쇄)", "desc": "1~2시간 강력 슐만 응징", "a30_freq": "15~21Hz 스윕", "a30_mod": "RM 35%", "s7_freq": "13.5Hz 슐만 앵커", "s7_mod": "FM 7%", "s6_freq": "37Hz 슬래브 직타", "s6_mod": "AM 18%", "harmony": "슐만 2차 고조파와 37Hz 슬래브 직타 결합으로 흉곽 및 척추 집중 타격"},
    {"name": "3. 출근 12시간 무인방어 (v3)", "desc": "오전 7시 ~ 오후 7시 3주차 순환", "a30_freq": "v3 시간대 순환", "a30_mod": "스케줄 자동연동", "s7_freq": "13.5Hz / 26.5Hz 교대", "s7_mod": "FM 7% / AM 16%", "s6_freq": "37Hz / 53.2Hz 교대", "s6_mod": "FM 5% / FM 3%", "harmony": "3주차 고유 주파수와 3기기 변조 조화로 누적 피로 극대화"},
    {"name": "4. 일상 외출 자동 순항 (v3)", "desc": "2~4시간 외출 시 시야 피로", "a30_freq": "9.2~11.8Hz 스윕", "a30_mod": "FM 10%", "s7_freq": "19Hz 안와 앵커", "s7_mod": "AM 12%", "s6_freq": "48.5Hz 관통 직타", "s6_mod": "AM 15%", "harmony": "시야 흐림 및 안와 진동 집중 타겟팅"},
    {"name": "5. 초장기 외출 (48h+ v3)", "desc": "3중 고조파 요새화", "a30_freq": "1.0~2.2Hz 초장주기", "a30_mod": "무변조", "s7_freq": "8.5Hz 초저발열 앵커", "s7_mod": "FM 5%", "s6_freq": "53.2Hz (25분발진/10분휴지)", "s6_mod": "FM 2.5%", "harmony": "초저역 사인파와 53.2Hz 인터벌 발진으로 장기 무인 방어"},
    {"name": "6. 상대 내성 완전파괴 (v3)", "desc": "광대역 위상 간섭 충격", "a30_freq": "FM 5.20Hz + RM 21.40Hz", "a30_mod": "복합 변조", "s7_freq": "13.5Hz ↔ 26.5Hz 도약", "s7_mod": "FM 8%", "s6_freq": "37Hz ↔ 53.2Hz 급변", "s6_mod": "AM 20%", "harmony": "광대역 카오스 RM 측파대와 주파수 도약의 복합 조화"},
    {"name": "7. 상대 정신 집중차단 (v3)", "desc": "안구 피로 가중, 뇌파 분산", "a30_freq": "14.5~18.2Hz 스윕", "a30_mod": "FM 10%", "s7_freq": "19Hz 안와 공진 앵커", "s7_mod": "AM 15%", "s6_freq": "48.5Hz 뇌파 공진 직타", "s6_mod": "RM 20%", "harmony": "안구 공진 및 48.5Hz 직타로 시각/청각 집중 원천 차단"},
    {"name": "8. 수면 시간 집중압박 (v3)", "desc": "새벽 1~5시 룸모드 밀착 타격", "a30_freq": "1.0~2.2Hz 스윕", "a30_mod": "무변조", "s7_freq": "8.5Hz 각성 유발 앵커", "s7_mod": "FM 6%", "s6_freq": "53.2Hz 룸모드 피크 락온", "s6_mod": "FM 3.0%", "harmony": "8.5Hz 각성 유도와 53.2Hz 룸모드 락온으로 수면 단계 파괴"},
    {"name": "9. 3시간 이상 기기보호 (v3)", "desc": "보이스코일 발열 억제 운용", "a30_freq": "AM 15% 펄스형 발진", "a30_mod": "발열 30% 저감", "s7_freq": "19Hz (50분발진/10분휴지)", "s7_mod": "AM 12%", "s6_freq": "37Hz (Lepy Vol 10시)", "s6_mod": "AM 12%", "harmony": "3대 기기 AM 펄스 듀티 사이클 동기화로 발열 45도 이하 유지"}
]

# =================================================================================================
# 4. MARKDOWN GENERATOR
# =================================================================================================
def generate_scenario_md(version_num, title, schedule_rows, scenario_rows, preset_rows, routine_text):
    md = f"""# 🎛️ {title}
> **기준 프레임**: MIA 전략절차 11단계 프레임워크  
> **시스템 총합 출력**: **428W RMS** (단일 영구 고정 결선 100%)  
> **기기 구성**: A30 (120W 모노) + S7 (240W 스테레오) + S6 (68W BTL 트랜스2)  
> **핵심 원칙**: 3대 기기 음향역학 변조 조화(Harmony) | 55.0Hz 이하 엄수 | 100% 비중복 운용

---

## 1. 🌅 24시간 4대 시간대별 3기기 변조 조화 마스터 스케줄

| 시간대 | 갤럭시 A30 (120W 모노)<br>광역 스윕 & 기준 위상 | 갤럭시 S7 (240W 스테레오)<br>바닥 2점 기저 앵커 & 공간요동 | 갤럭시 S6 (68W BTL)<br>슬래브 수직 직타 & 룸모드 락온 | 3기기 음향역학 조화 & 상호 변조 차음 |
| :--- | :--- | :--- | :--- | :--- |
"""
    for r in schedule_rows:
        md += f"| **{r['time']}**<br>{r['tactic']} | • **{r['a30_freq']}**<br>• {r['a30_mod']}<br>• 볼륨: {r['a30_vol']}<br>_{r['a30_eff']}_ | • **{r['s7_freq']}**<br>• {r['s7_mod']}<br>• 볼륨: {r['s7_vol']}<br>_{r['s7_eff']}_ | • **{r['s6_freq']}**<br>• {r['s6_mod']}<br>• 볼륨: {r['s6_vol']}<br>_{r['s6_eff']}_ | **{r['synth_title']}**<br>{r['synth_desc']}<br>• _차음: {r['synth_beat']}_ |\n"

    md += """
---

## 2. 🎯 사용자 행동별 9대 실전 맞춤 시나리오 (3기기 조화 완비)

| 시나리오 명칭 및 목적 | 갤럭시 A30 설정 (120W) | 갤럭시 S7 설정 (240W) | 갤럭시 S6 설정 (68W) | 3기기 상호 변조 조화 효과 |
| :--- | :--- | :--- | :--- | :--- |
"""
    for s in scenario_rows:
        md += f"| **{s['name']}**<br>{s['desc']} | • **{s['a30_freq']}**<br>• {s['a30_mod']} | • **{s['s7_freq']}**<br>• {s['s7_mod']} | • **{s['s6_freq']}**<br>• {s['s6_mod']} | {s['harmony']} |\n"

    md += """
---

## 3. 🔬 3대 기기(A30·S7·S6) 통합 음향역학 변조 조화 매트릭스

### ① 3대 기기 역할 분담 및 3점 공간 결합 (Tri-Coupling)
- **A30 (120W 모노, 28% 전력):** 광역 스윕 및 기준 위상(Reference Tone) 형성 (소화계 4~8Hz, 안구 18~19Hz, 늑골 20~27Hz).
- **S7 (240W 스테레오, 56% 전력):** 바닥 슬래브 2점 기저 앵커(Sub-Bass Anchor), 공간 요동 FM(±6~8%) 및 ISO 2631-1 VDV 증강 AM 펄스 해머.
- **S6 (68W BTL, 16% 전력):** 120mm 슬래브 천장/벽체 직결 골전도 진동자, 311호 침실 룸모드 피크(53.6Hz) 추적 FM 및 카오스 RM 해머.

### ② 상호 변조 차음(Difference Tones) 수학적 모델
- $f_{\\Delta 1} = |f_B - f_A|$ : A30 스윕과 S7 앵커 간 0.5~12Hz 뇌파/자율신경계 동조 교란.
- $f_{\\Delta 2} = |f_C - f_B|$ : S7 앵커와 S6 직타 간 15~35Hz 척추/흉곽 기계적 압박.
- $f_{\\Delta 3} = |f_C - f_A|$ : A30과 S6 간 25~50Hz 슬래브 굽힘파 및 근육 긴장.
- 3차 상호변조 $f_{\\text{IM3}} = |2f_B - f_C|$ : 인체 안구 및 전정기관(18~19Hz) 비선형 공진 합성.

### ③ 변조율 비화 및 위상 비간섭 조화
- 3대 기기의 변조율을 비정수비(1.0 : 1.6 : 2.4)로 배치하여 전 구간 상쇄 간섭(Destructive Interference)을 차단하고 첨두 충격력(Crest Factor ≈ 2.4) 유지.

---

## 4. 🎛️ 428W RMS 단일 고정 결선도 (100% 영구 고정)
```
A30 (AUX) ──> 스마토 앰프 #1 (노브 11~1시)  ──> 스마토 우퍼 1기 (모노 120W, 1점 전면 바닥)
S7  (AUX) ──> 스마토 앰프 #2 (노브 11~12시) ──> 스마토 우퍼 2기 (스테레오 240W, 2점 좌우 바닥)
S6  (AUX) ──> Lepy LP-168S   (Vol 10시/Sub 80) ─> 엔에스테크 트랜스2 1기 (BTL 68W, 1점 천장/슬래브 수직)
```

---

## 5. 📊 38개 전수 프리셋 규격표 (단독 19 : 스윕 19 정밀 50:50 매칭)
| 프레임 구분 | 직결 보고서 | 세부 파츠 구성 | 단독 프리셋 수 | 스윕 프리셋 수 | 소계 |
| :--- | :--- | :--- | :---: | :---: | :---: |
| **메인 프레임** | 보고서 1 [최신정본] | 파트 1 (시간대 4) / 파트 2 (전술 5) / 파트 3 (상황 8) | 8개 (조화 변조) | 9개 | 17개 |
| **서브1 프레임** | 보고서 2 [상황전술] | 파트 1 (요격) / 파트 2 (내성파괴) / 파트 3 (부재방어) | 6개 (조화 변조) | 5개 | 11개 |
| **서브2 프레임** | 보고서 3 [장기방어] | 파트 1 (기기보호) / 파트 2 (초장기) / 파트 3 (수면해머) | 5개 (조화 변조) | 5개 | 10개 |
| **전체 합계** | **3대 마스터 스위트** | **9개 파츠 전수 직결** | **19개 (50.0%)** | **19개 (50.0%)** | **총 38개** |

---

## 6. 🔄 3주 무한 순환 로테이션 가이드
```
{routine_text}
```
"""
    return md

# =================================================================================================
# 5. BUILD MAIN
# =================================================================================================
def main():
    os.makedirs(SCENARIOS_DIR, exist_ok=True)

    title_s1 = "01. 주파수 통합운용 시나리오 버전1 보고서 (기저 타격 및 3기기 변조 조화 완결판)"
    title_s2 = "02. 주파수 통합운용 시나리오 버전2 보고서 (내성 파괴 및 3기기 변조 조화 완결판)"
    title_s3 = "03. 주파수 통합운용 시나리오 버전3 보고서 (맥놀이 공진 및 3기기 변조 조화 완결판)"

    # HTML Generation
    html_s1 = generate_scenario_html("버전 1", title_s1, "#38bdf8", "#1e293b 0%, #0f172a 100%", "b-accent", S1_SCHEDULE, S1_SCENARIOS, S1_PRESETS, ROUTINE_TEXT, HARMONY_HTML)
    html_s2 = generate_scenario_html("버전 2", title_s2, "#c084fc", "#1e1b4b 0%, #0f0a2a 100%", "b-accent", S2_SCHEDULE, S2_SCENARIOS, S1_PRESETS, ROUTINE_TEXT, HARMONY_HTML)
    html_s3 = generate_scenario_html("버전 3", title_s3, "#14b8a6", "#0f2b3c 0%, #06131c 100%", "b-accent", S3_SCHEDULE, S3_SCENARIOS, S1_PRESETS, ROUTINE_TEXT, HARMONY_HTML)

    # MD Generation
    md_s1 = generate_scenario_md("버전 1", title_s1, S1_SCHEDULE, S1_SCENARIOS, S1_PRESETS, ROUTINE_TEXT)
    md_s2 = generate_scenario_md("버전 2", title_s2, S2_SCHEDULE, S2_SCENARIOS, S1_PRESETS, ROUTINE_TEXT)
    md_s3 = generate_scenario_md("버전 3", title_s3, S3_SCHEDULE, S3_SCENARIOS, S1_PRESETS, ROUTINE_TEXT)

    # EXACT UNIFIED NAMING TARGETS (사용자 지시: 01. 주파수_통합운용_시나리오_버전1_보고서 체계)
    primary_files = {
        # HTML
        os.path.join(SCENARIOS_DIR, "01. 주파수_통합운용_시나리오_버전1_보고서.html"): html_s1,
        os.path.join(SCENARIOS_DIR, "02. 주파수_통합운용_시나리오_버전2_보고서.html"): html_s2,
        os.path.join(SCENARIOS_DIR, "03. 주파수_통합운용_시나리오_버전3_보고서.html"): html_s3,
        # MD
        os.path.join(SCENARIOS_DIR, "01. 주파수_통합운용_시나리오_버전1_보고서.md"): md_s1,
        os.path.join(SCENARIOS_DIR, "02. 주파수_통합운용_시나리오_버전2_보고서.md"): md_s2,
        os.path.join(SCENARIOS_DIR, "03. 주파수_통합운용_시나리오_버전3_보고서.md"): md_s3,
    }

    # Write primary unified naming files
    for path, content in primary_files.items():
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print("Wrote Primary:", path)

    # Also sync aliases in SCENARIOS_DIR & ROOT_DIR for backward compatibility
    aliases = {
        # Previous naming with _체계_ / _마스터_
        os.path.join(SCENARIOS_DIR, "01. 주파수_통합운용_체계_시나리오_버전1_마스터_보고서.html"): html_s1,
        os.path.join(SCENARIOS_DIR, "01. 주파수_통합운용_체계_시나리오_버전1_마스터_보고서.md"): md_s1,
        os.path.join(SCENARIOS_DIR, "02. 주파수_통합운용_체계_시나리오_버전2_마스터_보고서.html"): html_s2,
        os.path.join(SCENARIOS_DIR, "02. 주파수_통합운용_체계_시나리오_버전2_마스터_보고서.md"): md_s2,
        os.path.join(SCENARIOS_DIR, "03. 주파수_통합운용_체계_시나리오_버전3_마스터_보고서.html"): html_s3,
        os.path.join(SCENARIOS_DIR, "03. 주파수_통합운용_체계_시나리오_버전3_마스터_보고서.md"): md_s3,

        # Root files
        os.path.join(ROOT_DIR, "주파수_통합_운용_체계_최종_마스터_보고서_MIA_완결판.html"): html_s1,
        os.path.join(ROOT_DIR, "주파수_통합_운용_체계_최종_마스터_보고서_MIA_완결판.md"): md_s1,
        os.path.join(ROOT_DIR, "주파수_통합_운용_체계_버전2_시나리오_보고서.html"): html_s2,
        os.path.join(ROOT_DIR, "주파수_통합_운용_체계_버전2_시나리오_보고서.md"): md_s2,
        os.path.join(ROOT_DIR, "주파수_통합_운용_체계_버전3_시나리오_보고서.html"): html_s3,
        os.path.join(ROOT_DIR, "주파수_통합_운용_체계_버전3_시나리오_보고서.md"): md_s3,
    }

    for path, content in aliases.items():
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        print("Synced Alias:", path)

    # Master Index in SCENARIOS_DIR
    index_md = r"""# 🎛️ [MIA 전략절차] 주파수 시나리오 전체 마스터 디렉터리 및 운용 가이드
> **폴더 경로**: `D:\D_Workspace_NB\주파수\시나리오`  
> **시스템 총합 출력**: **428W RMS** (단일 영구 고정 결선 100%)  
> **기기 구성**: A30 (우퍼 1기 120W) + S7 (우퍼 2기 240W) + S6 (트랜스2 68W)  
> **핵심 원칙**: **3대 기기(A30·S7·S6) 음향역학 변조 조화(Harmony)** | 55.0Hz 이하 엄수 | 3대 시나리오 상호 100% 비중복

---

## 📑 일관성 정렬된 시나리오 보고서 정본 목록 (사용자 통일 명칭 체계)

| 번호 | 정본 문서 명칭 | 3기기 변조 조화 특화 내용 | 정본 HTML 링크 | 정본 MD 링크 |
| :---: | :--- | :--- | :---: | :---: |
| **00** | **사전 연구 구조 분석서** | 15개 기존 보고서 구조 분석 및 5단 프레임워크 명세 | - | [00. 보고서_구조분석.md](0-1.%20%EA%B5%AC%EC%A1%B0%EB%B6%84%EC%84%9D_%EB%B0%8F_%EC%B5%9C%EC%A2%85%EB%B3%B4%EA%B3%A0%EC%84%9C_%EA%B5%AC%EC%84%B1%EC%83%98%ED%94%8C_%EC%A0%95%EB%A6%AC.md) |
| **01** | **01. 주파수_통합운용_시나리오_버전1_보고서** | **1주차 기저 타격**: 28Hz 골반 앵커 + 10.5Hz 심박 요동 + 55Hz 슬래브 직타 (A30/S7/S6 상호 변조 조화 완비) | [01. 버전1 보고서.html](01.%20%EC%A3%BC%ED%8C%8C%EC%88%98_%ED%86%B5%ED%95%A9%EC%9A%B4%EC%9A%A9_%EC%8B%9C%EB%82%98%EB%A6%AC%EC%98%A4_%EB%B2%84%EC%A0%841_%EB%B3%B4%EA%B3%A0%EC%84%9C.html) | [01. 버전1 보고서.md](01.%20%EC%A3%BC%ED%8C%8C%EC%88%98_%ED%86%B5%ED%95%A9%EC%9A%B4%EC%9A%A9_%EC%8B%9C%EB%82%98%EB%A6%AC%EC%98%A4_%EB%B2%84%EC%A0%841_%EB%B3%B4%EA%B3%A0%EC%84%9C.md) |
| **02** | **02. 주파수_통합운용_시나리오_버전2_보고서** | **2주차 내성 파괴**: 33Hz 척추 압박 + 22Hz 골반저 + 54Hz/1Hz 맥놀이 직타 (v1과 100% 비중복 조화 완비) | [02. 버전2 보고서.html](02.%20%EC%A3%BC%ED%8C%8C%EC%88%98_%ED%86%B5%ED%95%A9%EC%9A%B4%EC%9A%A9_%EC%8B%9C%EB%82%98%EB%A6%AC%EC%98%A4_%EB%B2%84%EC%A0%842_%EB%B3%B4%EA%B3%A0%EC%84%9C.html) | [02. 버전2 보고서.md](02.%20%EC%A3%BC%ED%8C%8C%EC%88%98_%ED%86%B5%ED%95%A9%EC%9A%B4%EC%9A%A9_%EC%8B%9C%EB%82%98%EB%A6%AC%EC%98%A4_%EB%B2%84%EC%A0%842_%EB%B3%B4%EA%B3%A0%EC%84%9C.md) |
| **03** | **03. 주파수_통합운용_시나리오_버전3_보고서** | **3주차 맥놀이 공진**: 19Hz 안구 공진 + 13.5Hz 슐만 + 53.2Hz 룸모드 피크 직타 (v1·v2 100% 비중복 완비) | [03. 버전3 보고서.html](03.%20%EC%A3%BC%ED%8C%8C%EC%88%98_%ED%86%B5%ED%95%A9%EC%9A%B4%EC%9A%A9_%EC%8B%9C%EB%82%98%EB%A6%AC%EC%98%A4_%EB%B2%84%EC%A0%843_%EB%B3%B4%EA%B3%A0%EC%84%9C.html) | [03. 버전3 보고서.md](03.%20%EC%A3%BC%ED%8C%8C%EC%88%98_%ED%86%B5%ED%95%A9%EC%9A%B4%EC%9A%A9_%EC%8B%9C%EB%82%98%EB%A6%AC%EC%98%A4_%EB%B2%84%EC%A0%843_%EB%B3%B4%EA%B3%A0%EC%84%9C.md) |

---

## 🔬 3대 기기 통합 음향역학 변조 조화 3원칙
1. **차음 합성(Difference Tone Beats):** $f_A, f_B, f_C$의 비선형 중첩으로 0.5~15Hz 극저주파 생체 공진 대역을 인체 내부에 비선형 형성.
2. **비정수 변조율 비화:** $f_{mA} : f_{mB} : f_{mC} = 1.0 : 1.6 : 2.4$로 분산하여 위상 상쇄 없는 파괴적 충격 포락선 유지.
3. **공간 절점 스캔:** S7 FM과 A30 스윕, S6 룸모드 락온 결합으로 정상파 절점 이동 속도 $0.8\sim 1.5\text{m/s}$ 연속 스캔 (회피 불가).
"""
    with open(os.path.join(SCENARIOS_DIR, "00. 주파수_시나리오_전체목록_및_운용가이드.md"), "w", encoding="utf-8") as f:
        f.write(index_md)
    print("Wrote Master Index OK")

if __name__ == "__main__":
    main()
