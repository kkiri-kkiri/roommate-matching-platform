package com.kkirikkiri.user.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;

public interface UserProfileRepository
        extends JpaRepository<UserProfile, Long>,
        JpaSpecificationExecutor<UserProfile> {

    @EntityGraph(attributePaths = {
            "user",
            "faculty",
            "major",
            "dormitory"
    })
    Page<UserProfile> findAll(
            Specification<UserProfile> spec,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "user",
            "faculty",
            "major",
            "dormitory"
    })
    List<UserProfile> findAllByUser_UserIdIn(Collection<Long> ids);

    Optional<UserProfile> findByUser_UserId(Long userId);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndUser_UserIdNot(
            String nickname,
            Long userId
    );

    List<UserProfile> findAllByUserIn(Collection<User> users);
}