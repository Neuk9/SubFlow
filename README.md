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
- 로그인 생략 (모든 요청 허용)
- 시연용 샘플 구독 6건과 공유 멤버 2건 (서버 재시작 시 초기화)

### 4. PostgreSQL로 실행

```bash
docker compose up -d
cp .env.example .env
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

`application-prod.yml`은 `ddl-auto: validate`를 사용하므로 스키마가 미리 준비되어 있어야 합니다.
Flyway 마이그레이션이 애플리케이션 시작 시 스키마를 적용합니다.

Google 로그인까지 켜려면 `oauth` 프로필을 함께 활성화합니다.

```bash
SPRING_PROFILES_ACTIVE=prod,oauth ./gradlew bootRun
```

### 5. 테스트

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

## API 요약

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET/POST | `/api/subscriptions` | 구독 목록/등록 |
| GET | `/api/subscriptions/calendar` | 월별 결제 일정 |
| POST | `/api/imports/preview` | CSV 미리보기 |
| POST | `/api/imports/confirm` | CSV 가져오기 확정 |
| GET | `/api/insights` | 절약 제안, 중복 구독 |
| GET | `/api/services` | 서비스 카탈로그와 요금제 |
| GET/POST | `/api/subscriptions/{id}/shares` | 공유 멤버 목록/추가 |
| PUT | `/api/shares/{id}/settlement` | 월별 정산 완료 처리 |
| GET | `/api/shares/summary` | 받은 금액/남은 금액 요약 |

## 인증

| 프로필 | 동작 |
| --- | --- |
| `local` | 로그인 생략. 모든 요청 허용 |
| `prod` | 인증 활성화. Google OAuth2 설정이 없으면 경고 후 인증만 비활성화 |
| `prod,oauth` | Google 로그인 필수. 미로그인 시 `/oauth2/authorization/google` 로 리다이렉트 |

소유자는 `subscriptions.owner_email` 로 구분합니다. 모든 조회·수정·삭제는 현재 로그인 사용자의 이메일로 제한됩니다.
공유 구독 멤버와 정산 기록도 구독 소유자만 조회·변경할 수 있습니다.

### Google OAuth2 설정

1. Google Cloud Console > API 및 서비스 > 사용자 인증 정보 > OAuth 2.0 클라이언트 ID 생성
2. 승인된 리디렉션 URI 등록: `http://localhost:8080/login/oauth2/code/google`
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

프로필을 지정하지 않으면 `local`이 적용되므로, 운영 배포 시에는 반드시 `SPRING_PROFILES_ACTIVE=prod`를 설정하세요.

## 프로필별 동작 차이

| 기능 | local | prod | prod,oauth |
| --- | --- | --- | --- |
| 로그인 | 생략 | 생략 (경고) | Google 필수 |
| H2 콘솔 (`/h2-console`) | 제공 | 차단 (404) | 차단 (404) |
| 배치 시뮬레이션 (`/api/simulation/*`) | 제공 | 차단 (404) | 차단 (404) |
| 샘플 데이터 | 자동 생성 | 생성하지 않음 | 생성하지 않음 |
| DB | H2 인메모리 | PostgreSQL | PostgreSQL |

## 프로젝트 구조

```text
src/main/java/com/subflow/
  global/                 # 공통 설정, 예외, 인증, 데모 데이터
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
