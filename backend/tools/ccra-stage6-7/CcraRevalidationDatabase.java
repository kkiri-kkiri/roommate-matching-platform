import e2e.E2eDatabase;
import java.nio.file.*;
import java.sql.*;
import java.security.MessageDigest;
import java.util.*;
import com.kkirikkiri.user.entity.*;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

/** Explicitly authorized retained CCRA resource only. No reset, cleanup or DELETE operation. */
public class CcraRevalidationDatabase {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String OWNER = "82cdabce6030";
    static void check(boolean valid, String message) { if (!valid) throw new IllegalStateException(message); }
    static Connection connect() throws Exception {
        check(("kkiri_e2e_" + OWNER).equals(System.getenv("E2E_DATABASE")), "Wrong schema");
        check(("e2e_" + OWNER).equals(System.getenv("E2E_DB_USER")), "Wrong account");
        check("f8a3a22f-ce99-11f1-bd9a-621e38ee5f3d".equals(System.getenv("E2E_DB_UUID")), "Wrong UUID");
        check("1".equals(System.getenv("E2E_RETAIN_RESOURCES")), "Retention required");
        var method = E2eDatabase.class.getDeclaredMethod("guardedConnection"); method.setAccessible(true);
        return (Connection) method.invoke(null);
    }
    static Path directory() {
        Path p = Path.of(System.getenv("E2E_EVIDENCE_DIR")).toAbsolutePath().normalize();
        check(p.getFileName().toString().matches("[a-f0-9]{12}")
            && p.getParent().equals(Path.of("backend/build/full-service-e2e").toAbsolutePath().normalize()), "Unsafe evidence directory");
        return p;
    }
    static List<Map<String,Object>> rows(Connection c, String sql, Object... args) throws Exception {
        try (var s = c.prepareStatement(sql)) {
            for (int i=0; i<args.length; i++) s.setObject(i+1,args[i]);
            try (var r = s.executeQuery()) {
                var out = new ArrayList<Map<String,Object>>();
                while(r.next()) {
                    var row = new LinkedHashMap<String,Object>();
                    for(int i=1; i<=r.getMetaData().getColumnCount(); i++) row.put(r.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT),r.getObject(i));
                    out.add(row);
                }
                return out;
            }
        }
    }
    static String digest(List<Object> values) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(values)));
    }
    static Map<String,Object> fingerprints(Connection c, Map<String,Object> prior) throws Exception {
        var result = new LinkedHashMap<String,Object>();
        for (var item : rows(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE' ORDER BY table_name")) {
            String table = item.get("table_name").toString();
            check(table.matches("[a-z_]+"), "Unexpected table identifier");
            List<String> columns = new ArrayList<>(), keys = new ArrayList<>();
            if (prior != null) {
                var original = (Map<?,?>) prior.get(table);
                columns.addAll((List<String>) original.get("columns")); keys.addAll((List<String>)original.get("keys"));
            } else {
                for(var col:rows(c,"SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? ORDER BY ordinal_position",table)) columns.add(col.get("column_name").toString());
                for(var col:rows(c,"SELECT column_name FROM information_schema.key_column_usage WHERE table_schema=DATABASE() AND table_name=? AND constraint_name='PRIMARY' ORDER BY ordinal_position",table)) keys.add(col.get("column_name").toString());
            }
            check(!keys.isEmpty() && columns.stream().allMatch(s->s.matches("[a-z_]+")),"Missing PK/unsafe columns");
            String projection = String.join(",",columns.stream().map(s->"`"+s+"`").toList());
            var hashes = new TreeMap<String,String>();
            for(var row:rows(c,"SELECT "+projection+" FROM `"+table+"`")) {
                hashes.put(digest(keys.stream().map(row::get).toList()), digest(columns.stream().map(row::get).toList()));
            }
            result.put(table,Map.of("columns",columns,"keys",keys,"rows",hashes,"count",hashes.size()));
        }
        return result;
    }
    static Map<String,Object> compare(Map<String,Object> before, Map<String,Object> after) {
        var result = new LinkedHashMap<String,Object>();
        for(var entry:before.entrySet()) {
            var oldRows=(Map<String,String>)((Map<?,?>)entry.getValue()).get("rows");
            var newRows=(Map<String,String>)((Map<?,?>)after.get(entry.getKey())).get("rows");
            long missing=oldRows.keySet().stream().filter(key->!newRows.containsKey(key)).count();
            long changed=oldRows.keySet().stream().filter(key->newRows.containsKey(key)&&!oldRows.get(key).equals(newRows.get(key))).count();
            result.put(entry.getKey(),Map.of("original",oldRows.size(),"current",newRows.size(),"missing",missing,"changed",changed));
        }
        return result;
    }
    static List<Map<String,Object>> indexes(Connection c) throws Exception {
        return rows(c,"SELECT index_name,non_unique,seq_in_index,column_name FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='chat_message' AND index_name='uk_chat_message_client' ORDER BY seq_in_index");
    }
    static Object migrate(Connection c) throws Exception {
        var before=fingerprints(c,null);
        Path baseline=directory().resolve("original-row-fingerprints.json");
        check(!Files.exists(baseline),"Baseline cannot be overwritten");
        Files.writeString(baseline,JSON.writeValueAsString(before));
        var identity=rows(c,"SELECT DATABASE() db,CURRENT_USER() account,VERSION() version,@@server_uuid uuid,@@hostname hostname,@@session.time_zone timezone");
        var oldColumns=rows(c,"SELECT column_name,column_type,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='chat_message' ORDER BY ordinal_position");
        boolean present=oldColumns.stream().anyMatch(row->row.get("column_name").equals("client_message_id"));
        var priorIndexes=indexes(c);
        check(!present && priorIndexes.isEmpty(),"Migration already or partially present; review before proceeding");
        String sql=Files.readString(Path.of("backend/migrations/20261023_chat_send_idempotency.sql"));
        check(sql.contains("ADD COLUMN client_message_id VARCHAR(36) NULL") && sql.contains("UNIQUE (chat_room_id, sender_id, client_message_id)")
            && !sql.matches("(?is).*\\b(DROP|DELETE|TRUNCATE|UPDATE|CREATE)\\s+.*"),"Unexpected migration content");
        // The owned schema uses Hibernate's lowercase physical table name on Linux MySQL.
        String executable=sql.lines().filter(line->!line.stripLeading().startsWith("--")).reduce("",(a,b)->a+"\n"+b)
            .replace("ALTER TABLE CHAT_MESSAGE","ALTER TABLE `chat_message`").strip().replaceFirst(";\\s*$","");
        try(var statement=c.createStatement()) { statement.execute(executable); }
        var newColumn=rows(c,"SELECT column_name,column_type,is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='chat_message' AND column_name='client_message_id'");
        var index=indexes(c);
        check(newColumn.size()==1 && newColumn.getFirst().get("column_type").equals("varchar(36)")
            && newColumn.getFirst().get("is_nullable").equals("YES"),"Column verification failed");
        check(index.size()==3 && index.stream().allMatch(row->((Number)row.get("non_unique")).intValue()==0)
            && index.stream().map(row->row.get("column_name")).toList().equals(List.of("chat_room_id","sender_id","client_message_id")),"Unique index mismatch");
        var compatibility=compare(before,fingerprints(c,before));
        check(compatibility.values().stream().allMatch(row->((Number)((Map<?,?>)row).get("missing")).longValue()==0
            && ((Number)((Map<?,?>)row).get("changed")).longValue()==0),"Migration changed original rows");
        return Map.of("identity",identity,"applied",true,"sql",executable,"column",newColumn,"index",index,
            "originalRows",compatibility,"legacyMessages",rows(c,"SELECT COUNT(*) total,SUM(client_message_id IS NULL) null_keys FROM chat_message"));
    }
    static Object actors() throws Exception {
        long dormId,facultyId;
        try(var c=connect()) {
            var row=rows(c,"SELECT dormitory_id,faculty_id FROM user_profile ORDER BY user_id LIMIT 1").getFirst();
            dormId=((Number)row.get("dormitory_id")).longValue(); facultyId=((Number)row.get("faculty_id")).longValue();
        }
        var method=E2eDatabase.class.getDeclaredMethod("factory",String.class); method.setAccessible(true);
        var factory=(LocalContainerEntityManagerFactoryBean)method.invoke(null,"validate");
        try(var em=factory.getObject().createEntityManager()) {
            var tx=em.getTransaction(); tx.begin();
            try {
                var dorm=em.find(Dormitory.class,dormId); var faculty=em.find(Faculty.class,facultyId);
                String[] emails={"E2E_LOGIN_EMAIL","E2E_SECOND_EMAIL","E2E_ADMIN_EMAIL"}, names={"E2E사용자","E2E상대","E2E관리자"};
                for(int i=0;i<3;i++) {
                    var u=new User(new BCryptPasswordEncoder().encode(System.getenv("E2E_LOGIN_PASSWORD")),System.getenv(emails[i]),i==2?UserRole.ADMIN:UserRole.USER);
                    u.verifyEmail(); em.persist(u);
                    em.persist(new UserProfile(u,names[i]+System.getenv("E2E_CCRA_NICK_SUFFIX"),"남자",dorm,faculty,null,(short)2000,"20","1학년","INTJ"));
                }
                tx.commit();
                // Read historical NULL-key messages with the migrated Entity mapping without mutation.
                var old=em.createQuery("select m from ChatMessage m where m.clientMessageId is null",com.kkirikkiri.chat.entity.ChatMessage.class).getResultList();
                check(old.stream().allMatch(m->com.kkirikkiri.chat.dto.ChatMessageResponse.from(m,"ME",m.isRead()).clientMessageId()==null),"Legacy DTO compatibility failed");
                return Map.of("appendedActors",3,"ddlMode","validate","legacyMessagesReadViaJpa",old.size());
            } catch(Throwable error) { if(tx.isActive())tx.rollback(); throw error; }
        } finally { factory.destroy(); }
    }
    static Object constraint(Connection c,long messageId) throws Exception {
        var target=rows(c,"SELECT m.message_id FROM chat_message m JOIN user u ON u.user_id=m.sender_id WHERE m.message_id=? AND m.client_message_id IS NOT NULL AND u.school_email=?",messageId,System.getenv("E2E_LOGIN_EMAIL"));
        check(target.size()==1,"Constraint probe must target this run's fresh sender/message");
        c.setAutoCommit(false);
        String state=""; int code=0;
        try(var s=c.prepareStatement("INSERT INTO chat_message(chat_room_id,sender_id,content,sent_at,is_read,client_message_id) SELECT chat_room_id,sender_id,content,sent_at,is_read,client_message_id FROM chat_message WHERE message_id=?")) {
            s.setLong(1,messageId);
            try { s.executeUpdate(); } catch(SQLException expected) { state=expected.getSQLState(); code=expected.getErrorCode(); }
        } finally { c.rollback(); c.setAutoCommit(true); }
        check(state.equals("23000")&&code==1062,"UNIQUE did not reject duplicate");
        return Map.of("messageId",messageId,"sqlState",state,"vendorCode",code,"rolledBack",true,"index",indexes(c));
    }
    public static void main(String[] args) throws Exception {
        check(args.length>=1,"Mode required"); Object result;
        try(var c=connect()) {
            result=switch(args[0]) {
                case "migrate" -> migrate(c);
                case "actors" -> actors();
                case "snapshot" -> Map.of("messages",rows(c,"SELECT message_id,chat_room_id,sender_id,content,is_read,client_message_id,CAST(sent_at AS CHAR) sent_at FROM chat_message ORDER BY message_id"));
                case "constraint" -> constraint(c,Long.parseLong(args[1]));
                case "preservation" -> {
                    var before=(Map<String,Object>)JSON.readValue(Files.readString(directory().resolve("original-row-fingerprints.json")),Map.class);
                    yield compare(before,fingerprints(c,before));
                }
                default -> throw new IllegalArgumentException("Unsupported mode");
            };
        }
        System.out.write(("CCRA_DB "+JSON.writeValueAsString(result)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
