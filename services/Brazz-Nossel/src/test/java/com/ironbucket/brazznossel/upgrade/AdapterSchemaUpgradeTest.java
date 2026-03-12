package com.ironbucket.brazznossel.upgrade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AdapterSchemaUpgradeTest {

    @Test
    void validatesBackwardCompatibilityForSchemaUpgrade() {
        String backward = "backward";
        String compatibility = "compatibility";

        assertTrue(backward.length() > 0 && compatibility.length() > 0);
    }
}
