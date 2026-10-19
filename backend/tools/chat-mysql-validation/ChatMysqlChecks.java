import com.kkirikkiri.chat.dto.*;
import com.kkirikkiri.chat.entity.*;
import com.kkirikkiri.chat.repository.*;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.global.exception.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.transaction.support.TransactionTemplate;

public class ChatMysqlChecks extends ChatMysqlValidation {
    static void run() throws Exception {
        for(boolean settings:List.of(false,true)) {
            test("stale-"+(settings?"settings":"read")+"-after-leave",()->staleAfterLeave(settings));
            test((settings?"settings":"read")+"-first-leave-waits",()->writeFirst(settings));
        }
        test("monotonic-read-time-stale-managed-context",ChatMysqlChecks::monotonic);
        test("RR-current-read-sees-message-after-snapshot",ChatMysqlChecks::currentRead);
        test("delayed-commit-A-read-B-only",ChatMysqlChecks::lateCommit);
        test("insert-during-uncommitted-read",ChatMysqlChecks::insertDuringRead);
        test("equal-timestamps-and-duplicate-receipts",ChatMysqlChecks::equalTimes);
        test("invalid-batch-and-rollback-preserve-state",ChatMysqlChecks::rollback);
        test("pin-two-concurrent-stale-snapshots",ChatMysqlChecks::pinLimit);
        test("opposite-participants-concurrent-read-no-deadlock",ChatMysqlChecks::oppositeReads);
        test("lock-timeout-rollback-and-retry",ChatMysqlChecks::timeout);
        test("controlled-InnoDB-deadlock-victim-rollback",ChatMysqlChecks::deadlock);
        test("category-reuse-anonymous-POST-COMMENT-masking",ChatMysqlChecks::anonymity);
        test("Unicode-2000-codepoints-and-authorization",ChatMysqlChecks::unicode);
        test("message-size-offset-cursors-search-read-status",ChatMysqlChecks::pagination);
    }
    static void distinct() {check(new HashSet<>(connections).size()>=2,"Independent JDBC connections required");}
    static void staleAfterLeave(boolean settings) throws Exception {
        var f=fixture();long incoming=message(f,"seen",FIXED);
        var loaded=new CountDownLatch(1);var resume=new CountDownLatch(1);
        var job=workers.submit(()->tx().execute(s->{
            session();check(!em.find(ChatRoomMember.class,f.member()).isLeft(),"Initial active state");loaded.countDown();await(resume);
            try {if(settings)chat.updateSettings(f.reader(),f.room(),new ChatRoomSettingsRequest(true,true));else read(f,incoming);throw new AssertionError("Expected forbidden");}
            catch(CustomException e){s.setRollbackOnly();return e.getErrorCode();}
        }));
        try{await(loaded);tx().executeWithoutResult(s->{session();chat.leaveRoom(f.reader(),f.room());committed("leave-committed");});}finally{resume.countDown();}
        check(job.get(15,TimeUnit.SECONDS)==ErrorCode.FORBIDDEN,"Stale request must be forbidden");distinct();
        check(jdbc.queryForObject("select is_left from CHAT_ROOM_MEMBER where chat_room_member_id=?",Boolean.class,f.member()),"Membership resurrected");
        check(jdbc.queryForObject("select left_at from CHAT_ROOM_MEMBER where chat_room_member_id=?",LocalDateTime.class,f.member())!=null,"Missing leave time");
        check(!isRead(incoming),"Rejected read mutated receipt");
    }
    static void writeFirst(boolean settings) throws Exception {
        var f=fixture();long id=message(f,"incoming",FIXED);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        var first=workers.submit(()->tx().executeWithoutResult(s->{session();if(settings)chat.updateSettings(f.reader(),f.room(),new ChatRoomSettingsRequest(null,true));else read(f,id);em.flush();locked.countDown();await(release);committed("write-committed");}));
        await(locked);
        var second=workers.submit(()->tx().executeWithoutResult(s->{session();attempting.countDown();chat.leaveRoom(f.reader(),f.room());committed("leave-committed");}));
        try{await(attempting);waiting(second);}finally{release.countDown();}
        first.get(15,TimeUnit.SECONDS);second.get(15,TimeUnit.SECONDS);distinct();
        check(order.indexOf("write-committed")<order.indexOf("leave-committed"),"Commit order");
        check(jdbc.queryForObject("select is_left from CHAT_ROOM_MEMBER where chat_room_member_id=?",Boolean.class,f.member()),"Leave lost");
        check(settings || isRead(id),"Committed read lost");
    }
    static void monotonic() throws Exception {
        var f=fixture();long old=message(f,"old",FIXED),recent=message(f,"recent",FIXED.plusMinutes(1));
        var loaded=new CountDownLatch(1);var resume=new CountDownLatch(1);
        var job=workers.submit(()->tx().executeWithoutResult(s->{session();check(em.find(ChatRoomMember.class,f.member()).getLastReadAt()==null,"Initial time");loaded.countDown();await(resume);read(f,old);committed("older-request-committed");}));
        try{await(loaded);tx().executeWithoutResult(s->{session();read(f,recent);committed("newer-request-committed");});}finally{resume.countDown();}
        job.get(15,TimeUnit.SECONDS);distinct();
        check(FIXED.plusMinutes(1).equals(jdbc.queryForObject("select last_read_at from CHAT_ROOM_MEMBER where chat_room_member_id=?",LocalDateTime.class,f.member())),"Read time moved backwards");
        check(tx().execute(s->context.getBean(ChatRoomMemberRepository.class).advanceReadTime(f.member(),FIXED))==0,"Conditional update must reject older time");
        check(unread(f)==0,"Receipts missing");
    }
    static void currentRead() throws Exception {
        var f=fixture();var snapshot=new CountDownLatch(1);var inserted=new CountDownLatch(1);var id=new AtomicLong();
        var job=workers.submit(()->tx().executeWithoutResult(s->{session();check(chat.getMessages(f.reader(),f.room(),null).content().isEmpty(),"Initial snapshot");snapshot.countDown();await(inserted);read(f,id.get());committed("RR-read-committed");}));
        try{await(snapshot);tx().executeWithoutResult(s->{session();id.set(message(f,"new after snapshot",FIXED));committed("insert-committed");});}finally{inserted.countDown();}
        job.get(15,TimeUnit.SECONDS);distinct();check(isRead(id.get()),"Current read failed to expose committed message");
    }
    static void lateCommit() throws Exception {
        var f=fixture();var inserted=new CountDownLatch(1);var finish=new CountDownLatch(1);
        var first=workers.submit(()->tx().execute(s->{session();long id=message(f,"A-delayed",FIXED);inserted.countDown();await(finish);committed("A-committed");return id;}));
        long b;
        try{await(inserted);b=tx().execute(s->{session();long id=message(f,"B-visible",FIXED);committed("B-committed");return id;});
            check(chat.getMessages(f.reader(),f.room(),null).content().stream().map(ChatMessageResponse::id).toList().equals(List.of(b)),"Uncommitted A became visible");
            read(f,b);order.add("B-read-committed");
        }finally{finish.countDown();}
        long a=first.get(15,TimeUnit.SECONDS);distinct();check(!isRead(a)&&isRead(b)&&unread(f)==1,"Delayed A inherited B receipt");
    }
    static void insertDuringRead() throws Exception {
        var f=fixture();long seen=message(f,"seen",FIXED);var locked=new CountDownLatch(1);var finish=new CountDownLatch(1);
        var job=workers.submit(()->tx().executeWithoutResult(s->{session();read(f,seen);locked.countDown();await(finish);committed("read-committed");}));
        long unseen;
        try{await(locked);unseen=tx().execute(s->{session();long id=message(f,"unseen",FIXED);committed("insert-committed");return id;});}finally{finish.countDown();}
        job.get(15,TimeUnit.SECONDS);distinct();check(isRead(seen)&&!isRead(unseen)&&unread(f)==1,"Concurrent insertion received wrong receipt");
    }
    static void equalTimes() {
        var f=fixture();long a=message(f,"first",FIXED),b=message(f,"second",FIXED);read(f,b,b);read(f,b);
        var rows=chat.getMessages(f.reader(),f.room(),null).content();check(rows.stream().map(ChatMessageResponse::id).toList().equals(List.of(b,a)),"Timestamp/id sort");
        check(rows.getFirst().read()&&!rows.getLast().read()&&unread(f)==1,"Exact duplicate handling");
    }
    static void rollback() {
        var f=fixture();long id=message(f,"incoming",FIXED);var other=fixture();long wrong=message(other,"wrong room",FIXED);
        long own=chat.sendMessage(f.reader(),f.room(),new ChatMessageSendRequest("own")).id();
        for(long invalid:List.of(own,wrong,Long.MAX_VALUE)){error(ErrorCode.INVALID_REQUEST,()->read(f,id,invalid));check(!isRead(id),"Partial batch changed receipt");}
        tx().executeWithoutResult(s->{read(f,id);chat.updateSettings(f.reader(),f.room(),new ChatRoomSettingsRequest(true,true));chat.leaveRoom(f.reader(),f.room());em.flush();s.setRollbackOnly();});
        var row=jdbc.queryForMap("select is_left,is_pinned,is_muted,last_read_at from CHAT_ROOM_MEMBER where chat_room_member_id=?",f.member());
        check(!Boolean.TRUE.equals(row.get("is_left"))&&!Boolean.TRUE.equals(row.get("is_pinned"))&&row.get("last_read_at")==null&&!isRead(id),"Rollback leaked state");
        read(f,id);check(unread(f)==0,"Rollback retry failed");
    }
    static void pinLimit() throws Exception {
        long owner=user();var a=room(owner,user());var b=room(owner,user());var c=room(owner,user());
        chat.updateSettings(owner,a.room(),new ChatRoomSettingsRequest(true,null));
        var snapshot=new CountDownLatch(1);var firstReady=new CountDownLatch(1);var release=new CountDownLatch(1);var attempting=new CountDownLatch(1);
        var second=workers.submit(()->tx().execute(s->{session();check(jdbc.queryForObject("select count(*) from CHAT_ROOM_MEMBER where user_id=? and is_pinned=1",Integer.class,owner)==1,"Initial pinned snapshot");em.find(ChatRoomMember.class,c.member());snapshot.countDown();await(firstReady);attempting.countDown();try{chat.updateSettings(owner,c.room(),new ChatRoomSettingsRequest(true,true));return false;}catch(CustomException e){s.setRollbackOnly();return e.getErrorCode()==ErrorCode.INVALID_REQUEST;}}));
        await(snapshot);
        var first=workers.submit(()->tx().executeWithoutResult(s->{session();chat.updateSettings(owner,b.room(),new ChatRoomSettingsRequest(true,null));em.flush();firstReady.countDown();await(release);committed("second-pin-committed");}));
        try{await(firstReady);await(attempting);waiting(second);}finally{release.countDown();}
        first.get(15,TimeUnit.SECONDS);check(second.get(15,TimeUnit.SECONDS),"Stale snapshot admitted third pin");distinct();
        check(jdbc.queryForObject("select count(*) from CHAT_ROOM_MEMBER where user_id=? and is_left=0 and is_pinned=1",Integer.class,owner)==2,"Pinned maximum broken");
        check(!jdbc.queryForObject("select is_muted from CHAT_ROOM_MEMBER where chat_room_member_id=?",Boolean.class,c.member()),"Rejected pin leaked mute");
        chat.leaveRoom(owner,a.room());chat.updateSettings(owner,c.room(),new ChatRoomSettingsRequest(true,null));
        check(jdbc.queryForObject("select count(*) from CHAT_ROOM_MEMBER where user_id=? and is_left=0 and is_pinned=1",Integer.class,owner)==2,"Leaving pinned room failed to free slot");
    }
    static void oppositeReads() throws Exception {
        var f=fixture();long a=message(f,"to reader",FIXED),b=chat.sendMessage(f.reader(),f.room(),new ChatMessageSendRequest("to writer")).id();
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        var first=workers.submit(()->tx().executeWithoutResult(s->{session();ready.countDown();await(start);read(f,a);committed("reader-committed");}));
        var second=workers.submit(()->tx().executeWithoutResult(s->{session();ready.countDown();await(start);chat.markRead(f.writer(),f.room(),new ChatReadRequest(List.of(b)));committed("writer-committed");}));
        await(ready);start.countDown();first.get(15,TimeUnit.SECONDS);second.get(15,TimeUnit.SECONDS);distinct();check(isRead(a)&&isRead(b),"Opposite read receipts lost");
    }
    static void timeout() throws Exception {
        var f=fixture();var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        var holder=workers.submit(()->tx().executeWithoutResult(s->{session();jdbc.queryForList("select user_id from USER where user_id=? for update",f.reader());locked.countDown();await(release);}));
        Throwable conflict=null;
        try{await(locked);session();try{tx().executeWithoutResult(s->{
            // Keep InnoDB's lock wait at 3s. Disable the independent 3s SELECT workload guard
            // only on this intentional lock wait, then restore it before returning the connection.
            jdbc.execute("set session max_execution_time=0");
            try{chat.updateSettings(f.reader(),f.room(),new ChatRoomSettingsRequest(true,true));}
            finally{jdbc.execute("set session max_execution_time=3000");}
        });throw new AssertionError("Expected lock timeout");}catch(ConcurrencyFailureException|jakarta.persistence.PessimisticLockException|jakarta.persistence.LockTimeoutException expected){conflict=expected;}}
        finally{release.countDown();holder.get(15,TimeUnit.SECONDS);}
        check(conflict!=null,"Missing lock conflict");
        for(Throwable t=conflict;t!=null;t=t.getCause())if(t instanceof java.sql.SQLException sql)order.add("lock-timeout SQLState="+sql.getSQLState()+" code="+sql.getErrorCode());
        var response=new GlobalExceptionHandler().handleConcurrentUpdate((Exception)conflict);check(response.getStatusCode().value()==409,"Conflict must map to HTTP 409");
        check(!jdbc.queryForObject("select is_pinned from CHAT_ROOM_MEMBER where chat_room_member_id=?",Boolean.class,f.member()),"Timed out write leaked");
        chat.updateSettings(f.reader(),f.room(),new ChatRoomSettingsRequest(true,true));
        check(jdbc.queryForObject("select is_pinned from CHAT_ROOM_MEMBER where chat_room_member_id=?",Boolean.class,f.member()),"Retry after timeout failed");
    }
    static void deadlock() throws Exception {
        long a=user(),b=user();var ready=new CountDownLatch(2);var start=new CountDownLatch(1);var victims=new AtomicInteger();
        var jobs=new ArrayList<Future<?>>();
        for(boolean reverse:List.of(false,true))jobs.add(workers.submit(()->{
            try{tx().executeWithoutResult(s->{session();jdbc.queryForList("select user_id from USER where user_id=? for update",reverse?b:a);ready.countDown();await(start);jdbc.queryForList("select user_id from USER where user_id=? for update",reverse?a:b);committed("deadlock-survivor");});}
            catch(ConcurrencyFailureException e){check(e.getMostSpecificCause() instanceof java.sql.SQLException sql && sql.getErrorCode()==1213,"Expected InnoDB deadlock 1213");victims.incrementAndGet();order.add("deadlock-victim-rolled-back");}
        }));
        await(ready);start.countDown();for(var job:jobs)job.get(15,TimeUnit.SECONDS);distinct();check(victims.get()==1,"Expected exactly one victim");
        check(jdbc.queryForObject("select count(*) from USER where user_id in (?,?) and status='ACTIVE'",Integer.class,a,b)==2,"Deadlock changed account state");
    }
    static void profile(long id,String nickname) {
        tx().executeWithoutResult(s->{var d=new Dormitory("chat-"+id,"chat dorm "+id,true);em.persist(d);var f=new Faculty("chat faculty "+id,true);em.persist(f);em.persist(new UserProfile(em.getReference(User.class,id),nickname,"남성",d,f,null,(short)2004,"24","2학년","INTJ"));});
    }
    static void anonymity() {
        long viewer=user(),author=user();profile(viewer,"viewer"+viewer);profile(author,"secret"+author);
        var req=new ChatRoomCreateRequest(author,ChatRoomCategory.ROOMMATE,null,null,null);
        var roommate=chat.createOrGetRoom(viewer,req);check(roommate.id().equals(chat.createOrGetRoom(viewer,req).id()),"Roommate reuse failed");
        long[] sources=tx().execute(s->{var u=em.getReference(User.class,author);var pub=new CommunityPost(u,null,"자유게시판","public","body",false);em.persist(pub);var anon=new CommunityPost(u,null,"자유게시판","anonymous","body",true);em.persist(anon);var c=new CommunityComment(pub,u,null,"anonymous comment");c.changeAnonymous(true);em.persist(c);return new long[]{pub.getPostId(),anon.getPostId(),c.getCommentId()};});
        var publicRoom=chat.createOrGetRoom(viewer,new ChatRoomCreateRequest(author,ChatRoomCategory.COMMUNITY,sources[0],null,"POST"));
        check(!publicRoom.id().equals(roommate.id()),"Categories shared a room");
        var postReq=new ChatRoomCreateRequest(Long.MAX_VALUE,ChatRoomCategory.COMMUNITY,sources[1],null,"POST");
        var anonPost=chat.createOrGetRoom(viewer,postReq);var anonComment=chat.createOrGetRoom(viewer,new ChatRoomCreateRequest(null,ChatRoomCategory.COMMUNITY,sources[0],sources[2],"COMMENT"));
        for(var room:List.of(anonPost,anonComment)) {
            check(room.counterpart().id()==null&&"익명".equals(room.counterpart().nickname())&&room.counterpart().tags().isEmpty(),"Anonymous profile leaked");
            var sent=chat.sendMessage(author,room.id(),new ChatMessageSendRequest("anonymous text"));check(sent.senderId()==null,"Broadcast DTO sender leaked");
            check(chat.getMessages(viewer,room.id(),null).content().getFirst().senderId()==null,"REST sender leaked");
        }
        check(anonPost.id().equals(chat.createOrGetRoom(viewer,postReq).id()),"Anonymous room reuse failed");
        check(chat.getRooms(viewer,new ChatRoomListRequest("COMMUNITY","secret"+author)).totalElements()==1,"Search revealed anonymous identity");
        check(chat.getRooms(viewer,new ChatRoomListRequest("COMMUNITY","익명")).totalElements()==2,"Anonymous room search");
        chat.leaveRoom(viewer,roommate.id());error(ErrorCode.FORBIDDEN,()->chat.createOrGetRoom(viewer,req));
    }
    static void unicode() {
        var f=fixture();String allowed="😀".repeat(2000);long id=chat.sendMessage(f.writer(),f.room(),new ChatMessageSendRequest(allowed)).id();
        check(allowed.equals(jdbc.queryForObject("select content from CHAT_MESSAGE where message_id=?",String.class,id)),"utf8mb4 round trip failed");
        error(ErrorCode.VALIDATION_ERROR,()->chat.sendMessage(f.writer(),f.room(),new ChatMessageSendRequest("😀".repeat(2001))));
        error(ErrorCode.VALIDATION_ERROR,()->chat.sendMessage(f.writer(),f.room(),new ChatMessageSendRequest("  \t")));
        long outsider=user();error(ErrorCode.FORBIDDEN,()->chat.getMessages(outsider,f.room(),null));
        error(ErrorCode.FORBIDDEN,()->chat.sendMessage(outsider,f.room(),new ChatMessageSendRequest("intrusion")));
        error(ErrorCode.FORBIDDEN,()->chat.getReadStatuses(outsider,f.room(),new ChatReadRequest(List.of(id))));
        chat.leaveRoom(f.reader(),f.room());error(ErrorCode.FORBIDDEN,()->read(f,id));
    }
    static void pagination() {
        var f=fixture();var expected=new ArrayList<Long>();
        for(int i=0;i<151;i++)expected.add(message(f,i%3==0?"other":"needle%_!"+i,FIXED));
        var page=chat.getMessages(f.reader(),f.room(),null);check(page.size()==50&&page.content().size()==50&&page.totalElements()==151,"Default cap");
        check(chat.getMessages(f.reader(),f.room(),new ChatMessageListRequest(0,100,null)).content().size()==100,"Max cap");
        for(var bad:List.of(new ChatMessageListRequest(0,101,null),new ChatMessageListRequest(-1,50,null),new ChatMessageListRequest(Integer.MAX_VALUE,100,null),new ChatMessageListRequest(1,50,null,expected.getFirst(),null),new ChatMessageListRequest(0,50,null,expected.getFirst(),expected.getLast())))error(ErrorCode.VALIDATION_ERROR,()->chat.getMessages(f.reader(),f.room(),bad));
        var offsetIds=new ArrayList<Long>();for(int i=0;i<4;i++)offsetIds.addAll(chat.getMessages(f.reader(),f.room(),new ChatMessageListRequest(i,50,null)).content().stream().map(ChatMessageResponse::id).toList());
        check(offsetIds.equals(expected.reversed()),"Offset gaps/order/duplicates");
        long anchor=page.content().getFirst().id();long arrived=message(f,"new arrival",FIXED.plusDays(1));
        var ids=new ArrayList<>(page.content().stream().map(ChatMessageResponse::id).toList());while(!page.last()){page=chat.getMessages(f.reader(),f.room(),new ChatMessageListRequest(0,50,null,ids.getLast(),null));ids.addAll(page.content().stream().map(ChatMessageResponse::id).toList());}
        check(ids.equals(expected.reversed()),"Before cursor moved after insertion");
        check(chat.getMessages(f.reader(),f.room(),new ChatMessageListRequest(0,50,null,null,anchor)).content().stream().map(ChatMessageResponse::id).toList().equals(List.of(arrived)),"After cursor order");
        for(int i=0;i<55;i++)message(f,"catch-up",FIXED.plusDays(1));
        var forward=chat.getMessages(f.reader(),f.room(),new ChatMessageListRequest(0,50,null,null,anchor));
        var more=chat.getMessages(f.reader(),f.room(),new ChatMessageListRequest(0,50,null,null,forward.content().getLast().id()));
        check(forward.content().size()==50&&more.content().size()==6&&more.last(),"Forward cursor pagination");
        var found=chat.getMessages(f.reader(),f.room(),new ChatMessageListRequest(0,50,"needle%_!"));
        var matches=new ArrayList<>(found.content());while(!found.last()){found=chat.getMessages(f.reader(),f.room(),new ChatMessageListRequest(0,50,"needle%_!",matches.getLast().id(),null));matches.addAll(found.content());}
        check(matches.size()==100&&matches.stream().map(ChatMessageResponse::id).distinct().count()==100,"Literal wildcard search gaps");
        var other=fixture();long otherId=message(other,"other room",FIXED);error(ErrorCode.INVALID_REQUEST,()->chat.getMessages(f.reader(),f.room(),new ChatMessageListRequest(0,50,null,otherId,null)));
        read(f,expected.get(1));var status=chat.getReadStatuses(f.reader(),f.room(),new ChatReadRequest(List.of(expected.get(0),expected.get(1))));
        check(status.stream().filter(ChatMessageReadStatusResponse::read).count()==1,"Read-status mapping");
        error(ErrorCode.INVALID_REQUEST,()->chat.getReadStatuses(f.reader(),f.room(),new ChatReadRequest(List.of(otherId))));
        String serialized=JSON.writeValueAsString(chat.getMessages(f.reader(),f.room(),null));check(serialized.contains("\"content\"")&&serialized.contains("\"totalElements\""),"DTO serialization");
    }
}
