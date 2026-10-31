package com.prashant.codeengine.repository;

import com.prashant.codeengine.entity.Difficulty;
import com.prashant.codeengine.entity.Problem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    Page<Problem> findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            String titleKeyword,
            String descriptionKeyword,
            Pageable pageable
    );

    Page<Problem> findByDifficulty(
            Difficulty difficulty,
            Pageable pageable
    );

    @Query("""
            SELECT p
            FROM Problem p
            WHERE p.difficulty = :difficulty
            AND (
                LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR
                LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
            )
            """)
    Page<Problem> findByDifficultyAndSearch(
            @Param("difficulty") Difficulty difficulty,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            SELECT p
            FROM Problem p
            WHERE
                (
                    :difficulty IS NULL
                    OR p.difficulty = :difficulty
                )
            AND
                (
                    :keyword IS NULL
                    OR :keyword = ''
                    OR LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
                )
            """)
    Page<Problem> findBySearchAndOptionalDifficulty(
            @Param("keyword") String keyword,
            @Param("difficulty") Difficulty difficulty,
            Pageable pageable
    );
}