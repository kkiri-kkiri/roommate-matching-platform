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

import java.util.Map;

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
        seedReferenceData();

        Dormitory dormitory = dormitoryRepository.findByDormitoryName("나래").orElseThrow();
        Faculty faculty = facultyRepository.findByFacultyName("컴퓨터응용수학부").orElseThrow();
        Major major = majorRepository.findByFaculty_FacultyIdAndMajorName(
                faculty.getFacultyId(), "소프트웨어융합전공").orElseThrow();

        createUserIfNotExists(
                "user01",
                "test1234!",
                "user01@hknu.ac.kr",
                UserRole.USER,
                "일반회원",
                dormitory,
                faculty,
                major
        );

        createUserIfNotExists(
                "admin01",
                "test1234!",
                "admin01@hknu.ac.kr",
                UserRole.ADMIN,
                "관리자",
                dormitory,
                faculty,
                major
        );
    }

        private void seedReferenceData() {
                Map.of("나래", "NARAE", "호연", "HOYEON", "비봉", "BIBONG", "창조", "CHANGJO")
                                .forEach((name, code) -> dormitoryRepository.findByDormitoryName(name)
                                                .orElseGet(() -> dormitoryRepository.save(new Dormitory(code, name, true))));

                Map.ofEntries(
                        Map.entry("인문융합공공인재학부", new String[]{"문예창작미디어콘텐츠홍보전공", "영미언어문화전공", "행정학전공", "노동복지전공"}),
                                Map.entry("컴퓨터응용수학부", new String[]{"소프트웨어&서비스컴퓨팅전공", "소프트웨어융합전공", "정보보안전공", "응용수학전공"}),
                                Map.entry("법경영학부", new String[]{"법학전공", "경영학전공"}),
                        Map.entry("웰니스산업융합학부", new String[]{"의류산업학전공", "복지상담학전공", "식품영양학전공"}),
                        Map.entry("식물자원조경학부", new String[]{"식물생명환경전공", "조경학전공"}),
                        Map.entry("동물생명융합학부", new String[]{"동물생명과학전공", "동물응용과학전공"}),
                        Map.entry("생명공학부", new String[]{"원예생명공학전공", "응용생명공학전공"}),
                        Map.entry("건설환경공학부", new String[]{"토목공학전공", "환경공학전공"}),
                        Map.entry("사회안전시스템공학부", new String[]{"안전공학전공", "지역자원시스템공학전공"}),
                        Map.entry("식품생명화학공학부", new String[]{"식품생명공학전공", "화학공학전공"}),
                        Map.entry("ICT로봇기계공학부", new String[]{"ICT로봇공학전공", "기계공학전공"}),
                        Map.entry("전자전기공학부", new String[]{"전자공학전공", "전기공학전공"}),
                        Map.entry("건축융합학부", new String[]{"건축학전공(5년제)", "건축공학전공"}),
                        Map.entry("디자인예술스포츠학부", new String[]{"스포츠과학전공", "시각미디어디자인전공", "제품공간디자인전공", "실용음악전공", "귀금속보석공예전공"}),
                        Map.entry("복지융합학부", new String[]{"사회복지학전공", "한국수어교육전공", "유아특수보육학전공", "의료재활공학전공"}),
                        Map.entry("사회통합학부", new String[]{"공공행정전공", "상담심리교육전공", "특수체육학전공"}),
                        Map.entry("AI반도체융합학부", new String[]{"AI반도체융합전공"}),
                        Map.entry("HK자율전공학부", new String[]{}),
                        Map.entry("글로벌창의인재학부", new String[]{"글로벌스포츠매니지먼트전공", "글로벌경영전공", "글로벌인공지능IT전공", "식물스마트팜전공", "식물생의학전공", "바이오에너지전공", "아시아문화경영전공", "Bio-Mobility전공", "English Language, Literature and Language Technology전공"}),
                        Map.entry("미래융합학부", new String[]{"노동복지전공", "행정관리전공", "레저스포츠매니지먼트전공", "융합레포츠전공"}),
                        Map.entry("AI융합학부", new String[]{"AI스마트팜융합전공(융합전공)", "스마트헬스케어전공(융합전공)", "AI빅데이터융합전공(융합전공)", "공공헬스케어IT융합전공(융합전공)", "반도체융합전공(융합전공)", "스마트애그리푸드시스템전공(융합전공)", "탄소중립바이오시스템융합전공(융합전공)"})
                ).forEach(this::saveFacultyAndMajors);
        }

        private void saveFacultyAndMajors(String facultyName, String[] majorNames) {
                Faculty faculty = facultyRepository.findByFacultyName(facultyName)
                                .orElseGet(() -> facultyRepository.save(new Faculty(facultyName, true)));
                for (String majorName : majorNames) {
                        if (majorRepository.findByFaculty_FacultyIdAndMajorName(faculty.getFacultyId(), majorName).isEmpty()) {
                                majorRepository.save(new Major(faculty, majorName, true));
                        }
                }
        }

    private void createUserIfNotExists(
            String loginId,
            String rawPassword,
            String schoolEmail,
            UserRole role,
            String nickname,
            Dormitory dormitory,
            Faculty faculty,
            Major major
    ) {
        if (userRepository.existsByLoginId(loginId)) {
            return;
        }

        String encodedPassword = passwordEncoder.encode(rawPassword);

        User user = new User(
                loginId,
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