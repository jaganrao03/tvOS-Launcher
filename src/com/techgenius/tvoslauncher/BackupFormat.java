package com.techgenius.tvoslauncher;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Portable, allowlisted data only. No file names or preference namespaces supplied by imports. */
final class BackupFormat {
    static final int LIMIT = 32 * 1024 * 1024;
    static void checkSize(long bytes) {
        if (bytes > LIMIT) throw new IllegalArgumentException("Backup exceeds supported 32 MiB serialized size");
    }
    static byte[] serialize(JSONObject root) throws Exception {
        validate(root);
        byte[] bytes = root.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        checkSize(bytes.length);
        return bytes;
    }
    static final Set<String> KEYS = new HashSet<>(Arrays.asList(
            "order", "hidden", "other_apps", "placement_defaults_v2", "wallpaper", "edge_glow",
            "last_classic_idx", "shelf_mode", "cw_enabled", "idle_sec", "wall_scale", "scene",
            "cw_hidden_sources", "cw_retention_days", "home_weather_shortcut", "shelf", "search_recent", "asked_default_home"));
    static void require(boolean b) { if (!b) throw new IllegalArgumentException("Invalid or unsupported backup data"); }
    static JSONObject encode(Map<String, ?> values) throws Exception {
        JSONObject out = new JSONObject();
        for (Map.Entry<String, ?> e : values.entrySet()) {
            if (!KEYS.contains(e.getKey())) continue;
            Object v=e.getValue(); String t;
            if (v instanceof String) t="string";
            else if(v instanceof Boolean) t="boolean";
            else if(v instanceof Integer) t="int";
            else if(v instanceof Long) t="long";
            else if(v instanceof Float) t="float";
            else if(v instanceof Set) { t="stringSet"; v=new JSONArray((Set<?>)v); }
            else throw new IllegalArgumentException("Unsupported preference type");
            out.put(e.getKey(),new JSONObject().put("type",t).put("value",v));
        }
        return out;
    }
    static Map<String,Object> decode(JSONObject o) throws Exception {
        Map<String,Object> out=new LinkedHashMap<>();
        Iterator<String> it=o.keys();
        while(it.hasNext()) {
            String k=it.next(); require(KEYS.contains(k)); JSONObject p=o.getJSONObject(k);
            Object v=p.get("value"); String t=p.getString("type");
            switch(t) {
                case "string": require(v instanceof String); break;
                case "boolean": require(v instanceof Boolean); break;
                case "int": require(v instanceof Number && ((Number)v).doubleValue()==((Number)v).intValue()); v=((Number)v).intValue(); break;
                case "long": require(v instanceof Number && v.toString().matches("-?[0-9]+")); v=Long.valueOf(v.toString()); break;
                case "float": require(v instanceof Number && Float.isFinite(((Number)v).floatValue())); v=((Number)v).floatValue(); break;
                case "stringSet": require(v instanceof JSONArray); Set<String> set=new HashSet<>(); JSONArray a=(JSONArray)v;
                    for(int i=0;i<a.length();i++) { require(a.get(i) instanceof String); set.add(a.getString(i)); } v=set; break;
                default: throw new IllegalArgumentException("Unsupported preference type");
            }
            // Enforce the consumer's type, not just the serialized type.
            if (Arrays.asList("order","wallpaper","shelf_mode","scene","shelf","search_recent").contains(k)) require(v instanceof String);
            if (Arrays.asList("hidden","other_apps","cw_hidden_sources").contains(k)) require(v instanceof Set);
            if (Arrays.asList("placement_defaults_v2","cw_enabled","home_weather_shortcut","asked_default_home").contains(k)) require(v instanceof Boolean);
            if (Arrays.asList("edge_glow","last_classic_idx","cw_retention_days","idle_sec").contains(k)) require(v instanceof Integer);
            if(k.equals("wall_scale")) require(v instanceof Float);
            if(k.equals("cw_retention_days")) require(v.equals(0)||v.equals(7)||v.equals(30));
            out.put(k,v);
        }
        return out;
    }
    static void validate(JSONObject root) throws Exception {
        require(root.get("format") instanceof String && "tvos-home".equals(root.getString("format")) && root.get("version") instanceof Number && root.getDouble("version")==1);
        Set<String> allowed=new HashSet<>(Arrays.asList("format","version","created","notes","profiles","current","ask_on_wake","homes","watching"));
        Iterator<String> top=root.keys(); while(top.hasNext()) require(allowed.contains(top.next()));
        JSONArray profiles=root.getJSONArray("profiles"); require(profiles.length()>0 && profiles.length()<=3);
        Set<String> ids=new HashSet<>();
        for(int i=0;i<profiles.length();i++) { JSONObject p=profiles.getJSONObject(i); String id=p.getString("id");
            require(p.get("id") instanceof String && id.matches("p[1-9][0-9]{0,8}") && ids.add(id));
            require(p.get("name") instanceof String && p.getString("name").length()<=200);
            require(p.get("color") instanceof Number && p.getDouble("color")==p.getInt("color") && p.getInt("color")>=0 && p.getInt("color")<7);
            require(p.get("emoji") instanceof String && p.getString("emoji").length()<=32);
        }
        require(ids.contains(root.getString("current")));
        if(root.has("ask_on_wake")) require(root.get("ask_on_wake") instanceof Boolean);
        JSONObject homes=root.getJSONObject("homes"), watching=root.getJSONObject("watching");
        Iterator<String> it=homes.keys(); while(it.hasNext()) { String id=it.next(); require(ids.contains(id)); decode(homes.getJSONObject(id)); }
        it=watching.keys(); while(it.hasNext()) { String id=it.next(); require(ids.contains(id)); JSONArray a=watching.getJSONArray(id);

            for(int i=0;i<a.length();i++) { JSONObject e=a.getJSONObject(i);
                require(e.get("pkg") instanceof String && e.getString("pkg").matches("[A-Za-z0-9_.]+")); require(e.get("key") instanceof String && e.get("title") instanceof String);
                if(e.has("art")) require(e.get("art") instanceof String);
                if(e.has("sub")) require(e.get("sub") instanceof String);
                String art=e.optString("art","");
                require(!art.contains("..") && (art.isEmpty() || art.matches("cw/[a-fA-F0-9]+\\.jpg")));
                for(String k:Arrays.asList("pos","dur","ts","dismiss_pos","raw_pos","dismiss_raw","sample_time")) if(e.has(k)) require(e.get(k) instanceof Number);
                for(String k:Arrays.asList("dismissed","rewound")) if(e.has(k)) require(e.get(k) instanceof Boolean);
                // Media URLs may carry signed auth; the portable artifact never contains them.
                require(!e.has("link"));
                Set<String> fields=new HashSet<>(Arrays.asList("pkg","key","title","sub","art","pos","dur","ts","dismissed","dismiss_pos","raw_pos","dismiss_raw","sample_time","rewound"));
                Iterator<String> fieldsIt=e.keys(); while(fieldsIt.hasNext()) require(fields.contains(fieldsIt.next()));
            }
        }
    }
}
