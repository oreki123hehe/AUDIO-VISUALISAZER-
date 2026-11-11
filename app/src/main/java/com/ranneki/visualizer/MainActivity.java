package com.ranneki.visualizer;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/** Native Android audio-reactive editor. The finished application works offline. */
public class MainActivity extends Activity {
    private static final int PICK_AUDIO=101, PICK_IMAGE=102;
    private static final int BG=0xff0a0e1d, PANEL=0xff141a30, PURPLE=0xffa999ff;
    private final ExecutorService analysisWorker=Executors.newSingleThreadExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private Future<?> analysisTask;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Button[] toggleButtons=new Button[ReactiveView.EFFECT_COUNT],
            mappingButtons=new Button[ReactiveView.EFFECT_COUNT];
    private final LinearLayout[] panels=new LinearLayout[ReactiveView.EFFECT_COUNT];
    private LinearLayout effectContainer;
    private ReactiveView reactive;
    private MediaPlayer player;
    private Button playButton, ratioButton;
    private TextView status, timeline;
    private SeekBar positionBar;
    private int durationMs=0;
    private volatile boolean analyzed=false;
    private SharedPreferences prefs;
    private final Runnable timeUpdater=new Runnable() {
        @Override public void run() {
            if(player!=null) {
                try {
                    int current=player.getCurrentPosition();
                    timeline.setText(format(current)+" / "+format(durationMs));
                    if(durationMs>0) positionBar.setProgress(Math.round(current*1000f/durationMs));
                } catch (IllegalStateException ignored) { }
            }
            ui.postDelayed(this,130);
        }
    };
    private final ReactiveView.AudioClock clock=new ReactiveView.AudioClock() {
        @Override public long positionMs() {
            if(player==null) return 0;
            try { return player.getCurrentPosition(); } catch(Exception ignored) { return 0; }
        }
        @Override public boolean playing() {
            try { return player!=null && player.isPlaying(); }
            catch(Exception ignored) { return false; }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("ranneki-presets",MODE_PRIVATE);
        makeUi();
        ui.post(timeUpdater);
    }
    private void makeUi() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(10),dp(8),dp(10),dp(7));
        setContentView(root);

        TextView title=text("RANNEKI  /  VISUALIZER STUDIO  1.2",17,PURPLE,true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(37)));
        // Horizontal list keeps import/export controls usable on narrow phones.
        android.widget.HorizontalScrollView commands=new android.widget.HorizontalScrollView(this);
        commands.setHorizontalScrollBarEnabled(false);
        LinearLayout commandsRow=row();
        commands.addView(commandsRow);
        addButton(commandsRow,"♪ Lagu",v->choose("audio/*",PICK_AUDIO));
        addButton(commandsRow,"▣ Background",v->choose("image/*",PICK_IMAGE));
        playButton=addButton(commandsRow,"▶ Play",v->togglePlay());
        ratioButton=addButton(commandsRow,"9:16",v->{
            reactive.setPortrait(!reactive.isPortrait());
            ratioButton.setText(reactive.isPortrait()?"9:16":"16:9");
        });
        addButton(commandsRow,"▤ Simpan PNG",v->saveImage());
        addButton(commandsRow,"✓ Preset",v->savePreset());
        root.addView(commands,new LinearLayout.LayoutParams(-1,dp(48)));

        reactive=new ReactiveView(this);
        reactive.setClock(clock);
        FrameLayout viewport=new FrameLayout(this);
        viewport.setBackgroundColor(0xff040612);
        viewport.addView(reactive,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams frameParams=new LinearLayout.LayoutParams(-1,0,1f);
        frameParams.topMargin=dp(5);
        root.addView(viewport,frameParams);

        LinearLayout playback=row();
        playback.setGravity(Gravity.CENTER_VERTICAL);
        timeline=text("00:00 / 00:00",11,0xffb3bbd7,false);
        playback.addView(timeline,new LinearLayout.LayoutParams(dp(99),dp(34)));
        positionBar=new SeekBar(this);
        positionBar.setMax(1000);
        positionBar.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        positionBar.setThumbTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        positionBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int progress,boolean user) {
                if(user && player!=null && durationMs>0) {
                    try { player.seekTo((int)(durationMs*progress/1000f)); }
                    catch(Exception ignored) { }
                }
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        playback.addView(positionBar,new LinearLayout.LayoutParams(0,dp(35),1));
        root.addView(playback,new LinearLayout.LayoutParams(-1,dp(37)));

        status=text("Impor lagu untuk memulai analisis frekuensi dan kick.",11,0xffb6c0db,false);
        status.setPadding(dp(4),dp(2),0,dp(2));
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(34)));
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(3),dp(5),dp(3),dp(24));
        scroll.addView(controls);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(260)));
        TextView section=text("AUDIO REACTIVE  •  CUSTOM FX",13,PURPLE,true);
        controls.addView(section);
        effectContainer=controls;
        preloadPresetSettings();
        LinearLayout spectrumControls=row();
        Button eco=addButton(spectrumControls,reactive.isEconomyMode()?"⚡ ECO 30 FPS":"⚡ QUALITY 60 FPS",v->{});
        eco.setOnClickListener(v->{
            reactive.setEconomyMode(!reactive.isEconomyMode());
            eco.setText(reactive.isEconomyMode()?"⚡ ECO 30 FPS":"⚡ QUALITY 60 FPS");
        });
        Button spectrum=addButton(spectrumControls,reactive.isSpectrumEnabled()?"◉ Spectrum ON":"○ Spectrum OFF",v->{});
        spectrum.setOnClickListener(v->{
            reactive.setSpectrumEnabled(!reactive.isSpectrumEnabled());
            spectrum.setText(reactive.isSpectrumEnabled()?"◉ Spectrum ON":"○ Spectrum OFF");
        });
        controls.addView(spectrumControls);
        addSlider(controls,"Ukuran Spectrum",reactive.getSpectrumScale(),value->reactive.setSpectrumScale(value));
        addSlider(controls,"Camera Shake",reactive.getCameraShake(),value->reactive.setCameraShake(value));
        for(int i=0;i<ReactiveView.EFFECT_COUNT;i++) addEffectPanel(controls,i);
        reorderPanels();
        TextView help=text("Semua FX memakai peta audio sendiri. Pilih Frekuensi custom lalu set Hz min/max di menu Lanjutan. Kick dan snare masih perkiraan onset, bukan stem AI. V1.2 menyimpan PNG, belum ekspor MP4.",11,0xff9ba7c5,false);
        help.setPadding(0,dp(12),0,dp(5));
        controls.addView(help);
        restorePreset();
    }

    private void addEffectPanel(LinearLayout controls,final int effect) {
        LinearLayout card=new LinearLayout(this);
        panels[effect]=card;
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10),dp(8),dp(10),dp(7));
        card.setBackground(rounded(PANEL,12));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.topMargin=dp(9);
        controls.addView(card,cp);
        LinearLayout header=row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=text(ReactiveView.EFFECT_NAMES[effect],14,Color.WHITE,true);
        header.addView(name,new LinearLayout.LayoutParams(0,dp(38),1));
        toggleButtons[effect]=addButton(header,reactive.isEnabled(effect)?"ON":"OFF",v->{
            reactive.setEnabled(effect,!reactive.isEnabled(effect));
            toggleButtons[effect].setText(reactive.isEnabled(effect)?"ON":"OFF");
        });
        card.addView(header);
        LinearLayout sourceLine=row();
        mappingButtons[effect]=addButton(sourceLine,"→ "+ReactiveView.SOURCES[reactive.getSource(effect)],v->{
            reactive.setSource(effect,reactive.getSource(effect)+1);
            mappingButtons[effect].setText("→ "+ReactiveView.SOURCES[reactive.getSource(effect)]);
        });
        addButton(sourceLine,"↑",v->moveCard(effect,-1));
        addButton(sourceLine,"↓",v->moveCard(effect,1));
        card.addView(sourceLine);
        addSlider(card,"Ukuran",reactive.getSize(effect),value->reactive.setSize(effect,value));
        addSlider(card,"Jumlah",reactive.getDensity(effect),value->reactive.setDensity(effect,value));
        addSlider(card,"Reaktivitas",reactive.getStrength(effect),value->reactive.setStrength(effect,value));
        LinearLayout advanced=new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);
        Button reveal=addButton(card,"⚙ Lanjutan +",v->{});
        reveal.setOnClickListener(v->{
            boolean show=advanced.getVisibility()!=View.VISIBLE;
            advanced.setVisibility(show?View.VISIBLE:View.GONE);
            reveal.setText(show?"⚙ Lanjutan −":"⚙ Lanjutan +");
        });
        card.addView(advanced);
        addSlider(advanced,"Opacity",reactive.getOpacity(effect),value->reactive.setOpacity(effect,value));
        addSlider(advanced,"Kecepatan",reactive.getVelocity(effect),value->reactive.setVelocity(effect,value));
        addSlider(advanced,"Threshold",reactive.getThreshold(effect),value->reactive.setThreshold(effect,value));
        addSlider(advanced,"Attack ms /10",reactive.getAttack(effect)/10,value->reactive.setAttack(effect,value*10));
        addSlider(advanced,"Release ms /15",reactive.getRelease(effect)/15,value->reactive.setRelease(effect,value*15));
        addFrequencySlider(advanced,"Min Hz",reactive.getMinHz(effect),value->{
            reactive.setFrequency(effect,value,Math.max(value,reactive.getMaxHz(effect)));
        });
        addFrequencySlider(advanced,"Max Hz",reactive.getMaxHz(effect),value->{
            reactive.setFrequency(effect,Math.min(value,reactive.getMinHz(effect)),value);
        });
    }
    private void moveCard(int effect,int delta) {
        if(reactive.moveLayer(effect,delta)) reorderPanels();
    }
    private void reorderPanels() {
        if(effectContainer==null) return;
        for(LinearLayout card:panels) if(card!=null && card.getParent()==effectContainer)
            effectContainer.removeView(card);
        // Section heading, spectrum toggle, and two global sliders stay above FX cards.
        int index=4;
        for(int effect:reactive.getLayerOrder()) {
            LinearLayout card=panels[effect];
            if(card!=null) {
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
                cp.topMargin=dp(9);
                effectContainer.addView(card,index++,cp);
            }
        }
    }
    private static int progressToHz(int progress) {
        return (int)Math.round(25*Math.pow(400,Math.max(0,Math.min(100,progress))/100.0));
    }
    private static int hzToProgress(int hz) {
        return (int)Math.round(100*Math.log(Math.max(25,hz)/25.0)/Math.log(400));
    }
    private void addFrequencySlider(LinearLayout parent,String label,int initial,OnValue callback) {
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(label,11,0xffb8c2dd,false);
        line.addView(title,new LinearLayout.LayoutParams(dp(116),dp(31)));
        SeekBar seek=new SeekBar(this);
        seek.setMax(100);seek.setProgress(hzToProgress(initial));
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        line.addView(seek,new LinearLayout.LayoutParams(0,dp(34),1));
        TextView number=text(initial+" Hz",10,Color.WHITE,false);
        number.setGravity(Gravity.CENTER);
        line.addView(number,new LinearLayout.LayoutParams(dp(68),dp(30)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean user) {
                if(user) {
                    int hz=progressToHz(progress);
                    callback.set(hz);number.setText(hz+" Hz");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(line);
    }
    private interface OnValue { void set(int value); }
    private void addSlider(LinearLayout parent,String label,int initial,OnValue callback) {
        LinearLayout line=row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(label,11,0xffb8c2dd,false);
        line.addView(title,new LinearLayout.LayoutParams(dp(116),dp(31)));
        SeekBar seek=new SeekBar(this);
        seek.setMax(100);
        seek.setProgress(initial);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(PURPLE));
        line.addView(seek,new LinearLayout.LayoutParams(0,dp(34),1));
        TextView number=text(String.valueOf(initial),11,Color.WHITE,false);
        number.setGravity(Gravity.CENTER);
        line.addView(number,new LinearLayout.LayoutParams(dp(28),dp(30)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb,int value,boolean user) {
                callback.set(value);
                number.setText(String.valueOf(value));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        parent.addView(line);
    }
    private void choose(String mime,int request) {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,request);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch(Exception ignored) { }
        if(request==PICK_AUDIO) loadAudio(uri);
        else if(request==PICK_IMAGE) loadImage(uri);
    }
    private void loadAudio(Uri uri) {
        final int version=generation.incrementAndGet();
        if(analysisTask!=null) analysisTask.cancel(true);
        analyzed=false;
        if(player!=null) { player.release();player=null; }
        reactive.setTrack(null);
        playButton.setText("▶ Play");
        durationMs=0;
        try {
            MediaPlayer next=new MediaPlayer();
            next.setDataSource(this,uri);
            next.prepare();
            next.setOnCompletionListener(mp->{playButton.setText("▶ Play");reactive.invalidate();});
            player=next;
            durationMs=player.getDuration();
        } catch(Exception e) {
            status.setText("Audio gagal dibuka: "+e.getMessage());
            if(player!=null) {player.release();player=null;}
            return;
        }
        status.setText("Menganalisis kick, bass, mid, dan treble…");
        analysisTask=analysisWorker.submit(()->{
            try {
                AudioAnalyzer.Track result=AudioAnalyzer.analyze(getApplicationContext(),uri);
                runOnUiThread(()->{
                    if(version!=generation.get() || isFinishing() || isDestroyed()) return;
                    reactive.setTrack(result);
                    analyzed=true;
                    status.setText("Siap! Efek mengikuti musik. Pilih sumber di tiap layer.");
                    Toast.makeText(this,"Analisis audio selesai",Toast.LENGTH_SHORT).show();
                });
            } catch(Exception e) {
                runOnUiThread(()->{
                    if(version!=generation.get() || isFinishing() || isDestroyed()) return;
                    status.setText("Analisis gagal: "+e.getMessage());
                });
            }
        });
    }
    private void loadImage(Uri uri) {
        try {
            BitmapFactory.Options bounds=new BitmapFactory.Options();
            bounds.inJustDecodeBounds=true;
            try(InputStream in=getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in,null,bounds);
            }
            int sample=1;
            while(bounds.outWidth/sample>2048 || bounds.outHeight/sample>2048) sample*=2;
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inSampleSize=sample;
            Bitmap bitmap;
            try(InputStream in=getContentResolver().openInputStream(uri)) {
                bitmap=BitmapFactory.decodeStream(in,null,options);
            }
            if(bitmap==null) throw new IllegalArgumentException("Gambar tidak terbaca");
            reactive.setBackdrop(bitmap);
            status.setText("Background terpasang. Atur mapping efek sesuai musik.");
        } catch(Exception e) {
            status.setText("Gambar gagal dibuka: "+e.getMessage());
        }
    }
    private void togglePlay() {
        if(player==null) { Toast.makeText(this,"Impor lagu dulu",Toast.LENGTH_SHORT).show();return; }
        if(!analyzed) { Toast.makeText(this,"Tunggu analisis audio selesai",Toast.LENGTH_SHORT).show();return; }
        try {
            if(player.isPlaying()) { player.pause();playButton.setText("▶ Play"); }
            else {player.start();playButton.setText("Ⅱ Pause");}
            reactive.invalidate();
        } catch(Exception e) {status.setText("Playback gagal: "+e.getMessage());}
    }
    private void savePreset() {
        SharedPreferences.Editor ed=prefs.edit();
        ed.putBoolean("portrait",reactive.isPortrait());
        ed.putBoolean("economyMode",reactive.isEconomyMode());
        ed.putBoolean("spectrumEnabled",reactive.isSpectrumEnabled());
        ed.putInt("spectrumScale",reactive.getSpectrumScale());
        ed.putInt("cameraShake",reactive.getCameraShake());
        StringBuilder orderText=new StringBuilder();
        for(int index:reactive.getLayerOrder()) {
            if(orderText.length()>0) orderText.append(',');
            orderText.append(index);
        }
        ed.putString("layerOrder",orderText.toString());
        for(int i=0;i<ReactiveView.EFFECT_COUNT;i++) {
            ed.putBoolean("on"+i,reactive.isEnabled(i));
            ed.putInt("source"+i,reactive.getSource(i));
            ed.putInt("size"+i,reactive.getSize(i));
            ed.putInt("density"+i,reactive.getDensity(i));
            ed.putInt("strength"+i,reactive.getStrength(i));
            ed.putInt("opacity"+i,reactive.getOpacity(i));
            ed.putInt("velocity"+i,reactive.getVelocity(i));
            ed.putInt("threshold"+i,reactive.getThreshold(i));
            ed.putInt("attack"+i,reactive.getAttack(i));
            ed.putInt("release"+i,reactive.getRelease(i));
            ed.putInt("minHz"+i,reactive.getMinHz(i));
            ed.putInt("maxHz"+i,reactive.getMaxHz(i));
        }
        ed.apply();
        Toast.makeText(this,"Preset tersimpan di HP",Toast.LENGTH_SHORT).show();
    }
    private void preloadPresetSettings() {
        reactive.setPortrait(prefs.getBoolean("portrait",true));
        reactive.setEconomyMode(prefs.getBoolean("economyMode",true));
        reactive.setSpectrumEnabled(prefs.getBoolean("spectrumEnabled",true));
        reactive.setSpectrumScale(prefs.getInt("spectrumScale",55));
        reactive.setCameraShake(prefs.getInt("cameraShake",0));
        int[] initialSources={2,1,4,0,1,3,0,4,2,0};
        for(int i=0;i<ReactiveView.EFFECT_COUNT;i++) {
            reactive.setEnabled(i,prefs.getBoolean("on"+i,i<4));
            reactive.setSource(i,prefs.getInt("source"+i,initialSources[i]));
            reactive.setSize(i,prefs.getInt("size"+i,55));
            reactive.setDensity(i,prefs.getInt("density"+i,60));
            reactive.setStrength(i,prefs.getInt("strength"+i,80));
            reactive.setOpacity(i,prefs.getInt("opacity"+i,75));
            reactive.setVelocity(i,prefs.getInt("velocity"+i,50));
            reactive.setThreshold(i,prefs.getInt("threshold"+i,15));
            reactive.setAttack(i,prefs.getInt("attack"+i,18));
            reactive.setRelease(i,prefs.getInt("release"+i,165));
            reactive.setFrequency(i,prefs.getInt("minHz"+i,35),prefs.getInt("maxHz"+i,150));
        }
        String[] encoded=prefs.getString("layerOrder","").split(",");
        if(encoded.length==ReactiveView.EFFECT_COUNT) {
            try {
                int[] indices=new int[encoded.length];
                for(int i=0;i<indices.length;i++) indices[i]=Integer.parseInt(encoded[i]);
                reactive.setLayerOrder(indices);
            } catch(NumberFormatException ignored) { }
        }
    }
    private void restorePreset() {
        ratioButton.setText(reactive.isPortrait()?"9:16":"16:9");
        for(int i=0;i<ReactiveView.EFFECT_COUNT;i++) {
            toggleButtons[i].setText(reactive.isEnabled(i)?"ON":"OFF");
            mappingButtons[i].setText("→ "+ReactiveView.SOURCES[reactive.getSource(i)]);
        }
    }
    private void saveImage() {
        try {
            int w=reactive.isPortrait()?720:1280;
            int h=reactive.isPortrait()?1280:720;
            Bitmap bitmap=reactive.snapshot(w,h);
            String name="Ranneki_"+System.currentTimeMillis()+".png";
            if(Build.VERSION.SDK_INT>=29) {
                ContentValues values=new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME,name);
                values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/RannekiStudio");
                Uri file=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                if(file==null) throw new IllegalStateException("Galeri tidak tersedia");
                try(OutputStream out=getContentResolver().openOutputStream(file)) {
                    if(out==null || !bitmap.compress(Bitmap.CompressFormat.PNG,100,out))
                        throw new IllegalStateException("Gagal menyimpan PNG");
                }
            } else {
                File folder=new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),"RannekiStudio");
                if(!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Folder gagal dibuat");
                try(OutputStream out=new FileOutputStream(new File(folder,name))) {
                    bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
                }
            }
            bitmap.recycle();
            Toast.makeText(this,"Frame PNG disimpan: "+name,Toast.LENGTH_LONG).show();
        } catch(Exception e) {
            status.setText("Gagal menyimpan PNG: "+e.getMessage());
        }
    }
    private static String format(int ms) {
        int seconds=Math.max(0,ms/1000);
        return String.format(java.util.Locale.US,"%02d:%02d",seconds/60,seconds%60);
    }
    private TextView text(String str,int sp,int color,boolean bold) {
        TextView v=new TextView(this);
        v.setText(str);v.setTextSize(sp);v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if(bold) v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return v;
    }
    private LinearLayout row() {
        LinearLayout layout=new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }
    private interface Click { void go(View view); }
    private Button addButton(LinearLayout root,String title,Click click) {
        Button b=new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(8),0,dp(8),0);
        b.setMinimumWidth(0);
        b.setMinWidth(0);
        b.setMinimumHeight(0);
        b.setMinHeight(0);
        b.setBackground(rounded(0xff252c48,9));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(34));
        lp.setMargins(dp(2),0,dp(3),0);
        root.addView(b,lp);
        b.setOnClickListener(click::go);
        return b;
    }
    private GradientDrawable rounded(int color,int radius) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1),0xff303953);
        return d;
    }
    private int dp(float value) {return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onPause() {
        super.onPause();
        if(player!=null) {
            try {player.pause();} catch(Exception ignored) { }
        }
        if(playButton!=null) playButton.setText("▶ Play");
    }
    @Override protected void onDestroy() {
        generation.incrementAndGet();
        ui.removeCallbacks(timeUpdater);
        if(analysisTask!=null) analysisTask.cancel(true);
        analysisWorker.shutdownNow();
        if(player!=null) {player.release();player=null;}
        super.onDestroy();
    }
}
