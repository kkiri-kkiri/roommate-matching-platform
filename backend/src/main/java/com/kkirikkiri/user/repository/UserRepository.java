package com.kkirikkiri.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {
}
