package kmi;
import java.sql.*;
import java.util.*;
import java.nio.file.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

/** Fixed loopback endpoint and per-run database/account; no .env or configurable DB URL. */
class MatchingMultiSupport {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static String db(){String v=System.getenv("KM_DATABASE");check(v!=null&&v.matches("kkiri_matching_mi_[a-f0-9]{12}"),"Unsafe schema");return v;}
    static String user(){String v=System.getenv("KM_USER");check(v!=null&&v.matches("kkiri_kmi_[a-f0-9]{12}"),"Unsafe account");return v;}
    static String url(String database){return "jdbc:mysql://127.0.0.1:3311/"+database+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&autoReconnect=false&allowMultiQueries=false&allowLoadLocalInfile=false&connectTimeout=5000&socketTimeout=20000";}
    static void identity(Connection c)throws Exception{var r=rows(c,"SELECT VERSION() version,@@server_uuid uuid,@@hostname hostname,@@session.time_zone zone").getFirst();check("8.0.46".equals(r.get("version"))&&System.getenv("KM_UUID").equals(r.get("uuid"))&&System.getenv("KM_HOSTNAME").equals(r.get("hostname"))&&Set.of("+00:00","UTC").contains(r.get("zone")),"MySQL identity mismatch");}
    static Connection open()throws Exception{var c=DriverManager.getConnection(url(db()),user(),System.getenv("KM_PASSWORD"));try{guard(c);return c;}catch(Throwable t){c.close();throw t;}}
    static void guard(Connection c)throws Exception{identity(c);check(db().equals(rows(c,"SELECT DATABASE()").getFirst().values().iterator().next()),"Wrong schema");check(rows(c,"SELECT CURRENT_USER()").getFirst().values().iterator().next().toString().startsWith(user()+"@"),"Wrong account");}
    static DataSource source(){return new DriverManagerDataSource(url(db()),user(),System.getenv("KM_PASSWORD"));}
    static List<Map<String,Object>> rows(Connection c,String sql,Object...args)throws SQLException{var result=new ArrayList<Map<String,Object>>();try(var s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){while(r.next()){var row=new LinkedHashMap<String,Object>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));result.add(row);}}}return result;}
    static long number(Connection c,String sql,Object...args)throws SQLException{return ((Number)rows(c,sql,args).getFirst().values().iterator().next()).longValue();}
    static int exec(Connection c,String sql,Object...args)throws SQLException{try(var s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s.executeUpdate();}}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static Map<String,Object> error(Throwable failure){var errors=new ArrayList<Object>();for(Throwable t=failure;t!=null;t=t.getCause()){var e=new LinkedHashMap<String,Object>();e.put("class",t.getClass().getName());if(t instanceof AssertionError)e.put("assertion",Objects.toString(t.getMessage(),""));if(t instanceof SQLException s){e.put("sqlState",s.getSQLState());e.put("vendorCode",s.getErrorCode());}e.put("frames",Arrays.stream(t.getStackTrace()).limit(8).map(Object::toString).toList());errors.add(e);}return Map.of("causes",errors);}
    static void write(Path path,Object value)throws Exception{Files.writeString(path,JSON.writeValueAsString(value));}
    static void event(String kind,Object value){System.out.println("KM_EVENT "+JSON.writeValueAsString(Map.of("kind",kind,"data",value)));System.out.flush();}
}
