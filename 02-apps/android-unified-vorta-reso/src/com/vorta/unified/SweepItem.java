package com.vorta.unified;

/**
 * Multi-sweep or dual-sine item for the Unified Pro scheduler workspace.
 */
public class SweepItem {
    public int id;
    public String title;
    public float startHz;
    public float endHz;
    public float durationSec;
    public float secondaryHz; // > 0f activates distortionless linear dual-sine beating (f1 + f2)
    public boolean isLoop;
    public volatile boolean isPlaying;
    public volatile float currentHz;

    public double sweepPos = 0.0;
    public double phase = 0.0;
    public double phase2 = 0.0;

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
    }
}
