package com.ironbucket.graphiteforge.filter;

import com.ironbucket.pactumscroll.filter.BaseRequestCorrelationWebFilter;
import org.springframework.stereotype.Component;

@Component
public class RequestCorrelationFilter extends BaseRequestCorrelationWebFilter {

    @Override
    protected boolean shouldWriteTenantId() {
        return false;
    }
}