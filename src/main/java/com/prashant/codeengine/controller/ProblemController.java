package com.prashant.codeengine.controller;
import com.prashant.codeengine.dto.ProblemRequestDTO;
import com.prashant.codeengine.dto.ProblemResponseDTO;
import com.prashant.codeengine.entity.Difficulty;
import com.prashant.codeengine.service.ProblemService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/problems")
public class ProblemController {

    private final ProblemService problemService;

    public ProblemController(ProblemService problemService) {

        this.problemService = problemService;
    }

    @PostMapping
    public ProblemResponseDTO createProblem(
            @Valid @RequestBody ProblemRequestDTO requestDTO) {

        return problemService.createProblem(requestDTO);
    }

    @GetMapping
    public List<ProblemResponseDTO> getAllProblems() {

        return problemService.getAllProblems();
    }

    @GetMapping("/paginated")
    public Page<ProblemResponseDTO> getProblemsPaginated(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10) Pageable pageable) {

        return problemService.getProblemsPaginated(
                search,
                pageable
        );
    }

    @GetMapping("/filter")
    public Page<ProblemResponseDTO> getProblemsByDifficulty(
            @RequestParam Difficulty difficulty,
            @PageableDefault(size = 10) Pageable pageable) {

        return problemService.getProblemsByDifficulty(
                difficulty,
                pageable
        );
    }

    @GetMapping("/search")
    public Page<ProblemResponseDTO> searchProblems(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Difficulty difficulty,
            @PageableDefault(size = 10) Pageable pageable) {

        return problemService.getProblemsBySearchAndOptionalDifficulty(
                search,
                difficulty,
                pageable
        );
    }

    @GetMapping("/filter/search")
    public Page<ProblemResponseDTO> getProblemsByDifficultyAndSearch(
            @RequestParam Difficulty difficulty,
            @RequestParam String search,
            @PageableDefault(size = 10) Pageable pageable) {

        return problemService.getProblemsByDifficultyAndSearch(
                difficulty,
                search,
                pageable
        );
    }

    @GetMapping("/{id}")
    public ProblemResponseDTO getProblemById(
            @PathVariable Long id) {

        return problemService.getProblemById(id);
    }

    @PutMapping("/{id}")
    public ProblemResponseDTO updateProblem(
            @PathVariable Long id,
            @Valid @RequestBody ProblemRequestDTO requestDTO) {

        return problemService.updateProblem(id, requestDTO);
    }

    @DeleteMapping("/{id}")
    public void deleteProblem(
            @PathVariable Long id) {

        problemService.deleteProblem(id);
    }
}