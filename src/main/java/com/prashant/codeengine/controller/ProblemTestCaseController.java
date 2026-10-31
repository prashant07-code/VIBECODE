package com.prashant.codeengine.controller;
import com.prashant.codeengine.dto.ProblemTestCaseRequestDTO;
import com.prashant.codeengine.dto.ProblemTestCaseResponseDTO;
import com.prashant.codeengine.service.ProblemTestCaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/problems/testcases")
public class ProblemTestCaseController {

    private final ProblemTestCaseService problemTestCaseService;

    public ProblemTestCaseController(
            ProblemTestCaseService problemTestCaseService) {

        this.problemTestCaseService = problemTestCaseService;
    }

    @GetMapping
    public List<ProblemTestCaseResponseDTO> getAllTestCases() {

        return problemTestCaseService.getAllTestCases();
    }

    @GetMapping("/{problemId}")
    public List<ProblemTestCaseResponseDTO> getTestCasesByProblemId(
            @PathVariable Long problemId) {

        return problemTestCaseService
                .getTestCasesByProblemId(problemId);
    }

    @PostMapping
    public ProblemTestCaseResponseDTO createTestCase(
            @Valid @RequestBody ProblemTestCaseRequestDTO requestDTO) {

        return problemTestCaseService.createTestCase(requestDTO);
    }

    @PutMapping("/{id}")
    public ProblemTestCaseResponseDTO updateTestCase(
            @PathVariable Long id,
            @Valid @RequestBody ProblemTestCaseRequestDTO requestDTO) {

        return problemTestCaseService.updateTestCase(id, requestDTO);
    }

    @DeleteMapping("/{id}")
    public void deleteTestCase(
            @PathVariable Long id) {

        problemTestCaseService.deleteTestCase(id);
    }
}