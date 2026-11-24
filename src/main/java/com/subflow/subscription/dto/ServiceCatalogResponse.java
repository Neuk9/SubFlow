package com.subflow.subscription.dto;

import com.subflow.subscription.domain.Category;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ServiceCatalogResponse {

    private String id;
    private String name;
    private Category category;
    private String categoryDescription;
    private String categoryColor;
    private String logo;
    private String logoType;
    private String brandColor;
    private List<ServicePlanResponse> plans;
    private String cancelGuide;
    private String cancelUrl;
}
