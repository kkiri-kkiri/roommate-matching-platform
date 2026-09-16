package com.kkirikkiri.global.response;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kkirikkiri.global.exception.ErrorCode;

public record ApiResponse<T>(
		boolean success,
		String message,
		@JsonInclude(JsonInclude.Include.ALWAYS) T data,
		@JsonInclude(JsonInclude.Include.NON_NULL) String errorCode,
		@JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> fieldErrors) {

	public static <T> ApiResponse<T> success(T data) {
		return success("요청이 성공했습니다.", data);
	}

	public static <T> ApiResponse<T> success(String message, T data) {
		return new ApiResponse<>(true, message, data, null, null);
	}

	public static ApiResponse<Void> successWithoutData() {
		return success("처리가 완료되었습니다.", null);
	}

	public static ApiResponse<Void> failure(ErrorCode errorCode) {
		return failure(errorCode, null);
	}

	public static ApiResponse<Void> failure(ErrorCode errorCode, Map<String, String> fieldErrors) {
		return new ApiResponse<>(false, errorCode.getMessage(), null, errorCode.name(), fieldErrors);
	}
}
