package com.subflow.subscription.brand;

import lombok.Getter;

import java.util.Locale;

/**
 * 구독 서비스명을 브랜드 로고(simple-icons 슬러그)와 브랜드 색상으로 매핑한다.
 * <p>
 * 로고 파일은 CC0 라이선스의 simple-icons 에셋을 사용하며, 각 상표는 소유자에게 있다.
 * 서비스 식별 목적으로만 사용한다.
 */
@Getter
public enum ServiceBrand {
    // 구체적인 이름을 먼저 선언해야 "Apple Music"이 "Apple"로 잘못 매칭되지 않는다.
    NETFLIX("netflix", "Netflix", "#E50914", "netflix", "넷플릭스"),
    SPOTIFY("spotify", "Spotify", "#1DB954", "spotify", "스포티파이"),
    YOUTUBE_MUSIC("youtubemusic", "YouTube Music", "#FF0000", "youtubemusic", "유튜브뮤직"),
    YOUTUBE("youtube", "YouTube", "#FF0000", "youtube", "유튜브"),
    APPLE_MUSIC("applemusic", "Apple Music", "#FA243C", "applemusic", "애플뮤직"),
    APPLE("apple", "Apple", "#000000", "apple", "애플", "icloud", "아이클라우드"),
    NOTION("notion", "Notion", "#000000", "notion", "노션"),
    FIGMA("figma", "Figma", "#F24E1E", "figma", "피그마"),
    DROPBOX("dropbox", "Dropbox", "#0061FF", "dropbox", "드롭박스"),
    GITHUB("github", "GitHub", "#181717", "github", "깃허브"),
    STEAM("steam", "Steam", "#000000", "steam", "스팀"),
    PLAYSTATION("playstation", "PlayStation", "#003791", "playstation", "플레이스테이션", "psplus", "ps플러스"),
    GOOGLE("google", "Google", "#4285F4", "google", "구글", "googleone", "구글원"),
    ZOOM("zoom", "Zoom", "#0B5CFF", "zoom", "줌"),
    COURSERA("coursera", "Coursera", "#0056D2", "coursera", "코세라"),
    UDEMY("udemy", "Udemy", "#A435F0", "udemy", "유데미"),
    DUOLINGO("duolingo", "Duolingo", "#58CC02", "duolingo", "듀오링고"),
    NAVER("naver", "Naver", "#03C75A", "naver", "네이버", "naverplus", "네이버플러스"),
    KAKAO_TALK("kakaotalk", "KakaoTalk", "#FFCD00", "kakaotalk", "카카오톡", "kakao", "카카오");

    private final String slug;
    private final String displayName;
    private final String color;
    private final String[] aliases;

    ServiceBrand(String slug, String displayName, String color, String... aliases) {
        this.slug = slug;
        this.displayName = displayName;
        this.color = color;
        this.aliases = aliases;
    }

    public static ServiceBrand resolve(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            return null;
        }
        String normalized = normalize(serviceName);
        if (normalized.isEmpty()) {
            return null;
        }
        for (ServiceBrand brand : values()) {
            for (String alias : brand.aliases) {
                if (normalized.contains(alias)) {
                    return brand;
                }
            }
        }
        return null;
    }

    private static String normalize(String serviceName) {
        return serviceName.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\-_/()+.]+", "");
    }
}
