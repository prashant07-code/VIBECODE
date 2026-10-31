package com.prashant.codeengine.dto;

public class StudentDTO {

    private Long id;
    private String name;
    private String course;

    // Default Constructor
    public StudentDTO() {
    }

    // Parameterized Constructor
    public StudentDTO(Long id, String name, String course) {
        this.id = id;
        this.name = name;
        this.course = course;
    }

    // Getters
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCourse() {
        return course;
    }

    // Setters
    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCourse(String course) {
        this.course = course;
    }
}