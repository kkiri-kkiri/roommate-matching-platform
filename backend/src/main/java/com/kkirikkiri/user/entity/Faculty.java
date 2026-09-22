package com.kkirikkiri.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "FACULTY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faculty {

	public Faculty(String facultyName, boolean active) {
		this.facultyName = facultyName;
		this.active = active;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "faculty_id")
	private Long facultyId;

	@Column(name = "faculty_name", nullable = false, unique = true, length = 100)
	private String facultyName;

	@Column(name = "is_active", nullable = false)
	private boolean active;
}
