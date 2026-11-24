const OCCUPATION_LABELS = {
    HIGH_SCHOOL: '고등학생', UNIVERSITY: '대학생', GRADUATE: '대학원생',
    WORKER: '직장인', JOB_SEEKER: '구직자', FREELANCER: '프리랜서', OTHER: '기타'
};

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
    fetch('/api/me')
        .then(res => res.ok ? res.json() : null)
        .then(profile => {
            if (!profile) return;
            document.getElementById('marketingAgreed').checked = Boolean(profile.marketingAgreed);
            document.getElementById('notificationAgreed').checked = Boolean(profile.notificationAgreed);
            document.getElementById('profileSummary').innerHTML = `
                <div><dt>이메일</dt><dd>${profile.email || '-'}</dd></div>
                <div><dt>이름</dt><dd>${profile.name || '-'}</dd></div>
                <div><dt>출생연도</dt><dd>${profile.birthYear ?? '-'}</dd></div>
                <div><dt>신분</dt><dd>${OCCUPATION_LABELS[profile.occupation] || '-'}</dd></div>
                <div><dt>가구원 수</dt><dd>${profile.householdSize ?? '-'}</dd></div>
                <div><dt>관심 카테고리</dt><dd>${(profile.interests || []).join(', ') || '-'}</dd></div>
                <div><dt>보유 멤버십</dt><dd>${(profile.memberships || []).join(', ') || '-'}</dd></div>`;
        })
        .catch(() => {});
}

async function updateConsent(type, agreed) {
    hideSettingsError();
    try {
        const res = await fetch('/api/me/consents', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ type, agreed })
        });
        if (!res.ok) {
            showSettingsError('동의 설정을 저장하지 못했습니다.');
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
        const res = await fetch('/api/me', {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ confirmation })
        });
        if (res.status === 204) {
            location.href = '/login';
        } else {
            showSettingsError('탈퇴하지 못했습니다. 잠시 후 다시 시도해 주세요.');
        }
    } catch (e) {
        showSettingsError('탈퇴하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    }
}

loadProfile();
