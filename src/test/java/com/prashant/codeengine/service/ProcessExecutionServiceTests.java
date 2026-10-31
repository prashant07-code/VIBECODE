package com.prashant.codeengine.service;

import com.prashant.codeengine.dto.CodeExecutionResultDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessExecutionServiceTests {

    private final ProcessExecutionService processExecutionService =
            new ProcessExecutionService();

    @Test
    void executesProcessSuccessfully() {
        CodeExecutionResultDTO result = processExecutionService.execute(
                "Program.source",
                "source",
                List.of("cmd.exe", "/c", "exit /b 0"),
                List.of("cmd.exe", "/c", "echo process-success"),
                null
        );

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("process-success" + System.lineSeparator(), result.getOutput());
        assertEquals(0, result.getExitCode());
    }

    @Test
    void suppliesStdinToProcessAndCapturesOutput() {
        CodeExecutionResultDTO result = processExecutionService.execute(
                "Program.source",
                "source",
                null,
                List.of("cmd.exe", "/c", "findstr /r ."),
                "stdin-value"
        );

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("stdin-value" + System.lineSeparator(), result.getOutput());
        assertEquals(0, result.getExitCode());
    }

    @Test
    void capturesStderrAndExitCodeForRuntimeFailure() {
        CodeExecutionResultDTO result = processExecutionService.execute(
                "Program.source",
                "source",
                null,
                List.of("cmd.exe", "/c", "echo runtime-error 1>&2 & exit /b 7"),
                null
        );

        assertEquals("RUNTIME_ERROR", result.getStatus());
        assertTrue(result.getError().contains("runtime-error"));
        assertEquals(7, result.getExitCode());
    }

    @Test
    void returnsCompilerStderrAndExitCodeWhenCompilationFails() {
        CodeExecutionResultDTO result = processExecutionService.execute(
                "Program.source",
                "source",
                List.of("cmd.exe", "/c", "echo compiler-error 1>&2 & exit /b 9"),
                List.of("cmd.exe", "/c", "echo should-not-run"),
                null
        );

        assertEquals("COMPILE_ERROR", result.getStatus());
        assertEquals("", result.getOutput());
        assertTrue(result.getError().contains("compiler-error"));
        assertEquals(9, result.getExitCode());
    }

    @Test
    void writesSourceUsingSuppliedFilename() {
        CodeExecutionResultDTO result = processExecutionService.execute(
                "custom-entry.source",
                "custom-file-content",
                null,
                List.of("cmd.exe", "/c", "type custom-entry.source"),
                null
        );

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("custom-file-content", result.getOutput());
    }

    @Test
    void executesSuccessfullyWithoutCompileCommand() {
        CodeExecutionResultDTO result = processExecutionService.execute(
                "Program.source",
                "source",
                null,
                List.of("cmd.exe", "/c", "echo no-compile"),
                null
        );

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("no-compile" + System.lineSeparator(), result.getOutput());
        assertEquals(0, result.getExitCode());
    }
}
