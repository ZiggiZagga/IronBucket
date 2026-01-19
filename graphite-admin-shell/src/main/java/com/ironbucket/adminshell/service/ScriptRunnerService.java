package com.ironbucket.adminshell.service;

import java.nio.file.Path;

public interface ScriptRunnerService {
    ScriptResult run(Path scriptPath, boolean force);
}
