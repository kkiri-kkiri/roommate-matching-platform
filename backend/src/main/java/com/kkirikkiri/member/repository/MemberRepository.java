package com.kkirikkiri.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.kkirikkiri.member.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Long> {

	Optional<Member> findByLoginId(String loginId);

	Optional<Member> findBySchoolEmail(String schoolEmail);

	boolean existsByLoginId(String loginId);

	boolean existsBySchoolEmail(String schoolEmail);

	boolean existsByLoginIdAndSchoolEmail(String loginId, String schoolEmail);
}
