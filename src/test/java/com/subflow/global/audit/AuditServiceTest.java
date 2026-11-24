package com.subflow.global.audit;

import com.subflow.user.domain.User;
import com.subflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditServiceTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("감사 기록은 원문 이메일 대신 pepper가 적용된 해시를 저장한다")
    void storesHashedActorAndNoRawEmail() {
        auditService.record(AuditEventType.LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                "A@B.com", "user", 1L, "google");

        AuditEvent event = auditRepository.findAll().get(0);
        assertThat(event.getActorEmailHash()).hasSize(64).doesNotContain("a@b.com");
        assertThat(event.getDetail()).isEqualTo("google");
    }

    @Test
    @DisplayName("요청 컨텍스트가 있으면 requestId와 IP 해시를 함께 저장한다")
    void storesRequestIdAndIpHashWhenRequestContextExists() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("1.2.3.4");
        request.setAttribute("requestId", "req-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            auditService.record(AuditEventType.DATA_EXPORTED, AuditOutcome.SUCCESS,
                    "a@b.com", "user", 1L, "json");
            AuditEvent event = auditRepository.findAll().get(0);
            assertThat(event.getRequestId()).isEqualTo("req-1");
            assertThat(event.getIpHash()).hasSize(64).doesNotContain("1.2.3.4");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("요청 컨텍스트가 없으면 requestId와 IP 해시는 null이다")
    void toleratesMissingRequestContext() {
        RequestContextHolder.resetRequestAttributes();
        auditService.record(AuditEventType.LOGIN_FAILURE, AuditOutcome.FAILURE,
                null, "user", null, "BadCredentialsException");

        AuditEvent event = auditRepository.findAll().get(0);
        assertThat(event.getActorEmailHash()).isNull();
        assertThat(event.getRequestId()).isNull();
        assertThat(event.getIpHash()).isNull();
        assertThat(event.getDetail()).isEqualTo("BadCredentialsException");
    }

    @Test
    @DisplayName("detail은 500자로 잘라 저장한다")
    void truncatesDetail() {
        String detail = "x".repeat(600);

        auditService.record(AuditEventType.CONSENT_CHANGED, AuditOutcome.SUCCESS,
                "a@b.com", "user", 1L, detail);

        AuditEvent event = auditRepository.findAll().get(0);
        assertThat(event.getDetail()).hasSize(500);
    }

    @Test
    @DisplayName("대문자가 섞인 이메일로 저장된 사용자도 actorUserId를 정상 해석한다")
    void resolvesActorUserIdIgnoringEmailCase() {
        User saved = userRepository.save(User.builder()
                .googleSub("google-case-test")
                .email("Alice@Example.com")
                .name("앨리스")
                .build());

        auditService.record(AuditEventType.LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                "Alice@Example.com", "user", saved.getId(), "oauth2");

        AuditEvent event = auditRepository.findAll().get(0);
        assertThat(event.getActorUserId()).isEqualTo(saved.getId());
    }
}
