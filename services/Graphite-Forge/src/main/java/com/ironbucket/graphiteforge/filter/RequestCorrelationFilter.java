package com.ironbucket.graphiteforge.filter;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class RequestCorrelationFilter implements WebFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String MDC_REQUEST_ID = "requestId";
    private static final String MDC_CORRELATION_ID = "correlationId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = requestId;
        }
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = requestId;
        }

        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);

        String finalRequestId = requestId;
        String finalCorrelationId = correlationId;
        return chain.filter(exchange)
            .contextWrite(context -> {
                MDC.put(MDC_REQUEST_ID, finalRequestId);
                MDC.put(MDC_CORRELATION_ID, finalCorrelationId);
                return context;
            })
            .doFinally(signalType -> {
                MDC.remove(MDC_REQUEST_ID);
                MDC.remove(MDC_CORRELATION_ID);
            });
    }
}