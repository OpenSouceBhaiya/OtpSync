// content.js — OTP Sync Companion
// CSS is injected via content.css (manifest), NOT inline here.
(function() {
    if (window.__otpsync_loaded) {
        if (typeof window.__otpsync_checkToast === 'function') {
            window.__otpsync_checkToast();
        }
        return;
    }
    window.__otpsync_loaded = true;

// ─── Pinned Toast Engine: Seamlessly pinned across all tabs like a sticky navbar ───
let currentToastTimer = null;
let isDismissing = false;

function checkAndDisplayPendingToast() {
    if (document.hidden) return; // Never render on hidden/background tabs
    try {
        chrome.storage.local.get(['pendingToast'], (data) => {
            if (chrome.runtime.lastError || document.hidden) return;
            const pt = data.pendingToast;
            if (pt && pt.showUntil && pt.showUntil > Date.now()) {
                renderPinnedToast(pt.otp, pt.sender, pt.showUntil, Boolean(pt.isDuplicate), false);
            } else {
                dismissToast(false);
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
    } else {
        // Tab is hidden (user switched away): remove toast from this inactive tab immediately
        dismissToast(false);
    }
});

// Sync across tabs immediately via storage changes
chrome.storage.onChanged.addListener((changes, area) => {
    if (area === 'local' && changes.pendingToast) {
        const pt = changes.pendingToast.newValue;
        if (pt && pt.showUntil && pt.showUntil > Date.now()) {
            if (document.hidden) return; // Strict: NEVER render on background tabs
            renderPinnedToast(pt.otp, pt.sender, pt.showUntil, Boolean(pt.isDuplicate), false);
        } else {
            dismissToast(false);
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

// ─── Message Listener: Show Toast & Copy ─────────────────────────────────────
{
    chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
        if (request.action === "show_toast_and_copy") {
            // Write OTP to clipboard immediately
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
            
            playNotificationChime();
            renderPinnedToast(request.otp, request.sender, request.showUntil, Boolean(request.isDuplicate), true);
            sendResponse({success: true});
            return true;
        }

        if (request.action === "ensure_pinned_toast") {
            renderPinnedToast(request.otp, request.sender, request.showUntil, Boolean(request.isDuplicate), false);
            sendResponse({success: true});
            return true;
        }
    });
}

function dismissToast(withAnimation = true) {
    if (currentToastTimer) {
        clearTimeout(currentToastTimer);
        currentToastTimer = null;
    }
    const existing = document.getElementById('otpsync-toast');
    if (!existing) return;

    if (withAnimation && !isDismissing && !document.hidden) {
        isDismissing = true;
        snapThanos(existing);
    } else {
        existing.remove();
        isDismissing = false;
    }
}

function renderPinnedToast(otp, sender, showUntil, isDuplicate = false, shouldSlideIn = false) {
    const calculatedRemaining = showUntil ? (showUntil - Date.now()) : 6000;
    if (calculatedRemaining <= 300) {
        dismissToast(false);
        return;
    }

    // Clean up any lingering Thanos dust canvases immediately
    document.querySelectorAll('.dust-canvas').forEach(c => c.remove());

    const displaySender = sender && sender.length > 25 ? sender.substring(0, 23) + '…' : (sender || 'OTP Sync');
    const toastTitle = isDuplicate ? '✓ Same OTP Re-sent' : '✓ OTP Copied';
    const toastSender = isDuplicate ? `from ${displaySender} · Re-copied to Clipboard` : `from ${displaySender}`;

    let toast = document.getElementById('otpsync-toast');
    if (toast) {
        // If the exact same OTP arrives again:
        if (toast.dataset.otp === otp) {
            const titleEl = toast.querySelector('.mailsync-toast-title');
            if (titleEl) titleEl.textContent = toastTitle;
            const senderEl = toast.querySelector('.mailsync-toast-sender');
            if (senderEl) senderEl.textContent = toastSender;
            if (isDuplicate) toast.dataset.duplicate = 'true';
            toast.dataset.showUntil = showUntil.toString();

            if (currentToastTimer) clearTimeout(currentToastTimer);
            currentToastTimer = setTimeout(() => {
                dismissToast(true);
            }, calculatedRemaining);
            return;
        }

        // Different OTP arrived: remove old one cleanly
        if (currentToastTimer) clearTimeout(currentToastTimer);
        currentToastTimer = null;
        toast.remove();
        toast = null;
    }

    isDismissing = false;

    toast = document.createElement('div');
    toast.id = 'otpsync-toast';
    toast.dataset.otp = otp;
    if (isDuplicate) toast.dataset.duplicate = 'true';
    toast.dataset.showUntil = showUntil.toString();

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
        chrome.storage.local.remove('pendingToast').catch(() => {});
        setTimeout(() => {
            dismissToast(true);
        }, 350);
    });

    if (shouldSlideIn) {
        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                toast.classList.add('show');
            });
        });
    } else {
        // Pinned sticky feel: instant presence with zero jitter across tab switches!
        toast.style.transition = 'none';
        toast.classList.add('show');
        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                toast.style.transition = '';
            });
        });
    }

    if (currentToastTimer) clearTimeout(currentToastTimer);
    currentToastTimer = setTimeout(() => {
        dismissToast(true);
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

    // ─── Ultra-Advanced Universal Login & OTP Detection Engine ───────────────────
    let hasSignaledLogin = false;
    let detectionTimeout = null;

    function isAuthOrLoginPage() {
        try {
            // 1. URL Analysis (catches 95% of login/signin portals worldwide)
            const pathname = (window.location.pathname || '').toLowerCase();
            const search = (window.location.search || '').toLowerCase();
            const isAuthUrl = /(login|signin|sign-in|auth|account|portal|verify|verification|otp|2fa|mfa|checkout|session|register|signup|passcode|security|gateway|payment|sso|identify)/i.test(pathname) ||
                              /(login|signin|sign-in|auth|account|verify|verification|otp|2fa|mfa)/i.test(search);
            if (isAuthUrl) return true;

            // 2. Mobile / Phone number input detection (Airtel, Amazon, Flipkart, banks, IRCTC, etc.)
            const phoneInputs = document.querySelectorAll('input[type="tel"], input[name*="mobile" i], input[name*="phone" i], input[placeholder*="mobile" i], input[placeholder*="phone" i], input[id*="mobile" i], input[id*="phone" i], input[aria-label*="mobile" i]');
            if (phoneInputs.length > 0) return true;

            // 3. Password input on page
            if (document.querySelector('input[type="password"]')) return true;

            // 4. Segmented OTP inputs (e.g. 3-8 single char boxes)
            const segmented = document.querySelectorAll('input[maxlength="1"], input[maxlength="2"]');
            if (segmented.length >= 3 && segmented.length <= 8) return true;

            // 5. Inputs with OTP or Auth keywords
            const keywords = ['otp', 'code', 'pin', '2fa', 'mfa', 'token', 'verify', 'verification', 'passcode', 'security-code', 'auth', 'mobile', 'phone', 'login', 'signin', 'username'];
            const inputs = document.querySelectorAll('input:not([type="hidden"]):not([disabled])');
            for (let i = 0; i < Math.min(inputs.length, 25); i++) {
                const inp = inputs[i];
                const n = (inp.name || '').toLowerCase();
                const id = (inp.id || '').toLowerCase();
                const ph = (inp.placeholder || '').toLowerCase();
                const aria = (inp.getAttribute('aria-label') || '').toLowerCase();
                const ac = (inp.getAttribute('autocomplete') || '').toLowerCase();
                if (ac === 'one-time-code' || ac === 'current-password' || ac === 'username') return true;
                if (keywords.some(k => n.includes(k) || id.includes(k) || ph.includes(k) || aria.includes(k))) return true;
            }

            // 6. Action buttons (e.g. "Get OTP", "Send OTP", "Login", "Continue")
            const buttons = document.querySelectorAll('button, input[type="submit"], input[type="button"], a[role="button"], [role="button"]');
            const btnRegex = /(send\s*otp|get\s*otp|request\s*otp|resend|verify|continue|sign\s*in|log\s*in|proceed|submit|next|authenticate)/i;
            for (let i = 0; i < Math.min(buttons.length, 25); i++) {
                const text = (buttons[i].textContent || buttons[i].value || '').toLowerCase().trim();
                if (btnRegex.test(text)) return true;
            }

            // 7. Page Headings & Instructions
            const headings = document.querySelectorAll('h1, h2, h3, h4, label, p, .title, .subtitle');
            const otpPhrases = ['login', 'sign in', 'enter mobile', 'enter phone', 'enter otp', 'enter verification code', 'verification code', 'one-time password', '6-digit', '4-digit', 'code sent to', 'security code', 'verify your account', 'two-factor'];
            for (let i = 0; i < Math.min(headings.length, 25); i++) {
                const text = (headings[i].textContent || '').toLowerCase();
                if (otpPhrases.some(p => text.includes(p))) return true;
            }

            // 8. Document Title
            const docTitle = (document.title || '').toLowerCase();
            if (/(login|sign\s*in|verify|verification|otp|authenticate)/i.test(docTitle)) return true;
        } catch (_) {}
        return false;
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
            });
        } catch (_) {}
    }

    function detectLoginPage() {
        if (detectionTimeout) clearTimeout(detectionTimeout);
        detectionTimeout = setTimeout(() => {
            const isLogin = isAuthOrLoginPage();
            if (isLogin) {
                if (!hasSignaledLogin) {
                    hasSignaledLogin = true;
                    safeSendMessage({ action: "pc_login_detected", url: window.location.hostname, title: document.title });
                    
                    if (window.loginPingInterval) clearInterval(window.loginPingInterval);
                    window.loginPingInterval = setInterval(() => {
                        safeSendMessage({ action: "pc_login_detected", url: window.location.hostname, title: document.title });
                    }, 5000);
                }
            }
        }, 250);
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

    // Listen to user clicking "Send OTP", "Request OTP", "Resend Code", "Login", "Continue", etc.
    document.addEventListener('click', (e) => {
        reportUserActivity();
        const target = e.target;
        if (!target) return;
        const el = target.closest('button, a, input[type="button"], input[type="submit"], [role="button"], div[role="button"], span[role="button"]');
        if (el) {
            const btnText = (el.textContent || el.value || el.getAttribute('aria-label') || '').toLowerCase().trim();
            const isOtpButton = /(send|get|request|resend|verify|submit|continue|next|proceed|login|signin|sign in|auth|confirm|generate|pay|checkout)\s*(otp|code|pin|passcode|verification)?/i.test(btnText) ||
                                /^(resend|send otp|get otp|request otp|verify|continue|proceed|submit|next|login|sign in|confirm|generate otp|pay|checkout)$/i.test(btnText) ||
                                (el.id && /otp|resend|verify|login|submit|auth|confirm|checkout/i.test(el.id)) ||
                                (typeof el.className === 'string' && /otp|resend|verify|login|submit|auth|confirm|checkout/i.test(el.className));
            if (isOtpButton) {
                safeSendMessage({
                    action: "otp_intent",
                    host: window.location.hostname,
                    title: document.title
                });
            }
        }
    }, { passive: true });

    // When focusing or typing in ANY input on a webpage, report OTP intent with current domain
    document.addEventListener('focusin', (e) => {
        reportUserActivity();
        const target = e.target;
        if (target && target.tagName === 'INPUT') {
            safeSendMessage({
                action: "otp_intent",
                host: window.location.hostname,
                title: document.title
            });
        }
    }, { passive: true });

    document.addEventListener('input', (e) => {
        reportUserActivity();
        const target = e.target;
        if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA')) {
            safeSendMessage({
                action: "otp_intent",
                host: window.location.hostname,
                title: document.title
            });
        }
    }, { passive: true });

    // Form submission is an unmistakable sign of user intent
    document.addEventListener('submit', () => {
        reportUserActivity();
        safeSendMessage({
            action: "otp_intent",
            host: window.location.hostname,
            title: document.title
        });
    }, { passive: true });

    // SPA routing changes (React / Vue / Angular / Next.js)
    window.addEventListener('popstate', () => detectLoginPage(), { passive: true });
    window.addEventListener('hashchange', () => detectLoginPage(), { passive: true });

    detectLoginPage();
    const targetRoot = document.body || document.documentElement;
    if (targetRoot) {
        const observer = new MutationObserver(() => detectLoginPage());
        observer.observe(targetRoot, { childList: true, subtree: true, attributes: true, attributeFilter: ['type', 'class', 'style'] });
    }

    window.__otpsync_checkToast = checkAndDisplayPendingToast;
})();
