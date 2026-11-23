package com.vorta.freqgen;

/**
 * Sweep / dual-sine preset entry with continuous runtime oscillator state.
 */
public class SweepItem {
    public int id;
    public String title;
    public float startHz;
    public float endHz;
    public float durationSec;
    public float secondaryHz; // > 0f enables pure linear dual-sine acoustic beating (f1 + f2)
    public boolean isLoop;
    public volatile boolean isPlaying;
    public volatile float currentHz;
    public int waveform; // 0: PURE_SINE

    // Synthesis phase states for phase-continuous zero-click waveform output
    public double sweepPos = 0.0; // 0.0 to 2.0 (triangle sweep)
    public double phase = 0.0;    // 0.0 to 2*PI (primary carrier)
    public double phase2 = 0.0;   // 0.0 to 2*PI (optional secondary pure carrier)

    public SweepItem(int id, String title, float startHz, float endHz, float durationSec, boolean isLoop) {
        this(id, title, startHz, endHz, durationSec, isLoop, 0.0f);
    }

    public SweepItem(int id, String title, float startHz, float endHz, float durationSec, boolean isLoop, float secondaryHz) {
        this.id = id;
        this.title = title;
        this.startHz = startHz;
        this.endHz = endHz;
        this.durationSec = durationSec;
        this.isLoop = isLoop;
        this.secondaryHz = secondaryHz;
        this.isPlaying = false;
        this.currentHz = startHz;
        this.waveform = 0;
    }
}
