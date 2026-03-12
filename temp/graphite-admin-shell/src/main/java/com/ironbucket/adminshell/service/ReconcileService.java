package com.ironbucket.adminshell.service;

public interface ReconcileService {
    ReconcileResult run(String bucket, boolean force);
}
