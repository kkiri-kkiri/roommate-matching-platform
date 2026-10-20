import com.kkirikkiri.chat.dto.*;
import com.kkirikkiri.chat.entity.*;
import com.kkirikkiri.chat.repository.*;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.data.jpa.repository.Query;
import com.kkirikkiri.chat.service.ChatService;

/** Read-only reuse of retained fixtures: no provisioning, fixture creation, cleanup or index DDL. */
public class ChatMysqlQueryComparison extends ChatMysqlValidation {
    static final String LOOKUP = """
             left join ChatMessage latest on latest.messageId = (
               select newest.messageId from ChatMessage newest where newest.chatRoom = r
               order by newest.sentAt desc, newest.messageId desc limit 1)
            """;
    static final String ANTI_JOIN = """
             left join ChatMessage latest on latest.chatRoom = r and not exists (
               select newer.messageId from ChatMessage newer where newer.chatRoom = r
               and (newer.sentAt > latest.sentAt or (newer.sentAt = latest.sentAt and newer.messageId > latest.messageId)))
            """;

    static void inventory() throws Exception {
        test("readonly-inventory-" + schema, () -> {
            var record = new LinkedHashMap<String,Object>();
            record.put("schema", schema);
            record.put("sessionReadOnly", jdbc.queryForObject("select @@session.transaction_read_only", Boolean.class));
            check(Boolean.TRUE.equals(record.get("sessionReadOnly")), "Read-only session required");
            for (String table : List.of("CHAT_ROOM", "CHAT_ROOM_MEMBER", "CHAT_MESSAGE")) {
                record.put(table + "Count", jdbc.queryForObject("select count(*) from " + table, Long.class));
                record.put(table + "DDL", jdbc.queryForList("show create table " + table));
                record.put(table + "Indexes", jdbc.queryForList("show index from " + table));
            }
            Files.writeString(output.resolve(schema + ".inventory.json"), JSON.writeValueAsString(record));
        });
    }

    static void run() throws Exception {
        inventory();
        String optimized = ChatRoomRepository.class.getMethod("findRoomPage", Long.class,
                ChatRoomCategory.class, String.class, Pageable.class).getAnnotation(Query.class).value();
        check(optimized.contains(LOOKUP), "Production query changed: review the baseline before comparing");
        String baseline = optimized.replace(LOOKUP, ANTI_JOIN);
        var owners = new TreeMap<Integer,Long>();
        for (String nickname : jdbc.queryForList("select nickname from USER_PROFILE where nickname regexp '^perf[0-9]+x0$'", String.class)) {
            long owner = Long.parseLong(nickname.substring(4, nickname.indexOf('x')));
            int active = jdbc.queryForObject("select count(*) from CHAT_ROOM_MEMBER m join CHAT_ROOM r on r.chat_room_id=m.chat_room_id where m.user_id=? and m.is_left=0 and r.status='ACTIVE'", Integer.class, owner);
            if (Set.of(1,10,60,100).contains(active)) check(owners.put(active, owner) == null, "Ambiguous existing scale");
        }
        check(owners.keySet().containsAll(Set.of(1,10,60)), "Missing existing 1/10/60-room fixtures; do not initialize automatically");
        Files.writeString(output.resolve("comparison-coverage.json"), JSON.writeValueAsString(Map.of(
                "availableScales", owners.keySet(), "hundredRoomsAvailable", owners.containsKey(100),
                "fixtureWrites", false, "indexChanges", false, "selectLimitMilliseconds", 3000)));
        for (var entry : owners.entrySet()) {
            int scale = entry.getKey(); long owner = entry.getValue();
            test("same-data-room-query-comparison-" + scale, () -> compare(scale, owner, baseline, optimized));
        }
    }

    static List<ChatRoomListRow> rows(String hql, long owner, int offset) {
        var transaction = tx(); transaction.setReadOnly(true);
        return transaction.execute(status -> em.createQuery(hql, ChatRoomListRow.class)
                .setParameter("userId", owner).setParameter("category", null).setParameter("pattern", null)
                .setFirstResult(offset).setMaxResults(50).getResultList());
    }

    static void compare(int scale, long owner, String baseline, String optimized) throws Exception {
        var evidence = new LinkedHashMap<String,Object>();
        evidence.put("activeRooms", scale); evidence.put("owner", owner);
        evidence.put("distribution", jdbc.queryForList("select count(*) rooms,min(n) minMessages,max(n) maxMessages,avg(n) avgMessages from (select r.chat_room_id,count(m.message_id) n from CHAT_ROOM_MEMBER a join CHAT_ROOM r on r.chat_room_id=a.chat_room_id left join CHAT_MESSAGE m on m.chat_room_id=r.chat_room_id where a.user_id=? and a.is_left=0 and r.status='ACTIVE' group by r.chat_room_id) counts", owner));
        try {
            // One warmup per phase, five measured repetitions, cold persistence contexts.
            var oldRows = rows(baseline, owner, 0);
            var newRows = rows(optimized, owner, 0);
            check(oldRows.equals(newRows), "Latest text/time, anonymity, participant and settings projection changed");
            for (int offset = 50; offset < scale; offset += 50)
                check(rows(baseline, owner, offset).equals(rows(optimized, owner, offset)), "Later page mismatch");
            evidence.put("baselineRoomQuery", measure(() -> rows(baseline, owner, 0)));
            evidence.put("optimizedRoomQuery", measure(() -> rows(optimized, owner, 0)));
            var response = chat.getRooms(owner, null);
            var baselineService=baselineService(baseline);
            check(response.equals(readOnlyCall(()->baselineService.getRooms(owner,null))),"Full baseline/optimized response differs");
            check(response.totalElements() == scale && response.content().size() == Math.min(scale,50), "Page metadata");
            for (int i=0; i<newRows.size(); i++) {
                var row = newRows.get(i); var item = response.content().get(i);
                check(row.roomId().equals(item.id()), "Room order");
                check(Objects.equals(row.lastSentAt(), item.lastMessage() == null ? null : item.lastMessage().sentAt()), "Latest timestamp");
                check(Objects.equals(row.lastText(), item.lastMessage() == null ? null : item.lastMessage().text()), "Latest text");
                check(item.unreadCount() == context.getBean(ChatMessageRepository.class).countUnreadMessages(row.roomId(), owner), "Page unread aggregate changed");
            }
            // Separate full service timings from data-query timings; do not label one as the other.
            evidence.put("baselineService", measure(() -> readOnlyCall(()->baselineService.getRooms(owner,null))));
            evidence.put("optimizedService", measure(() -> readOnlyCall(()->chat.getRooms(owner, null))));
            evidence.put("outcome", "PASS");
        } catch (Throwable failure) {
            evidence.put("outcome", "FAIL");
            evidence.put("failureClass", failure.getClass().getName());
            throw failure;
        } finally {
            Files.writeString(output.resolve("query-comparison-" + scale + ".json"), JSON.writeValueAsString(evidence));
        }
    }

    static <T> T readOnlyCall(Supplier<T> action) {
        var transaction=tx();transaction.setReadOnly(true);
        return transaction.execute(status->action.get());
    }

    /** The old 100-room warmup already hit 3024. Do not execute that baseline again. */
    static void optimizedHundred() throws Exception {
        inventory();
        var names=jdbc.queryForList("select nickname from USER_PROFILE where nickname regexp '^perf[0-9]+x99$'",String.class);
        check(names.size()==1,"Expected one additive 100-room fixture");
        String nickname=names.getFirst();long owner=Long.parseLong(nickname.substring(4,nickname.indexOf('x')));
        var record=new LinkedHashMap<String,Object>();record.put("owner",owner);record.put("rooms",100);
        test("optimized-100-room-pages-latest-unread-and-plan",()->{
            var all=new ArrayList<ChatRoomResponse>();
            for(int page=0;page<2;page++) {
                var response=chat.getRooms(owner,new ChatRoomListRequest("ALL",null,page,50));
                check(response.totalElements()==100&&response.totalPages()==2&&response.content().size()==50,"100-room metadata");
                check(response.first()==(page==0)&&response.last()==(page==1),"Page flags");all.addAll(response.content());
            }
            var ids=jdbc.queryForList("select r.chat_room_id from CHAT_ROOM r join CHAT_ROOM_MEMBER m on m.chat_room_id=r.chat_room_id where m.user_id=? and m.is_left=0 order by r.chat_room_id desc",Long.class,owner);
            check(ids.equals(all.stream().map(ChatRoomResponse::id).toList())&&new HashSet<>(ids).size()==100,"100-room page order/gaps");
            check(all.stream().allMatch(room->room.lastMessage()!=null&&room.lastMessage().text().equals("synthetic-199")&&room.unreadCount()==50&&room.counterpart().matchScore()==80),"100-room latest/unread/score");
            chat.getRooms(owner,null); // One optimized-service warmup.
            record.put("optimizedService",measure(()->readOnlyCall(()->chat.getRooms(owner,null))));
            // Non-executing EXPLAIN of captured historical SQL with only owner binds changed.
            var source=root.resolve("backend/build/chat-mysql-validation/run-51125a3e-9f57-4981-a44d-2ed58ecd09ce/query-comparison-60.json");
            var previous=JSON.readValue(Files.readString(source),Map.class);
            var plan=(Map)((List)((Map)previous.get("baselineRoomQuery")).get("plans")).getFirst();
            var parameters=new TreeMap<Integer,Object>();
            long previousOwner=((Number)previous.get("owner")).longValue();
            ((Map<?,?>)plan.get("parameters")).forEach((key,value)->parameters.put(Integer.parseInt(key.toString()),value instanceof Number n&&n.longValue()==previousOwner?owner:value));
            record.put("baselineExplainOnly",ChatMysqlPerformance.explain(new StatementData(plan.get("sql").toString(),parameters),false));
            record.put("baselineNotReexecutedAfter3024",true);
            Files.writeString(output.resolve("optimized-100.json"),JSON.writeValueAsString(record));
        });
    }

    /** Production service construction; only findRoomPage is replaced by the historical query. */
    static ChatService baselineService(String baseline) throws Exception {
        var delegate=context.getBean(ChatRoomRepository.class);
        var annotation=ChatRoomRepository.class.getMethod("findRoomPage",Long.class,ChatRoomCategory.class,String.class,Pageable.class).getAnnotation(Query.class);
        var repository=java.lang.reflect.Proxy.newProxyInstance(ChatRoomRepository.class.getClassLoader(),new Class[]{ChatRoomRepository.class},(proxy,method,args)->{
            if(method.getName().equals("findRoomPage")) {
                var page=(Pageable)args[3];
                var content=em.createQuery(baseline,ChatRoomListRow.class).setParameter("userId",args[0]).setParameter("category",args[1]).setParameter("pattern",args[2])
                        .setFirstResult(Math.toIntExact(page.getOffset())).setMaxResults(page.getPageSize()).getResultList();
                return PageableExecutionUtils.getPage(content,page,()->em.createQuery(annotation.countQuery(),Long.class)
                        .setParameter("userId",args[0]).setParameter("category",args[1]).setParameter("pattern",args[2]).getSingleResult());
            }
            try{return method.invoke(delegate,args);}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
        });
        var constructor=ChatService.class.getConstructors()[0];
        Object[] dependencies=Arrays.stream(constructor.getParameterTypes()).map(type->type==ChatRoomRepository.class?repository:context.getBean(type)).toArray();
        return (ChatService)constructor.newInstance(dependencies);
    }

    static Map<String,Object> measure(Supplier<?> action) throws Exception {
        var timings = new ArrayList<Double>();
        List<StatementData> queries = List.of();
        for (int i=0; i<5; i++) {
            CapturingDataSource.STATEMENTS.get().clear(); CapturingDataSource.ENABLED.set(true);
            long started = System.nanoTime();
            try { action.get(); }
            finally { CapturingDataSource.ENABLED.set(false); }
            timings.add((System.nanoTime()-started)/1e6);
            queries = List.copyOf(CapturingDataSource.STATEMENTS.get());
            check(queries.size() <= 7, "Unbounded statement count");
            Thread.sleep(75);
        }
        var plans = new ArrayList<Map<String,Object>>();
        for (var query : queries) {
            var plan = new LinkedHashMap<String,Object>();
            plan.put("sql", query.sql()); plan.put("parameters", query.parameters());
            plan.put("explain", ChatMysqlPerformance.explain(query,false));
            plan.put("analyze", ChatMysqlPerformance.explain(query,true)); plans.add(plan);
        }
        return Map.of("milliseconds",timings,"medianMs",timings.stream().sorted().toList().get(2),
                "repetitions",5,"sqlCount",queries.size(),"plans",plans);
    }
}
