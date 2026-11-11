# 기여 가이드

SubFlow 팀의 협업 규칙입니다. 모든 팀원이 같은 방식으로 일하기 위해 아래 내용을 따라 주세요.

## 목차

- [기본 원칙](#기본-원칙)
- [이슈](#이슈)
- [브랜치 전략](#브랜치-전략)
- [커밋 컨벤션](#커밋-컨벤션)
- [Pull Request](#pull-request)
- [코드 리뷰](#코드-리뷰)
- [머지 규칙](#머지-규칙)
- [코드 스타일](#코드-스타일)

## 기본 원칙

1. **`main`은 항상 동작해야 합니다.** 깨진 코드를 `main`에 올리지 않습니다.
2. **작게, 자주** PR을 올립니다. 리뷰하기 좋은 크기는 변경 300줄 이내입니다.
3. **혼자 결정하지 않습니다.** 구조나 인터페이스에 영향을 주는 변경은 이슈에서 먼저 논의합니다.
4. **모르면 물어봅니다.** PR 코멘트, Discussions, 단체 채팅 모두 좋습니다.

## 이슈

작업을 시작하기 전에 반드시 이슈를 만듭니다. 이슈 번호가 브랜치와 커밋을 추적하는 기준이 됩니다.

| 템플릿 | 사용 시점 |
| --- | --- |
| 버그 리포트 | 의도한 대로 동작하지 않는 기능 |
| 기능 제안 | 새로운 기능이나 개선 사항 |
| 리팩터링 / 개선 | 동작 변경 없이 구조를 개선 |

- 제목은 `[BUG]`, `[FEAT]`, `[REFACTOR]`로 시작합니다.
- 담당자(Assignee)와 라벨을 지정합니다.
- 기능 제안은 **완료 조건(Acceptance Criteria)** 을 반드시 작성합니다.

## 브랜치 전략

`main` 하나만 유지하는 GitHub Flow를 사용합니다.

```text
main ───●──────────●──────────●───
         \        /  \        /
  feat/#12-...  ●    ●  fix/#15-...
```

### 브랜치 이름

```text
<type>/#<이슈번호>-<짧은-설명>
```

| type | 의미 |
| --- | --- |
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩터링 |
| `docs` | 문서 |
| `test` | 테스트 |
| `chore` | 빌드, 설정, 의존성 |

예시:

```bash
git switch -c feat/#12-subscription-list
git switch -c fix/#15-login-500-error
```

### 규칙

- 브랜치는 항상 최신 `main`에서 분기합니다.
- 하나의 브랜치에는 하나의 이슈만 다룹니다.
- 머지된 브랜치는 원격에서 자동 삭제됩니다. 로컬 브랜치도 정리합니다.

```bash
git switch main
git pull
git branch -d feat/#12-subscription-list
```

## 커밋 컨벤션

[Conventional Commits](https://www.conventionalcommits.org/ko/v1.0.0/) 형식을 사용합니다.

```text
<type>: <변경 내용>

<본문 (선택)>

<이슈 번호 (선택)>
```

### type

| type | 의미 |
| --- | --- |
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩터링 |
| `docs` | 문서 변경 |
| `test` | 테스트 추가/수정 |
| `chore` | 빌드, 설정, 의존성 |
| `style` | 포맷팅, 세미콜론 등 동작에 영향 없는 변경 |

### 예시

```bash
git commit -m "feat: 구독 목록 조회 API 추가"
git commit -m "fix: 로그인 후 리다이렉트 경로 수정"
git commit -m "refactor: SubscriptionService의 중복 검증 로직 제거"

# 본문과 이슈 번호
git commit -m "feat: 구독 갱신 알림 스케줄러 추가

매일 오전 9시에 갱신 예정 구독을 조회해 알림을 발송한다.

Closes #23"
```

### 규칙

- 제목은 50자 이내, 마침표를 붙이지 않습니다.
- 제목은 명령형으로 씁니다. ("추가했다" → "추가")
- 한 커밋에는 하나의 논리적 변경만 담습니다.
- `WIP`, `수정`, `ㅁㄴㅇㄹ` 같은 의미 없는 메시지를 쓰지 않습니다.

## Pull Request

### 생성 전

```bash
git switch main
git pull
git switch feat/#12-subscription-list
git rebase origin/main   # 충돌 해결
./gradlew build          # 빌드와 테스트 통과 확인
git push -u origin feat/#12-subscription-list
```

### 생성

```bash
gh pr create --base main --title "feat: 구독 목록 조회 API 추가"
```

또는 GitHub 웹에서 PR을 만들면 [템플릿](.github/PULL_REQUEST_TEMPLATE.md)이 자동으로 채워집니다.

### 규칙

- 제목은 커밋 컨벤션과 동일하게 작성합니다.
- `Closes #이슈번호`를 반드시 적습니다. 머지 시 이슈가 자동으로 닫힙니다.
- **Draft PR**을 활용해 일찍 피드백을 받아도 됩니다. 단, 머지 전에 Ready로 전환합니다.
- 리뷰어는 팀원 중 최소 1명을 지정합니다. **본인 PR은 본인이 승인할 수 없습니다.**
- 승인 후 커밋을 추가하면 승인이 자동으로 무효화되므로, 리뷰 반영이 끝난 뒤 다시 승인을 요청합니다.
- 리뷰 반영 후에는 커밋을 추가하고, 필요하면 `git push --force-with-lease`로 정리합니다.

## 코드 리뷰

### 리뷰어

- **24시간 이내**에 1차 리뷰를 남깁니다.
- 단순 칭찬보다 개선점과 근거를 함께 적습니다.
- `nit:` 접두사는 "고쳐도 되고 안 고쳐도 되는" 제안에 사용합니다.
- 승인 기준: 버그 없음, 테스트 포함, 컨벤션 준수, `main`과 충돌 없음.

### 작성자

- 리뷰 코멘트에는 모두 답변합니다. (반영 또는 이유 설명)
- 리뷰를 반영했다면 어떤 커밋에서 바뀌었는지 알려 줍니다.
- 승인 후에는 **작성자가 직접 머지**합니다.

### 리뷰 코멘트 예시

```text
[필수] 이 쿼리는 N+1이 발생합니다. fetch join을 적용해 주세요.
[nit] 변수명을 `subscriptions`로 바꾸면 더 명확할 것 같습니다.
[질문] 이 예외를 400으로 내려주는 이유가 있을까요?
```

## 머지 규칙

- **Squash and merge**만 사용합니다. (커밋 히스토리를 깔끔하게 유지)
- 머지 후 브랜치는 자동 삭제됩니다.
- 머지 커밋 메시지는 PR 제목을 그대로 사용합니다.

### `main` 브랜치 보호 (Ruleset)

아래 규칙은 GitHub에서 강제됩니다. 팀원은 반드시 PR과 승인을 거쳐야 합니다.

| 규칙 | 내용 |
| --- | --- |
| PR 필수 | `main`에 직접 push할 수 없습니다. 반드시 PR을 거칩니다. |
| 승인 1명 | PR은 최소 1명의 승인이 필요합니다. |
| CODEOWNERS 승인 | 코드 소유자(`@jeonjw85`, `@Kangfdc`)의 승인이 필요합니다. |
| 마지막 push 승인 | 승인 후 새 커밋을 push하면 다시 승인을 받아야 합니다. |
| 리뷰 스레드 해결 | 모든 리뷰 코멘트가 resolve 되어야 합니다. |
| CI 통과 | `Build & Test` 체크가 성공해야 합니다. |
| 최신 `main` 반영 | 브랜치가 `main`보다 뒤처져 있으면 머지할 수 없습니다. |
| 선형 히스토리 | merge commit을 허용하지 않습니다. (squash만 가능) |
| 브랜치 삭제 금지 | `main` 브랜치를 삭제할 수 없습니다. |
| force push 금지 | `main`에 non-fast-forward push를 할 수 없습니다. |

> 본인 PR은 본인이 승인할 수 없습니다. 팀원의 승인을 받아야 합니다.

### 관리자 바이패스

저장소 관리자(admin)는 위 규칙을 우회할 수 있습니다. (`bypass_actors: admin`)

**다음 경우에만 사용합니다.**

- CI 장애 등으로 긴급 배포가 필요한 경우
- 팀원이 부재중이고 변경이 사소한 경우 (오타, 문서)
- 초기 세팅처럼 리뷰어가 없는 경우

```bash
# 관리자 권한으로 즉시 머지
gh pr merge <번호> --squash --admin

# 관리자 권한으로 직접 push
git push origin main
```

> 바이패스로 머지한 경우 PR에 사유를 코멘트로 남깁니다.

### 승인 후 수정이 필요할 때

```bash
# 수정 후 push → 기존 승인이 자동으로 무효화됨
git add .
git commit -m "fix: 리뷰 반영 - 예외 처리 추가"
git push

# 다시 승인 요청
gh pr edit --add-reviewer Kangfdc
```

## 코드 스타일

- 포맷은 [.editorconfig](.editorconfig)를 따릅니다. (UTF-8, LF, Java 4 spaces)
- Java 네이밍은 표준 컨벤션을 따릅니다.
- 패키지는 기능 단위로 나눕니다. (`com.subflow.subscription`, `com.subflow.user` …)
- 컨트롤러는 요청/응답 변환만, 비즈니스 로직은 서비스에 둡니다.
- 엔티티를 API 응답으로 직접 반환하지 않습니다. DTO를 사용합니다.
- 예외는 의미 있는 메시지와 함께 던지고, 전역 핸들러에서 응답을 통일합니다.

## 로컬 개발 팁

```bash
# DB 실행 / 중지
docker compose up -d
docker compose down

# DB 데이터까지 초기화
docker compose down -v

# 빌드만 (테스트 제외)
./gradlew build -x test

# 특정 테스트만
./gradlew test --tests "com.subflow.SubFlowApplicationTests"
```
