package com.prashant.codeengine.service;

import com.prashant.codeengine.dto.CodeExecutionResultDTO;
import com.prashant.codeengine.config.DockerExecutionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PythonCodeExecutor implements CodeExecutor {

    private final ExecutionBackend executionBackend;
    private final DockerExecutionProperties dockerProperties;

    public PythonCodeExecutor(ProcessExecutionService processExecutionService) {
        this(processExecutionService, new DockerExecutionProperties());
    }

    @Autowired
    public PythonCodeExecutor(
            ExecutionBackend executionBackend,
            DockerExecutionProperties dockerProperties) {
        this.executionBackend = executionBackend;
        this.dockerProperties = dockerProperties;
    }

    @Override
    public boolean supportsLanguage(String language) {
        return language != null
                && "python".equalsIgnoreCase(language.trim());
    }

    @Override
    public CodeExecutionResultDTO execute(String sourceCode, String input) {
        return executionBackend.execute(new ProcessExecutionRequest(
                "Main.py",
                sourceCode,
                null,
                List.of("python", "Main.py"),
                input,
                dockerProperties.getPythonImage(),
                5,
                1024 * 1024,
                1024 * 1024
        ));
    }
}
