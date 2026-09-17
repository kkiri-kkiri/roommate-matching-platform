package com.kkirikkiri.auth.dto;

public record EmailVerificationVerifyResponse(boolean verified, String verificationToken) {
}
