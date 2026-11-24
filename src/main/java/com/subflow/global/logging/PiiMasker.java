package com.subflow.global.logging;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로그에 남는 이메일을 마스킹한다.
 *
 * <p>이메일 로컬 파트는 첫 글자만 남기고 도메인은 유지한다.
 * 예: {@code seoyeon@example.com} → {@code s***@example.com}</p>
 */
public final class PiiMasker {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private PiiMasker() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return email;
        }
        return email.charAt(0) + "***@" + email.substring(at + 1);
    }

    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        int firstEnd = name.offsetByCodePoints(0, 1);
        int remaining = name.codePointCount(firstEnd, name.length());
        return name.substring(0, firstEnd) + "*".repeat(remaining);
    }

    public static String maskText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        StringBuilder masked = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(masked, Matcher.quoteReplacement(maskEmail(matcher.group())));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }
}
