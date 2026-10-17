package com.kkirikkiri.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kkirikkiri.chat.entity.*;
import com.kkirikkiri.chat.dto.ChatRoomSettingsRequest;
import com.kkirikkiri.global.exception.*;
import com.kkirikkiri.report.dto.ReportCreateRequest;
import com.kkirikkiri.report.service.ReportService;
import com.kkirikkiri.user.entity.*;

/** Real independent transactions; no test-level transaction or memory mutex. MySQL tested separately. */
@SpringBootTest
class SharedAccountConcurrencyIntegrationTest {
    @Autowired ChatService chat;
    @Autowired ReportService reports;
    @Autowired PlatformTransactionManager manager;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean(name="matchingRefreshExecutor") Executor refreshExecutor;
    long owner;
    final List<Long> people=new ArrayList<>(),rooms=new ArrayList<>();
    TransactionTemplate tx;
    @BeforeEach void setup(){
        tx=new TransactionTemplate(manager);
        tx.executeWithoutResult(s->{for(int i=0;i<4;i++){var user=new User("test-hash",UUID.randomUUID()+"@hknu.ac.kr");em.persist(user);people.add(user.getUserId());}owner=people.getFirst();for(int i=1;i<4;i++){var room=new ChatRoom(owner,people.get(i),ChatRoomCategory.ROOMMATE,null,null);em.persist(room);rooms.add(room.getChatRoomId());em.persist(new ChatRoomMember(room,em.getReference(User.class,owner)));em.persist(new ChatRoomMember(room,em.getReference(User.class,people.get(i))));}});
    }
    @AfterEach void cleanup(){tx.executeWithoutResult(s->{jdbc.update("DELETE FROM report WHERE reporter_id=?",owner);for(long id:rooms){jdbc.update("DELETE FROM chat_room_member WHERE chat_room_id=?",id);jdbc.update("DELETE FROM chat_room WHERE chat_room_id=?",id);}for(long id:people)jdbc.update("DELETE FROM `user` WHERE user_id=?",id);});}
    List<ErrorCode> race(int count,IntConsumer action)throws Exception{
        var start=new CountDownLatch(1);try(var workers=Executors.newVirtualThreadPerTaskExecutor()){var jobs=new ArrayList<Future<ErrorCode>>();for(int i=0;i<count;i++){int index=i;jobs.add(workers.submit(()->{start.await();try{action.accept(index);return null;}catch(CustomException e){return e.getErrorCode();}}));}start.countDown();var result=new ArrayList<ErrorCode>();for(var job:jobs)result.add(job.get(15,TimeUnit.SECONDS));return result;}
    }
    @Test void concurrentPinsRespectCapAndRejectedMuteRollsBack()throws Exception{
        var result=race(3,i->chat.updateSettings(owner,rooms.get(i),new ChatRoomSettingsRequest(true,true)));
        assertEquals(2,result.stream().filter(Objects::isNull).count());assertEquals(1,result.stream().filter(ErrorCode.INVALID_REQUEST::equals).count());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM chat_room_member WHERE user_id=? AND is_pinned=true",Integer.class,owner));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM chat_room_member WHERE user_id=? AND is_pinned=false AND is_muted=false",Integer.class,owner));
    }
    @Test void concurrentDuplicateReportsCommitExactlyOnce()throws Exception{
        var request=new ReportCreateRequest("USER",people.get(1),"도배·스팸",null);
        var result=race(8,i->reports.createReport(owner,request));assertEquals(1,result.stream().filter(Objects::isNull).count());assertEquals(7,result.stream().filter(ErrorCode.INVALID_REQUEST::equals).count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM report WHERE reporter_id=?",Integer.class,owner));
    }
    @Test void outerRollbackReleasesLockAndPreservesReportAndSettings(){
        var request=new ReportCreateRequest("USER",people.get(1),"도배·스팸",null);
        tx.executeWithoutResult(s->{chat.updateSettings(owner,rooms.getFirst(),new ChatRoomSettingsRequest(true,true));reports.createReport(owner,request);s.setRollbackOnly();});
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM report WHERE reporter_id=?",Integer.class,owner));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM chat_room_member WHERE user_id=? AND is_pinned=true",Integer.class,owner));
        assertNotNull(reports.createReport(owner,request));chat.updateSettings(owner,rooms.getFirst(),new ChatRoomSettingsRequest(true,false));
    }
    @Test void inactiveAccountCannotChangeSettingsOrReport(){
        tx.executeWithoutResult(s->em.find(User.class,owner).changeStatus(UserStatus.SUSPENDED));
        assertEquals(ErrorCode.UNAUTHORIZED,assertThrows(CustomException.class,()->chat.updateSettings(owner,rooms.getFirst(),new ChatRoomSettingsRequest(true,true))).getErrorCode());
        assertEquals(ErrorCode.UNAUTHORIZED,assertThrows(CustomException.class,()->reports.createReport(owner,new ReportCreateRequest("USER",people.get(1),"도배·스팸",null))).getErrorCode());
    }
}
