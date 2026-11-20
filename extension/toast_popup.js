// toast_popup.js — Floating Dialog Window for Restricted Tabs (New Tab, etc.)
(function() {
    const params = new URLSearchParams(window.location.search);
    const otp = params.get('otp') || '';
    const sender = params.get('sender') || 'OTP Sync';
    const isDuplicate = params.get('dup') === 'true';

    const titleEl = document.getElementById('toast-title');
    const codeEl = document.getElementById('toast-code');
    const senderEl = document.getElementById('toast-sender');
    const cardEl = document.getElementById('toast-card');

    if (titleEl) titleEl.textContent = isDuplicate ? '✓ SAME OTP RE-SENT' : '✓ OTP COPIED';
    if (codeEl) codeEl.textContent = otp;
    if (senderEl) {
        const displaySender = sender.length > 25 ? sender.substring(0, 23) + '…' : sender;
        senderEl.textContent = isDuplicate ? `from ${displaySender} · Re-copied to Clipboard` : `from ${displaySender} · Copied to Clipboard`;
    }

    try {
        const audio = new Audio('ting_sound.mp3');
        audio.volume = 0.95;
        audio.play().catch(() => {});
    } catch (_) {}

    if (cardEl) {
        cardEl.addEventListener('click', () => {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                navigator.clipboard.writeText(otp).catch(() => {});
            }
            if (titleEl) titleEl.textContent = '✓ COPIED TO CLIPBOARD!';
            setTimeout(() => {
                window.close();
            }, 350);
        });
    }

    const duration = isDuplicate ? 9000 : 6000;
    setTimeout(() => {
        window.close();
    }, duration);
})();
