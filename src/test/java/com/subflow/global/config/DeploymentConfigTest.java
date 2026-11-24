package com.subflow.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DeploymentConfigTest {

    @Test
    void composeExposesOnlyLoopbackPortAndKeepsDbInternal() throws Exception {
        String compose = Files.readString(Path.of("compose.prod.yml"));
        assertThat(compose).contains("127.0.0.1:${APP_PORT:-19007}:8080");
        assertThat(compose).contains("internal: true");
        assertThat(compose).contains("no-new-privileges:true");
        assertThat(compose).contains("read_only: true");
        assertThat(compose).contains("cap_drop:");
        assertThat(compose).doesNotContain("5432:5432");
    }

    @Test
    void prodConfigDoesNotBindLoopbackInsideContainer() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application-prod.yml"));
        assertThat(yaml).doesNotContain("address: 127.0.0.1");
        assertThat(yaml).contains("mode: oauth");
    }

    @Test
    void appUsesWritableUploadsTmpfsAndHasHealthcheckAndLogRotation() throws Exception {
        String compose = Files.readString(Path.of("compose.prod.yml"));
        assertThat(compose).contains("/uploads:size=64m,mode=0700");
        assertThat(compose).contains("/dev/tcp/127.0.0.1/8080");
        assertThat(compose).contains("start_period: 40s");
        assertThat(compose).contains("max-size: \"10m\"");
        assertThat(compose).contains("max-file: \"3\"");
        assertThat(compose.split("max-size: \"10m\"", -1)).hasSize(3);

        String yaml = Files.readString(Path.of("src/main/resources/application-prod.yml"));
        assertThat(yaml).contains("location: /uploads");
    }

    @Test
    void demoOriginsIncludeComposeLoopbackPort() throws Exception {
        String local = Files.readString(Path.of("src/main/resources/application-local.yml"));
        String test = Files.readString(Path.of("src/test/resources/application-test.yml"));
        assertThat(local).contains("http://localhost:19007");
        assertThat(test).contains("http://localhost:19007");
    }

    @Test
    void productionRequiresDbPasswordWithoutFallback() throws Exception {
        String compose = Files.readString(Path.of("compose.prod.yml"));
        assertThat(compose).contains("${DB_PASSWORD:?DB_PASSWORD is required}");
        assertThat(compose).doesNotContain("DB_PASSWORD:-");

        String yaml = Files.readString(Path.of("src/main/resources/application-prod.yml"));
        assertThat(yaml).contains("password: ${DB_PASSWORD}");
        assertThat(yaml).doesNotContain("${DB_PASSWORD:");
    }
}
