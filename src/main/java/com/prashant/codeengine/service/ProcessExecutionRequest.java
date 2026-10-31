package com.prashant.codeengine.service;

import java.util.List;

public record ProcessExecutionRequest(
        String sourceFileName,
        String sourceCode,
        List<String> compileCommand,
        List<String> runCommand,
        String input,
        String runtimeImage,
        long timeoutSeconds,
        int stdoutLimitBytes,
        int stderrLimitBytes) {
}
