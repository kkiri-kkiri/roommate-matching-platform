package kmi;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.kkirikkiri.global.security.JwtTokenProvider;
import com.kkirikkiri.user.entity.UserRole;
import static kmi.MatchingMultiSupport.*;

/** Regression scenarios for defects discovered during the whole-domain review. */
class MatchingExtraChecks extends MatchingMultiInstance {
    static long room(Member target)throws Exception{
        var reply=api(18081,"POST","/api/chat/rooms",Map.of("category","ROOMMATE","targetUserId",target.id()),me);
        expect(reply,200,"");return reply.data().path("id").asLong();
    }
    static void run(){
        test("auth-shared-login-failure-limit","24 simultaneous wrong passwords across JVMs: bounded failures, remaining 429; no successful login",()->{
            var victim=members.get(10);
            var results=concurrent(24,i->call(port(i),"POST","/api/auth/login",Map.of("email",victim.email(),"password","WrongPassword123!"),null));
            int failures=0,limited=0;for(var r:results){if(r.status()==429)limited++;else{expect(r,401,"UNAUTHORIZED");failures++;}}
            check(failures<=10&&failures>0&&limited==24-failures,"Shared login failure cap exceeded");
            return Map.of("parallel",24,"wrongPasswordResponses",failures,"limited",limited);
        });
        test("community-mysql-ownership-likes-and-comments","actual post/comment services across JVMs; duplicate likes stay unique; ownership violations do not mutate",()->{
            long postId;var em=emf.createEntityManager();try{em.getTransaction().begin();var owner=em.find(com.kkirikkiri.user.entity.User.class,me.id());var profile=em.createQuery("select p from UserProfile p where p.user.userId=:id",com.kkirikkiri.user.entity.UserProfile.class).setParameter("id",me.id()).getSingleResult();var post=new com.kkirikkiri.community.post.entity.CommunityPost(owner,profile.getDormitory(),"자유게시판","검증 게시물","본문");em.persist(post);em.getTransaction().commit();postId=post.getPostId();}finally{em.close();}
            expect(api(18082,"GET","/api/posts/"+postId,null,null),200,"");
            for(var r:concurrent(16,i->call(port(i),"POST","/api/posts/"+postId+"/likes",null,other)))expect(r,200,"");
            check(n("SELECT COUNT(*) FROM post_like WHERE post_id=? AND user_id=?",postId,other.id())==1,"Duplicate post like");
            var comment=api(18081,"POST","/api/posts/"+postId+"/comments",Map.of("content","검증 댓글"),other);expect(comment,200,"");long commentId=comment.data().asLong();
            expect(api(18082,"PATCH","/api/comments/"+commentId,Map.of("content","변경 댓글"),other),200,"");
            expect(api(18081,"DELETE","/api/comments/"+commentId,null,me),403,"FORBIDDEN");
            check(query("SELECT content FROM community_comment WHERE comment_id=?",commentId).getFirst().values().iterator().next().equals("변경 댓글"),"Unauthorized comment mutation");
            expect(api(18082,"DELETE","/api/posts/"+postId,null,other),403,"FORBIDDEN");
            return Map.of("parallelLikes",16,"likeRows",1,"ownershipPreserved",true);
        });
        test("calendar-mysql-cross-jvm-ownership-and-concurrent-settings","calendar created on A visible on B; own settings serialize to one row; private event hidden from others",()->{
            var category=api(18081,"POST","/api/calendar/categories/custom",Map.of("label","검증","color","#123456"),me);expect(category,200,"");long id=category.data().path("id").asLong();
            var created=api(18081,"POST","/api/calendar/events",Map.of("categoryId",id,"title","검증 일정","startDate","2026-10-18","endDate","2026-10-18"),me);expect(created,200,"");long eventId=created.data().path("id").asLong();
            var listed=api(18082,"GET","/api/calendar/events?from=2026-10-18&to=2026-10-18",null,me);expect(listed,200,"");check(listed.data().size()==1,"Cross-JVM calendar not visible");
            var hidden=api(18082,"GET","/api/calendar/events?from=2026-10-18&to=2026-10-18",null,other);expect(hidden,200,"");check(hidden.data().isEmpty(),"Private event leaked");
            expect(api(18082,"DELETE","/api/calendar/events/"+eventId,null,other),403,"FORBIDDEN");
            for(var r:concurrent(8,i->call(port(i),"PUT","/api/calendar/categories",List.of(Map.of("id",id,"label","검증","color","#654321","enabled",i%2==0)),me)))expect(r,200,"");
            check(n("SELECT COUNT(*) FROM calendar_category_setting WHERE user_id=? AND category_id=?",me.id(),id)==1,"Duplicate category setting");return Map.of("parallelSettings",8,"settingRows",1,"ownershipPreserved",true);
        });
        test("reports-concurrent-duplicate-and-ownership","16 cross-JVM reports from same reporter: one 200, fifteen 400, one stored report",()->{
            var body=Map.of("targetType","USER","targetId",target.id(),"reason","도배·스팸");
            var results=concurrent(16,i->call(port(i),"POST","/api/reports",body,me));int ok=0;
            for(var r:results){if(r.status()==200){expect(r,200,"");ok++;}else expect(r,400,"INVALID_REQUEST");}
            check(ok==1&&n("SELECT COUNT(*) FROM report WHERE reporter_id=? AND target_user_id=? AND report_type='USER'",me.id(),target.id())==1,"Duplicate reports committed");
            expect(api(18082,"POST","/api/reports",body,other),200,"");
            return Map.of("parallel",16,"sameReporterRows",1,"independentReporterAllowed",true);
        });
        test("chat-concurrent-pin-cap-and-settings-rollback","three cross-JVM pin requests: exactly two 200, one 400; rejected pin/mute changes both roll back",()->{
            var rooms=List.of(room(target),room(other),room(members.get(3)));
            var results=concurrent(3,i->call(port(i),"PATCH","/api/chat/rooms/"+rooms.get(i)+"/settings",Map.of("pinned",true,"muted",true),me));int ok=0;
            for(var r:results){if(r.status()==200){expect(r,200,"");ok++;}else expect(r,400,"INVALID_REQUEST");}
            check(ok==2&&n("SELECT COUNT(*) FROM chat_room_member WHERE user_id=? AND is_pinned=true AND is_left=false",me.id())==2,"Pin cap exceeded");
            check(n("SELECT COUNT(*) FROM chat_room_member WHERE user_id=? AND is_pinned=false AND is_muted=false",me.id())==1,"Failed settings mutated mute");
            expect(api(18082,"PATCH","/api/chat/rooms/"+rooms.getFirst()+"/settings",Map.of("muted",true),members.get(4)),403,"FORBIDDEN");
            return Map.of("successfulPins",ok,"rejected",1,"storedPins",2);
        });
        test("websocket-live-delivery-and-revoked-subscription","real STOMP delivers while active; after leave old subscription receives no new message; sender still commits",()->{
            long id=room(target);
            try(var reader=new SocketClient(18081,target.token());var writer=new SocketClient(18081,me.token())){
                reader.subscribe(id);probe(reader,writer,id);
                expect(api(18082,"DELETE","/api/chat/rooms/"+id+"/members/me",null,target),200,"");
                long before=n("SELECT COUNT(*) FROM chat_message WHERE chat_room_id=?",id);writer.send(id,"after-leave");
                awaitStored(id,before+1);check(reader.messages.poll(700,TimeUnit.MILLISECONDS)==null,"Revoked subscriber received message");
                return Map.of("activeDeliveryObserved",true,"oldSubscriptionBlocked",true,"writerCommitted",true);
            }
        });
        test("websocket-expired-session-delivery-blocked","connected token expires; existing subscriber receives no further messages",()->{
            long id=room(other);String shortToken=new JwtTokenProvider(System.getenv("KM_JWT"),4).createAccessToken(other.id(),UserRole.USER);
            try(var reader=new SocketClient(18082,shortToken);var writer=new SocketClient(18082,me.token())){
                reader.subscribe(id);probe(reader,writer,id);long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                while(System.nanoTime()<deadline)Thread.sleep(50);
                long before=n("SELECT COUNT(*) FROM chat_message WHERE chat_room_id=?",id);writer.send(id,"after-expiry");awaitStored(id,before+1);
                check(reader.messages.poll(700,TimeUnit.MILLISECONDS)==null,"Expired subscriber received message");return Map.of("expiredSubscriptionBlocked",true);
            }
        });
        test("websocket-suspended-account-send-and-delivery-blocked","after suspension connected subscriber gets no delivery and SEND cannot persist",()->{
            Member suspended=members.get(3);long id=room(suspended);
            try(var reader=new SocketClient(18081,suspended.token());var writer=new SocketClient(18081,me.token())){
                reader.subscribe(id);probe(reader,writer,id);update("UPDATE user SET status='SUSPENDED' WHERE user_id=?",suspended.id());
                try{long before=n("SELECT COUNT(*) FROM chat_message WHERE chat_room_id=?",id);writer.send(id,"after-suspension");awaitStored(id,before+1);
                    check(reader.messages.poll(700,TimeUnit.MILLISECONDS)==null,"Suspended subscriber received message");
                    reader.send(id,"forbidden-send");Thread.sleep(500);check(n("SELECT COUNT(*) FROM chat_message WHERE chat_room_id=?",id)==before+1,"Suspended sender persisted message");
                    return Map.of("oldSubscriptionBlocked",true,"sendBlocked",true);
                }finally{update("UPDATE user SET status='ACTIVE' WHERE user_id=?",suspended.id());}
            }
        });
    }
    static void awaitStored(long id,long expected)throws Exception{long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(n("SELECT COUNT(*) FROM chat_message WHERE chat_room_id=?",id)<expected){check(System.nanoTime()<deadline,"Message did not commit");Thread.sleep(20);}}
    static void probe(SocketClient reader,SocketClient writer,long id)throws Exception{
        // Confirm that the actual subscription is live before testing its revocation.
        for(int i=0;i<5;i++){writer.send(id,"probe-"+i);if(reader.messages.poll(300,TimeUnit.MILLISECONDS)!=null){reader.messages.clear();return;}}
        throw new AssertionError("Active STOMP subscription did not receive a message");
    }
    static class SocketClient implements WebSocket.Listener,AutoCloseable {
        final BlockingQueue<String> frames=new LinkedBlockingQueue<>(),messages=new LinkedBlockingQueue<>();
        final StringBuilder partial=new StringBuilder();final WebSocket socket;
        SocketClient(int port,String token)throws Exception{
            socket=HTTP.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).header("Origin","http://localhost:3000").buildAsync(URI.create("ws://127.0.0.1:"+port+"/ws/chat"),this).get(10,TimeUnit.SECONDS);
            frame("CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer "+token+"\n\n\0");
            String connected=frames.poll(5,TimeUnit.SECONDS);check(connected!=null&&connected.startsWith("CONNECTED"),"STOMP connect failed");
        }
        public void onOpen(WebSocket ws){ws.request(1);}
        public CompletionStage<?> onText(WebSocket ws,CharSequence data,boolean last){synchronized(partial){partial.append(data);if(last){String value=partial.toString();partial.setLength(0);for(String frame:value.split("\0")){String cleaned=frame.stripLeading();if(!cleaned.isBlank()){frames.add(cleaned);if(cleaned.startsWith("MESSAGE"))messages.add(cleaned);}}}}ws.request(1);return null;}
        void frame(String value){socket.sendText(value,true).join();}
        void subscribe(long room){frame("SUBSCRIBE\nid:subscription\ndestination:/sub/chat/rooms/"+room+"\n\n\0");}
        void send(long room,String text){frame("SEND\ndestination:/pub/chat/rooms/"+room+"/messages\ncontent-type:application/json\n\n"+JSON.writeValueAsString(Map.of("text",text))+"\0");}
        public void close(){socket.abort();}
    }
}
