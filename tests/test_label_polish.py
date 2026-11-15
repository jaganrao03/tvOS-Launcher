"""Host Java tests running the actual Inputs tuner-naming method, plus Control Center label checks."""
import pathlib
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / 'src/com/techgenius/tvoslauncher'
TUNER = 10
HDMI = 1007


def method(text, signature):
    start = text.index(signature)
    brace = text.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]


JAVA = '''import java.util.*;
public class TunerHarness {
static class TvInputInfo {static final int TYPE_TUNER=%d, TYPE_HDMI=%d;}
INPUT_CLASS
NUMBER_METHOD
static Input in(String id,int type,String label,boolean custom){Input i=new Input();i.id=id;i.type=type;i.label=label;i.custom=custom;return i;}
static String labels(List<Input> l){StringBuilder b=new StringBuilder();for(Input i:l)b.append(i.id).append('=').append(i.label).append(';');return b.toString();}
static int n; static void eq(String exp,List<Input> l){n++;String a=labels(l);if(!exp.equals(a))throw new AssertionError("expected "+exp+" got "+a);}
public static void main(String[] a){
BODY
System.out.println(n+" tuner assertions passed");
}}
''' % (TUNER, HDMI)

CASES = '''
List<Input> l=new ArrayList<>(Arrays.asList(in("p/.S/HW20",TvInputInfo.TYPE_TUNER,"TV",false),in("p/.S/HW10",TvInputInfo.TYPE_TUNER,"TV",false)));
numberDuplicateTuners(l); eq("p/.S/HW20=Tuner 2;p/.S/HW10=Tuner 1;",l);
l=new ArrayList<>(Arrays.asList(in("p/.S/HW10",TvInputInfo.TYPE_TUNER,"TV",false),in("p/.S/HW20",TvInputInfo.TYPE_TUNER,"TV",false)));
numberDuplicateTuners(l); eq("p/.S/HW10=Tuner 1;p/.S/HW20=Tuner 2;",l);
l=new ArrayList<>(Arrays.asList(in("p/.S/HW10",TvInputInfo.TYPE_TUNER,"TV",false)));
numberDuplicateTuners(l); eq("p/.S/HW10=TV;",l);
l=new ArrayList<>(Arrays.asList(in("a/.S/HW1",TvInputInfo.TYPE_TUNER,"Living",true),in("b/.S/HW2",TvInputInfo.TYPE_TUNER,"TV",true)));
numberDuplicateTuners(l); eq("a/.S/HW1=Living;b/.S/HW2=TV;",l);
l=new ArrayList<>(Arrays.asList(in("a/.S/HW1",TvInputInfo.TYPE_HDMI,"TV",false),in("b/.S/HW2",TvInputInfo.TYPE_HDMI,"TV",false)));
numberDuplicateTuners(l); eq("a/.S/HW1=TV;b/.S/HW2=TV;",l);
l=new ArrayList<>(Arrays.asList(in("c/.S/HW3",TvInputInfo.TYPE_TUNER,"TV",false),in("a/.S/HW1",TvInputInfo.TYPE_TUNER,"Satellite",false),in("b/.S/HW2",TvInputInfo.TYPE_TUNER,"Cable",false)));
numberDuplicateTuners(l); eq("c/.S/HW3=TV;a/.S/HW1=Satellite;b/.S/HW2=Cable;",l);
l=new ArrayList<>(Arrays.asList(in("z/.S/HW9",TvInputInfo.TYPE_TUNER,"TV",false),in("a/.S/HW1",TvInputInfo.TYPE_TUNER,"Antenna",false),in("m/.S/HW5",TvInputInfo.TYPE_TUNER,"TV",false),in("h/.S/HW4",TvInputInfo.TYPE_HDMI,"HDMI 1",false)));
numberDuplicateTuners(l); eq("z/.S/HW9=Tuner 2;a/.S/HW1=Antenna;m/.S/HW5=Tuner 1;h/.S/HW4=HDMI 1;",l);
'''


class LabelPolishTests(unittest.TestCase):
    def test_tuner_numbering_executes_real_method(self):
        src = (SRC / 'Inputs.java').read_text()
        self.assertIn('numberDuplicateTuners(', src, 'numberDuplicateTuners missing from Inputs.java')
        java = (JAVA.replace('INPUT_CLASS', method(src, 'static final class Input'))
                .replace('NUMBER_METHOD', method(src, 'static void numberDuplicateTuners('))
                .replace('BODY', CASES))
        tmp = pathlib.Path(tempfile.mkdtemp(prefix='tuner-label-'))
        (tmp / 'TunerHarness.java').write_text(java)
        subprocess.run(['javac', str(tmp / 'TunerHarness.java')], check=True, cwd=tmp)
        subprocess.run(['java', '-cp', str(tmp), 'TunerHarness'], check=True, cwd=tmp)

    def test_list_applies_numbering_before_sort(self):
        full = (SRC / 'Inputs.java').read_text()
        self.assertIn('static void numberDuplicateTuners(', full, 'numberDuplicateTuners helper missing from Inputs.java')
        src = method(full, 'static List<Input> list(')
        self.assertIn('numberDuplicateTuners(out)', src, 'list() does not call numberDuplicateTuners(out)')
        self.assertLess(src.index('numberDuplicateTuners(out)'), src.index('out.sort('))
        self.assertIn('in.custom = ', src)

    def test_control_center_short_labels(self):
        src = (SRC / 'ControlPanelOverlay.java').read_text()
        body = src[src.index('List<CapsuleSpec> capsules'):src.index('CapsuleSpec cs = capsules.get(i)')]
        for s in ['compactLabel(c, "Quiet", "Sessiz", "Do Not Disturb", "Rahatsız Etmeyin")',
                  'compactLabel(c, "Sleep", "Uyku", "Sleep Timer", "Uyku Zamanlayıcısı")',
                  'compactLabel(c, "Ambient", "Ortam", "Ambient Mode", "Ortam Modu")',
                  'compactLabel(c, "Lock", "Kilit", "Lock Screen", "Kilit Ekranı")',
                  'compactLabel(c, "Now", "Şimdi", "Lock Now", "Şimdi Kilitle")',
                  'compactLabel(c, "Saver", "Koruyucu", "Screensaver", "Ekran Koruyucu")']:
            self.assertIn(s, body)
        # original keys must reach I18n.s only through compactLabel, never directly
        for s in ['I18n.s(c, "Do Not Disturb"', 'I18n.s(c, "Sleep Timer"', 'I18n.s(c, "Ambient Mode"',
                  'I18n.s(c, "Lock Screen"', 'I18n.s(c, "Lock Now"', 'I18n.s(c, "Screensaver"',
                  'I18n.s(c, "Quiet"', 'I18n.s(c, "Sleep"', 'I18n.s(c, "Ambient"', 'I18n.s(c, "Lock"',
                  'I18n.s(c, "Now"', 'I18n.s(c, "Saver"']:
            self.assertNotIn(s, body)
        for s in ['toggleDnd(c)', 'cycleSleepTimer(c)', 'startAmbientMode(c)', 'SleepReceiver.sleepNow(c)']:
            self.assertIn(s, body)

    def test_font_sizes_unchanged(self):
        src = (SRC / 'ControlPanelOverlay.java').read_text()
        self.assertIn('Ui.textSize(tt, 12.5f);', src)
        self.assertIn('Ui.textSize(sub, 10.5f);', src)

    def test_actual_screenshot_generic_tuner_labels(self):
        ids = 'com.vt.source.tuner/.TunerTvInputService/HW'
        cases = '''
String P="%s";
List<Input> l=new ArrayList<>(Arrays.asList(in(P+"1",TvInputInfo.TYPE_TUNER,"Tuner",false),in(P+"0",TvInputInfo.TYPE_TUNER,"Tuner",false)));
numberDuplicateTuners(l); eq(P+"1=Tuner 2;"+P+"0=Tuner 1;",l);
l=new ArrayList<>(Arrays.asList(in("x/.S/HW0",TvInputInfo.TYPE_TUNER,"tuner",false),in("x/.S/HW1",TvInputInfo.TYPE_TUNER,"TUNER",false)));
numberDuplicateTuners(l); eq("x/.S/HW0=Tuner 1;x/.S/HW1=Tuner 2;",l);
l=new ArrayList<>(Arrays.asList(in("x/.S/HW1",TvInputInfo.TYPE_TUNER,"Tuner",false),in("x/.S/HW0",TvInputInfo.TYPE_TUNER,"TV",false)));
numberDuplicateTuners(l); eq("x/.S/HW1=Tuner 2;x/.S/HW0=Tuner 1;",l);
l=new ArrayList<>(Arrays.asList(in("x/.S/HW0",TvInputInfo.TYPE_TUNER,"Tuner",true),in("x/.S/HW1",TvInputInfo.TYPE_TUNER,"Tuner",true)));
numberDuplicateTuners(l); eq("x/.S/HW0=Tuner;x/.S/HW1=Tuner;",l);
l=new ArrayList<>(Arrays.asList(in("x/.S/HW0",TvInputInfo.TYPE_TUNER,"Tuner",false),in("x/.S/HW1",TvInputInfo.TYPE_TUNER,"Cable TV",false)));
numberDuplicateTuners(l); eq("x/.S/HW0=Tuner;x/.S/HW1=Cable TV;",l);
l=new ArrayList<>(Arrays.asList(in("x/.S/HW0",TvInputInfo.TYPE_HDMI,"Tuner",false),in("x/.S/HW1",TvInputInfo.TYPE_HDMI,"Tuner",false)));
numberDuplicateTuners(l); eq("x/.S/HW0=Tuner;x/.S/HW1=Tuner;",l);
''' % ids
        src = (SRC / 'Inputs.java').read_text()
        self.assertIn('static void numberDuplicateTuners(', src)
        java = (JAVA.replace('INPUT_CLASS', method(src, 'static final class Input'))
                .replace('NUMBER_METHOD', method(src, 'static void numberDuplicateTuners('))
                .replace('BODY', cases))
        self.run_java('TunerHarness', java)

    def test_tidy_preserves_cable_tv(self):
        src = (SRC / 'Inputs.java').read_text()
        java = '''import java.util.*;
public class TidyHarness {
static class TvInputInfo {static final int TYPE_TUNER=%d, TYPE_HDMI=%d;}
TIDY
static int n; static void eq(String e,String a){n++;if(!e.equals(a))throw new AssertionError("expected "+e+" got "+a);}
public static void main(String[] x){
 eq("Cable TV",tidy("Cable TV",TvInputInfo.TYPE_TUNER));
 eq("Satellite TV",tidy("Satellite TV",TvInputInfo.TYPE_TUNER));
 eq("TV",tidy("TV",TvInputInfo.TYPE_TUNER));
 eq("TV",tidy("tv",TvInputInfo.TYPE_TUNER));
 eq("TV",tidy("",TvInputInfo.TYPE_TUNER));
 eq("Tuner",tidy("Tuner",TvInputInfo.TYPE_TUNER));
 eq("HDMI 1",tidy("HDMI1",TvInputInfo.TYPE_HDMI));
 System.out.println(n+" tidy assertions passed");
}}
''' % (TUNER, HDMI)
        java = java.replace('TIDY', method(src, 'private static String tidy('))
        self.run_java('TidyHarness', java)

    def test_compact_label_keeps_dictionary_keys_for_other_languages(self):
        src = (SRC / 'ControlPanelOverlay.java').read_text()
        self.assertIn('static String compactLabel(', src, 'compactLabel helper missing from ControlPanelOverlay.java')
        java = '''import java.util.*;
public class CompactHarness {
static class Context {}
static class I18n {
 static final String LANG_EN="en", LANG_TR="tr"; static String lang="en";
 static String getLang(Context c){return lang;}
 static String s(Context c,String en,String tr){ if(lang.equals("en"))return en; if(lang.equals("tr"))return tr; return lang+":"+en; }
}
HELPER
static int n; static void eq(String e,String a){n++;if(!e.equals(a))throw new AssertionError("expected "+e+" got "+a);}
static String q(){return compactLabel(null,"Quiet","Sessiz","Do Not Disturb","Rahat\\u0131z Etmeyin");}
static String k(){return compactLabel(null,"Lock","Kilit","Lock Screen","Kilit Ekran\\u0131");}
public static void main(String[] x){
 I18n.lang="en"; eq("Quiet",q()); eq("Lock",k());
 I18n.lang="tr"; eq("Sessiz",q()); eq("Kilit",k());
 for(String l:new String[]{"sv","hi","de"}){ I18n.lang=l; eq(l+":Do Not Disturb",q()); eq(l+":Lock Screen",k()); }
 System.out.println(n+" compact assertions passed");
}}
'''.replace('HELPER', method(src, 'static String compactLabel('))
        self.run_java('CompactHarness', java)

    def test_memory_state_is_concise_but_keeps_long_key_elsewhere(self):
        src = (SRC / 'ControlPanelOverlay.java').read_text()
        self.assertIn('static String compactLabel(', src, 'compactLabel helper missing')
        self.assertIn('public static String memoryState(', src, 'memoryState missing')
        java = '''import java.util.*;
public class MemoryHarness {
static class Context {}
static class I18n {
 static final String LANG_EN="en", LANG_TR="tr"; static String lang="en";
 static String getLang(Context c){return lang;}
 static String s(Context c,String en,String tr){ if(lang.equals("en"))return en; if(lang.equals("tr"))return tr; return lang+":"+en; }
}
static float memoryUsed(Context c){return 0.4f;}
HELPER
MEMORY
static int n; static void eq(String e,String a){n++;if(!e.equals(a))throw new AssertionError("expected ["+e+"] got ["+a+"]");}
public static void main(String[] x){
 I18n.lang="en"; eq("40% used",memoryState(null));
 I18n.lang="tr"; eq("40% kullan\\u0131mda",memoryState(null));
 for(String l:new String[]{"sv","hi","de"}){ I18n.lang=l; eq("40"+l+":% used \\u00b7 Recent Apps",memoryState(null)); }
 System.out.println(n+" memory assertions passed");
}}
'''.replace('HELPER', method(src, 'static String compactLabel(')).replace('MEMORY', method(src, 'public static String memoryState('))
        self.run_java('MemoryHarness', java)

    def run_java(self, name, java):
        tmp = pathlib.Path(tempfile.mkdtemp(prefix='label-'))
        (tmp / (name + '.java')).write_text(java, encoding='utf-8')
        subprocess.run(['javac', '-encoding', 'UTF-8', str(tmp / (name + '.java'))], check=True, cwd=tmp)
        subprocess.run(['java', '-cp', str(tmp), name], check=True, cwd=tmp)


if __name__ == '__main__':
    unittest.main()
