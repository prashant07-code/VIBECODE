package com.prashant.codeengine.service;

import com.prashant.codeengine.dto.CodeExecutionResultDTO;

public interface ExecutionBackend {

    CodeExecutionResultDTO execute(ProcessExecutionRequest request);
}
