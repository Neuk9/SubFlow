package com.subflow.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateCsrfTest {

    @ParameterizedTest
    @ValueSource(strings = {"index", "onboarding", "settings"})
    @DisplayName("CSRF 보호 페이지는 meta 태그로 토큰을 노출한다")
    void pageContainsCsrfMeta(String page) throws IOException {
        String template = readTemplate(page + ".mustache");

        assertThat(template).contains("name=\"_csrf\"");
        assertThat(template).contains("content=\"{{_csrf.token}}\"");
        assertThat(template).contains("name=\"_csrf_header\"");
        assertThat(template).contains("content=\"{{_csrf.headerName}}\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"index", "onboarding", "settings"})
    @DisplayName("페이지 스크립트보다 http.js를 먼저 로드한다")
    void loadsHttpScriptBeforePageScript(String page) throws IOException {
        String template = readTemplate(page + ".mustache");

        String pageScript = page.equals("index") ? "app" : page;
        assertThat(template).contains("/js/http.js");
        assertThat(template.indexOf("/js/http.js")).isLessThan(template.indexOf("/js/" + pageScript + ".js"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"app", "onboarding", "settings"})
    @DisplayName("페이지는 CSRF 토큰을 보내는 subflowFetch를 사용한다")
    void pageUsesSubflowFetch(String scriptName) throws IOException {
        String script = readScript(scriptName + ".js");

        assertThat(script).doesNotContainPattern(Pattern.compile("(?<!subflow)fetch\\("));
    }

    @ParameterizedTest
    @ValueSource(strings = {"app", "settings"})
    @DisplayName("페이지 스크립트는 escapeHtml을 중복 정의하지 않는다")
    void pageDoesNotRedefineEscapeHtml(String scriptName) throws IOException {
        String script = readScript(scriptName + ".js");

        assertThat(script).doesNotContain("function escapeHtml(");
    }

    @Test
    @DisplayName("http.js는 subflowFetch와 전역 escapeHtml을 제공한다")
    void httpScriptProvidesSubflowFetch() throws IOException {
        String script = readScript("http.js");

        assertThat(script).contains("function subflowFetch(");
        assertThat(script).contains("function escapeHtml(");
        assertThat(script).contains("X-CSRF-TOKEN");
    }

    @Test
    @DisplayName("설정 페이지 로그아웃 폼은 CSRF hidden input을 포함한다")
    void logoutFormContainsCsrfInput() throws IOException {
        String template = readTemplate("settings.mustache");

        assertThat(template).contains("action=\"/logout\"");
        assertThat(template).contains("name=\"_csrf\" value=\"{{_csrf.token}}\"");
    }

    @Test
    @DisplayName("온보딩 페이지 로그아웃 폼은 CSRF hidden input을 포함한다")
    void onboardingLogoutFormContainsCsrfInput() throws IOException {
        String template = readTemplate("onboarding.mustache");

        assertThat(template).contains("action=\"/logout\"");
        assertThat(template).contains("name=\"_csrf\" value=\"{{_csrf.token}}\"");
    }

    private String readTemplate(String name) throws IOException {
        return read("/templates/" + name);
    }

    private String readScript(String name) throws IOException {
        return read("/static/js/" + name);
    }

    private String read(String path) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).as("resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
