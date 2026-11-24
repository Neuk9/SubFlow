package com.subflow.global.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 클라이언트 IP를 해석한다.
 *
 * <p>{@code CF-Connecting-IP} 는 Cloudflare 엣지를 거친 요청에만 존재하지만,
 * 애플리케이션에 직접 접근한 클라이언트도 헤더를 위조할 수 있다.
 * 따라서 요청 피어가 loopback 또는 사설망(Docker 브리지 포함)일 때만 헤더를 신뢰하고,
 * 그 외에는 {@link HttpServletRequest#getRemoteAddr()} 를 사용한다.</p>
 */
public class ClientIpResolver {

    static final String CLOUDFLARE_HEADER = "CF-Connecting-IP";

    private static final String IPV4_LOOPBACK = "127.0.0.1";
    private static final String IPV6_LOOPBACK = "::1";
    private static final String IPV6_LOOPBACK_EXPANDED = "0:0:0:0:0:0:0:1";
    private static final String IPV4_MAPPED_LOOPBACK_PREFIX = "::ffff:127.";

    private final boolean trustCloudflareHeaders;

    public ClientIpResolver(boolean trustCloudflareHeaders) {
        this.trustCloudflareHeaders = trustCloudflareHeaders;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!trustCloudflareHeaders || !isTrustedPeer(remoteAddress)) {
            return remoteAddress;
        }

        String cloudflareAddress = request.getHeader(CLOUDFLARE_HEADER);
        if (cloudflareAddress == null || cloudflareAddress.isBlank()) {
            return remoteAddress;
        }
        return cloudflareAddress.trim();
    }

    private boolean isTrustedPeer(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        return address.equals(IPV4_LOOPBACK)
                || address.equals(IPV6_LOOPBACK)
                || address.equals(IPV6_LOOPBACK_EXPANDED)
                || address.startsWith(IPV4_MAPPED_LOOPBACK_PREFIX)
                || isPrivateIpv4(address);
    }

    private boolean isPrivateIpv4(String address) {
        if (address.startsWith("10.") || address.startsWith("192.168.")) {
            return true;
        }
        if (!address.startsWith("172.")) {
            return false;
        }
        int secondDot = address.indexOf('.', 4);
        if (secondDot < 0) {
            return false;
        }
        try {
            int secondOctet = Integer.parseInt(address.substring(4, secondDot));
            return secondOctet >= 16 && secondOctet <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
