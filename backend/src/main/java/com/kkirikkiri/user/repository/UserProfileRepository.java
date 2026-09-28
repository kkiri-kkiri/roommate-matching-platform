package com.kkirikkiri.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.user.entity.UserProfile;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<UserProfile> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "faculty", "major", "dormitory"})
    org.springframework.data.domain.Page<UserProfile> findAll(
            org.springframework.data.jpa.domain.Specification<UserProfile> spec, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user", "faculty", "major", "dormitory"})
    java.util.List<UserProfile> findAllByUser_UserIdIn(java.util.Collection<Long> ids);

	Optional<UserProfile> findByUser_UserId(Long userId);

	boolean existsByNickname(String nickname);

	boolean existsByNicknameAndUser_UserIdNot(String nickname, Long userId);
}
