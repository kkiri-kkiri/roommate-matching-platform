package com.kkirikkiri.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.CannotAcquireLockException;
import com.kkirikkiri.global.exception.GlobalExceptionHandler;

class ConcurrentUpdateResponseTest {
    @RestController static class Probe {
        @GetMapping("/lock") Object lock() { throw new CannotAcquireLockException("internal SQL details"); }
        @GetMapping("/timeout") Object timeout() { throw new jakarta.persistence.LockTimeoutException("internal SQL details"); }
    }
    @Test void lockFailuresReturnRetryableConflictWithoutDatabaseDetails() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new GlobalExceptionHandler()).build();
        for(String path:new String[]{"/lock","/timeout"}) {
            mvc.perform(get(path)).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("ACCOUNT_UPDATE_CONFLICT"))
                    .andExpect(jsonPath("$.message").value("다른 변경 요청을 처리 중입니다. 잠시 후 다시 시도해 주세요."));
        }
    }
}
