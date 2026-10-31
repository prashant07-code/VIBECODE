package com.prashant.codeengine.service;

import com.prashant.codeengine.dto.CodeExecutionRequestDTO;
import com.prashant.codeengine.dto.CodeExecutionResponseDTO;
import com.prashant.codeengine.dto.JudgeStatus;
import com.prashant.codeengine.dto.SubmissionStatisticsDTO;
import com.prashant.codeengine.dto.TestCaseDTO;
import com.prashant.codeengine.entity.CodeSubmission;
import com.prashant.codeengine.entity.Difficulty;
import com.prashant.codeengine.entity.Problem;
import com.prashant.codeengine.entity.ProblemTestCase;
import com.prashant.codeengine.entity.TestCaseResult;
import com.prashant.codeengine.repository.CodeSubmissionRepository;
import com.prashant.codeengine.repository.ProblemRepository;
import com.prashant.codeengine.repository.ProblemTestCaseRepository;
import com.prashant.codeengine.repository.TestCaseResultRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubmissionWorkflowIntegrationTests {

    private static final String ECHO_PROGRAM = "public class Main { "
            + "public static void main(String[] args) throws Exception { "
            + "System.out.print(new java.util.Scanner(System.in).nextLine()); } }";

    @Autowired
    private CodeSubmissionService codeSubmissionService;

    @Autowired
    private CodeSubmissionRepository codeSubmissionRepository;

    @Autowired
    private TestCaseResultRepository testCaseResultRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemTestCaseRepository problemTestCaseRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    @Test
    void persistsSingleAcceptedJavaSubmissionAndLinkedResult() throws Exception {
        assertTestDatabase();
        CodeExecutionRequestDTO request = request(ECHO_PROGRAM);
        request.setInput("accepted-input");
        request.setExpectedOutput("accepted-input");

        CodeExecutionResponseDTO response = codeSubmissionService.saveSubmission(request);
        entityManager.flush();
        entityManager.clear();

        CodeSubmission saved = codeSubmissionRepository.findById(response.getId())
                .orElseThrow();
        List<TestCaseResult> results = testCaseResultRepository.findAll();

        assertEquals("SUCCESS", response.getStatus());
        assertEquals(JudgeStatus.ACCEPTED, response.getJudgeStatus());
        assertTrue(response.getOutputCorrect());
        assertEquals(1, codeSubmissionRepository.count());
        assertEquals(1, results.size());
        assertEquals("accepted-input", results.get(0).getInput());
        assertEquals("accepted-input", results.get(0).getExpectedOutput());
        assertEquals("accepted-input", results.get(0).getActualOutput());
        assertTrue(results.get(0).getOutputCorrect());
        assertEquals("ACCEPTED", results.get(0).getJudgeStatus());
        assertEquals(saved.getId(), results.get(0).getCodeSubmission().getId());
    }

    @Test
    void persistsSingleWrongAnswerWithoutChangingExecutionStatus() throws Exception {
        assertTestDatabase();
        CodeExecutionRequestDTO request = request(ECHO_PROGRAM);
        request.setInput("actual-value");
        request.setExpectedOutput("expected-value");

        CodeExecutionResponseDTO response = codeSubmissionService.saveSubmission(request);
        entityManager.flush();
        entityManager.clear();

        TestCaseResult result = testCaseResultRepository.findAll().get(0);
        CodeSubmission submission = codeSubmissionRepository.findById(response.getId())
                .orElseThrow();

        assertEquals("SUCCESS", response.getStatus());
        assertEquals(JudgeStatus.WRONG_ANSWER, response.getJudgeStatus());
        assertFalse(response.getOutputCorrect());
        assertEquals("SUCCESS", submission.getStatus());
        assertEquals("WRONG_ANSWER", result.getJudgeStatus());
        assertFalse(result.getOutputCorrect());
        assertEquals(submission.getId(), result.getCodeSubmission().getId());
    }

    @Test
    void persistsEveryMultiTestResultAndDerivesAcceptedAndWrongAnswerVerdicts()
            throws Exception {
        assertTestDatabase();
        CodeExecutionRequestDTO acceptedRequest = request(ECHO_PROGRAM);
        acceptedRequest.setTestCases(List.of(
                testCase("first", "first", false),
                testCase("second", "second", true)
        ));
        CodeExecutionResponseDTO accepted =
                codeSubmissionService.saveSubmission(acceptedRequest);

        CodeExecutionRequestDTO wrongRequest = request(ECHO_PROGRAM);
        wrongRequest.setTestCases(List.of(
                testCase("third", "third", false),
                testCase("fourth", "not-fourth", true)
        ));
        CodeExecutionResponseDTO wrong = codeSubmissionService.saveSubmission(wrongRequest);
        entityManager.flush();
        entityManager.clear();

        assertEquals(JudgeStatus.ACCEPTED, accepted.getJudgeStatus());
        assertEquals(2, accepted.getTestCaseResults().size());
        assertEquals(JudgeStatus.WRONG_ANSWER, wrong.getJudgeStatus());
        assertEquals(2, wrong.getTestCaseResults().size());
        assertEquals(4, testCaseResultRepository.count());

        List<TestCaseResult> wrongResults = testCaseResultRepository.findAll().stream()
                .filter(result -> result.getCodeSubmission().getId().equals(wrong.getId()))
                .toList();
        assertEquals(2, wrongResults.size());
        assertEquals("ACCEPTED", wrongResults.get(0).getJudgeStatus());
        assertEquals("WRONG_ANSWER", wrongResults.get(1).getJudgeStatus());
        assertEquals(wrong.getId(), wrongResults.get(0).getCodeSubmission().getId());
        assertEquals(wrong.getId(), wrongResults.get(1).getCodeSubmission().getId());
    }

    @Test
    void executesStoredPublicAndHiddenProblemCasesAndPersistsProblemLink()
            throws Exception {
        assertTestDatabase();
        Problem problem = saveProblemWithCases();
        CodeExecutionRequestDTO request = request(ECHO_PROGRAM);
        request.setProblemId(problem.getId());

        CodeExecutionResponseDTO response = codeSubmissionService.saveSubmission(request);
        entityManager.flush();
        entityManager.clear();

        CodeSubmission saved = codeSubmissionRepository.findById(response.getId())
                .orElseThrow();
        List<TestCaseResult> results = testCaseResultRepository.findAll();
        assertEquals("SUCCESS", response.getStatus());
        assertEquals(JudgeStatus.ACCEPTED, response.getJudgeStatus());
        assertEquals(problem.getId(), saved.getProblem().getId());
        assertEquals(2, results.size());
        assertEquals("public-value", results.get(0).getInput());
        assertEquals("hidden-value", results.get(1).getInput());
        assertTrue(results.stream().allMatch(result ->
                result.getCodeSubmission().getId().equals(saved.getId())));
    }

    @Test
    void persistedWorkflowFeedsStatisticsHistoryAndDetail() throws Exception {
        assertTestDatabase();
        Problem problem = saveProblemWithCases();
        CodeExecutionRequestDTO acceptedRequest = request(ECHO_PROGRAM);
        acceptedRequest.setProblemId(problem.getId());
        CodeExecutionResponseDTO accepted =
                codeSubmissionService.saveSubmission(acceptedRequest);

        CodeExecutionRequestDTO wrongRequest = request(ECHO_PROGRAM);
        wrongRequest.setProblemId(problem.getId());
        wrongRequest.setTestCases(List.of(
                testCase("input-one", "input-one", false),
                testCase("input-two", "different", true)
        ));
        CodeExecutionResponseDTO wrong = codeSubmissionService.saveSubmission(wrongRequest);
        entityManager.flush();
        entityManager.clear();

        SubmissionStatisticsDTO global = codeSubmissionService.getSubmissionStatistics();
        SubmissionStatisticsDTO forProblem =
                codeSubmissionService.getSubmissionStatistics(problem.getId());
        var languageStats = codeSubmissionService.getLanguageStatistics().stream()
                .filter(statistics -> "java".equalsIgnoreCase(statistics.getLanguage()))
                .findFirst()
                .orElseThrow();
        var difficultyStats = codeSubmissionService.getDifficultyStatistics().stream()
                .filter(statistics -> "EASY".equals(statistics.getDifficulty()))
                .findFirst()
                .orElseThrow();
        var history = codeSubmissionService.getSubmissionHistory(
                PageRequest.of(0, 10), "java", ECHO_PROGRAM);
        CodeExecutionResponseDTO detail = codeSubmissionService.getSubmissionById(accepted.getId());
        var acceptedHistory = history.getContent().stream()
                .filter(item -> accepted.getId().equals(item.getId()))
                .findFirst()
                .orElseThrow();
        var wrongHistory = history.getContent().stream()
                .filter(item -> wrong.getId().equals(item.getId()))
                .findFirst()
                .orElseThrow();

        assertEquals(2, global.getTotalSubmissions());
        assertEquals(1, global.getSuccessfulSubmissions());
        assertEquals(50.0, global.getAcceptanceRate());
        assertEquals(2, forProblem.getTotalSubmissions());
        assertEquals(1, forProblem.getSuccessfulSubmissions());
        assertEquals(50.0, forProblem.getAcceptanceRate());
        assertEquals(2, languageStats.getTotalSubmissions());
        assertEquals(1, languageStats.getSuccessfulSubmissions());
        assertEquals(50.0, languageStats.getAcceptanceRate());
        assertEquals(2, difficultyStats.getTotalSubmissions());
        assertEquals(1, difficultyStats.getSuccessfulSubmissions());
        assertEquals(50.0, difficultyStats.getAcceptanceRate());
        assertEquals(2, history.getTotalElements());
        assertEquals(2, history.getContent().size());
        assertEquals("SUCCESS", acceptedHistory.getStatus());
        assertEquals(JudgeStatus.ACCEPTED, acceptedHistory.getJudgeStatus());
        assertTrue(acceptedHistory.getOutputCorrect());
        assertEquals("SUCCESS", wrongHistory.getStatus());
        assertEquals(JudgeStatus.WRONG_ANSWER, wrongHistory.getJudgeStatus());
        assertFalse(wrongHistory.getOutputCorrect());
        assertEquals("SUCCESS", detail.getStatus());
        assertEquals(JudgeStatus.ACCEPTED, detail.getJudgeStatus());
        assertTrue(detail.getOutputCorrect());
        assertEquals(2, detail.getTestCaseResults().size());
        assertEquals(JudgeStatus.WRONG_ANSWER,
                codeSubmissionService.getSubmissionById(wrong.getId()).getJudgeStatus());
        assertEquals(problem.getId(), detail.getProblemId());
    }

    private Problem saveProblemWithCases() {
        Problem problem = new Problem();
        problem.setTitle("Workflow integration problem");
        problem.setDescription("Isolated workflow test problem");
        problem.setDifficulty(Difficulty.EASY);
        problem = problemRepository.saveAndFlush(problem);

        saveProblemTestCase(problem, "public-value", false);
        saveProblemTestCase(problem, "hidden-value", true);
        problemTestCaseRepository.flush();
        return problem;
    }

    private void saveProblemTestCase(Problem problem, String value, boolean hidden) {
        ProblemTestCase testCase = new ProblemTestCase();
        testCase.setInput(value);
        testCase.setExpectedOutput(value);
        testCase.setHidden(hidden);
        testCase.setProblem(problem);
        problemTestCaseRepository.save(testCase);
    }

    private static CodeExecutionRequestDTO request(String sourceCode) {
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

    private void assertTestDatabase() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().endsWith("/codeengine_test"));
            assertFalse(connection.getMetaData().getURL().endsWith("/codeengine"));
        }
    }
}
