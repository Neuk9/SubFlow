package com.subflow.user.domain;

import com.subflow.global.common.BaseTimeEntity;
import com.subflow.subscription.domain.Category;
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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_sub", nullable = false, unique = true, length = 255)
    private String googleSub;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 100)
    private String name;

    @Column(name = "picture_url", length = 500)
    private String pictureUrl;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Occupation occupation;

    @Column(name = "household_size")
    private Integer householdSize;

    @Column(length = 500)
    private String interests;

    @Column(length = 1000)
    private String memberships;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Builder
    public User(String googleSub, String email, String name, String pictureUrl) {
        this.googleSub = googleSub;
        this.email = email;
        this.name = name;
        this.pictureUrl = pictureUrl;
        this.onboardingCompleted = false;
    }

    public void updateGoogleProfile(String email, String name, String pictureUrl) {
        this.email = email;
        this.name = name;
        this.pictureUrl = pictureUrl;
    }

    public void completeOnboarding(int birthYear, Occupation occupation, Integer householdSize,
                                   Set<Category> interestSet, Set<Membership> membershipSet) {
        this.birthYear = birthYear;
        this.occupation = occupation;
        this.householdSize = householdSize;
        this.interests = join(interestSet);
        this.memberships = join(membershipSet);
        this.onboardingCompleted = true;
    }

    public void updateProfile(Integer birthYear, Occupation occupation, Integer householdSize,
                              Set<Category> interestSet, Set<Membership> membershipSet) {
        this.birthYear = birthYear;
        this.occupation = occupation;
        this.householdSize = householdSize;
        this.interests = join(interestSet);
        this.memberships = join(membershipSet);
    }

    public Set<Category> interestSet() {
        if (interests == null || interests.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(interests.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Category::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Set<Membership> membershipSet() {
        if (memberships == null || memberships.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(memberships.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Membership::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String join(Set<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .filter(java.util.Objects::nonNull)
                .map(Enum::name)
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }
}
