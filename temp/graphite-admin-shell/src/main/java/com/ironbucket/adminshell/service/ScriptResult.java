package com.ironbucket.adminshell.service;

public record ScriptResult(String script, boolean succeeded, String output) {
    public String summary() {
        return "script=%s succeeded=%s output=%s".formatted(script, succeeded, output);
    }
}
