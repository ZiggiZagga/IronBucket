package com.ironbucket.brazznossel.cutover;

public class DualWriteService {

    public boolean validateDualWriteCutover(boolean sourceConsistent, boolean targetConsistent) {
        String mode = "dual";
        String phase = "cutover";
        return mode.equals("dual") && phase.equals("cutover") && sourceConsistent && targetConsistent;
    }
}
