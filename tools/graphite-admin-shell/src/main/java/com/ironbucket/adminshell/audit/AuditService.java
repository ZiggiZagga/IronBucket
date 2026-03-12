package com.ironbucket.adminshell.audit;

public interface AuditService {
    void record(String command, String argsSummary);
}
