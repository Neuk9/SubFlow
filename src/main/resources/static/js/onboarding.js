const selectedOccupation = { value: null };
const selectedInterests = new Set();
const selectedMemberships = new Set();

document.querySelectorAll('.sf-chip-row').forEach(row => {
    row.addEventListener('click', event => {
        const button = event.target.closest('.sf-chip-select');
        if (!button) return;
        const group = row.dataset.group;

        if (group === 'occupation') {
            row.querySelectorAll('.sf-chip-select').forEach(chip => chip.classList.toggle('active', chip === button));
            selectedOccupation.value = button.dataset.value;
            return;
        }

        button.classList.toggle('active');
        const target = group === 'interests' ? selectedInterests : selectedMemberships;
        if (button.classList.contains('active')) {
            target.add(button.dataset.value);
        } else {
            target.delete(button.dataset.value);
        }
    });
});

function chip(group, value) {
    return document.querySelector(`.sf-chip-row[data-group="${group}"] .sf-chip-select[data-value="${value}"]`);
}

function loadProfile() {
    fetch('/api/me')
        .then(res => res.ok ? res.json() : null)
        .then(profile => {
            if (!profile) return;
            if (profile.birthYear) document.getElementById('birthYear').value = profile.birthYear;
            if (profile.householdSize) document.getElementById('householdSize').value = profile.householdSize;
            if (profile.occupation) {
                selectedOccupation.value = profile.occupation;
                const button = chip('occupation', profile.occupation);
                if (button) button.classList.add('active');
            }
            (profile.interests || []).forEach(value => {
                selectedInterests.add(value);
                const button = chip('interests', value);
                if (button) button.classList.add('active');
            });
            (profile.memberships || []).forEach(value => {
                selectedMemberships.add(value);
                const button = chip('memberships', value);
                if (button) button.classList.add('active');
            });
            document.getElementById('agreeMarketing').checked = Boolean(profile.marketingAgreed);
        })
        .catch(() => {});
}

function showError(message) {
    const box = document.getElementById('onboardingError');
    box.textContent = message;
    box.classList.remove('d-none');
}

function hideError() {
    const box = document.getElementById('onboardingError');
    box.textContent = '';
    box.classList.add('d-none');
}

async function submitOnboarding(event) {
    event.preventDefault();
    hideError();

    const birthYear = Number(document.getElementById('birthYear').value);
    const householdValue = document.getElementById('householdSize').value;
    const agreePrivacy = document.getElementById('agreePrivacy').checked;
    const agreeTerms = document.getElementById('agreeTerms').checked;
    const agreeMarketing = document.getElementById('agreeMarketing').checked;

    if (!birthYear) {
        showError('출생연도를 입력해 주세요.');
        return;
    }
    if (!selectedOccupation.value) {
        showError('현재 신분을 선택해 주세요.');
        return;
    }
    if (!agreePrivacy || !agreeTerms) {
        showError('필수 동의 항목을 확인해 주세요.');
        return;
    }

    const payload = {
        birthYear,
        occupation: selectedOccupation.value,
        householdSize: householdValue ? Number(householdValue) : null,
        interests: [...selectedInterests],
        memberships: [...selectedMemberships],
        agreePrivacy,
        agreeTerms,
        agreeMarketing
    };

    const button = document.getElementById('onboardingSubmit');
    button.disabled = true;
    button.textContent = '저장 중…';

    try {
        const res = await fetch('/api/me/onboarding', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!res.ok) {
            const error = await res.json().catch(() => ({}));
            showError(error.message || '저장하지 못했습니다. 입력값을 확인해 주세요.');
            return;
        }
        location.href = '/';
    } catch (e) {
        showError('저장하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    } finally {
        button.disabled = false;
        button.textContent = '시작하기';
    }
}

loadProfile();
