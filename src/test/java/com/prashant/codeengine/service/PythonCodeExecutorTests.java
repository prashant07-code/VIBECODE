package com.prashant.codeengine.service;

import com.prashant.codeengine.dto.CodeExecutionResultDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonCodeExecutorTests {

    private final PythonCodeExecutor pythonCodeExecutor =
            new PythonCodeExecutor(new ProcessExecutionService());

    @Test
    void executesPythonProgramSuccessfully() {
        CodeExecutionResultDTO result = pythonCodeExecutor.execute(
                "print('python-success')",
                null
        );

        assertTrue(pythonCodeExecutor.supportsLanguage(" PYTHON "));
        assertFalse(pythonCodeExecutor.supportsLanguage("ruby"));
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("python-success" + System.lineSeparator(), result.getOutput());
        assertEquals(0, result.getExitCode());
    }

    @Test
    void suppliesStdinToPythonProgram() {
        CodeExecutionResultDTO result = pythonCodeExecutor.execute(
                "import sys\nprint(sys.stdin.readline().strip())",
                "python-input"
        );

        assertEquals("SUCCESS", result.getStatus());
        assertEquals("python-input" + System.lineSeparator(), result.getOutput());
        assertEquals(0, result.getExitCode());
    }

    @Test
    void returnsRuntimeErrorForPythonFailure() {
        CodeExecutionResultDTO result = pythonCodeExecutor.execute(
                "import sys\nprint('python-error', file=sys.stderr)\nsys.exit(7)",
                null
        );

        assertEquals("RUNTIME_ERROR", result.getStatus());
        assertTrue(result.getError().contains("python-error"));
        assertEquals(7, result.getExitCode());
    }
}
