package com.kkirikkiri.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.auth.dto.*;
import com.kkirikkiri.auth.entity.EmailVerificationPurpose;
import com.kkirikkiri.auth.repository.*;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.global.security.JwtTokenProvider;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.user.repository.UserRepository;
import com.kkirikkiri.user.repository.DormitoryRepository;
import com.kkirikkiri.user.repository.FacultyRepository;
import com.kkirikkiri.user.repository.UserProfileRepository;

@SpringBootTest(properties="auth.email-verification.mail-from=test-only@example.invalid")
@ActiveProfiles("test")
class AuthFlowIntegrationTest {
    @Autowired EmailVerificationService verification;
    @Autowired PasswordResetService resets;
    @Autowired AuthService auth;
    @Autowired SignupService signup;
    @Autowired DormitoryRepository dormitories;
    @Autowired FacultyRepository faculties;
    @Autowired UserProfileRepository profiles;
    @Autowired UserRepository users;
    @Autowired EmailVerificationRepository codes;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenProvider jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean JavaMailSender smtp;
    List<SimpleMailMessage> messages;
    String email;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM auth_limit_bucket");
        email=UUID.randomUUID()+"@hknu.ac.kr";
        messages=new CopyOnWriteArrayList<>();
        doAnswer(call -> { messages.add(new SimpleMailMessage(call.getArgument(0))); return null; })
            .when(smtp).send(any(SimpleMailMessage.class));
    }
    String textValue(String prefix) {
        return messages.getLast().getText().lines().filter(line->line.startsWith(prefix))
            .findFirst().orElseThrow().substring(prefix.length());
    }
    @Test void sendsVerifiesAndConsumesTokenWithRollbackAndReuseProtection() {
        assertEquals(300,verification.send(new EmailVerificationSendRequest(email,"SIGNUP"),"127.0.0.1").expiresInSeconds());
        String code=textValue("인증번호: ");
        assertEquals(6,code.length());
        var result=verification.verify(new EmailVerificationVerifyRequest(email,code,"SIGNUP"),"127.0.0.1");
        assertTrue(result.verified());
        var tx=new TransactionTemplate(manager);
        tx.executeWithoutResult(status->{ verification.consumeToken(email,EmailVerificationPurpose.SIGNUP,result.verificationToken()); status.setRollbackOnly(); });
        tx.executeWithoutResult(status->verification.consumeToken(email,EmailVerificationPurpose.SIGNUP,result.verificationToken()));
        assertCode(ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID,()->tx.executeWithoutResult(status->verification.consumeToken(email,EmailVerificationPurpose.SIGNUP,result.verificationToken())));
        assertCode(ErrorCode.EMAIL_VERIFICATION_ALREADY_USED,()->verification.verify(new EmailVerificationVerifyRequest(email,code,"SIGNUP"),"127.0.0.1"));
        var stored=codes.findAll().stream().filter(row->row.getEmail().equals(email)).findFirst().orElseThrow();
        assertNotEquals(code,stored.getVerificationCode()); assertNotEquals(result.verificationToken(),stored.getVerificationTokenHash());
    }
    @Test void invalidCodeFailuresPersistAfterBusinessRollbackAndBlockFurtherAttempts() {
        verification.send(new EmailVerificationSendRequest(email,"SIGNUP"),"127.0.0.1");
        String wrong=textValue("인증번호: ").equals("000000")?"000001":"000000";
        for(int i=0;i<5;i++) assertCode(ErrorCode.EMAIL_VERIFICATION_INVALID,
            ()->verification.verify(new EmailVerificationVerifyRequest(email,wrong,"SIGNUP"),"127.0.0.1"));
        assertThrows(AuthRateLimitException.class,()->verification.verify(new EmailVerificationVerifyRequest(email,textValue("인증번호: "),"SIGNUP"),"127.0.0.2"));
        assertEquals(5,jdbc.queryForObject("SELECT MAX(failure_count) FROM auth_limit_bucket",Integer.class));
    }
    @Test void profilelessAdminLogsInAndIssuesValidJwtAndUniformCredentialErrors() {
        User admin=users.saveAndFlush(new User(encoder.encode("Admin1!pass"),email,UserRole.ADMIN));
        var result=auth.login(new LoginRequest(email,"Admin1!pass"),"127.0.0.1");
        assertEquals(email,result.user().nickname()); assertEquals("ADMIN",result.user().role());
        assertEquals(admin.getUserId(),jwt.getUserId(result.accessToken()));
        assertEquals("ADMIN",jwt.getRole(result.accessToken()));
        assertEquals(result.user(),auth.getAuthenticatedUser(admin.getUserId()));
        assertCode(ErrorCode.UNAUTHORIZED,()->auth.login(new LoginRequest(email,"Wrong1!pass"),"127.0.0.1"));
        assertCode(ErrorCode.UNAUTHORIZED,()->auth.login(new LoginRequest("missing@hknu.ac.kr","Wrong1!pass"),"127.0.0.1"));
    }
    @Test void repeatedLoginFailuresReturn401Then429() {
        for(int i=0;i<10;i++) assertCode(ErrorCode.UNAUTHORIZED,()->auth.login(new LoginRequest(email,"Wrong1!pass"),"127.0.0.1"));
        assertThrows(AuthRateLimitException.class,()->auth.login(new LoginRequest(email,"Wrong1!pass"),"127.0.0.2"));
    }
    @Test void concurrentResetRequestsSendOnceAndTokenCanOnlyBeUsedOnce() throws Exception {
        User user=users.saveAndFlush(new User(encoder.encode("Old1!pass"),email));
        var outcomes=concurrent(8,()->resets.requestPasswordReset(new PasswordResetRequest(email),"127.0.0.1"));
        assertEquals(1,outcomes.stream().filter(value->value==null).count());
        assertEquals(7,outcomes.stream().filter(value->value instanceof AuthRateLimitException).count());
        assertEquals(1,messages.size());
        assertEquals(1,tokens.findAll().stream().filter(t->t.getUser().getUserId().equals(user.getUserId())).count());
        String token=textValue("비밀번호 재설정 토큰: ");
        var confirms=concurrent(2,()->{ resets.resetPassword(new PasswordResetConfirmRequest(token,"New1!pass","New1!pass")); return null; });
        assertEquals(1,confirms.stream().filter(value->value==null).count());
        assertEquals(ErrorCode.PASSWORD_RESET_TOKEN_ALREADY_USED,((CustomException)confirms.stream().filter(value->value!=null).findFirst().orElseThrow()).getErrorCode());
        assertTrue(encoder.matches("New1!pass",users.findById(user.getUserId()).orElseThrow().getPassword()));
    }
    @Test void smtpFailureRollsBackIssuanceButKeepsCooldownAndQuota() {
        doThrow(new MailSendException("sensitive-mail-body")).when(smtp).send(any(SimpleMailMessage.class));
        assertCode(ErrorCode.INTERNAL_SERVER_ERROR,()->verification.send(new EmailVerificationSendRequest(email,"SIGNUP"),"127.0.0.1"));
        assertTrue(codes.findAll().stream().noneMatch(c->c.getEmail().equals(email)));
        assertThrows(AuthRateLimitException.class,()->verification.send(new EmailVerificationSendRequest(email,"SIGNUP"),"127.0.0.1"));
        String resetEmail="reset"+email;
        User user=users.saveAndFlush(new User(encoder.encode("Old1!pass"),resetEmail));
        assertCode(ErrorCode.INTERNAL_SERVER_ERROR,()->resets.requestPasswordReset(new PasswordResetRequest(resetEmail),"127.0.0.2"));
        assertTrue(tokens.findAll().stream().noneMatch(t->t.getUser().getUserId().equals(user.getUserId())));
        assertThrows(AuthRateLimitException.class,()->resets.requestPasswordReset(new PasswordResetRequest(resetEmail),"127.0.0.2"));
    }
    @Test void unknownResetAccountReturnsExistingSuccessContractAndSameCooldown() {
        assertTrue(resets.requestPasswordReset(new PasswordResetRequest(email),"127.0.0.1").sent());
        verifyNoInteractions(smtp);
        assertThrows(AuthRateLimitException.class,()->resets.requestPasswordReset(new PasswordResetRequest(email),"127.0.0.2"));
    }
    @Test void verifiedEmailSignsUpNormallyAndTokenConsumptionIsCommittedWithAccount() {
        String suffix=UUID.randomUUID().toString().substring(0,8);
        var dormitory=dormitories.saveAndFlush(new Dormitory("D"+suffix,"D"+suffix,true));
        var faculty=faculties.saveAndFlush(new Faculty("F"+suffix,true));
        verification.send(new EmailVerificationSendRequest(email,"SIGNUP"),"127.0.0.1");
        var verified=verification.verify(new EmailVerificationVerifyRequest(email,textValue("인증번호: "),"SIGNUP"),"127.0.0.1");
        var result=signup.signup(new SignupRequest("Password1!","Password1!",suffix,email,verified.verificationToken(),
            "남자",dormitory.getDormitoryName(),faculty.getFacultyName(),"세부전공 없음",(short)2002,"24","3학년","I","N","T","J"));
        assertTrue(users.findById(result.userId()).orElseThrow().isEmailVerified());
        assertNull(profiles.findByUser_UserId(result.userId()).orElseThrow().getMajor());
        assertEquals(suffix,auth.login(new LoginRequest(email,"Password1!"),"127.0.0.1").user().nickname());
        assertCode(ErrorCode.EMAIL_VERIFICATION_TOKEN_INVALID,()->new TransactionTemplate(manager).executeWithoutResult(tx->
            verification.consumeToken(email,EmailVerificationPurpose.SIGNUP,verified.verificationToken())));
    }
    @Test void concurrentVerificationSendAndValidCodeUseEachHaveOneWinner() throws Exception {
        var sent=concurrent(8,()->verification.send(new EmailVerificationSendRequest(email,"SIGNUP"),"127.0.0.1"));
        assertEquals(1,sent.stream().filter(value->value==null).count());
        assertEquals(7,sent.stream().filter(value->value instanceof AuthRateLimitException).count());
        assertEquals(1,messages.size());
        String code=textValue("인증번호: ");
        var verified=concurrent(2,()->verification.verify(new EmailVerificationVerifyRequest(email,code,"SIGNUP"),"127.0.0.1"));
        assertEquals(1,verified.stream().filter(value->value==null).count());
        assertEquals(ErrorCode.EMAIL_VERIFICATION_ALREADY_USED,
            ((CustomException)verified.stream().filter(value->value!=null).findFirst().orElseThrow()).getErrorCode());
    }
    void assertCode(ErrorCode code, Runnable action) { assertEquals(code,assertThrows(CustomException.class,action::run).getErrorCode()); }
    List<Throwable> concurrent(int count,Callable<?> action) throws Exception {
        var pool=Executors.newFixedThreadPool(count); var start=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<Throwable>>();
            for(int i=0;i<count;i++) futures.add(pool.submit(()->{ start.await(); try { action.call(); return null; } catch(Throwable e) { return e; } }));
            start.countDown(); var result=new ArrayList<Throwable>();
            for(var future:futures) result.add(future.get(20,TimeUnit.SECONDS));
            return result;
        } finally { pool.shutdownNow(); }
    }
}
