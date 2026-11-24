package com.subflow.subscription.importer.matcher;

import java.util.Locale;

/**
 * 가맹점명에서 도메인·법인 접미사·공백·특수문자를 제거해 비교 가능한 형태로 만든다.
 */
public final class MerchantNormalizer {

    private static final String CORPORATE_SUFFIXES = "\\(주\\)|주식회사|㈜";
    private static final String DOMAIN_SUFFIXES = "\\.(com|net|org|co\\.kr|kr)";
    private static final String LATIN_SUFFIXES = "\\b(inc|ltd|corp|ab|co)\\b\\.?";
    private static final String NON_LABEL = "[^0-9a-z가-힣]";

    private MerchantNormalizer() {
    }

    public static String normalize(String merchant) {
        if (merchant == null) {
            return "";
        }
        String value = merchant.trim().toLowerCase(Locale.ROOT);
        value = value.replaceAll(CORPORATE_SUFFIXES, "");
        value = value.replaceAll(DOMAIN_SUFFIXES, "");
        value = value.replaceAll(LATIN_SUFFIXES, "");
        value = value.replaceAll(NON_LABEL, "");
        return value;
    }
}
