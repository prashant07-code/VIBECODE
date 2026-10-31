package com.prashant.codeengine;

import com.prashant.codeengine.entity.CodeSubmission;
import com.prashant.codeengine.entity.TestCaseResult;
import com.prashant.codeengine.repository.CodeSubmissionRepository;
import com.prashant.codeengine.repository.TestCaseResultRepository;
import com.prashant.codeengine.service.LocalDockerCommandRunner;
import com.prashant.codeengine.service.JavaCodeExecutor;
import com.prashant.codeengine.service.PythonCodeExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class PythonExecutionEndpointTests {

    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mockMvc;

    @MockitoBean
    private CodeSubmissionRepository codeSubmissionRepository;

    @MockitoBean
    private TestCaseResultRepository testCaseResultRepository;

    @MockitoSpyBean
    private JavaCodeExecutor javaCodeExecutor;

    @MockitoSpyBean
    private PythonCodeExecutor pythonCodeExecutor;

    @MockitoSpyBean
    private LocalDockerCommandRunner localDockerCommandRunner;

    @BeforeEach
    void initializeMvcAndReturnSavedSubmissionWithoutDatabaseWrites() {
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
        when(codeSubmissionRepository.save(any(CodeSubmission.class)))
                .thenAnswer(invocation -> {
                    CodeSubmission submission = invocation.getArgument(0);
                    submission.setId(1L);
                    return submission;
                });
    }

    @Test
    void executesPythonThroughEndpoint() throws Exception {
        String sourceCode = "print('python-api-ok')";

        mockMvc.perform(post("/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"python\",\"sourceCode\":\""
                                + sourceCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.output")
                        .value(pythonOutputWithPlatformLineEnding("python-api-ok")));

        verify(pythonCodeExecutor).execute(eq(sourceCode), isNull());
        verify(javaCodeExecutor, never()).execute(anyString(), any());
    }

    @Test
    void selectsPythonForMixedCaseLanguageWithWhitespace() throws Exception {
        String sourceCode = "print('python-case-ok')";

        mockMvc.perform(post("/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"  PyThOn  \",\"sourceCode\":\""
                                + sourceCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.output")
                        .value(pythonOutputWithPlatformLineEnding("python-case-ok")));

        verify(pythonCodeExecutor).execute(eq(sourceCode), isNull());
        verify(javaCodeExecutor, never()).execute(anyString(), any());
    }

    @Test
    void suppliesRequestInputToPythonThroughEndpoint() throws Exception {
        String sourceCode = "import sys\nprint(sys.stdin.readline().strip())";

        mockMvc.perform(post("/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"python\",\"sourceCode\":\"import sys\\n"
                                + "print(sys.stdin.readline().strip())\",\"input\":\"api-input\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.output")
                        .value(pythonOutputWithPlatformLineEnding("api-input")));

        verify(pythonCodeExecutor).execute(eq(sourceCode), eq("api-input"));
        verify(javaCodeExecutor, never()).execute(anyString(), any());
    }

    @Test
    void preservesPythonRuntimeErrorThroughEndpoint() throws Exception {
        String sourceCode =
                "import sys\nprint('python-api-error', file=sys.stderr)\nsys.exit(7)";

        mockMvc.perform(post("/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"python\",\"sourceCode\":\"import sys\\n"
                                + "print('python-api-error', file=sys.stderr)\\n"
                                + "sys.exit(7)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNTIME_ERROR"))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString(
                        "python-api-error")))
                .andExpect(jsonPath("$.exitCode").value(7));

        verify(pythonCodeExecutor).execute(eq(sourceCode), isNull());
        verify(javaCodeExecutor, never()).execute(anyString(), any());
    }

    @Test
    void rejectsUnsupportedLanguageWithoutSelectingAnExecutor() throws Exception {
        mockMvc.perform(post("/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"ruby\",\"sourceCode\":\"puts 'hello'\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("The requested language is not supported."));

        verify(pythonCodeExecutor, never()).execute(anyString(), any());
        verify(javaCodeExecutor, never()).execute(anyString(), any());
    }

    @Test
    void timesOutAndRequestsForcedContainerRemovalForPythonThroughEndpoint()
            throws Exception {
        String sourceCode = "while True:\n    pass";

        mockMvc.perform(post("/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestContent("python", sourceCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TIME_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.exitCode").value(-1));

        assertForcedContainerRemovalRequested();
        verify(pythonCodeExecutor).execute(eq(sourceCode), isNull());
        verify(javaCodeExecutor, never()).execute(anyString(), any());
    }

    @Test
    void enforcesPythonStdoutLimitAndRequestsForcedContainerRemovalThroughEndpoint()
            throws Exception {
        String sourceCode = "import sys\n"
                + "while True:\n"
                + "    sys.stdout.write('x' * 8192)\n"
                + "    sys.stdout.flush()";

        mockMvc.perform(post("/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestContent("python", sourceCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUTPUT_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.output").value("[OUTPUT LIMIT EXCEEDED]"))
                .andExpect(jsonPath("$.error")
                        .value(org.hamcrest.Matchers.containsString("1048576 bytes")))
                .andExpect(jsonPath("$.exitCode").value(-1));

        assertForcedContainerRemovalRequested();
        verify(pythonCodeExecutor).execute(eq(sourceCode), isNull());
        verify(javaCodeExecutor, never()).execute(anyString(), any());
    }

    @Test
    void enforcesPythonStderrLimitAndRequestsForcedContainerRemovalThroughEndpoint()
            throws Exception {
        String sourceCode = "import sys\n"
                + "while True:\n"
                + "    sys.stderr.write('e' * 8192)\n"
                + "    sys.stderr.flush()";

        mockMvc.perform(post("/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestContent("python", sourceCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUTPUT_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.output").value("[OUTPUT LIMIT EXCEEDED]"))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString(
                        "Program error output exceeded the maximum allowed size")))
                .andExpect(jsonPath("$.exitCode").value(-1));

        assertForcedContainerRemovalRequested();
        verify(pythonCodeExecutor).execute(eq(sourceCode), isNull());
        verify(javaCodeExecutor, never()).execute(anyString(), any());
    }

    @Test
    void preservesJavaExecutionThroughEndpoint() throws Exception {
        String sourceCode =
                "public class Main { public static void main(String[] args) { System.out.print(\"java-api-ok\"); } }";

        mockMvc.perform(post("/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"java\",\"sourceCode\":\""
                                + sourceCode.replace("\\", "\\\\").replace("\"", "\\\"")
                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.output").value("java-api-ok"));

        verify(javaCodeExecutor).execute(eq(sourceCode), isNull());
        verify(pythonCodeExecutor, never()).execute(anyString(), any());
    }

    @Test
    void suppliesRequestInputToJavaThroughEndpoint() throws Exception {
        String sourceCode = "import java.util.Scanner; "
                + "public class Main { public static void main(String[] args) { "
                + "System.out.print(new Scanner(System.in).nextLine()); } }";

        mockMvc.perform(post("/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"java\",\"sourceCode\":\""
                                + sourceCode.replace("\\", "\\\\").replace("\"", "\\\"")
                                + "\",\"input\":\"java-input\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.output").value("java-input"));

        verify(javaCodeExecutor).execute(eq(sourceCode), eq("java-input"));
        verify(pythonCodeExecutor, never()).execute(anyString(), any());
    }

    @Test
    void returnsFilteredPaginatedHistoryAndSubmissionDetailThroughEndpoints()
            throws Exception {
        CodeSubmission submission = new CodeSubmission();
        submission.setId(9L);
        submission.setLanguage("java");
        submission.setStatus("SUCCESS");
        submission.setSourceCode("return needle");
        submission.setOutput("Status: SUCCESS\nOutput:\nneedle\nError:\nnull\nExit Code: 0");
        TestCaseResult testCaseResult = new TestCaseResult();
        testCaseResult.setInput("input");
        testCaseResult.setExpectedOutput("needle");
        testCaseResult.setActualOutput("needle");
        testCaseResult.setOutputCorrect(true);
        testCaseResult.setJudgeStatus("ACCEPTED");
        testCaseResult.setCodeSubmission(submission);
        submission.setTestCaseResults(new ArrayList<>(List.of(testCaseResult)));
        when(codeSubmissionRepository.findSubmissionsWithFilters(
                eq("java"), eq("needle"), any()))
                .thenReturn(new PageImpl<>(List.of(submission), PageRequest.of(1, 2), 5));
        when(codeSubmissionRepository.findById(9L)).thenReturn(Optional.of(submission));

        mockMvc.perform(get("/execute/submissions/history")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "id,desc")
                        .param("language", "java")
                        .param("search", "needle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].judgeStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.content[0].outputCorrect").value(true));
        verify(codeSubmissionRepository).findSubmissionsWithFilters(
                eq("java"),
                eq("needle"),
                eq(PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "id"))));

        mockMvc.perform(get("/execute/submissions")
                        .param("page", "1")
                        .param("size", "2")
                        .param("sort", "id,desc")
                        .param("language", "java")
                        .param("search", "needle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.content[0].judgeStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.content[0].outputCorrect").value(true));

        mockMvc.perform(get("/execute/submissions/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.judgeStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.outputCorrect").value(true))
                .andExpect(jsonPath("$.testCaseResults[0].input").value("input"))
                .andExpect(jsonPath("$.testCaseResults[0].judgeStatus").value("ACCEPTED"));

        verify(codeSubmissionRepository).findById(9L);
    }

    @Test
    void timesOutAndRequestsForcedContainerRemovalForJavaThroughEndpoint()
            throws Exception {
        String sourceCode = "public class Main { public static void main(String[] args) { "
                + "while (true) { } } }";

        mockMvc.perform(post("/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestContent("java", sourceCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TIME_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.exitCode").value(-1));

        assertForcedContainerRemovalRequested();
        verify(javaCodeExecutor).execute(eq(sourceCode), isNull());
        verify(pythonCodeExecutor, never()).execute(anyString(), any());
    }

    @Test
    void enforcesJavaStdoutLimitAndRequestsForcedContainerRemovalThroughEndpoint()
            throws Exception {
        String sourceCode = "public class Main { public static void main(String[] args) { "
                + "while (true) { System.out.print(\"x\".repeat(8192)); "
                + "System.out.flush(); } } }";

        mockMvc.perform(post("/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestContent("java", sourceCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUTPUT_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.output").value("[OUTPUT LIMIT EXCEEDED]"))
                .andExpect(jsonPath("$.error")
                        .value(org.hamcrest.Matchers.containsString("1048576 bytes")))
                .andExpect(jsonPath("$.exitCode").value(-1));

        assertForcedContainerRemovalRequested();
        verify(javaCodeExecutor).execute(eq(sourceCode), isNull());
        verify(pythonCodeExecutor, never()).execute(anyString(), any());
    }

    private static org.hamcrest.Matcher<String> pythonOutputWithPlatformLineEnding(
            String output) {
        return anyOf(
                equalTo(output + "\n"),
                equalTo(output + "\r\n")
        );
    }

    private static String requestContent(String language, String sourceCode) {
        String escapedSource = sourceCode
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");

        return "{\"language\":\"" + language
                + "\",\"sourceCode\":\"" + escapedSource + "\"}";
    }

    private void assertForcedContainerRemovalRequested() {
        verify(localDockerCommandRunner).run(
                argThat(command -> command.size() == 4
                        && "rm".equals(command.get(1))
                        && "--force".equals(command.get(2))
                        && !command.get(3).isBlank()),
                isNull(),
                eq(5L),
                eq(1024),
                eq(1024)
        );
    }
}
