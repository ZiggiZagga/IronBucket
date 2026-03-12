package com.ironbucket.adminshell.shell.provider;

import com.ironbucket.adminshell.catalog.CatalogProvider;
import org.springframework.shell.CompletionContext;
import org.springframework.shell.CompletionProposal;
import org.springframework.shell.standard.ValueProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdapterValueProvider implements ValueProvider {

    private final CatalogProvider catalogProvider;

    public AdapterValueProvider(CatalogProvider catalogProvider) {
        this.catalogProvider = catalogProvider;
    }

    @Override
    public List<CompletionProposal> complete(CompletionContext completionContext) {
        List<String> adapters = catalogProvider.adapters();
        return adapters.stream().map(CompletionProposal::new).toList();
    }
}
