package com.prashant.codeengine.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ProblemTestCaseRequestDTO {

    @NotBlank(message = "Input cannot be blank")
    private String input;

    @NotBlank(message = "Expected output cannot be blank")
    private String expectedOutput;

    @NotNull(message = "Problem ID is required")
    private Long problemId;

    private boolean hidden;

    public String getInput() {
        return input;
    }

    public void setInput(String input) {
        this.input = input;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }

    public Long getProblemId() {
        return problemId;
    }

    public void setProblemId(Long problemId) {
        this.problemId = problemId;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }
}