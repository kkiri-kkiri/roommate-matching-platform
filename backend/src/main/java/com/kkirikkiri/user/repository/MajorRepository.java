package com.kkirikkiri.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.user.entity.Major;

public interface MajorRepository extends JpaRepository<Major, Long> {

	Optional<Major> findByFaculty_FacultyIdAndMajorName(Long facultyId, String majorName);
}
