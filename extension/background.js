// background.js — MailSync Chrome Extension

self.addEventListener('offline', () => {
    setToolbarIcon('warning');
});
self.addEventListener('online', () => {
    setToolbarIcon('active');
});

// ─── Keep-Alive Alarm ────────────────────────────────────────────────────────
chrome.alarms.create("keepAlive", { periodInMinutes: 1 });
chrome.alarms.onAlarm.addListener(async (alarm) => {
    if (alarm.name !== "keepAlive") return;
    try {
        const data = await chrome.storage.local.get(['linked', 'uuid', 'aesKey']);
        if (!data.linked || !data.uuid || !data.aesKey) return;
        // Send Heartbeat to Firebase so Android knows the device is alive
        fetch(`https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/lastSeen.json`, {
            method: 'PUT',
            body: Date.now().toString()
        }).catch(() => {});

        const hasDoc = await chrome.offscreen.hasDocument().catch(() => false);
        if (!hasDoc) {
            await startListening(data.uuid, data.aesKey, true);
        } else {
            chrome.runtime.sendMessage({ target: 'offscreen', type: 'ping-sse' }, (response) => {
                if (chrome.runtime.lastError || !response || !response.alive) {
                    startListening(data.uuid, data.aesKey, true);
                } else {
                    fetchOtpImmediately(data.uuid, data.aesKey);
                }
            });
        }
    } catch(e) { /* silent */ }
});

// ─── Boot: Restore listening if already linked ────────────────────────────────
chrome.runtime.onStartup.addListener(() => {
    chrome.storage.local.get(['linked', 'uuid', 'aesKey'], async (data) => {
        if (data.linked && data.uuid && data.aesKey) {
            const hasDoc = await chrome.offscreen.hasDocument().catch(() => false);
            startListening(data.uuid, data.aesKey, !hasDoc);
        } else {
            setToolbarIcon('error');
        }
    });
});

chrome.storage.local.get(['linked', 'uuid', 'aesKey'], async (data) => {
    if (data.linked && data.uuid && data.aesKey) {
        // Ping firebase to tell Android this is a v2 extension that supports pcLoginActive
        fetch(`https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/extVersion.json`, {
            method: 'PUT',
            body: '2'
        }).catch(() => {});

        const hasDoc = await chrome.offscreen.hasDocument().catch(() => false);
        startListening(data.uuid, data.aesKey, !hasDoc);
    } else {
        setToolbarIcon('error');
    }
});

// ─── Message Handler ──────────────────────────────────────────────────────────
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.action === "force_icon") {
        setToolbarIcon(message.state);
        if (message.state === 'active' && currentUuid) {
            // Immediately attempt to fetch the latest state since internet is back
            fetch(`https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${currentUuid}.json`, { cache: 'no-store' })
                .then(r => r.json())
                .then(statusData => {
                    if (statusData && (statusData.syncEnabled === false || statusData.status === 'paused')) {
                        setToolbarIcon('warning');
                    }
                }).catch(() => {});
        }
    } else if (message.action === "start_listening") {
        chrome.storage.local.get(['uuid', 'aesKey'], (data) => {
            if (data.uuid && data.aesKey) {
                startListening(data.uuid, data.aesKey, true);
            }
        });
    } else if (message.action === "stop_listening") {
        isListening = false;
        setToolbarIcon('none');
        if (iconAnimInterval) { clearInterval(iconAnimInterval); iconAnimInterval = null; }
        // Close offscreen doc when explicitly stopped
        chrome.offscreen.hasDocument().then(has => {
            if (has) chrome.offscreen.closeDocument().catch(() => {});
        }).catch(() => {});
    } else if (message.action === "copy_to_clipboard") {
        copyToClipboard(message.text);
    } else if (message.action === "pc_login_detected") {
        chrome.storage.local.get(['uuid'], (data) => {
            if (!data.uuid) return;
            fetch(`https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/pcLoginActive.json`, {
                method: 'PUT',
                body: JSON.stringify({ active: true, host: message.url, ts: Date.now() })
            }).catch(() => {});
        });
    } else if (message.action === "pc_login_cleared") {
        chrome.storage.local.get(['uuid'], (data) => {
            if (!data.uuid) return;
            fetch(`https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/pcLoginActive.json`, {
                method: 'DELETE'
            }).catch(() => {});
        });
    } else if (message.action === "otp_intent") {
        chrome.storage.local.get(['uuid'], (data) => {
            if (!data.uuid) return;
            fetch(`https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/otpIntent.json`, {
                method: 'PUT',
                body: JSON.stringify({ active: true, host: message.host || '', ts: Date.now() })
            }).catch(() => {});
            fetch(`https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/lastActive.json`, {
                method: 'PUT',
                body: Date.now().toString()
            }).catch(() => {});
        });
    } else if (message.action === "user_activity") {
        chrome.storage.local.get(['uuid'], (data) => {
            if (!data.uuid) return;
            fetch(`https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/lastActive.json`, {
                method: 'PUT',
                body: Date.now().toString()
            }).catch(() => {});
        });
    } else if (message.action === "sse_otp") {
        // SSE fired a real OTP event — fetch it immediately
        chrome.storage.local.get(['uuid', 'aesKey'], (data) => {
            if (data.uuid && data.aesKey) fetchOtpImmediately(data.uuid, data.aesKey);
        });
    } else if (message.action === "sse_device") {
        chrome.storage.local.get(['uuid'], (data) => {
            if (data.uuid) {
                fetch(`https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}.json`, { cache: 'no-store' })
                    .then(r => r.json())
                    .then(statusData => {
                        if (statusData) {
                            const isError = statusData.status === 'error_no_accounts';
                            const isWarning = statusData.syncEnabled === false || statusData.status === 'paused';
                            setToolbarIcon(isError ? 'error' : (isWarning ? 'warning' : 'active'));
                            chrome.runtime.sendMessage({ action: "status_update", statusData }).catch(() => {});
                        }
                    }).catch(() => {});
            }
        });
    } else if (message.action === "capture_tab") {
        chrome.tabs.captureVisibleTab(null, {format: 'png'}, (dataUrl) => {
            sendResponse({ dataUrl });
        });
        return true; // Keep message channel open for async response
    }
});

// ─── Window Focus Tracking (Multi-device priority) ───────────────────────────
chrome.windows.onFocusChanged.addListener((windowId) => {
    chrome.storage.local.get(['uuid'], (data) => {
        if (!data.uuid) return;
        const isFocused = windowId !== chrome.windows.WINDOW_ID_NONE;
        fetch(`https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/isFocused.json`, {
            method: 'PUT',
            body: isFocused ? 'true' : 'false'
        }).catch(() => {});
        if (isFocused) {
            fetch(`https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/lastActive.json`, {
                method: 'PUT',
                body: Date.now().toString()
            }).catch(() => {});
        }
    });
});

// ─── State ───────────────────────────────────────────────────────────────────
let isListening = false;
let lastHandledOtp = null;
let lastHandledTime = 0;
let lastDeliveredOtp = null;
let lastDeliveredTime = 0;
let iconAnimInterval = null;
let currentIconState = 'none';
// Track current UUID so we know if a new QR scan happened
let currentUuid = null;

// ─── Toolbar Icon with Animated Dot ──────────────────────────────────────────
async function setToolbarIcon(state) {
    if (currentIconState === state) return;
    currentIconState = state;
    try {
        const resp = await fetch(chrome.runtime.getURL('icon128.png'));
        const blob = await resp.blob();
        const bitmap = await createImageBitmap(blob);

        const size = 128;
        const canvas = new OffscreenCanvas(size, size);
        const ctx = canvas.getContext('2d', { willReadFrequently: true });

        if (iconAnimInterval) {
            clearInterval(iconAnimInterval);
            iconAnimInterval = null;
        }

        async function drawFrame(dotVisible) {
            ctx.clearRect(0, 0, size, size);
            ctx.drawImage(bitmap, 0, 0, size, size);

            if (state === 'active') {
                const dotR = 18;
                const cx = size - dotR - 4;
                const cy = dotR + 4;

                if (dotVisible) {
                    ctx.beginPath();
                    ctx.arc(cx, cy, dotR + 6, 0, 2 * Math.PI);
                    ctx.fillStyle = 'rgba(16, 185, 129, 0.3)';
                    ctx.fill();
                }

                ctx.beginPath();
                ctx.arc(cx, cy, dotR, 0, 2 * Math.PI);
                ctx.fillStyle = '#10b981';
                ctx.fill();

                ctx.beginPath();
                ctx.arc(cx, cy, dotR, 0, 2 * Math.PI);
                ctx.strokeStyle = '#ffffff';
                ctx.lineWidth = 3;
                ctx.stroke();

            } else if (state === 'error' || state === 'warning') {
                const dotR = 18;
                const cx = size - dotR - 4;
                const cy = dotR + 4;
                
                const rColor = state === 'error' ? '239, 68, 68' : '255, 170, 0';
                const hexColor = state === 'error' ? '#ef4444' : '#ffaa00';

                if (dotVisible) {
                    ctx.beginPath();
                    ctx.arc(cx, cy, dotR + 6, 0, 2 * Math.PI);
                    ctx.fillStyle = `rgba(${rColor}, 0.3)`;
                    ctx.fill();
                }

                ctx.beginPath();
                ctx.arc(cx, cy, dotR, 0, 2 * Math.PI);
                ctx.fillStyle = hexColor;
                ctx.fill();

                ctx.beginPath();
                ctx.arc(cx, cy, dotR, 0, 2 * Math.PI);
                ctx.strokeStyle = '#ffffff';
                ctx.lineWidth = 3;
                ctx.stroke();
            }

            const imageData = ctx.getImageData(0, 0, size, size);
            await chrome.action.setIcon({ imageData: { 128: imageData } }).catch(() => {});
        }

        await drawFrame(true);

        if (state === 'active' || state === 'error' || state === 'warning') {
            let toggle = false;
            iconAnimInterval = setInterval(async () => {
                toggle = !toggle;
                await drawFrame(toggle);
            }, 800);
        }

    } catch (e) {
        chrome.action.setBadgeText({ text: '' });
    }
}

// ─── Main Listener Loop ───────────────────────────────────────────────────────
async function startListening(uuid, aesKeyBase64, force = false) {
    // If same uuid and already listening, don't tear down SSE unless forced
    if (isListening && !force && currentUuid === uuid) return;

    isListening = false;
    isListening = true;
    if (currentUuid !== uuid) {
        lastHandledOtp = null;
    }
    currentUuid = uuid;

    const dbBaseUrl = `https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app`;

    const rawKey = Uint8Array.from(atob(aesKeyBase64), c => c.charCodeAt(0));
    const cryptoKey = await crypto.subtle.importKey(
        'raw', rawKey,
        { name: 'AES-GCM', length: 256 },
        false, ['decrypt']
    );

    setToolbarIcon('active');

    // Only create/recreate offscreen doc if force=true (new QR scan or crash recovery)
    // Do NOT destroy it on routine alarm wakeups — that would kill the SSE connection!
    try {
        const hasDoc = await chrome.offscreen.hasDocument();
        if (force || !hasDoc) {
            if (hasDoc) {
                await chrome.offscreen.closeDocument().catch(() => {});
                await new Promise(resolve => setTimeout(resolve, 250)); // allow time for actual teardown
            }
            await chrome.offscreen.createDocument({
                url: 'offscreen.html',
                reasons: ['CLIPBOARD', 'AUDIO_PLAYBACK'],
                justification: 'SSE Connection, Clipboard and Audio chime'
            });
            // Wait for offscreen doc to initialize its message listener
            await new Promise(resolve => setTimeout(resolve, 400));
        }
        chrome.runtime.sendMessage({ target: 'offscreen', type: 'start-sse', uuid }).catch(() => {});
    } catch (e) {
        if (!e.message.includes("single offscreen document")) {
            reportGlobalError("Failed to start SSE: " + e.message);
        } else {
            // Document already exists due to race condition, safe to just send message
            chrome.runtime.sendMessage({ target: 'offscreen', type: 'start-sse', uuid }).catch(() => {});
        }
    }

    let bgFetchFailures = 0;

    const pollLoop = async () => {
        if (!isListening) {
            setToolbarIcon('none');
            return;
        }

        try {
            const statusRes = await fetch(`${dbBaseUrl}/devices/${uuid}.json`, { cache: 'no-store' });

            const statusData = await statusRes.json();

            if (!statusData || !statusData.dateLinked || statusData.status === 'terminated') {
                isListening = false;
                setToolbarIcon('error');
                chrome.runtime.sendMessage({ action: "device_terminated" }).catch(() => {});
                return;
            }

            const isError = statusData.status === 'error_no_accounts';
            const isWarning = statusData.syncEnabled === false || statusData.status === 'paused';
            setToolbarIcon(isError ? 'error' : (isWarning ? 'warning' : 'active'));

            // Log status transitions safely without polluting global errors
            chrome.storage.local.get(['lastLoggedStatus'], (data) => {
                if (chrome.runtime.lastError || !data) return;
                if (data.lastLoggedStatus !== statusData.status) {
                    console.log(`[MailSync] Status transition: ${data.lastLoggedStatus} -> ${statusData.status}`);
                    chrome.storage.local.set({ lastLoggedStatus: statusData.status });
                }
            });

            if (statusData.accountName && statusData.accountName.trim() !== '') {
                chrome.storage.local.set({ userName: statusData.accountName.trim() });
            }
            chrome.runtime.sendMessage({ action: "status_update", statusData }).catch(() => {});

            if (statusData.app_error) {
                reportGlobalError("Phone Error: " + statusData.app_error);
                fetch(`${dbBaseUrl}/devices/${uuid}/app_error.json`, { method: 'DELETE' }).catch(() => {});
            }

            bgFetchFailures = 0; // reset on success

            // Fallback poll — in case SSE missed something
            await fetchOtpImmediately(uuid, aesKeyBase64, cryptoKey);
        } catch (e) {
            // Silent — network hiccup
            bgFetchFailures++;
            if (bgFetchFailures >= 3 && navigator.onLine === false) {
                setToolbarIcon('warning');
            }
        }

        if (isListening) {
            setTimeout(pollLoop, 30000); // 30s fallback poll only
        }
    };

    pollLoop();
}

let isFetchingOtp = false;

async function fetchOtpImmediately(uuid, aesKeyBase64, providedCryptoKey = null) {
    if (isFetchingOtp) return;
    isFetchingOtp = true;

    try {
        const local = await chrome.storage.local.get(['linked']);
        if (!local || !local.linked) return;
        
        const rawKey = Uint8Array.from(atob(aesKeyBase64), c => c.charCodeAt(0));
        const cryptoKey = providedCryptoKey || await crypto.subtle.importKey(
            'raw', rawKey,
            { name: 'AES-GCM', length: 256 },
            false, ['decrypt']
        );

        const dbBaseUrl = `https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app`;
        const otpsUrl = `${dbBaseUrl}/otps/${uuid}.json`;
        const otpRes = await fetch(otpsUrl, { cache: 'no-store' });
        const otpData = await otpRes.json();

        if (otpData && otpData.iv && otpData.data) {
            const sig = otpData.iv + otpData.data;
            const now = Date.now();
            if (sig === lastHandledOtp && (now - lastHandledTime) < 20000) {
                return;
            }
            lastHandledOtp = sig;
            lastHandledTime = now;

            // Delete from Firebase FIRST — prevent duplicate delivery if SSE fires again before we finish
            await fetch(otpsUrl, { method: 'DELETE' }).catch(() => {});
            await handleEncryptedOtp(otpData.iv, otpData.data, cryptoKey);
        }
    } catch(e) {
        // silent
    } finally {
        isFetchingOtp = false;
    }
}

// ─── Clipboard ───────────────────────────────────────────────────────────────
async function copyToClipboard(text) {
    try {
        const hasDoc = await chrome.offscreen.hasDocument();
        if (!hasDoc) {
            await chrome.offscreen.createDocument({
                url: 'offscreen.html',
                reasons: ['CLIPBOARD', 'AUDIO_PLAYBACK'],
                justification: 'Copy OTP to clipboard and play chime'
            }).catch(e => { 
                if(!e.message.includes("single offscreen document")) throw e; 
            });
            await new Promise(resolve => setTimeout(resolve, 200));
        }
        chrome.runtime.sendMessage({ type: 'copy-data-to-clipboard', target: 'offscreen', data: text }).catch(() => {});
    } catch (e) { /* offscreen unavailable */ }
}

function reportGlobalError(msg) {
    if (!msg) return;
    chrome.storage.local.get(['globalErrors'], (data) => {
        if (chrome.runtime.lastError) return;
        const errors = (data && Array.isArray(data.globalErrors)) ? data.globalErrors : [];
        errors.unshift({ msg, time: Date.now() });
        if (errors.length > 20) errors.pop();
        chrome.storage.local.set({ globalErrors: errors }, () => {
            if (chrome.runtime.lastError) return;
            chrome.runtime.sendMessage({ action: "global_error_updated" }).catch(() => {});
        });
    });
}

// Removed Carry Forward Toast logic as requested by user to prevent duplicate animations on tab switch
// ─── Decrypt & Deliver OTP ───────────────────────────────────────────────────
async function handleEncryptedOtp(ivBase64, dataBase64, cryptoKey) {
    try {
        const iv = Uint8Array.from(atob(ivBase64), c => c.charCodeAt(0));
        const cipher = Uint8Array.from(atob(dataBase64), c => c.charCodeAt(0));

        const decrypted = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, cryptoKey, cipher);
        const text = new TextDecoder().decode(decrypted);

        // Format: "otpCode|sender|timestamp"
        const parts = text.split('|');
        if (parts.length < 2) {
            reportGlobalError("Decrypted OTP payload format invalid.");
            return;
        }

        const otpCode = parts[0].trim();
        const sender = parts[1].trim();

        // Smart Deduplication:
        // 1. Simultaneous dual arrival (< 2.5s e.g. concurrent SMS + Email dispatch): suppress to avoid double chime/toast.
        // 2. Provider re-send (between 2.5s and 120s): re-copy to clipboard and show an extended toast (9s) notifying user it's the same OTP already on clipboard.
        const now = Date.now();
        const stored = await chrome.storage.local.get(['recentDeliveredOtps']);
        let recentList = (stored.recentDeliveredOtps || []).filter(item => (now - item.time) < 180000);

        function extractTokens(str) {
            if (!str) return [];
            let clean = str.toLowerCase();
            if (clean.length > 3 && (clean[2] === '-' || clean[2] === '_')) clean = clean.substring(3);
            const ignore = new Set(['sms', 'otp', 'code', 'mail', 'gmail', 'service', 'noreply', 'team', 'account', 'info', 'support']);
            return clean.split(/[^a-z0-9]+/).filter(t => t.length >= 3 && !ignore.has(t));
        }

        const senderTokens = extractTokens(sender);
        let isResentDuplicate = false;

        const matchedIndex = recentList.findIndex(item => {
            if (item.otp === otpCode) {
                const elapsed = now - item.time;
                if (elapsed <= 60000) return true; // Exact same code within 60s
                if (elapsed <= 120000) {
                    const itemTokens = extractTokens(item.sender);
                    const sharesBrand = senderTokens.some(t => itemTokens.includes(t));
                    return sharesBrand || item.sender.toLowerCase() === sender.toLowerCase();
                }
            }
            return false;
        });

        if (matchedIndex !== -1) {
            const elapsed = now - recentList[matchedIndex].time;
            if (elapsed <= 2500) {
                console.log(`[MailSync] Simultaneous burst of ${otpCode} from ${sender} suppressed (${elapsed}ms).`);
                return;
            } else {
                isResentDuplicate = true;
                recentList[matchedIndex].time = now;
            }
        } else {
            recentList.unshift({ otp: otpCode, sender, time: now });
            if (recentList.length > 10) recentList.pop();
        }

        await chrome.storage.local.set({ recentDeliveredOtps: recentList });

        // parts[2] = timestamp
        // parts[3] = expiresAt timestamp in ms
        const expiresAt = parts.length >= 4 ? parseInt(parts[3], 10) : (Date.now() + 10 * 60 * 1000);

        // parts[4] = targetUuid (optional device targeting)
        const targetUuid = parts.length >= 5 ? parts[4].trim() : null;
        const currentData = await chrome.storage.local.get(['uuid']);
        if (targetUuid && targetUuid !== 'ALL' && currentData.uuid && targetUuid !== currentData.uuid) {
            console.log(`[MailSync] OTP targeted to ${targetUuid}, ignoring on this device ${currentData.uuid}`);
            return;
        }

        // Clean up any pending otpIntent now that the code has arrived
        if (currentData.uuid) {
            fetch(`https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${currentData.uuid}/otpIntent.json`, {
                method: 'DELETE'
            }).catch(() => {});
        }

        // 1. Copy to clipboard immediately
        await copyToClipboard(otpCode);

        // 2. Auto-clear clipboard after 60s
        setTimeout(() => copyToClipboard(' '), 60000);

        // 3. Save pending toast for persistence across page reloads, redirects and tab switches
        // Re-sent duplicate OTP stays on screen for 9s (user requested 8-10s for ample visibility); fresh OTP stays for 6s
        const toastDuration = isResentDuplicate ? 9000 : 6000;
        const showUntil = Date.now() + toastDuration;
        chrome.storage.local.set({
            pendingToast: {
                otp: otpCode,
                sender: sender,
                showUntil: showUntil,
                isDuplicate: isResentDuplicate
            }
        });
        setTimeout(() => {
            chrome.storage.local.get(['pendingToast'], (data) => {
                if (data.pendingToast && data.pendingToast.showUntil <= Date.now()) {
                    chrome.storage.local.remove(['pendingToast']);
                }
            });
        }, toastDuration + 500);

        // 4. Save to local history
        chrome.storage.local.get(['otpHistory', 'uuid'], (data) => {
            let history = data.otpHistory || [];
            
            // Check if exact same OTP is already in history
            const duplicateIndex = history.findIndex(h => h.otp === otpCode);
            
            if (duplicateIndex !== -1) {
                // It's a duplicate. Remove it from its current position so we can move it to the top
                history.splice(duplicateIndex, 1);
            }
            
            // Add to top of history (new or updated, with isDuplicate flag)
            history.unshift({ otp: otpCode, sender, time: Date.now(), expiresAt: expiresAt, isDuplicate: isResentDuplicate });
            
            if (history.length > 3) history.pop();
            chrome.storage.local.set({ otpHistory: history });
            chrome.runtime.sendMessage({ action: "otp_received", otpCode, sender, isDuplicate: isResentDuplicate }).catch(() => {});
            
            // 4.5 Write lastOtpCapturedTime to Firebase for THIS specific device
            if (data.uuid) {
                const dateOpts = { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit', hour12: true };
                let dateStr = new Date().toLocaleString('en-US', dateOpts);
                dateStr = dateStr.replace(',', ' at');
                fetch(`https://otpsync-adf52-default-rtdb.asia-southeast1.firebasedatabase.app/devices/${data.uuid}/lastOtpCapturedTime.json`, {
                    method: 'PUT',
                    body: JSON.stringify(dateStr)
                }).catch(() => {});
            }

            // In-page toast when viewing an active web page; fallback to native desktop notification if minimized or restricted.
            // This strictly guarantees NEVER showing two dialogues simultaneously!
            chrome.tabs.query({ active: true, lastFocusedWindow: true }, (tabs) => {
                let sentInPageToast = false;
                if (tabs && tabs.length > 0) {
                    const tab = tabs[0];
                    const isRestrictedUrl = !tab.url || tab.url.startsWith('chrome://') || tab.url.startsWith('chrome-extension://') || tab.url.startsWith('about:');
                    if (!isRestrictedUrl) {
                        sentInPageToast = true;
                        chrome.tabs.sendMessage(tab.id, {
                            action: "show_toast_and_copy",
                            otp: otpCode,
                            sender,
                            timeRemaining: toastDuration,
                            showUntil: showUntil,
                            isDuplicate: isResentDuplicate
                        }).catch(async (err) => {
                            if (err.message && (err.message.includes("Receiving end does not exist") || err.message.includes("Could not establish connection"))) {
                                try {
                                    await chrome.scripting.insertCSS({ target: { tabId: tab.id }, files: ["content.css"] });
                                    await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ["content.js"] });
                                    await new Promise(resolve => setTimeout(resolve, 200));
                                    await chrome.tabs.sendMessage(tab.id, { action: "show_toast_and_copy", otp: otpCode, sender, timeRemaining: toastDuration, showUntil: showUntil, isDuplicate: isResentDuplicate });
                                } catch (_) {
                                    showNativeNotification(otpCode, sender, isResentDuplicate);
                                }
                            } else {
                                showNativeNotification(otpCode, sender, isResentDuplicate);
                            }
                        });
                    }
                }

                // If not viewing an active webpage (e.g. Chrome minimized or on system page), show native notification
                if (!sentInPageToast) {
                    showNativeNotification(otpCode, sender, isResentDuplicate);
                }
            });
        });

    } catch (e) {
        reportGlobalError(`Decryption failed: ${e.message}`);
    }
}

function showNativeNotification(otpCode, sender, isDuplicate = false) {
    try {
        const title = isDuplicate ? `✓ Same OTP: ${otpCode} (Already Copied)` : `✓ OTP Copied: ${otpCode}`;
        const message = isDuplicate ? `from ${sender} • Re-sent code already on clipboard!` : `from ${sender} • Auto-copied to clipboard!`;
        chrome.notifications.create(`otp-${Date.now()}`, {
            type: 'basic',
            iconUrl: 'icon128.png',
            title: title,
            message: message,
            priority: 2,
            silent: true,
            requireInteraction: false
        }, () => {});
    } catch (_) {}
}

// ─── Re-show Pending Toast on Tab Reloads / Navigations / Switches ───────────
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
    if (changeInfo.status === 'complete' && tab && tab.active) {
        const isRestrictedUrl = !tab.url || tab.url.startsWith('chrome://') || tab.url.startsWith('chrome-extension://') || tab.url.startsWith('about:');
        if (isRestrictedUrl) return;

        chrome.storage.local.get(['pendingToast'], (data) => {
            const pt = data.pendingToast;
            if (pt && pt.showUntil && Date.now() < pt.showUntil) {
                const remaining = pt.showUntil - Date.now();
                if (remaining > 300) {
                    chrome.tabs.sendMessage(tabId, {
                        action: "show_toast_and_copy",
                        otp: pt.otp,
                        sender: pt.sender,
                        isCarryForward: true,
                        timeRemaining: remaining,
                        showUntil: pt.showUntil,
                        isDuplicate: Boolean(pt.isDuplicate)
                    }).catch(() => {});
                }
            }
        });
    }
});

chrome.tabs.onActivated.addListener((activeInfo) => {
    chrome.tabs.get(activeInfo.tabId, (tab) => {
        if (chrome.runtime.lastError || !tab) return;
        const isRestrictedUrl = !tab.url || tab.url.startsWith('chrome://') || tab.url.startsWith('chrome-extension://') || tab.url.startsWith('about:');
        if (isRestrictedUrl) return;

        chrome.storage.local.get(['pendingToast'], (data) => {
            const pt = data.pendingToast;
            if (pt && pt.showUntil && Date.now() < pt.showUntil) {
                const remaining = pt.showUntil - Date.now();
                if (remaining > 300) {
                    chrome.tabs.sendMessage(activeInfo.tabId, {
                        action: "show_toast_and_copy",
                        otp: pt.otp,
                        sender: pt.sender,
                        isCarryForward: true,
                        timeRemaining: remaining,
                        showUntil: pt.showUntil,
                        isDuplicate: Boolean(pt.isDuplicate)
                    }).catch(() => {});
                }
            }
        });
    });
});

