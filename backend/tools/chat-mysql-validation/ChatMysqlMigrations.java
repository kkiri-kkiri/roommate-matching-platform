import com.kkirikkiri.chat.entity.*;
import com.kkirikkiri.user.entity.User;
import com.kkirikkiri.community.post.entity.CommunityPost;
import com.kkirikkiri.community.comment.entity.CommunityComment;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class ChatMysqlMigrations extends ChatMysqlValidation {
    static Fixture f;
    static long before,equal,after,own;
    static List<Map<String,Object>> snapshot;
    static void run()throws Exception{
        test("legacy-synthetic-fixtures-and-pre-receipt-schema",()->{
            f=fixture();before=message(f,"before-boundary-😀",FIXED.minusSeconds(1));equal=message(f,"equal-boundary",FIXED);after=message(f,"after-boundary",FIXED.plusSeconds(1));
            own=tx().execute(s->{var m=new ChatMessage(em.getReference(ChatRoom.class,f.room()),em.getReference(User.class,f.reader()),"own-recipient-null");em.persist(m);return m.getMessageId();});
            jdbc.update("update CHAT_ROOM_MEMBER set last_read_at=? where chat_room_member_id=?",FIXED,f.member());
            snapshot=snapshot();jdbc.execute("ALTER TABLE CHAT_MESSAGE DROP COLUMN is_read");
            check(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name='chat_message' and column_name='is_read'",Integer.class)==0,"Not a legacy schema");
        });
        test("legacy-receipt-migration-data-and-boundaries",()->{applySql("20261019_chat_message_receipts.sql",jdbc);verify();});
        test("legacy-physical-column-constraints",()->{
            var col=jdbc.queryForMap("select COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT from information_schema.columns where table_schema=database() and table_name='chat_message' and column_name='is_read'");
            check("tinyint(1)".equals(col.get("COLUMN_TYPE"))&&"NO".equals(col.get("IS_NULLABLE"))&&"0".equals(col.get("COLUMN_DEFAULT")),"Receipt DDL mismatch");
            Files.writeString(output.resolve("receipt-column.json"),JSON.writeValueAsString(col));
        });
        test("legacy-rerun-conflict-preserves-data",()->{expectedSql(1060,()->{try{applySql("20261019_chat_message_receipts.sql",jdbc);}catch(java.io.IOException e){throw new RuntimeException(e);}catch(Exception e){if(e instanceof RuntimeException r)throw r;throw new RuntimeException(e);}});verify();});
        test("DDL-implicit-commit-survives-rollback",()->{
            jdbc.execute("create table chat_ddl_probe(id bigint primary key,value_before int not null) engine=InnoDB");
            try(var c=connection(schema)){c.setAutoCommit(false);try(var st=c.createStatement()){st.executeUpdate("insert into chat_ddl_probe values(1,42)");st.execute("alter table chat_ddl_probe add column ddl_committed int null");c.rollback();}}
            check(jdbc.queryForObject("select value_before from chat_ddl_probe where id=1",Integer.class)==42,"DDL did not implicitly commit prior DML");
            check(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=database() and table_name='chat_ddl_probe' and column_name='ddl_committed'",Integer.class)==1,"DDL was rolled back unexpectedly");
        });
        test("partial-receipt-failure-rollback-and-forward-recovery",()->{
            jdbc.execute("alter table CHAT_MESSAGE drop column is_read");
            jdbc.execute("alter table CHAT_MESSAGE add column is_read BOOLEAN NOT NULL DEFAULT FALSE");
            try(var c=connection(schema)){try(var st=c.createStatement()){
                st.execute("start transaction");st.executeUpdate("update CHAT_MESSAGE set is_read=true where message_id="+before);
                try{st.executeUpdate("update CHAT_MESSAGE set deliberately_missing_column=1");throw new AssertionError("Expected failed conversion");}
                catch(SQLException expected){check(expected.getErrorCode()==1054,"Wrong failure simulation");st.execute("rollback");}
            }}
            check(!isRead(before)&&!isRead(equal)&&!isRead(after),"Failed UPDATE transaction leaked changes");
            check(snapshot.equals(snapshot()),"Failed migration changed original message data");
            String source=Files.readString(root.resolve("backend/migrations/20261019_chat_message_receipts.sql")).replaceAll("(?m)^--.*$","");
            script(source.substring(source.indexOf("START TRANSACTION")),jdbc);verify();
        });
        test("PK-FK-NOT-NULL-UNIQUE-enforced",ChatMysqlMigrations::constraints);
        test("anonymous-key-migration-and-idempotence",ChatMysqlMigrations::anonymousKeys);
        test("legacy-index-partial-failure-and-recovery",()->{
            jdbc.execute("CREATE INDEX idx_chat_message_room_latest ON CHAT_MESSAGE(chat_room_id,sent_at,message_id)");
            expectedSql(1061,()->jdbc.execute("CREATE INDEX idx_chat_message_room_latest ON CHAT_MESSAGE(chat_room_id,sent_at,message_id)"));
            check(jdbc.queryForObject("select count(distinct index_name) from information_schema.statistics where table_schema=database() and index_name='idx_chat_message_room_latest'",Integer.class)==1,"Completed index missing after failure");
            jdbc.execute("CREATE INDEX idx_chat_message_room_unread ON CHAT_MESSAGE(chat_room_id,is_read,sender_id)");
            jdbc.execute("CREATE INDEX idx_chat_member_user_active_room ON CHAT_ROOM_MEMBER(user_id,is_left,chat_room_id)");
            ChatMysqlPerformance.indexMetadata();
            // Roll back only a nonunique candidate index on this owned schema, then re-create it.
            expectedSql(1553,()->jdbc.execute("DROP INDEX idx_chat_member_user_active_room ON CHAT_ROOM_MEMBER"));
            jdbc.execute("CREATE INDEX idx_chat_member_fk_restore ON CHAT_ROOM_MEMBER(user_id)");
            jdbc.execute("DROP INDEX idx_chat_member_user_active_room ON CHAT_ROOM_MEMBER");
            check(jdbc.queryForObject("select count(*) from information_schema.statistics where table_schema=database() and index_name='idx_chat_member_user_active_room'",Integer.class)==0,"Index rollback failed");
            jdbc.execute("CREATE INDEX idx_chat_member_user_active_room ON CHAT_ROOM_MEMBER(user_id,is_left,chat_room_id)");verify();
        });
    }
    static List<Map<String,Object>> snapshot(){return jdbc.queryForList("select message_id,content,sent_at,chat_room_id,sender_id from CHAT_MESSAGE order by message_id");}
    static void verify(){check(snapshot.equals(snapshot()),"Message data not preserved");check(isRead(before)&&isRead(equal)&&!isRead(after)&&!isRead(own),"Old display conversion boundary/null policy");}
    static void followUp()throws Exception{
        before=jdbc.queryForObject("select message_id from CHAT_MESSAGE where content='before-boundary-😀'",Long.class);
        equal=jdbc.queryForObject("select message_id from CHAT_MESSAGE where content='equal-boundary'",Long.class);
        after=jdbc.queryForObject("select message_id from CHAT_MESSAGE where content='after-boundary'",Long.class);
        own=jdbc.queryForObject("select message_id from CHAT_MESSAGE where content='own-recipient-null'",Long.class);
        long room=jdbc.queryForObject("select chat_room_id from CHAT_MESSAGE where message_id=?",Long.class,before);
        long writer=jdbc.queryForObject("select sender_id from CHAT_MESSAGE where message_id=?",Long.class,before);
        var member=jdbc.queryForMap("select chat_room_member_id,user_id from CHAT_ROOM_MEMBER where chat_room_id=? and user_id<>?",room,writer);
        f=new Fixture(((Number)member.get("user_id")).longValue(),writer,room,((Number)member.get("chat_room_member_id")).longValue());snapshot=snapshot();
        test("PK-FK-NOT-NULL-UNIQUE-enforced-diagnostic",ChatMysqlMigrations::constraints);
        test("legacy-final-index-and-data-preservation",()->{ChatMysqlPerformance.indexMetadata();verify();});
    }
    static void constraints()throws Exception{
        expectedSql(1062,()->jdbc.update("insert into CHAT_ROOM_MEMBER(chat_room_id,user_id,is_left,is_pinned,is_muted,joined_at) select chat_room_id,user_id,is_left,is_pinned,is_muted,joined_at from CHAT_ROOM_MEMBER where chat_room_member_id=?",f.member()));
        expectedSql(Set.of(1216,1452),()->jdbc.update("insert into CHAT_MESSAGE(chat_room_id,sender_id,content,sent_at,is_read) values(?,?,?,?,false)",Long.MAX_VALUE,f.writer(),"orphan",FIXED));
        expectedSql(1048,()->jdbc.update("insert into CHAT_MESSAGE(chat_room_id,sender_id,content,sent_at,is_read) values(?,?,?,?,false)",f.room(),f.writer(),null,FIXED));
        expectedSql(1062,()->jdbc.update("insert into CHAT_MESSAGE(message_id,chat_room_id,sender_id,content,sent_at,is_read) values(?,?,?,?,?,false)",before,f.room(),f.writer(),"duplicate",FIXED));
        expectedSql(Set.of(1217,1451),()->jdbc.update("delete from CHAT_ROOM where chat_room_id=?",f.room()));
        var keys=jdbc.queryForList("select table_name,constraint_name,constraint_type from information_schema.table_constraints where constraint_schema=database() and table_name in ('chat_message','chat_room','chat_room_member') order by table_name,constraint_name");
        check(keys.stream().filter(k->"FOREIGN KEY".equals(k.get("CONSTRAINT_TYPE"))).count()==6,"Chat foreign key count");
        Files.writeString(output.resolve("chat-constraints.json"),JSON.writeValueAsString(keys));verify();
    }
    static void anonymousKeys()throws Exception{
        long[] ids=tx().execute(s->{
            long a=user(),b=user(),c=user();var author=em.getReference(User.class,b);
            var p=new CommunityPost(author,null,"자유게시판","synthetic migration","dummy",true);em.persist(p);
            var comment=new CommunityComment(p,author,null,"dummy comment");comment.changeAnonymous(true);em.persist(comment);
            var r1=new ChatRoom(a,b,ChatRoomCategory.COMMUNITY,p,null);em.persist(r1);
            var r2=new ChatRoom(c,b,ChatRoomCategory.COMMUNITY,null,comment);em.persist(r2);
            return new long[]{r1.getChatRoomId(),r2.getChatRoomId(),p.getPostId(),comment.getCommentId()};
        });
        jdbc.update("update CHAT_ROOM set room_key=substring_index(room_key,':',3) where chat_room_id in (?,?)",ids[0],ids[1]);
        applySql("20261011_chat_anonymous_keys.sql",jdbc);
        var once=jdbc.queryForList("select chat_room_id,room_key from CHAT_ROOM where chat_room_id in (?,?) order by chat_room_id",ids[0],ids[1]);
        check(once.get(0).get("room_key").toString().endsWith(":POST:"+ids[2])&&once.get(1).get("room_key").toString().endsWith(":COMMENT:"+ids[3]),"Anonymous legacy key conversion");
        applySql("20261011_chat_anonymous_keys.sql",jdbc);check(once.equals(jdbc.queryForList("select chat_room_id,room_key from CHAT_ROOM where chat_room_id in (?,?) order by chat_room_id",ids[0],ids[1])),"Anonymous migration not idempotent");verify();
    }
}
