package com.prashant.codeengine.service;

import com.prashant.codeengine.config.DockerExecutionProperties;
import com.prashant.codeengine.dto.CodeExecutionResultDTO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DockerExecutionBackendTests {

    private static final String JAVA_IMAGE =
            "registry.example/java@sha256:" + "a".repeat(64);

    private static final String PYTHON_IMAGE =
            "registry.example/python@sha256:" + "b".repeat(64);

    @Test
    void localBackendIsSelectedByDefaultConfiguration() {
        new ApplicationContextRunner()
                .withUserConfiguration(BackendConfiguration.class)
                .run(context -> {
                    assertInstanceOf(
                            ProcessExecutionService.class,
                            context.getBean(ExecutionBackend.class)
                    );

                    assertTrue(
                            context.getBeansOfType(
                                    DockerExecutionService.class
                            ).isEmpty()
                    );
                });
    }

    @Test
    void dockerBackendCanBeSelectedWithoutStartingDocker() {
        new ApplicationContextRunner()
                .withUserConfiguration(BackendConfiguration.class)
                .withPropertyValues(
                        "codeengine.execution.backend=docker"
                )
                .run(context -> {
                    assertInstanceOf(
                            DockerExecutionService.class,
                            context.getBean(ExecutionBackend.class)
                    );

                    assertTrue(
                            context.getBeansOfType(
                                    ProcessExecutionService.class
                            ).isEmpty()
                    );
                });
    }

    @Test
    void invalidBackendConfigurationSelectsNoExecutionBackend() {
        new ApplicationContextRunner()
                .withUserConfiguration(BackendConfiguration.class)
                .withPropertyValues(
                        "codeengine.execution.backend=invalid"
                )
                .run(context -> {
                    assertTrue(
                            context.getBeansOfType(
                                    ExecutionBackend.class
                            ).isEmpty(),
                            "Invalid backend must not select an execution backend."
                    );

                    assertTrue(
                            context.getBeansOfType(
                                    ProcessExecutionService.class
                            ).isEmpty(),
                            "Invalid backend must not activate the local backend."
                    );

                    assertTrue(
                            context.getBeansOfType(
                                    DockerExecutionService.class
                            ).isEmpty(),
                            "Invalid backend must not activate the Docker backend."
                    );
                });
    }

    @Test
    void javaExecutorPassesJavaSpecificConfigurationToSelectedBackend() {
        ExecutionBackend backend = mock(ExecutionBackend.class);

        JavaCodeExecutor executor =
                new JavaCodeExecutor(backend, properties());

        executor.execute("class Main {}", "java-input");

        ArgumentCaptor<ProcessExecutionRequest> captor =
                ArgumentCaptor.forClass(ProcessExecutionRequest.class);

        verify(backend).execute(captor.capture());

        ProcessExecutionRequest request = captor.getValue();

        assertEquals("Main.java", request.sourceFileName());
        assertEquals(
                List.of("javac", "Main.java"),
                request.compileCommand()
        );
        assertEquals(
                List.of("java", "Main"),
                request.runCommand()
        );
        assertEquals("java-input", request.input());
        assertEquals(JAVA_IMAGE, request.runtimeImage());
        assertEquals(5, request.timeoutSeconds());
        assertEquals(1024 * 1024, request.stdoutLimitBytes());
        assertEquals(1024 * 1024, request.stderrLimitBytes());
    }

    @Test
    void pythonExecutorPassesPythonSpecificConfigurationToSelectedBackend() {
        ExecutionBackend backend = mock(ExecutionBackend.class);

        PythonCodeExecutor executor =
                new PythonCodeExecutor(backend, properties());

        executor.execute("print('ok')", "python-input");

        ArgumentCaptor<ProcessExecutionRequest> captor =
                ArgumentCaptor.forClass(ProcessExecutionRequest.class);

        verify(backend).execute(captor.capture());

        ProcessExecutionRequest request = captor.getValue();

        assertEquals("Main.py", request.sourceFileName());
        assertEquals(null, request.compileCommand());
        assertEquals(
                List.of("python", "Main.py"),
                request.runCommand()
        );
        assertEquals("python-input", request.input());
        assertEquals(PYTHON_IMAGE, request.runtimeImage());
    }

    @Test
    void dockerBackendBuildsRestrictedContainerAndMapsExecutionResult() {
        DockerCommandRunner runner = mock(DockerCommandRunner.class);

        when(
                runner.run(
                        anyList(),
                        any(),
                        anyLong(),
                        anyInt(),
                        anyInt()
                )
        )
                .thenReturn(success("container-id"))
                .thenReturn(success(""))
                .thenReturn(success(""))
                .thenReturn(success("python-output"))
                .thenReturn(success(""));

        DockerExecutionService backend =
                new DockerExecutionService(properties(), runner);

        CodeExecutionResultDTO result =
                backend.execute(pythonRequest("print('python-output')"));

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("python-output", result.getOutput());

        ArgumentCaptor<List<String>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(runner, times(5)).run(
                captor.capture(),
                any(),
                anyLong(),
                anyInt(),
                anyInt()
        );

        List<String> create = captor.getAllValues().get(0);

        // Verify automatic image pulling is disabled.
        assertTrue(create.contains("--pull"));
        assertTrue(create.contains("never"));

        // Verify container network isolation.
        assertTrue(create.contains("--network"));
        assertTrue(create.contains("none"));

        // Verify filesystem restrictions.
        assertTrue(create.contains("--read-only"));
        assertTrue(create.contains("--tmpfs"));
        assertTrue(create.contains("--rm"));

        // Verify non-root user and capability restrictions.
        assertTrue(create.contains("--user"));
        assertTrue(create.contains("10001:10001"));
        assertTrue(create.contains("--cap-drop"));
        assertTrue(create.contains("ALL"));
        assertTrue(create.contains("--security-opt"));
        assertTrue(create.contains("no-new-privileges:true"));
        assertFalse(create.contains("--privileged"));

        // Verify CPU, memory, and process limits.
        assertTrue(create.contains("--cpus"));
        assertTrue(create.contains("1"));
        assertTrue(create.contains("--memory"));
        assertTrue(create.contains("256m"));
        assertTrue(create.contains("--pids-limit"));
        assertTrue(create.contains("64"));

        // Verify restricted container entrypoint.
        assertTrue(create.contains("--entrypoint"));
        assertTrue(create.contains("sleep"));
        assertTrue(create.contains("infinity"));

        assertTrue(captor.getAllValues().get(3).contains("exec"));

        // Execution ke dauran null input wale runner calls ko verify karo.
        verify(runner, times(4)).run(
                anyList(),
                isNull(),
                anyLong(),
                anyInt(),
                anyInt()
        );
    }

    @Test
    void refusesUnpinnedImageWithoutInvokingDockerRunner() {
        DockerCommandRunner runner = mock(DockerCommandRunner.class);

        DockerExecutionService backend =
                new DockerExecutionService(properties(), runner);

        ProcessExecutionRequest request = new ProcessExecutionRequest(
                "Main.py",
                "print('ok')",
                null,
                List.of("python", "Main.py"),
                null,
                "python:latest",
                5,
                1024 * 1024,
                1024 * 1024
        );

        assertThrows(RuntimeException.class, () -> backend.execute(request));

        verify(runner, never()).run(
                anyList(),
                any(),
                anyLong(),
                anyInt(),
                anyInt()
        );
    }

    @Test
    void refusesMissingJavaImageWithoutInvokingDockerRunner() {
        DockerCommandRunner runner = mock(DockerCommandRunner.class);

        DockerExecutionService backend =
                new DockerExecutionService(properties(), runner);

        ProcessExecutionRequest request = new ProcessExecutionRequest(
                "Main.java",
                "public class Main {}",
                List.of("javac", "Main.java"),
                List.of("java", "Main"),
                null,
                "",
                5,
                1024 * 1024,
                1024 * 1024
        );

        assertThrows(RuntimeException.class, () -> backend.execute(request));

        verify(runner, never()).run(
                anyList(),
                any(),
                anyLong(),
                anyInt(),
                anyInt()
        );
    }

    @Test
    void refusesMissingPythonImageWithoutInvokingDockerRunner() {
        DockerCommandRunner runner = mock(DockerCommandRunner.class);

        DockerExecutionService backend =
                new DockerExecutionService(properties(), runner);

        ProcessExecutionRequest request = new ProcessExecutionRequest(
                "Main.py",
                "print('ok')",
                null,
                List.of("python", "Main.py"),
                null,
                "",
                5,
                1024 * 1024,
                1024 * 1024
        );

        assertThrows(RuntimeException.class, () -> backend.execute(request));

        verify(runner, never()).run(
                anyList(),
                any(),
                anyLong(),
                anyInt(),
                anyInt()
        );
    }

    @Test
    void refusesMalformedImageReferenceWithoutInvokingDockerRunner() {
        DockerCommandRunner runner = mock(DockerCommandRunner.class);

        DockerExecutionService backend =
                new DockerExecutionService(properties(), runner);

        ProcessExecutionRequest request = new ProcessExecutionRequest(
                "Main.py",
                "print('ok')",
                null,
                List.of("python", "Main.py"),
                null,
                "python@sha256:invalid",
                5,
                1024 * 1024,
                1024 * 1024
        );

        assertThrows(RuntimeException.class, () -> backend.execute(request));

        verify(runner, never()).run(
                anyList(),
                any(),
                anyLong(),
                anyInt(),
                anyInt()
        );
    }

    @Test
    void mapsDockerCompilerFailureToExistingCompileErrorResult() {
        DockerCommandRunner runner = mock(DockerCommandRunner.class);

        AtomicInteger callNumber = new AtomicInteger();
        AtomicReference<String> compileMarker = new AtomicReference<>();

        when(
                runner.run(
                        anyList(),
                        any(),
                        anyLong(),
                        anyInt(),
                        anyInt()
                )
        ).thenAnswer(invocation -> {
            int call = callNumber.getAndIncrement();

            if (call == 3) {
                List<String> command = invocation.getArgument(0);
                String script = command.get(command.size() - 1);

                int markerStart =
                        script.indexOf("__CODEENGINE_COMPILE_FAILED_");

                if (markerStart < 0) {
                    throw new AssertionError(
                            "Compile failure marker was not found in script."
                    );
                }

                int markerEnd = script.indexOf("__", markerStart + 2);

                if (markerEnd < 0) {
                    throw new AssertionError(
                            "Compile failure marker ending was not found."
                    );
                }

                markerEnd += 2;

                compileMarker.set(script.substring(markerStart, markerEnd));

                CodeExecutionResultDTO failed = new CodeExecutionResultDTO();
                failed.setStatus("RUNTIME_ERROR");
                failed.setError(
                        "javac diagnostic\n"
                                + compileMarker.get()
                                + "\n"
                );
                failed.setExitCode(125);

                return failed;
            }

            if (call == 0) {
                return success("container-id");
            }

            return success("");
        });

        DockerExecutionService backend =
                new DockerExecutionService(properties(), runner);

        CodeExecutionResultDTO result =
                backend.execute(javaRequest("public class Main {}"));

        assertEquals("COMPILE_ERROR", result.getStatus());
        assertTrue(result.getError().contains("javac diagnostic"));
        assertFalse(result.getError().contains(compileMarker.get()));

        verify(runner, times(5)).run(
                anyList(),
                any(),
                anyLong(),
                anyInt(),
                anyInt()
        );
    }

    @Test
    void removesContainerWhenDockerStartFails() {
        DockerCommandRunner runner = mock(DockerCommandRunner.class);

        when(
                runner.run(
                        anyList(),
                        any(),
                        anyLong(),
                        anyInt(),
                        anyInt()
                )
        )
                .thenReturn(success("container-id"))
                .thenReturn(failure("RUNTIME_ERROR", "docker start failed", 1))
                .thenReturn(success(""));

        DockerExecutionService backend =
                new DockerExecutionService(properties(), runner);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> backend.execute(pythonRequest("print('ok')"))
        );

        assertTrue(exception.getMessage().contains("Docker could not start"));

        verify(runner).run(
                eq(List.of("docker", "rm", "--force", "container-id")),
                isNull(),
                eq(5L),
                eq(1024),
                eq(1024)
        );
    }

    @Test
    void removesContainerWhenSourceTransferFails() {
        DockerCommandRunner runner = mock(DockerCommandRunner.class);

        AtomicInteger callNumber = new AtomicInteger();

        when(
                runner.run(
                        anyList(),
                        any(),
                        anyLong(),
                        anyInt(),
                        anyInt()
                )
        ).thenAnswer(invocation ->
                switch (callNumber.getAndIncrement()) {
                    case 0 -> success("container-id");
                    case 1 -> success("");
                    case 2 -> failure(
                            "RUNTIME_ERROR",
                            "source transfer failed",
                            1
                    );
                    default -> success("");
                }
        );

        DockerExecutionService backend =
                new DockerExecutionService(properties(), runner);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> backend.execute(pythonRequest("print('source')"))
        );

        assertTrue(
                exception.getMessage().contains(
                        "Docker could not copy the source"
                )
        );

        verify(runner).run(
                eq(List.of("docker", "rm", "--force", "container-id")),
                isNull(),
                eq(5L),
                eq(1024),
                eq(1024)
        );

        verify(runner, times(4)).run(
                anyList(),
                any(),
                anyLong(),
                anyInt(),
                anyInt()
        );
    }

    @Test
    void attemptsForcedCleanupAfterExceptionAndSuppressesCleanupFailure() {
        DockerCommandRunner runner = mock(DockerCommandRunner.class);

        AtomicInteger callNumber = new AtomicInteger();

        when(
                runner.run(
                        anyList(),
                        any(),
                        anyLong(),
                        anyInt(),
                        anyInt()
                )
        ).thenAnswer(invocation ->
                switch (callNumber.getAndIncrement()) {
                    case 0 -> success("container-id");
                    case 1 -> success("");
                    case 2 -> throw new IllegalStateException(
                            "source transfer command crashed"
                    );
                    default -> throw new IllegalStateException(
                            "cleanup command failed"
                    );
                }
        );

        DockerExecutionService backend =
                new DockerExecutionService(properties(), runner);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> backend.execute(pythonRequest("print('source')"))
        );

        assertTrue(
                exception.getMessage().contains("Docker execution failed")
        );

        verify(runner).run(
                eq(List.of("docker", "rm", "--force", "container-id")),
                isNull(),
                eq(5L),
                eq(1024),
                eq(1024)
        );

        verify(runner, times(4)).run(
                anyList(),
                any(),
                anyLong(),
                anyInt(),
                anyInt()
        );
    }

    @Test
    void executesPythonInDockerWhenDigestPinnedImageIsConfigured() {
        String pythonImage =
                System.getenv("CODEENGINE_EXECUTION_DOCKER_PYTHON_IMAGE");

        Assumptions.assumeTrue(
                pythonImage != null
                        && pythonImage.matches(
                        ".+@sha256:[a-fA-F0-9]{64}"
                ),
                "Optional live Docker test requires a digest-pinned Python image."
        );

        CodeExecutionResultDTO result = executeLiveDockerRequest(
                new ProcessExecutionRequest(
                        "Main.py",
                        "print('codeengine-python-live')",
                        null,
                        List.of("python", "Main.py"),
                        null,
                        pythonImage,
                        5,
                        1024 * 1024,
                        1024 * 1024
                )
        );

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("codeengine-python-live\n", result.getOutput());
        assertEquals(0, result.getExitCode());
    }

    @Test
    void executesJavaInDockerWhenVerifiedDigestImageIsConfigured() {
        String javaImage =
                System.getenv("CODEENGINE_EXECUTION_DOCKER_JAVA_IMAGE");

        Assumptions.assumeTrue(
                javaImage != null
                        && javaImage.equals(
                        "eclipse-temurin@sha256:"
                                + "5d6042fb8cdc14d614e4e421f52e3211fc5aeadddce44cbf9de8ed37c791f824"
                ),
                "Optional live Docker test requires the configured verified Java image."
        );

        CodeExecutionResultDTO result = executeLiveDockerRequest(
                new ProcessExecutionRequest(
                        "Main.java",
                        "public class Main { public static void main(String[] args) { System.out.println(\"codeengine-java-live\"); } }",
                        List.of("javac", "Main.java"),
                        List.of("java", "Main"),
                        null,
                        javaImage,
                        5,
                        1024 * 1024,
                        1024 * 1024
                )
        );

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("codeengine-java-live\n", result.getOutput());
        assertEquals(0, result.getExitCode());
    }

    private static CodeExecutionResultDTO executeLiveDockerRequest(
            ProcessExecutionRequest request
    ) {
        DockerExecutionProperties properties = new DockerExecutionProperties();

        AtomicReference<String> containerId = new AtomicReference<>();
        AtomicReference<String> containerConfiguration = new AtomicReference<>();

        LocalDockerCommandRunner localRunner = new LocalDockerCommandRunner();

        DockerCommandRunner capturingRunner =
                (command, input, timeout, stdout, stderr) -> {

                    CodeExecutionResultDTO result = localRunner.run(
                            command,
                            input,
                            timeout,
                            stdout,
                            stderr
                    );

                    if (command.size() > 1
                            && "create".equals(command.get(1))) {

                        // DIAGNOSTIC: docker create ka asli result dikhao,
                        // taaki failure chhupe nahi.
                        assertEquals("SUCCESS", result.getStatus(),
                                "docker create failed. status=" + result.getStatus()
                                        + " | error=" + result.getError()
                                        + " | output=" + result.getOutput()
                                        + " | exit=" + result.getExitCode());

                        containerId.set(result.getOutput().trim());

                        CodeExecutionResultDTO inspection = localRunner.run(
                                List.of("docker", "inspect", containerId.get()),
                                null,
                                5,
                                16 * 1024,
                                16 * 1024
                        );

                        // DIAGNOSTIC: docker inspect ka asli result dikhao.
                        assertEquals("SUCCESS", inspection.getStatus(),
                                "docker inspect failed. status=" + inspection.getStatus()
                                        + " | error=" + inspection.getError()
                                        + " | exit=" + inspection.getExitCode()
                                        + " | containerId=" + containerId.get());

                        containerConfiguration.set(inspection.getOutput());
                    }

                    return result;
                };

        DockerExecutionService backend =
                new DockerExecutionService(properties, capturingRunner);

        CodeExecutionResultDTO result = backend.execute(request);

        assertTrue(containerId.get() != null && !containerId.get().isBlank());

        String configuration = containerConfiguration.get();

        assertTrue(configuration.contains("\"User\": \"10001:10001\""));
        assertTrue(configuration.contains("\"Privileged\": false"));
        assertTrue(configuration.contains("\"NetworkMode\": \"none\""));
        assertTrue(configuration.contains("\"ReadonlyRootfs\": true"));
        assertTrue(configuration.contains("\"AutoRemove\": true"));
        assertTrue(configuration.contains("\"NanoCpus\": 1000000000"));
        assertTrue(configuration.contains("\"Memory\": 268435456"));
        assertTrue(configuration.contains("\"PidsLimit\": 64"));
        assertTrue(configuration.contains("\"/tmp\": \"rw,nosuid,size=64m\""));
        assertTrue(configuration.contains(
                "\"/workspace\": \"rw,nosuid,size=64m,mode=1777\""
        ));

        assertTrue(!configuration.contains("\"Type\": \"bind\""));

        CodeExecutionResultDTO inspection = localRunner.run(
                List.of("docker", "inspect", containerId.get()),
                null,
                5,
                1024,
                1024
        );

        assertFalse("SUCCESS".equals(inspection.getStatus()));

        return result;
    }

    private static DockerExecutionProperties properties() {
        DockerExecutionProperties properties =
                new DockerExecutionProperties();

        properties.setJavaImage(JAVA_IMAGE);
        properties.setPythonImage(PYTHON_IMAGE);

        return properties;
    }

    private static ProcessExecutionRequest pythonRequest(String sourceCode) {
        return new ProcessExecutionRequest(
                "Main.py",
                sourceCode,
                null,
                List.of("python", "Main.py"),
                null,
                PYTHON_IMAGE,
                5,
                1024 * 1024,
                1024 * 1024
        );
    }

    private static ProcessExecutionRequest javaRequest(String sourceCode) {
        return new ProcessExecutionRequest(
                "Main.java",
                sourceCode,
                List.of("javac", "Main.java"),
                List.of("java", "Main"),
                null,
                JAVA_IMAGE,
                5,
                1024 * 1024,
                1024 * 1024
        );
    }

    private static CodeExecutionResultDTO success(String output) {
        CodeExecutionResultDTO result = new CodeExecutionResultDTO();

        result.setStatus("SUCCESS");
        result.setOutput(output);
        result.setExitCode(0);

        return result;
    }

    private static CodeExecutionResultDTO failure(
            String status,
            String error,
            int exitCode
    ) {
        CodeExecutionResultDTO result = new CodeExecutionResultDTO();

        result.setStatus(status);
        result.setError(error);
        result.setExitCode(exitCode);

        return result;
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            ProcessExecutionService.class,
            DockerExecutionService.class,
            DockerExecutionProperties.class,
            LocalDockerCommandRunner.class
    })
    static class BackendConfiguration {
    }
}