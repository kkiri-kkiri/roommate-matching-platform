import java.nio.file.Files;
import java.util.*;

public class ChatMysqlFinalPerformance extends ChatMysqlValidation {
    static void run() throws Exception {
        var before=ChatMysqlPreservation.capture("performance-original-before");
        try {
            test("additive-100-room-200-message-fixture",()->{
                int existing=jdbc.queryForObject("select count(*) from USER_PROFILE where nickname regexp '^perf[0-9]+x99$'",Integer.class);
                check(existing<=1,"Ambiguous retained 100-room fixture");
                if(existing==0) {
                    var scale=ChatMysqlPerformance.fixtureScale(100,200);
                    Files.writeString(output.resolve("additive-100-fixture.json"),JSON.writeValueAsString(Map.of("owner",scale.owner(),"rooms",scale.rooms(),"targets",scale.targets(),"perRoom",200,"preserved",true)));
                }
                ChatMysqlPreservation.verify(before,"performance-original-after-fixture");
            });
            check("PASS".equals(results.getLast().get("result")),"Fixture validation failed; stop before measurements");
            close();open(text(manifest,"currentDb"),false,true);
            ChatMysqlQueryComparison.run();
        } finally { ChatMysqlPreservation.verify(before,"performance-original-final"); }
    }
}
