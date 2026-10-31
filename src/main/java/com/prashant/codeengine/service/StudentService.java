package com.prashant.codeengine.service;

import com.prashant.codeengine.dto.StudentDTO;
import com.prashant.codeengine.entity.Student;
import com.prashant.codeengine.exception.StudentNotFoundException;
import com.prashant.codeengine.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StudentService {

    @Autowired
    private StudentRepository studentRepository;

    // Save student into database
    public Student saveStudent(Student student) {
        return studentRepository.save(student);
    }

    // Get all students from database
    public List<Student> getAllStudents() {
        return studentRepository.findAll();
    }

    // Get single student by ID
    public Student getStudentById(Long id) {

        return studentRepository.findById(Math.toIntExact(id))
                .orElseThrow(() ->
                        new StudentNotFoundException("Student not found with ID: " + id));
    }

    // Delete student by ID
    public String deleteStudentById(Long id) {

        // Check if student exists
        if (studentRepository.existsById(Math.toIntExact(id))) {

            // Delete student
            studentRepository.deleteById(Math.toIntExact(id));

            return "Student deleted successfully";
        }

        // If student not found
        return "Student not found";
    }

    // Update student by ID
    public Student updateStudent(Long id, Student student) {

        // Check if student exists
        if (studentRepository.existsById(Math.toIntExact(id))) {

            // Set ID of the student
            student.setId(id);

            // Save updated student
            return studentRepository.save(student);
        }

        // If student not found
        return null;
    }

    // Convert Student Entity to StudentDTO
    public StudentDTO convertToDTO(Student student) {

        StudentDTO dto = new StudentDTO();

        dto.setId(student.getId());
        dto.setName(student.getName());
        dto.setCourse(student.getCourse());

        return dto;
    }
}