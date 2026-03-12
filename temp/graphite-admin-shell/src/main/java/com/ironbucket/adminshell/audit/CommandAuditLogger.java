package com.ironbucket.adminshell.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class CommandAuditLogger implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(CommandAuditLogger.class);

    @Override
    public void record(String command, String argsSummary) {
        // Minimal structured audit log; in production this should forward to PostgreSQL/SIEM
        String requestId = UUID.randomUUID().toString();
        log.info("audit command={} args={} ts={} requestId={}", command, argsSummary, Instant.now(), requestId);
    }
}
