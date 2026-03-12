package com.ironbucket.adminshell.catalog;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CatalogProvider {
    public List<String> tenants() {
        return List.of("tenant-a", "tenant-b", "tenant-c");
    }

    public List<String> buckets() {
        return List.of("alpha", "beta", "gamma");
    }

    public List<String> adapters() {
        return List.of("s3", "gcs", "azure-blob");
    }
}
