package com.ironbucket.adminshell.shell.provider;

import com.ironbucket.adminshell.catalog.CatalogProvider;
import org.springframework.shell.CompletionContext;
import org.springframework.shell.CompletionProposal;
import org.springframework.shell.standard.ValueProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BucketValueProvider implements ValueProvider {

    private final CatalogProvider catalogProvider;

    public BucketValueProvider(CatalogProvider catalogProvider) {
        this.catalogProvider = catalogProvider;
    }

    @Override
    public List<CompletionProposal> complete(CompletionContext completionContext) {
        List<String> buckets = catalogProvider.buckets();
        return buckets.stream().map(CompletionProposal::new).toList();
    }
}
