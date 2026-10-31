package com.prashant.codeengine.repository;

import com.prashant.codeengine.entity.CodeSubmission;
import com.prashant.codeengine.entity.Difficulty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CodeSubmissionRepository
        extends JpaRepository<CodeSubmission, Long> {

    // Language + Search + Pagination
    @Query(
            value = """
                    SELECT *
                    FROM code_submission
                    WHERE (:language IS NULL
                           OR LOWER(language) = LOWER(:language))
                    AND (:search IS NULL
                         OR LOWER(source_code)
                         LIKE LOWER(CONCAT('%', :search, '%')))
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM code_submission
                    WHERE (:language IS NULL
                           OR LOWER(language) = LOWER(:language))
                    AND (:search IS NULL
                         OR LOWER(source_code)
                         LIKE LOWER(CONCAT('%', :search, '%')))
                    """,
            nativeQuery = true
    )
    Page<CodeSubmission> findSubmissionsWithFilters(
            @Param("language") String language,
            @Param("search") String search,
            Pageable pageable
    );

    // Successful submissions
    long countByStatusIgnoreCase(String status);

    @Query("""
            SELECT COUNT(DISTINCT submission.id)
            FROM CodeSubmission submission
            LEFT JOIN submission.problem problem
            WHERE UPPER(submission.status) = 'SUCCESS'
            AND (:problemId IS NULL OR problem.id = :problemId)
            AND (:language IS NULL OR LOWER(submission.language) = LOWER(:language))
            AND (:difficulty IS NULL OR problem.difficulty = :difficulty)
            AND EXISTS (
                SELECT testResult.id
                FROM TestCaseResult testResult
                WHERE testResult.codeSubmission = submission
                AND UPPER(testResult.judgeStatus) = 'ACCEPTED'
            )
            AND NOT EXISTS (
                SELECT testResult.id
                FROM TestCaseResult testResult
                WHERE testResult.codeSubmission = submission
                AND UPPER(testResult.judgeStatus) IN (
                    'WRONG_ANSWER',
                    'TIME_LIMIT_EXCEEDED',
                    'OUTPUT_LIMIT_EXCEEDED'
                )
            )
            """)
    long countAcceptedSubmissions(
            @Param("problemId") Long problemId,
            @Param("language") String language,
            @Param("difficulty") Difficulty difficulty
    );

    // Day 56: Total submissions for a specific problem
    long countByProblemId(Long problemId);

    // Day 56: Status-wise count for a specific problem
    long countByStatusIgnoreCaseAndProblemId(String status, Long problemId);

    // Day 58: Distinct languages present in the database
    @Query(
            value = "SELECT DISTINCT language FROM code_submission",
            nativeQuery = true
    )
    List<String> findDistinctLanguages();

    // Day 58: Status-wise count for a specific language
    long countByStatusIgnoreCaseAndLanguageIgnoreCase(
            String status,
            String language
    );

    // Day 58: Total submissions for a specific language
    long countByLanguageIgnoreCase(String language);

    // Day 59: Total submissions for a specific difficulty
    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM code_submission cs
                    JOIN problem p ON cs.problem_id = p.id
                    WHERE UPPER(p.difficulty) = UPPER(:difficulty)
                    """,
            nativeQuery = true
    )
    long countByProblemDifficulty(
            @Param("difficulty") String difficulty
    );

    // Day 59: Status-wise count for a specific difficulty
    @Query(
            value = """
                    SELECT COUNT(*)
                    FROM code_submission cs
                    JOIN problem p ON cs.problem_id = p.id
                    WHERE UPPER(cs.status) = UPPER(:status)
                    AND UPPER(p.difficulty) = UPPER(:difficulty)
                    """,
            nativeQuery = true
    )
    long countByStatusAndProblemDifficulty(
            @Param("status") String status,
            @Param("difficulty") String difficulty
    );
}
