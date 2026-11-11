# SubFlow

구독 서비스를 한곳에서 관리하는 웹 애플리케이션입니다.

## 기술 스택

| 구분 | 버전 |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.1.2 |
| Build | Gradle 9.8.2 (Wrapper) |
| View | Mustache |
| Database | H2 (local) / PostgreSQL 17 (prod) |
| ORM | Spring Data JPA |
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
- 시연용 샘플 구독 4건 (서버 재시작 시 초기화)

### 4. PostgreSQL로 실행

```bash
docker compose up -d
cp .env.example .env
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

`application-prod.yml`은 `ddl-auto: validate`를 사용하므로 스키마가 미리 준비되어 있어야 합니다.
로컬에서 스키마를 자동 생성하려면 `--spring.jpa.hibernate.ddl-auto=update`를 덧붙입니다.

Google 로그인까지 켜려면 `oauth` 프로필을 함께 활성화합니다.

```bash
SPRING_PROFILES_ACTIVE=prod,oauth ./gradlew bootRun
```

### 5. 테스트

```bash
./gradlew test
```

테스트는 `test` 프로필을 사용하며 H2 인메모리 DB에서 실행됩니다. 별도 DB 실행이 필요 없습니다.

## 인증

| 프로필 | 동작 |
| --- | --- |
| `local` | 로그인 생략. 모든 요청 허용 |
| `prod` | 인증 활성화. Google OAuth2 설정이 없으면 경고 후 인증만 비활성화 |
| `prod,oauth` | Google 로그인 필수. 미로그인 시 `/oauth2/authorization/google` 로 리다이렉트 |

소유자는 `subscriptions.owner_email` 로 구분합니다. 모든 조회·수정·삭제는 현재 로그인 사용자의 이메일로 제한됩니다.

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
| `application.yml` | 공통 설정. 기본 프로필은 `local` |
| `application-local.yml` | H2 인메모리, H2 콘솔, 인증 생략, `create-drop` |
| `application-prod.yml` | PostgreSQL, H2 콘솔 비활성, 인증 활성, `validate` |
| `application-oauth.yml` | Google OAuth2 클라이언트 등록 |
| `application-test.yml` (test) | H2 인메모리, `create-drop` |

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
  global/          # 공통 설정, 예외, 인증
  subscription/    # 구독 도메인
src/main/resources/
  templates/       # Mustache 템플릿
  static/          # 정적 리소스
  application*.yml # 프로필별 설정
src/test/java/com/subflow/
```
