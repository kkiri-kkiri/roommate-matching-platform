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
@Table(name = "ROOMMATE_MATCH", uniqueConstraints = @UniqueConstraint(
		columnNames = {"requester_user_id", "target_user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoommateMatch {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "match_id")
	private Long matchId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "requester_user_id", nullable = false)
	private User requesterUser;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "target_user_id", nullable = false)
	private User targetUser;

	@Column(name = "match_score", nullable = false)
	private int matchScore;

	@Column(name = "recommendation_reason", nullable = true, columnDefinition = "TEXT")
	private String recommendationReason;

	public RoommateMatch(User requesterUser, User targetUser, int matchScore,
			String recommendationReason) {
		this.requesterUser = Objects.requireNonNull(requesterUser, "requesterUser");
		this.targetUser = Objects.requireNonNull(targetUser, "targetUser");
		validateDifferentUsers(requesterUser, targetUser);
		updateResult(matchScore, recommendationReason);
	}

	public void updateResult(int matchScore,
			String recommendationReason) {
		if (matchScore < 0 || matchScore > 100) {
			throw new IllegalArgumentException("matchScore must be between 0 and 100");
		}
		this.matchScore = matchScore;
		this.recommendationReason = recommendationReason;
	}

	private static void validateDifferentUsers(User requesterUser, User targetUser) {
		if (requesterUser == targetUser || (requesterUser.getUserId() != null
				&& requesterUser.getUserId().equals(targetUser.getUserId()))) {
			throw new IllegalArgumentException("requesterUser and targetUser must be different");
		}
	}
}
