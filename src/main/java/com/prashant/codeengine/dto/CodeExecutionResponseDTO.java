package com.prashant.codeengine.dto;

import java.time.LocalDateTime;
import java.util.List;

public class CodeExecutionResponseDTO {

    private Long id;

    private LocalDateTime createdAt;

    private Long problemId;

    private String problemTitle;

    private String language;

    private String sourceCode;

    private String input;

    private String status;

    private String output;

    private String error;

    private int exitCode;

    // Judge information
    private Boolean outputCorrect;

    private JudgeStatus judgeStatus;

    // Multiple test case results
    private List<TestCaseResultDTO> testCaseResults;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getProblemId() {
        return problemId;
    }

    public void setProblemId(Long problemId) {
        this.problemId = problemId;
    }

    public String getProblemTitle() {
        return problemTitle;
    }

    public void setProblemTitle(String problemTitle) {
        this.problemTitle = problemTitle;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public Boolean getOutputCorrect() {
        return outputCorrect;
    }

    public void setOutputCorrect(Boolean outputCorrect) {
        this.outputCorrect = outputCorrect;
    }

    public JudgeStatus getJudgeStatus() {
        return judgeStatus;
    }

    public void setJudgeStatus(JudgeStatus judgeStatus) {
        this.judgeStatus = judgeStatus;
    }

    public List<TestCaseResultDTO> getTestCaseResults() {
        return testCaseResults;
    }

    public void setTestCaseResults(
            List<TestCaseResultDTO> testCaseResults) {

        this.testCaseResults = testCaseResults;
    }
}