package com.subflow.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static final String CLOUDFLARE_HEADER = "CF-Connecting-IP";

    @Test
    @DisplayName("헤더 신뢰가 꺼져 있으면 원격 주소를 사용한다")
    void usesRemoteAddressWhenTrustDisabled() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader(CLOUDFLARE_HEADER, "1.2.3.4");
        request.addHeader("X-Forwarded-For", "5.6.7.8");

        ClientIpResolver resolver = new ClientIpResolver(false);

        assertThat(resolver.resolve(request)).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("헤더를 신뢰하면 공인 원격 주소여도 유효한 Cloudflare 헤더를 사용한다")
    void trustsValidCloudflareHeaderEvenFromPublicPeer() {
        MockHttpServletRequest request = request("203.0.113.5");
        request.addHeader(CLOUDFLARE_HEADER, "1.2.3.4");

        ClientIpResolver resolver = new ClientIpResolver(true);

        assertThat(resolver.resolve(request)).isEqualTo("1.2.3.4");
    }

    @Test
    @DisplayName("loopback·사설망 피어가 보낸 Cloudflare 헤더도 신뢰한다")
    void trustsCloudflareHeaderFromLocalPeers() {
        ClientIpResolver resolver = new ClientIpResolver(true);

        MockHttpServletRequest loopback = request("127.0.0.1");
        loopback.addHeader(CLOUDFLARE_HEADER, "1.2.3.4");
        assertThat(resolver.resolve(loopback)).isEqualTo("1.2.3.4");

        MockHttpServletRequest privatePeer = request("172.18.0.5");
        privatePeer.addHeader(CLOUDFLARE_HEADER, "1.2.3.4");
        assertThat(resolver.resolve(privatePeer)).isEqualTo("1.2.3.4");
    }

    @Test
    @DisplayName("유효한 IPv6 Cloudflare 헤더를 허용한다")
    void acceptsIpv6CloudflareHeader() {
        ClientIpResolver resolver = new ClientIpResolver(true);

        assertThat(resolveWithHeader(resolver, "203.0.113.5", "2001:db8::1"))
                .isEqualTo("2001:db8::1");
        assertThat(resolveWithHeader(resolver, "203.0.113.5", "::1"))
                .isEqualTo("::1");
        assertThat(resolveWithHeader(resolver, "203.0.113.5", "::ffff:192.0.2.1"))
                .isEqualTo("::ffff:192.0.2.1");
    }

    @Test
    @DisplayName("유효하지 않은 Cloudflare 헤더는 무시하고 원격 주소로 폴백한다")
    void fallsBackWhenCloudflareHeaderIsInvalid() {
        ClientIpResolver resolver = new ClientIpResolver(true);

        assertThat(resolveWithHeader(resolver, "203.0.113.5", "not-an-ip"))
                .isEqualTo("203.0.113.5");
        assertThat(resolveWithHeader(resolver, "203.0.113.5", "1.2.3.4, 5.6.7.8"))
                .isEqualTo("203.0.113.5");
        assertThat(resolveWithHeader(resolver, "203.0.113.5", "1.2.3.4:8080"))
                .isEqualTo("203.0.113.5");
        assertThat(resolveWithHeader(resolver, "203.0.113.5", "[2001:db8::1]"))
                .isEqualTo("203.0.113.5");
        assertThat(resolveWithHeader(resolver, "203.0.113.5", "999.1.1.1"))
                .isEqualTo("203.0.113.5");
        assertThat(resolveWithHeader(resolver, "203.0.113.5", "1::2::3"))
                .isEqualTo("203.0.113.5");
        assertThat(resolveWithHeader(resolver, "203.0.113.5", "example.com"))
                .isEqualTo("203.0.113.5");
        assertThat(resolveWithHeader(resolver, "203.0.113.5", "  "))
                .isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("Cloudflare 헤더가 없으면 원격 주소를 사용한다")
    void fallsBackToRemoteAddressWhenHeaderMissing() {
        MockHttpServletRequest request = request("::1");

        ClientIpResolver resolver = new ClientIpResolver(true);

        assertThat(resolver.resolve(request)).isEqualTo("::1");
    }

    @Test
    @DisplayName("X-Forwarded-For가 매 요청마다 바뀌어도 Cloudflare 헤더가 같으면 키가 같다")
    void ignoresRotatingXForwardedForWhenCloudflareHeaderIsFixed() {
        ClientIpResolver resolver = new ClientIpResolver(true);

        String first = resolveWithHeaders(resolver, "1.2.3.4", "10.0.0.1");
        String second = resolveWithHeaders(resolver, "1.2.3.4", "10.0.0.2");
        String third = resolveWithHeaders(resolver, "1.2.3.4", "198.51.100.9");

        assertThat(first).isEqualTo("1.2.3.4");
        assertThat(second).isEqualTo(first);
        assertThat(third).isEqualTo(first);
    }

    private String resolveWithHeader(ClientIpResolver resolver, String remoteAddress, String header) {
        MockHttpServletRequest request = request(remoteAddress);
        request.addHeader(CLOUDFLARE_HEADER, header);
        return resolver.resolve(request);
    }

    private String resolveWithHeaders(ClientIpResolver resolver,
                                      String cloudflareHeader,
                                      String forwardedFor) {
        MockHttpServletRequest request = request("203.0.113.5");
        request.addHeader(CLOUDFLARE_HEADER, cloudflareHeader);
        request.addHeader("X-Forwarded-For", forwardedFor);
        return resolver.resolve(request);
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me/data");
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
