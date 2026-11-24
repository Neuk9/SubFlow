package com.subflow.global;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 실제 PostgreSQL 에 Flyway 마이그레이션을 적용한 뒤 Hibernate {@code validate} 로
 * 엔티티와 스키마가 일치하는지 확인한다.
 *
 * <p>로컬에는 PostgreSQL 이 없을 수 있으므로 {@code CI=true} 일 때만 실행한다.
 * GitHub Actions 의 Postgres 서비스가 {@code localhost:5432/subflow_test} 를 제공한다.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/subflow_test",
        "spring.datasource.username=subflow",
        "spring.datasource.password=subflow",
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "CI", matches = "true")
class PostgresSchemaValidationTest {

    @Test
    void contextLoads() {
    }
}
