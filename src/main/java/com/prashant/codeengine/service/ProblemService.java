package com.prashant.codeengine.service;
import com.prashant.codeengine.dto.ProblemPublicTestCaseDTO;
import com.prashant.codeengine.dto.ProblemRequestDTO;
import com.prashant.codeengine.dto.ProblemResponseDTO;
import com.prashant.codeengine.entity.Difficulty;
import com.prashant.codeengine.entity.Problem;
import com.prashant.codeengine.entity.ProblemTestCase;
import com.prashant.codeengine.exception.ProblemNotFoundException;
import com.prashant.codeengine.repository.ProblemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProblemService {

    private final ProblemRepository problemRepository;

    public ProblemService(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    public ProblemResponseDTO createProblem(
            ProblemRequestDTO requestDTO) {

        Problem problem = new Problem();

        problem.setTitle(requestDTO.getTitle());
        problem.setDescription(requestDTO.getDescription());
        problem.setDifficulty(requestDTO.getDifficulty());

        Problem savedProblem =
                problemRepository.save(problem);

        return mapToResponseDTO(savedProblem);
    }

    public List<ProblemResponseDTO> getAllProblems() {

        return problemRepository.findAll()
                .stream()
                .map(this::mapToResponseDTO)
                .collect(Collectors.toList());
    }

    public Page<ProblemResponseDTO> getProblemsPaginated(
            Pageable pageable) {

        return problemRepository.findAll(pageable)
                .map(this::mapToResponseDTO);
    }

    public Page<ProblemResponseDTO> getProblemsPaginated(
            String search,
            Pageable pageable) {

        if (search == null || search.trim().isEmpty()) {

            return problemRepository.findAll(pageable)
                    .map(this::mapToResponseDTO);
        }

        String keyword = search.trim();

        return problemRepository
                .findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                        keyword,
                        keyword,
                        pageable
                )
                .map(this::mapToResponseDTO);
    }

    public Page<ProblemResponseDTO> getProblemsByDifficulty(
            Difficulty difficulty,
            Pageable pageable) {

        return problemRepository
                .findByDifficulty(difficulty, pageable)
                .map(this::mapToResponseDTO);
    }

    public Page<ProblemResponseDTO> getProblemsByDifficultyAndSearch(
            Difficulty difficulty,
            String search,
            Pageable pageable) {

        if (search == null || search.trim().isEmpty()) {

            return problemRepository
                    .findByDifficulty(difficulty, pageable)
                    .map(this::mapToResponseDTO);
        }

        String keyword = search.trim();

        return problemRepository
                .findByDifficultyAndSearch(
                        difficulty,
                        keyword,
                        pageable
                )
                .map(this::mapToResponseDTO);
    }

    public Page<ProblemResponseDTO> getProblemsBySearchAndOptionalDifficulty(
            String search,
            Difficulty difficulty,
            Pageable pageable) {

        String keyword = null;

        if (search != null && !search.trim().isEmpty()) {
            keyword = search.trim();
        }

        return problemRepository
                .findBySearchAndOptionalDifficulty(
                        keyword,
                        difficulty,
                        pageable
                )
                .map(this::mapToResponseDTO);
    }

    public ProblemResponseDTO getProblemById(Long id) {

        Problem problem =
                problemRepository.findById(id)
                        .orElseThrow(() ->
                                new ProblemNotFoundException(
                                        "Problem not found with id: " + id
                                )
                        );

        return mapToResponseDTO(problem);
    }

    public ProblemResponseDTO updateProblem(
            Long id,
            ProblemRequestDTO requestDTO) {

        Problem problem =
                problemRepository.findById(id)
                        .orElseThrow(() ->
                                new ProblemNotFoundException(
                                        "Problem not found with id: " + id
                                )
                        );

        problem.setTitle(requestDTO.getTitle());
        problem.setDescription(requestDTO.getDescription());
        problem.setDifficulty(requestDTO.getDifficulty());

        Problem updatedProblem =
                problemRepository.save(problem);

        return mapToResponseDTO(updatedProblem);
    }

    /*
     * Internal method used by other services.
     *
     * Returns the actual Problem Entity.
     *
     * CodeSubmissionService needs the Problem entity
     * to attach the problem to CodeSubmission and
     * load its stored test cases.
     */
    public Problem getProblemEntityById(Long id) {

        return problemRepository.findById(id)
                .orElseThrow(() ->
                        new ProblemNotFoundException(
                                "Problem not found with id: " + id
                        )
                );
    }

    public void deleteProblem(Long id) {

        Problem problem =
                problemRepository.findById(id)
                        .orElseThrow(() ->
                                new ProblemNotFoundException(
                                        "Problem not found with id: " + id
                                )
                        );

        problemRepository.delete(problem);
    }

    private ProblemResponseDTO mapToResponseDTO(
            Problem problem) {

        ProblemResponseDTO responseDTO =
                new ProblemResponseDTO();

        responseDTO.setId(problem.getId());
        responseDTO.setTitle(problem.getTitle());
        responseDTO.setDescription(problem.getDescription());
        responseDTO.setDifficulty(problem.getDifficulty());

        List<ProblemPublicTestCaseDTO> testCaseDTOs =
                problem.getTestCases()
                        .stream()
                        .filter(testCase -> !testCase.isHidden())
                        .map(this::mapTestCaseToPublicDTO)
                        .collect(Collectors.toList());

        responseDTO.setTestCases(testCaseDTOs);

        return responseDTO;
    }

    private ProblemPublicTestCaseDTO mapTestCaseToPublicDTO(
            ProblemTestCase testCase) {

        ProblemPublicTestCaseDTO responseDTO =
                new ProblemPublicTestCaseDTO();

        responseDTO.setId(testCase.getId());
        responseDTO.setInput(testCase.getInput());

        return responseDTO;
    }
}