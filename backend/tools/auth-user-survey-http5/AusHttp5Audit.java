import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Read-only diagnostics/audit against the same guarded NEW schema; never writes database rows. */
public class AusHttp5Audit {
    public static void main(String[] args)throws Exception{
        var result=new LinkedHashMap<String,Object>();
        try(var c=UserMultiSupport.open()){
            UserMultiSupport.grants(c);c.setReadOnly(true);
            result.put("identity",UserMultiSupport.rows(c,"SELECT DATABASE() db,CURRENT_USER() account,VERSION() version,@@server_uuid uuid,@@hostname hostname,@@session.time_zone zone"));
            result.put("grants",UserMultiSupport.rows(c,"SHOW GRANTS"));
            result.put("jvmZone",ZoneId.systemDefault().toString());result.put("jvmNow",LocalDateTime.now().toString());
            result.put("databaseTime",UserMultiSupport.rows(c,"SELECT DATE_FORMAT(NOW(),'%Y-%m-%d %H:%i:%s') sessionNow,DATE_FORMAT(UTC_TIMESTAMP(),'%Y-%m-%d %H:%i:%s') utcNow"));
            LocalDateTime sample=LocalDateTime.of(2000,1,2,3,4,5);
            try(var s=c.prepareStatement("SELECT CAST(? AS DATETIME) sample")){
                s.setObject(1,sample);
                try(var r=s.executeQuery()){
                    r.next();var diagnostic=new LinkedHashMap<String,Object>();
                    diagnostic.put("boundLocalDateTime",sample.toString());diagnostic.put("databaseLiteral",r.getString(1));
                    diagnostic.put("directJdbcLocalDateTime",r.getObject(1,LocalDateTime.class).toString());
                    diagnostic.put("hibernateTimestampWrap",org.hibernate.type.descriptor.java.LocalDateTimeJavaType.INSTANCE.wrap(r.getTimestamp(1),null).toString());
                    result.put("localDateTimeMapping",diagnostic);
                }
            }
            try(var s=c.prepareStatement("SELECT CAST(? AS DATETIME) sample")){
                s.setTimestamp(1,Timestamp.valueOf(sample));
                try(var r=s.executeQuery()){r.next();result.put("timestampBindDatabaseLiteral",r.getString(1));}
            }
            try(var s=c.prepareStatement("SELECT CAST(? AS DATETIME) sample")){
                s.setObject(1,Timestamp.valueOf(sample));
                try(var r=s.executeQuery()){r.next();result.put("objectTimestampBindDatabaseLiteral",r.getString(1));}
            }
            var counts=new TreeMap<String,Long>();var ddl=new TreeMap<String,Object>();
            for(var row:UserMultiSupport.rows(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name")){
                String table=row.values().iterator().next().toString();UserMultiSupport.check(table.matches("[a-z_]+"),"Unsafe audit table name");
                counts.put(table,UserMultiSupport.number(c,"SELECT COUNT(*) FROM `"+table+"`"));ddl.put(table,UserMultiSupport.rows(c,"SHOW CREATE TABLE `"+table+"`"));
            }
            result.put("rows",counts);result.put("ddl",ddl);result.put("allTablesEmpty",counts.size()==31&&counts.values().stream().allMatch(n->n==0));
        }
        Path target=Path.of(args[0]);UserMultiSupport.write(target,result);
        System.out.println("AUDIT evidence="+target+" empty="+result.get("allTablesEmpty"));
        System.out.println("TIME_MAPPING "+AuthMysqlConcurrency.json(result.get("localDateTimeMapping"))+" timestampBindDb="+result.get("timestampBindDatabaseLiteral")+" objectTimestampBindDb="+result.get("objectTimestampBindDatabaseLiteral"));
    }
}
