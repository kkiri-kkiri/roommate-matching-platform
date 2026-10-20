import com.kkirikkiri.chat.dto.*;
import com.kkirikkiri.chat.entity.*;
import com.kkirikkiri.user.entity.*;
import com.kkirikkiri.matching.entity.RoommateMatch;
import com.kkirikkiri.global.exception.ErrorCode;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.hibernate.SessionFactory;

public class ChatMysqlPerformance extends ChatMysqlValidation {
    record Scale(long owner,List<Long> rooms,List<Long> targets,int perRoom){}
    static final Map<Integer,Scale> scales=new LinkedHashMap<>();
    static void runBefore()throws Exception{
        test("bounded-performance-fixtures",()->{
            for(int count:List.of(1,10,60))scales.put(count,fixtureScale(count,200));
            check(jdbc.queryForObject("select count(*) from CHAT_MESSAGE",Long.class)<20000,"Unexpected fixture size");
        });
        test("performance-before-indexes",()->measure("before"));
        test("message-bounded-large-history",ChatMysqlPerformance::messageHistory);
    }
    static void runAfter()throws Exception{
        test("performance-after-indexes",()->measure("after"));
        test("room-list-pagination-search-sort-and-serialization-with-indexes",ChatMysqlPerformance::rooms);
    }
    static Scale fixtureScale(int count,int perRoom){
        long owner=user();var rooms=new ArrayList<Long>();var targets=new ArrayList<Long>();
        for(int i=0;i<count;i++){
            long target=user();ChatMysqlChecks.profile(target,"perf"+owner+"x"+i);var f=room(owner,target);rooms.add(f.room());targets.add(target);
            tx().executeWithoutResult(s->em.persist(new RoommateMatch(em.getReference(User.class,owner),em.getReference(User.class,target),80,null)));
            final int ordinal=i;
            tx().executeWithoutResult(s->jdbc.batchUpdate("insert into CHAT_MESSAGE(chat_room_id,sender_id,content,sent_at,is_read) values(?,?,?,?,?)",new org.springframework.jdbc.core.BatchPreparedStatementSetter(){
                public int getBatchSize(){return perRoom;}
                public void setValues(PreparedStatement ps,int n)throws SQLException{ps.setLong(1,f.room());ps.setLong(2,n%2==0?target:owner);ps.setString(3,"synthetic-"+n);ps.setTimestamp(4,Timestamp.valueOf(FIXED.plusSeconds(ordinal).plusMinutes(n)));ps.setBoolean(5,n%4==0);}
            }));
        }return new Scale(owner,rooms,targets,perRoom);
    }
    static void rooms(){
        var s=scales.get(60);var first=chat.getRooms(s.owner,new ChatRoomListRequest("ALL",null));
        check(first.content().size()==50&&first.totalElements()==60&&first.totalPages()==2&&first.counts().all()==60,"Room metadata");
        var second=chat.getRooms(s.owner,new ChatRoomListRequest("ALL",null,1,50));
        var all=new ArrayList<Long>(first.content().stream().map(ChatRoomResponse::id).toList());all.addAll(second.content().stream().map(ChatRoomResponse::id).toList());
        check(all.equals(s.rooms.reversed())&&new HashSet<>(all).size()==60,"DB latest sort/page");
        check(first.content().stream().allMatch(r->r.unreadCount()==s.perRoom/4&&r.counterpart().matchScore()==80),"Batch score/unread mapping");
        chat.updateSettings(s.owner,s.rooms.getFirst(),new ChatRoomSettingsRequest(true,null));
        check(chat.getRooms(s.owner,null).content().getFirst().id().equals(s.rooms.getFirst()),"Pinned priority");
        var found=chat.getRooms(s.owner,new ChatRoomListRequest("ROOMMATE","perf"+s.owner+"x59"));check(found.totalElements()==1,"DB nickname search");
        check(chat.getRooms(s.owner,new ChatRoomListRequest("COMMUNITY",null)).totalElements()==0,"Category filter");
        error(ErrorCode.VALIDATION_ERROR,()->chat.getRooms(s.owner,new ChatRoomListRequest("ALL",null,0,101)));
        chat.leaveRoom(s.owner,s.rooms.get(1));
        check(chat.getRooms(s.owner,null).totalElements()==59,"Left room visible");
        error(ErrorCode.FORBIDDEN,()->chat.getRoom(s.owner,s.rooms.get(1)));
        // Restore this synthetic measurement fixture with a new counterpart/room; left rows stay preserved.
        long target=user();ChatMysqlChecks.profile(target,"replacement"+target);var replacement=room(s.owner,target);
        tx().executeWithoutResult(t->{em.persist(new RoommateMatch(em.getReference(User.class,s.owner),em.getReference(User.class,target),80,null));});
        jdbc.update("insert into CHAT_MESSAGE(chat_room_id,sender_id,content,sent_at,is_read) values(?,?,?, ?,false)",replacement.room(),target,"replacement",FIXED);
        // Performance records the exact active count and varying unread distribution after this regression.
        String json=JSON.writeValueAsString(chat.getRooms(s.owner,null));check(json.contains("\"counts\"")&&json.contains("\"content\""),"Room DTO serialization");
    }
    static void measure(String phase)throws Exception{
        var evidence=new ArrayList<Map<String,Object>>();
        boolean failed=false;
        for(var entry:scales.entrySet()){
            var s=entry.getValue();var timings=new ArrayList<Double>();String outcome="PASS";String errorType="";
            List<StatementData> queries=List.of();int bytes=0;long allocation=0;
            for(int iteration=0;iteration<5;iteration++){
                var stats=em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();stats.clear();
                CapturingDataSource.STATEMENTS.get().clear();CapturingDataSource.ENABLED.set(true);
                long heapBefore=usedHeap(),start=System.nanoTime();ChatRoomPageResponse response;
                try{response=chat.getRooms(s.owner,null);}
                catch(org.springframework.dao.DataAccessException failure){
                    timings.add((System.nanoTime()-start)/1e6);queries=List.copyOf(CapturingDataSource.STATEMENTS.get());
                    outcome="FAIL";errorType=failure.getClass().getName();if(failure.getMostSpecificCause() instanceof SQLException sql)errorType+=" SQLState="+sql.getSQLState()+" code="+sql.getErrorCode();failed=true;break;
                }finally{CapturingDataSource.ENABLED.set(false);}
                timings.add((System.nanoTime()-start)/1e6);queries=List.copyOf(CapturingDataSource.STATEMENTS.get());
                check(queries.size()<=7,"N+1 query count: "+queries.size());
                check(stats.getEntityStatistics(ChatRoom.class.getName()).getLoadCount()==0,"Rooms loaded as entities");
                check(stats.getEntityStatistics(ChatRoomMember.class.getName()).getLoadCount()==0,"Members loaded as entities");
                check(response.content().size()==Math.min(entry.getKey(),50),"Page cap changed");
                bytes=JSON.writeValueAsString(response).getBytes(java.nio.charset.StandardCharsets.UTF_8).length;allocation=Math.max(allocation,usedHeap()-heapBefore);
                Thread.sleep(75);
            }
            var plans=new ArrayList<Map<String,Object>>();
            for(var q:queries){
                var item=new LinkedHashMap<String,Object>();item.put("sql",q.sql());item.put("parameters",q.parameters().entrySet().stream().collect(java.util.stream.Collectors.toMap(e->e.getKey().toString(),e->Objects.toString(e.getValue(),"NULL"))));
                item.put("explain",explain(q,false));
                if(entry.getKey()==60&&phase.equals("after")&&outcome.equals("PASS"))item.put("analyze",explain(q,true));
                plans.add(item);
            }
            var record=new LinkedHashMap<String,Object>();record.put("requestedScale",entry.getKey());record.put("activeRooms",jdbc.queryForObject("select count(*) from CHAT_ROOM_MEMBER where user_id=? and is_left=0",Integer.class,s.owner));record.put("messagesInOriginalRooms",s.rooms.size()*s.perRoom);record.put("requestedRepetitions",5);record.put("milliseconds",timings);record.put("outcome",outcome);record.put("error",errorType);record.put("sqlCount",queries.size());record.put("responseUtf8Bytes",bytes);record.put("observedHeapDeltaMaxBytes",allocation);record.put("plans",plans);evidence.add(record);
        }
        Files.writeString(output.resolve("performance-"+phase+".json"),JSON.writeValueAsString(evidence));
        check(!failed,"At least one scale exceeded the unchanged 3000ms SELECT guard; see performance-"+phase+".json");
    }
    static void followUp()throws Exception{
        // Reuse the first run's owned synthetic fixtures; no extra large data population.
        var groups=new LinkedHashMap<Long,List<Long>>();
        for(var row:jdbc.queryForList("select user_id,nickname from USER_PROFILE where nickname regexp '^perf[0-9]+x[0-9]+$' order by user_id")){
            String nick=row.get("nickname").toString();long owner=Long.parseLong(nick.substring(4,nick.indexOf('x')));groups.computeIfAbsent(owner,k->new ArrayList<>()).add(((Number)row.get("user_id")).longValue());
        }
        for(var e:groups.entrySet())if(Set.of(1,10,60).contains(e.getValue().size())&&!scales.containsKey(e.getValue().size())){
            var rooms=new ArrayList<Long>();for(long target:e.getValue())rooms.add(jdbc.queryForObject("select r.chat_room_id from CHAT_ROOM r join CHAT_ROOM_MEMBER a on a.chat_room_id=r.chat_room_id and a.user_id=? join CHAT_ROOM_MEMBER b on b.chat_room_id=r.chat_room_id and b.user_id=? where r.category='ROOMMATE'",Long.class,e.getKey(),target));
            scales.put(e.getValue().size(),new Scale(e.getKey(),rooms,e.getValue(),200));
        }
        check(scales.size()==3,"Missing existing bounded performance fixtures");
        try{
            test("owned-index-rollback-with-FK-support-restored",()->{
                jdbc.execute("CREATE INDEX idx_chat_member_fk_restore ON CHAT_ROOM_MEMBER(user_id)");
                jdbc.execute("CREATE INDEX idx_chat_message_fk_restore ON CHAT_MESSAGE(chat_room_id)");
                jdbc.execute("DROP INDEX idx_chat_member_user_active_room ON CHAT_ROOM_MEMBER");
                jdbc.execute("DROP INDEX idx_chat_message_room_latest ON CHAT_MESSAGE");
                jdbc.execute("DROP INDEX idx_chat_message_room_unread ON CHAT_MESSAGE");
            });
            test("matched-data-performance-before-indexes",()->measure("before"));
        }finally{
            for(String sql:List.of("CREATE INDEX idx_chat_message_room_latest ON CHAT_MESSAGE(chat_room_id,sent_at,message_id)","CREATE INDEX idx_chat_message_room_unread ON CHAT_MESSAGE(chat_room_id,is_read,sender_id)","CREATE INDEX idx_chat_member_user_active_room ON CHAT_ROOM_MEMBER(user_id,is_left,chat_room_id)"))jdbc.execute(sql);
        }
        test("matched-data-performance-after-indexes",()->measure("after"));
        test("room-list-pagination-search-sort-and-serialization-with-indexes",ChatMysqlPerformance::rooms);
        test("current-index-final-metadata",ChatMysqlPerformance::indexMetadata);
    }
    static long usedHeap(){return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();}
    static List<Map<String,Object>> explain(StatementData q,boolean analyze){
        return jdbc.queryForList((analyze?"EXPLAIN ANALYZE ":"EXPLAIN ")+q.sql(),q.parameters().values().toArray());
    }
    static void messageHistory()throws Exception{
        var f=fixture();tx().executeWithoutResult(s->jdbc.batchUpdate("insert into CHAT_MESSAGE(chat_room_id,sender_id,content,sent_at,is_read) values(?,?,?,?,false)",new org.springframework.jdbc.core.BatchPreparedStatementSetter(){
            public int getBatchSize(){return 3000;}
            public void setValues(PreparedStatement p,int n)throws SQLException{p.setLong(1,f.room());p.setLong(2,f.writer());p.setString(3,"history-"+n);p.setTimestamp(4,Timestamp.valueOf(FIXED.plusSeconds(n/5)));}
        }));
        var start=System.nanoTime();var ids=new ArrayList<Long>();var page=chat.getMessages(f.reader(),f.room(),null);int queries=0;
        while(true){ids.addAll(page.content().stream().map(ChatMessageResponse::id).toList());queries++;if(page.last())break;page=chat.getMessages(f.reader(),f.room(),new ChatMessageListRequest(0,100,null,ids.getLast(),null));Thread.sleep(20);}
        check(ids.size()==3000&&new HashSet<>(ids).size()==3000,"Large history gaps");
        check(ids.equals(ids.stream().sorted(Comparator.reverseOrder()).toList()),"Large history order");
        Files.writeString(output.resolve("large-message-history.json"),JSON.writeValueAsString(Map.of("messages",3000,"pages",queries,"elapsedMs",(System.nanoTime()-start)/1e6,"pageCap",100,"heapUsedBytes",usedHeap())));
    }
    static void indexMetadata()throws Exception{
        Map<String,List<String>> expected=Map.of("idx_chat_message_room_latest",List.of("chat_room_id","sent_at","message_id"),"idx_chat_message_room_unread",List.of("chat_room_id","is_read","sender_id"),"idx_chat_member_user_active_room",List.of("user_id","is_left","chat_room_id"));
        var rows=jdbc.queryForList("select table_name,index_name,non_unique,seq_in_index,column_name from information_schema.statistics where table_schema=database() and table_name in ('chat_message','chat_room_member') order by table_name,index_name,seq_in_index");
        for(var e:expected.entrySet()){var columns=rows.stream().filter(r->e.getKey().equals(r.get("INDEX_NAME"))).map(r->r.get("COLUMN_NAME").toString()).toList();check(columns.equals(e.getValue()),"Wrong index column order: "+e.getKey());}
        expectedSql(1061,()->jdbc.execute("CREATE INDEX idx_chat_message_room_latest ON CHAT_MESSAGE(chat_room_id,sent_at,message_id)"));
        Files.writeString(output.resolve(schema+".indexes.json"),JSON.writeValueAsString(rows));
    }
}
