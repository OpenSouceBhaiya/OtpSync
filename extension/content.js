// content.js — OTP Sync Companion
// CSS is injected via content.css (manifest), NOT inline here.

// ─── Persistent Toast: Re-show on page navigation or tab visibility ──────────
function checkAndDisplayPendingToast() {
    try {
        chrome.storage.local.get(['pendingToast'], (data) => {
            if (chrome.runtime.lastError) return;
            const pt = data.pendingToast;
            if (pt && pt.showUntil) {
                const remaining = pt.showUntil - Date.now();
                if (remaining > 300) {
                    showToast(pt.otp, pt.sender, true, remaining, pt.showUntil, Boolean(pt.isDuplicate));
                } else {
                    dismissToast();
                }
            } else {
                dismissToast();
            }
        });
    } catch (e) { }
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', checkAndDisplayPendingToast);
} else {
    checkAndDisplayPendingToast();
}

document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') {
        checkAndDisplayPendingToast();
    }
});

// Listen for storage changes so active tabs receive pending toast immediately
chrome.storage.onChanged.addListener((changes, area) => {
    if (area === 'local' && changes.pendingToast) {
        const pt = changes.pendingToast.newValue;
        if (pt && pt.showUntil) {
            const remaining = pt.showUntil - Date.now();
            if (remaining > 300) {
                const existing = document.getElementById('otpsync-toast');
                const isCarry = Boolean(existing && existing.dataset.otp === pt.otp && !pt.isDuplicate);
                showToast(pt.otp, pt.sender, isCarry, remaining, pt.showUntil, Boolean(pt.isDuplicate));
            } else {
                dismissToast();
            }
        } else {
            dismissToast();
        }
    }
});

// ─── Authentic iPhone "Ting" Glass Chime ─────────────────────────────────────
let lastChimeTime = 0;
function playNotificationChime() {
    if (document.hidden) return; // Only play sound on the visible/active tab
    const now = Date.now();
    if (now - lastChimeTime < 1500) return;
    lastChimeTime = now;

    try {
        const audio = new Audio(chrome.runtime.getURL('ting_sound.mp3'));
        audio.volume = 0.95;
        const playPromise = audio.play();
        if (playPromise !== undefined) {
            playPromise.catch(() => {
                // Autoplay blocked by tab policy — route to offscreen document which has audio permissions
                safeSendMessage({ type: 'play-ting', target: 'offscreen' });
            });
        }
    } catch (_) {
        safeSendMessage({ type: 'play-ting', target: 'offscreen' });
    }
}

// ─── Guard: Don't inject twice ────────────────────────────────────────────────
{
    chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
        if (request.action === "show_toast_and_copy") {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(request.otp).catch(() => {});
            } else {
                try {
                    const textArea = document.createElement("textarea");
                    textArea.value = request.otp;
                    textArea.style.position = "fixed";
                    textArea.style.opacity = "0";
                    document.body.appendChild(textArea);
                    textArea.focus();
                    textArea.select();
                    document.execCommand('copy');
                    document.body.removeChild(textArea);
                } catch (e) { }
            }
            
            showToast(request.otp, request.sender, request.isCarryForward, request.timeRemaining, request.showUntil, Boolean(request.isDuplicate));
            sendResponse({success: true});
            return true;
        }
    });
}

let currentToastTimer = null;
let isDismissing = false;

function dismissToast() {
    if (currentToastTimer) {
        clearTimeout(currentToastTimer);
        currentToastTimer = null;
    }
    const existing = document.getElementById('otpsync-toast');
    if (existing && !isDismissing) {
        isDismissing = true;
        snapThanos(existing);
    }
}

function showToast(otp, sender, isCarryForward = false, timeRemaining = 6000, showUntil = null, isDuplicate = false) {
    const calculatedRemaining = showUntil ? (showUntil - Date.now()) : timeRemaining;
    if (calculatedRemaining <= 300) {
        dismissToast();
        return;
    }

    // Clean up any lingering Thanos dust canvases immediately
    document.querySelectorAll('.dust-canvas').forEach(c => c.remove());

    const displaySender = sender && sender.length > 25 ? sender.substring(0, 23) + '…' : (sender || 'OTP Sync');
    const toastTitle = isDuplicate ? '✓ Same OTP already copied' : '✓ OTP Copied';
    const toastSender = isDuplicate ? `from ${displaySender} · Already on Clipboard` : `from ${displaySender}`;

    let existing = document.getElementById('otpsync-toast');
    if (existing) {
        // If the exact same OTP arrives again:
        if (existing.dataset.otp === otp) {
            const titleEl = existing.querySelector('.mailsync-toast-title');
            if (titleEl) titleEl.textContent = toastTitle;
            const senderEl = existing.querySelector('.mailsync-toast-sender');
            if (senderEl) senderEl.textContent = toastSender;
            if (isDuplicate) existing.dataset.duplicate = 'true';
            if (showUntil) existing.dataset.showUntil = showUntil.toString();

            // Synchronize the single dismissal timer strictly with calculatedRemaining
            if (currentToastTimer) clearTimeout(currentToastTimer);
            currentToastTimer = setTimeout(() => {
                dismissToast();
            }, calculatedRemaining);
            return;
        }

        // A new/different OTP arrived within seconds:
        // Immediately remove the previous toast without exit animation
        if (currentToastTimer) clearTimeout(currentToastTimer);
        currentToastTimer = null;
        existing.remove();
    }

    // Reset dismissing state on fresh toast
    isDismissing = false;

    // Play notification sound on fresh arrival only
    if (!isCarryForward) {
        playNotificationChime();
    }

    const toast = document.createElement('div');
    toast.id = 'otpsync-toast';
    toast.dataset.otp = otp;
    if (isDuplicate) toast.dataset.duplicate = 'true';
    if (showUntil) toast.dataset.showUntil = showUntil.toString();

    const iconUrl = chrome.runtime.getURL('icon48.png');

    toast.innerHTML = `
        <div class="mailsync-toast-icon">
            <img src="${iconUrl}" width="24" height="24" alt="Icon" />
        </div>
        <div class="mailsync-toast-content">
            <div class="mailsync-toast-title">${toastTitle}</div>
            <div class="mailsync-toast-code">${otp}</div>
            <div class="mailsync-toast-sender">${toastSender}</div>
        </div>
    `;

    const mount = () => {
        const container = document.body || document.documentElement;
        if (container) {
            container.appendChild(toast);
        } else {
            document.addEventListener('DOMContentLoaded', () => {
                (document.body || document.documentElement).appendChild(toast);
            }, { once: true });
        }
    };
    mount();

    toast.addEventListener('click', () => {
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(otp).catch(() => {});
        }
        const titleEl = toast.querySelector('.mailsync-toast-title');
        if (titleEl) titleEl.textContent = '✓ Copied to Clipboard!';
        setTimeout(() => {
            dismissToast();
        }, 400);
    });

    if (isCarryForward) {
        toast.style.transition = 'none';
        toast.classList.add('show');
        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                toast.style.transition = '';
            });
        });
    } else {
        // Trigger slide-in cleanly
        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                toast.classList.add('show');
            });
        });
    }

    if (currentToastTimer) clearTimeout(currentToastTimer);
    currentToastTimer = setTimeout(() => {
        dismissToast();
    }, calculatedRemaining);
}

function snapThanos(element) {
    if (!element || !element.parentNode) {
        isDismissing = false;
        return;
    }
    const LAYER_COUNT = 32;
    const rect = element.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) {
        if (element.parentNode) element.remove();
        isDismissing = false;
        return;
    }

    // Native Canvas Recreation of the Toast
    // 100% CSP compliance and zero external dependencies
    const canvas = document.createElement('canvas');
    const scale = window.devicePixelRatio || 1;
    const cw = Math.floor(rect.width * scale);
    const ch = Math.floor(rect.height * scale);
    canvas.width = cw;
    canvas.height = ch;
    const ctx = canvas.getContext('2d');
    if (!ctx) {
        if (element.parentNode) element.remove();
        isDismissing = false;
        return;
    }
    ctx.scale(scale, scale);

    // Draw Background
    ctx.fillStyle = '#16161c';
    ctx.beginPath();
    if (ctx.roundRect) {
        ctx.roundRect(0, 0, rect.width, rect.height, 16);
    } else {
        ctx.rect(0, 0, rect.width, rect.height);
    }
    ctx.fill();

    // Draw Border
    ctx.strokeStyle = 'rgba(0, 255, 163, 0.3)';
    ctx.lineWidth = 1;
    ctx.stroke();

    // Draw Icon Box
    ctx.fillStyle = 'rgba(0,255,163,0.12)';
    ctx.beginPath();
    if (ctx.roundRect) {
        ctx.roundRect(16, 14, 40, 40, 10);
    } else {
        ctx.rect(16, 14, 40, 40);
    }
    ctx.fill();
    ctx.strokeStyle = 'rgba(0,255,163,0.2)';
    ctx.stroke();

    // Draw Icon Image
    const iconImg = element.querySelector('.mailsync-toast-icon img');
    if (iconImg && iconImg.complete && iconImg.naturalWidth > 0) {
        try {
            ctx.drawImage(iconImg, 16 + 8, 14 + 8, 24, 24);
        } catch (_) {}
    }

    // Draw Texts
    const titleEl = element.querySelector('.mailsync-toast-title');
    const titleText = titleEl ? titleEl.innerText : '✓ OTP Copied';
    ctx.fillStyle = '#00FFA3';
    ctx.font = '700 11px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
    ctx.fillText(titleText, 70, 26);

    const codeEl = element.querySelector('.mailsync-toast-code');
    const otpText = codeEl ? codeEl.innerText : '';
    ctx.fillStyle = '#FFFFFF';
    ctx.font = '800 22px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
    if (ctx.letterSpacing !== undefined) {
        ctx.letterSpacing = '3px';
    }
    ctx.fillText(otpText, 70, 50);

    const senderEl = element.querySelector('.mailsync-toast-sender');
    const senderText = senderEl ? senderEl.innerText : '';
    ctx.fillStyle = '#9CA3AF';
    ctx.font = '400 11px -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
    ctx.fillText(senderText, 70, 68);

    let originalData;
    try {
        originalData = ctx.getImageData(0, 0, cw, ch);
    } catch (e) {
        if (element.parentNode) element.remove();
        isDismissing = false;
        return;
    }

    const canvases = [];
    const imageDataList = [];

    for (let i = 0; i < LAYER_COUNT; i++) {
        const c = document.createElement('canvas');
        c.width = cw;
        c.height = ch;
        c.className = 'dust-canvas';
        c.style.width = rect.width + 'px';
        c.style.height = rect.height + 'px';
        c.style.top = rect.top + 'px';
        c.style.left = rect.left + 'px';
        (document.body || document.documentElement).appendChild(c);
        canvases.push(c);

        const layerCtx = c.getContext('2d');
        imageDataList.push(layerCtx.createImageData(cw, ch));
    }

    // Distribute pixels across 32 layers
    for (let x = 0; x < cw; x++) {
        for (let y = 0; y < ch; y++) {
            const pixelIndex = (y * cw + x) * 4;
            if (originalData.data[pixelIndex + 3] > 0) {
                const layerIndex = Math.floor((LAYER_COUNT * (Math.random() + 2 * x / cw)) / 3);
                const safeLayer = Math.min(LAYER_COUNT - 1, Math.max(0, layerIndex));

                imageDataList[safeLayer].data[pixelIndex] = originalData.data[pixelIndex];
                imageDataList[safeLayer].data[pixelIndex + 1] = originalData.data[pixelIndex + 1];
                imageDataList[safeLayer].data[pixelIndex + 2] = originalData.data[pixelIndex + 2];
                imageDataList[safeLayer].data[pixelIndex + 3] = originalData.data[pixelIndex + 3];
            }
        }
    }

    for (let i = 0; i < LAYER_COUNT; i++) {
        canvases[i].getContext('2d').putImageData(imageDataList[i], 0, 0);
    }

    // Hide original element immediately
    element.style.visibility = 'hidden';

    canvases.forEach((c, i) => {
        setTimeout(() => {
            const randomAngle = (Math.random() * 60 - 30) * Math.PI / 180;
            const randomDistance = 150 + Math.random() * 150;
            const tx = Math.cos(randomAngle) * randomDistance;
            const ty = Math.sin(randomAngle) * randomDistance - 50;
            const rotate = (Math.random() - 0.5) * 45;

            c.style.transform = `translate(${tx}px, ${ty}px) rotate(${rotate}deg)`;
            c.style.opacity = '0';
            c.style.filter = 'blur(2px)';

            setTimeout(() => {
                if (c.parentNode) c.remove();
                if (i === LAYER_COUNT - 1) {
                    if (element.parentNode) element.remove();
                    isDismissing = false;
                }
            }, 1500);
        }, i * 35);
    });
}

    // ─── Smart Login Detection ────────────────────────────────────────────────────
    let hasSignaledLogin = false;
    let detectionTimeout = null;

    function canAutoFill(input) {
        if (input.type !== 'text' && input.type !== 'number' && input.type !== 'tel') return false;
        if (input.disabled || input.readOnly || input.type === 'hidden') return false;
        const name = (input.name || '').toLowerCase();
        const id = (input.id || '').toLowerCase();
        const placeholder = (input.placeholder || '').toLowerCase();
        const aria = (input.getAttribute('aria-label') || '').toLowerCase();
        const autocomplete = (input.getAttribute('autocomplete') || '').toLowerCase();
        if (autocomplete === 'one-time-code') return true;
        const maxLen = input.maxLength;
        const isReasonableLength = maxLen === -1 || (maxLen >= 4 && maxLen <= 10);
        const isNumericType = input.type === 'number' || input.inputMode === 'numeric' || input.pattern === '[0-9]*';
        const keywords = ['otp', 'code', 'pin', '2fa', 'mfa', 'token', 'verify', 'verification', 'security'];
        const hasKeyword = keywords.some(k => name.includes(k) || id.includes(k) || placeholder.includes(k) || aria.includes(k));
        return (hasKeyword && isReasonableLength) || (isNumericType && hasKeyword);
    }

    function safeSendMessage(msg) {
        try {
            chrome.runtime.sendMessage(msg).catch((e) => {
                if (e && e.message && (
                    e.message.includes("Extension context invalidated") ||
                    e.message.includes("No SW") ||
                    e.message.includes("Receiving end does not exist") ||
                    e.message.includes("Could not establish connection")
                )) return;
                console.warn("MailSync sendMessage failed:", e);
            });
        } catch (e) {
            if (e && e.message && (
                e.message.includes("Extension context invalidated") ||
                e.message.includes("No SW") ||
                e.message.includes("Receiving end does not exist") ||
                e.message.includes("Could not establish connection")
            )) return;
            console.warn("MailSync sendMessage failed:", e);
        }
    }

    function detectLoginPage() {
        if (detectionTimeout) clearTimeout(detectionTimeout);
        detectionTimeout = setTimeout(() => {
            let isLogin = false;
            const inputs = document.querySelectorAll('input');
            for (let i = 0; i < inputs.length; i++) {
                if (canAutoFill(inputs[i])) { isLogin = true; break; }
            }
            if (!isLogin) {
                const segmented = document.querySelectorAll('input[maxlength="1"]');
                if (segmented.length >= 4 && segmented.length <= 8) isLogin = true;
            }
            if (isLogin) {
                if (!hasSignaledLogin) {
                    hasSignaledLogin = true;
                    safeSendMessage({ action: "pc_login_detected", url: window.location.hostname });
                    
                    // Ping every 5 seconds to keep the session alive, so if tab closes, Firebase can timeout instantly (15s)
                    window.loginPingInterval = setInterval(() => {
                        safeSendMessage({ action: "pc_login_detected", url: window.location.hostname });
                    }, 5000);
                    
                    if (!window.loginUnloadListener) {
                        window.loginUnloadListener = () => {
                            safeSendMessage({ action: "pc_login_cleared" });
                        };
                        window.addEventListener('beforeunload', window.loginUnloadListener);
                    }
                }
            } else {
                if (hasSignaledLogin) {
                    hasSignaledLogin = false;
                    if (window.loginPingInterval) clearInterval(window.loginPingInterval);
                    if (window.loginUnloadListener) {
                        window.removeEventListener('beforeunload', window.loginUnloadListener);
                        window.loginUnloadListener = null;
                    }
                    safeSendMessage({ action: "pc_login_cleared" });
                }
            }
        }, 500);
    }

    // ─── Active User Activity & OTP Intent Reporting ──────────────────────────────
    let lastActivityReport = 0;
    function reportUserActivity() {
        const now = Date.now();
        if (now - lastActivityReport > 15000) {
            lastActivityReport = now;
            safeSendMessage({ action: "user_activity" });
        }
    }

    window.addEventListener('keydown', reportUserActivity, { passive: true });
    window.addEventListener('pointerdown', reportUserActivity, { passive: true });

    document.addEventListener('focusin', (e) => {
        reportUserActivity();
        const target = e.target;
        if (target && target.tagName === 'INPUT') {
            if (canAutoFill(target) || target.type === 'password' || target.getAttribute('autocomplete') === 'one-time-code') {
                safeSendMessage({
                    action: "otp_intent",
                    host: window.location.hostname
                });
            }
        }
    }, { passive: true });

    detectLoginPage();
    const targetRoot = document.body || document.documentElement;
    if (targetRoot) {
        const observer = new MutationObserver(() => detectLoginPage());
        observer.observe(targetRoot, { childList: true, subtree: true, attributes: true, attributeFilter: ['type', 'class', 'style'] });
    }
