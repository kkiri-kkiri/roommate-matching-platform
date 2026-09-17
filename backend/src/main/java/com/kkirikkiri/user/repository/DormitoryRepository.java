package com.kkirikkiri.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.user.entity.Dormitory;

public interface DormitoryRepository extends JpaRepository<Dormitory, Long> {

	Optional<Dormitory> findByDormitoryName(String dormitoryName);
}
