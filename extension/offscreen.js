let otpSse = null;
let deviceSse = null;

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.target !== 'offscreen') {
        return false;
    }

    if (message.type === 'ping-sse') {
        const isAlive = (otpSse && otpSse.readyState !== EventSource.CLOSED) || (deviceSse && deviceSse.readyState !== EventSource.CLOSED);
        sendResponse({ alive: isAlive });
        return true;
    }

    if (message.type === 'start-sse') {
        const otpUrl = `https://mailsync-osb-default-rtdb.asia-southeast1.firebasedatabase.app/otps/${message.uuid}.json`;
        const deviceUrl = `https://mailsync-osb-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${message.uuid}.json`;

        // 1. Setup OTP SSE
        if (otpSse && otpSse.url !== otpUrl) { otpSse.close(); otpSse = null; }
        if (!otpSse) {
            otpSse = new EventSource(otpUrl);
            let otpSseReady = false; // Skip the first (initial snapshot) put event to avoid false triggers
            otpSse.addEventListener('put', (e) => {
                if (!otpSseReady) {
                    // The very first 'put' is Firebase's initial data snapshot — parse it but only
                    // notify if there is ALREADY an OTP waiting (e.g. after service worker restart)
                    otpSseReady = true;
                    try {
                        const parsed = JSON.parse(e.data);
                        // parsed.data is the OTP object or null
                        if (parsed && parsed.data && parsed.data.iv && parsed.data.data) {
                            notifyBg('sse_otp');
                        }
                    } catch (_) {}
                    return;
                }
                try {
                    const parsed = JSON.parse(e.data);
                    // Only trigger if this is a real OTP write (not a deletion/null)
                    if (parsed && parsed.data && parsed.data.iv && parsed.data.data) {
                        notifyBg('sse_otp');
                    }
                } catch (_) {}
            });
            otpSse.addEventListener('patch', (e) => {
                try {
                    const parsed = JSON.parse(e.data);
                    if (parsed && parsed.data && (parsed.data.iv || parsed.data.data)) {
                        notifyBg('sse_otp');
                    }
                } catch (_) {}
            });
            otpSse.onerror = () => {};
        }

        // 2. Setup Device Status SSE
        if (deviceSse && deviceSse.url !== deviceUrl) { deviceSse.close(); deviceSse = null; }
        if (!deviceSse) {
            deviceSse = new EventSource(deviceUrl);
            let deviceSseReady = false;
            deviceSse.addEventListener('put', (e) => {
                if (!deviceSseReady) { deviceSseReady = true; return; } // Skip initial snapshot
                if (e.data) notifyBg('sse_device');
            });
            deviceSse.addEventListener('patch', (e) => {
                if (e.data) notifyBg('sse_device');
            });
            deviceSse.onerror = () => {};
        }

        sendResponse({ success: true });
        return true;
    }

    if (message.type === 'play-ting') {
        playIphoneTing();
        sendResponse({ success: true });
        return true;
    }

    if (message.type === 'copy-data-to-clipboard') {
        const textToCopy = message.data;
        
        // Play iPhone "ting" glass chime for genuine OTP codes (not clipboard clear spaces)
        if (textToCopy && textToCopy.trim().length > 0) {
            playIphoneTing();
        }

        // Modern approach first (supported in offscreen in MV3)
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(textToCopy)
                .then(() => {
                    chrome.runtime.sendMessage({ target: 'background', type: 'copy-complete' }).catch(() => {});
                    sendResponse({ success: true });
                })
                .catch(() => {
                    // Fallback to execCommand if modern fails
                    const textEl = document.getElementById('copy-area');
                    textEl.value = textToCopy;
                    textEl.focus();
                    textEl.select();
                    textEl.setSelectionRange(0, 99999);
                    document.execCommand('copy');
                    chrome.runtime.sendMessage({ target: 'background', type: 'copy-complete' }).catch(() => {});
                    sendResponse({ success: true });
                });
            return true; // async response
        } else {
            const textEl = document.getElementById('copy-area');
            textEl.value = textToCopy;
            textEl.focus();
            textEl.select();
            textEl.setSelectionRange(0, 99999);
            document.execCommand('copy');

            chrome.runtime.sendMessage({ target: 'background', type: 'copy-complete' }).catch(() => {});
            sendResponse({ success: true });
            return true;
        }
    }
});

// ─── iPhone "Ting" Crystal Glass Chime Synthesizer ─────────────────────────────
// Generates the iconic, studio-grade iOS notification ping using Web Audio API.
function playIphoneTing() {
    try {
        const AudioCtx = window.AudioContext || window.webkitAudioContext;
        if (!AudioCtx) return;
        const ctx = new AudioCtx();
        if (ctx.state === 'suspended') {
            ctx.resume();
        }

        const now = ctx.currentTime;

        const playTone = (freq, startTime, duration, peakGain) => {
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();

            osc.type = 'sine';
            osc.frequency.setValueAtTime(freq, startTime);

            // Natural glass bell envelope: fast attack (2ms), exponential decay
            gain.gain.setValueAtTime(0.0001, startTime);
            gain.gain.exponentialRampToValueAtTime(peakGain, startTime + 0.003);
            gain.gain.exponentialRampToValueAtTime(0.0001, startTime + duration);

            osc.connect(gain);
            gain.connect(ctx.destination);

            osc.start(startTime);
            osc.stop(startTime + duration);
        };

        // Note 1: First crystal ping (1568 Hz - G6)
        playTone(1567.98, now, 0.40, 0.28);
        playTone(3135.96, now, 0.20, 0.08);

        // Note 2: Main bright, iconic iPhone "Ting" chime (2093 Hz - C7)
        playTone(2093.00, now + 0.055, 0.65, 0.38);
        playTone(4186.01, now + 0.055, 0.35, 0.14);
        playTone(5232.51, now + 0.055, 0.20, 0.06);
    } catch (e) {
        console.warn('Audio playback error:', e);
    }
}

function notifyBg(action) {
    chrome.runtime.sendMessage({ target: 'background', action: action }).catch(() => {});
}

window.addEventListener('online', () => {
    chrome.runtime.sendMessage({ action: 'force_icon', state: 'active' }).catch(() => {});
});
window.addEventListener('offline', () => {
    chrome.runtime.sendMessage({ action: 'force_icon', state: 'warning' }).catch(() => {});
});
