package com.prashant.codeengine.controller;
import com.prashant.codeengine.dto.CodeExecutionRequestDTO;
import com.prashant.codeengine.dto.CodeExecutionResponseDTO;
import com.prashant.codeengine.dto.DifficultyStatisticsDTO;
import com.prashant.codeengine.dto.LanguageStatisticsDTO;
import com.prashant.codeengine.dto.SubmissionHistoryResponseDTO;
import com.prashant.codeengine.dto.SubmissionListResponseDTO;
import com.prashant.codeengine.dto.SubmissionStatisticsDTO;
import com.prashant.codeengine.service.CodeSubmissionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/execute")
public class ExecuteController {

    private final CodeSubmissionService service;

    public ExecuteController(CodeSubmissionService service) {
        this.service = service;
    }

    // =========================================================
    // POST /execute
    // Execute code and create a new submission
    // =========================================================
    @PostMapping
    public CodeExecutionResponseDTO execute(
            @Valid @RequestBody CodeExecutionRequestDTO request)
            throws Exception {

        return service.saveSubmission(request);
    }

    // =========================================================
    // GET /execute/submissions
    //
    // Lightweight submission list
    //
    // Example:
    // /execute/submissions?page=0&size=10&sort=id,desc
    //
    // Optional filters:
    // /execute/submissions?page=0&size=10&sort=id,desc&language=java
    // /execute/submissions?page=0&size=10&sort=id,desc&search=hello
    // =========================================================
    @GetMapping("/submissions")
    public ResponseEntity<Page<SubmissionListResponseDTO>>
    getAllSubmissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String sort,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String search) {

        String[] sortParams =
                sort.split(",");

        String sortField =
                sortParams[0];

        Sort.Direction direction =
                sortParams.length > 1
                        && sortParams[1].equalsIgnoreCase("asc")
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                direction,
                                sortField
                        )
                );

        Page<SubmissionListResponseDTO> submissions =
                service.getSubmissionList(
                        pageable,
                        language,
                        search
                );

        return ResponseEntity.ok(
                submissions
        );
    }

    // =========================================================
    // GET /execute/submissions/history
    //
    // Lightweight submission history
    //
    // Example:
    // /execute/submissions/history?page=0&size=10&sort=id,desc
    //
    // Optional filters:
    // /execute/submissions/history?page=0&size=10&sort=id,desc&language=java
    // /execute/submissions/history?page=0&size=10&sort=id,desc&search=hello
    // =========================================================
    @GetMapping("/submissions/history")
    public ResponseEntity<Page<SubmissionHistoryResponseDTO>>
    getSubmissionHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String sort,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String search) {

        String[] sortParams =
                sort.split(",");

        String sortField =
                sortParams[0];

        Sort.Direction direction =
                sortParams.length > 1
                        && sortParams[1].equalsIgnoreCase("asc")
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        Pageable pageable =
                PageRequest.of(
                        page,
                        size,
                        Sort.by(
                                direction,
                                sortField
                        )
                );

        Page<SubmissionHistoryResponseDTO> history =
                service.getSubmissionHistory(
                        pageable,
                        language,
                        search
                );

        return ResponseEntity.ok(
                history
        );
    }

    // =========================================================
    // GET /execute/submissions/statistics
    //
    // Global statistics:
    // /execute/submissions/statistics
    //
    // Problem-specific statistics (Day 56):
    // /execute/submissions/statistics?problemId=5
    // =========================================================
    @GetMapping("/submissions/statistics")
    public SubmissionStatisticsDTO getSubmissionStatistics(
            @RequestParam(required = false) Long problemId) {

        if (problemId != null) {

            return service.getSubmissionStatistics(
                    problemId
            );
        }

        return service.getSubmissionStatistics();
    }

    // =========================================================
    // GET /execute/submissions/statistics/by-language
    //
    // Language-wise statistics breakdown (Day 58)
    // =========================================================
    @GetMapping("/submissions/statistics/by-language")
    public List<LanguageStatisticsDTO> getLanguageStatistics() {

        return service.getLanguageStatistics();
    }

    // =========================================================
    // GET /execute/submissions/statistics/by-difficulty
    //
    // Difficulty-wise statistics breakdown (Day 59)
    // =========================================================
    @GetMapping("/submissions/statistics/by-difficulty")
    public List<DifficultyStatisticsDTO> getDifficultyStatistics() {

        return service.getDifficultyStatistics();
    }

    // =========================================================
    // GET /execute/submissions/{id}
    //
    // Full details of one submission
    //
    // Example:
    // /execute/submissions/68
    // =========================================================
    @GetMapping("/submissions/{id}")
    public CodeExecutionResponseDTO getSubmissionById(
            @PathVariable Long id) {

        return service.getSubmissionById(
                id
        );
    }

    // =========================================================
    // GET /execute/submissions/history/{id}
    //
    // Complete details of one submission from history
    //
    // Example:
    // /execute/submissions/history/68
    // =========================================================
    @GetMapping("/submissions/history/{id}")
    public CodeExecutionResponseDTO getSubmissionHistoryById(
            @PathVariable Long id) {

        return service.getSubmissionById(
                id
        );
    }
}