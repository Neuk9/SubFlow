package com.subflow.global.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

/**
 * 클라이언트 IP를 해석한다.
 *
 * <p>{@code CF-Connecting-IP} 는 Cloudflare 엣지를 거친 요청에만 존재하지만,
 * 애플리케이션에 직접 접근한 클라이언트도 헤더를 위조할 수 있다. 따라서
 * {@code subflow.security.trust-cloudflare-headers=true} 인 배포(Cloudflare Tunnel
 * 뒤에서만 노출)에서만 헤더를 사용하고, 그 외에는
 * {@link HttpServletRequest#getRemoteAddr()} 를 사용한다.</p>
 *
 * <p>헤더 값은 IPv4/IPv6 리터럴만 정규식으로 허용한다(DNS 조회 없음). 콤마 목록,
 * 포트가 붙은 값, 호스트명, 그 밖의 쓰레기 값은 무시하고 원격 주소로 폴백한다.
 * {@code X-Forwarded-For} 는 사용하지 않는다.</p>
 */
public class ClientIpResolver {

    static final String CLOUDFLARE_HEADER = "CF-Connecting-IP";

    private static final String IPV4_OCTET = "(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";
    private static final String IPV4_LITERAL_PATTERN = IPV4_OCTET + "(?:\\." + IPV4_OCTET + "){3}";
    private static final String H16 = "[0-9A-Fa-f]{1,4}";

    private static final Pattern IPV4_LITERAL = Pattern.compile("^" + IPV4_LITERAL_PATTERN + "$");
    private static final Pattern IPV6_LITERAL = Pattern.compile(
            "^(?:"
                    + "(?:" + H16 + ":){7}" + H16 + "|"
                    + "(?:" + H16 + ":){1,7}:|"
                    + "(?:" + H16 + ":){1,6}:" + H16 + "|"
                    + "(?:" + H16 + ":){1,5}(?::" + H16 + "){1,2}|"
                    + "(?:" + H16 + ":){1,4}(?::" + H16 + "){1,3}|"
                    + "(?:" + H16 + ":){1,3}(?::" + H16 + "){1,4}|"
                    + "(?:" + H16 + ":){1,2}(?::" + H16 + "){1,5}|"
                    + H16 + ":(?::" + H16 + "){1,6}|"
                    + ":(?:(?::" + H16 + "){1,7}|:)"
                    + ")$");
    private static final Pattern IPV6_IPV4_MAPPED = Pattern.compile(
            "^::(?:[fF]{4}(?::0{1,4})?:)?" + IPV4_LITERAL_PATTERN + "$");

    private final boolean trustCloudflareHeaders;

    public ClientIpResolver(boolean trustCloudflareHeaders) {
        this.trustCloudflareHeaders = trustCloudflareHeaders;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!trustCloudflareHeaders) {
            return remoteAddress;
        }

        String cloudflareAddress = request.getHeader(CLOUDFLARE_HEADER);
        if (cloudflareAddress == null) {
            return remoteAddress;
        }
        String candidate = cloudflareAddress.trim();
        if (!isIpLiteral(candidate)) {
            return remoteAddress;
        }
        return candidate;
    }

    private boolean isIpLiteral(String value) {
        if (value.isEmpty()) {
            return false;
        }
        return IPV4_LITERAL.matcher(value).matches()
                || IPV6_LITERAL.matcher(value).matches()
                || IPV6_IPV4_MAPPED.matcher(value).matches();
    }
}
