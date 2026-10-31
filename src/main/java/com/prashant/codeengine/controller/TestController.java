package com.prashant.codeengine.controller;

import com.prashant.codeengine.dto.StudentDTO;
import com.prashant.codeengine.entity.Student;
import com.prashant.codeengine.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TestController {

    @Autowired
    private StudentService studentService;

    // Save student
    @PostMapping("/student")
    public ResponseEntity<Student> addStudent(@Valid @RequestBody Student student) {

        Student savedStudent = studentService.saveStudent(student);

        return new ResponseEntity<>(savedStudent, HttpStatus.CREATED);
    }

    // Get all students
    @GetMapping("/students")
    public List<Student> getStudents() {
        return studentService.getAllStudents();
    }

    // Get single student by ID (Return DTO)
    @GetMapping("/student/{id}")
    public ResponseEntity<StudentDTO> getStudentById(@PathVariable Long id) {

        Student student = studentService.getStudentById(id);

        StudentDTO dto = studentService.convertToDTO(student);

        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    // Update student by ID
    @PutMapping("/student/{id}")
    public ResponseEntity<Student> updateStudent(@PathVariable Long id,
                                                 @Valid @RequestBody Student student) {

        Student updatedStudent = studentService.updateStudent(id, student);

        if (updatedStudent != null) {
            return new ResponseEntity<>(updatedStudent, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    // Delete student by ID
    @DeleteMapping("/student/{id}")
    public ResponseEntity<String> deleteStudentById(@PathVariable Long id) {

        String message = studentService.deleteStudentById(id);

        if (message.equals("Student deleted successfully")) {
            return new ResponseEntity<>(message, HttpStatus.OK);
        }

        return new ResponseEntity<>(message, HttpStatus.NOT_FOUND);
    }

    // Test API
    @GetMapping("/test-student")
    public Student testStudent() {

        Student s = new Student();
        s.setId(99L);
        s.setName("Prashant");
        s.setCourse("CSE");

        return s;
    }
}