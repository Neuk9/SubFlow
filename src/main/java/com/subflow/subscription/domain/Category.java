package com.subflow.subscription.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Category {
    OTT("OTT/영상"),
    MUSIC("음악"),
    PRODUCTIVITY("생산성/업무"),
    SHOPPING("쇼핑/멤버십"),
    CLOUD("클라우드/스토리지"),
    GAME("게임"),
    EDUCATION("교육/도서"),
    OTHER("기타");

    private final String description;
}
