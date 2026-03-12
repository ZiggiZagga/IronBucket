package com.ironbucket.adminshell.shell.provider;

import com.ironbucket.adminshell.catalog.CatalogProvider;
import org.junit.jupiter.api.Test;
import org.springframework.shell.CompletionProposal;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueProvidersTest {

    private final CatalogProvider catalogProvider = new CatalogProvider();

    @Test
    void tenantProviderCompletesTenants() {
        TenantValueProvider provider = new TenantValueProvider(catalogProvider);
        List<String> proposals = provider.complete(null).stream().map(CompletionProposal::value).toList();
        assertTrue(proposals.contains("tenant-a"));
    }

    @Test
    void bucketProviderCompletesBuckets() {
        BucketValueProvider provider = new BucketValueProvider(catalogProvider);
        List<String> proposals = provider.complete(null).stream().map(CompletionProposal::value).toList();
        assertTrue(proposals.contains("alpha"));
    }

    @Test
    void adapterProviderCompletesAdapters() {
        AdapterValueProvider provider = new AdapterValueProvider(catalogProvider);
        List<String> proposals = provider.complete(null).stream().map(CompletionProposal::value).toList();
        assertTrue(proposals.contains("s3"));
    }
}
