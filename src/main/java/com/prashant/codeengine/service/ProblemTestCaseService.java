package com.prashant.codeengine.service;
import com.prashant.codeengine.dto.ProblemTestCaseRequestDTO;
import com.prashant.codeengine.dto.ProblemTestCaseResponseDTO;
import com.prashant.codeengine.entity.Problem;
import com.prashant.codeengine.entity.ProblemTestCase;
import com.prashant.codeengine.repository.ProblemTestCaseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProblemTestCaseService {

    private final ProblemTestCaseRepository problemTestCaseRepository;
    private final ProblemService problemService;

    public ProblemTestCaseService(
            ProblemTestCaseRepository problemTestCaseRepository,
            ProblemService problemService) {

        this.problemTestCaseRepository = problemTestCaseRepository;
        this.problemService = problemService;
    }

    public ProblemTestCaseResponseDTO createTestCase(
            ProblemTestCaseRequestDTO requestDTO) {

        Problem problem = problemService.getProblemEntityById(
                requestDTO.getProblemId()
        );

        ProblemTestCase testCase = new ProblemTestCase();

        testCase.setInput(requestDTO.getInput());
        testCase.setExpectedOutput(requestDTO.getExpectedOutput());
        testCase.setHidden(requestDTO.isHidden());
        testCase.setProblem(problem);

        ProblemTestCase savedTestCase =
                problemTestCaseRepository.save(testCase);

        return mapToResponseDTO(savedTestCase);
    }

    public List<ProblemTestCaseResponseDTO> getAllTestCases() {

        return problemTestCaseRepository.findAll()
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    public List<ProblemTestCaseResponseDTO> getTestCasesByProblemId(
            Long problemId) {

        problemService.getProblemEntityById(problemId);

        return problemTestCaseRepository
                .findByProblemIdOrderByIdAsc(problemId)
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    public ProblemTestCaseResponseDTO updateTestCase(
            Long id,
            ProblemTestCaseRequestDTO requestDTO) {

        ProblemTestCase testCase =
                problemTestCaseRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Test case not found with id: " + id
                                )
                        );

        Problem problem = problemService.getProblemEntityById(
                requestDTO.getProblemId()
        );

        testCase.setInput(requestDTO.getInput());
        testCase.setExpectedOutput(requestDTO.getExpectedOutput());
        testCase.setHidden(requestDTO.isHidden());
        testCase.setProblem(problem);

        ProblemTestCase updatedTestCase =
                problemTestCaseRepository.save(testCase);

        return mapToResponseDTO(updatedTestCase);
    }

    public void deleteTestCase(Long id) {

        ProblemTestCase testCase =
                problemTestCaseRepository.findById(id)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Test case not found with id: " + id
                                )
                        );

        problemTestCaseRepository.delete(testCase);
    }

    private ProblemTestCaseResponseDTO mapToResponseDTO(
            ProblemTestCase testCase) {

        ProblemTestCaseResponseDTO responseDTO =
                new ProblemTestCaseResponseDTO();

        responseDTO.setId(testCase.getId());
        responseDTO.setInput(testCase.getInput());
        responseDTO.setExpectedOutput(testCase.getExpectedOutput());
        responseDTO.setHidden(testCase.isHidden());

        if (testCase.getProblem() != null) {
            responseDTO.setProblemId(testCase.getProblem().getId());
        }

        return responseDTO;
    }
}