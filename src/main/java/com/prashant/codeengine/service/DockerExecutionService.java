package com.prashant.codeengine.service;

import com.prashant.codeengine.config.DockerExecutionProperties;
import com.prashant.codeengine.dto.CodeExecutionResultDTO;
import com.prashant.codeengine.exception.CodeExecutionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(
        name = "codeengine.execution.backend",
        havingValue = "docker"
)
public class DockerExecutionService implements ExecutionBackend {

    private final DockerExecutionProperties properties;
    private final DockerCommandRunner commandRunner;

    public DockerExecutionService(
            DockerExecutionProperties properties,
            DockerCommandRunner commandRunner
    ) {
        this.properties = properties;
        this.commandRunner = commandRunner;
    }

    @Override
    public CodeExecutionResultDTO execute(ProcessExecutionRequest request) {

        validate(request);

        String containerId = null;

        String marker = "__CODEENGINE_COMPILE_FAILED_"
                + UUID.randomUUID()
                + "__";

        try {
            String containerName = "code-engine-" + UUID.randomUUID();

            // Step 1: Create a restricted container.
            CodeExecutionResultDTO created = commandRunner.run(
                    createCommand(request, containerName),
                    null,
                    30,
                    64 * 1024,
                    64 * 1024
            );

            requireCommandSuccess(
                    created,
                    "Docker could not create an execution container."
            );

            containerId = created.getOutput() == null
                    ? ""
                    : created.getOutput().trim();

            if (containerId.isEmpty()) {
                throw new CodeExecutionException(
                        "Docker returned no container identifier."
                );
            }

            // Step 2: Start the container.
            CodeExecutionResultDTO started = commandRunner.run(
                    List.of(
                            properties.getDockerCommand(),
                            "start",
                            containerId
                    ),
                    null,
                    30,
                    64 * 1024,
                    64 * 1024
            );

            requireCommandSuccess(
                    started,
                    "Docker could not start an execution container."
            );

            // Step 3: Copy source code into /workspace.
            CodeExecutionResultDTO copied = commandRunner.run(
                    List.of(
                            properties.getDockerCommand(),
                            "exec",
                            "--interactive",
                            "--user",
                            properties.getUser(),
                            "--workdir",
                            "/workspace",
                            containerId,
                            "sh",
                            "-c",
                            "cat > " + request.sourceFileName()
                    ),
                    request.sourceCode(),
                    30,
                    64 * 1024,
                    64 * 1024
            );

            requireCommandSuccess(
                    copied,
                    "Docker could not copy the source into the container."
            );

            // Step 4: Compile and execute the submitted code.
            CodeExecutionResultDTO executionResult = commandRunner.run(
                    List.of(
                            properties.getDockerCommand(),
                            "exec",
                            "--interactive",
                            "--user",
                            properties.getUser(),
                            "--workdir",
                            "/workspace",
                            containerId,
                            "sh",
                            "-c",
                            buildExecutionScript(request, marker)
                    ),
                    request.input(),
                    request.timeoutSeconds(),
                    request.stdoutLimitBytes(),
                    request.stderrLimitBytes()
            );

            return mapResult(executionResult, marker);

        } catch (CodeExecutionException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new CodeExecutionException(
                    "Docker execution failed.",
                    exception
            );

        } finally {
            // Always attempt to remove the container.
            if (containerId != null && !containerId.isBlank()) {
                try {
                    commandRunner.run(
                            List.of(
                                    properties.getDockerCommand(),
                                    "rm",
                                    "--force",
                                    containerId
                            ),
                            null,
                            5,
                            1024,
                            1024
                    );
                } catch (Exception ignored) {
                    // Preserve the original execution result or exception.
                }
            }
        }
    }

    private List<String> createCommand(
            ProcessExecutionRequest request,
            String containerName
    ) {
        return List.of(
                properties.getDockerCommand(),
                "create",

                // Never pull an image automatically.
                "--pull",
                "never",

                "--rm",
                "--name",
                containerName,
                "--network",
                "none",
                "--cpus",
                properties.getCpus(),
                "--memory",
                properties.getMemory(),
                "--pids-limit",
                String.valueOf(properties.getPidsLimit()),
                "--read-only",
                "--tmpfs",
                "/tmp:rw,nosuid,size=" + properties.getWorkspaceSize(),
                "--tmpfs",
                "/workspace:rw,nosuid,size="
                        + properties.getWorkspaceSize()
                        + ",mode=1777",
                "--user",
                properties.getUser(),
                "--cap-drop",
                "ALL",
                "--security-opt",
                "no-new-privileges:true",
                "--entrypoint",
                "sleep",
                request.runtimeImage(),
                "infinity"
        );
    }

    private String buildExecutionScript(
            ProcessExecutionRequest request,
            String marker
    ) {
        StringBuilder script = new StringBuilder();

        List<String> compileCommand = request.compileCommand();

        if (compileCommand != null && !compileCommand.isEmpty()) {
            script.append(String.join(" ", compileCommand));
            script.append(" || { printf '\\\n");
            script.append(marker);
            script.append("\\\n'; exit 86; }; ");
        }

        script.append(String.join(" ", request.runCommand()));

        return script.toString();
    }

    private CodeExecutionResultDTO mapResult(
            CodeExecutionResultDTO result,
            String marker
    ) {
        if (result == null) {
            throw new CodeExecutionException(
                    "Docker returned no execution result."
            );
        }

        String output = result.getOutput() == null
                ? ""
                : result.getOutput();

        String error = result.getError() == null
                ? ""
                : result.getError();

        String combinedOutput = output + error;

        CodeExecutionResultDTO mapped = new CodeExecutionResultDTO();

        mapped.setExitCode(result.getExitCode());

        // Compilation error.
        if (combinedOutput.contains(marker)) {
            String cleanedError = combinedOutput
                    .replace(marker, "")
                    .trim();

            mapped.setStatus("COMPILE_ERROR");
            mapped.setOutput("");
            mapped.setError(cleanedError);

            return mapped;
        }

        // Output limit exceeded.
        if ("OUTPUT_LIMIT_EXCEEDED".equals(result.getStatus())) {
            mapped.setStatus("OUTPUT_LIMIT_EXCEEDED");
            mapped.setOutput(output);
            mapped.setError(error);

            return mapped;
        }

        // Time limit exceeded.
        if ("TIME_LIMIT_EXCEEDED".equals(result.getStatus())) {
            mapped.setStatus("TIME_LIMIT_EXCEEDED");
            mapped.setOutput(output);
            mapped.setError(error);

            return mapped;
        }

        // Runtime error.
        if (result.getExitCode() != 0) {
            mapped.setStatus("RUNTIME_ERROR");
            mapped.setOutput(output);
            mapped.setError(error);

            return mapped;
        }

        // Successful execution.
        mapped.setStatus("SUCCESS");
        mapped.setOutput(output);
        mapped.setError(error);

        return mapped;
    }

    private void requireCommandSuccess(
            CodeExecutionResultDTO result,
            String message
    ) {
        if (result == null || result.getExitCode() != 0) {
            String details = "";

            if (result != null && result.getError() != null) {
                details = result.getError();
            }

            throw new CodeExecutionException(
                    details.isBlank()
                            ? message
                            : message + " " + details
            );
        }
    }

    private void validate(ProcessExecutionRequest request) {

        if (request == null) {
            throw new CodeExecutionException(
                    "Execution request cannot be null."
            );
        }

        if (request.sourceFileName() == null
                || !request.sourceFileName().matches(
                "[A-Za-z0-9_-]+\\.(java|py)"
        )) {
            throw new CodeExecutionException(
                    "Invalid source filename."
            );
        }

        if (request.runCommand() == null
                || request.runCommand().isEmpty()) {
            throw new CodeExecutionException(
                    "Execution command cannot be empty."
            );
        }

        if (request.runtimeImage() == null
                || !request.runtimeImage().matches(
                ".+@sha256:[a-fA-F0-9]{64}"
        )) {
            throw new CodeExecutionException(
                    "A valid digest-pinned Docker image is required."
            );
        }

        if (request.timeoutSeconds() < 1
                || request.stdoutLimitBytes() < 1
                || request.stderrLimitBytes() < 1) {
            throw new CodeExecutionException(
                    "Invalid execution resource limits."
            );
        }

        if (properties.getDockerCommand() == null
                || properties.getDockerCommand().isBlank()) {
            throw new CodeExecutionException(
                    "Docker command is not configured."
            );
        }

        if (properties.getUser() == null
                || !properties.getUser().matches(
                "[1-9][0-9]*(:[1-9][0-9]*)?"
        )) {
            throw new CodeExecutionException(
                    "Invalid Docker user configuration."
            );
        }

        if (properties.getPidsLimit() < 1) {
            throw new CodeExecutionException(
                    "Invalid Docker PID limit."
            );
        }

        if (properties.getCpus() == null
                || !properties.getCpus().matches(
                "(?:[1-9][0-9]*(?:\\.[0-9]+)?|0?\\.[0-9]*[1-9][0-9]*)"
        )) {
            throw new CodeExecutionException(
                    "Invalid Docker CPU configuration."
            );
        }

        if (properties.getMemory() == null
                || !properties.getMemory().matches(
                "[1-9][0-9]*[kKmMgG]?"
        )) {
            throw new CodeExecutionException(
                    "Invalid Docker memory configuration."
            );
        }

        if (properties.getWorkspaceSize() == null
                || !properties.getWorkspaceSize().matches(
                "[1-9][0-9]*[kKmMgG]?"
        )) {
            throw new CodeExecutionException(
                    "Invalid Docker workspace size configuration."
            );
        }
    }
}