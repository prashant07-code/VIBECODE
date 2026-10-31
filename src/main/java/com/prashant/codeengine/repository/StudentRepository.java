package com.prashant.codeengine.repository;

import com.prashant.codeengine.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, Integer> {
}