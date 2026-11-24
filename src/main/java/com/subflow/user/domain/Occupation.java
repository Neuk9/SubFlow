package com.subflow.user.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Occupation {
    HIGH_SCHOOL("고등학생", true),
    UNIVERSITY("대학생", true),
    GRADUATE("대학원생", true),
    WORKER("직장인", false),
    JOB_SEEKER("구직자", false),
    FREELANCER("프리랜서", false),
    OTHER("기타", false);

    private final String description;
    private final boolean student;
}
