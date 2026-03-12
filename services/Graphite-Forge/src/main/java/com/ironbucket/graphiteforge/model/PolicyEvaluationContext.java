package com.ironbucket.graphiteforge.model;

import java.util.List;

public record PolicyEvaluationContext(
    String tenant,
    List<String> roles,
    String operation,
    String resource
) {
}
