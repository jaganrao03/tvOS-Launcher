package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.os.Bundle;
import android.widget.Toast;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Local, readable backups. Import destinations are derived exclusively from validated profile IDs. */
public final class HomeBackup {
    static final int IMPORT = 7261;
    static final int LIMIT = BackupFormat.LIMIT;
    static String read(InputStream in) throws Exception {
        if(in==null) throw new IOException("Cannot open backup");
        try(InputStream src=in; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192]; int n;
            while((n=src.read(b))!=-1) { BackupFormat.checkSize((long)out.size()+n); out.write(b,0,n); }
            return out.toString("UTF-8");
        }
    }
    static JSONObject snapshot(Context c) throws Exception {
        JSONArray profiles=new JSONArray(); JSONObject homes=new JSONObject(), watching=new JSONObject();
        for(Profiles.Profile p:Profiles.list(c)) {
            profiles.put(new JSONObject().put("id",p.id).put("name",p.name).put("color",p.color).put("emoji",p.emoji));
            homes.put(p.id,BackupFormat.encode(c.getSharedPreferences(Profiles.prefsName(p.id),0).getAll()));
            File f=new File(c.getFilesDir(),Profiles.continueFile(p.id));
            JSONArray a=f.exists()?new JSONArray(read(new FileInputStream(f))):new JSONArray();
            for(int i=0;i<a.length();i++) {
                JSONObject e=a.getJSONObject(i); e.remove("link");
                String art=e.optString("art","");
                File dir=new File(c.getFilesDir(),"cw");
                if(art.startsWith(dir.getAbsolutePath()+"/") && new File(art).getName().matches("[a-fA-F0-9]+\\.jpg")) e.put("art","cw/"+new File(art).getName());
                else e.put("art","");
            }
            watching.put(p.id,a);
        }
        JSONObject root=new JSONObject().put("format","tvos-home").put("version",1).put("created",System.currentTimeMillis())
                .put("notes","Home preferences and Continue Watching metadata only. No wallpaper, icon or artwork bytes; no media URLs, credentials, Hermes or network settings.")
                .put("profiles",profiles).put("current",Profiles.current(c).id).put("ask_on_wake",Profiles.askOnWake(c)).put("homes",homes).put("watching",watching);
        BackupFormat.validate(root); return root;
    }
    static File export(Context c, String reason) throws Exception {
        synchronized(MediaWatchService.Store.class) {
            JSONObject data=snapshot(c);
            byte[] bytes=BackupFormat.serialize(data); // Check import's bound before creating anything.
            File external=c.getExternalFilesDir(null); if(external==null) throw new IOException("External app files unavailable");
            File dir=new File(external,"backups"); if(!dir.isDirectory()&&!dir.mkdirs()) throw new IOException("Cannot create backups directory");
            String stamp=new java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS",Locale.US).format(new Date());
            File f=new File(dir,"home-v1-"+stamp+"-"+reason+"-"+UUID.randomUUID()+".json");
            if(!f.createNewFile()) throw new IOException("Backup already exists");
            try(FileOutputStream out=new FileOutputStream(f)) { out.write(bytes); out.getFD().sync(); }
            return f;
        }
    }
    static void initial(Context c) {
        SharedPreferences sp=c.getSharedPreferences("backup_status",0);
        if(sp.getInt("upgrade_version",0)>=26) return;
        try { File f=export(c,"upgrade26"); if(!sp.edit().putInt("upgrade_version",26).commit()) throw new IOException("Cannot mark backup complete");
            android.util.Log.i("HomeBackup","Upgrade backup: "+f);
        } catch(Exception e) { failure(c,e); }
    }
    static void failure(Context c,Exception e) {
        android.util.Log.e("HomeBackup","Backup/restore failed",e);
        Toast.makeText(c,"Backup/restore FAILED: "+e.getMessage(),Toast.LENGTH_LONG).show();
    }
    static void choose(Activity a) {
        try { a.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),IMPORT); }
        catch(Exception e) { failure(a,e); }
    }
    static void selected(Activity a,Intent data) {
        if(data==null||data.getData()==null) return;
        try {
            JSONObject root=new JSONObject(read(a.getContentResolver().openInputStream(data.getData())));
            BackupFormat.validate(root); // Absolutely no writes before all validation succeeds.
            new AlertDialog.Builder(a).setTitle("Restore home backup?").setMessage("Profiles and current selection will be restored: "+root.getJSONArray("profiles").toString(2)
                    +"\nHome sections: "+root.getJSONObject("homes").names()+"\nContinue Watching sections: "+root.getJSONObject("watching").names()
                    +"\nIncluded keys are merged; omitted namespaces and keys stay unchanged. Included Continue Watching lists replace their profile lists. No wallpaper/icon/artwork bytes or media URLs are included. A new pre-restore backup is required. Launcher profiles do NOT map to Nuvio profiles.")
                    .setNegativeButton("Cancel",null).setPositiveButton("Restore these sections",(d,w)-> {
                        try { restore(a,root); Toast.makeText(a,"Restore complete",Toast.LENGTH_LONG).show(); a.recreate(); }
                        catch(Exception e) { failure(a,e); }
                    }).show();
        } catch(Exception e) { failure(a,e); }
    }
    @SuppressWarnings("unchecked")
    static void restore(Context c,JSONObject root) throws Exception {
        BackupFormat.serialize(root);
        synchronized(MediaWatchService.Store.class) {
            export(c,"pre-restore");
            JSONObject homes=root.getJSONObject("homes"), watching=root.getJSONObject("watching");
            Iterator<String> it=homes.keys();
            while(it.hasNext()) {
                String id=it.next(); SharedPreferences.Editor ed=c.getSharedPreferences(Profiles.prefsName(id),0).edit();
                for(Map.Entry<String,Object> e:BackupFormat.decode(homes.getJSONObject(id)).entrySet()) {
                    String k=e.getKey(); Object v=e.getValue();
                    if(v instanceof String) ed.putString(k,(String)v); else if(v instanceof Boolean) ed.putBoolean(k,(Boolean)v);
                    else if(v instanceof Integer) ed.putInt(k,(Integer)v); else if(v instanceof Long) ed.putLong(k,(Long)v);
                    else if(v instanceof Float) ed.putFloat(k,(Float)v); else ed.putStringSet(k,(Set<String>)v);
                }
                if(!ed.commit()) throw new IOException("Failed restoring home "+id+"; use pre-restore backup");
            }
            it=watching.keys(); while(it.hasNext()) {
                String id=it.next(); JSONArray a=new JSONArray(watching.getJSONArray(id).toString());
                for(int i=0;i<a.length();i++) { JSONObject e=a.getJSONObject(i); String art=e.optString("art");
                    if(!art.isEmpty()) e.put("art",new File(c.getFilesDir(),art).getAbsolutePath()); }
                android.util.AtomicFile af=new android.util.AtomicFile(new File(c.getFilesDir(),Profiles.continueFile(id)));
                FileOutputStream out=af.startWrite(); try { out.write(a.toString().getBytes(StandardCharsets.UTF_8)); af.finishWrite(out); }
                catch(Exception e) { af.failWrite(out); throw e; }
            }
            SharedPreferences.Editor ed=Profiles.store(c).edit().putString("list",root.getJSONArray("profiles").toString()).putString("current",root.getString("current"));
            if(root.has("ask_on_wake")) ed.putBoolean("ask_on_wake",root.getBoolean("ask_on_wake"));
            if(!ed.commit()) throw new IOException("Failed restoring profiles; use pre-restore backup");
        }
    }
    public static class UpgradeReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context c,Intent i) { if(Intent.ACTION_MY_PACKAGE_REPLACED.equals(i.getAction())) initial(c); }
    }
}
