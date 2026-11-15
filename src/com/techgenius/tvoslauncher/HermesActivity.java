package com.techgenius.tvoslauncher;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/** ADB display client only: never captures audio or opens a socket. */
public class HermesActivity extends Activity {
    private final Handler timer=new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private int generation;
    private boolean ready;
    private static final HermesContract.Tracker tracker=new HermesContract.Tracker();
    private String activeRequest;
    private static final String PRIVACY="The existing laptop microphone handles the local always-listening wake word. This TV only displays replies; it never records audio. Anyone near this shared TV can read replies. Disconnecting here disables TV integration, NOT the laptop microphone. Use the laptop controls to mute its microphone. Back dismisses this panel.";
    static boolean enabled(android.content.Context c) { return c.getSharedPreferences("hermes_tv",0).getBoolean("enabled",false); }
    private void enable(boolean on) {
        if(!getSharedPreferences("hermes_tv",0).edit().putBoolean("enabled",on).commit())
            Toast.makeText(this,"Could not save TV integration setting",Toast.LENGTH_LONG).show();
    }
    @Override public void onCreate(Bundle b) { super.onCreate(b); receive(getIntent(),true); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); receive(i,false); }

    private void receive(Intent i,boolean first) {
        String state=i.getStringExtra("state"), request=i.getStringExtra("request"), text=i.getStringExtra("text");
        if(text==null) text="";
        if(!HermesContract.valid(state,request,text)) { if(first) finish(); return; }
        if(!"setup".equals(state)&&!enabled(this)) { if(first) finish(); return; }
        if(!tracker.accept(state,request)) { if(first) finish(); return; }
        activeRequest="setup".equals(state)?null:request;
        generation++; int current=generation;
        timer.removeCallbacksAndMessages(null); stopSpeech();
        LinearLayout panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(36),dp(28),dp(36),dp(28));
        GradientDrawable bg=new GradientDrawable(); bg.setColor(0xEE11151D); bg.setCornerRadius(dp(24)); bg.setStroke(dp(1),0x667E92AF); panel.setBackground(bg);
        TextView heading=label("Hey Hermes · "+state,28); panel.addView(heading);
        TextView body=label("setup".equals(state)?PRIVACY:text,22); body.setPadding(0,dp(18),0,dp(18));
        ScrollView scroll=new ScrollView(this); scroll.addView(body); panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if("setup".equals(state)) {
            Button toggle=new Button(this); toggle.setText(enabled(this)?"TV integration: ON — turn off":"TV integration: OFF — turn on");
            toggle.setOnClickListener(v->{ enable(!enabled(this)); toggle.setText(enabled(this)?"TV integration: ON — turn off":"TV integration: OFF — turn on"); });
            panel.addView(toggle); toggle.requestFocus();
        } else {
            Button disconnect=new Button(this); disconnect.setText("Disconnect TV integration (laptop mic stays on)");
            disconnect.setOnClickListener(v->{enable(false); finish();}); panel.addView(disconnect); disconnect.requestFocus();
        }
        Button dismiss=new Button(this); dismiss.setText("Dismiss / Back"); dismiss.setOnClickListener(v->finish()); panel.addView(dismiss);
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(0x22000000);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(760),dp(440),Gravity.CENTER); root.addView(panel,lp); setContentView(root);
        long timeout=HermesContract.timeout(state); if(timeout>0) timer.postDelayed(this::finish,timeout);
        if(HermesContract.shouldSpeak(state,i.getBooleanExtra("speak",false),text)) {
            final String spoken=text;
            tts=new TextToSpeech(this,status->{
                if(current!=generation||isFinishing()||status!=TextToSpeech.SUCCESS) return;
                // Post because some engines complete initialization before the constructor returns.
                timer.post(()->{ if(current==generation&&tts!=null&&!isFinishing()) {
                    ready=true; tts.setAudioAttributes(new android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    tts.speak(spoken,TextToSpeech.QUEUE_FLUSH,null,request);
                }});
            });
        }
    }
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }
    private TextView label(String s,int size) { TextView t=new TextView(this); t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(size); return t; }
    private void stopSpeech() { ready=false; if(tts!=null) { tts.stop(); tts.shutdown(); tts=null; } }
    @Override public void onBackPressed() { finish(); }
    @Override public void finish() { tracker.dismiss(activeRequest); super.finish(); }
    @Override protected void onDestroy() { generation++; timer.removeCallbacksAndMessages(null); stopSpeech(); super.onDestroy(); }
}
