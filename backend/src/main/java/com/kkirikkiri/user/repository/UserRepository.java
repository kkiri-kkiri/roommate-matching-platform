package com.kkirikkiri.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByLoginId(String loginId);

	Optional<User> findBySchoolEmail(String schoolEmail);

	boolean existsByLoginId(String loginId);

	boolean existsBySchoolEmail(String schoolEmail);
}
