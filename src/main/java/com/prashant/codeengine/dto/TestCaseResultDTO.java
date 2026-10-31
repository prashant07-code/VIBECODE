package com.prashant.codeengine.dto;

public class TestCaseResultDTO {

    private String input;
    private String expectedOutput;
    private String actualOutput;

    private Boolean outputCorrect;
    private JudgeStatus judgeStatus;

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

    public String getActualOutput() {
        return actualOutput;
    }

    public void setActualOutput(String actualOutput) {
        this.actualOutput = actualOutput;
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
}