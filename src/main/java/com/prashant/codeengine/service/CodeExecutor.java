package com.prashant.codeengine.service;

import com.prashant.codeengine.dto.CodeExecutionResultDTO;

public interface CodeExecutor {

    boolean supportsLanguage(String language);

    CodeExecutionResultDTO execute(
            String sourceCode,
            String input
    );
}
