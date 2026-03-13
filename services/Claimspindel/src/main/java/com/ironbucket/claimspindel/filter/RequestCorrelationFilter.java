package com.ironbucket.claimspindel.filter;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Request Correlation Filter
 * 
 * Adds X-Request-ID header and populates MDC for distributed tracing
 * across multiple microservices
 */
@Component
public class RequestCorrelationFilter implements WebFilter {
    
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_CORRELATION_ID = "correlationId";
    private static final String MDC_TENANT_ID = "tenantId";
    private static final String TENANT_HEADER = "X-Tenant-ID";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);

        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = requestId;
        }
        
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = requestId;
        }

        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);
        
        // Extract tenant ID if present
        String tenantId = exchange.getRequest()
            .getHeaders()
            .getFirst(TENANT_HEADER);
        
        // Add to MDC for logging
        final String finalRequestId = requestId;
        final String finalTenantId = tenantId;
        final String finalCorrelationId = correlationId;
        
        return chain.filter(exchange).contextWrite(context -> {
            MDC.put(MDC_REQUEST_ID, finalRequestId);
            MDC.put(MDC_CORRELATION_ID, finalCorrelationId);
            if (finalTenantId != null && !finalTenantId.isEmpty()) {
                MDC.put(MDC_TENANT_ID, finalTenantId);
            }
            return context;
        }).doFinally(signalType -> {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_TENANT_ID);
        });
    }
}
