import {
    VolumeCurve,
    TAB_NAMES,
    TAB_SHORT_NAMES,
    TabSyncPolicy,
    buildPresetSpecLine
} from "./policy.js";

const DETENT_VALUES = [20, 45, 75, 100];
const DETENT_LABELS = ["20% 배경", "45% 병행", "75% 단독", "100% 최대"];
const DEFAULT_MECHANISM =
    "• 위상 연속 정현파 무왜곡 출력 대기\n• 다른 오디오·작업 앱과 간섭 없이 동시 재생됩니다";

const state = {
    presets: [],
    selectedTab: 0,
    isPlaying: false,
    activePreset: null,
    lastPresetId: 1,
    liveHz: 0,
    ultrasonicGuarded: false,
    ultrasonicGuardEnabled: true,
    volumePercent: VolumeCurve.DEFAULT_PERCENT,
    muted: false,
    audioCtx: null,
    workletNode: null,
    audioReadyPromise: null
};

const el = {
    hwSubline: document.getElementById("hwSubline"),
    btnUltrasonicGuard: document.getElementById("btnUltrasonicGuard"),
    consoleJumpArea: document.getElementById("consoleJumpArea"),
    statusLed: document.getElementById("statusLed"),
    statusText: document.getElementById("statusText"),
    heroFreq: document.getElementById("heroFreq"),
    heroSubBadge: document.getElementById("heroSubBadge"),
    scenarioTitle: document.getElementById("scenarioTitle"),
    jumpCueBtn: document.getElementById("jumpCueBtn"),
    mechanismInfo: document.getElementById("mechanismInfo"),
    volumeReadout: document.getElementById("volumeReadout"),
    btnMute: document.getElementById("btnMute"),
    iconVolOn: document.getElementById("iconVolOn"),
    iconVolMute: document.getElementById("iconVolMute"),
    btnMinus: document.getElementById("btnMinus"),
    seekVolume: document.getElementById("seekVolume"),
    btnPlus: document.getElementById("btnPlus"),
    detentRow: document.getElementById("detentRow"),
    btnStop: document.getElementById("btnStop"),
    tabScroll: document.getElementById("tabScroll"),
    presetList: document.getElementById("presetList")
};

let activeCardEl = null;
let tabButtons = [];
let detentButtons = [];
let eventSource = null;

async function persistStatePatch(patch) {
    try {
        await fetch("/api/state", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(patch)
        });
    } catch (_) {
        // Fallback already buffered in UI state
    }
    try {
        localStorage.setItem("vorta_aegis_pc_state", JSON.stringify({
            volumePercent: state.volumePercent,
            muted: state.muted,
            lastTab: state.selectedTab,
            lastPresetId: state.lastPresetId,
            ultrasonicGuard: state.ultrasonicGuardEnabled
        }));
    } catch (_) {}
}

async function loadInitialState() {
    let loaded = null;
    try {
        const res = await fetch("/api/state");
        if (res.ok) loaded = await res.json();
    } catch (_) {}
    if (!loaded) {
        try {
            const raw = localStorage.getItem("vorta_aegis_pc_state");
            if (raw) loaded = JSON.parse(raw);
        } catch (_) {}
    }
    if (loaded) {
        state.volumePercent = VolumeCurve.clampPercent(loaded.volumePercent ?? VolumeCurve.DEFAULT_PERCENT);
        state.muted = Boolean(loaded.muted);
        state.selectedTab = TabSyncPolicy.clampTabIndex(loaded.lastTab ?? 0);
        state.lastPresetId = Number.isInteger(loaded.lastPresetId) ? loaded.lastPresetId : 1;
        state.ultrasonicGuardEnabled = loaded.ultrasonicGuard !== false;
    }
}

async function ensureAudioEngine() {
    if (state.audioReadyPromise) {
        await state.audioReadyPromise;
        if (state.audioCtx && state.audioCtx.state === "suspended") {
            await state.audioCtx.resume();
        }
        return state.workletNode;
    }

    state.audioReadyPromise = (async () => {
        const Ctx = window.AudioContext || window.webkitAudioContext;
        const ctx = new Ctx({ latencyHint: "playback" });
        await ctx.audioWorklet.addModule("/aegis-worklet.js");
        const node = new AudioWorkletNode(ctx, "vorta-aegis-processor", {
            numberOfInputs: 0,
            numberOfOutputs: 1,
            outputChannelCount: [2]
        });
        node.connect(ctx.destination);
        node.port.onmessage = (evt) => {
            const msg = evt.data;
            if (!msg || !msg.type) return;
            if (msg.sampleRate) {
                el.hwSubline.textContent = `Windows WASAPI · ${(msg.sampleRate / 1000).toFixed(1)}kHz Stereo · -1.0 dBTP`;
            }
            if (msg.type === "telemetry" && state.isPlaying) {
                state.liveHz = msg.liveHz;
                state.ultrasonicGuarded = Boolean(msg.ultrasonicGuarded);
                updateHeroFreqViews(state.liveHz, state.activePreset, true);
                updateMechanismText();
            } else if (msg.type === "stopped") {
                if (!state.isPlaying) {
                    state.liveHz = 0;
                    state.ultrasonicGuarded = false;
                }
            }
        };

        state.audioCtx = ctx;
        state.workletNode = node;
        el.hwSubline.textContent = `Windows WASAPI · ${(ctx.sampleRate / 1000).toFixed(1)}kHz Stereo · -1.0 dBTP`;
        if (ctx.state === "suspended") {
            await ctx.resume();
        }
        return node;
    })();

    return state.audioReadyPromise;
}

function buildDetents() {
    el.detentRow.innerHTML = "";
    detentButtons = DETENT_VALUES.map((val, idx) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "detent-chip";
        btn.textContent = DETENT_LABELS[idx];
        btn.addEventListener("click", () => applyVolume(val, false));
        el.detentRow.appendChild(btn);
        return btn;
    });
}

function buildTabs() {
    el.tabScroll.innerHTML = "";
    tabButtons = TAB_NAMES.map((name, idx) => {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "tab-btn";
        btn.setAttribute("role", "tab");
        btn.addEventListener("click", () => selectTab(idx, true, true));
        el.tabScroll.appendChild(btn);
        return btn;
    });
}

function refreshTabStyles() {
    const playingTab = (state.isPlaying && state.activePreset) ? state.activePreset.tabIndex : -1;
    tabButtons.forEach((btn, idx) => {
        const selected = idx === state.selectedTab;
        const isOwner = idx === playingTab;
        btn.classList.toggle("selected", selected);
        btn.classList.toggle("playing-owner", isOwner);
        btn.setAttribute("aria-selected", selected ? "true" : "false");
        btn.innerHTML = isOwner
            ? `<span class="tab-dot">●</span><span>${TAB_NAMES[idx]}</span>`
            : `<span>${TAB_NAMES[idx]}</span>`;
    });
}

function scrollTabIntoCenter(idx, smooth) {
    const btn = tabButtons[idx];
    if (!btn) return;
    requestAnimationFrame(() => {
        btn.scrollIntoView({
            inline: "center",
            block: "nearest",
            behavior: smooth ? "smooth" : "auto"
        });
    });
}

function renderPresetCards() {
    el.presetList.innerHTML = "";
    activeCardEl = null;
    const activeId = (state.isPlaying && state.activePreset) ? state.activePreset.id : -1;

    for (const preset of state.presets) {
        if (preset.tabIndex !== state.selectedTab) continue;
        const isActive = preset.id === activeId;

        const card = document.createElement("article");
        card.className = "preset-card" + (isActive ? " active" : "");

        const eyebrow = document.createElement("div");
        eyebrow.className = "card-eyebrow";
        const badge = document.createElement("span");
        badge.className = "card-badge";
        badge.textContent = `${preset.shortCode} · ${preset.badge}`;
        eyebrow.appendChild(badge);
        if (isActive) {
            const flag = document.createElement("span");
            flag.className = "card-playing-flag";
            flag.textContent = "재생 중";
            eyebrow.appendChild(flag);
        }
        card.appendChild(eyebrow);

        const title = document.createElement("div");
        title.className = "card-title";
        title.textContent = preset.title;
        card.appendChild(title);

        const spec = document.createElement("div");
        spec.className = "card-spec";
        spec.textContent = buildPresetSpecLine(preset);
        card.appendChild(spec);

        const desc = document.createElement("div");
        desc.className = "card-desc";
        desc.textContent = preset.desc;
        card.appendChild(desc);

        card.addEventListener("click", () => playPreset(preset));
        if (isActive) {
            activeCardEl = card;
        }
        el.presetList.appendChild(card);
    }
}

function selectTab(tabIndex, smoothScroll = true, persistIfIdle = false) {
    state.selectedTab = TabSyncPolicy.clampTabIndex(tabIndex);
    if (persistIfIdle && !state.isPlaying) {
        persistStatePatch({ lastTab: state.selectedTab });
    }
    refreshTabStyles();
    renderPresetCards();
    scrollTabIntoCenter(state.selectedTab, smoothScroll);
    updateJumpCue();
    updateGuardChip();
}

function updateHeroFreqViews(liveHz, preset, isRunning) {
    if (!isRunning || !preset) {
        el.heroFreq.textContent = "0.00 Hz";
        el.heroFreq.classList.add("idle");
        el.heroSubBadge.classList.add("hidden");
        el.heroSubBadge.textContent = "";
        return;
    }

    el.heroFreq.textContent = liveHz >= 1000
        ? `${liveHz.toFixed(1)} Hz`
        : `${liveHz.toFixed(2)} Hz`;
    el.heroFreq.classList.remove("idle");

    let badge = "";
    if (preset.binauralBeat > 0) {
        badge = `Δ ${preset.binauralBeat.toFixed(2)} Hz`;
    } else if (preset.isSweep) {
        badge = "SWEEP";
    } else if (preset.freq2 > 0) {
        badge = `+ ${preset.freq2.toFixed(1)} Hz`;
    } else if (preset.fmMod) {
        badge = "FM";
    } else if (preset.amMod) {
        badge = "AM";
    }

    if (badge) {
        el.heroSubBadge.textContent = badge;
        el.heroSubBadge.classList.remove("hidden");
    } else {
        el.heroSubBadge.classList.add("hidden");
    }
}

function updateMechanismText() {
    if (!state.isPlaying || !state.activePreset) {
        el.mechanismInfo.textContent = DEFAULT_MECHANISM;
        return;
    }
    if (state.ultrasonicGuarded) {
        el.mechanismInfo.textContent = `• 이어폰/헤드폰 보호 모드 — 초음파 안전 감쇄(-12dB) 적용 중\n${state.activePreset.desc}`;
    } else {
        el.mechanismInfo.textContent = state.activePreset.desc;
    }
}

function updateJumpCue() {
    if (!state.isPlaying || !state.activePreset) {
        el.jumpCueBtn.classList.add("hidden");
        return;
    }
    el.jumpCueBtn.classList.remove("hidden");
    const diff = TabSyncPolicy.isViewingDifferentTab(true, state.activePreset.tabIndex, state.selectedTab);
    if (diff) {
        el.jumpCueBtn.textContent = "재생 탭 ›";
        el.jumpCueBtn.classList.remove("same-tab");
    } else {
        el.jumpCueBtn.textContent = "위치 보기 ›";
        el.jumpCueBtn.classList.add("same-tab");
    }
}

function updateGuardChip() {
    const show = state.selectedTab === 7 || (state.isPlaying && state.activePreset && state.activePreset.noiseType === 2);
    el.btnUltrasonicGuard.classList.toggle("hidden", !show);
    el.btnUltrasonicGuard.classList.toggle("off", !state.ultrasonicGuardEnabled);
    el.btnUltrasonicGuard.textContent = state.ultrasonicGuardEnabled
        ? "초음파 보호 -12dB ON"
        : "초음파 보호 OFF (스피커용)";
}

function syncVolumeUi() {
    const effectiveSlider = state.muted ? 0 : state.volumePercent;
    el.seekVolume.value = String(effectiveSlider);

    const dbStr = VolumeCurve.formatDb(state.volumePercent, state.muted);
    if (state.muted) {
        el.volumeReadout.textContent = `음소거 · ${dbStr}`;
        el.volumeReadout.classList.add("muted");
    } else {
        el.volumeReadout.textContent = `${state.volumePercent}% · ${dbStr}`;
        el.volumeReadout.classList.remove("muted");
    }

    const mutedVisual = state.muted || state.volumePercent === 0;
    el.iconVolOn.classList.toggle("hidden", mutedVisual);
    el.iconVolMute.classList.toggle("hidden", !mutedVisual);

    detentButtons.forEach((btn, idx) => {
        const active = !state.muted && state.volumePercent === DETENT_VALUES[idx];
        btn.classList.toggle("active", active);
    });
}

function updateDashboard() {
    if (state.isPlaying && state.activePreset) {
        el.statusLed.className = "led led-running";
        el.statusText.textContent = `재생 중 · ${TAB_SHORT_NAMES[state.activePreset.tabIndex] || "이지스"}`;
        el.statusText.classList.add("running");

        const displayHz = state.liveHz > 0
            ? state.liveHz
            : (state.activePreset.isSweep ? state.activePreset.sweepStart : state.activePreset.freq1);
        updateHeroFreqViews(displayHz, state.activePreset, true);
        el.scenarioTitle.textContent = state.activePreset.title;

        el.btnStop.textContent = "재생 중지";
        el.btnStop.className = "stop-btn running";
        el.btnStop.disabled = false;
    } else {
        el.statusLed.className = "led led-stopped";
        el.statusText.textContent = "대기 중";
        el.statusText.classList.remove("running");

        updateHeroFreqViews(0, null, false);
        el.scenarioTitle.textContent = "프리셋을 선택하면 즉시 재생됩니다";

        el.btnStop.textContent = "대기 중 (프리셋 선택 시 재생)";
        el.btnStop.className = "stop-btn idle";
        el.btnStop.disabled = true;
    }

    updateMechanismText();
    refreshTabStyles();
    renderPresetCards();
    updateJumpCue();
    updateGuardChip();
}

function applyVolume(percent, muted) {
    state.volumePercent = VolumeCurve.clampPercent(percent);
    state.muted = Boolean(muted);
    syncVolumeUi();
    persistStatePatch({ volumePercent: state.volumePercent, muted: state.muted });

    if (state.workletNode) {
        state.workletNode.port.postMessage({
            type: "volume",
            volumePercent: state.volumePercent,
            muted: state.muted
        });
    }
}

async function playPreset(preset) {
    if (!preset) return;
    state.activePreset = preset;
    state.lastPresetId = preset.id;
    state.isPlaying = true;
    state.liveHz = preset.isSweep ? preset.sweepStart : preset.freq1;
    state.ultrasonicGuarded = (preset.noiseType === 2) && state.ultrasonicGuardEnabled;

    if (state.selectedTab !== preset.tabIndex) {
        state.selectedTab = TabSyncPolicy.clampTabIndex(preset.tabIndex);
        scrollTabIntoCenter(state.selectedTab, true);
    }

    persistStatePatch({ lastTab: preset.tabIndex, lastPresetId: preset.id });
    updateDashboard();

    const node = await ensureAudioEngine();
    node.port.postMessage({
        type: "play",
        preset,
        volumePercent: state.volumePercent,
        muted: state.muted,
        ultrasonicGuard: state.ultrasonicGuardEnabled
    });

    if ("mediaSession" in navigator) {
        try {
            navigator.mediaSession.metadata = new MediaMetadata({
                title: preset.title,
                artist: "보르타 이지스 Pro v2.3 PC",
                album: TAB_SHORT_NAMES[preset.tabIndex] || "이지스"
            });
            navigator.mediaSession.playbackState = "playing";
        } catch (_) {}
    }
}

function stopPlayback() {
    state.isPlaying = false;
    state.liveHz = 0;
    state.ultrasonicGuarded = false;
    state.activePreset = null;
    if (state.workletNode) {
        state.workletNode.port.postMessage({ type: "stop" });
    }
    if ("mediaSession" in navigator) {
        try {
            navigator.mediaSession.playbackState = "paused";
        } catch (_) {}
    }
    updateDashboard();
}

function jumpToPlayingPreset() {
    if (!state.isPlaying || !state.activePreset) return;
    const targetTab = TabSyncPolicy.clampTabIndex(state.activePreset.tabIndex);
    if (state.selectedTab !== targetTab) {
        selectTab(targetTab, true, false);
    } else {
        scrollTabIntoCenter(targetTab, true);
    }
    requestAnimationFrame(() => {
        if (activeCardEl) {
            activeCardEl.scrollIntoView({ behavior: "smooth", block: "nearest" });
        }
    });
}

function bindEvents() {
    el.seekVolume.addEventListener("input", () => {
        const val = Number(el.seekVolume.value);
        applyVolume(val, val === 0);
    });

    el.btnMute.addEventListener("click", () => {
        if (state.muted) {
            const restored = state.volumePercent === 0 ? VolumeCurve.DEFAULT_PERCENT : state.volumePercent;
            applyVolume(restored, false);
        } else {
            applyVolume(state.volumePercent, true);
        }
    });

    el.btnMinus.addEventListener("click", () => {
        const next = VolumeCurve.clampPercent(state.volumePercent - 5);
        applyVolume(next, next === 0);
    });

    el.btnPlus.addEventListener("click", () => {
        const next = VolumeCurve.clampPercent(state.volumePercent + 5);
        applyVolume(next, next === 0);
    });

    el.btnStop.addEventListener("click", stopPlayback);

    el.jumpCueBtn.addEventListener("click", (evt) => {
        evt.stopPropagation();
        jumpToPlayingPreset();
    });

    el.consoleJumpArea.addEventListener("click", () => {
        if (state.isPlaying && state.activePreset) {
            jumpToPlayingPreset();
        }
    });

    el.btnUltrasonicGuard.addEventListener("click", () => {
        state.ultrasonicGuardEnabled = !state.ultrasonicGuardEnabled;
        if (state.isPlaying && state.activePreset) {
            state.ultrasonicGuarded = (state.activePreset.noiseType === 2) && state.ultrasonicGuardEnabled;
        }
        persistStatePatch({ ultrasonicGuard: state.ultrasonicGuardEnabled });
        if (state.workletNode) {
            state.workletNode.port.postMessage({
                type: "guard",
                enabled: state.ultrasonicGuardEnabled
            });
        }
        updateGuardChip();
        updateMechanismText();
    });

    document.addEventListener("keydown", (evt) => {
        if (evt.code === "Space" && evt.target.tagName !== "BUTTON" && evt.target.tagName !== "INPUT") {
            evt.preventDefault();
            if (state.isPlaying) {
                stopPlayback();
            } else {
                const preset = state.presets.find((p) => p.id === state.lastPresetId) || state.presets[0];
                if (preset) playPreset(preset);
            }
        } else if (evt.key === "m" || evt.key === "M") {
            el.btnMute.click();
        } else if (evt.key === "ArrowUp") {
            evt.preventDefault();
            el.btnPlus.click();
        } else if (evt.key === "ArrowDown") {
            evt.preventDefault();
            el.btnMinus.click();
        } else if (evt.key >= "1" && evt.key <= "8") {
            selectTab(Number(evt.key) - 1, true, true);
        }
    });
}

function connectLifecycleEvents() {
    if (eventSource) return;
    eventSource = new EventSource("/api/events");
    window.addEventListener("pagehide", () => {
        if (eventSource) eventSource.close();
    });
}

async function init() {
    buildDetents();
    buildTabs();
    bindEvents();
    connectLifecycleEvents();

    const [presetsRes] = await Promise.all([
        fetch("/presets.json"),
        loadInitialState()
    ]);
    state.presets = await presetsRes.json();

    syncVolumeUi();
    updateDashboard();
    selectTab(state.selectedTab, false, false);
}

init();
