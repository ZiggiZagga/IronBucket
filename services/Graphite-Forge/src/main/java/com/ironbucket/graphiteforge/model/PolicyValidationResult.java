package com.ironbucket.graphiteforge.model;

import java.util.List;

public record PolicyValidationResult(
    boolean valid,
    List<String> errors
) {
    public PolicyValidationResult {
        if (errors == null) {
            throw new IllegalArgumentException("Errors list cannot be null");
        }
    }

    public static PolicyValidationResult success() {
        return new PolicyValidationResult(true, List.of());
    }

    public static PolicyValidationResult failure(List<String> errors) {
        return new PolicyValidationResult(false, errors);
    }

    public boolean isValid() {
        return valid;
    }
}
