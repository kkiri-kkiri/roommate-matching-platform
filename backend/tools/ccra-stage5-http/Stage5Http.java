package kmi;

import static kmi.MatchingMultiSupport.*;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import com.kkirikkiri.user.entity.UserRole;
import tools.jackson.databind.JsonNode;

/** Additional cases only. Real TCP HTTP/Boot/MySQL; existing CCRA cases never invoked. */
public class Stage5Http extends CcraMysqlValidation {
    record Scenario(String name, String expected, Work work) {}
    record Upload(String name, String mime, byte[] bytes) {}
    static final List<Map<String,Object>> responses = new ArrayList<>();
    static final List<Map<String,Object>> sockets = new ArrayList<>();
    static final byte[] PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
    static Path images;

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("PREPARE")) {
            var names = catalog().stream().map(Scenario::name).toList();
            check(new HashSet<>(names).size() == names.size(), "Duplicate case name");
            System.out.println(JSON.writeValueAsString(Map.of("cases",names,"caseCount",names.size(),"jdbcConnections",0,"httpRequests",0)));
            return;
        }
        check("true".equals(System.getenv("CCRA5_APPROVED")), "Explicit resource approval required");
        if (args.length == 3) { provisionCcra(args); return; }
        check(args.length == 4 && args[0].equals("VERIFY"), "Invalid invocation");
        repo=Path.of(args[1]); output=Path.of(args[2]);
        images=Path.of(System.getenv("CCRA_IMAGES")).toRealPath();
        check(images.startsWith(repo.resolve("backend/build/ccra-stage5-http").toRealPath()), "Unsafe image root");
        ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
        try {
            try(var listener=new ServerSocket()) { listener.bind(new InetSocketAddress("127.0.0.1",PORT)); }
            initialize(); // brand-new, empty schema only; no old cases or migrations.
            try(var c=open()) { strictGuard(c); }
            a=start(PORT,1);
            me=member(UserRole.USER,true); other=member(UserRole.USER,true); target=member(UserRole.USER,true);
            adminA=member(UserRole.ADMIN,true);
            var selected=catalog();String selection=System.getenv("CCRA5_CASES");
            if(selection!=null&&!selection.isBlank()) { var names=new HashSet<>(List.of(selection.split(",")));check(selected.stream().map(Scenario::name).toList().containsAll(names),"Unknown case selection");selected=selected.stream().filter(s->names.contains(s.name())).toList(); }
            check(!selected.isEmpty(),"No selected cases");for(var scenario:selected) test(scenario.name(),scenario.expected(),scenario.work());
        } catch(Throwable t) { cases.add(Map.of("name","harness","result","BLOCKED","error",error(t))); }
        finally {
            for(var server:servers) try { server.stop(); } catch(Throwable t) { cases.add(Map.of("name","server-stop","result","FAIL","error",error(t))); }
            if(emf!=null)emf.close();HTTP.close();
            write(output.resolve("cases.json"),cases);write(output.resolve("requests.json"),requests);
            write(output.resolve("responses.json"),responses);write(output.resolve("websocket.json"),sockets);write(output.resolve("servers.json"),events);
            var files=new ArrayList<Object>();try(var stream=Files.list(images)) { for(var f:stream.toList()) files.add(Map.of("name",f.getFileName().toString(),"bytes",Files.size(f),"sha256",sha(Files.readAllBytes(f)))); }
            write(output.resolve("files.json"),files);
            write(output.resolve("blocked.json"),List.of(Map.of("name","deployment-wss-tls-origin-reconnect","result","BLOCKED","reason","No deployed WSS/TLS/reverse-proxy environment supplied; loopback WS is not deployment validation")));
            long passed=cases.stream().filter(c->c.get("result").equals("PASS")).count();
            var summary=new LinkedHashMap<String,Object>();summary.put("executed",cases.size());summary.put("passed",passed);
            summary.put("failed",cases.stream().filter(c->c.get("result").equals("FAIL")).count());summary.put("blocked",1+cases.stream().filter(c->c.get("result").equals("BLOCKED")).count());
            summary.put("httpRequests",requests.size());summary.put("singleJvm",true);summary.put("oldCcraCasesExecuted",0);summary.put("worker13SuiteExecuted",0);
            summary.put("serversStopped",servers.stream().noneMatch(s->s.process.isAlive()));summary.put("finishedUtc",Instant.now().toString());
            write(output.resolve("summary.json"),summary);System.out.println(JSON.writeValueAsString(summary));
            if(passed!=cases.size())System.exit(1);
        }
    }

    static String sha(byte[] bytes)throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    static Reply api(String method,String path,Object body,Member who)throws Exception {
        var r=MatchingMultiInstance.api(PORT,method,path,body,who);
        responses.add(Map.of("case",current,"method",method,"path",path,"status",r.status(),"errorCode",r.code(),"body",r.body()));return r;
    }
    static Upload png(String name) { return new Upload(name,"image/png",PNG); }
    static Reply upload(String method,String path,Map<String,Object> body,Member who,Upload... files)throws Exception {
        String boundary="ccra5"+UUID.randomUUID();var bytes=new ByteArrayOutputStream();
        bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"request\"\r\nContent-Type: application/json\r\n\r\n"+JSON.writeValueAsString(body)+"\r\n").getBytes(StandardCharsets.UTF_8));
        for(var file:files) {
            bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"images\"; filename=\""+file.name()+"\"\r\nContent-Type: "+file.mime()+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            bytes.write(file.bytes());bytes.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        bytes.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+PORT+path)).timeout(Duration.ofSeconds(35)).header("Content-Type","multipart/form-data; boundary="+boundary);
        if(who!=null)builder.header("Authorization","Bearer "+who.token());
        var response=HTTP.send(builder.method(method,HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
        var r=new Reply(response.statusCode(),JSON.readTree(response.body()));
        requests.add(Map.of("case",current,"method",method,"path",path,"status",r.status(),"errorCode",r.code(),"port",PORT));
        responses.add(Map.of("case",current,"method",method,"path",path,"status",r.status(),"body",r.body(),"request",body,"files",Arrays.stream(files).map(f->Map.of("name",f.name(),"mime",f.mime(),"bytes",f.bytes().length)).toList()));
        return r;
    }
    static Map<String,Object> body(boolean anonymous) { return new LinkedHashMap<>(Map.of("category","자유게시판","title","stage5 "+UUID.randomUUID(),"content","stage5 body","anonymous",anonymous)); }
    static Map<String,Object> edit(List<Long> keep) { return new LinkedHashMap<>(Map.of("category","자유게시판","title","stage5 edited","content","stage5 edited body","keepImageIds",keep)); }
    static long post(Member who,boolean anon,Upload... files)throws Exception { var r=upload("POST","/api/posts",body(anon),who,files);expect(r,200,"");return r.data().asLong(); }
    static JsonNode detail(long post,Member viewer)throws Exception { var r=api("GET","/api/posts/"+post,null,viewer);expect(r,200,"");return r.data(); }
    static List<Long> ids(JsonNode array) { var result=new ArrayList<Long>();for(var item:array)result.add(item.path("id").asLong());return result; }
    static List<Long> imageIds(long post)throws Exception { return ids(detail(post,me).path("images")); }
    static List<Map<String,Object>> links(long post)throws Exception { return query("SELECT post_image_id,image_url,sort_order FROM post_image WHERE post_id=? ORDER BY sort_order,post_image_id",post); }
    static List<Map<String,Object>> postState(long post)throws Exception { return query("SELECT title,content,category,anonymous,status FROM community_post WHERE post_id=?",post); }
    static Set<String> fileNames()throws Exception { try(var stream=Files.list(images)) { return new HashSet<>(stream.map(f->f.getFileName().toString()).toList()); } }
    static Path file(String url) { Path f=images.resolve(url.substring(url.lastIndexOf('/')+1)).normalize();check(f.getParent().equals(images),"Unsafe file path");return f; }
    static HttpResponse<byte[]> image(String url,int expected,byte[] content,Member who)throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+PORT+url)).timeout(Duration.ofSeconds(15));
        if(who!=null)builder.header("Authorization","Bearer "+who.token());
        var r=HTTP.send(builder.GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        requests.add(Map.of("case",current,"method","GET","path",url,"status",r.statusCode(),"errorCode","","port",PORT));
        responses.add(Map.of("case",current,"path",url,"status",r.statusCode(),"bytes",r.body().length,"sha256",sha(r.body()),"headers",r.headers().map()));
        check(r.statusCode()==expected,"Image expected "+expected+", received "+r.statusCode());
        check(r.headers().firstValue("cache-control").orElse("").equals("no-store"),"Image caching allowed");
        check(r.headers().firstValue("pragma").orElse("").equals("no-cache"),"Image pragma missing");
        check(r.headers().firstValue("expires").orElse("").equals("0"),"Image expiry header missing");
        check(r.headers().firstValue("etag").isEmpty()&&r.headers().firstValue("last-modified").isEmpty(),"Image conditional cache metadata");
        if(content!=null)check(Arrays.equals(content,r.body()),"Image bytes differ");
        if(expected==404)check(r.body().length==0,"404 image must have empty body");return r;
    }
    static void eventuallyRemoved(String url)throws Exception { long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(Files.exists(file(url))&&System.nanoTime()<deadline)Thread.sleep(25);check(!Files.exists(file(url)),"Unlinked file remains after successful commit"); }
    static long room(Member owner,Member counterpart,String category,Long source)throws Exception {
        var request=new LinkedHashMap<String,Object>();request.put("category",category);
        if(source==null)request.put("targetUserId",counterpart.id());else { request.put("sourcePostId",source);request.put("sourceFrom","POST"); }
        var r=api("POST","/api/chat/rooms",request,owner);expect(r,200,"");return r.data().path("id").asLong();
    }
    static String roomPath(long room) { return "/api/chat/rooms/"+room; }
    static long send(long room,Member who,String text)throws Exception {
        long before=n("SELECT COUNT(*) FROM chat_message WHERE chat_room_id=?",room);
        try(var socket=new MatchingExtraChecks.SocketClient(PORT,who.token())) { socket.send(room,text);MatchingExtraChecks.awaitStored(room,before+1); }
        var row=query("SELECT message_id,content FROM chat_message WHERE chat_room_id=? ORDER BY message_id DESC LIMIT 1",room).getFirst();
        check(row.get("content").equals(text),"STOMP persisted different content");
        sockets.add(Map.of("case",current,"room",room,"direction","SEND","codePoints",text.codePointCount(0,text.length()),"storedId",row.get("message_id")));
        return ((Number)row.get("message_id")).longValue();
    }
    static JsonNode page(String path,Member who)throws Exception { var r=api("GET",path,null,who);expect(r,200,"");for(String field:List.of("content","page","size","totalElements","totalPages","first","last"))check(r.data().has(field),"Missing page field "+field);return r.data(); }
    static String enc(String value) { return URLEncoder.encode(value,StandardCharsets.UTF_8); }
    static void mask(JsonNode counterpart) {
        check(counterpart.path("nickname").asString().equals("익명")&&counterpart.path("tags").isArray()&&counterpart.path("tags").isEmpty(),"Anonymous label/tags");
        for(String field:List.of("id","profileImageUrl","dormitory","admissionYear","major","matchScore","introduction"))check(counterpart.has(field)&&counterpart.path(field).isNull(),"Anonymous profile leaked: "+field);
    }
    static List<Scenario> catalog() {
        var list=new ArrayList<Scenario>();
        list.add(new Scenario("image-multipart-create-get-update-order","real PNG bytes; keep ID permutation preserves existing order; new files append",()->{
            long p=post(me,false,png("a.png"),png("b.png"));var original=links(p);var first=imageIds(p);
            for(var link:original) { String url=link.get("image_url").toString();image(url,200,PNG,null);check(Arrays.equals(PNG,Files.readAllBytes(file(url))),"Stored bytes differ"); }
            expect(upload("PATCH","/api/posts/"+p,edit(List.of(first.get(1),first.get(0))),me,png("c.png")),200,"");
            var after=imageIds(p);check(after.size()==3&&after.subList(0,2).equals(first),"Keep permutation changed existing order or append failed");
            for(var link:links(p))image(link.get("image_url").toString(),200,PNG,null);
            String removed=original.getFirst().get("image_url").toString();
            expect(upload("PATCH","/api/posts/"+p,edit(List.of(after.get(2),after.get(1))),me),200,"");
            check(imageIds(p).equals(List.of(after.get(1),after.get(2))),"Subset retention order differs");
            image(removed,404,null,null);eventuallyRemoved(removed);
            for(var link:links(p))image(link.get("image_url").toString(),200,PNG,null);
            return Map.of("post",p,"original",original,"after",links(p),"orderPolicy","existing order retained; requested ID order is not a reorder API");
        }));
        for(String mode:List.of("omitted","null","empty"))list.add(new Scenario("image-remove-"+mode,"omitted/null/[] all remove links; old URL 404; committed unreferenced file deleted",()->{
            long p=post(me,false,png("remove.png"));String url=links(p).getFirst().get("image_url").toString();var request=edit(List.of());
            if(mode.equals("omitted"))request.remove("keepImageIds");if(mode.equals("null"))request.put("keepImageIds",null);
            Path unrelated=images.resolve("unlinked-"+UUID.randomUUID()+".png");Files.write(unrelated,PNG);
            expect(upload("PATCH","/api/posts/"+p,request,me),200,"");check(imageIds(p).isEmpty()&&links(p).isEmpty(),"Removed link still visible");
            image(url,404,null,null);eventuallyRemoved(url);check(Files.exists(unrelated),"Unrelated file swept");image("/api/post-images/"+unrelated.getFileName(),404,null,null);
            return Map.of("post",p,"url",url,"fileExists",false,"unrelatedFileRetained",true);
        }));
        list.add(new Scenario("image-count-and-byte-limits","5 files and exact 5MiB accepted; 6/5MiB+1/zero/disallowed MIME rejected without rows/files",()->{
            long full=post(me,false,png("1.png"),png("2.png"),png("3.png"),png("4.png"),png("5.png"));check(imageIds(full).size()==5,"Five images not accepted");
            byte[] max=new byte[5*1024*1024];System.arraycopy(PNG,0,max,0,PNG.length);
            long large=post(me,false,new Upload("limit.png","image/png",max));String url=links(large).getFirst().get("image_url").toString();image(url,200,max,null);
            var invalid=List.of(new Upload[]{png("1.png"),png("2.png"),png("3.png"),png("4.png"),png("5.png"),png("6.png")},new Upload[]{new Upload("over.png","image/png",new byte[max.length+1])},new Upload[]{new Upload("zero.png","image/png",new byte[0])},new Upload[]{new Upload("bad.txt","text/plain",PNG)});
            for(var files:invalid) { long count=n("SELECT COUNT(*) FROM community_post");var beforeFiles=fileNames();boolean parserLimit=files.length==1&&files[0].bytes().length>max.length;expect(upload("POST","/api/posts",body(false),me,files),parserLimit?413:400,parserLimit?"INVALID_REQUEST":"VALIDATION_ERROR");check(n("SELECT COUNT(*) FROM community_post")==count,"Invalid upload inserted post");check(fileNames().equals(beforeFiles),"Invalid upload left file"); }
            var before=links(full);expect(upload("PATCH","/api/posts/"+full,edit(imageIds(full)),me,png("sixth.png")),400,"VALIDATION_ERROR");check(before.equals(links(full))&&imageIds(full).size()==5,"Failed update mutated images");
            return Map.of("fiveImagePost",full,"exact5MiBPost",large,"invalidCreateRequests",4,"invalidCombinedUpdate",1);
        }));
        list.add(new Scenario("image-foreign-and-duplicate-ids-atomic","foreign post image and duplicate IDs reject INVALID_REQUEST with all text/links/files preserved",()->{
            long p=post(me,false,png("own.png")),foreign=post(other,false,png("foreign.png"));var own=imageIds(p);var before=postState(p);var original=links(p);
            for(var keep:List.of(List.of(own.getFirst(),imageIds(foreign).getFirst()),List.of(own.getFirst(),own.getFirst()))) {
                var filesBefore=fileNames();
                expect(upload("PATCH","/api/posts/"+p,edit(keep),me,png("attempt.png")),400,"INVALID_REQUEST");check(before.equals(postState(p))&&original.equals(links(p)),"Rejected ID request partially applied");
                check(fileNames().equals(filesBefore),"Rejected image ID upload left new files");
                check(detail(p,me).path("title").asString().equals(before.getFirst().get("title")),"Title changed after rejection");
                for(var link:original)image(link.get("image_url").toString(),200,PNG,null);
            }
            return Map.of("post",p,"foreignPost",foreign,"preserved",original);
        }));
        for(boolean anon:List.of(false,true))list.add(new Scenario("anonymity-change-atomic-"+anon,"opposite anonymous value rejects whole multipart; omitted/null/same retain state",()->{
            long p=post(me,anon,png("private.png"));var before=postState(p);var original=links(p);var request=edit(List.of());request.put("anonymous",!anon);
            var filesBefore=fileNames();
            expect(upload("PATCH","/api/posts/"+p,request,me,png("rejected.png")),400,"VALIDATION_ERROR");check(before.equals(postState(p))&&original.equals(links(p)),"Anonymous rejection partially applied");
            check(fileNames().equals(filesBefore),"Anonymous rejection left new files");
            image(original.getFirst().get("image_url").toString(),200,PNG,null);check(detail(p,me).path("anonymous").asBoolean()==anon,"Anonymity changed");
            for(String mode:List.of("omitted","null","same")) { var same=edit(imageIds(p));if(!mode.equals("omitted"))same.put("anonymous",mode.equals("null")?null:anon);expect(upload("PATCH","/api/posts/"+p,same,me),200,"");check(detail(p,me).path("anonymous").asBoolean()==anon,"Anonymity lost"); }
            return Map.of("post",p,"originalAnonymous",anon,"rejectedRequestPreserved",true);
        }));
        list.add(new Scenario("chat-room-list-detail-filter-sort-page","category/search/literal wildcards/counts/pinned then message time then room ID; paged union exact",()->{
            var viewer=member(UserRole.USER,true);var x=member(UserRole.USER,true);var y=member(UserRole.USER,true);var z=member(UserRole.USER,true);
            update("UPDATE user_profile SET nickname=? WHERE user_id=?","Stage5%_!Alpha",x.id());update("UPDATE user_profile SET nickname=? WHERE user_id=?","Stage5Beta",y.id());
            long one=room(viewer,x,"ROOMMATE",null),two=room(viewer,y,"ROOMMATE",null),p=post(z,false),three=room(viewer,z,"COMMUNITY",p);
            send(two,y,"newest unpinned");expect(api("PATCH",roomPath(one)+"/settings",Map.of("pinned",true),viewer),200,"");
            var all=page("/api/chat/rooms?size=100",viewer);check(ids(all.path("content")).equals(List.of(one,two,three)),"Room sort differs");
            var counts=all.path("counts");check(counts.path("all").asLong()==3&&counts.path("roommate").asLong()==2&&counts.path("community").asLong()==1&&counts.path("pinned").asLong()==1,"Room counts differ");
            var combined=new ArrayList<Long>();for(int i=0;i<3;i++) { var part=page("/api/chat/rooms?page="+i+"&size=1",viewer);combined.addAll(ids(part.path("content")));check(part.path("last").asBoolean()==(i==2),"Last flag"); }
            check(combined.equals(List.of(one,two,three)),"Room pages lost/duplicated rows");check(page("/api/chat/rooms?page=3&size=1",viewer).path("content").isEmpty(),"Empty room page");
            var community=page("/api/chat/rooms?category=COMMUNITY",viewer);check(ids(community.path("content")).equals(List.of(three))&&community.path("counts").equals(counts),"Category/count contract");
            for(String term:List.of("  sTaGe5%_!aLpHa  ","%","_","!"))check(ids(page("/api/chat/rooms?q="+enc(term),viewer).path("content")).equals(List.of(one)),"Search escaping/case/trim");
            var single=api("GET",roomPath(three),null,viewer);expect(single,200,"");check(single.data().equals(community.path("content").get(0)),"Single room contract differs");
            for(String suffix:List.of("category=BAD","page=-1","size=0","size=101","page=2147483647&size=100","page=2147483648"))expect(api("GET","/api/chat/rooms?"+suffix,null,viewer),400,"VALIDATION_ERROR");
            expect(api("GET",roomPath(one),null,other),403,"FORBIDDEN");expect(api("GET",roomPath(Long.MAX_VALUE),null,viewer),403,"FORBIDDEN");expect(api("GET","/api/chat/rooms",null,null),401,"UNAUTHORIZED");
            return Map.of("viewer",viewer.id(),"orderedRooms",combined,"counts",counts);
        }));
        list.add(new Scenario("chat-before-after-cursors-no-loss-duplicate","actual STOMP fixtures; before DESC and after ASC unions equal independently queried committed rows",()->{
            var viewer=member(UserRole.USER,true);var author=member(UserRole.USER,true);long r=room(viewer,author,"ROOMMATE",null);
            for(int i=0;i<8;i++)send(r,author,"stage5 cursor "+i);
            var sql=query("SELECT message_id FROM chat_message WHERE chat_room_id=? ORDER BY sent_at DESC,message_id DESC",r).stream().map(row->((Number)row.get("message_id")).longValue()).toList();
            String base=roomPath(r)+"/messages";var first=page(base+"?size=3",viewer);var descending=new ArrayList<>(ids(first.path("content")));
            long added=send(r,author,"stage5 cursor new during pagination");
            JsonNode part=first;int iterations=0;while(!part.path("last").asBoolean()) { check(++iterations<=10,"before cursor did not terminate");part=page(base+"?size=3&beforeId="+descending.getLast(),viewer);descending.addAll(ids(part.path("content"))); }
            check(descending.equals(sql)&&new HashSet<>(descending).size()==8,"before cursor missing/duplicate/new insertion drift");
            var ascending=new ArrayList<Long>();long cursor=sql.getLast();iterations=0;do { check(++iterations<=10,"after cursor did not terminate");part=page(base+"?size=3&afterId="+cursor,viewer);var more=ids(part.path("content"));ascending.addAll(more);if(!more.isEmpty())cursor=more.getLast(); }while(!part.path("last").asBoolean());
            var expected=new ArrayList<>(sql);Collections.reverse(expected);expected.removeFirst();expected.add(added);check(ascending.equals(expected)&&new HashSet<>(ascending).size()==8,"after cursor missing/duplicate/order");
            var search=page(base+"?size=100&q="+enc("cursor new")+"&afterId="+sql.getLast(),viewer);check(ids(search.path("content")).equals(List.of(added)),"Cursor search");
            for(String bad:List.of("beforeId=0","afterId=-1","beforeId="+added+"&afterId="+added,"beforeId="+added+"&page=1"))expect(api("GET",base+"?"+bad,null,viewer),400,"VALIDATION_ERROR");
            long foreign=room(viewer,other,"ROOMMATE",null),foreignId=send(foreign,other,"foreign");expect(api("GET",base+"?beforeId="+foreignId,null,viewer),400,"INVALID_REQUEST");
            expect(api("GET",base+"?afterId="+Long.MAX_VALUE,null,viewer),400,"INVALID_REQUEST");
            return Map.of("room",r,"beforeIds",descending,"afterIds",ascending,"insertedDuringPagination",added);
        }));
        list.add(new Scenario("chat-read-status-readonly-permissions","deduplicated receipt-only response; GET/receipt do not mark read; outsider/admin/foreign IDs denied atomically",()->{
            var viewer=member(UserRole.USER,true);var sender=member(UserRole.USER,true);long r=room(viewer,sender,"ROOMMATE",null),id=send(r,sender,"receipt fixture"),r2=room(viewer,other,"ROOMMATE",null),foreign=send(r2,other,"foreign receipt");
            var before=query("SELECT * FROM chat_room_member WHERE chat_room_id=? ORDER BY user_id",r);var receipt=Map.of("messageIds",List.of(id,id));String path=roomPath(r)+"/messages/read-status";
            page(roomPath(r)+"/messages",viewer);var response=api("POST",path,receipt,viewer);expect(response,200,"");check(response.data().size()==1,"Receipt duplicate not removed");
            var item=response.data().get(0);check(item.size()==2&&item.has("id")&&item.has("read")&&!item.path("read").asBoolean(),"Receipt leaked fields or read changed");
            check(before.equals(query("SELECT * FROM chat_room_member WHERE chat_room_id=? ORDER BY user_id",r))&&n("SELECT COUNT(*) FROM chat_message WHERE message_id=? AND is_read=1",id)==0,"Receipt query mutated state");
            for(var who:List.of(other,adminA)) { expect(api("POST",path,receipt,who),403,"FORBIDDEN");expect(api("GET",roomPath(r)+"/messages",null,who),403,"FORBIDDEN"); }
            expect(api("POST",path,receipt,null),401,"UNAUTHORIZED");
            for(long bad:List.of(foreign,Long.MAX_VALUE))expect(api("POST",path,Map.of("messageIds",List.of(id,bad)),viewer),400,"INVALID_REQUEST");
            expect(api("POST",path,Map.of("messageIds",List.of()),viewer),400,"VALIDATION_ERROR");
            expect(api("POST",roomPath(r)+"/read",Map.of("messageIds",List.of(id)),viewer),200,"");response=api("POST",path,receipt,sender);expect(response,200,"");check(response.data().get(0).path("read").asBoolean(),"Committed receipt not visible");
            expect(api("DELETE",roomPath(r)+"/members/me",null,viewer),200,"");expect(api("POST",path,receipt,viewer),403,"FORBIDDEN");expect(api("GET",roomPath(r),null,viewer),403,"FORBIDDEN");
            return Map.of("room",r,"message",id,"receiptAfterRead",response.data(),"queryPreservedState",true);
        }));
        list.add(new Scenario("anonymous-post-chat-list-detail-search-messages","post author masked, counterpart masked across creation/list/detail; real nickname cannot search; senderId null",()->{
            var writer=member(UserRole.USER,true);var viewer=member(UserRole.USER,true);long p=post(writer,true,png("anonymous.png"));
            var view=detail(p,viewer);check(view.path("author").path("id").isNull()&&view.path("author").path("major").isNull()&&!view.path("mine").asBoolean(),"Post identity leaked");check(detail(p,writer).path("mine").asBoolean(),"Owner mine missing");
            long r=room(viewer,writer,"COMMUNITY",p);long message=send(r,writer,"anonymous message");
            var single=api("GET",roomPath(r),null,viewer);expect(single,200,"");mask(single.data().path("counterpart"));
            var rooms=page("/api/chat/rooms?q="+enc("익명"),viewer);check(ids(rooms.path("content")).equals(List.of(r)),"Anonymous search missing");mask(rooms.path("content").get(0).path("counterpart"));
            String nickname=value("SELECT nickname FROM user_profile WHERE user_id=?",writer.id());check(page("/api/chat/rooms?q="+enc(nickname),viewer).path("content").isEmpty(),"Hidden real nickname searchable");
            var messages=page(roomPath(r)+"/messages",viewer);check(messages.path("content").get(0).path("senderId").isNull()&&messages.path("content").get(0).path("senderType").asString().equals("OTHER"),"Anonymous message sender leaked");
            var forged=api("POST","/api/chat/rooms",Map.of("category","COMMUNITY","sourceFrom","POST","sourcePostId",p,"targetUserId",other.id()),viewer);expect(forged,200,"");check(forged.data().path("id").asLong()==r,"Guessed target changed anonymous room");mask(forged.data().path("counterpart"));
            expect(api("DELETE","/api/posts/"+p,null,writer),200,"");single=api("GET",roomPath(r),null,viewer);expect(single,200,"");mask(single.data().path("counterpart"));
            return Map.of("post",p,"room",r,"message",message,"anonymousAfterSourceDelete",true);
        }));
        list.add(new Scenario("admin-direct-post-delete-image-public-denial","direct DELETE; guest/user denied; descendants deleted; links/files retained; public detail denied; no invented report audit",()->{
            long p=post(target,false,png("admin-delete.png")),c=comment(p,other,"admin child",false),child=reply(c,me);var original=links(p);long history=n("SELECT COUNT(*) FROM report");
            String path="/api/admin/posts/"+p;expect(api("DELETE",path,null,null),401,"UNAUTHORIZED");expect(api("DELETE",path,null,me),403,"FORBIDDEN");
            expect(api("DELETE",path,null,adminA),200,"");expect(api("GET","/api/posts/"+p,null,null),400,"INVALID_REQUEST");
            check(value("SELECT status FROM community_post WHERE post_id=?",p).equals("DELETED")&&n("SELECT COUNT(*) FROM community_comment WHERE comment_id IN (?,?) AND status='DELETED'",c,child)==2,"Direct post deletion not committed/cascaded");
            check(original.equals(links(p)),"Whole post deletion removed image link");for(var link:original) { String url=link.get("image_url").toString();image(url,404,null,null);image(url,404,null,adminA);check(Files.exists(file(url))&&Arrays.equals(Files.readAllBytes(file(url)),PNG),"Whole delete removed file"); }
            check(history==n("SELECT COUNT(*) FROM report"),"Direct delete unexpectedly fabricated report audit");expect(api("DELETE",path,null,adminA),400,"INVALID_REQUEST");
            var audit=query("SELECT post_id,author_id,title,content,status,created_at,updated_at FROM community_post WHERE post_id=?",p);
            return Map.of("post",p,"retainedContent",audit,"retainedImageLinks",original,"auditLimitation","Direct deletion has no actor/deletedAt/reason audit record in existing schema; report audit unchanged");
        }));
        list.add(new Scenario("admin-direct-comment-delete-public-thread","direct comment DELETE cascades replies, preserves sibling/post/image; content retained without report audit",()->{
            long p=post(target,false,png("comment-parent.png")),c=comment(p,target,"direct target",false),child=reply(c,other),sibling=comment(p,me,"surviving sibling",false);long history=n("SELECT COUNT(*) FROM report");String path="/api/admin/comments/"+c;
            expect(api("DELETE",path,null,null),401,"UNAUTHORIZED");expect(api("DELETE",path,null,me),403,"FORBIDDEN");expect(api("DELETE",path,null,adminA),200,"");
            var view=detail(p,null);check(ids(view.path("comments")).equals(List.of(sibling)),"Deleted comments visible or sibling lost");
            check(n("SELECT COUNT(*) FROM community_comment WHERE comment_id IN (?,?) AND status='DELETED'",c,child)==2&&history==n("SELECT COUNT(*) FROM report"),"Comment status/history mismatch");
            image(links(p).getFirst().get("image_url").toString(),200,PNG,null);expect(api("DELETE",path,null,adminA),400,"INVALID_REQUEST");
            return Map.of("post",p,"removedComment",c,"removedReply",child,"retained",query("SELECT comment_id,content,status FROM community_comment WHERE post_id=? ORDER BY comment_id",p),"auditLimitation","No separate direct-delete actor/time/reason audit");
        }));
        list.add(new Scenario("post-and-my-activity-list-contract","page shape/filter/latest/views/likes and own posts/comments; mine and image indicators",()->{
            var owner=member(UserRole.USER,true);String key="stage5-list-"+UUID.randomUUID();var b=body(false);b.put("title",key);var created=upload("POST","/api/posts",b,owner,png("list.png"));expect(created,200,"");long p=created.data().asLong();
            var secondBody=body(false);secondBody.put("title",key+" middle");var second=upload("POST","/api/posts",secondBody,target);expect(second,200,"");long p2=second.data().asLong();
            var thirdBody=body(false);thirdBody.put("title",key+" last");var third=upload("POST","/api/posts",thirdBody,other);expect(third,200,"");long p3=third.data().asLong();
            long c=comment(p,owner,"my list comment",false),child=reply(c,owner);
            expect(api("POST","/api/posts/"+p+"/likes",null,owner),200,"");
            expect(api("POST","/api/posts/"+p+"/likes",null,target),200,"");expect(api("POST","/api/posts/"+p2+"/likes",null,me),200,"");detail(p,owner);detail(p,owner);detail(p2,owner);
            for(String sort:List.of("LATEST","VIEWS","LIKES")) { var result=page("/api/posts?q="+enc(key)+"&category="+enc("자유게시판")+"&sort="+sort,owner);check(ids(result.path("content")).equals(sort.equals("LATEST")?List.of(p3,p2,p):List.of(p,p2,p3)),"Post filter/sort differs");JsonNode item=null;for(var row:result.path("content"))if(row.path("id").asLong()==p)item=row;check(item!=null,"Missing list fixture");for(String field:List.of("contentPreview","author","anonymous","mine","hasImages","liked","likes","commentCount","reported","views","createdAt"))check(item.has(field),"Missing list field "+field);check(item.path("mine").asBoolean()&&item.path("hasImages").asBoolean()&&item.path("liked").asBoolean()&&item.path("commentCount").asLong()==2,"List state differs"); }
            var firstPage=page("/api/posts?q="+enc(key)+"&size=2",owner);var nextPage=page("/api/posts?q="+enc(key)+"&size=2&page=1",owner);var combined=new ArrayList<>(ids(firstPage.path("content")));combined.addAll(ids(nextPage.path("content")));check(combined.equals(List.of(p3,p2,p))&&firstPage.path("totalElements").asLong()==3&&nextPage.path("last").asBoolean(),"Post page union/count differs");
            check(ids(page("/api/users/me/posts",owner).path("content")).equals(List.of(p)),"My posts contract");
            var activity=page("/api/users/me/comments",owner);check(new HashSet<>(ids(activity.path("content"))).equals(Set.of(c,child)),"My comments missing");for(var item:activity.path("content"))check(item.path("postId").asLong()==p&&item.path("postTitle").asString().equals(key)&&item.has("isReply"),"Comment navigation contract");
            expect(api("GET","/api/users/me/posts",null,null),401,"UNAUTHORIZED");return Map.of("post",p,"comments",List.of(c,child));
        }));
        list.add(new Scenario("post-unlike-and-comment-like-state","cancel post like and add/cancel comment like; detail/list follow committed rows",()->{
            var viewer=member(UserRole.USER,true);long p=post(target,false),c=comment(p,target,"like target",false);String pp="/api/posts/"+p+"/likes",cp="/api/comments/"+c+"/likes";
            expect(api("POST",pp,null,viewer),200,"");expect(api("DELETE",pp,null,viewer),200,"");var view=detail(p,viewer);check(!view.path("liked").asBoolean()&&view.path("likes").asLong()==0,"Unlike state stale");var posts=page("/api/posts?q="+enc(view.path("title").asString()),viewer);check(posts.path("content").size()==1&&!posts.path("content").get(0).path("liked").asBoolean()&&posts.path("content").get(0).path("likes").asLong()==0,"Unlike list state stale");
            expect(api("POST",cp,null,viewer),200,"");view=detail(p,viewer);check(view.path("comments").get(0).path("liked").asBoolean()&&view.path("comments").get(0).path("likes").asLong()==1,"Comment like missing");
            expect(api("DELETE",cp,null,viewer),200,"");view=detail(p,viewer);check(!view.path("comments").get(0).path("liked").asBoolean()&&view.path("comments").get(0).path("likes").asLong()==0,"Comment unlike stale");
            return Map.of("post",p,"comment",c,"postLikeRows",n("SELECT COUNT(*) FROM post_like WHERE post_id=?",p),"commentLikeRows",n("SELECT COUNT(*) FROM comment_like WHERE comment_id=?",c));
        }));
        list.add(new Scenario("report-other-reason-boundaries","기타 null/empty/blank/501 rejected; nonblank/500 UTF16 accepted exactly; no invalid rows",()->{
            int accepted=0,rejected=0;var descriptions=Arrays.asList(null,""," \t\r\n","x"," 한글 ","가".repeat(500),"😀".repeat(250),"가".repeat(501),"😀".repeat(250)+"x");
            for(String description:descriptions) { var victim=member(UserRole.USER,false);var request=new LinkedHashMap<String,Object>();request.put("targetType","USER");request.put("targetId",victim.id());request.put("reason","기타");request.put("description",description);var result=api("POST","/api/reports",request,me);
                boolean valid=description!=null&&!description.isBlank()&&description.length()<=500;
                if(valid) { expect(result,200,"");check(value("SELECT description FROM report WHERE report_id=?",result.data().asLong()).equals(description),"Description changed");accepted++; }
                else { expect(result,400,"VALIDATION_ERROR");check(n("SELECT COUNT(*) FROM report WHERE target_user_id=?",victim.id())==0,"Invalid 기타 persisted");rejected++; }
            }
            return Map.of("accepted",accepted,"rejected",rejected);
        }));
        list.add(new Scenario("user-report-delete-target-action-rollback","USER report DELETE_TARGET invalid; report/audit/user unchanged and admin detail still pending",()->{
            var victim=member(UserRole.USER,false);long id=report("USER",victim.id(),me,null);var before=state(id);var user=query("SELECT status FROM user WHERE user_id=?",victim.id());
            expect(api("PATCH","/api/admin/reports/"+id,Map.of("action","DELETE_TARGET"),adminA),400,"INVALID_REQUEST");check(before.equals(state(id))&&user.equals(query("SELECT status FROM user WHERE user_id=?",victim.id())),"Invalid USER action mutated report/user");
            var result=api("GET","/api/admin/reports/"+id,null,adminA);expect(result,200,"");check(result.data().path("status").asString().equals("PENDING"),"Invalid action changed HTTP report state");return state(id);
        }));
        list.add(new Scenario("chat-stomp-text-boundaries","2000 Unicode codepoints ASCII/emoji accepted intact; null/blank/2001 complete real handler without DB row; frame behavior recorded",()->{
            var sender=member(UserRole.USER,true);long r=room(sender,other,"ROOMMATE",null);send(r,sender,"a".repeat(2000));send(r,sender,"😀".repeat(2000));
            for(String text:Arrays.asList(null,""," \t\n","a".repeat(2001),"😀".repeat(2001))) {
                long count=n("SELECT COUNT(*) FROM chat_message WHERE chat_room_id=?",r);var payload=new LinkedHashMap<String,Object>();payload.put("text",text);
                try(var socket=new MatchingExtraChecks.SocketClient(PORT,sender.token())) {
                    String correlation=UUID.randomUUID().toString();socket.frames.clear();socket.frame("SEND\ndestination:/pub/chat/rooms/"+r+"/messages\ncontent-type:application/json\nx-stage5-id:"+correlation+"\n\n"+JSON.writeValueAsString(payload)+"\0");
                    var handled=a.await("stompHandled",5);check(correlation.equals(handled.get("id")),"Wrong STOMP completion event");
                    check(n("SELECT COUNT(*) FROM chat_message WHERE chat_room_id=?",r)==count,"Invalid text persisted");
                    String frame=socket.frames.poll(150,TimeUnit.MILLISECONDS);String outcome=frame!=null&&frame.startsWith("ERROR")?"ERROR":socket.socket.isInputClosed()?"CLOSED":"NO_ERROR_FRAME";
                    sockets.add(Map.of("case",current,"room",r,"input",text==null?"null":Integer.toString(text.codePointCount(0,text.length())),"outcome",outcome,"handlerCompleted",true,"invalidRows",0,"httpStatus","N/A","errorCode","N/A"));
                    if(!socket.socket.isInputClosed()&&!socket.socket.isOutputClosed()) { socket.send(r,"valid continuation "+count);MatchingExtraChecks.awaitStored(r,count+1);check(n("SELECT COUNT(*) FROM chat_message WHERE chat_room_id=?",r)==count+1,"Continuation count differs"); }
                }
                page(roomPath(r)+"/messages",sender);
            }
            return Map.of("room",r,"validMessages",2,"invalidMessages",5,"storedLengths",query("SELECT CHAR_LENGTH(content) codePoints FROM chat_message WHERE chat_room_id=? ORDER BY message_id",r));
        }));
        return list;
    }
}
