package com.kkirikkiri.global.init;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.*;

class SeedDataSafetyTest {
    @Test void localGenerationMarksOnlyNewUsersAndKeepsProfileAndSurveyCreation() {
        var users=mock(UserRepository.class);var profiles=mock(UserProfileRepository.class);
        var dormitories=mock(DormitoryRepository.class);var faculties=mock(FacultyRepository.class);var majors=mock(MajorRepository.class);
        var jdbc=mock(JdbcTemplate.class);var community=mock(CommunitySeedDataService.class);var manifest=mock(SeedUserManifest.class);
        var encoder=mock(PasswordEncoder.class);
        Faculty faculty=new Faculty("컴퓨터응용수학부",true);ReflectionTestUtils.setField(faculty,"facultyId",1L);
        when(dormitories.findByDormitoryName("나래")).thenReturn(Optional.of(new Dormitory("N","나래",true)));
        when(faculties.findByFacultyName("컴퓨터응용수학부")).thenReturn(Optional.of(faculty));
        when(majors.findByFaculty_FacultyIdAndMajorName(1L,"소프트웨어융합전공")).thenReturn(Optional.of(new Major(faculty,"소프트웨어융합전공",true)));
        when(encoder.encode(anyString())).thenReturn("encoded-seed-password");
        var sequence=new java.util.concurrent.atomic.AtomicLong();
        when(users.save(any(User.class))).thenAnswer(call->{
            User user=call.getArgument(0);ReflectionTestUtils.setField(user,"userId",sequence.incrementAndGet());return user;
        });
        when(jdbc.queryForObject(anyString(),eq(Integer.class),any(Object.class))).thenReturn(0);
        when(jdbc.queryForObject(anyString(),eq(Long.class),any(Object.class))).thenReturn(1L);
        when(jdbc.queryForList(anyString(),eq(String.class),eq("profile_survey"))).thenReturn(List.of("user_id","completed"));
        var service=new SeedDataService(users,profiles,dormitories,faculties,majors,encoder,jdbc,community,manifest);
        service.createSeedData();
        verify(users,times(30)).save(any(User.class));verify(profiles,times(30)).save(any(UserProfile.class));
        verify(manifest,times(30)).registerAfterCommit(argThat(user->user.getUserId()!=null&&user.isEmailVerified()));
        verify(community,times(30)).createPosts(any(User.class),any(Dormitory.class),anyInt());
    }
    @Test void prodHasNoSeedOrFixedAccountInitializerEvenWhenDeleteIsConfigured() {
        new ApplicationContextRunner().withUserConfiguration(SeedDataInitializer.class,SeedDataService.class,
                SeedUserManifest.class,DevDataInitializer.class)
                .withPropertyValues("spring.profiles.active=prod","app.seed.mode=DELETE")
                .run(context->{assertNull(context.getStartupFailure());
                    assertFalse(context.containsBean("seedDataInitializer"));
                    assertTrue(context.getBeansOfType(SeedDataService.class).isEmpty());
                    assertTrue(context.getBeansOfType(DevDataInitializer.class).isEmpty());});
    }
    @Test void createAndDeleteNeverAdoptExistingAccountsWithoutProvenance() {
        var users=mock(UserRepository.class);var profiles=mock(UserProfileRepository.class);
        var dormitories=mock(DormitoryRepository.class); var faculties=mock(FacultyRepository.class);var majors=mock(MajorRepository.class);
        var jdbc=mock(JdbcTemplate.class);var community=mock(CommunitySeedDataService.class);var manifest=mock(SeedUserManifest.class);
        User real=new User("encoded-real-password","seed_user01@hknu.ac.kr");ReflectionTestUtils.setField(real,"userId",1L);
        Faculty faculty=new Faculty("컴퓨터응용수학부",true);ReflectionTestUtils.setField(faculty,"facultyId",1L);
        when(dormitories.findByDormitoryName("나래")).thenReturn(Optional.of(new Dormitory("N","나래",true)));
        when(faculties.findByFacultyName("컴퓨터응용수학부")).thenReturn(Optional.of(faculty));
        when(majors.findByFaculty_FacultyIdAndMajorName(1L,"소프트웨어융합전공")).thenReturn(Optional.of(new Major(faculty,"소프트웨어융합전공",true)));
        when(users.findBySchoolEmail(anyString())).thenReturn(Optional.of(real));
        when(users.findAllBySchoolEmailIn(any())).thenReturn(List.of(real));
        when(users.findByIdForAccountUpdate(1L)).thenReturn(Optional.of(real));
        var service=new SeedDataService(users,profiles,dormitories,faculties,majors,mock(PasswordEncoder.class),jdbc,community,manifest);
        service.createSeedData();service.deleteSeedData();
        verify(users,never()).save(any()); verify(users,never()).deleteAll(any());
        verifyNoInteractions(profiles,jdbc,community); verify(manifest,never()).registerAfterCommit(any());
        verify(manifest,never()).removeAfterCommit(any());
    }
}
