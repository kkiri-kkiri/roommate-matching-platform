package com.kkirikkiri.global.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.kkirikkiri.global.response.ApiResponse;

@RestControllerAdvice
@NullMarked
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(CustomException.class)
	public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException exception) {
		ErrorCode errorCode = exception.getErrorCode();
		return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.failure(errorCode));
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
				fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage() == null
						? ErrorCode.VALIDATION_ERROR.getMessage() : error.getDefaultMessage()));
		return handleExceptionInternal(exception,
				ApiResponse.failure(ErrorCode.VALIDATION_ERROR, fieldErrors), headers, status, request);
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException exception, HttpHeaders headers, HttpStatusCode status,
			WebRequest request) {
		ErrorCode errorCode = exception.isForReturnValue()
				? ErrorCode.INTERNAL_SERVER_ERROR : ErrorCode.VALIDATION_ERROR;
		return handleExceptionInternal(exception, ApiResponse.failure(errorCode), headers, status, request);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR));
	}

	@Override
	protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception exception, @Nullable Object body,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		if (status.is5xxServerError()) {
			log.error("Request processing failed", exception);
		}
		ErrorCode errorCode = status.is5xxServerError()
				? ErrorCode.INTERNAL_SERVER_ERROR : ErrorCode.INVALID_REQUEST;
		Object response = body instanceof ApiResponse<?> ? body : ApiResponse.failure(errorCode);
		return super.handleExceptionInternal(exception, response, headers, status, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
		log.error("Unexpected request processing failure", exception);
		ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
		return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.failure(errorCode));
	}
}
