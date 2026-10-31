package com.prashant.codeengine;

import com.prashant.codeengine.dto.CodeExecutionRequestDTO;
import com.prashant.codeengine.dto.CodeExecutionResultDTO;
import com.prashant.codeengine.entity.CodeSubmission;
import com.prashant.codeengine.exception.CodeExecutionException;
import com.prashant.codeengine.exception.GlobalExceptionHandler;
import com.prashant.codeengine.controller.ExecuteController;
import com.prashant.codeengine.repository.CodeSubmissionRepository;
import com.prashant.codeengine.repository.TestCaseResultRepository;
import com.prashant.codeengine.service.CodeSubmissionService;
import com.prashant.codeengine.service.JavaCodeExecutor;
import com.prashant.codeengine.service.OutputEvaluator;
import org.modelmapper.ModelMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnsupportedLanguageHandlingTests {

    @Test
    void javaLanguageSelectsExecutorAndExecutesProgram() throws Exception {
        JavaCodeExecutor javaCodeExecutor = spy(new JavaCodeExecutor());
        CodeSubmissionRepository repository = mock(CodeSubmissionRepository.class);
        TestCaseResultRepository testCaseResultRepository =
                mock(TestCaseResultRepository.class);

        when(repository.save(any(CodeSubmission.class)))
                .thenAnswer(invocation -> {
                    CodeSubmission submission = invocation.getArgument(0);
                    submission.setId(101L);
                    return submission;
                });

        CodeSubmissionService service = new CodeSubmissionService(
                repository,
                testCaseResultRepository,
                List.of(javaCodeExecutor),
                new ModelMapper(),
                new OutputEvaluator(),
                null,
                null
        );

        String sourceCode =
                "public class Main { public static void main(String[] args) { System.out.print(\"ok\"); } }";
        CodeExecutionRequestDTO request = new CodeExecutionRequestDTO();
        request.setLanguage("java");
        request.setSourceCode(sourceCode);

        var response = service.saveSubmission(request);

        assertEquals("SUCCESS", response.getStatus());
        assertEquals("ok", response.getOutput());
        verify(javaCodeExecutor).execute(sourceCode, null);
        verify(repository).save(any(CodeSubmission.class));
    }

    @Test
    void unsupportedLanguageIsRejectedAsBadRequestWithoutExecution()
            throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);

        JavaCodeExecutor javaCodeExecutor = new JavaCodeExecutor() {
            @Override
            public CodeExecutionResultDTO execute(
                    String sourceCode,
                    String input) {

                executed.set(true);
                return super.execute(sourceCode, input);
            }
        };

        CodeSubmissionService service = new CodeSubmissionService(
                null,
                null,
                List.of(javaCodeExecutor),
                null,
                null,
                null,
                null
        );

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new ExecuteController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"ruby\",\"sourceCode\":\"puts 'hello'\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("The requested language is not supported."));

        assertFalse(executed.get());
    }

    @Test
    void javaExecutionAndExistingStatusesRemainUnchanged() {
        JavaCodeExecutor executor = new JavaCodeExecutor();

        CodeExecutionResultDTO success = executor.execute(
                "public class Main { public static void main(String[] args) { System.out.print(\"ok\"); } }",
                null
        );
        CodeExecutionResultDTO compileError = executor.execute(
                "public class Main { invalid Java }",
                null
        );
        CodeExecutionResultDTO runtimeError = executor.execute(
                "public class Main { public static void main(String[] args) { throw new RuntimeException(); } }",
                null
        );

        assertTrue(executor.supportsLanguage(" java "));
        assertTrue(executor.supportsLanguage(" JaVa "));
        assertEquals("SUCCESS", success.getStatus());
        assertEquals("ok", success.getOutput());
        assertEquals("COMPILE_ERROR", compileError.getStatus());
        assertEquals("RUNTIME_ERROR", runtimeError.getStatus());
        assertTrue(runtimeError.getExitCode() != 0);
    }

    @Test
    void otherCodeExecutionExceptionsRemainServerErrors() {
        var response = new GlobalExceptionHandler()
                .handleCodeExecutionException(
                        new CodeExecutionException("Execution failed.")
                );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().getStatus());
    }
}
