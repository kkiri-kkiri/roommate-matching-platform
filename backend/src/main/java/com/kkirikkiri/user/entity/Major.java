package com.kkirikkiri.user.entity;

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

@Entity
@Table(name = "MAJOR", uniqueConstraints = @UniqueConstraint(columnNames = {"faculty_id", "major_name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Major {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "major_id")
	private Long majorId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "faculty_id", nullable = false)
	private Faculty faculty;

	@Column(name = "major_name", nullable = false, length = 150)
	private String majorName;

	@Column(name = "is_active", nullable = false)
	private boolean active;
}
