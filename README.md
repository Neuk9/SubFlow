# SubFlow

구독 서비스를 한곳에서 관리하는 웹 애플리케이션입니다.

## 기술 스택

| 구분 | 버전 |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.1.2 |
| Build | Gradle 9.8.2 (Wrapper) |
| View | Mustache |
| Database | PostgreSQL 17 |
| ORM | Spring Data JPA |
| Test | JUnit 5 |

## 시작하기

### 1. 사전 요구 사항

- JDK 21
- Docker (PostgreSQL 실행용)

### 2. 저장소 클론

```bash
git clone https://github.com/Neuk9/SubFlow.git
cd SubFlow
```

### 3. 데이터베이스 실행

```bash
docker compose up -d
```

`docker-compose.yml`에 정의된 기본값으로 `localhost:5432`에 PostgreSQL이 실행됩니다.

### 4. 환경 변수 설정

기본값으로 바로 실행할 수 있습니다. 값을 바꾸고 싶다면:

```bash
cp .env.example .env
```

`.env`는 커밋하지 않습니다.

### 5. 애플리케이션 실행

```bash
./gradlew bootRun
```

`http://localhost:8080`에서 확인할 수 있습니다.

### 6. 테스트

```bash
./gradlew test
```

테스트는 `test` 프로필을 사용하며 `subflow_test` 데이터베이스에 `create-drop`으로 스키마를 생성합니다.
개발용 `subflow` DB와 분리되어 있어 로컬 데이터는 유지됩니다.
PostgreSQL이 실행 중이어야 합니다. (`docker compose up -d`)

DB를 처음부터 다시 만들려면:

```bash
docker compose down -v && docker compose up -d
```

## 프로젝트 구조

```text
src/main/java/com/subflow/    # 애플리케이션 코드
src/main/resources/
  templates/                  # Mustache 템플릿
  static/                     # 정적 리소스 (CSS, JS, 이미지)
  application.yml             # 공통 설정
src/test/java/com/subflow/    # 테스트 코드
```

