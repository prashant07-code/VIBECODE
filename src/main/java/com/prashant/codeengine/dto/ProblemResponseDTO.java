package com.prashant.codeengine.dto;

import com.prashant.codeengine.entity.Difficulty;

import java.util.List;

public class ProblemResponseDTO {

    private Long id;
    private String title;
    private String description;
    private Difficulty difficulty;
    private List<ProblemPublicTestCaseDTO> testCases;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public List<ProblemPublicTestCaseDTO> getTestCases() {
        return testCases;
    }

    public void setTestCases(List<ProblemPublicTestCaseDTO> testCases) {
        this.testCases = testCases;
    }
}