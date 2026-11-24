package com.subflow.global.security;

/**
 * 인증 없이 접근할 수 있는 공개 경로를 한 곳에서 정의한다.
 * Spring Security 설정과 온보딩 인터셉터가 같은 목록을 사용하도록 통합한다.
 */
public final class SecurityPaths {

    public static final String[] PUBLIC_PATHS = {
            "/css/**", "/js/**", "/images/**", "/logos/**", "/vendor/**", "/favicon.ico", "/error",
            "/login", "/privacy", "/terms", "/oauth2/**", "/login/oauth2/**", "/logout"
    };

    private SecurityPaths() {
    }

    public static boolean isPublic(String path) {
        if (path == null) {
            return false;
        }
        for (String pattern : PUBLIC_PATHS) {
            if (pattern.endsWith("/**")) {
                String prefix = pattern.substring(0, pattern.length() - 3);
                if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                    return true;
                }
            } else if (path.equals(pattern)) {
                return true;
            }
        }
        return false;
    }
}
