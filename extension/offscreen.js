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
        const uuid = message.uuid;
        const otpUrl = `https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app/otps/${uuid}.json`;
        const deviceUrl = `https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${uuid}.json`;

        function setupOtp() {
            if (otpSse) {
                try { otpSse.close(); } catch(_) {}
                otpSse = null;
            }
            otpSse = new EventSource(otpUrl);
            let otpSseReady = false;
            otpSse.addEventListener('put', (e) => {
                if (!otpSseReady) {
                    otpSseReady = true;
                    try {
                        const parsed = JSON.parse(e.data);
                        if (parsed && parsed.data && parsed.data.iv && parsed.data.data) {
                            notifyBg('sse_otp');
                        }
                    } catch (_) {}
                    return;
                }
                try {
                    const parsed = JSON.parse(e.data);
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
            otpSse.onerror = () => {
                if (otpSse && otpSse.readyState === EventSource.CLOSED) {
                    try { otpSse.close(); } catch(_) {}
                    otpSse = null;
                    setTimeout(setupOtp, 2500);
                }
            };
        }

        function setupDevice() {
            if (deviceSse) {
                try { deviceSse.close(); } catch(_) {}
                deviceSse = null;
            }
            deviceSse = new EventSource(deviceUrl);
            let deviceSseReady = false;
            deviceSse.addEventListener('put', (e) => {
                if (!deviceSseReady) { deviceSseReady = true; return; }
                if (e.data) notifyBg('sse_device');
            });
            deviceSse.addEventListener('patch', (e) => {
                if (e.data) notifyBg('sse_device');
            });
            deviceSse.onerror = () => {
                if (deviceSse && deviceSse.readyState === EventSource.CLOSED) {
                    try { deviceSse.close(); } catch(_) {}
                    deviceSse = null;
                    setTimeout(setupDevice, 2500);
                }
            };
        }

        setupOtp();
        setupDevice();

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

// ─── Authentic Apple iPhone "Ting" Crystal Glass Chime ───────────────────────
let lastSoundPlayTime = 0;

function playIphoneTing() {
    const now = Date.now();
    // Strict debounce: never play twice within 1.5 seconds
    if (now - lastSoundPlayTime < 1500) return;
    lastSoundPlayTime = now;

    try {
        const audioEl = document.getElementById('ting-audio');
        if (audioEl) {
            audioEl.currentTime = 0;
            audioEl.volume = 0.95;
            const playPromise = audioEl.play();
            if (playPromise !== undefined) {
                playPromise.catch(() => {
                    const fallbackAudio = new Audio(chrome.runtime.getURL('ting_sound.mp3'));
                    fallbackAudio.volume = 0.95;
                    fallbackAudio.play().catch(() => {});
                });
            }
        } else {
            const audio = new Audio(chrome.runtime.getURL('ting_sound.mp3'));
            audio.volume = 0.95;
            audio.play().catch(() => {});
        }
    } catch (_) {}
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
