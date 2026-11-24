package com.subflow.user.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Membership {
    SKT("SKT"),
    KT("KT"),
    LG_U_PLUS("LG U+"),
    MVNO("알뜰폰"),
    SHINHAN_CARD("신한카드"),
    SAMSUNG_CARD("삼성카드"),
    HYUNDAI_CARD("현대카드"),
    KB_CARD("KB국민카드"),
    LOTTE_CARD("롯데카드"),
    HANA_CARD("하나카드"),
    WOORI_CARD("우리카드"),
    NH_CARD("NH농협카드"),
    KAKAO_BANK("카카오뱅크"),
    TOSS_BANK("토스뱅크"),
    COUPANG_WOW("쿠팡 와우"),
    NAVER_PLUS("네이버 플러스"),
    BAEMIN_CLUB("배민 클럽");

    private final String description;
}
