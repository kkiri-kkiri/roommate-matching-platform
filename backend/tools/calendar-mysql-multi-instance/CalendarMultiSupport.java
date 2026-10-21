package com.kkirikkiri.calendar.crawler;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import tools.jackson.databind.json.JsonMapper;

/** Shared isolated-server guards. Never falls back to project/environment DB configuration. */
final class CalendarMultiSupport {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static String db(){String v=System.getenv("CM_DB");check(v!=null&&v.matches("calendarmulti[0-9a-f]{12}"),"Unsafe Calendar schema");return v;}
    static String account(){String v=System.getenv("CM_USER");check(v!=null&&v.matches("calmulti[0-9a-f]{12}")&&v.substring(8).equals(db().substring(13)),"Unsafe Calendar account");return v;}
    static String zone(){return "Asia/Seoul".equals(System.getenv("CM_JDBC_ZONE"))?"Asia/Seoul":"UTC";}
    static String url(){return "jdbc:mysql://127.0.0.1:3307/"+db()+"?connectionTimeZone="+zone()+"&forceConnectionTimeZoneToSession=true&autoReconnect=false&allowMultiQueries=false&allowLoadLocalInfile=false&connectTimeout=5000&socketTimeout=15000";}
    static Connection open()throws SQLException{Connection c=DriverManager.getConnection(url(),account(),System.getenv("CM_PASSWORD"));try{guard(c);return c;}catch(Throwable t){c.close();throw t;}}
    static void guard(Connection c)throws SQLException{
        var r=rows(c,"SELECT DATABASE() db,CURRENT_USER() account,VERSION() version,@@server_uuid uuid,@@hostname host,@@session.time_zone zone").getFirst();
        String owner=System.getenv("CM_MANAGED_OWNER");
        boolean identity;
        if(owner==null) {
            identity="53ab727e-c847-11f1-9aaf-bad504fada53".equals(r.get("uuid"))&&"e15a46faca0b".equals(r.get("host"));
        } else {
            // Fresh-container runner verifies Docker ownership/image/loopback binding before supplying this identity.
            String uuid=System.getenv("CM_MANAGED_UUID");
            check(owner.matches("[0-9a-f]{12}")&&db().equals("calendarmulti"+owner)
                    &&uuid!=null&&uuid.matches("[0-9a-f-]{36}"),"Invalid disposable Calendar identity");
            identity=uuid.equals(r.get("uuid"))&&"kkiri-calendar-managed".equals(r.get("host"));
        }
        check(db().equals(r.get("db"))&&(account()+"@%").equals(r.get("account"))&&"8.0.46".equals(r.get("version"))&&identity,"Unexpected database identity");
        check((zone().equals("UTC")?Set.of("UTC","+00:00"):Set.of("Asia/Seoul","+09:00")).contains(r.get("zone")),"Unexpected JDBC session zone");
        check(c.getMetaData().getURL().startsWith("jdbc:mysql://127.0.0.1:3307/"+db()+"?"),"Unexpected JDBC URL");
    }
    static DataSource source(){return new AbstractDataSource(){public Connection getConnection()throws SQLException{return open();}public Connection getConnection(String u,String p)throws SQLException{throw new SQLException("Alternate credentials forbidden");}};}
    static void grants(Connection c)throws SQLException{var all=rows(c,"SHOW GRANTS").stream().map(r->r.values().iterator().next().toString()).toList();check(all.size()==2&&all.contains("GRANT USAGE ON *.* TO `"+account()+"`@`%`")&&all.contains("GRANT SELECT, INSERT, UPDATE, DELETE ON `"+db()+"`.* TO `"+account()+"`@`%`"),"Runtime account is not strictly schema-local DML-only");}
    static List<Map<String,Object>> rows(Connection c,String sql,Object...args)throws SQLException{
        var all=new ArrayList<Map<String,Object>>();try(var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(var rs=p.executeQuery()){while(rs.next()){var m=new LinkedHashMap<String,Object>();for(int i=1;i<=rs.getMetaData().getColumnCount();i++)m.put(rs.getMetaData().getColumnLabel(i),rs.getObject(i));all.add(m);}}}return all;
    }
    static long number(Connection c,String sql,Object...args)throws SQLException{return ((Number)rows(c,sql,args).getFirst().values().iterator().next()).longValue();}
    static int exec(Connection c,String sql,Object...args)throws SQLException{try(var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p.executeUpdate();}}
    static Map<String,Object> error(Throwable t){var list=new ArrayList<Object>();for(Throwable x=t;x!=null;x=x.getCause()){var m=new LinkedHashMap<String,Object>();m.put("type",x.getClass().getSimpleName());if(x instanceof AssertionError)m.put("assertion",Objects.toString(x.getMessage(),""));if(x instanceof org.springframework.beans.factory.BeanCreationException b)m.put("bean",Objects.toString(b.getBeanName(),""));if(x instanceof org.springframework.beans.factory.NoUniqueBeanDefinitionException b)m.put("candidates",Objects.toString(b.getBeanNamesFound(),""));if(x instanceof SQLException s){m.put("mysqlCode",s.getErrorCode());m.put("sqlState",Objects.toString(s.getSQLState(),""));}m.put("applicationFrames",Arrays.stream(x.getStackTrace()).filter(f->f.getClassName().startsWith("com.kkirikkiri.")).map(Object::toString).toList());list.add(m);}return Map.of("causes",list);}
    static void check(boolean value,String msg){if(!value)throw new AssertionError(msg);}
    static void write(Path file,Object value)throws Exception{Files.writeString(file,JSON.writeValueAsString(value));}
}
