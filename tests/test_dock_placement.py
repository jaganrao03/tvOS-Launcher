"""Host Java regression tests using the actual placement code, not a reimplementation."""
import pathlib
import re
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / 'src/com/techgenius/tvoslauncher'


def method(text, signature):
    start = text.index(signature)
    brace = text.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]


class DockPlacementTests(unittest.TestCase):
    def test_inputs_dock_optical_size_and_stroke(self):
        scene = (SRC / 'SceneHome.java').read_text()
        glyphs = (SRC / 'Glyphs.java').read_text()
        self.assertIn('Glyphs.get("input_dock", inputGlyphSize, 0xEBF4F4F6)', scene)
        self.assertIn('Math.round(dp(22) * scale)', scene)
        self.assertRegex(glyphs, r'case "input_dock":\s*p.setStrokeWidth\(s \* 0\.06f\);')
        self.assertIn('if (isGear || isMore || isInputs)', scene)
        self.assertIn('.translationZ(isGear || isMore || isInputs ? 0f : dp(12))', scene)
        self.assertIn('focusRing.setStroke(dp(2.2f), Color.WHITE)', scene)
        self.assertIn('.scaleX(1.20f).scaleY(1.20f)', scene)

    def test_real_placement_logic(self):
        src = (SRC / 'MainActivity.java').read_text()
        # Regex group returns only the body after "static final".
        constants = '\n'.join('static final ' + x for x in re.findall(r'static final (String PREF_\w+ = [^;]+;|List<String> (?:DEFAULT_HIDDEN|DEFAULT_OTHER|ALWAYS_OTHER) = Arrays.asList\([\s\S]*?\);)', src))
        start = src.index('        Set<String> hidden = prefs.getStringSet(PREF_HIDDEN', src.index('void loadApps('))
        end = src.index('        Collections.sort(hiddenApps', start)
        placement = src[start:end]
        actual = method(src, 'void setOtherApp(')
        java = '''import java.util.*;
public class PlacementHarness {
CONSTANTS
static class Entry {String key,pkg,label; Entry(String p){key=p+"/.Main";pkg=p;label=p;}}
static class Prefs {Map<String,Object> m=new HashMap<>(); boolean contains(String k){return m.containsKey(k);} boolean getBoolean(String k,boolean d){return (boolean)m.getOrDefault(k,d);} @SuppressWarnings("unchecked") Set<String> getStringSet(String k,Set<String>d){return (Set<String>)m.getOrDefault(k,d);} Editor edit(){return new Editor();} class Editor {Editor putStringSet(String k,Set<String>v){m.put(k,new HashSet<>(v));return this;} Editor putBoolean(String k,boolean v){m.put(k,v);return this;} void apply(){}}}
Prefs prefs=new Prefs(); Map<String,Entry> all=new LinkedHashMap<>(); List<Entry> apps=new ArrayList<>(),otherApps=new ArrayList<>(),hiddenApps=new ArrayList<>(); int focusedIndex=0; boolean folderMode=false; Object shelfEntry;
void loadApps(){apps.clear();otherApps.clear();hiddenApps.clear(); PLACEMENT apps.addAll(pool.values());}
void ensureInFirstDockRow(String k){} void buildGrid(){} void focusIndex(int i){} List<Entry> shown=apps; void toast(String t){} static class Scenes {static boolean isScenesLook(Object x){return true;}} static class I18n {static String s(Object x,String en,String tr){return en;}}
ACTUAL
static int assertions; static void check(boolean ok,String msg){assertions++;if(!ok)throw new AssertionError(msg);}
public static void main(String[] args){
 PlacementHarness h=new PlacementHarness(); Entry a=new Entry("app.fluffy"), b=new Entry("ch.protonvpn.android");h.all.put(a.key,a);h.all.put(b.key,b);h.loadApps();
 check(h.otherApps.contains(a),"utility should default to Other Apps");
 h.setOtherApp(a,false);check(h.apps.contains(a),"explicit pin must override utility default");h.loadApps();check(h.apps.contains(a),"pin must survive reload");
 h.setOtherApp(b,false);check(h.apps.contains(b),"default-other app can be pinned");h.loadApps();check(h.otherApps.isEmpty(),"empty selection must not restore defaults");
 h.setOtherApp(a,true);check(h.otherApps.contains(a),"move back to Other Apps");h.setOtherApp(a,false);check(h.apps.contains(a),"repin after moving back");
 Entry hidden=new Entry(DEFAULT_HIDDEN.get(0));h.all.put(hidden.key,hidden);h.loadApps();check(h.hiddenApps.contains(hidden),"first-run hidden defaults");
 h.prefs.edit().putStringSet(PREF_HIDDEN,Collections.emptySet()).apply();h.loadApps();check(!h.hiddenApps.contains(hidden),"explicit empty hidden set survives reload");
 h.prefs.m.remove("placement_defaults_v2");h.prefs.m.remove(PREF_OTHER);h.loadApps();check(h.otherApps.contains(a),"reset restores default utility placement");
 System.out.println(assertions+" placement assertions passed");
}}
'''.replace('CONSTANTS', constants).replace('PLACEMENT', placement.replace('AppEntry', 'Entry')).replace('ACTUAL', actual.replace('AppEntry', 'Entry'))
        tmp = pathlib.Path(tempfile.mkdtemp(prefix='dock-regression-'))
        (tmp / 'PlacementHarness.java').write_text(java)
        subprocess.run(['javac', str(tmp / 'PlacementHarness.java')], check=True, cwd=tmp)
        subprocess.run(['java', '-cp', str(tmp), 'PlacementHarness'], check=True, cwd=tmp)


if __name__ == '__main__':
    unittest.main()
