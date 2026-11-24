package com.subflow.global.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 감사 이벤트. 원문 이메일·IP·토큰은 저장하지 않고 해시와 코드만 남긴다.
 */
@Entity
@Table(name = "audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditOutcome outcome;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_email_hash", length = 64)
    private String actorEmailHash;

    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "request_id", length = 36)
    private String requestId;

    @Column(length = 500)
    private String detail;

    @Builder
    public AuditEvent(LocalDateTime occurredAt,
                      AuditEventType eventType,
                      AuditOutcome outcome,
                      Long actorUserId,
                      String actorEmailHash,
                      String targetType,
                      Long targetId,
                      String ipHash,
                      String requestId,
                      String detail) {
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.outcome = outcome;
        this.actorUserId = actorUserId;
        this.actorEmailHash = actorEmailHash;
        this.targetType = targetType;
        this.targetId = targetId;
        this.ipHash = ipHash;
        this.requestId = requestId;
        this.detail = detail;
    }
}
