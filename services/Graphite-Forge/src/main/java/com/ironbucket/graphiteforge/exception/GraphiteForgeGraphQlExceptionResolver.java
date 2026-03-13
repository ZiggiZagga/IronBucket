package com.ironbucket.graphiteforge.exception;

import com.netflix.graphql.dgs.DgsComponent;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;

@DgsComponent
public class GraphiteForgeGraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GraphiteForgeGraphQlExceptionResolver.class);

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        String correlationId = MDC.get("correlationId");
        String traceId = MDC.get("traceId");
        String field = env.getField().getName();

        if (ex instanceof IllegalArgumentException || ex instanceof BucketNotFoundException || ex instanceof PolicyNotFoundException) {
            LOGGER.warn("GraphQL request failed with client error: field={}, correlationId={}, traceId={}, message={}",
                field, correlationId, traceId, ex.getMessage());
            return GraphqlErrorBuilder.newError(env)
                .errorType(ErrorType.ValidationError)
                .message(ex.getMessage())
                .extensions(Map.of("correlationId", correlationId, "traceId", traceId))
                .build();
        }

        LOGGER.error("GraphQL request failed with server error: field={}, correlationId={}, traceId={}",
            field, correlationId, traceId, ex);

        return GraphqlErrorBuilder.newError(env)
            .errorType(ErrorType.DataFetchingException)
            .message("Internal server error")
            .extensions(Map.of("correlationId", correlationId, "traceId", traceId))
            .build();
    }
}