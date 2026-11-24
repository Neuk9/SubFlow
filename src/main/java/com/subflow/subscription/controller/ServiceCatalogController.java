package com.subflow.subscription.controller;

import com.subflow.subscription.catalog.ServiceCatalog;
import com.subflow.subscription.dto.ServiceCatalogResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/services")
public class ServiceCatalogController {

    @GetMapping
    public List<ServiceCatalogResponse> getServices() {
        return ServiceCatalog.services();
    }
}
