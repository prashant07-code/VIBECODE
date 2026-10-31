package com.prashant.codeengine.service;
import com.prashant.codeengine.dto.JudgeStatus;
import com.prashant.codeengine.dto.CodeExecutionRequestDTO;
import com.prashant.codeengine.dto.CodeExecutionResponseDTO;
import com.prashant.codeengine.dto.CodeExecutionResultDTO;
import com.prashant.codeengine.dto.DifficultyStatisticsDTO;
import com.prashant.codeengine.dto.LanguageStatisticsDTO;
import com.prashant.codeengine.dto.SubmissionHistoryResponseDTO;
import com.prashant.codeengine.dto.SubmissionListResponseDTO;
import com.prashant.codeengine.dto.SubmissionStatisticsDTO;
import com.prashant.codeengine.dto.TestCaseDTO;
import com.prashant.codeengine.dto.TestCaseResultDTO;
import com.prashant.codeengine.dto.ProblemTestCaseResponseDTO;
import com.prashant.codeengine.entity.CodeSubmission;
import com.prashant.codeengine.entity.Difficulty;
import com.prashant.codeengine.entity.Problem;
import com.prashant.codeengine.entity.TestCaseResult;
import com.prashant.codeengine.exception.SubmissionNotFoundException;
import com.prashant.codeengine.repository.CodeSubmissionRepository;
import com.prashant.codeengine.repository.TestCaseResultRepository;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class CodeSubmissionService {

    private final CodeSubmissionRepository repository;
    private final TestCaseResultRepository testCaseResultRepository;
    private final List<CodeExecutor> codeExecutors;
    private final ModelMapper modelMapper;
    private final OutputEvaluator outputEvaluator;
    private final ProblemService problemService;
    private final ProblemTestCaseService problemTestCaseService;

    public CodeSubmissionService(
            CodeSubmissionRepository repository,
            TestCaseResultRepository testCaseResultRepository,
            List<CodeExecutor> codeExecutors,
            ModelMapper modelMapper,
            OutputEvaluator outputEvaluator,
            ProblemService problemService,
            ProblemTestCaseService problemTestCaseService) {

        this.repository = repository;
        this.testCaseResultRepository = testCaseResultRepository;
        this.codeExecutors = codeExecutors;
        this.modelMapper = modelMapper;
        this.outputEvaluator = outputEvaluator;
        this.problemService = problemService;
        this.problemTestCaseService = problemTestCaseService;
    }

    private CodeExecutor getExecutor(String language) {
        return codeExecutors.stream()
                .filter(executor -> executor.supportsLanguage(language))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "The requested language is not supported."
                ));
    }

    // Execute Java code and save submission
    public CodeExecutionResponseDTO saveSubmission(
            CodeExecutionRequestDTO request) throws Exception {

        CodeExecutor executor =
                getExecutor(request.getLanguage());

        /*
         * Day 30:
         *
         * If problemId is provided and request does not contain
         * test cases, load the test cases from the selected problem.
         */
        if (request.getProblemId() != null
                && (request.getTestCases() == null
                || request.getTestCases().isEmpty())) {

            Problem problem =
                    problemService.getProblemEntityById(
                            request.getProblemId()
                    );

            List<ProblemTestCaseResponseDTO> problemTestCases =
                    problemTestCaseService.getTestCasesByProblemId(
                            problem.getId()
                    );

            List<TestCaseDTO> testCases =
                    new ArrayList<>();

            for (ProblemTestCaseResponseDTO problemTestCase
                    : problemTestCases) {

                TestCaseDTO testCase =
                        new TestCaseDTO();

                testCase.setInput(
                        problemTestCase.getInput()
                );

                testCase.setExpectedOutput(
                        problemTestCase.getExpectedOutput()
                );

                testCase.setHidden(
                        problemTestCase.isHidden()
                );

                testCases.add(testCase);
            }

            request.setTestCases(testCases);
        }

        /*
         * Day 28:
         * If multiple test cases are provided,
         * execute every test case independently.
         */
        if (request.getTestCases() != null
                && !request.getTestCases().isEmpty()) {

            return executeMultipleTestCases(
                    request,
                    executor
            );
        }

        /*
         * Backward-compatible single test case flow.
         */
        return executeSingleTestCase(
                request,
                executor
        );
    }

    // Day 28 + Day 30 - Execute multiple test cases independently
    private CodeExecutionResponseDTO executeMultipleTestCases(
            CodeExecutionRequestDTO request,
            CodeExecutor executor) throws Exception {

        List<TestCaseResultDTO> testCaseResults =
                new ArrayList<>();

        JudgeStatus overallJudgeStatus =
                JudgeStatus.ACCEPTED;

        String overallStatus = "SUCCESS";
        String overallOutput = "";
        String overallError = null;
        int overallExitCode = 0;

        /*
         * Execute every test case separately.
         */
        for (TestCaseDTO testCase : request.getTestCases()) {

            CodeExecutionResultDTO result =
                    executor.execute(
                            request.getSourceCode(),
                            testCase.getInput()
                    );

            TestCaseResultDTO testCaseResult =
                    new TestCaseResultDTO();

            testCaseResult.setInput(
                    testCase.getInput()
            );

            testCaseResult.setExpectedOutput(
                    testCase.getExpectedOutput()
            );

            /*
             * Protect response from very large output.
             */
            if ("OUTPUT_LIMIT_EXCEEDED".equalsIgnoreCase(
                    result.getStatus())) {

                testCaseResult.setActualOutput(
                        "[OUTPUT LIMIT EXCEEDED]"
                );

            } else {

                testCaseResult.setActualOutput(
                        result.getOutput()
                );
            }

            /*
             * Compile error, runtime error,
             * timeout or output-limit error.
             */
            if (!"SUCCESS".equalsIgnoreCase(
                    result.getStatus())) {

                overallStatus =
                        result.getStatus();

                overallError =
                        result.getError();

                overallExitCode =
                        result.getExitCode();

                testCaseResult.setOutputCorrect(
                        null
                );

                if ("TIME_LIMIT_EXCEEDED".equalsIgnoreCase(
                        result.getStatus())) {

                    testCaseResult.setJudgeStatus(
                            JudgeStatus.TIME_LIMIT_EXCEEDED
                    );

                    overallJudgeStatus =
                            JudgeStatus.TIME_LIMIT_EXCEEDED;

                } else if ("OUTPUT_LIMIT_EXCEEDED".equalsIgnoreCase(
                        result.getStatus())) {

                    testCaseResult.setJudgeStatus(
                            JudgeStatus.OUTPUT_LIMIT_EXCEEDED
                    );

                    overallJudgeStatus =
                            JudgeStatus.OUTPUT_LIMIT_EXCEEDED;

                } else {

                    testCaseResult.setJudgeStatus(
                            null
                    );

                    overallJudgeStatus =
                            null;
                }

                testCaseResults.add(
                        testCaseResult
                );

                break;
            }

            /*
             * Compare current test case output
             * with current test case expected output.
             */
            Boolean outputCorrect =
                    outputEvaluator.isOutputCorrect(
                            result.getOutput(),
                            testCase.getExpectedOutput()
                    );

            testCaseResult.setOutputCorrect(
                    outputCorrect
            );

            if (outputCorrect) {

                testCaseResult.setJudgeStatus(
                        JudgeStatus.ACCEPTED
                );

            } else {

                testCaseResult.setJudgeStatus(
                        JudgeStatus.WRONG_ANSWER
                );

                overallJudgeStatus =
                        JudgeStatus.WRONG_ANSWER;
            }

            testCaseResults.add(
                    testCaseResult
            );

            overallOutput =
                    result.getOutput();

            overallError =
                    result.getError();

            overallExitCode =
                    result.getExitCode();
        }

        /*
         * Convert request DTO to entity.
         */
        CodeSubmission submission =
                mapToEntity(request);

        /*
         * Protect database from huge output.
         */
        String safeOutput =
                "OUTPUT_LIMIT_EXCEEDED".equalsIgnoreCase(
                        overallStatus
                )
                        ? "[OUTPUT LIMIT EXCEEDED]"
                        : overallOutput;

        /*
         * Create formatted output for database.
         */
        String formattedOutput =
                "Status: " + overallStatus
                        + "\nOutput:\n"
                        + safeOutput
                        + "\nError:\n"
                        + (overallError == null
                        ? "null\n"
                        : overallError + "\n")
                        + "Exit Code: "
                        + overallExitCode;

        submission.setOutput(
                formattedOutput
        );

        submission.setStatus(
                overallStatus
        );

        /*
         * Save submission.
         */
        CodeSubmission savedSubmission =
                repository.save(
                        submission
                );

        /*
         * Save individual test case results.
         */
        for (TestCaseResultDTO testCaseResultDTO
                : testCaseResults) {

            TestCaseResult testCaseResult =
                    new TestCaseResult();

            testCaseResult.setInput(
                    testCaseResultDTO.getInput()
            );

            testCaseResult.setExpectedOutput(
                    testCaseResultDTO.getExpectedOutput()
            );

            testCaseResult.setActualOutput(
                    testCaseResultDTO.getActualOutput()
            );

            testCaseResult.setOutputCorrect(
                    testCaseResultDTO.getOutputCorrect()
            );

            testCaseResult.setJudgeStatus(
                    testCaseResultDTO.getJudgeStatus() == null
                            ? null
                            : testCaseResultDTO
                            .getJudgeStatus()
                            .name()
            );

            testCaseResult.setCodeSubmission(
                    savedSubmission
            );

            testCaseResultRepository.save(
                    testCaseResult
            );
        }

        /*
         * Convert entity to response DTO.
         */
        CodeExecutionResponseDTO response =
                mapToResponse(
                        savedSubmission
                );

        response.setStatus(
                overallStatus
        );

        response.setOutput(
                safeOutput
        );

        response.setError(
                overallError
        );

        response.setExitCode(
                overallExitCode
        );

        response.setTestCaseResults(
                testCaseResults
        );

        if (overallJudgeStatus ==
                JudgeStatus.ACCEPTED) {

            response.setOutputCorrect(
                    true
            );

        } else if (overallJudgeStatus ==
                JudgeStatus.WRONG_ANSWER) {

            response.setOutputCorrect(
                    false
            );

        } else {

            response.setOutputCorrect(
                    null
            );
        }

        response.setJudgeStatus(
                overallJudgeStatus
        );

        return response;
    }

    // Day 27 - Execute single test case
    private CodeExecutionResponseDTO executeSingleTestCase(
            CodeExecutionRequestDTO request,
            CodeExecutor executor) throws Exception {

        CodeExecutionResultDTO result =
                executor.execute(
                        request.getSourceCode(),
                        request.getInput()
                );

        Boolean outputCorrect = null;

        if ("SUCCESS".equalsIgnoreCase(
                result.getStatus())
                && request.getExpectedOutput() != null) {

            outputCorrect =
                    outputEvaluator.isOutputCorrect(
                            result.getOutput(),
                            request.getExpectedOutput()
                    );
        }

        JudgeStatus judgeStatus = null;

        if ("SUCCESS".equalsIgnoreCase(
                result.getStatus())) {

            if (request.getExpectedOutput() != null) {

                judgeStatus =
                        outputCorrect
                                ? JudgeStatus.ACCEPTED
                                : JudgeStatus.WRONG_ANSWER;
            }

        } else if ("TIME_LIMIT_EXCEEDED".equalsIgnoreCase(
                result.getStatus())) {

            judgeStatus =
                    JudgeStatus.TIME_LIMIT_EXCEEDED;

        } else if ("OUTPUT_LIMIT_EXCEEDED".equalsIgnoreCase(
                result.getStatus())) {

            judgeStatus =
                    JudgeStatus.OUTPUT_LIMIT_EXCEEDED;
        }

        /*
         * Protect response/database from huge output.
         */
        String safeOutput;

        if ("OUTPUT_LIMIT_EXCEEDED".equalsIgnoreCase(
                result.getStatus())) {

            safeOutput =
                    "[OUTPUT LIMIT EXCEEDED]";

        } else {

            safeOutput =
                    result.getOutput();
        }

        CodeSubmission submission =
                mapToEntity(request);

        /*
         * Create formatted output for database.
         */
        String formattedOutput =
                "Status: " + result.getStatus()
                        + "\nOutput:\n"
                        + safeOutput
                        + "\nError:\n"
                        + (result.getError() == null
                        ? "null\n"
                        : result.getError() + "\n")
                        + "Exit Code: "
                        + result.getExitCode();

        submission.setOutput(
                formattedOutput
        );

        submission.setStatus(
                result.getStatus()
        );

        /*
         * Save submission.
         */
        CodeSubmission savedSubmission =
                repository.save(
                        submission
                );

        if (request.getExpectedOutput() != null) {

            TestCaseResult testCaseResult =
                    new TestCaseResult();

            testCaseResult.setInput(
                    request.getInput()
            );

            testCaseResult.setExpectedOutput(
                    request.getExpectedOutput()
            );

            testCaseResult.setActualOutput(
                    safeOutput
            );

            testCaseResult.setOutputCorrect(
                    outputCorrect
            );

            testCaseResult.setJudgeStatus(
                    judgeStatus == null
                            ? null
                            : judgeStatus.name()
            );

            testCaseResult.setCodeSubmission(
                    savedSubmission
            );

            testCaseResultRepository.save(
                    testCaseResult
            );
        }

        /*
         * Convert saved entity to response DTO.
         */
        CodeExecutionResponseDTO response =
                mapToResponse(
                        savedSubmission
                );

        response.setStatus(
                result.getStatus()
        );

        response.setOutput(
                safeOutput
        );

        response.setError(
                result.getError()
        );

        response.setExitCode(
                result.getExitCode()
        );

        response.setOutputCorrect(
                outputCorrect
        );

        response.setJudgeStatus(
                judgeStatus
        );

        return response;
    }

    // Get all code submissions
    public List<CodeExecutionResponseDTO> getAllSubmissions() {

        List<CodeSubmission> submissions =
                repository.findAll();

        return submissions.stream()
                .map(this::mapToResponse)
                .toList();
    }

    // Get all submissions with pagination + language filter + search
    public Page<CodeExecutionResponseDTO> getAllSubmissions(
            Pageable pageable,
            String language,
            String search) {

        if (language != null && language.isBlank()) {
            language = null;
        }

        if (search != null && search.isBlank()) {
            search = null;
        }

        Page<CodeSubmission> submissions =
                repository.findSubmissionsWithFilters(
                        language,
                        search,
                        pageable
                );

        return submissions.map(
                this::mapToResponse
        );
    }

    /*
     * Day 55:
     * Lightweight submission list.
     *
     * Source code, output, error,
     * test case details etc. are excluded.
     */
    public Page<SubmissionListResponseDTO> getSubmissionList(
            Pageable pageable,
            String language,
            String search) {

        if (language != null && language.isBlank()) {
            language = null;
        }

        if (search != null && search.isBlank()) {
            search = null;
        }

        Page<CodeSubmission> submissions =
                repository.findSubmissionsWithFilters(
                        language,
                        search,
                        pageable
                );

        return submissions.map(
                this::mapToSubmissionListResponse
        );
    }

    /*
     * Submission History.
     *
     * Returns lightweight history information.
     */
    public Page<SubmissionHistoryResponseDTO> getSubmissionHistory(
            Pageable pageable,
            String language,
            String search) {

        if (language != null && language.isBlank()) {
            language = null;
        }

        if (search != null && search.isBlank()) {
            search = null;
        }

        Page<CodeSubmission> submissions =
                repository.findSubmissionsWithFilters(
                        language,
                        search,
                        pageable
                );

        return submissions.map(
                this::mapToHistoryResponse
        );
    }

    // Get single submission by ID
    public CodeExecutionResponseDTO getSubmissionById(
            Long id) {

        CodeSubmission submission =
                repository.findById(id)
                        .orElseThrow(() ->
                                new SubmissionNotFoundException(
                                        "Submission not found with id: "
                                                + id
                                )
                        );

        return mapToResponse(
                submission
        );
    }

    // Get submission statistics (global)
    public SubmissionStatisticsDTO getSubmissionStatistics() {

        long totalSubmissions =
                repository.count();

        long successfulSubmissions =
                repository.countAcceptedSubmissions(
                        null,
                        null,
                        null
                );

        long compileErrors =
                repository.countByStatusIgnoreCase(
                        "COMPILE_ERROR"
                );

        long runtimeErrors =
                repository.countByStatusIgnoreCase(
                        "RUNTIME_ERROR"
                );

        long timeLimitExceeded =
                repository.countByStatusIgnoreCase(
                        "TIME_LIMIT_EXCEEDED"
                );

        long outputLimitExceeded =
                repository.countByStatusIgnoreCase(
                        "OUTPUT_LIMIT_EXCEEDED"
                );

        SubmissionStatisticsDTO statistics =
                new SubmissionStatisticsDTO();

        statistics.setTotalSubmissions(
                totalSubmissions
        );

        statistics.setSuccessfulSubmissions(
                successfulSubmissions
        );

        statistics.setCompileErrors(
                compileErrors
        );

        statistics.setRuntimeErrors(
                runtimeErrors
        );

        statistics.setTimeLimitExceeded(
                timeLimitExceeded
        );

        statistics.setOutputLimitExceeded(
                outputLimitExceeded
        );

        /*
         * Day 57:
         * Acceptance rate percentage.
         * Avoid divide-by-zero when there are no submissions yet.
         */
        double acceptanceRate =
                totalSubmissions == 0
                        ? 0.0
                        : (successfulSubmissions * 100.0) / totalSubmissions;

        statistics.setAcceptanceRate(
                acceptanceRate
        );

        return statistics;
    }

    // Get submission statistics for a specific problem (Day 56)
    public SubmissionStatisticsDTO getSubmissionStatistics(
            Long problemId) {

        Problem problem =
                problemService.getProblemEntityById(
                        problemId
                );

        long totalSubmissions =
                repository.countByProblemId(
                        problemId
                );

        long successfulSubmissions =
                repository.countAcceptedSubmissions(
                        problemId,
                        null,
                        null
                );

        long compileErrors =
                repository.countByStatusIgnoreCaseAndProblemId(
                        "COMPILE_ERROR",
                        problemId
                );

        long runtimeErrors =
                repository.countByStatusIgnoreCaseAndProblemId(
                        "RUNTIME_ERROR",
                        problemId
                );

        long timeLimitExceeded =
                repository.countByStatusIgnoreCaseAndProblemId(
                        "TIME_LIMIT_EXCEEDED",
                        problemId
                );

        long outputLimitExceeded =
                repository.countByStatusIgnoreCaseAndProblemId(
                        "OUTPUT_LIMIT_EXCEEDED",
                        problemId
                );

        SubmissionStatisticsDTO statistics =
                new SubmissionStatisticsDTO();

        statistics.setProblemId(
                problem.getId()
        );

        statistics.setProblemTitle(
                problem.getTitle()
        );

        statistics.setTotalSubmissions(
                totalSubmissions
        );

        statistics.setSuccessfulSubmissions(
                successfulSubmissions
        );

        statistics.setCompileErrors(
                compileErrors
        );

        statistics.setRuntimeErrors(
                runtimeErrors
        );

        statistics.setTimeLimitExceeded(
                timeLimitExceeded
        );

        statistics.setOutputLimitExceeded(
                outputLimitExceeded
        );

        /*
         * Day 57:
         * Acceptance rate percentage.
         * Avoid divide-by-zero when there are no submissions yet.
         */
        double acceptanceRate =
                totalSubmissions == 0
                        ? 0.0
                        : (successfulSubmissions * 100.0) / totalSubmissions;

        statistics.setAcceptanceRate(
                acceptanceRate
        );

        return statistics;
    }

    // Day 58: Language-wise statistics breakdown
    public List<LanguageStatisticsDTO> getLanguageStatistics() {

        List<String> languages =
                repository.findDistinctLanguages();

        List<LanguageStatisticsDTO> languageStatisticsList =
                new ArrayList<>();

        for (String language : languages) {

            long totalSubmissions =
                    repository.countByLanguageIgnoreCase(
                            language
                    );

            long successfulSubmissions =
                    repository.countAcceptedSubmissions(
                            null,
                            language,
                            null
                    );

            long compileErrors =
                    repository.countByStatusIgnoreCaseAndLanguageIgnoreCase(
                            "COMPILE_ERROR",
                            language
                    );

            long runtimeErrors =
                    repository.countByStatusIgnoreCaseAndLanguageIgnoreCase(
                            "RUNTIME_ERROR",
                            language
                    );

            long timeLimitExceeded =
                    repository.countByStatusIgnoreCaseAndLanguageIgnoreCase(
                            "TIME_LIMIT_EXCEEDED",
                            language
                    );

            long outputLimitExceeded =
                    repository.countByStatusIgnoreCaseAndLanguageIgnoreCase(
                            "OUTPUT_LIMIT_EXCEEDED",
                            language
                    );

            double acceptanceRate =
                    totalSubmissions == 0
                            ? 0.0
                            : (successfulSubmissions * 100.0) / totalSubmissions;

            LanguageStatisticsDTO languageStatistics =
                    new LanguageStatisticsDTO();

            languageStatistics.setLanguage(
                    language
            );

            languageStatistics.setTotalSubmissions(
                    totalSubmissions
            );

            languageStatistics.setSuccessfulSubmissions(
                    successfulSubmissions
            );

            languageStatistics.setCompileErrors(
                    compileErrors
            );

            languageStatistics.setRuntimeErrors(
                    runtimeErrors
            );

            languageStatistics.setTimeLimitExceeded(
                    timeLimitExceeded
            );

            languageStatistics.setOutputLimitExceeded(
                    outputLimitExceeded
            );

            languageStatistics.setAcceptanceRate(
                    acceptanceRate
            );

            languageStatisticsList.add(
                    languageStatistics
            );
        }

        return languageStatisticsList;
    }

    // Day 59: Difficulty-wise statistics breakdown
    public List<DifficultyStatisticsDTO> getDifficultyStatistics() {

        List<DifficultyStatisticsDTO> difficultyStatisticsList =
                new ArrayList<>();

        for (Difficulty difficulty : Difficulty.values()) {

            String difficultyName =
                    difficulty.name();

            long totalSubmissions =
                    repository.countByProblemDifficulty(
                            difficultyName
                    );

            long successfulSubmissions =
                    repository.countAcceptedSubmissions(
                            null,
                            null,
                            difficulty
                    );

            long compileErrors =
                    repository.countByStatusAndProblemDifficulty(
                            "COMPILE_ERROR",
                            difficultyName
                    );

            long runtimeErrors =
                    repository.countByStatusAndProblemDifficulty(
                            "RUNTIME_ERROR",
                            difficultyName
                    );

            long timeLimitExceeded =
                    repository.countByStatusAndProblemDifficulty(
                            "TIME_LIMIT_EXCEEDED",
                            difficultyName
                    );

            long outputLimitExceeded =
                    repository.countByStatusAndProblemDifficulty(
                            "OUTPUT_LIMIT_EXCEEDED",
                            difficultyName
                    );

            double acceptanceRate =
                    totalSubmissions == 0
                            ? 0.0
                            : (successfulSubmissions * 100.0) / totalSubmissions;

            DifficultyStatisticsDTO difficultyStatistics =
                    new DifficultyStatisticsDTO();

            difficultyStatistics.setDifficulty(
                    difficultyName
            );

            difficultyStatistics.setTotalSubmissions(
                    totalSubmissions
            );

            difficultyStatistics.setSuccessfulSubmissions(
                    successfulSubmissions
            );

            difficultyStatistics.setCompileErrors(
                    compileErrors
            );

            difficultyStatistics.setRuntimeErrors(
                    runtimeErrors
            );

            difficultyStatistics.setTimeLimitExceeded(
                    timeLimitExceeded
            );

            difficultyStatistics.setOutputLimitExceeded(
                    outputLimitExceeded
            );

            difficultyStatistics.setAcceptanceRate(
                    acceptanceRate
            );

            difficultyStatisticsList.add(
                    difficultyStatistics
            );
        }

        return difficultyStatisticsList;
    }

    /*
     * Convert request DTO to entity.
     */
    private CodeSubmission mapToEntity(
            CodeExecutionRequestDTO request) {

        CodeSubmission submission =
                modelMapper.map(
                        request,
                        CodeSubmission.class
                );

        submission.setId(null);

        if (request.getProblemId() != null) {

            Problem problem =
                    problemService.getProblemEntityById(
                            request.getProblemId()
                    );

            submission.setProblem(
                    problem
            );
        }

        return submission;
    }

    /*
     * Convert submission entity to complete response DTO.
     */
    private CodeExecutionResponseDTO mapToResponse(
            CodeSubmission submission) {

        CodeExecutionResponseDTO response =
                modelMapper.map(
                        submission,
                        CodeExecutionResponseDTO.class
                );

        /*
         * CodeSubmission does not store exitCode
         * as a separate database column.
         */
        response.setExitCode(
                extractExitCode(
                        submission.getOutput()
                )
        );

        String status =
                submission.getStatus();

        /*
         * TIME_LIMIT_EXCEEDED
         */
        if ("TIME_LIMIT_EXCEEDED".equalsIgnoreCase(
                status)) {

            response.setJudgeStatus(
                    JudgeStatus.TIME_LIMIT_EXCEEDED
            );

            response.setOutputCorrect(
                    null
            );

            /*
             * OUTPUT_LIMIT_EXCEEDED
             */
        } else if ("OUTPUT_LIMIT_EXCEEDED".equalsIgnoreCase(
                status)) {

            response.setJudgeStatus(
                    JudgeStatus.OUTPUT_LIMIT_EXCEEDED
            );

            response.setOutputCorrect(
                    null
            );

            /*
             * SUCCESS
             */
        } else if ("SUCCESS".equalsIgnoreCase(
                status)) {

            JudgeStatus judgeStatus =
                    determineJudgeStatus(
                            submission
                    );

            response.setJudgeStatus(
                    judgeStatus
            );

            if (judgeStatus ==
                    JudgeStatus.ACCEPTED) {

                response.setOutputCorrect(
                        true
                );

            } else if (judgeStatus ==
                    JudgeStatus.WRONG_ANSWER) {

                response.setOutputCorrect(
                        false
                );

            } else {

                response.setOutputCorrect(
                        null
                );
            }

            /*
             * COMPILE_ERROR / RUNTIME_ERROR / others
             */
        } else {

            response.setJudgeStatus(
                    null
            );

            response.setOutputCorrect(
                    null
            );
        }

        return response;
    }

    /*
     * Convert submission entity to lightweight list response.
     */
    private SubmissionListResponseDTO mapToSubmissionListResponse(
            CodeSubmission submission) {

        SubmissionListResponseDTO response =
                new SubmissionListResponseDTO();

        response.setId(
                submission.getId()
        );

        response.setLanguage(
                submission.getLanguage()
        );

        response.setStatus(
                submission.getStatus()
        );

        response.setCreatedAt(
                submission.getCreatedAt()
        );

        if (submission.getProblem() != null) {

            response.setProblemId(
                    submission.getProblem().getId()
            );

            response.setProblemTitle(
                    submission.getProblem().getTitle()
            );
        }

        String status =
                submission.getStatus();

        if ("SUCCESS".equalsIgnoreCase(
                status)) {

            JudgeStatus judgeStatus =
                    determineJudgeStatus(
                            submission
                    );

            response.setJudgeStatus(
                    judgeStatus
            );

            if (judgeStatus ==
                    JudgeStatus.ACCEPTED) {

                response.setOutputCorrect(
                        true
                );

            } else if (judgeStatus ==
                    JudgeStatus.WRONG_ANSWER) {

                response.setOutputCorrect(
                        false
                );

            } else {

                response.setOutputCorrect(
                        null
                );
            }

        } else if ("TIME_LIMIT_EXCEEDED".equalsIgnoreCase(
                status)) {

            response.setJudgeStatus(
                    JudgeStatus.TIME_LIMIT_EXCEEDED
            );

            response.setOutputCorrect(
                    null
            );

        } else if ("OUTPUT_LIMIT_EXCEEDED".equalsIgnoreCase(
                status)) {

            response.setJudgeStatus(
                    JudgeStatus.OUTPUT_LIMIT_EXCEEDED
            );

            response.setOutputCorrect(
                    null
            );

        } else {

            response.setJudgeStatus(
                    null
            );

            response.setOutputCorrect(
                    null
            );
        }

        return response;
    }

    /*
     * Convert submission entity to lightweight
     * submission history response.
     */
    private SubmissionHistoryResponseDTO mapToHistoryResponse(
            CodeSubmission submission) {

        SubmissionHistoryResponseDTO response =
                new SubmissionHistoryResponseDTO();

        response.setId(
                submission.getId()
        );

        response.setLanguage(
                submission.getLanguage()
        );

        response.setStatus(
                submission.getStatus()
        );

        String status =
                submission.getStatus();

        if ("SUCCESS".equalsIgnoreCase(
                status)) {

            JudgeStatus judgeStatus =
                    determineJudgeStatus(
                            submission
                    );

            response.setJudgeStatus(
                    judgeStatus
            );

            if (judgeStatus ==
                    JudgeStatus.ACCEPTED) {

                response.setOutputCorrect(
                        true
                );

            } else if (judgeStatus ==
                    JudgeStatus.WRONG_ANSWER) {

                response.setOutputCorrect(
                        false
                );

            } else {

                response.setOutputCorrect(
                        null
                );
            }

        } else if ("TIME_LIMIT_EXCEEDED".equalsIgnoreCase(
                status)) {

            response.setJudgeStatus(
                    JudgeStatus.TIME_LIMIT_EXCEEDED
            );

            response.setOutputCorrect(
                    null
            );

        } else if ("OUTPUT_LIMIT_EXCEEDED".equalsIgnoreCase(
                status)) {

            response.setJudgeStatus(
                    JudgeStatus.OUTPUT_LIMIT_EXCEEDED
            );

            response.setOutputCorrect(
                    null
            );

        } else {

            response.setJudgeStatus(
                    null
            );

            response.setOutputCorrect(
                    null
            );
        }

        /*
         * Exit code is stored inside formatted output.
         */
        response.setExitCode(
                extractExitCode(
                        submission.getOutput()
                )
        );

        /*
         * Problem information.
         */
        if (submission.getProblem() != null) {

            response.setProblemId(
                    submission.getProblem().getId()
            );

            response.setProblemTitle(
                    submission.getProblem().getTitle()
            );
        }

        response.setCreatedAt(
                submission.getCreatedAt()
        );

        return response;
    }

    /*
     * Determine overall judge status from TestCaseResult records.
     *
     * Important:
     * Null/unknown judgeStatus records are ignored.
     *
     * Priority:
     * 1. OUTPUT_LIMIT_EXCEEDED
     * 2. TIME_LIMIT_EXCEEDED
     * 3. WRONG_ANSWER
     * 4. ACCEPTED
     *
     * This prevents old/incomplete TestCaseResult records
     * from incorrectly changing the current submission status.
     */
    private JudgeStatus determineJudgeStatus(
            CodeSubmission submission) {

        List<TestCaseResult> testCaseResults =
                submission.getTestCaseResults();

        if (testCaseResults == null
                || testCaseResults.isEmpty()) {

            return null;
        }

        boolean hasAcceptedResult = false;
        boolean hasWrongAnswer = false;
        boolean hasTimeLimitExceeded = false;
        boolean hasOutputLimitExceeded = false;

        for (TestCaseResult testCaseResult
                : testCaseResults) {

            if (testCaseResult == null) {
                continue;
            }

            String judgeStatus =
                    testCaseResult.getJudgeStatus();

            /*
             * Ignore null/blank historical records.
             */
            if (judgeStatus == null
                    || judgeStatus.isBlank()) {

                continue;
            }

            if (JudgeStatus.OUTPUT_LIMIT_EXCEEDED.name()
                    .equalsIgnoreCase(judgeStatus)) {

                hasOutputLimitExceeded = true;

            } else if (JudgeStatus.TIME_LIMIT_EXCEEDED.name()
                    .equalsIgnoreCase(judgeStatus)) {

                hasTimeLimitExceeded = true;

            } else if (JudgeStatus.WRONG_ANSWER.name()
                    .equalsIgnoreCase(judgeStatus)) {

                hasWrongAnswer = true;

            } else if (JudgeStatus.ACCEPTED.name()
                    .equalsIgnoreCase(judgeStatus)) {

                hasAcceptedResult = true;
            }
        }

        /*
         * Any output-limit failure takes priority.
         */
        if (hasOutputLimitExceeded) {

            return JudgeStatus.OUTPUT_LIMIT_EXCEEDED;
        }

        /*
         * Any timeout takes priority over wrong answer.
         */
        if (hasTimeLimitExceeded) {

            return JudgeStatus.TIME_LIMIT_EXCEEDED;
        }

        /*
         * Any wrong answer means the submission
         * is not fully accepted.
         */
        if (hasWrongAnswer) {

            return JudgeStatus.WRONG_ANSWER;
        }

        /*
         * If at least one valid test case was accepted
         * and there were no failures, submission is accepted.
         */
        if (hasAcceptedResult) {

            return JudgeStatus.ACCEPTED;
        }

        return null;
    }

    /*
     * Extract exit code from formatted database output.
     */
    private int extractExitCode(
            String formattedOutput) {

        if (formattedOutput == null
                || formattedOutput.isBlank()) {

            return 0;
        }

        String marker =
                "Exit Code:";

        int markerIndex =
                formattedOutput.lastIndexOf(
                        marker
                );

        if (markerIndex == -1) {

            return 0;
        }

        String exitCodeText =
                formattedOutput
                        .substring(
                                markerIndex
                                        + marker.length()
                        )
                        .trim();

        try {

            return Integer.parseInt(
                    exitCodeText
            );

        } catch (NumberFormatException e) {

            return 0;
        }
    }
}
