package com.subflow.subscription.catalog;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.dto.ServiceCatalogResponse;
import com.subflow.subscription.dto.ServicePlanResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

/**
 * 구독 등록 화면에서 선택할 수 있는 서비스/요금제 카탈로그.
 * 실제 가격은 변동될 수 있으므로 이 파일에서 수동으로 관리한다.
 */
public final class ServiceCatalog {

    private static final Map<String, Brand> BRANDS = Map.ofEntries(
            entry("netflix", new Brand("netflix", "#E50914")),
            entry("youtube", new Brand("youtube", "#FF0000")),
            entry("apple-tv", new Brand("appletv", "#000000")),
            entry("spotify", new Brand("spotify", "#1DB954")),
            entry("apple-music", new Brand("applemusic", "#FA243C")),
            entry("youtube-music", new Brand("youtubemusic", "#FF0000")),
            entry("tidal", new Brand("tidal", "#000000")),
            entry("deezer", new Brand("deezer", "#A238FF")),
            entry("notion", new Brand("notion", "#000000")),
            entry("figma", new Brand("figma", "#F24E1E")),
            entry("zoom", new Brand("zoom", "#0B5CFF")),
            entry("dropbox", new Brand("dropbox", "#0061FF")),
            entry("miro", new Brand("miro", "#050038")),
            entry("todoist", new Brand("todoist", "#E44332")),
            entry("evernote", new Brand("evernote", "#00A82D")),
            entry("grammarly", new Brand("grammarly", "#027E6F")),
            entry("github-copilot", new Brand("githubcopilot", "#000000")),
            entry("claude", new Brand("claude", "#D97757")),
            entry("perplexity", new Brand("perplexity", "#1FB8CD")),
            entry("gemini", new Brand("googlegemini", "#8E75B2")),
            entry("1password", new Brand("1password", "#145FE4")),
            entry("cursor", new Brand("cursor", "#000000")),
            entry("linear", new Brand("linear", "#5E6AD2")),
            entry("duolingo", new Brand("duolingo", "#58CC02")),
            entry("coursera", new Brand("coursera", "#0056D2")),
            entry("udemy", new Brand("udemy", "#A435F0")),
            entry("naver-plus", new Brand("naver", "#03C75A")),
            entry("naver-mybox", new Brand("naver", "#03C75A")),
            entry("playstation-plus", new Brand("playstation", "#0070D1")),
            entry("ea-play", new Brand("ea", "#000000")),
            entry("geforce-now", new Brand("nvidia", "#76B900")),
            entry("google-play-pass", new Brand("googleplay", "#414141")),
            entry("icloud", new Brand("icloud", "#3693F3")),
            entry("google-one", new Brand("google", "#4285F4"))
    );

    private static final List<ServiceCatalogResponse> SERVICES = List.of(
            /* OTT / 영상 */
            service("netflix", "넷플릭스", Category.OTT,
                    plan("netflix-ad", "광고형", "넷플릭스 광고형", 5500, BillingCycle.MONTHLY),
                    plan("netflix-basic", "베이직", "넷플릭스 베이직", 9500, BillingCycle.MONTHLY),
                    plan("netflix-standard", "스탠다드", "넷플릭스 스탠다드", 13500, BillingCycle.MONTHLY),
                    plan("netflix-premium", "프리미엄", "넷플릭스 프리미엄", 17000, BillingCycle.MONTHLY)),
            service("youtube", "유튜브 프리미엄", Category.OTT,
                    plan("youtube-individual", "개인", "유튜브 프리미엄 개인", 14900, BillingCycle.MONTHLY),
                    plan("youtube-family", "패밀리", "유튜브 프리미엄 패밀리", 22900, BillingCycle.MONTHLY)),
            service("disney", "디즈니+", Category.OTT,
                    plan("disney-standard", "스탠다드", "디즈니+ 스탠다드", 9900, BillingCycle.MONTHLY),
                    plan("disney-premium", "프리미엄", "디즈니+ 프리미엄", 13900, BillingCycle.MONTHLY)),
            service("tving", "티빙", Category.OTT,
                    plan("tving-basic", "베이직", "티빙 베이직", 5500, BillingCycle.MONTHLY),
                    plan("tving-standard", "스탠다드", "티빙 스탠다드", 9900, BillingCycle.MONTHLY),
                    plan("tving-premium", "프리미엄", "티빙 프리미엄", 13900, BillingCycle.MONTHLY)),
            service("wavve", "웨이브", Category.OTT,
                    plan("wavve-basic", "베이직", "웨이브 베이직", 5500, BillingCycle.MONTHLY),
                    plan("wavve-standard", "스탠다드", "웨이브 스탠다드", 9900, BillingCycle.MONTHLY),
                    plan("wavve-premium", "프리미엄", "웨이브 프리미엄", 13900, BillingCycle.MONTHLY)),
            service("watcha", "왓챠", Category.OTT,
                    plan("watcha-basic", "베이직", "왓챠 베이직", 7900, BillingCycle.MONTHLY),
                    plan("watcha-premium", "프리미엄", "왓챠 프리미엄", 12900, BillingCycle.MONTHLY)),
            service("coupang-play", "쿠팡플레이", Category.OTT,
                    plan("coupang-play-monthly", "월간", "쿠팡플레이", 5500, BillingCycle.MONTHLY)),
            service("apple-tv", "Apple TV+", Category.OTT,
                    plan("apple-tv-monthly", "월간", "Apple TV+", 6500, BillingCycle.MONTHLY)),
            service("prime-video", "프라임 비디오", Category.OTT,
                    plan("prime-video-monthly", "월간", "프라임 비디오", 6900, BillingCycle.MONTHLY)),
            service("laftel", "라프텔", Category.OTT,
                    plan("laftel-basic", "베이직", "라프텔 베이직", 8900, BillingCycle.MONTHLY),
                    plan("laftel-premium", "프리미엄", "라프텔 프리미엄", 12900, BillingCycle.MONTHLY)),

            /* 음악 */
            service("spotify", "스포티파이", Category.MUSIC,
                    plan("spotify-individual", "개인", "스포티파이 개인", 10900, BillingCycle.MONTHLY),
                    plan("spotify-duo", "듀오", "스포티파이 듀오", 16350, BillingCycle.MONTHLY),
                    plan("spotify-family", "패밀리", "스포티파이 패밀리", 19500, BillingCycle.MONTHLY)),
            service("apple-music", "애플 뮤직", Category.MUSIC,
                    plan("apple-music-individual", "개인", "애플 뮤직 개인", 11000, BillingCycle.MONTHLY),
                    plan("apple-music-student", "학생", "애플 뮤직 학생", 6500, BillingCycle.MONTHLY),
                    plan("apple-music-family", "가족", "애플 뮤직 가족", 16500, BillingCycle.MONTHLY)),
            service("melon", "멜론", Category.MUSIC,
                    plan("melon-streaming", "스트리밍", "멜론 스트리밍", 6900, BillingCycle.MONTHLY),
                    plan("melon-all", "음악+영상", "멜론 음악+영상", 10900, BillingCycle.MONTHLY)),
            service("youtube-music", "유튜브 뮤직", Category.MUSIC,
                    plan("youtube-music-individual", "개인", "유튜브 뮤직", 10900, BillingCycle.MONTHLY)),
            service("genie", "지니뮤직", Category.MUSIC,
                    plan("genie-monthly", "스트리밍", "지니뮤직", 6900, BillingCycle.MONTHLY)),
            service("flo", "FLO", Category.MUSIC,
                    plan("flo-monthly", "스트리밍", "FLO", 6900, BillingCycle.MONTHLY)),
            service("bugs", "벅스", Category.MUSIC,
                    plan("bugs-monthly", "스트리밍", "벅스", 6900, BillingCycle.MONTHLY)),
            service("tidal", "TIDAL", Category.MUSIC,
                    plan("tidal-monthly", "HiFi", "TIDAL", 13900, BillingCycle.MONTHLY)),
            service("deezer", "Deezer", Category.MUSIC,
                    plan("deezer-monthly", "Premium", "Deezer", 11900, BillingCycle.MONTHLY)),

            /* 생산성 / 업무 */
            service("notion", "노션", Category.PRODUCTIVITY,
                    plan("notion-plus-monthly", "Plus 월간", "노션 Plus", 15000, BillingCycle.MONTHLY),
                    plan("notion-plus-yearly", "Plus 연간", "노션 Plus (연간)", 120000, BillingCycle.YEARLY)),
            service("chatgpt", "ChatGPT", Category.PRODUCTIVITY,
                    plan("chatgpt-plus", "Plus", "ChatGPT Plus", 29000, BillingCycle.MONTHLY)),
            service("figma", "피그마", Category.PRODUCTIVITY,
                    plan("figma-professional", "Professional", "피그마 Professional", 22000, BillingCycle.MONTHLY)),
            service("slack", "Slack", Category.PRODUCTIVITY,
                    plan("slack-pro", "Pro", "Slack Pro", 10000, BillingCycle.MONTHLY)),
            service("zoom", "Zoom", Category.PRODUCTIVITY,
                    plan("zoom-pro", "Pro", "Zoom Pro", 18000, BillingCycle.MONTHLY)),
            service("dropbox", "Dropbox", Category.PRODUCTIVITY,
                    plan("dropbox-plus", "Plus", "Dropbox Plus", 15000, BillingCycle.MONTHLY)),
            service("microsoft-365", "Microsoft 365", Category.PRODUCTIVITY,
                    plan("microsoft-365-personal", "Personal", "Microsoft 365 Personal", 11900, BillingCycle.MONTHLY),
                    plan("microsoft-365-family", "Family", "Microsoft 365 Family", 15900, BillingCycle.MONTHLY)),
            service("adobe-creative-cloud", "Adobe Creative Cloud", Category.PRODUCTIVITY,
                    plan("adobe-photography", "Photography", "Adobe Photography", 13000, BillingCycle.MONTHLY),
                    plan("adobe-all-apps", "All Apps", "Adobe All Apps", 62000, BillingCycle.MONTHLY)),
            service("canva", "Canva", Category.PRODUCTIVITY,
                    plan("canva-pro", "Pro", "Canva Pro", 15000, BillingCycle.MONTHLY)),
            service("miro", "Miro", Category.PRODUCTIVITY,
                    plan("miro-starter", "Starter", "Miro Starter", 10000, BillingCycle.MONTHLY)),
            service("todoist", "Todoist", Category.PRODUCTIVITY,
                    plan("todoist-pro", "Pro", "Todoist Pro", 6000, BillingCycle.MONTHLY)),
            service("evernote", "Evernote", Category.PRODUCTIVITY,
                    plan("evernote-professional", "Professional", "Evernote Professional", 8000, BillingCycle.MONTHLY)),
            service("grammarly", "Grammarly", Category.PRODUCTIVITY,
                    plan("grammarly-premium", "Premium", "Grammarly Premium", 18000, BillingCycle.MONTHLY)),
            service("github-copilot", "GitHub Copilot", Category.PRODUCTIVITY,
                    plan("github-copilot-individual", "Individual", "GitHub Copilot", 13000, BillingCycle.MONTHLY)),
            service("claude", "Claude", Category.PRODUCTIVITY,
                    plan("claude-pro", "Pro", "Claude Pro", 29000, BillingCycle.MONTHLY)),
            service("perplexity", "Perplexity", Category.PRODUCTIVITY,
                    plan("perplexity-pro", "Pro", "Perplexity Pro", 29000, BillingCycle.MONTHLY)),
            service("gemini", "Google Gemini", Category.PRODUCTIVITY,
                    plan("gemini-ai-pro", "Google AI Pro", "Google AI Pro", 29000, BillingCycle.MONTHLY)),
            service("1password", "1Password", Category.PRODUCTIVITY,
                    plan("1password-individual", "Individual", "1Password", 4500, BillingCycle.MONTHLY)),
            service("cursor", "Cursor", Category.PRODUCTIVITY,
                    plan("cursor-pro", "Pro", "Cursor Pro", 29000, BillingCycle.MONTHLY)),
            service("linear", "Linear", Category.PRODUCTIVITY,
                    plan("linear-basic", "Basic", "Linear Basic", 10000, BillingCycle.MONTHLY)),
            service("microsoft-copilot", "Microsoft Copilot", Category.PRODUCTIVITY,
                    plan("microsoft-copilot-pro", "Pro", "Microsoft Copilot Pro", 29000, BillingCycle.MONTHLY)),

            /* 교육 / 도서 */
            service("duolingo", "Duolingo", Category.EDUCATION,
                    plan("duolingo-super", "Super", "Duolingo Super", 9900, BillingCycle.MONTHLY),
                    plan("duolingo-family", "Family", "Duolingo Family", 15900, BillingCycle.MONTHLY)),
            service("coursera", "Coursera", Category.EDUCATION,
                    plan("coursera-plus", "Plus", "Coursera Plus", 59000, BillingCycle.MONTHLY)),
            service("udemy", "Udemy", Category.EDUCATION,
                    plan("udemy-personal", "Personal", "Udemy Personal Plan", 19000, BillingCycle.MONTHLY)),
            service("millie", "밀리의 서재", Category.EDUCATION,
                    plan("millie-monthly", "월간", "밀리의 서재", 9900, BillingCycle.MONTHLY)),
            service("ridi", "리디셀렉트", Category.EDUCATION,
                    plan("ridi-select", "셀렉트", "리디셀렉트", 4900, BillingCycle.MONTHLY)),
            service("class101", "클래스101", Category.EDUCATION,
                    plan("class101-monthly", "월간", "클래스101", 19000, BillingCycle.MONTHLY)),

            /* 쇼핑 / 멤버십 */
            service("coupang", "쿠팡", Category.SHOPPING,
                    plan("coupang-wow", "와우 멤버십", "쿠팡 와우 멤버십", 7890, BillingCycle.MONTHLY)),
            service("naver-plus", "네이버 플러스 멤버십", Category.SHOPPING,
                    plan("naver-plus-monthly", "월간", "네이버 플러스 멤버십", 4900, BillingCycle.MONTHLY)),
            service("musinsa", "무신사", Category.SHOPPING,
                    plan("musinsa-club", "무신사클럽", "무신사클럽", 9900, BillingCycle.MONTHLY)),
            service("kurly", "컬리", Category.SHOPPING,
                    plan("kurly-members", "컬리멤버스", "컬리멤버스", 3900, BillingCycle.MONTHLY)),

            /* 게임 */
            service("xbox-game-pass", "Xbox Game Pass", Category.GAME,
                    plan("xbox-ultimate", "Ultimate", "Xbox Game Pass Ultimate", 16900, BillingCycle.MONTHLY)),
            service("playstation-plus", "PlayStation Plus", Category.GAME,
                    plan("ps-plus-essential", "Essential", "PlayStation Plus Essential", 8900, BillingCycle.MONTHLY),
                    plan("ps-plus-extra", "Extra", "PlayStation Plus Extra", 13900, BillingCycle.MONTHLY),
                    plan("ps-plus-premium", "Premium", "PlayStation Plus Premium", 16900, BillingCycle.MONTHLY)),
            service("nintendo-switch-online", "Nintendo Switch Online", Category.GAME,
                    plan("nintendo-individual", "개인", "Nintendo Switch Online", 4900, BillingCycle.MONTHLY),
                    plan("nintendo-family", "패밀리", "Nintendo Switch Online 패밀리", 9900, BillingCycle.MONTHLY)),
            service("ea-play", "EA Play", Category.GAME,
                    plan("ea-play-standard", "Standard", "EA Play", 5500, BillingCycle.MONTHLY),
                    plan("ea-play-pro", "Pro", "EA Play Pro", 17900, BillingCycle.MONTHLY)),
            service("geforce-now", "GeForce NOW", Category.GAME,
                    plan("geforce-now-priority", "Priority", "GeForce NOW Priority", 11000, BillingCycle.MONTHLY)),
            service("google-play-pass", "Google Play Pass", Category.GAME,
                    plan("google-play-pass-monthly", "월간", "Google Play Pass", 5900, BillingCycle.MONTHLY)),

            /* 클라우드 / 스토리지 */
            service("icloud", "iCloud+", Category.CLOUD,
                    plan("icloud-50", "50GB", "iCloud+ 50GB", 1100, BillingCycle.MONTHLY),
                    plan("icloud-200", "200GB", "iCloud+ 200GB", 3300, BillingCycle.MONTHLY),
                    plan("icloud-2tb", "2TB", "iCloud+ 2TB", 11000, BillingCycle.MONTHLY)),
            service("google-one", "Google One", Category.CLOUD,
                    plan("google-one-100", "100GB", "Google One 100GB", 2400, BillingCycle.MONTHLY),
                    plan("google-one-200", "200GB", "Google One 200GB", 3700, BillingCycle.MONTHLY)),
            service("naver-mybox", "네이버 MYBOX", Category.CLOUD,
                    plan("naver-mybox-80", "80GB", "네이버 MYBOX 80GB", 2900, BillingCycle.MONTHLY))
    );

    private ServiceCatalog() {
    }

    public static List<ServiceCatalogResponse> services() {
        return SERVICES;
    }

    private static ServiceCatalogResponse service(String id, String name, Category category, ServicePlanResponse... plans) {
        Brand brand = BRANDS.get(id);

        return ServiceCatalogResponse.builder()
                .id(id)
                .name(name)
                .category(category)
                .categoryDescription(category.getDescription())
                .categoryColor(category.getColor())
                .logo(brand != null ? brand.logo() : null)
                .brandColor(brand != null ? brand.color() : null)
                .plans(List.of(plans))
                .build();
    }

    private static ServicePlanResponse plan(String id, String name, String displayName, long fee, BillingCycle billingCycle) {
        return ServicePlanResponse.builder()
                .id(id)
                .name(name)
                .displayName(displayName)
                .fee(BigDecimal.valueOf(fee))
                .billingCycle(billingCycle)
                .billingCycleDescription(billingCycle.getDescription())
                .build();
    }

    private record Brand(String logo, String color) {
    }
}
