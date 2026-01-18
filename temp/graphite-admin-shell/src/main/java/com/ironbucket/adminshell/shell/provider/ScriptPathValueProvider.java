package com.ironbucket.adminshell.shell.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.CompletionContext;
import org.springframework.shell.CompletionProposal;
import org.springframework.shell.standard.ValueProvider;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ScriptPathValueProvider implements ValueProvider {

    private final Path scriptsDir;

    public ScriptPathValueProvider(@Value("${admin.shell.scripts-dir:/opt/scripts}") String scriptsDir) {
        this.scriptsDir = Path.of(scriptsDir);
    }

    @Override
    public List<CompletionProposal> complete(CompletionContext completionContext) {
        if (!Files.exists(scriptsDir)) {
            return List.of();
        }
        try {
            return Files.list(scriptsDir)
                .filter(path -> Files.isRegularFile(path))
                .map(path -> new CompletionProposal(path.toString()))
                .collect(Collectors.toList());
        } catch (IOException e) {
            return List.of();
        }
    }
}
