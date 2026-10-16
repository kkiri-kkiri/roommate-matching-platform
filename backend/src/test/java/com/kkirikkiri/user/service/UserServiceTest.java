package com.kkirikkiri.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.sql.SQLException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.*;
import com.kkirikkiri.user.dto.*;
import com.kkirikkiri.global.exception.*;

class UserServiceTest {
    UserRepository users; UserProfileRepository profiles; FacultyRepository faculties; MajorRepository majors;
    ApplicationEventPublisher events; UserService service; User user; UserProfile profile; Faculty faculty;
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    @BeforeEach void setup() {
        users=mock(UserRepository.class); profiles=mock(UserProfileRepository.class);
        faculties=mock(FacultyRepository.class); majors=mock(MajorRepository.class); events=mock(ApplicationEventPublisher.class);
        faculty=new Faculty("컴퓨터응용수학부",true); ReflectionTestUtils.setField(faculty,"facultyId",1L);
        Major major=new Major(faculty,"소프트웨어융합전공",true); ReflectionTestUtils.setField(major,"majorId",1L);
        user=new User(encoder.encode("Old1!pass"),"student@hknu.ac.kr"); ReflectionTestUtils.setField(user,"userId",1L);
        profile=new UserProfile(user,"학생하나","남자",new Dormitory("N","나래",true),faculty,major,(short)2002,"24","3학년","INTJ");
        when(users.findByIdForAccountUpdate(1L)).thenReturn(Optional.of(user));
        when(profiles.findByUser_UserId(1L)).thenReturn(Optional.of(profile));
        service=new UserService(users,profiles,mock(DormitoryRepository.class),faculties,majors,
                Validation.buildDefaultValidatorFactory().getValidator(),encoder,events);
    }
    UserUpdateRequest update(String nickname,String grade,String faculty,String major,String energy) {
        return new UserUpdateRequest(nickname,null,grade,faculty,major,energy,null,null,null);
    }
    void error(ErrorCode expected, Runnable action) { assertEquals(expected,assertThrows(CustomException.class,action::run).getErrorCode()); }

    @Test void preservesNullFieldsAndCombinesMbtiAxes() {
        var result=service.updateMyInfo(1L,update(null,null,null,null,"E"));
        assertEquals("학생하나",result.nickname()); assertEquals("ENTJ",profile.getMbti());
        assertEquals("소프트웨어융합전공",result.major()); verify(profiles).flush(); verifyNoInteractions(events);
    }
    @Test void explicitlyClearsMajorAndAllowsFacultyWithoutMajors() {
        Faculty target=new Faculty("HK자율전공학부",true); ReflectionTestUtils.setField(target,"facultyId",2L);
        when(faculties.findByFacultyName(target.getFacultyName())).thenReturn(Optional.of(target));
        assertNull(service.updateMyInfo(1L,update(null,null,target.getFacultyName(),"세부전공 없음",null)).major());
        assertSame(target,profile.getFaculty()); assertNull(profile.getMajor()); verify(events).publishEvent(any(Object.class));
    }
    @Test void omittedMajorStillPreservesAndRejectsWrongAffiliation() {
        Faculty target=new Faculty("HK자율전공학부",true); ReflectionTestUtils.setField(target,"facultyId",2L);
        when(faculties.findByFacultyName(target.getFacultyName())).thenReturn(Optional.of(target));
        error(ErrorCode.INVALID_REQUEST,()->service.updateMyInfo(1L,update(null,null,target.getFacultyName(),null,null)));
        assertSame(faculty,profile.getFaculty()); verify(profiles,never()).flush();
    }
    @Test void rejectsInactiveAccountUnderLock() {
        user.changeStatus(UserStatus.SUSPENDED);
        error(ErrorCode.UNAUTHORIZED,()->service.changePassword(1L,new PasswordChangeRequest("Old1!pass","New1!pass","New1!pass")));
        error(ErrorCode.UNAUTHORIZED,()->service.updateMyInfo(1L,update("학생둘",null,null,null,null)));
        verify(profiles,never()).findByUser_UserId(anyLong());
    }
    @Test void changesPasswordOnlyAfterMatchingLockedCurrentPassword() {
        error(ErrorCode.INVALID_REQUEST,()->service.changePassword(1L,new PasswordChangeRequest("Wrong1!pass","New1!pass","New1!pass")));
        service.changePassword(1L,new PasswordChangeRequest("Old1!pass","New1!pass","New1!pass"));
        assertTrue(encoder.matches("New1!pass",user.getPassword())); verify(users,times(2)).findByIdForAccountUpdate(1L);
    }
    @Test void rejectsByteOverflowBeforeRepositoryAccess() {
        String value="Aa1!"+"가".repeat(24);
        error(ErrorCode.VALIDATION_ERROR,()->service.changePassword(1L,new PasswordChangeRequest("Old1!pass",value,value)));
        verify(users,never()).findByIdForAccountUpdate(anyLong());
    }
    @Test void handlesConcurrentNicknameUniqueViolationWithoutPublishingRefresh() {
        doThrow(new DataIntegrityViolationException("conflict",new SQLException("duplicate","23000",1062))).when(profiles).flush();
        error(ErrorCode.INVALID_REQUEST,()->service.updateMyInfo(1L,update("학생둘","4학년",null,null,null)));
        verifyNoInteractions(events);
    }
    @Test void doesNotMisreportOtherIntegrityFailuresAsDuplicateNickname() {
        var failure=new DataIntegrityViolationException("not null",new SQLException("missing","23502"));
        doThrow(failure).when(profiles).flush();
        assertSame(failure,assertThrows(DataIntegrityViolationException.class,()->service.updateMyInfo(1L,update("학생둘",null,null,null,null))));
    }
}
