package com.prashant.codeengine.repository;
import com.prashant.codeengine.entity.ProblemTestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProblemTestCaseRepository extends JpaRepository<ProblemTestCase, Long> {

    List<ProblemTestCase> findByProblemIdOrderByIdAsc(Long problemId);
}