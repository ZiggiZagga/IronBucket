package com.ironbucket.adminshell.service;

import java.util.List;

public interface OrphanPartService {
    List<String> listOrphanParts(String bucket);
    int cleanupOrphanParts(String bucket, boolean force);
}
