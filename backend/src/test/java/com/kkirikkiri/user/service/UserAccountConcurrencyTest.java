package com.kkirikkiri.user.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.UserRepository;
import com.kkirikkiri.user.repository.UserProfileRepository;
import com.kkirikkiri.user.repository.DormitoryRepository;
import com.kkirikkiri.user.repository.FacultyRepository;
import com.kkirikkiri.user.dto.UserUpdateRequest;
import com.kkirikkiri.user.dto.PasswordChangeRequest;
import com.kkirikkiri.admin.dto.AdminMemberStatusUpdateRequest;
import com.kkirikkiri.admin.service.AdminService;
import com.kkirikkiri.auth.service.PasswordResetService;
import com.kkirikkiri.auth.dto.PasswordResetConfirmRequest;
import com.kkirikkiri.auth.entity.PasswordResetToken;
import com.kkirikkiri.auth.repository.PasswordResetTokenRepository;
import com.kkirikkiri.global.exception.*;

@SpringBootTest
@ActiveProfiles("test")
class UserAccountConcurrencyTest {
    @Autowired UserService passwords;
    @Autowired PasswordResetService resets;
    @Autowired AdminService admins;
    @Autowired UserRepository users;
    @Autowired UserProfileRepository profiles;
    @Autowired DormitoryRepository dormitories;
    @Autowired FacultyRepository faculties;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PlatformTransactionManager manager;
    @Autowired PasswordEncoder encoder;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager entityManager;
    Long adminId, userId, tokenId;
    String token;
    Long otherId, dormitoryId, facultyId;
    ExecutorService workers;
    TransactionTemplate tx;

    @BeforeEach void setup() throws Exception {
        workers=Executors.newFixedThreadPool(2); tx=new TransactionTemplate(manager);
        token=UUID.randomUUID().toString();
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        tx.executeWithoutResult(status -> {
            User admin=users.saveAndFlush(new User(encoder.encode("Admin1!pass"),UUID.randomUUID()+"@hknu.ac.kr",UserRole.ADMIN));
            User user=users.saveAndFlush(new User(encoder.encode("Old1!pass"),UUID.randomUUID()+"@hknu.ac.kr"));
            adminId=admin.getUserId(); userId=user.getUserId();
            tokenId=tokens.saveAndFlush(new PasswordResetToken(user,hash,LocalDateTime.now().plusMinutes(5))).getPasswordResetTokenId();
        });
    }
    @AfterEach void cleanup() {
        workers.shutdownNow();
        tx.executeWithoutResult(status -> {
            tokens.deleteById(tokenId);
            profiles.findByUser_UserId(userId).ifPresent(profiles::delete);
            if(otherId!=null) profiles.findByUser_UserId(otherId).ifPresent(profiles::delete);
            profiles.flush();
            users.deleteById(userId); users.deleteById(adminId);
            if(otherId!=null) users.deleteById(otherId);
            users.flush();
            if(dormitoryId!=null) dormitories.deleteById(dormitoryId);
            if(facultyId!=null) faculties.deleteById(facultyId);
        });
    }
    void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5,TimeUnit.SECONDS),"worker coordination timed out"); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
    void password(boolean reset) {
        if(reset) resets.resetPassword(new PasswordResetConfirmRequest(token,"New1!pass","New1!pass"));
        else passwords.changePassword(userId,new PasswordChangeRequest("Old1!pass","New1!pass","New1!pass"));
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void passwordChangeCommitsBeforeSuspensionWithoutLosingEitherChange(boolean reset) throws Exception {
        CountDownLatch locked=new CountDownLatch(1), release=new CountDownLatch(1), adminStarted=new CountDownLatch(1);
        Future<?> change=workers.submit(()->tx.executeWithoutResult(status->{
            users.findByIdForAccountUpdate(userId).orElseThrow(); locked.countDown(); await(release); password(reset);
        }));
        await(locked);
        Future<?> suspend=workers.submit(()->{adminStarted.countDown(); admins.updateMemberStatus(adminId,userId,new AdminMemberStatusUpdateRequest("SUSPENDED"));});
        await(adminStarted);
        try { assertThrows(TimeoutException.class,()->suspend.get(150,TimeUnit.MILLISECONDS)); }
        finally { release.countDown(); }
        change.get(10,TimeUnit.SECONDS); suspend.get(10,TimeUnit.SECONDS);
        User stored=users.findById(userId).orElseThrow();
        assertEquals(UserStatus.SUSPENDED,stored.getStatus()); assertTrue(encoder.matches("New1!pass",stored.getPassword()));
        assertEquals(reset,tokens.findById(tokenId).orElseThrow().isUsed());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void suspensionCommitsFirstAndRejectsWaitingPasswordChange(boolean reset) throws Exception {
        CountDownLatch locked=new CountDownLatch(1), release=new CountDownLatch(1), changeStarted=new CountDownLatch(1);
        Future<?> suspend=workers.submit(()->tx.executeWithoutResult(status->{
            admins.updateMemberStatus(adminId,userId,new AdminMemberStatusUpdateRequest("SUSPENDED"));
            locked.countDown(); await(release);
        }));
        await(locked);
        Future<?> change=workers.submit(()->{changeStarted.countDown(); password(reset);});
        await(changeStarted);
        try { assertThrows(TimeoutException.class,()->change.get(150,TimeUnit.MILLISECONDS)); }
        finally { release.countDown(); }
        suspend.get(10,TimeUnit.SECONDS);
        ExecutionException failure=assertThrows(ExecutionException.class,()->change.get(10,TimeUnit.SECONDS));
        assertInstanceOf(CustomException.class,failure.getCause());
        assertEquals(reset?ErrorCode.PASSWORD_RESET_ACCOUNT_INACTIVE:ErrorCode.UNAUTHORIZED,
                ((CustomException)failure.getCause()).getErrorCode());
        User stored=users.findById(userId).orElseThrow();
        assertEquals(UserStatus.SUSPENDED,stored.getStatus()); assertTrue(encoder.matches("Old1!pass",stored.getPassword()));
        assertFalse(tokens.findById(tokenId).orElseThrow().isUsed());
    }
    @Test void rechecksManagedUserAfterAnotherTransactionChangesStatus() throws Exception {
        tx.executeWithoutResult(status->{
            User stale=users.findById(userId).orElseThrow(); assertEquals(UserStatus.ACTIVE,stale.getStatus());
            try { workers.submit(()->admins.updateMemberStatus(adminId,userId,new AdminMemberStatusUpdateRequest("SUSPENDED"))).get(5,TimeUnit.SECONDS); }
            catch(Exception failure){throw new AssertionError(failure);}
            assertEquals(UserStatus.SUSPENDED,users.findByIdForAccountUpdate(userId).orElseThrow().getStatus());
        });
    }
    @Test void currentPasswordIsRecheckedAfterWaitingForAnotherPasswordChange() throws Exception {
        CountDownLatch locked=new CountDownLatch(1),release=new CountDownLatch(1);
        Future<?> first=workers.submit(()->tx.executeWithoutResult(status->{
            users.findByIdForAccountUpdate(userId).orElseThrow(); locked.countDown(); await(release); password(false);
        }));
        await(locked);
        Future<?> second=workers.submit(()->password(false)); release.countDown(); first.get(10,TimeUnit.SECONDS);
        ExecutionException failure=assertThrows(ExecutionException.class,()->second.get(10,TimeUnit.SECONDS));
        assertEquals(ErrorCode.INVALID_REQUEST,((CustomException)failure.getCause()).getErrorCode());
        assertTrue(encoder.matches("New1!pass",users.findById(userId).orElseThrow().getPassword()));
    }

    @Test void repeatedAccountLockRetainsManagedIdentityAndPendingChanges() {
        tx.executeWithoutResult(status->{
            User locked=users.findByIdForAccountUpdate(userId).orElseThrow();
            locked.changePassword(encoder.encode("New1!pass"));
            locked.changeStatus(UserStatus.SUSPENDED);
            User repeated=users.findByIdForAccountUpdate(userId).orElseThrow();
            assertSame(locked,repeated);
            assertTrue(entityManager.contains(repeated));
            assertTrue(encoder.matches("New1!pass",repeated.getPassword()));
            assertEquals(UserStatus.SUSPENDED,repeated.getStatus());
        });
        User stored=users.findById(userId).orElseThrow();
        assertTrue(encoder.matches("New1!pass",stored.getPassword()));
        assertEquals(UserStatus.SUSPENDED,stored.getStatus());
    }

    @Test void accountReloadPreservesOtherManagedChangesAndRollbackBoundary() {
        tx.executeWithoutResult(status->{
            User admin=users.findById(adminId).orElseThrow();
            admin.changePassword(encoder.encode("New1!pass"));
            User locked=users.findByIdForAccountUpdate(userId).orElseThrow();
            locked.changePassword(encoder.encode("New1!pass"));
            assertSame(locked,users.findByIdForAccountUpdate(userId).orElseThrow());
            assertSame(admin,users.findById(adminId).orElseThrow());
            assertTrue(encoder.matches("New1!pass",admin.getPassword()));
            status.setRollbackOnly();
        });
        assertTrue(encoder.matches("Old1!pass",users.findById(userId).orElseThrow().getPassword()));
        assertTrue(encoder.matches("Admin1!pass",users.findById(adminId).orElseThrow().getPassword()));
    }

    @Test void competingNicknameWriteReturnsBusinessErrorAndRollsBackAllLosingChanges() throws Exception {
        tx.executeWithoutResult(status->{
            String suffix=UUID.randomUUID().toString().substring(0,8);
            Dormitory d=dormitories.saveAndFlush(new Dormitory(suffix,suffix,true)); dormitoryId=d.getDormitoryId();
            Faculty f=faculties.saveAndFlush(new Faculty(suffix,true)); facultyId=f.getFacultyId();
            User a=users.findById(userId).orElseThrow();
            User b=users.saveAndFlush(new User(encoder.encode("Old1!pass"),UUID.randomUUID()+"@hknu.ac.kr")); otherId=b.getUserId();
            profiles.saveAndFlush(new UserProfile(a,"A"+suffix.substring(0,7),"남자",d,f,null,(short)2002,"24","3학년","INTJ"));
            profiles.saveAndFlush(new UserProfile(b,"B"+suffix.substring(0,7),"남자",d,f,null,(short)2002,"24","3학년","INTJ"));
        });
        String nickname="N"+UUID.randomUUID().toString().substring(0,7);
        var request=new UserUpdateRequest(nickname,null,"4학년",null,null,null,null,null,null);
        CountDownLatch flushed=new CountDownLatch(1),release=new CountDownLatch(1),secondStarted=new CountDownLatch(1);
        Future<?> first=workers.submit(()->tx.executeWithoutResult(status->{
            passwords.updateMyInfo(userId,request); flushed.countDown(); await(release);
        }));
        await(flushed);
        Future<?> second=workers.submit(()->{secondStarted.countDown(); passwords.updateMyInfo(otherId,request);});
        await(secondStarted);
        try { assertThrows(TimeoutException.class,()->second.get(150,TimeUnit.MILLISECONDS)); }
        finally { release.countDown(); }
        first.get(10,TimeUnit.SECONDS);
        ExecutionException failure=assertThrows(ExecutionException.class,()->second.get(10,TimeUnit.SECONDS));
        assertInstanceOf(CustomException.class,failure.getCause());
        assertEquals(ErrorCode.INVALID_REQUEST,((CustomException)failure.getCause()).getErrorCode());
        tx.executeWithoutResult(status->{
            UserProfile winner=profiles.findByUser_UserId(userId).orElseThrow(), loser=profiles.findByUser_UserId(otherId).orElseThrow();
            assertEquals(nickname,winner.getNickname());assertEquals("4학년",winner.getGrade());
            assertNotEquals(nickname,loser.getNickname());assertEquals("3학년",loser.getGrade());
        });
    }
}
