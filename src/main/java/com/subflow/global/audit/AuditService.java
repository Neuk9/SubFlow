package com.subflow.global.audit;

import com.subflow.global.security.ClientIpResolver;
import com.subflow.global.security.SecurityProperties;
import com.subflow.user.domain.User;
import com.subflow.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

/**
 * PII 없는 감사 이벤트를 기록한다.
 *
 * <p>도메인 이벤트는 업무 트랜잭션에 참여하고, CSRF·요청 제한·로그인 실패 같은
 * 인프라 이벤트는 호출부에서 best-effort로 감싼다.</p>
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final String REQUEST_ID_ATTRIBUTE = "requestId";
    private static final int DETAIL_MAX_LENGTH = 500;
    private static final int REQUEST_ID_MAX_LENGTH = 36;

    private final AuditRepository auditRepository;
    private final UserRepository userRepository;
    private final SecurityProperties securityProperties;
    private final Clock clock;

    @Transactional
    public void record(AuditEventType type,
                       AuditOutcome outcome,
                       String actorEmail,
                       String targetType,
                       Long targetId,
                       String detail) {
        String email = StringUtils.hasText(actorEmail) ? actorEmail.trim() : null;
        HttpServletRequest request = currentRequest();

        auditRepository.save(AuditEvent.builder()
                .occurredAt(LocalDateTime.now(clock))
                .eventType(type)
                .outcome(outcome)
                .actorUserId(actorUserId(email))
                .actorEmailHash(email == null ? null : hash("email:", email.toLowerCase(Locale.ROOT)))
                .targetType(targetType)
                .targetId(targetId)
                .ipHash(ipHash(request))
                .requestId(requestId(request))
                .detail(truncate(detail))
                .build());
    }

    private Long actorUserId(String email) {
        if (email == null) {
            return null;
        }
        return userRepository.findByEmail(email)
                .map(User::getId)
                .orElse(null);
    }

    private HttpServletRequest currentRequest() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest();
    }

    private String requestId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (value == null) {
            return null;
        }
        return truncate(String.valueOf(value), REQUEST_ID_MAX_LENGTH);
    }

    private String ipHash(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String ip = new ClientIpResolver(securityProperties.trustCloudflareHeaders()).resolve(request);
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        return hash("ip:", ip);
    }

    private String hash(String prefix, String value) {
        String pepper = securityProperties.auditHashPepper() == null
                ? ""
                : securityProperties.auditHashPepper();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((prefix + value + pepper).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }

    private String truncate(String value) {
        return truncate(value, DETAIL_MAX_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
