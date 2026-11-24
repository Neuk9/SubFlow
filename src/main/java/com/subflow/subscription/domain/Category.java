package com.subflow.subscription.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Category {
    OTT("OTT/영상", "#e50914"),
    MUSIC("음악", "#1db954"),
    PRODUCTIVITY("생산성/업무", "#0d6efd"),
    SHOPPING("쇼핑/멤버십", "#fd7e14"),
    CLOUD("클라우드/스토리지", "#0dcaf0"),
    GAME("게임", "#6f42c1"),
    EDUCATION("교육/도서", "#20c997"),
    OTHER("기타", "#6c757d");

    private final String description;
    private final String color;
}
