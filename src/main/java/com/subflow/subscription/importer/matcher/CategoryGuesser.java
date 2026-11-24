package com.subflow.subscription.importer.matcher;

import com.subflow.subscription.brand.ServiceBrand;
import com.subflow.subscription.domain.Category;

import java.util.Map;

/**
 * 브랜드 또는 가맹점 키워드로 카테고리를 추정한다.
 */
public final class CategoryGuesser {

    private static final Map<ServiceBrand, Category> BRAND_CATEGORIES = Map.ofEntries(
            Map.entry(ServiceBrand.NETFLIX, Category.OTT),
            Map.entry(ServiceBrand.YOUTUBE, Category.OTT),
            Map.entry(ServiceBrand.YOUTUBE_MUSIC, Category.MUSIC),
            Map.entry(ServiceBrand.SPOTIFY, Category.MUSIC),
            Map.entry(ServiceBrand.APPLE_MUSIC, Category.MUSIC),
            Map.entry(ServiceBrand.APPLE, Category.CLOUD),
            Map.entry(ServiceBrand.DROPBOX, Category.CLOUD),
            Map.entry(ServiceBrand.GOOGLE, Category.CLOUD),
            Map.entry(ServiceBrand.NOTION, Category.PRODUCTIVITY),
            Map.entry(ServiceBrand.FIGMA, Category.PRODUCTIVITY),
            Map.entry(ServiceBrand.GITHUB, Category.PRODUCTIVITY),
            Map.entry(ServiceBrand.ZOOM, Category.PRODUCTIVITY),
            Map.entry(ServiceBrand.STEAM, Category.GAME),
            Map.entry(ServiceBrand.PLAYSTATION, Category.GAME),
            Map.entry(ServiceBrand.COURSERA, Category.EDUCATION),
            Map.entry(ServiceBrand.UDEMY, Category.EDUCATION),
            Map.entry(ServiceBrand.DUOLINGO, Category.EDUCATION),
            Map.entry(ServiceBrand.NAVER, Category.SHOPPING)
    );

    private CategoryGuesser() {
    }

    public static Category guess(ServiceBrand brand, String merchant) {
        if (brand != null && BRAND_CATEGORIES.containsKey(brand)) {
            return BRAND_CATEGORIES.get(brand);
        }
        return Category.OTHER;
    }
}
