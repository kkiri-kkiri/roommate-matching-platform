package com.kkirikkiri.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    MATCH_ANALYSIS_UNAVAILABLE(HttpStatus.CONFLICT, "매칭 결과를 계산할 수 없습니다. 프로필과 선호 설문을 확인해 주세요."),
    AI_GATEWAY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 분석 서비스에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."),
    AI_GATEWAY_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "AI 분석 응답을 확인할 수 없습니다. 다시 시도해 주세요."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증 정보가 유효하지 않습니다."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청을 처리할 수 없습니다."),
	PASSWORD_RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "재설정 토큰이 올바르지 않습니다."),
	PASSWORD_RESET_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "재설정 토큰이 만료되었습니다. 이메일을 다시 요청해 주세요."),
	PASSWORD_RESET_TOKEN_ALREADY_USED(HttpStatus.BAD_REQUEST, "이미 사용된 재설정 토큰입니다. 이메일을 다시 요청해 주세요."),
	PASSWORD_RESET_ACCOUNT_INACTIVE(HttpStatus.BAD_REQUEST, "계정이 활성 상태가 아니어서 비밀번호를 변경할 수 없습니다."),
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
	EMAIL_VERIFICATION_COOLDOWN(HttpStatus.TOO_MANY_REQUESTS, "인증번호는 잠시 후 다시 요청해 주세요."),
	EMAIL_VERIFICATION_NOT_FOUND(HttpStatus.BAD_REQUEST, "먼저 인증번호를 요청해 주세요."),
	EMAIL_VERIFICATION_INVALID(HttpStatus.BAD_REQUEST, "인증번호가 올바르지 않습니다."),
	EMAIL_VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "인증번호가 만료되었습니다. 새 인증번호를 요청해 주세요."),
	EMAIL_VERIFICATION_ALREADY_USED(HttpStatus.BAD_REQUEST, "이미 사용한 인증번호입니다. 새 인증번호를 요청해 주세요."),
	EMAIL_VERIFICATION_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "이메일 인증이 만료되었거나 유효하지 않습니다. 다시 인증해 주세요."),
	EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 학교 이메일입니다. 다른 이메일을 사용해 주세요."),
	DUPLICATE_ACCOUNT_VALUE(HttpStatus.CONFLICT, "학교 이메일 또는 닉네임이 이미 사용 중입니다."),
	DORMITORY_NOT_FOUND(HttpStatus.BAD_REQUEST, "선택한 생활관을 사용할 수 없습니다. 다시 선택해 주세요."),
	FACULTY_NOT_FOUND(HttpStatus.BAD_REQUEST, "선택한 학부를 사용할 수 없습니다. 다시 선택해 주세요."),
	MAJOR_NOT_FOUND(HttpStatus.BAD_REQUEST, "선택한 전공을 사용할 수 없습니다. 다시 선택해 주세요."),
	ACCOUNT_SAVE_CONFLICT(HttpStatus.CONFLICT, "입력한 회원정보가 이미 사용 중입니다. 다른 값으로 시도해 주세요."),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}
}
