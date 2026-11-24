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

        ClientIpResolver resolver = new ClientIpResolver(false);

        assertThat(resolver.resolve(request)).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("loopback 피어가 보낸 Cloudflare 헤더는 신뢰한다")
    void trustsCloudflareHeaderFromLoopbackPeer() {
        MockHttpServletRequest request = request("127.0.0.1");
        request.addHeader(CLOUDFLARE_HEADER, "1.2.3.4");

        ClientIpResolver resolver = new ClientIpResolver(true);

        assertThat(resolver.resolve(request)).isEqualTo("1.2.3.4");
    }

    @Test
    @DisplayName("사설망 피어가 보낸 Cloudflare 헤더는 신뢰한다")
    void trustsCloudflareHeaderFromPrivatePeer() {
        MockHttpServletRequest request = request("172.18.0.5");
        request.addHeader(CLOUDFLARE_HEADER, "1.2.3.4");

        ClientIpResolver resolver = new ClientIpResolver(true);

        assertThat(resolver.resolve(request)).isEqualTo("1.2.3.4");
    }

    @Test
    @DisplayName("신뢰하지 않는 피어가 보낸 Cloudflare 헤더는 무시한다")
    void ignoresCloudflareHeaderFromPublicPeer() {
        MockHttpServletRequest request = request("203.0.113.5");
        request.addHeader(CLOUDFLARE_HEADER, "1.2.3.4");

        ClientIpResolver resolver = new ClientIpResolver(true);

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.5");
    }

    @Test
    @DisplayName("loopback 피어라도 헤더가 비어 있으면 원격 주소를 사용한다")
    void fallsBackToRemoteAddressWhenHeaderBlank() {
        MockHttpServletRequest request = request("::1");
        request.addHeader(CLOUDFLARE_HEADER, "  ");

        ClientIpResolver resolver = new ClientIpResolver(true);

        assertThat(resolver.resolve(request)).isEqualTo("::1");
    }

    private MockHttpServletRequest request(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me/data");
        request.setRemoteAddr(remoteAddress);
        return request;
    }
}
