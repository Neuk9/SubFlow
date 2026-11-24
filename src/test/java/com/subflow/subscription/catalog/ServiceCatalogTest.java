package com.subflow.subscription.catalog;

import com.subflow.subscription.dto.ServiceCatalogResponse;
import com.subflow.subscription.dto.ServicePlanResponse;
import com.subflow.subscription.domain.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceCatalogTest {

    @Test
    @DisplayName("카탈로그에 서비스가 여러 개 등록되어 있다")
    void catalogHasServices() {
        assertThat(ServiceCatalog.services()).hasSizeGreaterThanOrEqualTo(40);
    }

    @Test
    @DisplayName("서비스 ID는 중복되지 않는다")
    void serviceIdsAreUnique() {
        List<String> ids = ServiceCatalog.services().stream()
                .map(ServiceCatalogResponse::getId)
                .toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("모든 서비스는 유효한 요금제를 하나 이상 가진다")
    void everyServiceHasValidPlans() {
        Set<String> planIds = new HashSet<>();

        for (ServiceCatalogResponse service : ServiceCatalog.services()) {
            assertThat(service.getName()).isNotBlank();
            assertThat(service.getCategory()).isNotNull();
            assertThat(service.getPlans()).isNotEmpty();

            for (ServicePlanResponse plan : service.getPlans()) {
                assertThat(plan.getId()).isNotBlank();
                assertThat(planIds.add(plan.getId()))
                        .as("요금제 ID 중복: %s", plan.getId())
                        .isTrue();
                assertThat(plan.getName()).isNotBlank();
                assertThat(plan.getDisplayName()).isNotBlank();
                assertThat(plan.getBillingCycle()).isNotNull();
                assertThat(plan.getFee()).isGreaterThan(BigDecimal.ZERO);
            }
        }
    }

    @Test
    @DisplayName("데모 데이터에 쓰이는 주요 서비스가 포함되어 있다")
    void containsMainServices() {
        List<String> ids = ServiceCatalog.services().stream()
                .map(ServiceCatalogResponse::getId)
                .toList();

        assertThat(ids).contains("netflix", "youtube", "coupang", "notion");
    }

    @Test
    @DisplayName("브랜드 로고가 있는 서비스는 로고 슬러그와 색상을 함께 제공한다")
    void attachesBrandLogoWhenKnown() {
        ServiceCatalogResponse netflix = ServiceCatalog.services().stream()
                .filter(service -> service.getId().equals("netflix"))
                .findFirst()
                .orElseThrow();

        assertThat(netflix.getLogo()).isEqualTo("netflix");
        assertThat(netflix.getBrandColor()).isEqualTo("#E50914");

        ServiceCatalogResponse disney = ServiceCatalog.services().stream()
                .filter(service -> service.getId().equals("disney"))
                .findFirst()
                .orElseThrow();

        assertThat(disney.getLogo()).isNull();
        assertThat(disney.getCategoryColor()).isEqualTo(Category.OTT.getColor());
    }
}
