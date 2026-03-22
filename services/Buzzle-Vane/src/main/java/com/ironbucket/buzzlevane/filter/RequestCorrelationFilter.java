package com.ironbucket.buzzlevane.filter;

import com.ironbucket.pactumscroll.filter.BaseRequestCorrelationWebFilter;
import org.springframework.stereotype.Component;

/**
 * Request Correlation Filter
 * 
 * Adds X-Request-ID header and populates MDC for distributed tracing
 * across multiple microservices
 */
@Component
public class RequestCorrelationFilter extends BaseRequestCorrelationWebFilter {
}
