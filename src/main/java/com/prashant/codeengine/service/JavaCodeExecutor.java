package com.prashant.codeengine.service;

import com.prashant.codeengine.dto.CodeExecutionResultDTO;
import com.prashant.codeengine.config.DockerExecutionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JavaCodeExecutor implements CodeExecutor {

    private final ExecutionBackend executionBackend;
    private final DockerExecutionProperties dockerProperties;

    public JavaCodeExecutor() {
        this(new ProcessExecutionService(), new DockerExecutionProperties());
    }

    public JavaCodeExecutor(ProcessExecutionService processExecutionService) {
        this(processExecutionService, new DockerExecutionProperties());
    }

    @Autowired
    public JavaCodeExecutor(
            ExecutionBackend executionBackend,
            DockerExecutionProperties dockerProperties) {
        this.executionBackend = executionBackend;
        this.dockerProperties = dockerProperties;
    }

    @Override
    public boolean supportsLanguage(String language) {
        return language != null
                && "java".equalsIgnoreCase(language.trim());
    }

    @Override
    public CodeExecutionResultDTO execute(String sourceCode, String input) {
        return executionBackend.execute(new ProcessExecutionRequest(
                "Main.java",
                sourceCode,
                List.of("javac", "Main.java"),
                List.of("java", "Main"),
                input,
                dockerProperties.getJavaImage(),
                5,
                1024 * 1024,
                1024 * 1024
        ));
    }
}
