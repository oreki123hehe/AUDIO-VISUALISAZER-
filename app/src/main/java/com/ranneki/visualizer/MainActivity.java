package com.ranneki.visualizer;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.EditText;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Native Android audio-reactive editor. The finished application works offline. */
public class MainActivity extends Activity {
    private static final int PICK_AUDIO=101, PICK_IMAGE=102, PICK_EXPORT=103,
            PICK_LAYER_IMAGE=104;
    private static final int BG=0xff0a0e1d, PANEL=0xff141a30, PURPLE=0xffa999ff;
    private final ExecutorService analysisWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService exportWorker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean exportCancel=new AtomicBoolean(false);
    private volatile boolean exporting=false;
    private Uri musicUri;
    private int pendingImageLayer=-1;
    private int exportWidth=720,exportHeight=1280;
    private final AtomicInteger generation=new AtomicInteger();
    private Future<?> analysisTask;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Button[] toggleButtons=new Button[ReactiveView.MAX_LAYERS],
            mappingButtons=new Button[ReactiveView.MAX_LAYERS];
    private final LinearLayout[] panels=new LinearLayout[ReactiveView.MAX_LAYERS];
    private final int[] selectedKeyframeParam=new int[ReactiveView.MAX_LAYERS];
    private LinearLayout effectContainer;
    private ReactiveView reactive;
    private MediaPlayer player;
    private Button playButton, ratioButton;
    private TextView status, timeline;
    private TimelineView timelineView;
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
                    if(timelineView!=null) timelineView.setPosition(current);
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

        TextView title=text("RANNEKI  /  VISUALIZER STUDIO  1.7",17,PURPLE,true);
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
        addButton(commandsRow,"● Export MP4",v->chooseExport());
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
                    try {
                        player.seekTo((int)(durationMs*progress/1000f));
                        reactive.invalidate();
                    } catch(Exception ignored) { }
                }
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        playback.addView(positionBar,new LinearLayout.LayoutParams(0,dp(35),1));
        root.addView(playback,new LinearLayout.LayoutParams(-1,dp(37)));
        timelineView=new TimelineView(this);
        timelineView.setScene(reactive);
        timelineView.setDuration(durationMs);
        timelineView.setSeekListener(ms->{
            if(player!=null){
                try{player.seekTo((int)Math.min(Integer.MAX_VALUE,ms));reactive.invalidate();}
                catch(Exception ignored) { }
            }
        });
        LinearLayout timelineHeader=row();
        timelineView.setVisibility(View.GONE);
        Button timelineToggle=addButton(timelineHeader,"▤ Timeline ▸",v->{});
        timelineToggle.setOnClickListener(v->{
            boolean show=timelineView.getVisibility()!=View.VISIBLE;
            timelineView.setVisibility(show?View.VISIBLE:View.GONE);
            timelineToggle.setText(show?"▤ Timeline ▾":"▤ Timeline ▸");
        });
        timelineHeader.addView(text("Waveform • FX lanes • keyframe",11,0xff8f9ec5,false));
        root.addView(timelineHeader,new LinearLayout.LayoutParams(-1,dp(37)));
        root.addView(timelineView,new LinearLayout.LayoutParams(-1,dp(179)));

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
        addButton(controls,"＋ Tambah Layer FX",v->chooseLayer());
        for(int id:reactive.getLayerOrder()) addEffectPanel(controls,id);
        reorderPanels();
        TextView help=text("V1.7: waveform timeline dan keyframe 16 parameter per layer. Efek mengikuti kick/bass secara independen. Ekspor MP4 H.264 + AAC offline. Kick/snare masih estimasi onset.",11,0xff9ba7c5,false);
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
        TextView name=text("#"+(effect+1)+"  "+reactive.getLayerName(effect),14,Color.WHITE,true);
        name.setOnClickListener(v->{
            EditText input=new EditText(this);input.setSingleLine(true);
            input.setText(reactive.getLayerName(effect));
            new AlertDialog.Builder(this).setTitle("Ganti nama layer")
                .setView(input).setNegativeButton("Batal",(d,w)->{})
                .setPositiveButton("Simpan",(d,w)->{
                    reactive.setLayerName(effect,input.getText().toString());
                    timelineView.refresh();
                    name.setText("#"+(effect+1)+"  "+reactive.getLayerName(effect));
                }).show();
        });
        header.addView(name,new LinearLayout.LayoutParams(0,dp(38),1));
        addButton(header,"◎",v->{timelineView.setFocusLayer(effect);timelineView.refresh();});
        toggleButtons[effect]=addButton(header,reactive.isEnabled(effect)?"ON":"OFF",v->{
            reactive.setEnabled(effect,!reactive.isEnabled(effect));
            toggleButtons[effect].setText(reactive.isEnabled(effect)?"ON":"OFF");timelineView.refresh();
        });
        card.addView(header);
        LinearLayout sourceLine=row();
        mappingButtons[effect]=addButton(sourceLine,"→ "+ReactiveView.SOURCES[reactive.getSource(effect)],v->{
            reactive.setSource(effect,reactive.getSource(effect)+1);
            mappingButtons[effect].setText("→ "+ReactiveView.SOURCES[reactive.getSource(effect)]);
        });
        addButton(sourceLine,"↑",v->moveCard(effect,-1));
        addButton(sourceLine,"↓",v->moveCard(effect,1));
        addButton(sourceLine,"⧉",v->{
            int added=reactive.duplicateLayer(effect);
            if(added<0) {toast("Maksimum 48 layer aktif");return;}
            addEffectPanel(effectContainer,added);reorderPanels();timelineView.refresh();
        });
        addButton(sourceLine,"✕",v->new AlertDialog.Builder(this)
                .setTitle("Hapus layer?")
                .setMessage("Hapus "+ReactiveView.LAYER_NAMES[reactive.getLayerType(effect)]+" dari scene?")
                .setNegativeButton("Batal",(d,w)->{})
                .setPositiveButton("Hapus",(d,w)->{
                    reactive.removeLayer(effect);
                    if(card.getParent()==effectContainer) effectContainer.removeView(card);
                    timelineView.refresh();
                    panels[effect]=null;toggleButtons[effect]=null;mappingButtons[effect]=null;
                    reorderPanels();
                }).show());
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
        if(reactive.getLayerType(effect)==ReactiveView.TEXT){
            addButton(advanced,"✎ Edit Teks",v->editTextLayer(effect));
            addButton(advanced,"◉ Warna Teks",v->editLayerColor(effect));
        } else if(reactive.getLayerType(effect)==ReactiveView.IMAGE) {
            addButton(advanced,"▣ Ganti Gambar",v->requestLayerImage(effect));
        }
        TextView layoutLabel=text("TRANSFORM • tap nama layer untuk mengganti nama",11,PURPLE,true);
        advanced.addView(layoutLabel);
        addSlider(advanced,"Posisi X",reactive.getLayerX(effect),value->reactive.setLayerX(effect,value));
        addSlider(advanced,"Posisi Y",reactive.getLayerY(effect),value->reactive.setLayerY(effect,value));
        addSlider(advanced,"Skala Layer",reactive.getLayerScale(effect),value->reactive.setLayerScale(effect,value));
        addSlider(advanced,"Rotasi Layer",reactive.getLayerRotation(effect),value->reactive.setLayerRotation(effect,value));
        TextView rangeLabel=text("TIMELINE: "+getRangeTitle(effect),11,PURPLE,true);
        advanced.addView(rangeLabel);
        LinearLayout rangeRow=row();
        addButton(rangeRow,"Mulai di sini",v->{reactive.setLayerStart(effect,currentPosition());rangeLabel.setText("TIMELINE: "+getRangeTitle(effect));timelineView.refresh();});
        addButton(rangeRow,"Akhir di sini",v->{reactive.setLayerEnd(effect,currentPosition());rangeLabel.setText("TIMELINE: "+getRangeTitle(effect));timelineView.refresh();});
        addButton(rangeRow,"Reset",v->{reactive.clearLayerRange(effect);rangeLabel.setText("TIMELINE: "+getRangeTitle(effect));timelineView.refresh();});
        advanced.addView(rangeRow);
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
        TextView routes=text("ROUTING PER PARAMETER  •  INDEPENDEN",11,PURPLE,true);
        advanced.addView(routes);
        for(int property=0;property<ReactiveView.PROPERTY_NAMES.length;property++) {
            final int param=property;
            Button button=addButton(advanced,propertyTitle(effect,param),v->{});
            button.setOnClickListener(v->{
                int current=reactive.getPropertySource(effect,param);
                reactive.setPropertySource(effect,param,current+1>=ReactiveView.SOURCES.length?-1:current+1);
                button.setText(propertyTitle(effect,param));
            });
        }
        LinearLayout material=row();
        Button blend=addButton(material,"Blend: "+ReactiveView.BLEND_NAMES[reactive.getBlend(effect)],v->{});
        blend.setOnClickListener(v->{
            reactive.setBlend(effect,reactive.getBlend(effect)+1);
            blend.setText("Blend: "+ReactiveView.BLEND_NAMES[reactive.getBlend(effect)]);
        });
        Button mask=addButton(material,"Mask: "+ReactiveView.MASK_NAMES[reactive.getMask(effect)],v->{});
        mask.setOnClickListener(v->{
            reactive.setMask(effect,reactive.getMask(effect)+1);
            mask.setText("Mask: "+ReactiveView.MASK_NAMES[reactive.getMask(effect)]);
        });
        advanced.addView(material);
        addSlider(advanced,"Mask Radius",reactive.getMaskRadius(effect),value->reactive.setMaskRadius(effect,value));
        addSlider(advanced,"Arah (0=auto)",reactive.getDirection(effect),value->reactive.setDirection(effect,value));
        TextView automationLabel=text(automationSummary(effect),11,PURPLE,false);
        advanced.addView(automationLabel);
        Button selectParam=addButton(advanced,"↔ Parameter: "+ReactiveView.KEYFRAME_NAMES[selectedKeyframeParam[effect]],v->{});
        selectParam.setOnClickListener(v->{
            selectedKeyframeParam[effect]=(selectedKeyframeParam[effect]+1)%ReactiveView.KEYFRAME_NAMES.length;
            selectParam.setText("↔ Parameter: "+ReactiveView.KEYFRAME_NAMES[selectedKeyframeParam[effect]]);
            automationLabel.setText(automationSummary(effect));
        });
        LinearLayout keyframes=row();
        addButton(keyframes,"＋ Edit Titik",v->editKeyframe(effect,automationLabel));
        addButton(keyframes,"− Titik",v->{
            reactive.removeKeyframe(effect,selectedKeyframeParam[effect],currentPosition());
            automationLabel.setText(automationSummary(effect));timelineView.refresh();
        });
        addButton(keyframes,"Hapus Kurva",v->new AlertDialog.Builder(this)
                .setTitle("Hapus seluruh keyframe parameter?")
                .setMessage(ReactiveView.KEYFRAME_NAMES[selectedKeyframeParam[effect]])
                .setNegativeButton("Batal",(dialog,which)->{})
                .setPositiveButton("Hapus",(dialog,which)->{
                    reactive.clearKeyframes(effect,selectedKeyframeParam[effect]);
                    automationLabel.setText(automationSummary(effect));timelineView.refresh();
                }).show());
        advanced.addView(keyframes);
    }
    private String automationSummary(int effect) {
        int param=selectedKeyframeParam[effect];
        return "KEYFRAME • "+ReactiveView.KEYFRAME_NAMES[param]+" ("+
                reactive.keyframeCount(effect,param)+" titik) • Posisi: "+format((int)Math.min(Integer.MAX_VALUE,currentPosition()));
    }
    private void editKeyframe(int effect,TextView summary) {
        final int param=selectedKeyframeParam[effect];
        final long atMs=currentPosition();
        EditText input=new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setText(String.valueOf(reactive.keyframeValueAt(effect,param,atMs)));
        input.selectAll();
        int min=AutomationParameters.min(param),max=AutomationParameters.max(param);
        new AlertDialog.Builder(this)
            .setTitle("Keyframe "+ReactiveView.KEYFRAME_NAMES[param])
            .setMessage("Waktu "+format((int)Math.min(Integer.MAX_VALUE,atMs))+
                    " • nilai "+min+"–"+max+". Tambah titik atau ubah titik pada posisi ini.")
            .setView(input).setNegativeButton("Batal",(d,w)->{})
            .setPositiveButton("Simpan",(d,w)->{
                try {
                    int value=Integer.parseInt(input.getText().toString().trim());
                    if(value<min || value>max) {toast("Nilai harus "+min+"–"+max);return;}
                    reactive.setKeyframe(effect,param,atMs,value);
                    summary.setText(automationSummary(effect));timelineView.refresh();
                } catch(NumberFormatException e) {toast("Masukkan angka yang valid");}
            }).show();
    }
    private String getRangeTitle(int effect) {
        String end=reactive.getLayerEnd(effect)==Long.MAX_VALUE?"akhir lagu":
                format((int)Math.min(Integer.MAX_VALUE,reactive.getLayerEnd(effect)));
        return format((int)Math.min(Integer.MAX_VALUE,reactive.getLayerStart(effect)))+" – "+end;
    }
    private String propertyTitle(int effect,int param) {
        int source=reactive.getPropertySource(effect,param);
        return ReactiveView.PROPERTY_NAMES[param]+" ← "+
                (source<0?"Manual":ReactiveView.SOURCES[source]);
    }
    private long currentPosition() {
        if(player==null) return 0L;
        try {return Math.max(0,player.getCurrentPosition());}
        catch(Exception ignored) {return 0L;}
    }
    private void moveCard(int effect,int delta) {
        if(reactive.moveLayer(effect,delta)){reorderPanels();timelineView.refresh();}
    }
    private void requestLayerImage(int layer) {
        pendingImageLayer=layer;
        choose("image/*",PICK_LAYER_IMAGE);
    }
    private void editTextLayer(int layer) {
        EditText input=new EditText(this);input.setSingleLine(false);input.setMinLines(2);
        input.setText(reactive.getLayerText(layer));
        new AlertDialog.Builder(this).setTitle("Edit isi teks layer")
                .setView(input).setPositiveButton("Simpan",(d,w)->
                        reactive.setLayerText(layer,input.getText().toString()))
                .setNegativeButton("Batal",(d,w)->{}).show();
    }
    private void editLayerColor(int layer) {
        EditText input=new EditText(this);input.setSingleLine(true);
        input.setText(String.format(java.util.Locale.US,"#%06X",reactive.getLayerColor(layer)&0xffffff));
        new AlertDialog.Builder(this).setTitle("Warna teks HEX (#RRGGBB)")
                .setView(input).setPositiveButton("Ubah",(d,w)->{
                    try{reactive.setLayerColor(layer,Color.parseColor(input.getText().toString()));}
                    catch(Exception e){toast("Warna tidak valid");}
                }).setNegativeButton("Batal",(d,w)->{}).show();
    }
    private void chooseLayer() {
        new AlertDialog.Builder(this).setTitle("Tambah layer")
                .setItems(ReactiveView.LAYER_NAMES,(dialog,type)->{
                    int id=reactive.addLayer(type);
                    if(id<0){toast("Batas 48 layer; hapus sebagian layer dulu");return;}
                    reactive.setEnabled(id,true);
                    addEffectPanel(effectContainer,id);reorderPanels();timelineView.setFocusLayer(id);timelineView.refresh();
                    if(type==ReactiveView.IMAGE)requestLayerImage(id);
                }).show();
    }
    private void toast(String message) {Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private void reorderPanels() {
        if(effectContainer==null) return;
        for(LinearLayout card:panels) if(card!=null && card.getParent()==effectContainer)
            effectContainer.removeView(card);
        // Section heading, spectrum toggle, and two global sliders stay above FX cards.
        int index=5;
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
        if(result!=RESULT_OK || data==null || data.getData()==null) {
            if(request==PICK_LAYER_IMAGE)pendingImageLayer=-1;
            return;
        }
        Uri uri=data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch(Exception ignored) { }
        if(request==PICK_AUDIO) loadAudio(uri);
        else if(request==PICK_IMAGE) loadImage(uri);
        else if(request==PICK_LAYER_IMAGE && pendingImageLayer>=0){
            int layer=pendingImageLayer;pendingImageLayer=-1;
            try {
                if(reactive.isAllocated(layer))reactive.setLayerImage(layer,decodeImage(uri,1024),uri.toString());
                status.setText("Gambar layer berhasil dimuat");
            }catch(Exception e){status.setText("Gambar layer gagal: "+e.getMessage());}
        } else if(request==PICK_EXPORT) exportMp4(uri);
    }
    private void loadAudio(Uri uri) {
        musicUri=uri;
        final int version=generation.incrementAndGet();
        if(analysisTask!=null) analysisTask.cancel(true);
        analyzed=false;
        if(player!=null) { player.release();player=null; }
        reactive.setTrack(null);
        timelineView.setTrack(null);
        playButton.setText("▶ Play");
        durationMs=0;
        try {
            MediaPlayer next=new MediaPlayer();
            next.setDataSource(this,uri);
            next.prepare();
            next.setOnCompletionListener(mp->{playButton.setText("▶ Play");reactive.invalidate();});
            next.setOnSeekCompleteListener(mp->reactive.invalidate());
            player=next;
            durationMs=player.getDuration();
            timelineView.setDuration(durationMs);
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
                    timelineView.setTrack(result);
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
    private Bitmap decodeImage(Uri uri,int maxDimension) throws Exception {
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        try(InputStream in=getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in,null,bounds);
        }
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IllegalArgumentException("Gambar tidak valid");
        int sample=1;
        while(bounds.outWidth/sample>maxDimension || bounds.outHeight/sample>maxDimension)sample*=2;
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inSampleSize=sample;
        try(InputStream in=getContentResolver().openInputStream(uri)) {
            Bitmap bitmap=BitmapFactory.decodeStream(in,null,options);
            if(bitmap==null)throw new IllegalArgumentException("Gambar tidak terbaca");
            return bitmap;
        }
    }
    private void loadImage(Uri uri) {
        try {
            reactive.setBackdrop(decodeImage(uri,2048));
            status.setText("Background terpasang. Atur mapping efek sesuai musik.");
        } catch(Exception e) {status.setText("Gambar gagal dibuka: "+e.getMessage());}
    }
    private void restoreLayerImages(){
        for(int id:reactive.getLayerOrder()){
            if(reactive.getLayerType(id)!=ReactiveView.IMAGE)continue;
            String uri=reactive.getImageUri(id);
            if(uri==null||uri.isEmpty())continue;
            try{reactive.setLayerImage(id,decodeImage(Uri.parse(uri),1024),uri);}
            catch(Exception e){status.setText("Sebagian gambar preset tidak tersedia lagi");}
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
        try {
            prefs.edit().putString("projectV2",reactive.exportProjectJson()).apply();
            toast("Preset layer tersimpan offline");
        }catch(Exception e){status.setText("Preset gagal: "+e.getMessage());}
    }
    private void preloadPresetSettings() {
        String saved=prefs.getString("projectV2",null);
        if(saved!=null) {
            if(reactive.importProjectJson(saved))restoreLayerImages();
            else status.setText("Preset lama rusak, memakai scene default");
        }
    }
    private void restorePreset() {
        ratioButton.setText(reactive.isPortrait()?"9:16":"16:9");
        for(int id:reactive.getLayerOrder()) {
            if(toggleButtons[id]!=null)toggleButtons[id].setText(reactive.isEnabled(id)?"ON":"OFF");
            if(mappingButtons[id]!=null)mappingButtons[id].setText("→ "+ReactiveView.SOURCES[reactive.getSource(id)]);
        }
    }
    private void chooseExport() {
        if(musicUri==null || !analyzed) {toast("Impor lagu dan tunggu analisis dulu");return;}
        if(exporting) {toast("Ekspor sedang berjalan");return;}
        String[] sizes={"540p • Hemat baterai", "720p • Seimbang", "1080p • Berat"};
        new AlertDialog.Builder(this).setTitle("Ekspor MP4 • 30 FPS")
                .setSingleChoiceItems(sizes,1,(dialog,which)->{
                    int shortSide=which==0?540:which==1?720:1080;
                    exportWidth=reactive.isPortrait()?shortSide:shortSide*16/9;
                    exportHeight=reactive.isPortrait()?shortSide*16/9:shortSide;
                    dialog.dismiss();
                    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("video/mp4");
                    intent.putExtra(Intent.EXTRA_TITLE,"Ranneki_"+System.currentTimeMillis()+".mp4");
                    startActivityForResult(intent,PICK_EXPORT);
                }).setNegativeButton("Batal",(d,w)->{}).show();
    }
    private void exportMp4(Uri outputUri) {
        if(musicUri==null || !analyzed || exporting)return;
        if(player!=null && player.isPlaying()) {player.pause();playButton.setText("▶ Play");}
        exporting=true;exportCancel.set(false);
        // Snapshot the project on the UI thread. Export never mutates the live preview.
        ReactiveView renderCopy=new ReactiveView(this);
        if(!renderCopy.importProjectJson(reactive.exportProjectJson())){
            exporting=false;status.setText("Gagal membuat salinan scene");return;
        }
        renderCopy.setTrack(reactive.getTrack());
        Bitmap bg=reactive.getBackdrop();
        if(bg!=null && !bg.isRecycled())renderCopy.setBackdrop(bg.copy(Bitmap.Config.ARGB_8888,false));
        reactive.copyImageLayersTo(renderCopy);
        renderCopy.setEconomyMode(false);
        final Uri input=musicUri;
        final int width=exportWidth,height=exportHeight;
        final int length=durationMs;
        status.setText("Ekspor MP4 dimulai — tekan status untuk membatalkan");
        status.setOnClickListener(v->{if(exporting){exportCancel.set(true);status.setText("Membatalkan ekspor...");}});
        exportWorker.execute(()->{
            try {
                Mp4Exporter.export(getApplicationContext(),input,outputUri,renderCopy,
                        width,height,length,exportCancel,(progress,stage)->{
                            runOnUiThread(()->status.setText(stage+" "+progress+"% • ketuk untuk batal"));
                        });
                runOnUiThread(()->{status.setText("MP4 tersimpan: video H.264 + audio AAC");toast("Video berhasil diekspor");});
            }catch(Exception e){
                runOnUiThread(()->status.setText("Ekspor gagal: "+e.getMessage()));
            }finally {
                Bitmap b=renderCopy.getBackdrop();if(b!=null && !b.isRecycled())b.recycle();
                renderCopy.recycleLayerImages();
                exporting=false;
                runOnUiThread(()->status.setOnClickListener(null));
            }
        });
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
        exportCancel.set(true);exportWorker.shutdownNow();
        if(player!=null) {player.release();player=null;}
        super.onDestroy();
    }
}
