package com.prashant.codeengine.repository;

import com.prashant.codeengine.entity.TestCaseResult;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestCaseResultRepository extends JpaRepository<TestCaseResult, Long> {

}