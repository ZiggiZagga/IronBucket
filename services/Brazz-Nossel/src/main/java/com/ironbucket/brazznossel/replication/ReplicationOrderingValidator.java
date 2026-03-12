package com.ironbucket.brazznossel.replication;

import java.util.List;

public class ReplicationOrderingValidator {

    public boolean hasMonotonicSequence(List<Long> sequence) {
        if (sequence == null || sequence.isEmpty()) {
            return false;
        }
        long previous = sequence.get(0);
        for (int i = 1; i < sequence.size(); i++) {
            long current = sequence.get(i);
            if (current < previous) {
                return false;
            }
            previous = current;
        }
        return true;
    }

    public boolean validateOrdering(List<Long> ordering) {
        return hasMonotonicSequence(ordering);
    }
}
