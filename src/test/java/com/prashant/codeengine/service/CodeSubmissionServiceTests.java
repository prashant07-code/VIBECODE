package com.prashant.codeengine.service;

import com.prashant.codeengine.dto.CodeExecutionRequestDTO;
import com.prashant.codeengine.dto.CodeExecutionResponseDTO;
import com.prashant.codeengine.dto.CodeExecutionResultDTO;
import com.prashant.codeengine.dto.DifficultyStatisticsDTO;
import com.prashant.codeengine.dto.JudgeStatus;
import com.prashant.codeengine.dto.LanguageStatisticsDTO;
import com.prashant.codeengine.dto.SubmissionHistoryResponseDTO;
import com.prashant.codeengine.dto.SubmissionStatisticsDTO;
import com.prashant.codeengine.dto.TestCaseDTO;
import com.prashant.codeengine.entity.CodeSubmission;
import com.prashant.codeengine.entity.Difficulty;
import com.prashant.codeengine.entity.Problem;
import com.prashant.codeengine.entity.TestCaseResult;
import com.prashant.codeengine.repository.CodeSubmissionRepository;
import com.prashant.codeengine.repository.TestCaseResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodeSubmissionServiceTests {

    private CodeSubmissionRepository repository;
    private TestCaseResultRepository testCaseResultRepository;
    private ProblemService problemService;
    private ProblemTestCaseService problemTestCaseService;
    private CodeSubmissionService service;

    @BeforeEach
    void setUp() {
        repository = mock(CodeSubmissionRepository.class);
        testCaseResultRepository = mock(TestCaseResultRepository.class);
        problemService = mock(ProblemService.class);
        problemTestCaseService = mock(ProblemTestCaseService.class);
        service = new CodeSubmissionService(
                repository,
                testCaseResultRepository,
                List.of(new JavaCodeExecutor()),
                new ModelMapper(),
                new OutputEvaluator(),
                problemService,
                problemTestCaseService
        );

        when(repository.save(any(CodeSubmission.class)))
                .thenAnswer(invocation -> {
                    CodeSubmission submission = invocation.getArgument(0);
                    submission.setId(42L);
                    return submission;
                });
        when(testCaseResultRepository.save(any(TestCaseResult.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void acceptsMatchingJavaOutputAndPersistsAcceptedTestResult() throws Exception {
        CodeExecutionRequestDTO request = javaRequest(
                "public class Main { public static void main(String[] args) { "
                        + "System.out.print(\"accepted\"); } }"
        );
        request.setInput("case-input");
        request.setExpectedOutput("accepted\n");

        CodeExecutionResponseDTO response = service.saveSubmission(request);

        assertEquals("SUCCESS", response.getStatus());
        assertEquals("accepted", response.getOutput());
        assertEquals(JudgeStatus.ACCEPTED, response.getJudgeStatus());
        assertTrue(response.getOutputCorrect());

        var resultCaptor = org.mockito.ArgumentCaptor.forClass(TestCaseResult.class);
        verify(testCaseResultRepository).save(resultCaptor.capture());
        TestCaseResult result = resultCaptor.getValue();
        assertEquals("case-input", result.getInput());
        assertEquals("accepted\n", result.getExpectedOutput());
        assertEquals("accepted", result.getActualOutput());
        assertTrue(result.getOutputCorrect());
        assertEquals("ACCEPTED", result.getJudgeStatus());
        assertEquals("SUCCESS", result.getCodeSubmission().getStatus());
        assertEquals("SUCCESS", response.getStatus());
    }

    @Test
    void returnsWrongAnswerForSuccessfulJavaExecution() throws Exception {
        CodeExecutionRequestDTO request = javaRequest(
                "public class Main { public static void main(String[] args) { "
                        + "System.out.print(\"actual\"); } }"
        );
        request.setExpectedOutput("expected");

        CodeExecutionResponseDTO response = service.saveSubmission(request);

        assertEquals("SUCCESS", response.getStatus());
        assertEquals(JudgeStatus.WRONG_ANSWER, response.getJudgeStatus());
        assertFalse(response.getOutputCorrect());

        var resultCaptor = org.mockito.ArgumentCaptor.forClass(TestCaseResult.class);
        verify(testCaseResultRepository).save(resultCaptor.capture());
        assertEquals("WRONG_ANSWER", resultCaptor.getValue().getJudgeStatus());
        assertEquals("SUCCESS", resultCaptor.getValue().getCodeSubmission().getStatus());
    }

    @Test
    void doesNotPersistSingleTestResultWhenExpectedOutputIsAbsent() throws Exception {
        CodeExecutionRequestDTO request = javaRequest(
                "public class Main { public static void main(String[] args) { "
                        + "System.out.print(\"no-expected\"); } }"
        );

        CodeExecutionResponseDTO response = service.saveSubmission(request);

        assertEquals("SUCCESS", response.getStatus());
        assertNull(response.getJudgeStatus());
        assertNull(response.getOutputCorrect());
        verify(testCaseResultRepository, never()).save(any(TestCaseResult.class));
    }

    @Test
    void persistsEachMultiTestResultAgainstTheSameSubmission() throws Exception {
        CodeExecutor executor = mock(CodeExecutor.class);
        when(executor.supportsLanguage("java")).thenReturn(true);
        when(executor.execute(anyString(), eq("first-input")))
                .thenReturn(executionResult("first-output"));
        when(executor.execute(anyString(), eq("second-input")))
                .thenReturn(executionResult("wrong-output"));
        service = serviceWithExecutor(executor);

        CodeExecutionRequestDTO request = new CodeExecutionRequestDTO();
        request.setLanguage("java");
        request.setSourceCode("source");
        request.setTestCases(List.of(
                testCase("first-input", "first-output", false),
                testCase("second-input", "second-output", true)
        ));

        CodeExecutionResponseDTO response = service.saveSubmission(request);

        assertEquals(2, response.getTestCaseResults().size());
        assertEquals(JudgeStatus.WRONG_ANSWER, response.getJudgeStatus());
        assertFalse(response.getOutputCorrect());
        assertEquals("SUCCESS", response.getStatus());

        var resultsCaptor = org.mockito.ArgumentCaptor.forClass(TestCaseResult.class);
        verify(testCaseResultRepository, org.mockito.Mockito.times(2))
                .save(resultsCaptor.capture());
        List<TestCaseResult> savedResults = resultsCaptor.getAllValues();
        assertTestCaseResult(savedResults.get(0), "first-input", "first-output",
                "first-output", "ACCEPTED");
        assertTestCaseResult(savedResults.get(1), "second-input", "second-output",
                "wrong-output", "WRONG_ANSWER");
        assertSame(savedResults.get(0).getCodeSubmission(), savedResults.get(1).getCodeSubmission());
        assertEquals(42L, savedResults.get(0).getCodeSubmission().getId());
        assertEquals(2, response.getTestCaseResults().size());
    }

    @Test
    void loadsAndExecutesAllStoredProblemCasesIncludingHiddenCases() throws Exception {
        Problem problem = new Problem();
        problem.setId(7L);
        problem.setTitle("Stored cases");
        when(problemService.getProblemEntityById(7L)).thenReturn(problem);
        var publicCase = new com.prashant.codeengine.dto.ProblemTestCaseResponseDTO();
        publicCase.setInput("public-input");
        publicCase.setExpectedOutput("public-output");
        publicCase.setHidden(false);
        var hiddenCase = new com.prashant.codeengine.dto.ProblemTestCaseResponseDTO();
        hiddenCase.setInput("hidden-input");
        hiddenCase.setExpectedOutput("hidden-output");
        hiddenCase.setHidden(true);
        when(problemTestCaseService.getTestCasesByProblemId(7L))
                .thenReturn(List.of(publicCase, hiddenCase));

        CodeExecutor executor = mock(CodeExecutor.class);
        when(executor.supportsLanguage("java")).thenReturn(true);
        when(executor.execute(anyString(), eq("public-input")))
                .thenReturn(executionResult("public-output"));
        when(executor.execute(anyString(), eq("hidden-input")))
                .thenReturn(executionResult("hidden-output"));
        service = serviceWithExecutor(executor);

        CodeExecutionRequestDTO request = new CodeExecutionRequestDTO();
        request.setLanguage("java");
        request.setSourceCode("source");
        request.setProblemId(7L);

        CodeExecutionResponseDTO response = service.saveSubmission(request);

        assertEquals(7L, response.getProblemId());
        assertEquals("Stored cases", response.getProblemTitle());
        assertEquals(JudgeStatus.ACCEPTED, response.getJudgeStatus());
        assertEquals(2, response.getTestCaseResults().size());
        assertEquals("hidden-input", response.getTestCaseResults().get(1).getInput());
        verify(problemTestCaseService).getTestCasesByProblemId(7L);
        verify(executor).execute("source", "public-input");
        verify(executor).execute("source", "hidden-input");
        verify(testCaseResultRepository, org.mockito.Mockito.times(2))
                .save(any(TestCaseResult.class));
    }

    @Test
    void calculatesGlobalProblemLanguageAndDifficultyStatisticsFromAcceptedCounts()
    {
        Problem problem = new Problem();
        problem.setId(7L);
        problem.setTitle("Statistics problem");
        problem.setDifficulty(Difficulty.EASY);
        when(problemService.getProblemEntityById(7L)).thenReturn(problem);
        when(repository.count()).thenReturn(10L);
        when(repository.countAcceptedSubmissions(any(), any(), any())).thenReturn(3L);
        when(repository.countByProblemId(7L)).thenReturn(4L);
        when(repository.countByStatusIgnoreCase(anyString())).thenReturn(1L);
        when(repository.countByStatusIgnoreCaseAndProblemId(anyString(), eq(7L)))
                .thenReturn(1L);
        when(repository.findDistinctLanguages()).thenReturn(List.of("java"));
        when(repository.countByLanguageIgnoreCase("java")).thenReturn(5L);
        when(repository.countByStatusIgnoreCaseAndLanguageIgnoreCase(anyString(), eq("java")))
                .thenReturn(1L);
        when(repository.countByProblemDifficulty(anyString())).thenReturn(5L);
        when(repository.countByStatusAndProblemDifficulty(anyString(), anyString()))
                .thenReturn(1L);

        SubmissionStatisticsDTO global = service.getSubmissionStatistics();
        SubmissionStatisticsDTO forProblem = service.getSubmissionStatistics(7L);
        List<LanguageStatisticsDTO> languages = service.getLanguageStatistics();
        List<DifficultyStatisticsDTO> difficulties = service.getDifficultyStatistics();

        assertEquals(10L, global.getTotalSubmissions());
        assertEquals(3L, global.getSuccessfulSubmissions());
        assertEquals(30.0, global.getAcceptanceRate());
        assertEquals(4L, forProblem.getTotalSubmissions());
        assertEquals(3L, forProblem.getSuccessfulSubmissions());
        assertEquals(75.0, forProblem.getAcceptanceRate());
        assertEquals(1, languages.size());
        assertEquals("java", languages.get(0).getLanguage());
        assertEquals(3L, languages.get(0).getSuccessfulSubmissions());
        assertEquals(60.0, languages.get(0).getAcceptanceRate());
        assertEquals(Difficulty.values().length, difficulties.size());
        DifficultyStatisticsDTO easy = difficulties.stream()
                .filter(statistics -> "EASY".equals(statistics.getDifficulty()))
                .findFirst()
                .orElseThrow();
        assertEquals(3L, easy.getSuccessfulSubmissions());
        assertEquals(60.0, easy.getAcceptanceRate());
        verify(repository).countAcceptedSubmissions(null, null, null);
        verify(repository).countAcceptedSubmissions(7L, null, null);
        verify(repository).countAcceptedSubmissions(null, "java", null);
        for (Difficulty difficulty : Difficulty.values()) {
            verify(repository).countAcceptedSubmissions(null, null, difficulty);
        }
        verify(repository, never()).countByStatusIgnoreCase("SUCCESS");
    }

    @Test
    void mapsPaginatedHistoryAndSubmissionDetailVerdictsAndTestResults() {
        CodeSubmission submission = new CodeSubmission();
        submission.setId(9L);
        submission.setLanguage("java");
        submission.setStatus("SUCCESS");
        submission.setSourceCode("return 9");
        submission.setOutput("Status: SUCCESS\nOutput:\n9\nError:\nnull\nExit Code: 0");
        TestCaseResult testCaseResult = new TestCaseResult();
        testCaseResult.setInput("input");
        testCaseResult.setExpectedOutput("9");
        testCaseResult.setActualOutput("9");
        testCaseResult.setOutputCorrect(true);
        testCaseResult.setJudgeStatus("ACCEPTED");
        testCaseResult.setCodeSubmission(submission);
        submission.setTestCaseResults(new ArrayList<>(List.of(testCaseResult)));
        when(repository.findSubmissionsWithFilters(eq("java"), eq("return"), any()))
                .thenReturn(new PageImpl<>(List.of(submission), PageRequest.of(1, 2), 5));
        when(repository.findById(9L)).thenReturn(Optional.of(submission));

        var pageable = PageRequest.of(1, 2);
        var history = service.getSubmissionHistory(pageable, "java", "return");
        CodeExecutionResponseDTO detail = service.getSubmissionById(9L);

        assertEquals(5, history.getTotalElements());
        assertEquals(1, history.getNumber());
        assertEquals(2, history.getSize());
        SubmissionHistoryResponseDTO historyItem = history.getContent().get(0);
        assertEquals("SUCCESS", historyItem.getStatus());
        assertEquals(JudgeStatus.ACCEPTED, historyItem.getJudgeStatus());
        assertTrue(historyItem.getOutputCorrect());
        assertEquals("SUCCESS", detail.getStatus());
        assertEquals(JudgeStatus.ACCEPTED, detail.getJudgeStatus());
        assertTrue(detail.getOutputCorrect());
        assertNotNull(detail.getTestCaseResults());
        assertEquals(1, detail.getTestCaseResults().size());
        assertEquals(JudgeStatus.ACCEPTED, detail.getTestCaseResults().get(0).getJudgeStatus());
        verify(repository).findSubmissionsWithFilters(eq("java"), eq("return"), eq(pageable));
        verify(repository).findById(9L);
    }

    private CodeSubmissionService serviceWithExecutor(CodeExecutor executor) {
        return new CodeSubmissionService(
                repository,
                testCaseResultRepository,
                List.of(executor),
                new ModelMapper(),
                new OutputEvaluator(),
                problemService,
                problemTestCaseService
        );
    }

    private static CodeExecutionRequestDTO javaRequest(String sourceCode) {
        CodeExecutionRequestDTO request = new CodeExecutionRequestDTO();
        request.setLanguage("java");
        request.setSourceCode(sourceCode);
        return request;
    }

    private static TestCaseDTO testCase(String input, String expectedOutput, boolean hidden) {
        TestCaseDTO testCase = new TestCaseDTO();
        testCase.setInput(input);
        testCase.setExpectedOutput(expectedOutput);
        testCase.setHidden(hidden);
        return testCase;
    }

    private static CodeExecutionResultDTO executionResult(String output) {
        CodeExecutionResultDTO result = new CodeExecutionResultDTO();
        result.setStatus("SUCCESS");
        result.setOutput(output);
        result.setExitCode(0);
        return result;
    }

    private static void assertTestCaseResult(
            TestCaseResult result,
            String input,
            String expectedOutput,
            String actualOutput,
            String judgeStatus) {
        assertEquals(input, result.getInput());
        assertEquals(expectedOutput, result.getExpectedOutput());
        assertEquals(actualOutput, result.getActualOutput());
        assertEquals("ACCEPTED".equals(judgeStatus), result.getOutputCorrect());
        assertEquals(judgeStatus, result.getJudgeStatus());
        assertNotNull(result.getCodeSubmission());
    }
}
