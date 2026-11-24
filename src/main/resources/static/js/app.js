/* ==========================================================================
   SubFlow - dashboard behaviours
   ========================================================================== */

const todayObj = new Date();
const todayString = `${todayObj.getFullYear()}-${String(todayObj.getMonth() + 1).padStart(2, '0')}-${String(todayObj.getDate()).padStart(2, '0')}`;

document.getElementById('startDate').value = todayString;
document.getElementById('todayLabel').innerText = todayObj.toLocaleDateString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short'
});

/* --------------------------------------------------------------------------
   Service catalog (service -> plan auto-fill)
   -------------------------------------------------------------------------- */

let serviceCatalog = [];
let lastAutoFill = null;
let selectedServiceId = null;
let currentFilter = 'all';
let searchTerm = '';

async function loadServiceCatalog() {
    try {
        const res = await fetch('/api/services');
        if (!res.ok) return;
        serviceCatalog = await res.json();
    } catch (e) {
        console.error('서비스 카탈로그를 불러오지 못했습니다.', e);
    } finally {
        renderServiceFilters();
        renderServiceGrid(currentFilter);
    }
}

function renderServiceFilters() {
    const container = document.getElementById('serviceFilters');
    const categories = [...new Set(serviceCatalog.map(service => service.categoryDescription))];
    const chips = ['전체', ...categories].map((label, index) => {
        const value = index === 0 ? 'all' : label;
        return `<button type="button" class="sf-filter-chip ${value === currentFilter ? 'active' : ''}"
                        data-filter="${escapeHtml(value)}" aria-pressed="${value === currentFilter}">${escapeHtml(label)}</button>`;
    });
    container.innerHTML = chips.join('');
}

function serviceLogoHtml(service) {
    if (service.logo) {
        if (service.logoType === 'png') {
            return `<span class="sf-service-tile" aria-hidden="true"><img class="sf-service-img" src="/logos/${encodeURIComponent(service.logo)}.png" alt=""></span>`;
        }
        return `<span class="sf-service-tile" aria-hidden="true"><span class="sf-service-logo" style="--brand:${escapeHtml(service.brandColor)};--logo:url('/logos/${encodeURIComponent(service.logo)}.svg')"></span></span>`;
    }
    const initial = Array.from(service.name)[0] || '?';
    return `<span class="sf-service-tile is-fallback" aria-hidden="true">${escapeHtml(initial)}</span>`;
}

function renderServiceGrid(filter = currentFilter) {
    const grid = document.getElementById('serviceGrid');
    const term = searchTerm.trim().toLowerCase();
    const services = serviceCatalog.filter(service => {
        const haystack = `${service.name} ${service.id} ${service.categoryDescription}`.toLowerCase();
        const matchesCategory = filter === 'all' || service.categoryDescription === filter;
        const matchesSearch = !term || haystack.includes(term);
        return matchesCategory && matchesSearch;
    });

    const empty = services.length === 0
        ? `<div class="sf-grid-empty"><i class="bi bi-search"></i><p>검색 결과가 없습니다. 직접 입력해 주세요.</p></div>`
        : '';

    const cards = services.map(service => `
        <button type="button" class="sf-service-card ${service.id === selectedServiceId ? 'is-selected' : ''}" data-service-id="${escapeHtml(service.id)}">
            ${serviceLogoHtml(service)}
            <span class="sf-service-name">${escapeHtml(service.name)}</span>
        </button>`).join('');

    grid.innerHTML = empty + cards;
}

function handleServiceGridClick(event) {
    const card = event.target.closest('[data-service-id], [data-direct]');
    if (!card) return;

    if (card.dataset.direct !== undefined) {
        selectDirectEntry();
        return;
    }

    const service = serviceCatalog.find(item => item.id === card.dataset.serviceId);
    if (service) selectService(service);
}

function handleServiceFilterClick(event) {
    const chip = event.target.closest('[data-filter]');
    if (!chip) return;

    currentFilter = chip.dataset.filter;
    document.querySelectorAll('#serviceFilters .sf-filter-chip').forEach(item => {
        const active = item.dataset.filter === currentFilter;
        item.classList.toggle('active', active);
        item.setAttribute('aria-pressed', String(active));
    });
    renderServiceGrid(currentFilter);
}

function focusSoon(element) {
    if (!element) return;
    setTimeout(() => element.focus({ preventScroll: true }), 50);
}

function resetModalScroll() {
    const body = document.querySelector('#createModal .modal-body');
    if (body) body.scrollTop = 0;
}

function selectService(service) {
    selectedServiceId = service.id;
    renderSelectedService(service);
    document.getElementById('planSection').classList.remove('d-none');
    renderPlans(service);
    applyPlan(service, service.plans[0]);
    showDetailsStep();
    focusSoon(document.querySelector('#planOptions input[name="servicePlan"]'));
}

function selectDirectEntry() {
    selectedServiceId = null;
    document.getElementById('planSection').classList.add('d-none');
    document.getElementById('planOptions').innerHTML = '';

    if (lastAutoFill) {
        const nameInput = document.getElementById('serviceName');
        const feeInput = document.getElementById('fee');
        if (nameInput.value === lastAutoFill.name) nameInput.value = '';
        if (feeInput.value === lastAutoFill.fee) feeInput.value = '';
        lastAutoFill = null;
        updatePreview();
    }

    renderSelectedService(null);
    showDetailsStep();
    focusSoon(document.getElementById('serviceName'));
}

function renderSelectedService(service) {
    const box = document.getElementById('selectedService');
    if (service) {
        box.innerHTML = `
            ${serviceLogoHtml(service)}
            <div>
                <div class="sf-selected-name">${escapeHtml(service.name)}</div>
                <div class="sf-selected-meta">${escapeHtml(service.categoryDescription)} · 요금제를 선택하세요</div>
            </div>`;
        return;
    }

    box.innerHTML = `
        <span class="sf-direct-plus" aria-hidden="true"><i class="bi bi-pencil"></i></span>
        <div>
            <div class="sf-selected-name">직접 입력</div>
            <div class="sf-selected-meta">서비스 정보를 직접 입력하세요</div>
        </div>`;
}

function showDetailsStep() {
    document.getElementById('stepService').classList.add('d-none');
    document.getElementById('stepDetails').classList.remove('d-none');
    document.getElementById('createSubmitBtn').classList.remove('d-none');
    resetModalScroll();
}

function showServiceStep(focusSearch = false) {
    document.getElementById('stepDetails').classList.add('d-none');
    document.getElementById('stepService').classList.remove('d-none');
    document.getElementById('createSubmitBtn').classList.add('d-none');
    renderServiceGrid(currentFilter);
    resetModalScroll();
    focusSoon(focusSearch ? document.getElementById('serviceSearch') : document.querySelector('#serviceGrid .sf-service-card'));
}

function renderPlans(service) {
    const container = document.getElementById('planOptions');
    container.innerHTML = service.plans.map((plan, index) => `
        <label class="sf-plan-card">
            <input type="radio" name="servicePlan" value="${escapeHtml(plan.id)}" ${index === 0 ? 'checked' : ''}>
            <span class="sf-plan-main">
                <span class="sf-plan-name">${escapeHtml(plan.name)}${plan.student ? '<span class="sf-plan-tag">학생</span>' : ''}</span>
                <span class="sf-plan-price">${Number(plan.fee).toLocaleString()}원 <small>/${plan.billingCycle === 'YEARLY' ? '연' : '월'}</small></span>
            </span>
        </label>`).join('');

    container.querySelectorAll('input[name="servicePlan"]').forEach(input => {
        input.addEventListener('change', () => {
            const plan = service.plans.find(item => item.id === input.value);
            if (plan) applyPlan(service, plan);
        });
    });
}

function applyPlan(service, plan) {
    const fee = String(Number(plan.fee));

    document.getElementById('serviceName').value = plan.displayName;
    document.getElementById('category').value = service.category;
    document.getElementById('billingCycle').value = plan.billingCycle;
    document.getElementById('fee').value = fee;
    lastAutoFill = { name: plan.displayName, fee };

    updatePreview();
}

/* --------------------------------------------------------------------------
   Live preview
   -------------------------------------------------------------------------- */

let previewSeq = 0;

async function updatePreview() {
    previewSeq += 1;
    const seq = previewSeq;

    const fee = document.getElementById('fee').value;
    const cycle = document.getElementById('billingCycle').value;
    const billingDay = document.getElementById('billingDay').value;
    const startDate = document.getElementById('startDate').value;

    const dayContainer = document.getElementById('billingDayContainer');
    dayContainer.style.opacity = cycle === 'MONTHLY' ? '1' : '0.5';

    if (!fee || fee <= 0) {
        document.getElementById('previewNextBilling').innerText = '-';
        document.getElementById('previewMonthlyExpense').innerText = '-';
        document.getElementById('previewDDay').innerText = '';
        return;
    }

    let url = `/api/subscriptions/calculate-preview?fee=${fee}&billingCycle=${cycle}`;
    if (billingDay) url += `&billingDay=${billingDay}`;
    if (startDate) url += `&startDate=${startDate}`;

    try {
        const res = await fetch(url);
        if (seq !== previewSeq) return;
        if (!res.ok) return;

        const data = await res.json();
        if (seq !== previewSeq) return;

        document.getElementById('previewNextBilling').innerText = data.nextBillingDate;
        document.getElementById('previewMonthlyExpense').innerText = Number(data.monthlyExpense).toLocaleString();
        document.getElementById('previewDDay').innerText = `D-${data.dDay}`;
    } catch (e) {
        console.error(e);
    }
}

/* --------------------------------------------------------------------------
   Create subscription
   -------------------------------------------------------------------------- */

async function submitSubscription() {
    const serviceName = document.getElementById('serviceName').value.trim();
    const category = document.getElementById('category').value;
    const fee = document.getElementById('fee').value;
    const billingCycle = document.getElementById('billingCycle').value;
    const billingDay = document.getElementById('billingDay').value;
    const startDate = document.getElementById('startDate').value;
    const remindDaysBefore = document.getElementById('remindDaysBefore').value;
    const memo = document.getElementById('memo').value.trim();

    if (!serviceName || !fee) {
        showToast('서비스명과 금액을 입력해 주세요.', 'danger');
        return;
    }

    const submitBtn = document.getElementById('createSubmitBtn');
    submitBtn.disabled = true;

    const payload = {
        serviceName,
        category,
        fee: parseFloat(fee),
        billingCycle,
        billingDay: billingDay ? parseInt(billingDay) : null,
        startDate: startDate || null,
        remindDaysBefore: parseInt(remindDaysBefore),
        memo
    };

    try {
        const res = await fetch('/api/subscriptions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            showToast(`'${serviceName}' 구독이 등록되었습니다.`, 'success');
            setTimeout(() => location.reload(), 700);
            return;
        }

        let message = '입력값을 확인해 주세요.';
        try {
            const err = await res.json();
            message = err.message || message;
        } catch (e) {
            // JSON이 아닌 응답은 기본 메시지 사용
        }
        showToast('등록 실패: ' + message, 'danger');
    } catch (e) {
        showToast('네트워크 오류로 등록에 실패했습니다.', 'danger');
    } finally {
        submitBtn.disabled = false;
    }
}

/* --------------------------------------------------------------------------
   Delete subscription
   -------------------------------------------------------------------------- */

async function deleteSubscription(button) {
    const id = button.dataset.id;
    if (!confirm('정말 이 구독을 삭제하시겠습니까?')) return;

    try {
        const res = await fetch(`/api/subscriptions/${id}`, { method: 'DELETE' });
        if (res.ok) {
            showToast('구독이 삭제되었습니다.', 'secondary');
            setTimeout(() => location.reload(), 500);
            return;
        }
        showToast('삭제에 실패했습니다.', 'danger');
    } catch (e) {
        showToast('네트워크 오류로 삭제에 실패했습니다.', 'danger');
    }
}

/* --------------------------------------------------------------------------
   Cancel subscription
   -------------------------------------------------------------------------- */

let cancelModalInstance = null;

function cancelModal(button) {
    document.getElementById('cancelSubscriptionId').value = button.dataset.id;
    document.getElementById('cancelServiceName').innerText = button.dataset.name;
    document.getElementById('cancelModeImmediate').checked = true;
    document.getElementById('cancelDate').value = '';
    document.getElementById('cancelDate').min = todayString;
    document.getElementById('cancelError').classList.add('d-none');
    syncCancelMode();

    cancelModalInstance = cancelModalInstance || new bootstrap.Modal(document.getElementById('cancelModal'));
    cancelModalInstance.show();
}

function syncCancelMode() {
    const scheduled = document.getElementById('cancelModeScheduled').checked;
    document.getElementById('cancelDateGroup').classList.toggle('d-none', !scheduled);
    document.getElementById('cancelDate').required = scheduled;
    document.getElementById('cancelError').classList.add('d-none');
}

async function submitCancel() {
    const id = document.getElementById('cancelSubscriptionId').value;
    const scheduled = document.getElementById('cancelModeScheduled').checked;
    const date = document.getElementById('cancelDate').value;
    const errorEl = document.getElementById('cancelError');

    const showError = (message) => {
        errorEl.innerText = message;
        errorEl.classList.remove('d-none');
    };

    if (scheduled) {
        if (!date) {
            showError('해지 예정일을 선택해 주세요.');
            return;
        }
        if (date < todayString) {
            showError('오늘 이후 날짜를 선택해 주세요.');
            return;
        }
    }

    const submitBtn = document.getElementById('cancelSubmitBtn');
    submitBtn.disabled = true;
    try {
        const res = await fetch(`/api/subscriptions/${id}/cancel`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(scheduled ? { scheduledDate: date } : {})
        });

        if (!res.ok) {
            let message = '해지 처리에 실패했습니다.';
            try {
                const error = await res.json();
                message = error.message || message;
            } catch (e) {
                // 응답 본문이 JSON이 아닌 경우 기본 메시지 사용
            }
            showError(message);
            return;
        }

        cancelModalInstance?.hide();
        showToast('해지 처리되었습니다.', 'warning');
        setTimeout(() => location.reload(), 500);
    } catch (e) {
        showError('네트워크 오류로 해지 처리에 실패했습니다.');
    } finally {
        submitBtn.disabled = false;
    }
}

/* --------------------------------------------------------------------------
   Toast
   -------------------------------------------------------------------------- */

function showToast(message, variant = 'primary') {
    const toastEl = document.getElementById('liveToast');
    toastEl.className = `toast align-items-center text-bg-${variant} border-0`;
    document.getElementById('toastMessage').innerText = message;
    new bootstrap.Toast(toastEl, { delay: 3500 }).show();
}

/* --------------------------------------------------------------------------
   Demo batch triggers
   -------------------------------------------------------------------------- */

async function triggerBatch(type) {
    try {
        const res = await fetch(`/api/simulation/${type}`, { method: 'POST' });
        const data = await res.json();
        showToast(data.message || '배치를 실행했습니다.', res.ok ? 'info' : 'danger');
        if (res.ok) {
            loadLogs();
            setTimeout(() => location.reload(), 1200);
        }
    } catch (e) {
        showToast('배치 실행에 실패했습니다.', 'danger');
    }
}

/* --------------------------------------------------------------------------
   Activity log
   -------------------------------------------------------------------------- */

function escapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

async function loadLogs() {
    try {
        const [notifRes, recordRes] = await Promise.all([
            fetch('/api/simulation/notifications'),
            fetch('/api/simulation/records')
        ]);
        const notifs = await notifRes.json();
        const records = await recordRes.json();

        const container = document.getElementById('logsContainer');
        if (notifs.length === 0 && records.length === 0) {
            return;
        }

        let html = '<div class="sf-log-list">';
        notifs.slice(0, 5).forEach(n => {
            html += `<div class="sf-log">
                <span class="sf-log-icon is-warning"><i class="bi bi-bell"></i></span>
                <div>
                    <div class="sf-log-title">${escapeHtml(n.title)}</div>
                    <div class="sf-log-meta">${escapeHtml(n.message)}</div>
                </div>
            </div>`;
        });
        records.slice(0, 5).forEach(r => {
            html += `<div class="sf-log">
                <span class="sf-log-icon is-success"><i class="bi bi-receipt"></i></span>
                <div>
                    <div class="sf-log-title">${escapeHtml(r.serviceName)} · ${Number(r.amount).toLocaleString()}원</div>
                    <div class="sf-log-meta">${escapeHtml(r.billingDate)} · ${escapeHtml(r.statusDescription)}</div>
                </div>
            </div>`;
        });
        html += '</div>';
        container.innerHTML = html;
    } catch (e) {
        console.error(e);
    }
}

/* --------------------------------------------------------------------------
   Calendar
   -------------------------------------------------------------------------- */

let calYear = todayObj.getFullYear();
let calMonth = todayObj.getMonth() + 1;

function changeMonth(delta) {
    calMonth += delta;
    if (calMonth < 1) { calMonth = 12; calYear -= 1; }
    if (calMonth > 12) { calMonth = 1; calYear += 1; }
    loadCalendar();
}

function goToCurrentMonth() {
    calYear = todayObj.getFullYear();
    calMonth = todayObj.getMonth() + 1;
    loadCalendar();
}

async function loadCalendar() {
    document.getElementById('calCurrentTitle').innerText = `${calYear}년 ${calMonth}월`;

    let events = [];
    try {
        const res = await fetch(`/api/subscriptions/calendar?year=${calYear}&month=${calMonth}`);
        if (res.ok) events = await res.json();
    } catch (e) {
        console.error(e);
    }

    const eventsByDay = {};
    events.forEach(e => {
        if (!eventsByDay[e.day]) eventsByDay[e.day] = [];
        eventsByDay[e.day].push(e);
    });

    const firstDayOfMonth = new Date(calYear, calMonth - 1, 1).getDay();
    const daysInMonth = new Date(calYear, calMonth, 0).getDate();
    const daysInPrevMonth = new Date(calYear, calMonth - 1, 0).getDate();

    let gridHtml = '<tr>';
    let col = 0;

    for (let i = firstDayOfMonth - 1; i >= 0; i--) {
        gridHtml += `<td class="calendar-day-cell other-month"><div class="calendar-date-num">${daysInPrevMonth - i}</div></td>`;
        col += 1;
    }

    const isCurrentYearMonth = calYear === todayObj.getFullYear() && calMonth === todayObj.getMonth() + 1;
    const todayDate = todayObj.getDate();

    for (let d = 1; d <= daysInMonth; d++) {
        const isToday = isCurrentYearMonth && d === todayDate;
        gridHtml += `<td class="calendar-day-cell ${isToday ? 'today' : ''}">
            <div class="calendar-date-num">${d}</div>`;

        if (eventsByDay[d]) {
            const dayEvents = eventsByDay[d];
            dayEvents.slice(0, 2).forEach(ev => {
                gridHtml += `<span class="cal-event-pill" style="--c: ${escapeHtml(ev.color)};" title="${escapeHtml(ev.serviceName)}: ${Number(ev.fee).toLocaleString()}원">
                    <span class="cal-event-dot"></span>
                    <span class="cal-event-name">${escapeHtml(ev.serviceName)}</span>
                </span>`;
            });
            if (dayEvents.length > 2) {
                gridHtml += `<span class="cal-more">+${dayEvents.length - 2}</span>`;
            }
        }

        gridHtml += '</td>';
        col += 1;
        if (col % 7 === 0 && d !== daysInMonth) {
            gridHtml += '</tr><tr>';
        }
    }

    let nextD = 1;
    while (col % 7 !== 0) {
        gridHtml += `<td class="calendar-day-cell other-month"><div class="calendar-date-num">${nextD++}</div></td>`;
        col += 1;
    }
    gridHtml += '</tr>';

    document.getElementById('calendarGridBody').innerHTML = gridHtml;
    renderMonthPayments(events);
}

function renderMonthPayments(events) {
    const list = document.getElementById('monthPayments');
    const totalLabel = document.getElementById('monthTotal');
    if (!list) return;

    const sorted = [...events].sort((a, b) => a.day - b.day);
    const total = sorted.reduce((sum, event) => sum + Number(event.fee || 0), 0);
    if (totalLabel) {
        totalLabel.textContent = `${total.toLocaleString('ko-KR')}원`;
    }

    if (sorted.length === 0) {
        list.innerHTML = '<div class="sf-empty-line">이번 달 결제가 없습니다.</div>';
        return;
    }

    list.innerHTML = sorted.map(event => {
        const date = String(event.billingDate).slice(5).replace('-', '.');
        return `<div class="sf-pay-row">
            <span class="sf-pay-date">${escapeHtml(date)}</span>
            <span class="sf-pay-dot" style="background:${escapeHtml(event.color)}"></span>
            <span class="sf-pay-name">${escapeHtml(event.serviceName)}</span>
            <span class="sf-pay-amount">${Number(event.fee).toLocaleString('ko-KR')}</span>
        </div>`;
    }).join('');
}

/* --------------------------------------------------------------------------
   Insights (savings / duplicates)
   -------------------------------------------------------------------------- */

function normalizeServiceName(value) {
    return String(value || '').toLowerCase().replace(/[\s\-_/()+.]+/g, '');
}

function findCatalogService(serviceName) {
    const target = normalizeServiceName(serviceName);
    if (!target) return null;
    return serviceCatalog
        .filter(service => target.includes(normalizeServiceName(service.name)))
        .sort((a, b) => b.name.length - a.name.length)[0] || null;
}

async function renderInsights() {
    const savingsBox = document.getElementById('insightSavings');
    const duplicatesBox = document.getElementById('insightDuplicates');
    if (!savingsBox || !duplicatesBox) return;

    try {
        const res = await fetch('/api/insights');
        if (!res.ok) return;
        const data = await res.json();

        const items = [];
        (data.annualSavings || []).forEach(item => items.push({
            title: `${item.serviceName} 연간 전환`,
            detail: `${item.planName} · 월 ${Number(item.currentMonthly).toLocaleString()}원 → 연 ${Number(item.yearlyFee).toLocaleString()}원`,
            saving: item.saving,
            period: '년'
        }));
        (data.studentSavings || []).forEach(item => items.push({
            title: `${item.serviceName} 학생 요금`,
            detail: `월 ${Number(item.currentMonthly).toLocaleString()}원 → ${Number(item.studentFee).toLocaleString()}원`,
            saving: item.saving,
            period: '월'
        }));

        const annualTotal = Number(data.annualSavingTotal || 0);
        const studentTotal = Number(data.studentSavingTotal || 0) * 12;
        document.getElementById('insightSavingTotal').textContent =
            (annualTotal + studentTotal) > 0
                ? `연 ${(annualTotal + studentTotal).toLocaleString()}원 절약 가능`
                : '절약 가능한 항목이 없습니다';

        savingsBox.innerHTML = items.length === 0
            ? '<div class="sf-empty-line">지금은 절약할 수 있는 항목이 없습니다.</div>'
            : items.map(item => `
                <div class="sf-insight-row">
                    <div class="sf-insight-main">
                        <div class="sf-insight-title">${escapeHtml(item.title)}</div>
                        <div class="sf-insight-detail">${escapeHtml(item.detail)}</div>
                    </div>
                    <div class="sf-insight-saving">-${Number(item.saving).toLocaleString()}원/${item.period}</div>
                </div>`).join('');

        const groups = data.duplicateGroups || [];
        const duplicateTotal = groups.reduce((sum, group) => sum + Number(group.monthlyTotal), 0);
        document.getElementById('insightDuplicateTotal').textContent =
            groups.length > 0 ? `월 ${duplicateTotal.toLocaleString()}원 겹침` : '중복 구독이 없습니다';

        duplicatesBox.innerHTML = groups.length === 0
            ? '<div class="sf-empty-line">같은 용도의 구독이 겹치지 않습니다.</div>'
            : groups.map(group => `
                <div class="sf-insight-row">
                    <div class="sf-insight-main">
                        <div class="sf-insight-title">${escapeHtml(group.group)}</div>
                        <div class="sf-insight-detail">${group.serviceNames.map(escapeHtml).join(' · ')}</div>
                    </div>
                    <div class="sf-insight-warn">월 ${Number(group.monthlyTotal).toLocaleString()}원</div>
                </div>`).join('');
    } catch (e) {
        console.error('인사이트를 불러오지 못했습니다.', e);
    }
}

function openCancelGuide(button) {
    const serviceName = button.dataset.name || '';
    const service = findCatalogService(serviceName);
    document.getElementById('cancelGuideService').textContent = serviceName;
    const body = document.getElementById('cancelGuideBody');

    if (service && service.cancelGuide) {
        body.innerHTML = `
            <p class="sf-guide-step">${escapeHtml(service.cancelGuide)}</p>
            ${service.cancelUrl ? `<a class="sf-guide-link" href="${escapeHtml(service.cancelUrl)}" target="_blank" rel="noopener">해지 페이지 열기</a>` : ''}`;
    } else {
        body.innerHTML = '<p class="sf-guide-step">등록된 해지 안내가 없습니다. 서비스 앱의 설정에서 구독을 해지해 주세요.</p>';
    }

    bootstrap.Modal.getOrCreateInstance(document.getElementById('cancelGuideModal')).show();
}

/* --------------------------------------------------------------------------
   Shared subscription settlement
   -------------------------------------------------------------------------- */

const shareState = { subscriptionId: null, period: null };

function currentPeriod() {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function openShareModal(button) {
    shareState.subscriptionId = button.dataset.id;
    shareState.period = currentPeriod();
    document.getElementById('shareServiceName').textContent = button.dataset.name || '';
    document.getElementById('shareMemberName').value = '';
    document.getElementById('shareAmount').value = '';
    document.getElementById('shareError').classList.add('d-none');
    document.getElementById('shareList').innerHTML = '<div class="sf-empty-line">불러오는 중…</div>';
    loadShares();
    bootstrap.Modal.getOrCreateInstance(document.getElementById('shareModal')).show();
}

async function loadShares() {
    if (!shareState.subscriptionId) return;
    try {
        const res = await fetch(`/api/subscriptions/${shareState.subscriptionId}/shares?period=${shareState.period}`);
        if (!res.ok) return;
        renderShares(await res.json());
    } catch (e) {
        console.error('공유 멤버를 불러오지 못했습니다.', e);
    }
}

function renderShares(shares) {
    const total = shares.reduce((sum, share) => sum + Number(share.shareAmount), 0);
    const received = shares
        .filter(share => share.settled)
        .reduce((sum, share) => sum + Number(share.shareAmount), 0);

    document.getElementById('shareReceived').textContent = `${received.toLocaleString('ko-KR')}원`;
    document.getElementById('sharePending').textContent = `${(total - received).toLocaleString('ko-KR')}원`;

    document.getElementById('shareList').innerHTML = shares.length === 0
        ? '<div class="sf-empty-line">아직 함께 쓰는 멤버가 없습니다.</div>'
        : shares.map(share => `
            <div class="sf-share-row">
                <input type="checkbox" class="form-check-input" ${share.settled ? 'checked' : ''}
                       aria-label="${escapeHtml(share.memberName)} 정산 완료"
                       onchange="toggleShareSettle(${share.id}, this.checked)">
                <div class="sf-share-main">
                    <div class="sf-share-name">${escapeHtml(share.memberName)}</div>
                    <div class="sf-share-amount">${Number(share.shareAmount).toLocaleString('ko-KR')}원</div>
                </div>
                <button type="button" class="sf-icon-btn" aria-label="${escapeHtml(share.memberName)} 삭제"
                        onclick="removeShare(${share.id})"><i class="bi bi-x-lg"></i></button>
            </div>`).join('');
}

function showShareError(message) {
    const box = document.getElementById('shareError');
    box.textContent = message;
    box.classList.remove('d-none');
}

async function addShare(event) {
    event.preventDefault();
    const memberName = document.getElementById('shareMemberName').value.trim();
    const shareAmount = document.getElementById('shareAmount').value;

    if (!memberName || !shareAmount) {
        showShareError('멤버 이름과 분담 금액을 입력해 주세요.');
        return;
    }
    document.getElementById('shareError').classList.add('d-none');

    try {
        const res = await fetch(`/api/subscriptions/${shareState.subscriptionId}/shares`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ memberName, shareAmount: Number(shareAmount) })
        });
        if (!res.ok) {
            const error = await res.json().catch(() => ({}));
            showShareError(error.message || '멤버를 추가하지 못했습니다.');
            return;
        }
        document.getElementById('shareMemberName').value = '';
        document.getElementById('shareAmount').value = '';
        await loadShares();
    } catch (e) {
        showShareError('멤버를 추가하지 못했습니다.');
    }
}

async function toggleShareSettle(shareId, settled) {
    await fetch(`/api/shares/${shareId}/settlement`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ period: shareState.period, settled })
    });
    await loadShares();
}

async function removeShare(shareId) {
    if (!confirm('이 멤버를 삭제할까요?')) return;
    await fetch(`/api/shares/${shareId}`, { method: 'DELETE' });
    await loadShares();
}

/* --------------------------------------------------------------------------
   Charts
   -------------------------------------------------------------------------- */

let donutChartInstance = null;
let barChartInstance = null;

if (window.Chart) {
    Chart.defaults.font.family = '"Pretendard Variable", Pretendard, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif';
    Chart.defaults.color = '#667085';
}

async function renderCharts() {
    try {
        const [summaryRes, subsRes] = await Promise.all([
            fetch('/api/subscriptions/summary'),
            fetch('/api/subscriptions?status=ACTIVE')
        ]);
        const summary = await summaryRes.json();
        const subscriptions = await subsRes.json();

        document.getElementById('donutTotal').innerText = Number(summary.totalMonthlyExpense ?? 0).toLocaleString();

        const donutCtx = document.getElementById('categoryDonutChart').getContext('2d');
        const catLabels = summary.categoryExpenses.map(c => c.categoryDescription);
        const catData = summary.categoryExpenses.map(c => c.amount);

        if (donutChartInstance) donutChartInstance.destroy();
        renderInsights();

        donutChartInstance = new Chart(donutCtx, {
            type: 'doughnut',
            data: {
                labels: catLabels,
                datasets: [{
                    data: catData,
                    backgroundColor: summary.categoryExpenses.map(c => c.color),
                    borderWidth: 0,
                    spacing: 2,
                    hoverOffset: 4
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '72%',
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            usePointStyle: true,
                            pointStyle: 'circle',
                            boxWidth: 7,
                            boxHeight: 7,
                            padding: 16,
                            color: '#8b8b93',
                            font: { size: 12 }
                        }
                    },
                    tooltip: {
                        backgroundColor: '#1f1f26',
                        padding: 10,
                        cornerRadius: 8,
                        displayColors: false,
                        callbacks: {
                            label: ctx => ` ${ctx.label}  ${Number(ctx.raw).toLocaleString()}원`
                        }
                    }
                }
            }
        });

        const barCtx = document.getElementById('servicesBarChart').getContext('2d');
        const subLabels = subscriptions.map(s => s.serviceName);
        const subData = subscriptions.map(s => s.monthlyExpense);

        if (barChartInstance) barChartInstance.destroy();
        barChartInstance = new Chart(barCtx, {
            type: 'bar',
            data: {
                labels: subLabels,
                datasets: [{
                    label: '월 예상 지출',
                    data: subData,
                    backgroundColor: '#0a84ff',
                    borderRadius: 8,
                    maxBarThickness: 40
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                layout: { padding: { top: 8 } },
                scales: {
                    x: {
                        grid: { display: false },
                        border: { display: false },
                        ticks: {
                            color: '#8b8b93',
                            font: { size: 11 },
                            autoSkip: false,
                            maxRotation: 30,
                            minRotation: 0
                        }
                    },
                    y: {
                        beginAtZero: true,
                        grid: { color: '#26262c' },
                        border: { display: false },
                        ticks: {
                            color: '#8b8b93',
                            font: { size: 11 },
                            callback: value => Number(value).toLocaleString()
                        }
                    }
                },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: '#1f1f26',
                        padding: 10,
                        cornerRadius: 8,
                        displayColors: false,
                        callbacks: {
                            label: ctx => ` 월 환산 ${Number(ctx.raw).toLocaleString()}원`
                        }
                    }
                }
            }
        });
    } catch (e) {
        console.error('차트 렌더링 실패:', e);
    }
}

/* --------------------------------------------------------------------------
   CSV 내역 가져오기
   -------------------------------------------------------------------------- */

const CATEGORY_LABELS = {
    OTT: 'OTT/영상', MUSIC: '음악', PRODUCTIVITY: '생산성/업무', SHOPPING: '쇼핑/멤버십',
    CLOUD: '클라우드/스토리지', GAME: '게임', EDUCATION: '교육/도서', OTHER: '기타'
};
const CYCLE_LABELS = { MONTHLY: '매월', YEARLY: '매년', WEEKLY: '매주' };

const importState = { file: null, preview: null, done: false, loading: false };

function resetImportState() {
    importState.file = null;
    importState.preview = null;
    importState.done = false;
    importState.loading = false;
    document.getElementById('importFileInput').value = '';
    document.getElementById('importFileName').classList.add('d-none');
    document.getElementById('importStep1').classList.remove('d-none');
    document.getElementById('importStep2').classList.add('d-none');
    document.getElementById('importStep3').classList.add('d-none');
    document.getElementById('importMapping').classList.add('d-none');
    document.getElementById('importPreviewBody').classList.remove('d-none');
    document.getElementById('importPreviewBtn').classList.remove('d-none');
    document.getElementById('importPreviewBtn').disabled = true;
    document.getElementById('importPreviewBtn').textContent = '다음';
    document.getElementById('importConfirmBtn').classList.add('d-none');
    document.getElementById('importSelectAll').checked = false;
    hideImportError();
}

function selectImportFile(input) {
    const file = input.files && input.files[0];
    if (!file) return;
    setImportFile(file);
}

function setImportFile(file) {
    importState.file = file;
    const label = document.getElementById('importFileName');
    label.textContent = `${file.name} · ${Math.max(1, Math.round(file.size / 1024))}KB`;
    label.classList.remove('d-none');
    document.getElementById('importPreviewBtn').disabled = false;
}

function setupImportDropZone() {
    const zone = document.getElementById('importDropZone');
    if (!zone) return;
    ['dragenter', 'dragover'].forEach(type => zone.addEventListener(type, event => {
        event.preventDefault();
        zone.classList.add('is-over');
    }));
    ['dragleave', 'drop'].forEach(type => zone.addEventListener(type, event => {
        event.preventDefault();
        zone.classList.remove('is-over');
    }));
    zone.addEventListener('drop', event => {
        const file = event.dataTransfer.files && event.dataTransfer.files[0];
        if (file) setImportFile(file);
    });
}

function showImportError(message) {
    const box = document.getElementById('importError');
    box.textContent = message;
    box.classList.remove('d-none');
}

function hideImportError() {
    const box = document.getElementById('importError');
    box.textContent = '';
    box.classList.add('d-none');
}

async function previewImport(useMapping = false) {
    if (!importState.file || importState.loading) return;
    hideImportError();

    const form = new FormData();
    form.append('file', importState.file);
    if (useMapping) {
        const date = document.getElementById('mapDate').value;
        const merchant = document.getElementById('mapMerchant').value;
        const amount = document.getElementById('mapAmount').value;
        if (date === '' || merchant === '' || amount === '') {
            showImportError('날짜·가맹점·금액 컬럼을 모두 선택해 주세요.');
            return;
        }
        form.append('dateColumn', date);
        form.append('merchantColumn', merchant);
        form.append('amountColumn', amount);
    }

    setImportLoading(true);
    try {
        const res = await fetch('/api/imports/preview', { method: 'POST', body: form });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || '파일을 분석하지 못했습니다.');
        importState.preview = data;

        document.getElementById('importStep2').classList.remove('d-none');
        if (data.needsMapping) {
            renderImportMapping(data);
            document.getElementById('importMapping').classList.remove('d-none');
            document.getElementById('importPreviewBody').classList.add('d-none');
            document.getElementById('importPreviewBtn').classList.add('d-none');
            document.getElementById('importConfirmBtn').classList.add('d-none');
        } else {
            renderImportPreview(data);
            document.getElementById('importMapping').classList.add('d-none');
            document.getElementById('importPreviewBody').classList.remove('d-none');
            document.getElementById('importPreviewBtn').classList.add('d-none');
            document.getElementById('importConfirmBtn').classList.remove('d-none');
        }
    } catch (e) {
        showImportError(e.message);
    } finally {
        setImportLoading(false);
    }
}

function setImportLoading(loading) {
    importState.loading = loading;
    const button = document.getElementById('importPreviewBtn');
    button.disabled = loading || !importState.file;
    button.textContent = loading ? '분석 중…' : '다음';
}

function renderImportMapping(data) {
    const headers = data.headers && data.headers.length ? data.headers : ((data.sampleRows && data.sampleRows[0]) || []);
    const selects = [
        { id: 'mapDate', value: data.columns ? data.columns.date : 0 },
        { id: 'mapMerchant', value: data.columns ? data.columns.merchant : 1 },
        { id: 'mapAmount', value: data.columns ? data.columns.amount : 2 }
    ];
    selects.forEach(select => {
        const element = document.getElementById(select.id);
        element.innerHTML = headers.map((header, index) =>
            `<option value="${index}">${escapeHtml(header || `컬럼 ${index + 1}`)}</option>`
        ).join('');
        if (select.value >= 0 && select.value < headers.length) {
            element.value = String(select.value);
        }
    });
}

function renderImportPreview(data) {
    const auto = data.rows.filter(row => row.matchType === 'AUTO' && !row.alreadyRecorded).length;
    const review = data.rows.filter(row => row.matchType === 'REVIEW').length;
    document.getElementById('importSummary').innerHTML = [
        `<span>분석 ${data.rowCount}건</span>`,
        `<span>자동 매칭 ${auto}건</span>`,
        `<span>확인 필요 ${review}건</span>`,
        `<span>신규 후보 ${data.candidates.length}개</span>`,
        `<span>일회성 ${data.oneOffCount}건</span>`
    ].join('');

    document.getElementById('importRows').innerHTML = data.rows.map((row, index) => {
        const selectable = row.matchedSubscriptionId !== null && !row.alreadyRecorded;
        const checked = row.matchType === 'AUTO' && selectable;
        const label = row.matchType === 'AUTO' ? '자동' : row.matchType === 'REVIEW' ? '확인 필요' : '-';
        const confidence = row.matchedSubscriptionId !== null ? `${Math.round(row.confidence * 100)}%` : '-';
        const note = row.alreadyRecorded ? '<span class="sf-import-badge">이미 기록됨</span>' : '';
        return `<tr class="${row.alreadyRecorded ? 'is-muted' : ''}">
            <td><input type="checkbox" class="form-check-input import-row-check" data-index="${index}"
                       ${checked ? 'checked' : ''} ${selectable ? '' : 'disabled'}></td>
            <td>${escapeHtml(row.billingDate)}</td>
            <td>${escapeHtml(row.merchant)}</td>
            <td class="text-end">${Number(row.amount).toLocaleString('ko-KR')}원</td>
            <td>${escapeHtml(row.matchedServiceName || '-')} ${note}</td>
            <td>${label} ${confidence}</td>
        </tr>`;
    }).join('');

    const candidates = data.candidates || [];
    document.getElementById('importCandidates').innerHTML = candidates.length === 0 ? '' : `
        <div class="sf-import-section-title">신규 구독 후보</div>
        <div class="sf-candidate-list">
            ${candidates.map((candidate, index) => `
                <label class="sf-candidate">
                    <input type="checkbox" class="form-check-input import-candidate-check" data-index="${index}" checked>
                    <div class="sf-candidate-logo ${candidate.logoSlug ? '' : 'is-fallback'}"
                         ${candidate.logoSlug ? `style="--logo:url('/logos/${encodeURIComponent(candidate.logoSlug)}.svg')"` : ''}>
                        ${candidate.logoSlug ? '' : escapeHtml(Array.from(candidate.suggestedName)[0] || '?')}
                    </div>
                    <div class="sf-candidate-main">
                        <div class="sf-candidate-name">${escapeHtml(candidate.suggestedName)}</div>
                        <div class="sf-candidate-meta">
                            ${escapeHtml(CATEGORY_LABELS[candidate.suggestedCategory] || candidate.suggestedCategory)} ·
                            ${escapeHtml(CYCLE_LABELS[candidate.suggestedBillingCycle] || candidate.suggestedBillingCycle)} ·
                            ${Number(candidate.suggestedFee).toLocaleString('ko-KR')}원 ·
                            ${candidate.occurrences}회 반복
                        </div>
                    </div>
                </label>`).join('')}
        </div>`;

    const skipped = data.skipped || [];
    const skippedBox = document.getElementById('importSkipped');
    if (skipped.length === 0) {
        skippedBox.classList.add('d-none');
        skippedBox.innerHTML = '';
    } else {
        skippedBox.classList.remove('d-none');
        skippedBox.textContent = `건너뛴 행 ${skipped.length}건: ` +
            skipped.slice(0, 5).map(item => `${item.rowNumber}행(${item.reason})`).join(', ') +
            (skipped.length > 5 ? ' 외' : '');
    }
}

function toggleAllImportRows(checked) {
    document.querySelectorAll('#importRows .import-row-check:not(:disabled)')
        .forEach(checkbox => { checkbox.checked = checked; });
}

async function confirmImport() {
    if (!importState.preview || importState.loading) return;
    hideImportError();

    const records = [...document.querySelectorAll('#importRows .import-row-check:checked')]
        .map(checkbox => importState.preview.rows[Number(checkbox.dataset.index)])
        .map(row => ({
            subscriptionId: row.matchedSubscriptionId,
            billingDate: row.billingDate,
            amount: row.amount,
            merchant: row.merchant
        }));
    const newSubscriptions = [...document.querySelectorAll('#importCandidates .import-candidate-check:checked')]
        .map(checkbox => importState.preview.candidates[Number(checkbox.dataset.index)])
        .map(candidate => ({
            serviceName: candidate.suggestedName,
            category: candidate.suggestedCategory,
            fee: candidate.suggestedFee,
            billingCycle: candidate.suggestedBillingCycle,
            billingDay: candidate.suggestedBillingDay,
            startDate: candidate.suggestedStartDate,
            memo: 'CSV 가져오기'
        }));

    if (records.length === 0 && newSubscriptions.length === 0) {
        showImportError('적용할 항목을 하나 이상 선택해 주세요.');
        return;
    }

    importState.loading = true;
    const confirmButton = document.getElementById('importConfirmBtn');
    confirmButton.disabled = true;
    confirmButton.textContent = '적용 중…';
    try {
        const res = await fetch('/api/imports/confirm', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ records, newSubscriptions })
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.message || '가져오기에 실패했습니다.');

        importState.done = true;
        document.getElementById('importStep2').classList.add('d-none');
        document.getElementById('importStep3').classList.remove('d-none');
        document.getElementById('importPreviewBtn').classList.add('d-none');
        confirmButton.classList.add('d-none');
        document.getElementById('importResultText').textContent =
            `결제 ${data.recordedCount}건을 기록하고, 구독 ${data.createdSubscriptions}개를 추가했습니다.` +
            (data.skippedCount > 0 ? ` 중복 ${data.skippedCount}건은 건너뛰었습니다.` : '');
    } catch (e) {
        showImportError(e.message);
    } finally {
        importState.loading = false;
        confirmButton.disabled = false;
        confirmButton.textContent = '적용';
    }
}

document.getElementById('importModal').addEventListener('show.bs.modal', resetImportState);
document.getElementById('importModal').addEventListener('hidden.bs.modal', () => {
    if (importState.done) location.reload();
});
setupImportDropZone();

/* --------------------------------------------------------------------------
   Init
   -------------------------------------------------------------------------- */

document.getElementById('serviceGrid').addEventListener('click', handleServiceGridClick);
document.getElementById('serviceFilters').addEventListener('click', handleServiceFilterClick);
document.getElementById('serviceSearch').addEventListener('input', event => {
    searchTerm = event.target.value;
    renderServiceGrid(currentFilter);
});
document.getElementById('createModal').addEventListener('show.bs.modal', () => {
    searchTerm = '';
    document.getElementById('serviceSearch').value = '';
    currentFilter = 'all';
    renderServiceFilters();
    showServiceStep(true);
});
loadServiceCatalog();
loadLogs();
loadCalendar();
