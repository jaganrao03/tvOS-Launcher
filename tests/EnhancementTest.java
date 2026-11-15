package com.techgenius.tvoslauncher;
import org.json.*;
import java.util.*;
public class EnhancementTest {
    static int checks;
    static void ok(boolean b) { checks++; if (!b) throw new AssertionError("check " + checks); }
    static void bad(JSONObject o) throws Exception { boolean rejected=false; try { BackupFormat.validate(o); } catch (Exception e) { rejected=true; } ok(rejected); }
    public static void main(String[] args) throws Exception {
        JSONObject root = new JSONObject("{\"format\":\"tvos-home\",\"version\":1,\"profiles\":[{\"id\":\"p1\",\"name\":\"Jagan\",\"color\":0,\"emoji\":\"\"}],\"current\":\"p1\",\"homes\":{},\"watching\":{}}");
        BackupFormat.validate(root); ok(true);
        Map<String,Object> values = new HashMap<>(); values.put("order", "vpn,inputs"); values.put("cw_enabled",true); values.put("edge_glow",40); values.put("idle_sec",20); values.put("wall_scale",1.5f); values.put("hidden",new HashSet<>(Arrays.asList("x","y")));
        JSONObject typed=BackupFormat.encode(values); Map<String,Object> decoded=BackupFormat.decode(typed); ok(decoded.equals(values));
        typed.put("idle_sec",new JSONObject().put("type","int").put("value",20));
        root.getJSONObject("homes").put("p1",typed); BackupFormat.validate(root); ok(true);
        JSONObject invalid=new JSONObject(root.toString()); invalid.getJSONObject("homes").put("../credentials", typed); bad(invalid);
        invalid=new JSONObject(root.toString()); invalid.put("version",2); bad(invalid);
        invalid=new JSONObject(root.toString()); invalid.getJSONObject("homes").getJSONObject("p1").put("token",new JSONObject("{\"type\":\"string\",\"value\":\"secret\"}")); bad(invalid);
        invalid=new JSONObject(root.toString()); invalid.put("current","p9"); bad(invalid);
        invalid=new JSONObject(root.toString()); invalid.getJSONObject("watching").put("p1",new JSONArray("[{\"key\":\"x|t\",\"pkg\":\"x\",\"title\":\"t\",\"art\":\"../../secret\"}]")); bad(invalid);
        invalid=new JSONObject(root.toString()); invalid.getJSONObject("homes").getJSONObject("p1").put("order",new JSONObject("{\"type\":\"boolean\",\"value\":\"false\"}")); bad(invalid);
        ok(ContinuePolicy.suppressed(100000,100000,false,false));
        ok(ContinuePolicy.suppressed(100000,105000,true,false));
        ok(!ContinuePolicy.suppressed(100000,131000,true,false));
        ok(ContinuePolicy.suppressed(100000,-1,true,false));
        ok(!ContinuePolicy.suppressed(100000,1000,true,true));
        ok(ContinuePolicy.visible(1000,1000+8L*86400000,0));
        ok(!ContinuePolicy.visible(1000,1000+8L*86400000,7));
        ok(ContinuePolicy.visible(1000,1000+8L*86400000,30));
        ok(HermesContract.valid("reply","req","hello")); ok(!HermesContract.valid("other","req","hello")); ok(!HermesContract.valid("reply"," ","hello")); ok(!HermesContract.valid("reply","req","a".repeat(2001)));
        ok(HermesContract.timeout("reply")==12000); ok(HermesContract.timeout("thinking")==240000); ok(HermesContract.timeout("setup")==0);
        System.out.println("PASS " + checks + " behavioral checks");
    }
}
