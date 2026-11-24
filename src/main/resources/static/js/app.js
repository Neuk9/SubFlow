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
        return `<span class="sf-service-logo" style="--brand:${escapeHtml(service.brandColor)};--logo:url('/logos/${encodeURIComponent(service.logo)}.svg')" aria-hidden="true"></span>`;
    }
    const initial = Array.from(service.name)[0] || '?';
    return `<span class="sf-service-logo is-fallback" style="background-color:${escapeHtml(service.categoryColor)}1f;" aria-hidden="true">${escapeHtml(initial)}</span>`;
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
                <span class="sf-plan-name">${escapeHtml(plan.name)}</span>
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
            eventsByDay[d].forEach(ev => {
                gridHtml += `<span class="cal-event-pill" style="--c: ${escapeHtml(ev.color)};" title="${escapeHtml(ev.serviceName)}: ${Number(ev.fee).toLocaleString()}원">
                    <span class="cal-event-dot"></span>
                    <span class="cal-event-name">${escapeHtml(ev.serviceName)}</span>
                </span>`;
            });
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
                            color: '#667085',
                            font: { size: 12 }
                        }
                    },
                    tooltip: {
                        backgroundColor: '#101828',
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
                    backgroundColor: '#335cff',
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
                            color: '#667085',
                            font: { size: 11 },
                            autoSkip: false,
                            maxRotation: 30,
                            minRotation: 0
                        }
                    },
                    y: {
                        beginAtZero: true,
                        grid: { color: '#eef1f6' },
                        border: { display: false },
                        ticks: {
                            color: '#667085',
                            font: { size: 11 },
                            callback: value => Number(value).toLocaleString()
                        }
                    }
                },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: '#101828',
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
