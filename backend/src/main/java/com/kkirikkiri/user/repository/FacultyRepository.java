package com.kkirikkiri.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.user.entity.Faculty;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {

	Optional<Faculty> findByFacultyName(String facultyName);
}
