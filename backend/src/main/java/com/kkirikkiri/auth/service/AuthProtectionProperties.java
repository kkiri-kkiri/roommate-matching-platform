package com.kkirikkiri.auth.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "auth.protection")
@Validated
@Getter
@Setter
public class AuthProtectionProperties {
    @Valid @NotNull private Requests loginRequests = new Requests(20, 120, 60);
    @Valid @NotNull private Failures loginFailures = new Failures(10, 30, 600, 60);
    @Valid @NotNull private Requests verifyRequests = new Requests(10, 60, 60);
    @Valid @NotNull private Failures verifyFailures = new Failures(5, 20, 300, 300);
    @Valid @NotNull private Requests resetRequests = new Requests(5, 20, 3600);
    @Valid @NotNull private Requests sendRequests = new Requests(5, 20, 3600);
    @Min(1) private int resetCooldownSeconds = 60;
    @Min(1) private int mailMax = 100;
    @Min(1) private int mailWindowSeconds = 3600;

    @Getter @Setter
    public static class Requests {
        @Min(1) private int emailMax;
        @Min(1) private int ipMax;
        @Min(1) private int windowSeconds;
        public Requests() { }
        public Requests(int emailMax, int ipMax, int windowSeconds) {
            this.emailMax = emailMax; this.ipMax = ipMax; this.windowSeconds = windowSeconds;
        }
    }
    @Getter @Setter
    public static class Failures {
        @Min(1) private int emailMax;
        @Min(1) private int ipMax;
        @Min(1) private int windowSeconds;
        @Min(1) private int blockSeconds;
        public Failures() { }
        public Failures(int emailMax, int ipMax, int windowSeconds, int blockSeconds) {
            this.emailMax = emailMax; this.ipMax = ipMax;
            this.windowSeconds = windowSeconds; this.blockSeconds = blockSeconds;
        }
    }
}
