package com.subflow.user.repository;

import com.subflow.user.domain.ConsentType;
import com.subflow.user.domain.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    Optional<UserConsent> findByUserIdAndConsentTypeAndVersion(Long userId, ConsentType consentType, String version);

    List<UserConsent> findAllByUserId(Long userId);

    void deleteAllByUserId(Long userId);
}
