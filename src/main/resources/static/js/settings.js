const OCCUPATION_LABELS = {
    HIGH_SCHOOL: '고등학생', UNIVERSITY: '대학생', GRADUATE: '대학원생',
    WORKER: '직장인', JOB_SEEKER: '구직자', FREELANCER: '프리랜서', OTHER: '기타'
};

const CATEGORY_LABELS = {
    OTT: 'OTT/영상', MUSIC: '음악', PRODUCTIVITY: '생산성/업무', SHOPPING: '쇼핑/멤버십',
    CLOUD: '클라우드/스토리지', GAME: '게임', EDUCATION: '교육/도서', OTHER: '기타'
};

const MEMBERSHIP_LABELS = {
    SKT: 'SKT', KT: 'KT', LG_U_PLUS: 'LG U+', MVNO: '알뜰폰',
    SHINHAN_CARD: '신한카드', SAMSUNG_CARD: '삼성카드', HYUNDAI_CARD: '현대카드',
    KB_CARD: 'KB국민카드', LOTTE_CARD: '롯데카드', HANA_CARD: '하나카드',
    WOORI_CARD: '우리카드', NH_CARD: 'NH농협카드', KAKAO_BANK: '카카오뱅크',
    TOSS_BANK: '토스뱅크', COUPANG_WOW: '쿠팡 와우', NAVER_PLUS: '네이버 플러스',
    BAEMIN_CLUB: '배민 클럽'
};

function extractErrorMessage(error, fallback) {
    if (error && Array.isArray(error.errors) && error.errors.length > 0 && error.errors[0].reason) {
        return error.errors[0].reason;
    }
    return (error && error.message) || fallback;
}

function showSettingsError(message) {
    const box = document.getElementById('settingsError');
    box.textContent = message;
    box.classList.remove('d-none');
}

function hideSettingsError() {
    const box = document.getElementById('settingsError');
    box.textContent = '';
    box.classList.add('d-none');
}

function loadProfile() {
    subflowFetch('/api/me')
        .then(res => res.ok ? res.json() : null)
        .then(profile => {
            if (!profile) {
                document.getElementById('profileSummary').innerHTML =
                    '<div><dt>안내</dt><dd>프로필 정보를 불러오지 못했습니다.</dd></div>';
                return;
            }
            document.getElementById('marketingAgreed').checked = Boolean(profile.marketingAgreed);
            document.getElementById('notificationAgreed').checked = Boolean(profile.notificationAgreed);
            const interests = (profile.interests || []).map(v => CATEGORY_LABELS[v] || v).join(', ');
            const memberships = (profile.memberships || []).map(v => MEMBERSHIP_LABELS[v] || v).join(', ');
            document.getElementById('profileSummary').innerHTML = `
                <div><dt>이메일</dt><dd>${escapeHtml(profile.email || '-')}</dd></div>
                <div><dt>이름</dt><dd>${escapeHtml(profile.name || '-')}</dd></div>
                <div><dt>출생연도</dt><dd>${escapeHtml(profile.birthYear ?? '-')}</dd></div>
                <div><dt>신분</dt><dd>${escapeHtml(OCCUPATION_LABELS[profile.occupation] || '-')}</dd></div>
                <div><dt>가구원 수</dt><dd>${escapeHtml(profile.householdSize != null ? `${profile.householdSize}명` : '-')}</dd></div>
                <div><dt>관심 카테고리</dt><dd>${escapeHtml(interests || '-')}</dd></div>
                <div><dt>보유 멤버십</dt><dd>${escapeHtml(memberships || '-')}</dd></div>`;
        })
        .catch(() => {
            document.getElementById('profileSummary').innerHTML =
                '<div><dt>안내</dt><dd>프로필 정보를 불러오지 못했습니다.</dd></div>';
        });
}

async function updateConsent(type, agreed) {
    hideSettingsError();
    try {
        const res = await subflowFetch('/api/me/consents', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ type, agreed })
        });
        if (!res.ok) {
            const error = await res.json().catch(() => ({}));
            showSettingsError(extractErrorMessage(error, '동의 설정을 저장하지 못했습니다.'));
            loadProfile();
        }
    } catch (e) {
        showSettingsError('동의 설정을 저장하지 못했습니다.');
        loadProfile();
    }
}

async function deleteAccount() {
    hideSettingsError();
    const confirmation = document.getElementById('deleteConfirmation').value.trim();
    if (confirmation !== '탈퇴') {
        showSettingsError('확인을 위해 "탈퇴"를 정확히 입력해 주세요.');
        return;
    }
    if (!confirm('정말 탈퇴할까요? 모든 데이터가 삭제됩니다.')) {
        return;
    }
    try {
        const res = await subflowFetch('/api/me', {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ confirmation })
        });
        if (res.status === 204) {
            location.href = '/login';
        } else {
            const error = await res.json().catch(() => ({}));
            showSettingsError(extractErrorMessage(error, '탈퇴하지 못했습니다. 잠시 후 다시 시도해 주세요.'));
        }
    } catch (e) {
        showSettingsError('탈퇴하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    }
}

loadProfile();

document.querySelectorAll('[data-action="update-consent"]').forEach(input => {
    input.addEventListener('change', event => {
        updateConsent(event.target.dataset.consentType, event.target.checked);
    });
});
document.querySelector('[data-action="delete-account"]').addEventListener('click', () => deleteAccount());
