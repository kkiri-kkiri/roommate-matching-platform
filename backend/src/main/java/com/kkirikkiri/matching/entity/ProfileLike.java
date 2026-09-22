package com.kkirikkiri.matching.entity;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.kkirikkiri.user.entity.User;

@Entity
@Table(name = "PROFILE_LIKE", uniqueConstraints = @UniqueConstraint(
		columnNames = {"user_id", "target_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileLike {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "profile_like_id")
	private Long profileLikeId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "target_user_id", nullable = false)
	private User targetUser;

	public ProfileLike(User user, User targetUser) {
		this.user = Objects.requireNonNull(user, "user");
		this.targetUser = Objects.requireNonNull(targetUser, "targetUser");
		validateDifferentUsers(user, targetUser);
	}

	private static void validateDifferentUsers(User user, User targetUser) {
		if (user == targetUser || (user.getUserId() != null
				&& user.getUserId().equals(targetUser.getUserId()))) {
			throw new IllegalArgumentException("user and targetUser must be different");
		}
	}
}
