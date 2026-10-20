import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** Hash original rows only; later inserts must not mask an update/deletion of an original row. */
public class ChatMysqlPreservation extends ChatMysqlValidation {
    record Bound(String table, String numericKey, Long maxId, List<String> order, long count, String sha256) { }
    static String quote(String identifier) {
        check(identifier.matches("[A-Za-z0-9_]+"), "Unsafe metadata identifier");
        return "`" + identifier + "`";
    }
    static List<Bound> capture(String label) throws Exception {
        var snapshots = new ArrayList<Bound>();
        for (String table : jdbc.queryForList("select table_name from information_schema.tables where table_schema=database() and table_type='BASE TABLE' order by table_name", String.class)) {
            var pk = jdbc.queryForList("select column_name from information_schema.statistics where table_schema=database() and table_name=? and index_name='PRIMARY' order by seq_in_index", String.class, table);
            String numericKey = null; Long max = null;
            if (pk.size()==1) {
                String type=jdbc.queryForObject("select data_type from information_schema.columns where table_schema=database() and table_name=? and column_name=?", String.class, table, pk.getFirst());
                if (Set.of("bigint","int","smallint","tinyint").contains(type)) {
                    numericKey=pk.getFirst();
                    max=jdbc.queryForObject("select coalesce(max("+quote(numericKey)+"),0) from "+quote(table), Long.class);
                }
            }
            var order=pk.isEmpty()?jdbc.queryForList("select column_name from information_schema.columns where table_schema=database() and table_name=? order by ordinal_position", String.class, table):pk;
            snapshots.add(hash(new Bound(table,numericKey,max,order,0,"")));
        }
        Files.writeString(output.resolve(label+".original-rows.json"), JSON.writeValueAsString(snapshots));
        return snapshots;
    }
    static Bound hash(Bound bound) throws Exception {
        String sql="select * from "+quote(bound.table());
        if(bound.numericKey()!=null)sql+=" where "+quote(bound.numericKey())+" <= "+bound.maxId();
        sql+=" order by "+String.join(",",bound.order().stream().map(ChatMysqlPreservation::quote).toList());
        var digest=MessageDigest.getInstance("SHA-256"); long count=0;
        try(var c=pool.getConnection();var statement=c.createStatement();var rows=statement.executeQuery(sql)) {
            int columns=rows.getMetaData().getColumnCount();
            while(rows.next()) {
                count++;
                for(int i=1;i<=columns;i++) {
                    String value=rows.getString(i);
                    byte[] bytes=value==null?new byte[0]:value.getBytes(StandardCharsets.UTF_8);
                    digest.update((byte)(value==null?0:1));
                    digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array()); digest.update(bytes);
                }
            }
        }
        return new Bound(bound.table(),bound.numericKey(),bound.maxId(),bound.order(),count,HexFormat.of().formatHex(digest.digest()));
    }
    static void verify(List<Bound> before,String label) throws Exception {
        var after=new ArrayList<Bound>();
        for(var bound:before) after.add(hash(bound));
        Files.writeString(output.resolve(label+".original-rows.json"),JSON.writeValueAsString(after));
        check(before.equals(after),"Original rows changed: see "+label+" hashes; stop further writes");
    }
}
