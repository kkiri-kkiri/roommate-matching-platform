package com.kkirikkiri.global.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

@Component
@Profile("local & !prod & !test")
@RequiredArgsConstructor
@Slf4j
public class SeedDataInitializer implements ApplicationRunner {

    private final SeedDataService seedDataService;

    @Value("${app.seed.mode:NONE}")
    private String seedMode;

    @Override
    public void run(ApplicationArguments args) {

        SeedMode mode;

        try {
            mode = SeedMode.valueOf(seedMode.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "잘못된 Seed mode입니다: " + seedMode
                            + " (NONE, CREATE, DELETE 중 하나를 사용하세요.)"
            );
        }

        switch (mode) {

            case CREATE -> {
                log.info("Seed 데이터 생성을 시작합니다.");
                seedDataService.createSeedData();
                log.info("Seed 데이터 생성이 완료되었습니다.");
            }

            case DELETE -> {
                log.info("Seed 데이터 삭제를 시작합니다.");
                seedDataService.deleteSeedData();
                log.info("Seed 데이터 삭제가 완료되었습니다.");
            }

            case NONE -> log.info("Seed 데이터 작업을 실행하지 않습니다.");
        }
    }
}
