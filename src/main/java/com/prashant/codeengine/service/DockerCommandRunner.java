package com.prashant.codeengine.service;

import com.prashant.codeengine.dto.CodeExecutionResultDTO;

import java.util.List;

public interface DockerCommandRunner {

    CodeExecutionResultDTO run(
            List<String> command,
            String input,
            long timeoutSeconds,
            int stdoutLimitBytes,
            int stderrLimitBytes);
}
