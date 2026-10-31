package com.prashant.codeengine.repository;

import com.prashant.codeengine.dto.DifficultyStatisticsDTO;
import com.prashant.codeengine.dto.JudgeStatus;
import com.prashant.codeengine.dto.LanguageStatisticsDTO;
import com.prashant.codeengine.dto.SubmissionStatisticsDTO;
import com.prashant.codeengine.entity.CodeSubmission;
import com.prashant.codeengine.entity.Difficulty;
import com.prashant.codeengine.entity.Problem;
import com.prashant.codeengine.entity.TestCaseResult;
import com.prashant.codeengine.service.CodeSubmissionService;
import com.prashant.codeengine.service.OutputEvaluator;
import com.prashant.codeengine.service.ProblemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.modelmapper.ModelMapper;

import jakarta.persistence.EntityManager;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CodeSubmissionStatisticsRepositoryTests {

    @Autowired
    private CodeSubmissionRepository codeSubmissionRepository;

    @Autowired
    private TestCaseResultRepository testCaseResultRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private DataSource dataSource;

    @Test
    void countsAcceptedSubmissionsByPersistedVerdictsAndAllStatisticsScopes()
            throws SQLException {
        assertTestDatabase();
        Fixture fixture = persistStatisticsFixture();

        assertEquals(8L, codeSubmissionRepository.count());
        assertEquals(1L, codeSubmissionRepository.countAcceptedSubmissions(
                null, null, null));
        assertEquals(1L, codeSubmissionRepository.countAcceptedSubmissions(
                fixture.easyProblem().getId(), null, null));
        assertEquals(0L, codeSubmissionRepository.countAcceptedSubmissions(
                fixture.mediumProblem().getId(), null, null));
        assertEquals(1L, codeSubmissionRepository.countAcceptedSubmissions(
                null, "java", null));
        assertEquals(0L, codeSubmissionRepository.countAcceptedSubmissions(
                null, "python", null));
        assertEquals(1L, codeSubmissionRepository.countAcceptedSubmissions(
                null, null, Difficulty.EASY));
        assertEquals(0L, codeSubmissionRepository.countAcceptedSubmissions(
                null, null, Difficulty.MEDIUM));

        CodeSubmissionService service = statisticsService();
        SubmissionStatisticsDTO global = service.getSubmissionStatistics();
        SubmissionStatisticsDTO problem =
                service.getSubmissionStatistics(fixture.easyProblem().getId());

        assertEquals(8L, global.getTotalSubmissions());
        assertEquals(1L, global.getSuccessfulSubmissions());
        assertEquals(1L, global.getCompileErrors());
        assertEquals(1L, global.getRuntimeErrors());
        assertEquals(1L, global.getTimeLimitExceeded());
        assertEquals(1L, global.getOutputLimitExceeded());
        assertEquals(12.5, global.getAcceptanceRate());

        assertEquals(5L, problem.getTotalSubmissions());
        assertEquals(1L, problem.getSuccessfulSubmissions());
        assertEquals(1L, problem.getCompileErrors());
        assertEquals(0L, problem.getRuntimeErrors());
        assertEquals(0L, problem.getTimeLimitExceeded());
        assertEquals(0L, problem.getOutputLimitExceeded());
        assertEquals(20.0, problem.getAcceptanceRate());

        Map<String, LanguageStatisticsDTO> languageStatistics =
                service.getLanguageStatistics().stream()
                        .collect(Collectors.toMap(
                                LanguageStatisticsDTO::getLanguage,
                                Function.identity()
                        ));
        assertEquals(5L, languageStatistics.get("java").getTotalSubmissions());
        assertEquals(1L, languageStatistics.get("java").getSuccessfulSubmissions());
        assertEquals(20.0, languageStatistics.get("java").getAcceptanceRate());
        assertEquals(3L, languageStatistics.get("python").getTotalSubmissions());
        assertEquals(0L, languageStatistics.get("python").getSuccessfulSubmissions());
        assertEquals(0.0, languageStatistics.get("python").getAcceptanceRate());

        Map<String, DifficultyStatisticsDTO> difficultyStatistics =
                service.getDifficultyStatistics().stream()
                        .collect(Collectors.toMap(
                                DifficultyStatisticsDTO::getDifficulty,
                                Function.identity()
                        ));
        assertEquals(5L, difficultyStatistics.get("EASY").getTotalSubmissions());
        assertEquals(1L, difficultyStatistics.get("EASY").getSuccessfulSubmissions());
        assertEquals(20.0, difficultyStatistics.get("EASY").getAcceptanceRate());
        assertEquals(3L, difficultyStatistics.get("MEDIUM").getTotalSubmissions());
        assertEquals(0L, difficultyStatistics.get("MEDIUM").getSuccessfulSubmissions());
        assertEquals(0.0, difficultyStatistics.get("MEDIUM").getAcceptanceRate());
        assertEquals(0L, difficultyStatistics.get("HARD").getTotalSubmissions());
        assertEquals(0.0, difficultyStatistics.get("HARD").getAcceptanceRate());
    }

    @Test
    void persistsTestCaseResultsWithTheirSubmissionRelationship()
            throws SQLException {
        assertTestDatabase();
        persistStatisticsFixture();
        entityManager.flush();
        entityManager.clear();

        List<TestCaseResult> savedResults = testCaseResultRepository.findAll();

        assertEquals(7, savedResults.size());
        for (TestCaseResult result : savedResults) {
            assertNotNull(result.getId());
            assertNotNull(result.getCodeSubmission());
            CodeSubmission submission = codeSubmissionRepository
                    .findById(result.getCodeSubmission().getId())
                    .orElseThrow();
            assertEquals(submission.getId(), result.getCodeSubmission().getId());
        }
        assertTrue(savedResults.stream().anyMatch(result ->
                JudgeStatus.ACCEPTED.name().equals(result.getJudgeStatus())));
        assertTrue(savedResults.stream().anyMatch(result ->
                JudgeStatus.WRONG_ANSWER.name().equals(result.getJudgeStatus())));
    }

    private Fixture persistStatisticsFixture() {
        Problem easyProblem = saveProblem("Easy statistics", Difficulty.EASY);
        Problem mediumProblem = saveProblem("Medium statistics", Difficulty.MEDIUM);

        saveSubmission("java", "SUCCESS", easyProblem,
                JudgeStatus.ACCEPTED, JudgeStatus.ACCEPTED);
        saveSubmission("python", "SUCCESS", easyProblem);
        saveSubmission("java", "SUCCESS", easyProblem,
                JudgeStatus.WRONG_ANSWER);
        saveSubmission("java", "SUCCESS", easyProblem,
                JudgeStatus.ACCEPTED, JudgeStatus.WRONG_ANSWER);
        saveSubmission("java", "COMPILE_ERROR", easyProblem);
        saveSubmission("python", "RUNTIME_ERROR", mediumProblem);
        saveSubmission("python", "TIME_LIMIT_EXCEEDED", mediumProblem,
                JudgeStatus.TIME_LIMIT_EXCEEDED);
        saveSubmission("java", "OUTPUT_LIMIT_EXCEEDED", mediumProblem,
                JudgeStatus.OUTPUT_LIMIT_EXCEEDED);

        entityManager.flush();
        return new Fixture(easyProblem, mediumProblem);
    }

    private Problem saveProblem(String title, Difficulty difficulty) {
        Problem problem = new Problem();
        problem.setTitle(title);
        problem.setDescription("Statistics repository test fixture");
        problem.setDifficulty(difficulty);
        return problemRepository.saveAndFlush(problem);
    }

    private void saveSubmission(
            String language,
            String status,
            Problem problem,
            JudgeStatus... verdicts) {
        CodeSubmission submission = new CodeSubmission();
        submission.setLanguage(language);
        submission.setStatus(status);
        submission.setProblem(problem);
        submission.setTestCaseResults(new ArrayList<>());
        CodeSubmission savedSubmission =
                codeSubmissionRepository.saveAndFlush(submission);

        for (JudgeStatus verdict : verdicts) {
            TestCaseResult result = new TestCaseResult();
            result.setInput("test-input");
            result.setExpectedOutput("expected");
            result.setActualOutput("actual");
            result.setOutputCorrect(verdict == JudgeStatus.ACCEPTED);
            result.setJudgeStatus(verdict.name());
            result.setCodeSubmission(savedSubmission);
            testCaseResultRepository.save(result);
        }
    }

    private CodeSubmissionService statisticsService() {
        return new CodeSubmissionService(
                codeSubmissionRepository,
                testCaseResultRepository,
                List.of(),
                new ModelMapper(),
                new OutputEvaluator(),
                new ProblemService(problemRepository),
                null
        );
    }

    private void assertTestDatabase() throws SQLException {
        try (var connection = dataSource.getConnection()) {
            assertEquals(
                    "jdbc:mysql://localhost:3306/codeengine_test",
                    connection.getMetaData().getURL()
            );
        }
    }

    private record Fixture(Problem easyProblem, Problem mediumProblem) {
    }
}
