package com.kkirikkiri.global.init;

import com.kkirikkiri.user.entity.Dormitory;
import com.kkirikkiri.user.entity.Faculty;
import com.kkirikkiri.user.entity.Major;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.user.entity.UserProfile;
import com.kkirikkiri.user.entity.UserRole;
import com.kkirikkiri.user.repository.DormitoryRepository;
import com.kkirikkiri.user.repository.FacultyRepository;
import com.kkirikkiri.user.repository.MajorRepository;
import com.kkirikkiri.user.repository.UserProfileRepository;
import com.kkirikkiri.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final DormitoryRepository dormitoryRepository;
    private final FacultyRepository facultyRepository;
    private final MajorRepository majorRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Common reference data is loaded by data-common.sql before this runner executes.

        Dormitory dormitory = dormitoryRepository.findByDormitoryName("나래").orElseThrow();
        Faculty faculty = facultyRepository.findByFacultyName("컴퓨터응용수학부").orElseThrow();
        Major major = majorRepository.findByFaculty_FacultyIdAndMajorName(
                faculty.getFacultyId(), "소프트웨어융합전공").orElseThrow();

        createUserIfNotExists(
                "test1234!",
                "user01@hknu.ac.kr",
                UserRole.USER,
                "일반회원",
                dormitory,
                faculty,
                major
        );

        createUserIfNotExists(
                "test1234!",
                "admin01@hknu.ac.kr",
                UserRole.ADMIN,
                "관리자",
                dormitory,
                faculty,
                major
        );
    }


    private void createUserIfNotExists(
            String rawPassword,
            String schoolEmail,
            UserRole role,
            String nickname,
            Dormitory dormitory,
            Faculty faculty,
            Major major
    ) {
        if (userRepository.existsBySchoolEmail(schoolEmail)) {
            return;
        }

        String encodedPassword = passwordEncoder.encode(rawPassword);

        User user = new User(
                encodedPassword,
                schoolEmail,
                role
        );

        user.verifyEmail();
        userRepository.save(user);

        UserProfile profile = new UserProfile(
                user,
                nickname,
                "남자",
                dormitory,
                faculty,
                major,
                (short) 2002,
                "24",
                "3학년",
                "INTJ"
        );

        userProfileRepository.save(profile);
    }
}
