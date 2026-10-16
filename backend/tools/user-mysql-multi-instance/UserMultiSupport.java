import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Fixed endpoint, unique per-run schema/account; no application/dev database fallback. */
class UserMultiSupport {
    static String database,account,password;
    static final String UUID="ffc96046-c893-11f1-8230-829c8c700e9e";
    static String db(){String s=database!=null?database:System.getenv("KKIRI_MI_DATABASE");check(s!=null&&s.matches("kkiri_user_mi_[0-9a-f]{12}"),"Unsafe schema");return s;}
    static String user(){String s=account!=null?account:System.getenv("KKIRI_MI_USER");check(s!=null&&s.matches("kkiri_umi_[0-9a-f]{12}"),"Unsafe account");return s;}
    static String password(){return password!=null?password:System.getenv("KKIRI_MI_PASSWORD");}
    static String url(){return "jdbc:mysql://127.0.0.1:3308/"+db()+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&autoReconnect=false&allowMultiQueries=false&allowLoadLocalInfile=false&connectTimeout=5000&socketTimeout=15000";}
    static Connection open()throws Exception {var c=DriverManager.getConnection(url(),user(),password());try{guard(c);return c;}catch(Throwable t){c.close();throw t;}}
    static void guard(Connection c)throws SQLException {
        try(var s=c.createStatement();var r=s.executeQuery("SELECT DATABASE(),CURRENT_USER(),USER(),VERSION(),@@server_uuid,@@hostname,@@port,@@session.time_zone,@@global.read_only,@@global.super_read_only")) {
            check(r.next()&&db().equals(r.getString(1))&&(user()+"@%").equals(r.getString(2))&&r.getString(3).startsWith(user()+"@")&&"8.0.46".equals(r.getString(4))&&UUID.equals(r.getString(5))&&"cbede7f4b30d".equals(r.getString(6))&&r.getInt(7)==3306&&Set.of("+00:00","UTC").contains(r.getString(8))&&r.getInt(9)==0&&r.getInt(10)==0,"MySQL identity mismatch");
        }
        check(c.getMetaData().getURL().startsWith("jdbc:mysql://127.0.0.1:3308/"+db()+"?"),"JDBC endpoint mismatch");
    }
    static List<Map<String,Object>> rows(Connection c,String sql,Object...args)throws SQLException{
        var list=new ArrayList<Map<String,Object>>();try(var s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(var r=s.executeQuery()){while(r.next()){var m=new LinkedHashMap<String,Object>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)m.put(r.getMetaData().getColumnLabel(i),r.getObject(i));list.add(m);}}}return list;
    }
    static long number(Connection c,String sql,Object...args)throws SQLException {return ((Number)rows(c,sql,args).getFirst().values().iterator().next()).longValue();}
    static int exec(Connection c,String sql,Object...args)throws SQLException {try(var s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s.executeUpdate();}}
    static void grants(Connection c)throws SQLException {
        var rs=rows(c,"SHOW GRANTS");check(rs.size()==2,"Unexpected roles/grants");boolean usage=false, schema=false;
        for(var row:rs){String g=row.values().iterator().next().toString();if(g.equals("GRANT USAGE ON *.* TO `"+user()+"`@`%`"))usage=true;
            if(g.equals("GRANT ALL PRIVILEGES ON `"+db().replace("_","\\_")+"`.* TO `"+user()+"`@`%`"))schema=true;}
        check(usage&&schema,"Unexpected account privileges");
    }
    static String hash(String s)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
    static String key(String scope,String subject)throws Exception{return hash(scope+":"+subject);}
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void write(Path file,Object obj)throws Exception {Files.writeString(file,AuthMysqlConcurrency.json(obj),StandardCharsets.UTF_8);}
    static Map<String,Object> error(Throwable t){var list=new ArrayList<Object>();for(Throwable e=t;e!=null;e=e.getCause()){var m=new LinkedHashMap<String,Object>();m.put("class",e.getClass().getName());if(e instanceof AssertionError)m.put("assertion",Objects.toString(e.getMessage(),""));if(e instanceof SQLException s){m.put("sqlState",s.getSQLState());m.put("vendorCode",s.getErrorCode());}m.put("frames",Arrays.stream(e.getStackTrace()).map(Object::toString).toList());m.put("applicationFrames",Arrays.stream(e.getStackTrace()).filter(f->f.getClassName().startsWith("com.kkirikkiri.")).map(Object::toString).toList());list.add(m);}return Map.of("causes",list);}
}
