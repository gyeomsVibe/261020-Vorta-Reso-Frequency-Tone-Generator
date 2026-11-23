/**
 * AudioWorkletProcessor running on Chromium/Edge's real-time audio render thread
 * (WASAPI Shared Mode on Windows -> concurrent mixing with all music apps).
 */
import { AegisDspEngine } from "./dsp-core.js";

class VortaAegisProcessor extends AudioWorkletProcessor {
    constructor() {
        super();
        this.engine = new AegisDspEngine(sampleRate);
        this.blockCounter = 0;
        this.wasActive = false;

        this.port.onmessage = (event) => {
            const msg = event.data;
            if (!msg || !msg.type) return;
            switch (msg.type) {
                case "play":
                    if (typeof msg.volumePercent === "number" || typeof msg.muted === "boolean") {
                        this.engine.setVolume(
                            msg.volumePercent ?? this.engine.volumePercent,
                            msg.muted ?? this.engine.isMuted
                        );
                    }
                    if (typeof msg.ultrasonicGuard === "boolean") {
                        this.engine.setUltrasonicGuard(msg.ultrasonicGuard);
                    }
                    this.engine.setPreset(msg.preset);
                    break;
                case "stop":
                    this.engine.requestStop();
                    break;
                case "volume":
                    this.engine.setVolume(msg.volumePercent, msg.muted);
                    break;
                case "guard":
                    this.engine.setUltrasonicGuard(msg.enabled);
                    break;
                default:
                    break;
            }
        };
    }

    process(_inputs, outputs) {
        const output = outputs[0];
        if (!output || output.length === 0) return true;

        const outL = output[0];
        const outR = output.length > 1 ? output[1] : null;
        const status = this.engine.renderBlock(outL, outR);

        this.blockCounter++;
        // ~120ms telemetry cadence (48000 / 128 = 375 blocks/sec -> every 45 blocks)
        if (this.wasActive && !status.active) {
            this.wasActive = false;
            this.port.postMessage({ type: "stopped", sampleRate });
        } else if (status.active && (!this.wasActive || (this.blockCounter % 45 === 0))) {
            this.wasActive = true;
            this.port.postMessage({
                type: "telemetry",
                active: true,
                liveHz: status.liveHz,
                ultrasonicGuarded: status.ultrasonicGuarded,
                sampleRate
            });
        }

        return true;
    }
}

registerProcessor("vorta-aegis-processor", VortaAegisProcessor);
