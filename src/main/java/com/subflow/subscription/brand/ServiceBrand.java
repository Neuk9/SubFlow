package com.subflow.subscription.brand;

import lombok.Getter;

import java.util.Locale;

/**
 * 구독 서비스명을 브랜드 로고와 브랜드 색상으로 매핑한다.
 * <p>
 * 로고 파일은 CC0 simple-icons SVG 또는 각 서비스의 파비콘 PNG를 사용하며,
 * 각 상표는 소유자에게 있다. 서비스 식별 목적으로만 사용한다.
 */
@Getter
public enum ServiceBrand {
    // 구체적인 이름을 먼저 선언해야 "Apple Music"이 "Apple"로 잘못 매칭되지 않는다.
    NETFLIX("netflix", "Netflix", "#E50914", false, "netflix", "넷플릭스"),
    SPOTIFY("spotify", "Spotify", "#1DB954", false, "spotify", "스포티파이"),
    YOUTUBE_MUSIC("youtubemusic", "YouTube Music", "#FF0000", false, "youtubemusic", "유튜브뮤직"),
    YOUTUBE("youtube", "YouTube", "#FF0000", false, "youtube", "유튜브"),
    APPLE_MUSIC("applemusic", "Apple Music", "#FA243C", false, "applemusic", "애플뮤직"),
    APPLE("apple", "Apple", "#000000", false, "apple", "애플", "icloud", "아이클라우드"),
    DISNEY("disney", "Disney+", "#113CCF", true, "disney", "디즈니", "디즈니플러스"),
    TVING("tving", "TVING", "#FF153C", true, "tving", "티빙"),
    WAVVE("wavve", "Wavve", "#1356EC", true, "wavve", "웨이브"),
    WATCHA("watcha", "Watcha", "#FF0558", true, "watcha", "왓챠"),
    COUPANG_PLAY("coupangplay", "Coupang Play", "#1A73E8", true, "coupangplay", "쿠팡플레이"),
    PRIME_VIDEO("primevideo", "Prime Video", "#00A8E1", true, "primevideo", "prime video", "프라임비디오"),
    LAFTEL("laftel", "Laftel", "#7B61FF", true, "laftel", "라프텔"),
    MELON("melon", "Melon", "#00CD3C", true, "melon", "멜론"),
    GENIE("genie", "Genie Music", "#3B5BDB", true, "genie", "지니뮤직", "지니"),
    FLO("flo", "FLO", "#3D5AFE", true, "flo", "플로"),
    BUGS("bugs", "Bugs", "#FF3D00", true, "bugs", "벅스"),
    NOTION("notion", "Notion", "#000000", false, "notion", "노션"),
    FIGMA("figma", "Figma", "#F24E1E", false, "figma", "피그마"),
    DROPBOX("dropbox", "Dropbox", "#0061FF", false, "dropbox", "드롭박스"),
    GITHUB("github", "GitHub", "#181717", false, "github", "깃허브"),
    STEAM("steam", "Steam", "#000000", false, "steam", "스팀"),
    PLAYSTATION("playstation", "PlayStation", "#003791", false, "playstation", "플레이스테이션", "psplus", "ps플러스"),
    XBOX("xbox", "Xbox", "#107C10", false, "xbox", "엑스박스", "gamepass", "게임패스"),
    NINTENDO("nintendoswitch", "Nintendo", "#E60012", false, "nintendo", "닌텐도", "switchonline"),
    GOOGLE("google", "Google", "#4285F4", false, "google", "구글", "googleone", "구글원"),
    MICROSOFT("microsoft", "Microsoft", "#5E5E5E", false, "microsoft", "마이크로소프트", "office365", "m365", "onedrive", "원드라이브"),
    ADOBE("adobe", "Adobe", "#FF0000", false, "adobe", "어도비", "creativecloud", "크리에이티브클라우드"),
    CANVA("canva", "Canva", "#00C4CC", false, "canva", "캔바"),
    SLACK("slack", "Slack", "#4A154B", false, "slack", "슬랙"),
    OPENAI("openai", "OpenAI", "#412991", false, "openai", "chatgpt", "챗gpt", "챗지피티"),
    ZOOM("zoom", "Zoom", "#0B5CFF", false, "zoom", "줌"),
    COURSERA("coursera", "Coursera", "#0056D2", false, "coursera", "코세라"),
    UDEMY("udemy", "Udemy", "#A435F0", false, "udemy", "유데미"),
    DUOLINGO("duolingo", "Duolingo", "#58CC02", false, "duolingo", "듀오링고"),
    MILLIE("millie", "Millie", "#FF6B00", true, "millie", "밀리의서재", "밀리"),
    RIDI("ridi", "RIDI", "#1E9EFF", true, "ridi", "리디셀렉트", "리디"),
    CLASS101("class101", "CLASS101", "#FF5A5A", true, "class101", "클래스101"),
    NAVER("naver", "Naver", "#03C75A", false, "naver", "네이버", "naverplus", "네이버플러스"),
    COUPANG("coupang", "Coupang", "#E52528", true, "coupang", "쿠팡"),
    MUSINSA("musinsa", "Musinsa", "#000000", true, "musinsa", "무신사"),
    KURLY("kurly", "Kurly", "#5F0080", true, "kurly", "컬리", "마켓컬리"),
    KAKAO_TALK("kakaotalk", "KakaoTalk", "#FFCD00", false, "kakaotalk", "카카오톡", "kakao", "카카오");

    private final String slug;
    private final String displayName;
    private final String color;
    private final boolean image;
    private final String[] aliases;

    ServiceBrand(String slug, String displayName, String color, boolean image, String... aliases) {
        this.slug = slug;
        this.displayName = displayName;
        this.color = color;
        this.image = image;
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
