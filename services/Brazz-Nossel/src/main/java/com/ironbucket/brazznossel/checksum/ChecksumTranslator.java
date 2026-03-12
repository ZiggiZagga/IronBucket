package com.ironbucket.brazznossel.checksum;

public class ChecksumTranslator {

    public String translateChecksum(String value, String digestAlgorithm) {
        if (value == null || digestAlgorithm == null) {
            return "";
        }
        return digestAlgorithm.toLowerCase() + ":" + value;
    }
}
