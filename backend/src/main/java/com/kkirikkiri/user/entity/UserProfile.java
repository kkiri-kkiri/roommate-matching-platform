package com.kkirikkiri.user.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "USER_PROFILE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "user_profile_id")
	private Long userProfileId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Column(name = "nickname", nullable = false, unique = true, length = 20)
	private String nickname;

	@Column(name = "gender", nullable = false, length = 10)
	private String gender;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "dormitory_id", nullable = false)
	private Dormitory dormitory;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "faculty_id", nullable = false)
	private Faculty faculty;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "major_id", nullable = true)
	private Major major;

	@Column(name = "birth_year", nullable = false, columnDefinition = "SMALLINT")
	private Short birthYear;

	@Column(name = "admission_year", nullable = false, length = 2, columnDefinition = "CHAR(2)")
	private String admissionYear;

	@Column(name = "grade", nullable = false, length = 20)
	private String grade;

	@Column(name = "mbti", nullable = false, length = 4, columnDefinition = "CHAR(4)")
	private String mbti;

	@Column(name = "introduction", nullable = true, length = 255)
	private String introduction;

	@Column(name = "profile_image_url", nullable = true, length = 500)
	private String profileImageUrl;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "DATETIME")
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME")
	private LocalDateTime updatedAt;

	public UserProfile(User user, String nickname, String gender, Dormitory dormitory,
			Faculty faculty, Major major, Short birthYear, String admissionYear, String grade, String mbti) {
		this.user = Objects.requireNonNull(user, "user");
		this.nickname = Objects.requireNonNull(nickname, "nickname");
		this.gender = Objects.requireNonNull(gender, "gender");
		this.dormitory = Objects.requireNonNull(dormitory, "dormitory");
		this.faculty = Objects.requireNonNull(faculty, "faculty");
		this.major = major;
		this.birthYear = Objects.requireNonNull(birthYear, "birthYear");
		this.admissionYear = Objects.requireNonNull(admissionYear, "admissionYear");
		this.grade = Objects.requireNonNull(grade, "grade");
		this.mbti = Objects.requireNonNull(mbti, "mbti");
	}

	public void changeNickname(String nickname) {
		this.nickname = Objects.requireNonNull(nickname, "nickname");
	}

	/** Length validation is performed by the survey request; retain meaningful whitespace. */
	public void changeIntroduction(String introduction) {
		this.introduction = introduction;
	}

	public void changeDormitory(Dormitory dormitory) {
		this.dormitory = Objects.requireNonNull(dormitory, "dormitory");
	}

	/**
	 * 학부와 전공의 소속 일치 여부는 서비스에서 확인한다. 전공은 null로 해제할 수 있다.
	 */
	public void changeAcademicAffiliation(Faculty faculty, Major major) {
		this.faculty = Objects.requireNonNull(faculty, "faculty");
		this.major = major;
	}

	public void changeGrade(String grade) {
		this.grade = Objects.requireNonNull(grade, "grade");
	}

	/**
	 * MBTI 부분 수정값은 서비스에서 기존 값과 조합하여 네 자리 문자열로 전달한다.
	 */
	public void changeMbti(String mbti) {
		this.mbti = Objects.requireNonNull(mbti, "mbti");
	}

	@PrePersist
	protected void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
}
