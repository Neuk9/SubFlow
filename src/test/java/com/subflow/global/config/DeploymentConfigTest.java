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
}
