package com.subflow.global.page;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSP {@code script-src 'self'}와 호환되도록 템플릿과 JS가 생성하는 HTML에
 * 인라인 이벤트 핸들러 속성이 남아 있지 않은지 확인한다.
 */
class TemplateInlineHandlerTest {

    @Test
    @DisplayName("템플릿과 JS 생성 HTML에는 인라인 이벤트 핸들러가 없다")
    void templatesAndGeneratedHtmlMustNotContainInlineEventHandlers() throws Exception {
        List<Path> sources = new ArrayList<>();
        try (var paths = Files.walk(Path.of("src/main/resources/templates"))) {
            paths.filter(path -> path.toString().endsWith(".mustache")).forEach(sources::add);
        }
        try (var paths = Files.walk(Path.of("src/main/resources/static/js"))) {
            paths.filter(path -> path.toString().endsWith(".js")).forEach(sources::add);
        }

        List<String> offenders = sources.stream()
                .flatMap(path -> {
                    try {
                        var matcher = Pattern.compile("\\son(click|change|input|submit)\\s*=")
                                .matcher(Files.readString(path));
                        return matcher.find() ? Stream.of(path + " -> " + matcher.group()) : Stream.empty();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
                .toList();
        assertThat(offenders).isEmpty();
    }
}
