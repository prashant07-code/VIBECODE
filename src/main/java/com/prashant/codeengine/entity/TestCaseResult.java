package com.prashant.codeengine.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class TestCaseResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String input;

    private String expectedOutput;

    private String actualOutput;

    private Boolean outputCorrect;

    private String judgeStatus;

    @ManyToOne
    private CodeSubmission codeSubmission;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getJudgeStatus() {
        return judgeStatus;
    }

    public void setJudgeStatus(String judgeStatus) {
        this.judgeStatus = judgeStatus;
    }

    public CodeSubmission getCodeSubmission() {
        return codeSubmission;
    }

    public void setCodeSubmission(CodeSubmission codeSubmission) {
        this.codeSubmission = codeSubmission;
    }
}