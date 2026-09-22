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
@Table(name = "DORMITORY")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dormitory {

	public Dormitory(String dormitoryCode, String dormitoryName, boolean active) {
		this.dormitoryCode = dormitoryCode;
		this.dormitoryName = dormitoryName;
		this.active = active;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "dormitory_id")
	private Long dormitoryId;

	@Column(name = "dormitory_code", nullable = false, unique = true, length = 20)
	private String dormitoryCode;

	@Column(name = "dormitory_name", nullable = false, unique = true, length = 30)
	private String dormitoryName;

	@Column(name = "is_active", nullable = false)
	private boolean active;
}
