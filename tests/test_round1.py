"""Host regressions compile production policy and actual lifecycle/record method bodies."""
import pathlib
import subprocess
import tempfile
import unittest
from test_dock_placement import method, SRC

JSON = '/home/jagan/.hermes/cache/scratch/tvos-v26-tests/json.jar'

class Round1Tests(unittest.TestCase):
    def test_contract_and_large_backup(self):
        java = r'''package com.techgenius.tvoslauncher;
import org.json.*;
public class Round1Harness {
static void ok(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] args)throws Exception {
 HermesContract.Tracker t=new HermesContract.Tracker();
 ok(!t.accept("reply","unknown")); ok(t.accept("listening","A"));
 ok(t.accept("listening","B")); ok(!t.accept("reply","A"));
 ok(t.accept("reply","B")); ok(!t.accept("thinking","B"));
 t.dismiss("B"); ok(!t.accept("reply","B")); ok(!t.accept("listening","B"));
 ok(t.accept("setup","setup")); ok(!t.accept("reply","setup"));
 ok(!t.accept("reply","unknown"));
 for(int i=0;i<1000;i++)ok(t.accept("listening","n"+i));
 ok(t.size()<=128); ok(!t.accept("reply","A"));
 ok(HermesContract.valid("reply","r","😀".repeat(1000)));
 ok(!HermesContract.valid("reply","r","😀".repeat(1000)+"x"));
 ok(HermesContract.valid("reply","r","😀".repeat(999)+"ab"));
 ok(HermesContract.shouldSpeak("error",true,"failure"));
 ok(!HermesContract.shouldSpeak("thinking",true,"wait"));
 JSONObject root=new JSONObject("{\"format\":\"tvos-home\",\"version\":1,\"profiles\":[{\"id\":\"p1\",\"name\":\"J\",\"color\":0,\"emoji\":\"\"}],\"current\":\"p1\",\"homes\":{},\"watching\":{}}");
 JSONArray entries=new JSONArray();
 for(int i=0;i<10001;i++)entries.put(new JSONObject().put("pkg","app.media").put("key","k"+i).put("title","t".repeat(450)).put("art","cw/"+"a".repeat(64)+".jpg"));
 root.getJSONObject("watching").put("p1",entries);
 byte[] bytes=BackupFormat.serialize(root); ok(bytes.length>4*1024*1024);
 BackupFormat.validate(new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)));
 entries.getJSONObject(0).put("art","../bad");
 boolean bad=false;try{BackupFormat.serialize(root);}catch(Exception e){bad=true;}ok(bad);
 bad=false;try{BackupFormat.checkSize(BackupFormat.LIMIT+1L);}catch(Exception e){bad=true;}ok(bad);
 System.out.println("PASS tracker, UTF16, error speech, 10001-entry >4MiB backup roundtrip and malformed/oversize rejection");
}}
'''
        with tempfile.TemporaryDirectory(prefix='round1-') as tmp:
            p=pathlib.Path(tmp); (p/'Round1Harness.java').write_text(java)
            subprocess.run(['javac','--release','11','-cp',JSON,'-d',tmp,str(p/'Round1Harness.java'),str(SRC/'HermesContract.java'),str(SRC/'BackupFormat.java')],check=True)
            subprocess.run(['java','-Xmx512m','-cp',tmp+':'+JSON,'com.techgenius.tvoslauncher.Round1Harness'],check=True)

    def test_artwork_and_actual_record(self):
        record=method((SRC/'MediaWatchService.java').read_text(),'static synchronized void record(')
        text=method((SRC/'MediaWatchService.java').read_text(),'static String text(')
        java=r'''package com.techgenius.tvoslauncher;
import java.io.*;import org.json.*;import android.graphics.Bitmap;
public class RecordHarness {
static class Context {File dir;Context(File d){dir=d;}File getFilesDir(){return dir;}String getPackageName(){return "launcher";}Context getApplicationContext(){return this;}}
static class SystemClock {static long elapsedRealtime(){return 5000;}}
static class MediaMetadata {
static final String METADATA_KEY_DISPLAY_TITLE="dt",METADATA_KEY_TITLE="t",METADATA_KEY_DISPLAY_SUBTITLE="sub",METADATA_KEY_ARTIST="artist",METADATA_KEY_ALBUM="album",METADATA_KEY_DURATION="dur",METADATA_KEY_ART="art",METADATA_KEY_ALBUM_ART="aa",METADATA_KEY_DISPLAY_ICON="di",METADATA_KEY_ART_URI="au",METADATA_KEY_ALBUM_ART_URI="aau",METADATA_KEY_DISPLAY_ICON_URI="diu",METADATA_KEY_MEDIA_URI="uri",METADATA_KEY_MEDIA_ID="id";
java.util.Map<String,String> m=new java.util.HashMap<>();Bitmap bitmap;long dur;
CharSequence getText(String k){return m.get(k);}long getLong(String k){return dur;}Bitmap getBitmap(String k){return k.equals("art")?bitmap:null;}}
static class PlaybackState {static final int STATE_PLAYING=3;long pos=100000,sample=1000;long getPosition(){return pos;}long getLastPositionUpdateTime(){return sample;}int getState(){return 3;}float getPlaybackSpeed(){return 1;}}
static JSONArray data=new JSONArray();static int writes;
static JSONArray read(Context c){return data;}static void write(Context c,JSONArray a){data=a;writes++;}
static boolean isYouTubePkg(String p){return false;}static String extractYouTubeVideoId(String s){return "";}static String searchYouTubeVideoId(String a,String b){throw new AssertionError();}static void updateLinkForKey(Context c,String k,String l){}
TEXT
RECORD
static void ok(boolean b){if(!b)throw new AssertionError("writes="+writes);}
public static void main(String[] args)throws Exception {
 Context c=new Context(new File(args[0]));MediaMetadata m=new MediaMetadata();m.m.put("t","Film");m.bitmap=new Bitmap("image");PlaybackState s=new PlaybackState();
 record(c,"app.media",m,s);ok(writes==1);String art=data.getJSONObject(0).getString("art");int encodes=Bitmap.encodes;
 record(c,"app.media",m,s);ok(writes==1);ok(Bitmap.encodes==encodes);
 m.m.put("sub","New subtitle");m.dur=900000;record(c,"app.media",m,s);ok(writes==2);ok(data.getJSONObject(0).getString("sub").equals("New subtitle"));ok(data.getJSONObject(0).getLong("dur")==900000);
 m.bitmap=new Bitmap("image");record(c,"app.media",m,s);ok(writes==2);ok(data.getJSONObject(0).getString("art").equals(art));ok(new File(c.dir,"cw").list().length==1);
 m.bitmap.generation++;m.bitmap.content="different";record(c,"app.media",m,s);ok(writes==3);ok(new File(c.dir,"cw").list().length==2);ok(new File(art).exists());
 data.getJSONObject(0).put("dismissed",true).put("dismiss_raw",s.pos);record(c,"app.media",m,s);ok(writes==3);ok(data.getJSONObject(0).getBoolean("dismissed"));
 s.sample++;s.pos+=40000;record(c,"app.media",m,s);ok(writes==4);ok(!data.getJSONObject(0).getBoolean("dismissed"));
 for(int i=0;i<40;i++)ArtworkStore.save(new File(c.dir,"cw"),new Bitmap("other"+i));
 java.lang.reflect.Field f=ArtworkStore.class.getDeclaredField("cache");f.setAccessible(true);ok(((java.util.List<?>)f.get(null)).size()<=32);
 System.out.println("PASS actual record metadata merge, duplicate persistence, dismissal revival; artwork identity/generation/SHA256 dedup/history/bounded cache");
}}
'''.replace('TEXT',text).replace('RECORD',record)
        bitmap='''package android.graphics; public class Bitmap {public static int encodes;public int generation=1;public String content;public Bitmap(String c){content=c;}public int getGenerationId(){return generation;}public int getWidth(){return 200;}public int getHeight(){return 200;}public static Bitmap createScaledBitmap(Bitmap b,int w,int h,boolean f){return b;}public void recycle(){}public enum CompressFormat {JPEG}public boolean compress(CompressFormat f,int q,java.io.OutputStream out){encodes++;try{out.write(content.getBytes());return true;}catch(Exception e){throw new RuntimeException(e);}}}'''
        atomic='''package android.util;public class AtomicFile {java.io.File file;public AtomicFile(java.io.File f){file=f;}public java.io.FileOutputStream startWrite()throws Exception{return new java.io.FileOutputStream(file);}public void finishWrite(java.io.FileOutputStream out)throws Exception{out.close();}public void failWrite(java.io.FileOutputStream out)throws Exception{out.close();}}'''
        with tempfile.TemporaryDirectory(prefix='record-') as tmp:
            p=pathlib.Path(tmp)
            for name,source in [('RecordHarness.java',java),('android/graphics/Bitmap.java',bitmap),('android/util/AtomicFile.java',atomic)]:
                dest=p/name;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(source)
            subprocess.run(['javac','--release','11','-cp',JSON,'-d',tmp,*map(str,p.rglob('*.java')),str(SRC/'ArtworkStore.java'),str(SRC/'ContinuePolicy.java')],check=True)
            subprocess.run(['java','-cp',tmp+':'+JSON,'com.techgenius.tvoslauncher.RecordHarness',tmp],check=True)

    def test_resume_actual_guard(self):
        text=(SRC/'MainActivity.java').read_text()
        actual=method(text,'protected void onResume()')
        # Execute the actual lifecycle entry through the recreation guard; UI tail is irrelevant.
        prefix=actual[:actual.index('        resumed = true;')]+'    }'
        java='''package com.techgenius.tvoslauncher;
class ResumeHarness extends Base {
String boundProfileId="p1",activeLang="en",activeLook="same"; boolean activeHomeEditorial=true; int recreates;
void recreate(){recreates++;} static class Profiles {static String id="p1"; static Profiles current(Object c){return new Profiles();}}
static class I18n {static String getLang(Object c){return "en";}} static class Scenes {static boolean editorial=true; static String getLook(Object c){return "same";} static boolean isHomeEditorial(Object c){return editorial;}}
ACTUAL
public static void main(String[] a){ResumeHarness h=new ResumeHarness();h.onResume();if(h.recreates!=0)throw new AssertionError();Profiles.id="p2";h.onResume();if(h.recreates!=1)throw new AssertionError("profile restore failed to recreate");Profiles.id="p1";Scenes.editorial=false;h.onResume();if(h.recreates!=2)throw new AssertionError("home style change failed to recreate");System.out.println("PASS actual onResume profile + home style guard");}}
class Base {protected void onResume(){}}
'''.replace('ACTUAL',prefix)
        with tempfile.TemporaryDirectory(prefix='resume-') as tmp:
            p=pathlib.Path(tmp)/'ResumeHarness.java';p.write_text(java)
            subprocess.run(['javac','-d',tmp,str(p),str(SRC/'HomeComposition.java')],check=True)
            subprocess.run(['java','-cp',tmp,'com.techgenius.tvoslauncher.ResumeHarness'],check=True)

    def test_provisioning_and_record_guards(self):
        activity=(SRC/'HermesActivity.java').read_text()
        self.assertNotIn('launchedByShell',activity)
        self.assertNotIn('getLaunchedFromUid',activity)
        self.assertNotIn('hasExtra("enabled")',activity)
        self.assertIn('tracker.accept(state,request)',activity)
        self.assertIn('tracker.dismiss(activeRequest)',activity)
        store=(SRC/'MediaWatchService.java').read_text()
        self.assertNotIn('System.nanoTime()',store)
        self.assertIn('ArtworkStore.save',store)
        self.assertIn('e.toString().equals(prev.toString())',store)
        self.assertIn('if (fresh && pos >= 0 || prev == null)',store)
        backup=(SRC/'HomeBackup.java').read_text()
        self.assertLess(backup.index('BackupFormat.serialize(data)'),backup.index('f.createNewFile()'))
        self.assertIn('android:permission="android.permission.DUMP"',(SRC.parents[3]/'AndroidManifest.xml').read_text())

if __name__=='__main__':unittest.main()
