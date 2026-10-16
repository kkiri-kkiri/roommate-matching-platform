package com.kkirikkiri.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Database equality, not Java Unicode folding, chooses a pre-account rate-limit subject. */
@Entity
@Table(name = "AUTH_EMAIL_IDENTITY")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthEmailIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "identity_id")
    private Long identityId;

    // Manual MySQL DDL copies each source column's charset/collation independently.
    @Column(name = "account_email", length = 100, unique = true)
    private String accountEmail;

    @Column(name = "verification_email", length = 100, unique = true)
    private String verificationEmail;
}
