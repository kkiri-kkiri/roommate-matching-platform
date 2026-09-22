package com.kkirikkiri.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.user.entity.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

	Optional<UserProfile> findByUser_UserId(Long userId);

	boolean existsByNickname(String nickname);

	boolean existsByNicknameAndUser_UserIdNot(String nickname, Long userId);
}
