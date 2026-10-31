package com.prashant.codeengine.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SubmissionListResponseDTO {

    private Long id;
    private String language;
    private String status;
    private JudgeStatus judgeStatus;
    private Boolean outputCorrect;
    private LocalDateTime createdAt;
    private Long problemId;
    private String problemTitle;
}
