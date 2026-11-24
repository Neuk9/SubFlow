package com.subflow.global.page;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 외부 CDN 없이 페이지가 동작하도록 필요한 정적 자원과 라이선스가 저장소에 포함됐는지 확인한다.
 */
class VendorAssetsTest {

    private static final String VENDOR = "static/vendor/";

    private static final List<String> REQUIRED_ASSETS = List.of(
            "bootstrap/bootstrap.min.css",
            "bootstrap/bootstrap.bundle.min.js",
            "bootstrap-icons/bootstrap-icons.min.css",
            "bootstrap-icons/fonts/bootstrap-icons.woff2",
            "chartjs/chart.umd.min.js",
            "pretendard/pretendardvariable.css",
            "pretendard/woff2/PretendardVariable.woff2",
            "LICENSES.md");

    @Test
    @DisplayName("필수 벤더 자산과 라이선스 파일이 존재하고 비어 있지 않다")
    void requiredVendorAssetsExist() throws IOException {
        for (String asset : REQUIRED_ASSETS) {
            Resource resource = new ClassPathResource(VENDOR + asset);
            assertThat(resource.exists()).as("vendor asset %s", asset).isTrue();
            try (InputStream input = resource.getInputStream()) {
                assertThat(input.readAllBytes().length).as("vendor asset %s size", asset).isPositive();
            }
        }
    }

    @Test
    @DisplayName("벤더 CSS의 url() 대상이 저장소 안에서 모두 해석된다")
    void cssUrlReferencesResolve() throws IOException {
        assertThat(unresolvedReferences("bootstrap-icons/bootstrap-icons.min.css")).isEmpty();
        assertThat(unresolvedReferences("pretendard/pretendardvariable.css")).isEmpty();
    }

    private List<String> unresolvedReferences(String cssPath) throws IOException {
        ClassPathResource css = new ClassPathResource(VENDOR + cssPath);
        String content = new String(css.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String base = cssPath.substring(0, cssPath.lastIndexOf('/') + 1);
        Pattern pattern = Pattern.compile("url\\(\\s*['\"]?([^'\")]+)['\"]?\\s*\\)");
        List<String> unresolved = new ArrayList<>();
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String target = matcher.group(1);
            int query = target.indexOf('?');
            if (query >= 0) {
                target = target.substring(0, query);
            }
            int fragment = target.indexOf('#');
            if (fragment >= 0) {
                target = target.substring(0, fragment);
            }
            if (target.startsWith("data:") || target.startsWith("http://") || target.startsWith("https://")) {
                continue;
            }
            String normalized = normalize(base + target);
            if (!new ClassPathResource(VENDOR + normalized).exists()) {
                unresolved.add(target);
            }
        }
        return unresolved;
    }

    private String normalize(String path) {
        List<String> parts = new ArrayList<>();
        for (String part : path.split("/")) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!parts.isEmpty()) {
                    parts.remove(parts.size() - 1);
                }
                continue;
            }
            parts.add(part);
        }
        return String.join("/", parts);
    }
}
