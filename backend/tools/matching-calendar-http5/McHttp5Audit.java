package kmi;
import java.nio.file.*;
import java.util.*;
import static kmi.MatchingMultiSupport.*;
/** Only the newly owned schema via its restricted account; no mutation. */
public class McHttp5Audit extends MatchingMultiInstance {
    public static void main(String[] args)throws Exception{
        if(args.length==2&&args[1].equals("--diagnose")){
            output=Path.of(args[0]).toRealPath();boolean ready=false;try{a=start(18095,1);ready=true;}catch(Throwable t){write(output.resolve("error.json"),error(t));}finally{for(var s:servers)try{s.stop();}catch(Throwable t){events.add(Map.of("kind","stopFailure","error",error(t)));}write(output.resolve("servers.json"),events);write(output.resolve("summary.json"),Map.of("ready",ready,"httpRequests",0,"processesStopped",servers.stream().noneMatch(s->s.process.isAlive())));HTTP.close();}return;
        }
        var result=new LinkedHashMap<String,Object>();try(var c=open()){
            result.put("identity",rows(c,"SELECT VERSION() version,DATABASE() db,CURRENT_USER() account,@@server_uuid uuid,@@hostname hostname,@@session.time_zone zone,@@collation_database collation"));result.put("grants",rows(c,"SHOW GRANTS"));
            var counts=new TreeMap<String,Long>();var ddl=new TreeMap<String,String>();
            for(var row:rows(c,"SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name")){String t=row.values().iterator().next().toString();check(t.matches("[a-z_]+"),"Unexpected table");counts.put(t,number(c,"SELECT COUNT(*) FROM `"+t+"`"));ddl.put(t,rows(c,"SHOW CREATE TABLE `"+t+"`").getFirst().values().stream().skip(1).findFirst().orElseThrow().toString().replaceAll(" AUTO_INCREMENT=\\d+",""));}
            result.put("rows",counts);result.put("ddl",ddl);result.put("allEmpty",counts.values().stream().allMatch(n->n==0));result.put("accountConnections",number(c,"SELECT COUNT(*) FROM information_schema.processlist WHERE USER=? AND DB=?",user(),db()));
        }
        result.put("readOnly",true);write(Path.of(args[0]),result);System.out.println("New-schema read-only audit written");
    }
}
