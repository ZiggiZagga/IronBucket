package com.ironbucket.claimspindel.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ironbucket.pactumscroll.error.AbstractGlobalErrorWebExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GlobalErrorWebExceptionHandler extends AbstractGlobalErrorWebExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalErrorWebExceptionHandler.class);

    public GlobalErrorWebExceptionHandler(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    protected Logger logger() {
        return LOGGER;
    }
}