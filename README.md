# SubFlow

구독 서비스를 한곳에서 관리하는 웹 애플리케이션입니다.

## 주요 기능

- **캘린더 중심 대시보드**: 이번 달 결제 일정과 결제 목록을 한 화면에 표시
- **구독 관리**: 등록, 수정, 해지, 일시 정지와 결제일·월 환산 계산
- **CSV 내역 가져오기**: 카드/은행 내역 파일을 올려 기존 구독과 매칭하고, 반복 결제는 신규 구독 후보로 제안
  - UTF-8/CP949 인코딩 자동 감지
  - 컬럼 자동 탐지, 실패 시 수동 매핑
  - 같은 파일을 다시 올려도 결제 기록이 중복되지 않음
- **지출 분석**: 카테고리별 비율, 서비스별 월 지출
- **절약 제안**
  - 연간 요금제 전환 시 절약액
  - 학생 요금 전환 시 절약액
  - 같은 용도의 중복 구독 탐지 (음악, 클라우드, AI)
- **해지 안내**: 서비스별 해지 경로와 링크
- **공유 구독 정산**: 멤버별 분담 금액과 월 단위 정산 기록
- **계정 연동 공유**: 이메일 초대 → 수락 → 참여 중인 구독 확인, 양방향 정산(송금 완료/소유자 확인)
- **Google 로그인·온보딩**: Google 단일 로그인, 최초 로그인 시 맞춤 혜택용 정보 입력
- **개인정보 보호**: 동의 기록, 개인정보 처리방침·이용약관, 회원 탈퇴
- **설정**: 마케팅·알림 수신 동의 변경, 내 데이터 JSON 내려받기
- **배치 시뮬레이션**: 결제 갱신, 알림 발송, 만료 처리 (`local` 전용)

## 기술 스택

| 구분 | 버전 |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.1.2 |
| Build | Gradle 9.8.2 (Wrapper) |
| View | Mustache |
| Database | H2 (local) / PostgreSQL 17 (prod) |
| ORM | Spring Data JPA, Flyway |
| CSV | Apache Commons CSV 1.14.1 |
| 인증 | Google OAuth2 (prod) / 생략 (local) |
| Test | JUnit 5 |

## 시작하기

### 1. 사전 요구 사항

- JDK 21

PostgreSQL을 사용할 때만 Docker가 필요합니다. 기본값인 `local` 프로필은 H2 인메모리 DB를 사용하므로 Docker 없이 바로 실행됩니다.

### 2. 저장소 클론

```bash
git clone https://github.com/Neuk9/SubFlow.git
cd SubFlow
./gradlew installHooks
```

`installHooks`는 커밋 메시지 컨벤션을 검사하는 Git 훅을 설치합니다. 자세한 내용은 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고해 주세요.

### 3. 애플리케이션 실행 (H2)

```bash
./gradlew bootRun
```

`http://localhost:8080`에서 확인할 수 있습니다. 기본 프로필은 `local`이며 다음이 자동 적용됩니다.

- H2 인메모리 DB (`jdbc:h2:mem:subflow-local`)
- H2 콘솔: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:subflow-local`, 사용자 `sa`, 비밀번호 없음)
- 로그인 생략 (`local@subflow.dev` 데모 사용자 자동 인증)
- 시연용 샘플 구독 6건과 공유 멤버 2건 (서버 재시작 시 초기화)

### 4. 로컬 PostgreSQL (개발용, 선택)

```bash
docker compose up -d
```

저장소의 `docker-compose.yml`은 **로컬 개발 전용**입니다. PostgreSQL `5432` 포트를 호스트에 공개하고 `docker/initdb`를 마운트하므로 운영 배포에는 사용하지 않습니다.

### 5. 운영 배포 (Docker Compose + Cloudflare Tunnel)

```bash
cp .env.example .env
# .env에 GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET, DB_PASSWORD, AUDIT_HASH_PEPPER 등 실제 값 입력
docker compose -f compose.prod.yml up -d --build
```

- 앱은 `SPRING_PROFILES_ACTIVE=prod,oauth`로 기동하며, Google OAuth2 설정이 없으면 **기동에 실패**합니다.
- 호스트 포트는 `127.0.0.1:${APP_PORT:-19007}:8080`으로 loopback에만 매핑됩니다.
- PostgreSQL은 포트를 공개하지 않고 `internal` 네트워크로만 앱과 연결됩니다.
- 앱 컨테이너는 non-root(UID 1000), `read_only`, `tmpfs /tmp`, `cap_drop: ALL`, `no-new-privileges`로 실행됩니다.

Cloudflare Tunnel은 호스트의 loopback 포트로 연결합니다.

```yaml
# cloudflared config
tunnel: subflow
credentials-file: /etc/cloudflared/<tunnel-id>.json
ingress:
  - hostname: subflow.stda.app
    service: http://localhost:19007
  - service: http_status:404
```

`subflow.stda.app`은 Cloudflare 엣지에서 TLS가 종료되고 `http://localhost:19007`로 전달됩니다. 방화벽에서 19007 외부 접근을 차단하고 `.env` 권한을 `600`으로 유지하세요.

### 6. 테스트

```bash
./gradlew test
```

테스트는 `test` 프로필을 사용하며 H2 인메모리 DB에서 실행됩니다. 별도 DB 실행이 필요 없습니다.

## 데모 시나리오

`local` 프로필로 실행하면 아래 순서로 기능을 확인할 수 있습니다.

1. **캘린더**: 오늘 날짜, 결제일 표시, 오른쪽 이번 달 결제 합계
2. **구독**: 구독 목록과 브랜드 로고, 상태
3. **분석**
   - 절약 제안: 노션 연간 전환, 학생 요금 3건
   - 중복 구독: 음악 스트리밍 2건
4. **구독 행의 `⋯` 메뉴**
   - 함께 쓰기: 넷플릭스 공유 멤버 2명, 정산 체크
   - 해지 방법: 서비스별 해지 경로와 링크
5. **내역 가져오기**: `docs/samples/` 파일로 CSV 가져오기
   - `standard-utf8.csv`: 넷플릭스 매칭, Spotify 후보
   - `card-cp949.csv`: CP949 인코딩과 상단 안내문 처리
   - `bank-semicolon.csv`: 세미콜론 구분, 입금 행 제외

## 데이터베이스 마이그레이션

Flyway를 사용합니다. (`src/main/resources/db/migration`)

| 버전 | 내용 |
| --- | --- |
| `V1__init_schema.sql` | 구독, 결제 기록, 알림 이력 |
| `V2__subscription_shares.sql` | 공유 구독 멤버, 월별 정산 기록 |
| `V3__users.sql` | Google 사용자, 동의 기록 |
| `V4__share_invitations.sql` | 공유 이메일 초대, 멤버 송금 상태 |

## API 요약

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET/POST | `/api/subscriptions` | 구독 목록/등록 |
| GET | `/api/subscriptions/calendar` | 월별 결제 일정 |
| POST | `/api/imports/preview` | CSV 미리보기 |
| POST | `/api/imports/confirm` | CSV 가져오기 확정 |
| GET | `/api/insights` | 절약 제안, 중복 구독 |
| GET | `/api/services` | 서비스 카탈로그와 요금제 |
| GET/PUT/DELETE | `/api/me` | 내 프로필 조회/수정/탈퇴 |
| POST | `/api/me/onboarding` | 온보딩 제출 |
| PUT | `/api/me/consents` | 마케팅·알림 동의 변경 |
| GET | `/api/me/data` | 내 데이터 JSON 내려받기 |
| GET/POST | `/api/subscriptions/{id}/shares` | 공유 멤버 목록/추가 |
| PUT | `/api/shares/{id}/settlement` | 소유자 정산 확인 |
| POST | `/api/shares/{id}/accept` | 공유 초대 수락 |
| POST | `/api/shares/{id}/decline` | 공유 초대 거절 |
| PUT | `/api/shares/{id}/payment` | 멤버 송금 완료 표시 |
| GET | `/api/shares/shared` | 나에게 공유된 구독 |
| GET | `/api/shares/summary` | 받은 금액/남은 금액 요약 |

## 인증

| 프로필 | 동작 |
| --- | --- |
| `local` | 로그인 생략. `local@subflow.dev` 데모 사용자로 동작 |
| `prod`, `prod,oauth` | Google 로그인 필수. OAuth2 설정이 없으면 기동 실패 |

### 로그인과 온보딩

- Google 계정 하나로 가입과 로그인을 처리합니다.
- 최초 로그인 시 `/onboarding`에서 아래 정보를 입력합니다.
  - 필수: 출생연도, 현재 신분
  - 선택: 가구원 수, 관심 카테고리, 보유 멤버십, 마케팅 수신 동의
- 필수 동의(개인정보 수집·이용, 이용약관)가 없으면 온보딩을 완료할 수 없습니다.
- 만 14세 미만은 가입할 수 없습니다.
- 온보딩을 마치지 않으면 다른 화면 접근 시 `/onboarding`으로 이동합니다.

### 개인정보와 탈퇴

- `/privacy` 개인정보 처리방침, `/terms` 이용약관
- 동의 기록은 종류·문서 버전·시각과 함께 저장합니다.
- `DELETE /api/me`로 탈퇴하면 구독·결제·공유·정산·동의·사용자 데이터를 순서대로 파기합니다.
- `/settings`에서 마케팅·알림 동의를 변경하고, 내 데이터를 JSON으로 내려받을 수 있습니다.

소유자는 `subscriptions.owner_email` 로 구분합니다. 모든 조회·수정·삭제는 현재 로그인 사용자의 이메일로 제한됩니다.
공유 구독 멤버와 정산 기록도 구독 소유자만 조회·변경할 수 있습니다.

### Google OAuth2 설정

1. Google Cloud Console > API 및 서비스 > 사용자 인증 정보 > OAuth 2.0 클라이언트 ID 생성
2. 승인된 리디렉션 URI 등록
   - 로컬: `http://localhost:8080/login/oauth2/code/google`
   - 운영: `https://subflow.stda.app/login/oauth2/code/google`
3. `.env`에 값 입력

```bash
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
```

키는 커밋하지 않습니다. `.env`는 `.gitignore`에 포함되어 있습니다.

## 설정 파일

| 파일 | 용도 |
| --- | --- |
| `application.yml` | 공통 설정, 파일 업로드 제한. 기본 프로필은 `local` |
| `application-local.yml` | H2 인메모리, H2 콘솔, 인증 생략, `create-drop` |
| `application-prod.yml` | PostgreSQL, H2 콘솔 비활성, 인증 활성, `validate` |
| `application-oauth.yml` | Google OAuth2 클라이언트 등록 |
| `application-test.yml` (test) | H2 인메모리, Flyway 비활성, `create-drop` |

프로필을 지정하지 않으면 `local`이 적용됩니다. 운영 배포에서는 `compose.prod.yml`이 `prod,oauth`를 지정하므로 반드시 `oauth` 프로필과 Google OAuth2 설정을 함께 준비하세요.

## 프로필별 동작 차이

| 기능 | local | prod | prod,oauth |
| --- | --- | --- | --- |
| 로그인 | 생략 | Google 필수 | Google 필수 |
| H2 콘솔 (`/h2-console`) | 제공 | 차단 (404) | 차단 (404) |
| 배치 시뮬레이션 (`/api/simulation/*`) | 제공 | 차단 (404) | 차단 (404) |
| 샘플 데이터 | 자동 생성 | 생성하지 않음 | 생성하지 않음 |
| DB | H2 인메모리 | PostgreSQL | PostgreSQL |

## 프로젝트 구조

```text
src/main/java/com/subflow/
  global/                 # 공통 설정, 예외, 인증, 페이지, 데모 데이터
  subscription/
    brand/                # 서비스명 → 브랜드 로고/색상 매핑
    catalog/              # 서비스·요금제 카탈로그 (연간/학생 요금, 해지 안내)
    controller/           # 화면/API 컨트롤러
    domain/               # 구독, 결제 기록, 공유 멤버, 정산 기록
    dto/                  # 요청/응답
    importer/             # CSV 파싱, 매칭, 후보 판별
    repository/
    scheduler/            # 결제 갱신, 알림, 만료 배치
    service/              # 구독, 가져오기, 인사이트, 공유 정산
  user/                   # Google 사용자, 온보딩, 동의, 탈퇴
src/main/resources/
  db/migration/           # Flyway 마이그레이션
  templates/              # Mustache 템플릿
  static/                 # CSS, JS, 로고
  application*.yml        # 프로필별 설정
src/test/java/com/subflow/
```

## 브랜드 로고

`src/main/resources/static/logos/`의 로고는 다음 출처를 사용합니다.

- [simple-icons](https://simpleicons.org) 16.38.1 (CC0 1.0) SVG
- 각 서비스의 공개 파비콘 PNG

각 상표는 해당 소유자에게 있으며, SubFlow는 이들과 제휴하거나 보증받지 않았습니다.
로고는 구독 서비스를 식별하는 목적으로만 사용합니다.
