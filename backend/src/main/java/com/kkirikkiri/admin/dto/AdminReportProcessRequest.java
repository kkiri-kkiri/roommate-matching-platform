package com.kkirikkiri.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 처리 대기 상태 확인과 대상 회원 정지/게시글·댓글 삭제는 서비스에서 처리한다. */
public record AdminReportProcessRequest(
		@NotBlank @Pattern(regexp = "COMPLETE|REJECT|SUSPEND_USER|DELETE_TARGET") String action) {
}
