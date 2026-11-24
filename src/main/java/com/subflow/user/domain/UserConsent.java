package com.subflow.user.domain;

import com.subflow.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_consents",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_consents_type_version",
                columnNames = {"user_id", "consent_type", "version"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 30)
    private ConsentType consentType;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at", nullable = false)
    private LocalDateTime agreedAt;

    @Builder
    public UserConsent(User user, ConsentType consentType, String version, boolean agreed, LocalDateTime agreedAt) {
        this.user = user;
        this.consentType = consentType;
        this.version = version;
        this.agreed = agreed;
        this.agreedAt = agreedAt;
    }

    public void record(boolean agreed, LocalDateTime agreedAt) {
        this.agreed = agreed;
        this.agreedAt = agreedAt;
    }
}
