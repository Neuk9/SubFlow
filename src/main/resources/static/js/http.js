/* 공통 HTTP 유틸리티: CSRF 헤더 주입과 HTML 이스케이프 */

function subflowFetch(input, init = {}) {
    const method = (init.method || 'GET').toUpperCase();
    const headers = new Headers(init.headers || {});
    if (!['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
        const token = document.querySelector('meta[name="_csrf"]')?.content;
        const header = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';
        if (token) headers.set(header, token);
    }
    return fetch(input, { ...init, headers, credentials: 'same-origin' });
}

function escapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
