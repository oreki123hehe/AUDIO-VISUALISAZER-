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
    private final ExecutorService studioWorker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean studioCancel=new AtomicBoolean();
    private boolean studioBusy;
    private AudioMixProject audioMix=new AudioMixProject();
    private CanvasGizmoView canvasGizmo;
    private ScrollView inspectorScroll;
    private Button canvasDone,timelineToggle;
    private boolean canvasEditing;
    private static final int PICK_MIX=120;
    private void showStudio(){
        String[] items={"Pisahkan audio otomatis + sinkronkan layer","Bagian lagu & Auto Arrange","Kamera perspektif & kedalaman","Multitrack audio / suara video","Edit layer di kanvas","Tutup kontrol kanvas","Uji ketahanan preview 5 menit"};
        new AlertDialog.Builder(this).setTitle("Studio • gerak & audio").setItems(items,(d,w)->{switch(w){case 0:separateAudio();break;case 1:editSections();break;case 2:editCamera();break;case 3:showMixer();break;case 4:selectCanvasLayer();break;case 5:closeCanvas();break;case 6:stressPreview();break;}}).show();
    }
    private void focusCanvas(int id){if(!reactive.isAllocated(id))return;if(player!=null&&player.isPlaying()){player.pause();playButton.setText("▶ Play");}canvasEditing=true;inspectorScroll.setVisibility(View.GONE);timelineView.setVisibility(View.GONE);timelineToggle.setText("▤ Timeline ▸");canvasDone.setVisibility(View.VISIBLE);canvasDone.post(()->canvasDone.requestRectangleOnScreen(new android.graphics.Rect(0,0,canvasDone.getWidth(),canvasDone.getHeight()),true));canvasGizmo.select(id);}
    private void closeCanvas(){canvasEditing=false;canvasGizmo.select(-1);canvasDone.setVisibility(View.GONE);inspectorScroll.setVisibility(timelineView.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);}
    @Override public void onBackPressed(){if(canvasEditing){closeCanvas();return;}super.onBackPressed();}
    private void resizeEditorPanels(){if(inspectorScroll==null)return;int h=(int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density);inspectorScroll.getLayoutParams().height=dp(Math.max(100,Math.min(240,(int)(h*.28f))));timelineView.getLayoutParams().height=dp(Math.max(100,Math.min(240,(int)(h*.30f))));inspectorScroll.requestLayout();timelineView.requestLayout();}
    @Override public void onConfigurationChanged(android.content.res.Configuration c){super.onConfigurationChanged(c);resizeEditorPanels();}
    private void selectCanvasLayer(){int[] ids=reactive.getLayerOrder();String[] names=new String[ids.length];for(int i=0;i<ids.length;i++)names[i]=reactive.getLayerName(ids[i]);new AlertDialog.Builder(this).setTitle("Pilih layer untuk digeser").setItems(names,(d,w)->{if(player!=null&&player.isPlaying()){player.pause();playButton.setText("▶ Play");}focusCanvas(ids[w]);timelineView.setFocusLayer(ids[w]);}).show();}
    private void separateAudio(){
        if(musicUri==null){toast("Impor lagu dulu");return;}if(studioBusy){toast("Pemrosesan audio masih berjalan");return;}
        final Uri source=musicUri;final int audioVersion=generation.get();final int[] stemVersions=new int[4];for(int i=0;i<4;i++)stemVersions[i]=stemGeneration.incrementAndGet(i);
        android.app.ProgressDialog progress=beginStudioJob("Pemisahan audio offline");
        File folder=new File(getFilesDir(),"stems/"+java.util.UUID.randomUUID());
        studioWorker.submit(()->{try{
            File[] files=StemSeparator.separate(getApplicationContext(),source,folder,studioCancel,(n,message)->ui.post(()->{if(!isDestroyed()){progress.setProgress(n);progress.setMessage(message);}}));
            AudioAnalyzer.Track[] tracks=new AudioAnalyzer.Track[4];for(int i=0;i<4;i++){PcmAudio.check(studioCancel);tracks[i]=AudioAnalyzer.analyze(getApplicationContext(),Uri.fromFile(files[i]));}
            ui.post(()->{endStudioJob(progress);if(audioVersion!=generation.get()||isDestroyed())return;for(int i=0;i<4;i++)if(stemVersions[i]!=stemGeneration.get(i))return;
                for(int i=0;i<4;i++)reactive.setStem(i,tracks[i],Uri.fromFile(files[i]).toString());
                reactive.arrangeAll(true);int visual=0;
                for(int id:reactive.getLayerOrder()){int type=reactive.getLayerType(id);AudioRoute r=new AudioRoute();r.source=type==ReactiveView.TEXT?7:type==ReactiveView.VISUALIZER?9:type==ReactiveView.IMAGE?10:8;r.depth=type==ReactiveView.IMAGE?8:16;
                    reactive.setAudioRoute(id,AutomationParameters.SCALE,r);
                    if(type==ReactiveView.VISUALIZER)reactive.setSource(id,(visual++%2==0)?9:8);
                }
                refreshLayerEditor();saveSession();status.setText("4 stem siap • setiap layer mengikuti sumbernya • Auto Arrange aktif");
            });
        }catch(Exception e){ui.post(()->{endStudioJob(progress);status.setText(studioCancel.get()?"Pemisahan dibatalkan":"Pemisahan gagal: "+e.getMessage());});}});
    }
    private android.app.ProgressDialog beginStudioJob(String title){studioBusy=true;studioCancel.set(false);if(player!=null&&player.isPlaying()){player.pause();playButton.setText("▶ Play");}
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        android.app.ProgressDialog p=new android.app.ProgressDialog(this);p.setTitle(title);p.setMessage("Menyiapkan…");p.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);p.setMax(100);p.setCancelable(false);p.setButton(android.app.ProgressDialog.BUTTON_NEGATIVE,"Batalkan",(d,w)->studioCancel.set(true));p.show();return p;}
    private void endStudioJob(android.app.ProgressDialog p){studioBusy=false;if(!isDestroyed()){p.dismiss();getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}}
    private void editSections(){
        SongSections s=reactive.getSections();if(s.sections.isEmpty()&&reactive.getTrack()!=null){s=reactive.getTrack().sections();reactive.setSections(s);}
        String[] names=new String[s.sections.size()];for(int i=0;i<names.length;i++){SongSections.Section section=s.sections.get(i);names[i]=format((int)section.start)+"–"+format((int)section.end)+"  "+SongSections.NAMES[section.type];}
        final SongSections current=s;
        new AlertDialog.Builder(this).setTitle("Bagian lagu • hasil perkiraan").setItems(names,(d,index)->{
            SongSections.Section section=current.sections.get(index);LinearLayout panel=dialogPanel();EditText start=numberField(panel,"Mulai (detik)",section.start/1000f),end=numberField(panel,"Selesai (detik)",section.end/1000f);
            android.widget.Spinner type=new android.widget.Spinner(this);type.setAdapter(new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,SongSections.NAMES));type.setSelection(section.type);panel.addView(type);
            new AlertDialog.Builder(this).setTitle("Koreksi bagian lagu").setView(panel).setPositiveButton("Simpan",(x,w)->{try{section.start=(long)(Float.parseFloat(start.getText().toString())*1000);section.end=(long)(Float.parseFloat(end.getText().toString())*1000);section.type=type.getSelectedItemPosition();reactive.setSections(SongSections.decode(current.encode()));}catch(Exception e){toast("Rentang tidak valid atau bertumpuk");}}).setNegativeButton("Batal",null).show();
        }).setPositiveButton("Aktifkan Auto Arrange",(d,w)->{reactive.arrangeAll(true);status.setText("Intensitas, opacity dan gerak mengikuti bagian lagu");}).setNeutralButton("Nonaktifkan",(d,w)->reactive.arrangeAll(false)).setNegativeButton("Tutup",null).show();
    }
    private EditText numberField(LinearLayout panel,String title,float value){panel.addView(text(title,12,0xffbdc6de,false));EditText f=new EditText(this);f.setSingleLine(true);f.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);f.setText(String.format(java.util.Locale.US,"%.3f",value));panel.addView(f);return f;}
    private float number(EditText field){float f=Float.parseFloat(field.getText().toString());if(!Float.isFinite(f))throw new IllegalArgumentException("Nilai harus angka");return f;}
    private void editCamera(){CameraRig c=reactive.getCamera();LinearLayout panel=dialogPanel();
        addSlider(panel,"Kamera X",Math.round(c.x/4+50),v->{c.x=(v-50)*4;reactive.setCamera(c);});
        addSlider(panel,"Kamera Y",Math.round(c.y/6+50),v->{c.y=(v-50)*6;reactive.setCamera(c);});
        addSlider(panel,"Dolly Z",Math.round(c.z/4+50),v->{c.z=(v-50)*4;reactive.setCamera(c);});
        addSlider(panel,"Yaw",Math.round(c.yaw+50),v->{c.yaw=v-50;reactive.setCamera(c);});
        addSlider(panel,"Pitch",Math.round(c.pitch+50),v->{c.pitch=v-50;reactive.setCamera(c);});
        addSlider(panel,"Roll",Math.round(c.roll+50),v->{c.roll=v-50;reactive.setCamera(c);});
        addSlider(panel,"Perspektif",Math.round((c.focal-200)/10),v->{c.focal=200+v*10;reactive.setCamera(c);});
        addButton(panel,"Gerak kamera otomatis",v->new AlertDialog.Builder(this).setTitle("Gerak kamera").setItems(new String[]{"Diam","Orbit","Dolly","Audio punch"},(d,w)->{c.motion=w;reactive.setCamera(c);}).show());
        addButton(panel,"Sumber audio kamera",v->new AlertDialog.Builder(this).setTitle("Sumber kamera").setItems(ReactiveView.SOURCES,(d,w)->{c.source=w;reactive.setCamera(c);}).show());
        addSlider(panel,"Gerak kamera",c.amount,v->{c.amount=v;reactive.setCamera(c);});addSlider(panel,"Laju kamera",c.speed,v->{c.speed=v;reactive.setCamera(c);});
        ScrollView scroll=new ScrollView(this);scroll.addView(panel);new AlertDialog.Builder(this).setTitle("Kamera 3D • layer gambar datar").setView(scroll).setPositiveButton("Selesai",null).setNeutralButton("Reset",(d,w)->reactive.setCamera(new CameraRig())).show();}
    private void changeAdvanced(int id,java.util.function.Consumer<LayerAdvanced> edit){LayerAdvanced current=reactive.getAdvanced(id);edit.accept(current);reactive.setAdvanced(id,current);}
    private void editLayerStudio(int id){final AlertDialog[] popup=new AlertDialog[1];LayerAdvanced a=reactive.getAdvanced(id);LinearLayout panel=dialogPanel();
        addButton(panel,"Geser / putar / ukuran di kanvas",v->{popup[0].dismiss();focusCanvas(id);});
        addSlider(panel,"Pivot X",Math.round(a.pivotX/4+50),v->{changeAdvanced(id,current->current.pivotX=(v-50)*4);});
        addSlider(panel,"Pivot Y",Math.round(a.pivotY/7.1f+50),v->{changeAdvanced(id,current->current.pivotY=(v-50)*7.1f);});
        addSlider(panel,"Depth",Math.round((a.depth+400)/16),v->{changeAdvanced(id,current->current.depth=v*16-400);});
        addButton(panel,"Gambar jalur gerak",v->drawPath(id,0));
        addButton(panel,"Pengulangan jalur",v->new AlertDialog.Builder(this).setTitle("Jalur gerak").setItems(new String[]{"Loop","Bolak-balik","Sekali"},(d,w)->changeAdvanced(id,current->current.pathMode=w)).show());
        addButton(panel,"Easing jalur",v->new AlertDialog.Builder(this).setTitle("Kurva gerak").setItems(MotionCurve.NAMES,(d,w)->changeAdvanced(id,current->current.pathCurve=new MotionCurve(w))).show());
        addSlider(panel,"Durasi jalur",Math.min(100,Math.max(0,(a.pathDuration-100)/200)),v->{changeAdvanced(id,current->current.pathDuration=100+v*200);});
        android.widget.CheckBox follow=new android.widget.CheckBox(this);follow.setText("Rotasi mengikuti arah jalur");follow.setChecked(a.followPath);follow.setOnCheckedChangeListener((b,on)->{changeAdvanced(id,current->current.followPath=on);});panel.addView(follow);
        if(reactive.getLayerType(id)==ReactiveView.IMAGE){android.widget.CheckBox fill=new android.widget.CheckBox(this);fill.setText("Background penuh • crop mengikuti kanvas");fill.setChecked(a.fillCanvas);fill.setOnCheckedChangeListener((b,on)->{changeAdvanced(id,current->current.fillCanvas=on);});panel.addView(fill);}
        if(reactive.getLayerType(id)==ReactiveView.VISUALIZER){addButton(panel,"Gambar bentuk visualizer A",v->drawPath(id,1));addButton(panel,"Gambar bentuk morph B",v->drawPath(id,2));addSlider(panel,"Morph A → B",Math.round(a.morph*100),v->{changeAdvanced(id,current->current.morph=v/100f);});addButton(panel,"Audio untuk morph",v->new AlertDialog.Builder(this).setTitle("Morph mengikuti sumber audio").setItems(ReactiveView.SOURCES,(d,w)->{changeAdvanced(id,current->current.morphSource=w);}).setNeutralButton("Manual",(d,w)->{changeAdvanced(id,current->current.morphSource=-1);}).show());}
        addButton(panel,"Gambar mask",v->drawPath(id,3));addSlider(panel,"Feather mask",Math.round(a.feather*2.5f),v->{changeAdvanced(id,current->current.feather=v*.4f);});
        addButton(panel,"Trim / speed / transisi klip",v->editClip(id));
        addButton(panel,"Split pada playhead",v->{int next=reactive.splitLayer(id,currentPosition());if(next<0)toast("Playhead harus berada di dalam klip dan tersedia slot layer");else refreshLayerEditor();});
        addButton(panel,"Join dengan klip berikutnya",v->{if(!reactive.joinLayer(id))toast("Klip hasil split harus berurutan dengan media, gerak, efek dan pengaturan yang sama");else refreshLayerEditor();});
        ScrollView scroll=new ScrollView(this);scroll.addView(panel);popup[0]=new AlertDialog.Builder(this).setTitle(reactive.getLayerName(id)).setView(scroll).setPositiveButton("Selesai",null).show();}
    private void drawPath(int id,int kind){LayerAdvanced a=reactive.getAdvanced(id);PathEditorView drawing=new PathEditorView(this,kind==0?a.motionPath:kind==1?a.spectrumPath:kind==2?a.morphPath:null);LinearLayout panel=dialogPanel();panel.addView(drawing,new LinearLayout.LayoutParams(-1,dp(300)));
        new AlertDialog.Builder(this).setTitle(kind==3?"Gambar mask tertutup":"Gambar jalur dari awal ke akhir").setView(panel).setPositiveButton("Pakai",(d,w)->{try{DrawnPath path=drawing.value();LayerAdvanced current=reactive.getAdvanced(id);
            if(kind==0)current.motionPath=path;else if(kind==1){current.spectrumPath=path;reactive.setVisualizerStyle(id,27);}else if(kind==2){current.morphPath=path;reactive.setVisualizerStyle(id,28);}else{float[] points=path.resample(24);StringBuilder b=new StringBuilder();for(int i=0;i<points.length;i+=2){if(i>0)b.append(';');b.append((points[i]+1)/2).append(',').append((points[i+1]+1)/2);}reactive.setPolygonMask(id,b.toString());reactive.setMask(id,4);}reactive.setAdvanced(id,current);refreshLayerEditor();
        }catch(Exception e){toast(e.getMessage());}}).setNeutralButton("Hapus jalur",(d,w)->{LayerAdvanced current=reactive.getAdvanced(id);if(kind==0)current.motionPath=null;else if(kind==1)current.spectrumPath=null;else if(kind==2)current.morphPath=null;else reactive.setMask(id,0);reactive.setAdvanced(id,current);}).setNegativeButton("Batal",null).show();}
    private void editClip(int id){LayerAdvanced a=reactive.getAdvanced(id);LinearLayout panel=dialogPanel();EditText in=numberField(panel,"In media (detik)",a.clip.inMs/1000f),out=numberField(panel,"Out media (detik), 0 = seluruh media",a.clip.outMs==Long.MAX_VALUE?0:a.clip.outMs/1000f),speed=numberField(panel,"Kecepatan 0.25–4×",a.clip.speed),fadeIn=numberField(panel,"Fade masuk (detik)",a.clip.fadeIn/1000f),fadeOut=numberField(panel,"Fade keluar (detik)",a.clip.fadeOut/1000f);
        android.widget.CheckBox loop=new android.widget.CheckBox(this);loop.setText("Ulangi klip");loop.setChecked(a.clip.loop);panel.addView(loop);ScrollView scroll=new ScrollView(this);scroll.addView(panel);
        new AlertDialog.Builder(this).setTitle("Klip • speed mengubah waktu sumber").setView(scroll).setPositiveButton("Simpan",(d,w)->{try{a.clip.inMs=(long)(number(in)*1000);a.clip.outMs=number(out)==0?Long.MAX_VALUE:(long)(number(out)*1000);a.clip.speed=number(speed);a.clip.fadeIn=(long)(number(fadeIn)*1000);a.clip.fadeOut=(long)(number(fadeOut)*1000);a.clip.loop=loop.isChecked();a.clip=ClipTiming.decode(a.clip.encode());reactive.setAdvanced(id,a);if(reactive.getLayerEnd(id)==Long.MAX_VALUE&&durationMs>0)reactive.setLayerEnd(id,durationMs);toast("Klip diperbarui. Tumpuk dua klip untuk crossfade.");}catch(Exception e){toast("Nilai klip tidak valid");}}).setNegativeButton("Batal",null).show();}
    private void addMixSource(Uri uri){try{if(audioMix.clips.size()>=AudioMixProject.MAX)throw new IllegalArgumentException("Maksimum 16 clip");android.media.MediaMetadataRetriever metadata=new android.media.MediaMetadataRetriever();long duration;try{metadata.setDataSource(this,uri);duration=Long.parseLong(metadata.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION));}finally{metadata.release();}AudioMixProject.Clip c=new AudioMixProject.Clip();c.uri=uri.toString();c.name="Audio "+(audioMix.clips.size()+1);c.timing.outMs=duration;c.timing.loop=false;audioMix.clips.add(c);queueSave();showMixer();}catch(Exception e){toast("Audio clip gagal: "+e.getMessage());}}
    private void showMixer(){if(audioMix.clips.isEmpty()&&musicUri!=null&&durationMs>0){AudioMixProject.Clip c=new AudioMixProject.Clip();c.uri=musicUri.toString();c.name="Lagu utama";c.timing.outMs=durationMs;c.timing.loop=false;audioMix.clips.add(c);}
        LinearLayout panel=dialogPanel();panel.addView(text("16 clip audio / suara video. Tumpuk waktu untuk mixing dan crossfade. Speed juga mengubah pitch. Terapkan mix untuk mendengar hasilnya.",12,0xffbdc6de,false));
        AudioMixTimelineView lanes=new AudioMixTimelineView(this,audioMix,this::queueSave);panel.addView(lanes,new LinearLayout.LayoutParams(-1,dp(Math.min(300,60+audioMix.clips.size()*34))));
        for(int i=0;i<audioMix.clips.size();i++){final int index=i;AudioMixProject.Clip c=audioMix.clips.get(i);addButton(panel,(i+1)+"  "+c.name+(c.mute?" • mute":"")+"  "+format((int)c.start)+"–"+format((int)(c.start+c.duration())),v->editMixClip(audioMix.clips.indexOf(c)));}
        addButton(panel,"＋ Audio / video",v->choose("*/*",PICK_MIX));ScrollView scroll=new ScrollView(this);scroll.addView(panel);new AlertDialog.Builder(this).setTitle("Multitrack audio").setView(scroll).setPositiveButton("Terapkan mix",(d,w)->renderMix()).setNegativeButton("Tutup",null).show();}
    private void editMixClip(int index){if(index<0||index>=audioMix.clips.size())return;final AlertDialog[] editor=new AlertDialog[1];AudioMixProject.Clip original=audioMix.clips.get(index),c=original.copy();LinearLayout panel=dialogPanel();EditText start=numberField(panel,"Mulai di timeline (detik)",c.start/1000f),in=numberField(panel,"In sumber (detik)",c.timing.inMs/1000f),out=numberField(panel,"Out sumber (detik)",c.timing.outMs/1000f),speed=numberField(panel,"Speed 0.25–4×",c.timing.speed),gain=numberField(panel,"Volume 0–4×",c.gain),pan=numberField(panel,"Pan −1 kiri, +1 kanan",c.pan),fi=numberField(panel,"Fade masuk (detik)",c.timing.fadeIn/1000f),fo=numberField(panel,"Fade keluar (detik)",c.timing.fadeOut/1000f);
        android.widget.CheckBox mute=new android.widget.CheckBox(this);mute.setText("Mute");mute.setChecked(c.mute);panel.addView(mute);addButton(panel,"Split di playhead",v->{if(!audioMix.split(audioMix.clips.indexOf(original),currentPosition()))toast("Playhead di luar clip / slot penuh");else{editor[0].dismiss();queueSave();showMixer();}});addButton(panel,"Join dengan clip berikutnya",v->{if(!audioMix.join(audioMix.clips.indexOf(original)))toast("Sumber, volume, pan dan speed harus sama dan rentang harus bersebelahan");else{editor[0].dismiss();queueSave();showMixer();}});ScrollView scroll=new ScrollView(this);scroll.addView(panel);
        editor[0]=new AlertDialog.Builder(this).setTitle(c.name).setView(scroll).setPositiveButton("Simpan",(d,w)->{try{c.start=(long)(number(start)*1000);c.timing.inMs=(long)(number(in)*1000);c.timing.outMs=(long)(number(out)*1000);c.timing.speed=number(speed);c.gain=number(gain);c.pan=number(pan);c.timing.fadeIn=(long)(number(fi)*1000);c.timing.fadeOut=(long)(number(fo)*1000);c.mute=mute.isChecked();AudioMixProject.Clip.from(c.json());if(audioMix.clips.contains(original)){audioMix.clips.set(audioMix.clips.indexOf(original),c);queueSave();}}catch(Exception e){toast("Nilai audio clip tidak valid");}}).setNeutralButton("Hapus",(d,w)->{audioMix.clips.remove(original);queueSave();}).setNegativeButton("Batal",null).show();}
    private void renderMix(){if(studioBusy){toast("Pemrosesan masih berjalan");return;}final AudioMixProject snapshot;try{snapshot=AudioMixProject.from(audioMix.json());}catch(Exception e){toast(e.getMessage());return;}final int audioVersion=generation.get();android.app.ProgressDialog progress=beginStudioJob("Menerapkan multitrack audio");File folder=new File(getFilesDir(),"mixes/"+java.util.UUID.randomUUID());
        studioWorker.submit(()->{try{File result=AudioMixer.render(getApplicationContext(),snapshot,folder,studioCancel,(n,m)->ui.post(()->{if(!isDestroyed()){progress.setProgress(n);progress.setMessage(m);}}));ui.post(()->{endStudioJob(progress);if(isDestroyed()||audioVersion!=generation.get())return;for(int i=0;i<4;i++){stemGeneration.incrementAndGet(i);reactive.setStem(i,null,"");}reactive.setSections(new SongSections());restorePosition=0;loadAudio(Uri.fromFile(result));saveSession();});}catch(Exception e){ui.post(()->{endStudioJob(progress);status.setText(studioCancel.get()?"Mix dibatalkan":"Mix gagal: "+e.getMessage());});}});}
    private boolean stressRunning;private long stressStarted;private int stressThermal;
    private final Runnable stressTick=new Runnable(){public void run(){if(!stressRunning||isDestroyed())return;int thermal=Build.VERSION.SDK_INT>=29?((android.os.PowerManager)getSystemService(POWER_SERVICE)).getCurrentThermalStatus():-1;stressThermal=Math.max(stressThermal,thermal);long elapsed=android.os.SystemClock.elapsedRealtime()-stressStarted;
        status.setText("Uji preview "+elapsed/1000+" / 300 detik • thermal "+thermal);
        if(elapsed>=300000){stressRunning=false;if(player!=null)player.setLooping(false);getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);lastExportReport="PREVIEW SUSTAINED\nPerangkat: "+Build.MANUFACTURER+" "+Build.MODEL+"\nDurasi: "+elapsed/1000+" detik\nThermal maksimum: "+stressThermal+"\n"+reactive.previewReport()+"\n"+gpuPreview.report();showPerformance();}else ui.postDelayed(this,1000);}};
    private void stressPreview(){if(stressRunning){stressRunning=false;if(player!=null)player.setLooping(false);ui.removeCallbacks(stressTick);getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);toast("Uji preview dihentikan");return;}if(player==null||!analyzed){toast("Impor lagu dan tunggu analisis selesai");return;}reactive.resetPreviewStats();gpuPreview.resetMetrics();stressThermal=0;stressStarted=android.os.SystemClock.elapsedRealtime();stressRunning=true;getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);player.setLooping(true);player.start();playButton.setText("Ⅱ Pause");ui.post(stressTick);}

    private static final int PICK_AUDIO=101, PICK_IMAGE=102, PICK_EXPORT=103,
            PICK_LAYER_IMAGE=104, PICK_PRESET_OPEN=105, PICK_PRESET_SAVE=106, PICK_LAYER_VIDEO=107, PICK_BUNDLE_SAVE=108, PICK_BUNDLE_OPEN=109, PICK_STEM=110;
    private static final int BG=0xff0a0e1d, PANEL=0xff141a30, PURPLE=0xffa999ff;
    private final ExecutorService analysisWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService exportWorker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean exportCancel=new AtomicBoolean(false);
    private volatile boolean exporting=false;
    private Uri musicUri, backgroundUri;
    private int pendingImageLayer=-1, pendingVideoLayer=-1;
    private int exportWidth=720,exportHeight=1280;
    private int exportFps=30,exportRasterPercent=75;
    private SeekBar chromaticSlider,vignetteSlider;
    private String lastExportReport="Belum ada hasil render.\nJalankan ekspor untuk mengukur performa perangkat asli.";
    private final AtomicInteger generation=new AtomicInteger();
    private Future<?> analysisTask;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Button[] toggleButtons=new Button[ReactiveView.MAX_LAYERS],
            mappingButtons=new Button[ReactiveView.MAX_LAYERS];
    private final LinearLayout[] panels=new LinearLayout[ReactiveView.MAX_LAYERS];
    private final int[] selectedKeyframeParam=new int[ReactiveView.MAX_LAYERS];
    private LinearLayout effectContainer;
    private Button addLayerButton;
    private ReactiveView reactive;
    private GpuPreviewView gpuPreview;
    private MediaPlayer player;
    private Button playButton, ratioButton;
    private TextView status, timeline;
    private TimelineView timelineView;
    private SeekBar positionBar;
    private int durationMs=0;
    private volatile boolean analyzed=false;
    private SharedPreferences prefs;
    private final SceneHistory history=new SceneHistory();
    private boolean restoringSession;
    private int restorePosition;
    private final java.util.concurrent.atomic.AtomicIntegerArray stemGeneration=new java.util.concurrent.atomic.AtomicIntegerArray(4);
    private final Runnable pendingSave=()->saveSession();
    private void queueSave(){if(!restoringSession){ui.removeCallbacks(pendingSave);ui.postDelayed(pendingSave,450);}}
    private String sessionJson(){
        try{org.json.JSONObject root=new org.json.JSONObject(reactive.exportProjectJson());
            root.put("audioClips",audioMix.json());root.put("sessionMusic",musicUri==null?"":musicUri.toString());
            root.put("sessionBackground",backgroundUri==null?"":backgroundUri.toString());return root.toString();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private void saveSession(){
        if(restoringSession||reactive==null||prefs==null)return;
        try{String state=sessionJson();history.record(state);
            prefs.edit().putString("projectV2",state).putInt("playhead",(int)currentPosition()).apply();
        }catch(Exception e){if(status!=null)status.setText("Sesi belum tersimpan: "+e.getMessage());}
    }
    private void restoreMedia(String json){
        try{org.json.JSONObject state=new org.json.JSONObject(json);
            audioMix=AudioMixProject.from(state.optJSONArray("audioClips"));
            String audio=state.optString("sessionMusic",""),background=state.optString("sessionBackground","");
            if(!background.isEmpty()&&(backgroundUri==null||!background.equals(backgroundUri.toString())))loadImage(Uri.parse(background));
            else if(background.isEmpty()){backgroundUri=null;reactive.setBackdrop(null);}
            if(!audio.isEmpty()&&(musicUri==null||!audio.equals(musicUri.toString())))loadAudio(Uri.parse(audio));
            else if(audio.isEmpty()){musicUri=null;analyzed=false;if(player!=null){player.release();player=null;}reactive.setTrack(null);}
            for(int i=0;i<4;i++)if(!reactive.getStemUri(i).isEmpty()&&!reactive.hasStem(i))loadStem(i,Uri.parse(reactive.getStemUri(i)));
        }catch(Exception e){status.setText("Sebagian media sesi perlu diimpor ulang");}
    }
    private void historyStep(boolean redo){
        if(exporting){toast("Tunggu ekspor selesai");return;}
        saveSession();String state=redo?history.redo():history.undo();
        if(state==null){toast(redo?"Tidak ada redo":"Tidak ada undo");return;}
        restoringSession=true;
        try{if(!reactive.importProjectJson(state))throw new IllegalStateException("Sesi tidak valid");
            restoreLayerImages();restoreMedia(state);refreshLayerEditor();
            prefs.edit().putString("projectV2",state).apply();
        }catch(Exception e){status.setText("Pemulihan gagal: "+e.getMessage());}
        finally{restoringSession=false;}
    }
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
        reactive.setEditListener(this::queueSave);
        String saved=prefs.getString("projectV2",null);
        if(saved!=null){restoringSession=true;restorePosition=prefs.getInt("playhead",0);restoreMedia(saved);restoringSession=false;}
        else StudioTemplates.apply(reactive,0);
        refreshLayerEditor();history.record(sessionJson());
        ui.post(timeUpdater);
    }
    private void makeUi() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(10),dp(8),dp(10),dp(7));
        setContentView(root);

        TextView title=text("RANNEKI  /  REACTIVE STUDIO",17,PURPLE,true);
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
        addButton(commandsRow,"✦ Studio+",v->showStudio());
        canvasDone=addButton(commandsRow,"↩ Editor",v->closeCanvas());canvasDone.setVisibility(View.GONE);
        addButton(commandsRow,"✦ Template",v->chooseTemplate());
        addButton(commandsRow,"＋ Visualizer",v->addVisualizer());
        addButton(commandsRow,"◉ Logo",v->chooseLogoTarget());
        addButton(commandsRow,"↶",v->historyStep(false));
        addButton(commandsRow,"↷",v->historyStep(true));
        addButton(commandsRow,"● Export MP4",v->chooseExport());
        addButton(commandsRow,"♪ Audio Terpisah",v->chooseStem());
        addButton(commandsRow,"⋯ Proyek & Alat",v->showTools());
        root.addView(commands,new LinearLayout.LayoutParams(-1,dp(48)));

        reactive=new ReactiveView(this);
        reactive.setClock(clock);
        FrameLayout viewport=new FrameLayout(this);
        viewport.setBackgroundColor(0xff040612);
        viewport.addView(reactive,new FrameLayout.LayoutParams(-1,-1));
        gpuPreview=new GpuPreviewView(this,reactive);
        viewport.addView(gpuPreview,new FrameLayout.LayoutParams(-1,-1));
        canvasGizmo=new CanvasGizmoView(this,reactive,clock);viewport.addView(canvasGizmo,new FrameLayout.LayoutParams(-1,-1));
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
        timelineView.setParameterProvider(id->selectedKeyframeParam[id]);
        timelineView.setOnEditListener(()->{
            // Only write once when the gesture finishes, never for each drag frame.
            queueSave();
        });
        timelineView.setSeekListener(ms->{
            if(player!=null){
                try{player.seekTo((int)Math.min(Integer.MAX_VALUE,ms));reactive.invalidate();}
                catch(Exception ignored) { }
            }
        });
        LinearLayout timelineHeader=row();
        timelineView.setVisibility(View.GONE);
        timelineToggle=addButton(timelineHeader,"▤ Timeline ▸",v->{});
        timelineToggle.setOnClickListener(v->{
            if(canvasEditing)closeCanvas();
            boolean show=timelineView.getVisibility()!=View.VISIBLE;
            timelineView.setVisibility(show?View.VISIBLE:View.GONE);inspectorScroll.setVisibility(show?View.GONE:View.VISIBLE);
            timelineToggle.setText(show?"▤ Timeline ▾":"▤ Timeline ▸");
        });
        addButton(timelineHeader,"−",v->timelineView.setZoom(timelineView.getZoom()/2));
        addButton(timelineHeader,"＋",v->timelineView.setZoom(timelineView.getZoom()*2));
        addButton(timelineHeader,"◀",v->timelineView.scrollWindow(-1));
        addButton(timelineHeader,"▶",v->timelineView.scrollWindow(1));
        addButton(timelineHeader,"↑",v->timelineView.scrollLanes(-1));
        addButton(timelineHeader,"↓",v->timelineView.scrollLanes(1));
        Button beatSnap=addButton(timelineHeader,"SNAP OFF",v->{});
        beatSnap.setOnClickListener(v->{
            timelineView.setSnapEnabled(!timelineView.isSnapEnabled());
            beatSnap.setText(timelineView.isSnapEnabled()?"SNAP ON":"SNAP OFF");
        });
        android.widget.HorizontalScrollView timelineScroll=new android.widget.HorizontalScrollView(this);
        timelineScroll.setHorizontalScrollBarEnabled(false);
        timelineScroll.addView(timelineHeader);
        root.addView(timelineScroll,new LinearLayout.LayoutParams(-1,dp(37)));
        root.addView(timelineView,new LinearLayout.LayoutParams(-1,dp(240)));

        status=text("Impor lagu untuk memulai analisis frekuensi dan kick.",11,0xffb6c0db,false);
        status.setPadding(dp(4),dp(2),0,dp(2));
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(34)));
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout controls=new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(3),dp(5),dp(3),dp(24));
        scroll.addView(controls);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(180)));inspectorScroll=scroll;resizeEditorPanels();
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
        addSlider(controls,"Camera Shake",reactive.getCameraShake(),value->reactive.setCameraShake(value));
        chromaticSlider=addSlider(controls,"RGB Glitch",reactive.getChromatic(),value->reactive.setPostFx(value,reactive.getVignette()));
        vignetteSlider=addSlider(controls,"Vignette",reactive.getVignette(),value->reactive.setPostFx(reactive.getChromatic(),value));
        addLayerButton=addButton(controls,"＋ Tambah Layer FX",v->chooseLayer());
        for(int id:reactive.getLayerOrder()) addEffectPanel(controls,id);
        reorderPanels();
        TextView help=text("Pilih Template, impor Lagu dan Logo. Tambahkan visualizer sebanyak yang diperlukan. Audio → Parameter mengatur gerakan setiap elemen secara terpisah. Studio+ memisahkan stem otomatis, mengatur bagian lagu, kamera, dan multitrack audio.",11,0xff9ba7c5,false);
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
        addButton(header,"◎",v->{focusCanvas(effect);timelineView.setFocusLayer(effect);timelineView.refresh();});
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
        if(reactive.getLayerType(effect)==ReactiveView.VISUALIZER){
            LinearLayout quick=row();
            addButton(quick,"Bentuk: "+VisualizerMesh.STYLES[reactive.getVisualizerStyle(effect)],v->chooseVisualizerStyle(effect));
            addButton(quick,"◉ Logo",v->requestLayerImage(effect));card.addView(quick);
            addButton(card,"♫ Audio → Parameter",v->chooseAudioParameter(effect));
        }
        addButton(card,"✎ Kanvas • Path • Pivot • Klip",v->editLayerStudio(effect));
        addButton(card,"✦ Gerak & Motion Blur",v->editMotion(effect));
        addButton(card,"◈ Efek berurutan",v->editFilterStack(effect));
        addSlider(card,"Ukuran",reactive.getSize(effect),value->reactive.setSize(effect,value));
        addSlider(card,"Jumlah",reactive.getDensity(effect),value->reactive.setDensity(effect,value));
        addSlider(card,"Reaktivitas",reactive.getStrength(effect),value->reactive.setStrength(effect,value));
        LinearLayout advanced=new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);
        Button reveal=addButton(card,"⚙ Lanjutan +",v->{});
        reveal.setOnClickListener(v->{
            boolean show=advanced.getVisibility()!=View.VISIBLE;
            if(show&&advanced.getChildCount()==0)populateAdvanced(advanced,effect);
            advanced.setVisibility(show?View.VISIBLE:View.GONE);
            reveal.setText(show?"⚙ Lanjutan −":"⚙ Lanjutan +");
        });
        card.addView(advanced);
    }
    private void populateAdvanced(LinearLayout advanced,final int effect){
        addButton(advanced,"♫ Audio → Parameter (independen)",v->chooseAudioParameter(effect));
        if(reactive.getLayerType(effect)==ReactiveView.VISUALIZER){
            addButton(advanced,"Warna utama",v->editLayerColor(effect));
            addButton(advanced,"Warna aksen",v->editAccent(effect));
            addSlider(advanced,"Glow",reactive.getVisualizerGlow(effect),value->reactive.setVisualizerGlow(effect,value));
            addSlider(advanced,"Ukuran Logo",reactive.getLogoSize(effect),value->reactive.setLogoSize(effect,value));
            String[] shapes={"Otomatis","Lingkaran","Sudut bulat","Persegi","Segitiga","Hexagon","Diamond","Hati","Bintang"};
            Button shape=addButton(advanced,"Crop Logo: "+shapes[reactive.getLogoShape(effect)],v->{});
            shape.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Crop logo • mengikuti visualizer")
                    .setItems(shapes,(d,index)->{reactive.setLogoShape(effect,index);shape.setText("Crop Logo: "+shapes[index]);}).show());
            addButton(advanced,"Hapus logo",v->reactive.setLayerImage(effect,null,""));
        }
        if(reactive.getLayerType(effect)==ReactiveView.TEXT){
            addButton(advanced,"✎ Edit Teks",v->editTextLayer(effect));
            addButton(advanced,"◉ Warna Teks",v->editLayerColor(effect));
        } else if(reactive.getLayerType(effect)==ReactiveView.IMAGE) {
            addButton(advanced,"▣ Ganti Gambar",v->requestLayerImage(effect));
        } else if(reactive.getLayerType(effect)==ReactiveView.VIDEO) {
            addButton(advanced,"▣ Ganti Video • audio dari lagu utama",v->requestLayerVideo(effect));
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
        addButton(advanced,"✎ Edit Polygon Mask (x,y; x,y)",v->editPolygon(effect));
        addSlider(advanced,"Mask Radius",reactive.getMaskRadius(effect),value->reactive.setMaskRadius(effect,value));
        addSlider(advanced,"Arah (0=auto)",reactive.getDirection(effect),value->reactive.setDirection(effect,value));
        TextView automationLabel=text(automationSummary(effect),11,PURPLE,false);
        advanced.addView(automationLabel);
        Button selectParam=addButton(advanced,"↔ Parameter: "+ReactiveView.KEYFRAME_NAMES[selectedKeyframeParam[effect]],v->{});
        selectParam.setOnClickListener(v->{
            selectedKeyframeParam[effect]=(selectedKeyframeParam[effect]+1)%ReactiveView.KEYFRAME_NAMES.length;
            selectParam.setText("↔ Parameter: "+ReactiveView.KEYFRAME_NAMES[selectedKeyframeParam[effect]]);
            automationLabel.setText(automationSummary(effect));
            timelineView.setFocusLayer(effect);
            timelineView.refresh();
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
        addButton(advanced,"⌁ Kurva Easing / Bezier",v->editCurve(effect));
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
    private void requestLayerVideo(int layer) {
        pendingVideoLayer=layer;
        choose("video/*",PICK_LAYER_VIDEO);
    }
    private void editPolygon(int layer) {
        EditText input=new EditText(this);
        input.setMinLines(3);input.setSingleLine(false);
        input.setText(reactive.getPolygonMask(layer));
        new AlertDialog.Builder(this)
            .setTitle("Polygon mask • titik 0–1")
            .setMessage("Minimal 3 titik. Contoh: 0,0;1,0;1,1;0,1. Pilih 'Polygon custom' pada Mask.")
            .setView(input)
            .setNegativeButton("Batal",(d,w)->{})
            .setPositiveButton("Simpan",(d,w)->{
                try{reactive.setPolygonMask(layer,input.getText().toString());}
                catch(IllegalArgumentException e){toast("Polygon tidak valid: "+e.getMessage());}
            }).show();
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
        new AlertDialog.Builder(this).setTitle("Warna utama (#RRGGBB)")
                .setView(input).setPositiveButton("Ubah",(d,w)->{
                    try{reactive.setLayerColor(layer,Color.parseColor(input.getText().toString()));}
                    catch(Exception e){toast("Warna tidak valid");}
                }).setNegativeButton("Batal",(d,w)->{}).show();
    }
    private void chooseTemplate(){
        new AlertDialog.Builder(this).setTitle("Studio otomatis")
            .setItems(StudioTemplates.NAMES,(d,index)->{
                saveSession();StudioTemplates.apply(reactive,index);refreshLayerEditor();saveSession();
                status.setText(StudioTemplates.NAMES[index]+" siap • logo dipertahankan • Undo tersedia");
            }).show();
    }
    private void addVisualizer(){
        int id=reactive.addLayer(ReactiveView.VISUALIZER);
        if(id<0){toast("Maksimum 48 layer");return;}
        addEffectPanel(effectContainer,id);reorderPanels();timelineView.refresh();chooseVisualizerStyle(id);
    }
    private void chooseVisualizerStyle(int id){
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(VisualizerMesh.STYLES.length+" visualizer • pilih bentuk").setView(scroll).setNegativeButton("Tutup",null).create();
        for(int group=0;group<VisualizerMesh.GROUPS.length;group++){
            list.addView(text(VisualizerMesh.GROUPS[group],14,PURPLE,true));LinearLayout line=null;int n=0;
            for(int style=0;style<VisualizerMesh.STYLES.length;style++)if(VisualizerMesh.group(style)==group){
                if(n++%2==0){line=row();list.addView(line);}final int chosen=style;
                LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setPadding(dp(4),dp(4),dp(4),dp(8));
                VisualizerGalleryView thumb=new VisualizerGalleryView(this,style);tile.addView(thumb,new LinearLayout.LayoutParams(-1,dp(115)));
                TextView label=text(VisualizerMesh.STYLES[style]+(style==reactive.getVisualizerStyle(id)?" ✓":""),12,Color.WHITE,true);label.setGravity(Gravity.CENTER);tile.addView(label);
                tile.setOnClickListener(v->{reactive.setVisualizerStyle(id,chosen);refreshLayerEditor();dialog.dismiss();});line.addView(tile,new LinearLayout.LayoutParams(0,-2,1));
            }
        }dialog.show();dialog.getWindow().setLayout(-1,Math.min(dp(600),getResources().getDisplayMetrics().heightPixels-dp(80)));
    }
    private android.widget.Spinner choices(LinearLayout panel,String label,String[] options,int selected){
        panel.addView(text(label,12,PURPLE,true));android.widget.Spinner view=new android.widget.Spinner(this);
        view.setAdapter(new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,options));view.setSelection(selected);panel.addView(view);return view;
    }
    private LinearLayout dialogPanel(){LinearLayout p=new LinearLayout(this);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(16),dp(8),dp(16),dp(8));return p;}
    private ScrollView scrolling(LinearLayout panel){ScrollView sc=new ScrollView(this);sc.addView(panel);return sc;}
    private void editMotion(int id){
        MotionStyle m=reactive.getMotion(id);LinearLayout panel=dialogPanel();
        android.widget.Spinner style=choices(panel,"Preset gerak otomatis",MotionStyle.NAMES,m.type);
        android.widget.Spinner source=choices(panel,"Audio untuk Punch / Spring / Bounce / Tilt",ReactiveView.SOURCES,m.source);
        addSlider(panel,"Intensitas",m.amount,v->m.amount=v);addSlider(panel,"Kecepatan gerak",m.speed,v->m.speed=v);addSlider(panel,"Motion Blur",m.blur,v->m.blur=v);
        java.util.ArrayList<Integer> parents=new java.util.ArrayList<>();java.util.ArrayList<String> names=new java.util.ArrayList<>();parents.add(-1);names.add("Tanpa induk");int selected=0;
        for(int p:reactive.getLayerOrder())if(p!=id){parents.add(p);names.add(reactive.getLayerName(p));if(p==reactive.getParent(id))selected=parents.size()-1;}
        android.widget.Spinner parent=choices(panel,"Ikuti transform layer induk",names.toArray(new String[0]),selected);
        panel.addView(text("Preset berjalan otomatis pada waktu lagu. Motion blur mengikuti posisi, skala dan rotasi. Nilai lebih tinggi menambah beban render.",12,0xffb5bfd5,false));
        new AlertDialog.Builder(this).setTitle("Gerak • "+reactive.getLayerName(id)).setView(scrolling(panel)).setPositiveButton("Terapkan",(d,w)->{
            int p=parents.get(parent.getSelectedItemPosition());if(!reactive.setParent(id,p)){toast("Hubungan induk tidak boleh berputar");return;}
            m.type=style.getSelectedItemPosition();m.source=source.getSelectedItemPosition();reactive.setMotion(id,m);
        }).setNegativeButton("Batal",null).show();
    }
    private void editCurve(int id){
        int param=selectedKeyframeParam[id];long time=currentPosition();
        if(reactive.keyframeCount(id,param)<2){toast("Tambahkan minimal dua keyframe pada parameter ini");return;}
        LinearLayout panel=dialogPanel();MotionCurve initial=reactive.getCurve(id,param,time);
        android.widget.Spinner mode=choices(panel,"Jenis kurva",MotionCurve.NAMES,initial.type);CurveEditorView graph=new CurveEditorView(this,initial);panel.addView(graph,new LinearLayout.LayoutParams(-1,dp(220)));
        mode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onNothingSelected(android.widget.AdapterView<?> p){}
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int position,long item){MotionCurve c=graph.getCurve();c.type=position;graph.setCurve(c);}
        });
        panel.addView(text("Bezier Custom: geser dua handle. Kurva berlaku dari keyframe sebelum playhead menuju titik berikutnya.",12,0xffb5bfd5,false));
        new AlertDialog.Builder(this).setTitle("Easing • "+AutomationParameters.NAMES[param]).setView(panel)
            .setPositiveButton("Segmen ini",(d,w)->{if(!reactive.setCurve(id,param,time,graph.getCurve(),false))toast("Geser playhead di antara dua keyframe");})
            .setNeutralButton("Semua segmen",(d,w)->reactive.setCurve(id,param,time,graph.getCurve(),true)).setNegativeButton("Batal",null).show();
    }
    private void editFilterStack(int id){
        LayerFilter[] filters=reactive.getFilters(id);String[] labels=new String[filters.length+1];
        for(int i=0;i<filters.length;i++)labels[i]=(i+1)+". "+LayerFilter.NAMES[filters[i].type]+" • "+filters[i].amount;
        labels[filters.length]="＋ Tambah efek";
        new AlertDialog.Builder(this).setTitle("Efek • diproses dari atas ke bawah").setItems(labels,(d,index)->{
            if(index==filters.length){if(filters.length>=LayerFilter.MAX){toast("Maksimum 12 efek per layer");return;}
                new AlertDialog.Builder(this).setTitle("Pilih efek").setItems(LayerFilter.NAMES,(dd,type)->{LayerFilter[] a=java.util.Arrays.copyOf(filters,filters.length+1);a[filters.length]=new LayerFilter(type);reactive.setFilters(id,a);editFilterStack(id);}).show();
            }else editFilter(id,index);
        }).setNegativeButton("Tutup",null).show();
    }
    private void editFilter(int id,int index){
        LayerFilter[] items=reactive.getFilters(id);LayerFilter f=items[index];LinearLayout panel=dialogPanel();
        addSlider(panel,"Nilai efek",f.amount,v->f.amount=v);
        String[] sources=new String[ReactiveView.SOURCES.length+1];sources[0]="Manual";System.arraycopy(ReactiveView.SOURCES,0,sources,1,ReactiveView.SOURCES.length);
        android.widget.Spinner source=choices(panel,"Respons audio efek ini",sources,f.route.source+1);
        addSlider(panel,"Arah & intensitas audio",(f.route.depth+100)/2,v->f.route.depth=v*2-100);
        addFrequencySlider(panel,"Min Hz (custom)",f.route.minHz,v->{f.route.minHz=v;f.route.maxHz=Math.max(v,f.route.maxHz);});
        addFrequencySlider(panel,"Max Hz (custom)",f.route.maxHz,v->{f.route.maxHz=v;f.route.minHz=Math.min(v,f.route.minHz);});
        addSlider(panel,"Attack ms /10",f.route.attack/10,v->f.route.attack=v*10);addSlider(panel,"Release ms /15",f.route.release/15,v->f.route.release=v*15);
        String[] order=new String[items.length];for(int i=0;i<order.length;i++)order[i]="Urutan "+(i+1);
        android.widget.Spinner position=choices(panel,"Urutan efek",order,index);
        new AlertDialog.Builder(this).setTitle(LayerFilter.NAMES[f.type]).setView(scrolling(panel)).setPositiveButton("Simpan",(d,w)->{
            f.route.source=source.getSelectedItemPosition()-1;java.util.ArrayList<LayerFilter> list=new java.util.ArrayList<>(java.util.Arrays.asList(items));list.remove(index);list.add(position.getSelectedItemPosition(),f);reactive.setFilters(id,list.toArray(new LayerFilter[0]));editFilterStack(id);
        }).setNeutralButton("Hapus",(d,w)->{java.util.ArrayList<LayerFilter> list=new java.util.ArrayList<>(java.util.Arrays.asList(items));list.remove(index);reactive.setFilters(id,list.toArray(new LayerFilter[0]));editFilterStack(id);}).setNegativeButton("Batal",null).show();
    }
    private void chooseLogoTarget(){
        java.util.ArrayList<Integer> ids=new java.util.ArrayList<>();java.util.ArrayList<String> names=new java.util.ArrayList<>();
        for(int id:reactive.getLayerOrder())if(reactive.getLayerType(id)==ReactiveView.VISUALIZER){ids.add(id);names.add(reactive.getLayerName(id));}
        if(ids.isEmpty()){int id=reactive.addLayer(ReactiveView.VISUALIZER);if(id<0){toast("Maksimum 48 layer");return;}refreshLayerEditor();requestLayerImage(id);}
        else if(ids.size()==1)requestLayerImage(ids.get(0));
        else new AlertDialog.Builder(this).setTitle("Pasang logo pada visualizer")
            .setItems(names.toArray(new String[0]),(d,index)->requestLayerImage(ids.get(index))).show();
    }
    private void editAccent(int id){
        EditText input=new EditText(this);input.setText(String.format(java.util.Locale.US,"#%06X",reactive.getAccentColor(id)&0xffffff));
        new AlertDialog.Builder(this).setTitle("Warna aksen (#RRGGBB)").setView(input)
            .setPositiveButton("Simpan",(d,w)->{try{reactive.setAccentColor(id,Color.parseColor(input.getText().toString()));}catch(Exception e){toast("Warna tidak valid");}})
            .setNegativeButton("Batal",null).show();
    }
    private void chooseAudioParameter(int id){
        int[] targets={0,1,2,3,4,5,6,7,8,14,15};String[] names=new String[targets.length];
        for(int i=0;i<targets.length;i++)names[i]=AutomationParameters.NAMES[targets[i]];
        new AlertDialog.Builder(this).setTitle("Parameter yang mengikuti audio")
            .setItems(names,(d,index)->editAudioRoute(id,targets[index])).show();
    }
    private void editAudioRoute(int id,int param){
        AudioRoute route=reactive.getAudioRoute(id,param);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(8),dp(16),dp(8));
        String[] choices=new String[ReactiveView.SOURCES.length+1];choices[0]="Manual • tanpa koneksi";
        System.arraycopy(ReactiveView.SOURCES,0,choices,1,ReactiveView.SOURCES.length);
        android.widget.Spinner sources=new android.widget.Spinner(this);
        sources.setAdapter(new android.widget.ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,choices));
        sources.setSelection(route.source+1);panel.addView(sources);
        panel.addView(text("Gerakan ditambahkan ke nilai dasar/keyframe. 50 = diam; ke kiri negatif, ke kanan positif.",12,0xffc4cce2,false));
        addSlider(panel,"Arah & intensitas",(route.depth+100)/2,v->route.depth=v*2-100);
        addFrequencySlider(panel,"Min Hz (custom)",route.minHz,v->{route.minHz=v;route.maxHz=Math.max(v,route.maxHz);});
        addFrequencySlider(panel,"Max Hz (custom)",route.maxHz,v->{route.maxHz=v;route.minHz=Math.min(v,route.minHz);});
        addSlider(panel,"Attack ms /10",route.attack/10,v->route.attack=v*10);
        addSlider(panel,"Release ms /15",route.release/15,v->route.release=v*15);
        new AlertDialog.Builder(this).setTitle(reactive.getLayerName(id)+" → "+AutomationParameters.NAMES[param])
            .setView(panel).setPositiveButton("Hubungkan",(d,w)->{
                route.source=sources.getSelectedItemPosition()-1;reactive.setAudioRoute(id,param,route);
                if(route.source>=7&&!reactive.hasStem(route.source-7))toast("Impor "+ReactiveView.STEM_NAMES[route.source-7]+" melalui Audio Terpisah");
            }).setNegativeButton("Batal",null).show();
    }
    private void chooseStem(){
        String[] options=new String[4];for(int i=0;i<4;i++)options[i]=ReactiveView.STEM_NAMES[i]+(reactive.hasStem(i)?" ✓":" • belum diimpor");
        new AlertDialog.Builder(this).setTitle("Audio terpisah • empat sumber")
            .setItems(options,(d,index)->choose("audio/*",PICK_STEM+index))
            .setNeutralButton("Pisahkan otomatis",(d,w)->separateAudio())
            .setNegativeButton("Tutup",null).show();
    }
    private void loadStem(int slot,Uri uri){
        int version=stemGeneration.incrementAndGet(slot);reactive.setStem(slot,null,uri.toString());
        status.setText("Menganalisis stem "+ReactiveView.STEM_NAMES[slot]+"…");
        analysisWorker.submit(()->{
            try{AudioAnalyzer.Track result=AudioAnalyzer.analyze(getApplicationContext(),uri);
                runOnUiThread(()->{if(isDestroyed()||version!=stemGeneration.get(slot)||!uri.toString().equals(reactive.getStemUri(slot)))return;
                    reactive.setStem(slot,result,uri.toString());status.setText("Stem "+ReactiveView.STEM_NAMES[slot]+" siap • pilih di Audio → Parameter");});
            }catch(Exception e){runOnUiThread(()->{if(!isDestroyed()&&version==stemGeneration.get(slot))status.setText("Stem gagal: "+e.getMessage());});}
        });
    }
    private void showTools(){
        String[] options={"Simpan PNG","Simpan proyek","Buka proyek ZIP + media","Ekspor proyek ZIP + media",
            "Buka preset JSON","Ekspor preset JSON","Shader kustom","Laporan performa","Uji preview 15 detik","Uji ekspor 8 detik","Uji 3 profil Poco F6"};
        new AlertDialog.Builder(this).setTitle("Proyek & alat").setItems(options,(d,index)->{
            switch(index){case 0:saveImage();break;case 1:savePreset();break;case 2:choose("application/zip",PICK_BUNDLE_OPEN);break;
                case 3:createPortableProject();break;case 4:choose("application/json",PICK_PRESET_OPEN);break;case 5:createProjectFile();break;
                case 6:editGpuShader();break;case 7:showPerformance();break;case 8:benchmarkPreview();break;case 9:runBenchmark();break;case 10:runPocoBenchmark();break;}
        }).show();
    }
    private void chooseLayer() {
        new AlertDialog.Builder(this).setTitle("Tambah layer")
                .setItems(ReactiveView.LAYER_NAMES,(dialog,type)->{
                    int id=reactive.addLayer(type);
                    if(id<0){toast("Batas 48 layer; hapus sebagian layer dulu");return;}
                    reactive.setEnabled(id,true);
                    addEffectPanel(effectContainer,id);reorderPanels();timelineView.setFocusLayer(id);timelineView.refresh();
                    if(type==ReactiveView.IMAGE)requestLayerImage(id);
                    else if(type==ReactiveView.VIDEO)requestLayerVideo(id);
                    else if(type==ReactiveView.VISUALIZER)chooseVisualizerStyle(id);
                }).show();
    }
    private void toast(String message) {Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private void reorderPanels() {
        if(effectContainer==null) return;
        for(LinearLayout card:panels) if(card!=null && card.getParent()==effectContainer)
            effectContainer.removeView(card);
        // Section heading, spectrum toggle, and two global sliders stay above FX cards.
        int index=addLayerButton==null?effectContainer.getChildCount():effectContainer.indexOfChild(addLayerButton)+1;
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
    private SeekBar addSlider(LinearLayout parent,String label,int initial,OnValue callback) {
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
                if(user)callback.set(value);
                number.setText(String.valueOf(value));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        parent.addView(line);return seek;
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
            if(request==PICK_LAYER_VIDEO)pendingVideoLayer=-1;
            return;
        }
        Uri uri=data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch(Exception ignored) { }
        if(request>=PICK_STEM&&request<PICK_STEM+4)loadStem(request-PICK_STEM,uri);
        else if(request==PICK_MIX)addMixSource(uri);
        else if(request==PICK_AUDIO){audioMix=new AudioMixProject();reactive.setSections(new SongSections());for(int i=0;i<4;i++){stemGeneration.incrementAndGet(i);reactive.setStem(i,null,"");}restorePosition=0;loadAudio(uri);}
        else if(request==PICK_IMAGE) loadEditableBackground(uri);
        else if(request==PICK_LAYER_IMAGE && pendingImageLayer>=0){
            int layer=pendingImageLayer;pendingImageLayer=-1;
            try {
                if(reactive.isAllocated(layer))reactive.setLayerImage(layer,decodeImage(uri,2048),uri.toString());
                status.setText("Gambar layer berhasil dimuat");
            }catch(Exception e){status.setText("Gambar layer gagal: "+e.getMessage());}
        } else if(request==PICK_LAYER_VIDEO && pendingVideoLayer>=0){
            int layer=pendingVideoLayer;pendingVideoLayer=-1;
            try {
                if(reactive.isAllocated(layer))reactive.setLayerVideoUri(layer,uri.toString());
                status.setText("Video layer siap • suara tetap memakai lagu utama");
            }catch(Exception e){status.setText("Video gagal dibuka: "+e.getMessage());}
        } else if(request==PICK_BUNDLE_SAVE)savePortableProject(uri);
        else if(request==PICK_BUNDLE_OPEN)openPortableProject(uri);
        else if(request==PICK_EXPORT) exportMp4(uri);
        else if(request==PICK_PRESET_OPEN) importProjectFile(uri);
        else if(request==PICK_PRESET_SAVE) saveProjectFile(uri);
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
            if(restorePosition>0){player.seekTo(Math.min(restorePosition,durationMs));restorePosition=0;}
            queueSave();
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
                    if(reactive.getSections().sections.isEmpty())reactive.setSections(result.sections());
                    timelineView.setTrack(result);
                    analyzed=true;
                    status.setText("Siap! Audio dianalisis"+(result.estimatedBpm()>0?
                            " • tempo perkiraan "+result.estimatedBpm()+" BPM":"")+
                            ". Efek tetap mengikuti sinyal audio asli.");
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
    private void loadEditableBackground(Uri uri){
        try{int id=-1;for(int layer:reactive.getLayerOrder())if(reactive.getLayerType(layer)==ReactiveView.IMAGE&&reactive.getAdvanced(layer).fillCanvas){id=layer;break;}
            if(id<0)id=reactive.addLayer(ReactiveView.IMAGE);if(id<0)throw new IllegalArgumentException("Slot layer penuh");
            reactive.setLayerImage(id,decodeImage(uri,2048),uri.toString());LayerAdvanced a=reactive.getAdvanced(id);a.fillCanvas=true;reactive.setAdvanced(id,a);reactive.setLayerName(id,"Background");reactive.setOpacity(id,100);reactive.setLayerScale(id,50);reactive.setStrength(id,0);
            int[] order=reactive.getLayerOrder();int[] sorted=new int[order.length];sorted[0]=id;int n=1;for(int layer:order)if(layer!=id)sorted[n++]=layer;reactive.setLayerOrder(sorted);reactive.setBackdrop(null);backgroundUri=null;refreshLayerEditor();queueSave();status.setText("Background menjadi layer • gerak, mask, audio, dan efek bisa diatur");
        }catch(Exception e){status.setText("Background gagal: "+e.getMessage());}
    }
    private void loadImage(Uri uri) {
        try {
            reactive.setBackdrop(decodeImage(uri,2048));
            backgroundUri=uri;queueSave();
            status.setText("Background terpasang. Atur mapping efek sesuai musik.");
        } catch(Exception e) {status.setText("Gambar gagal dibuka: "+e.getMessage());}
    }
    private void restoreLayerImages(){
        for(int id:reactive.getLayerOrder()){
            int type=reactive.getLayerType(id);
            if(type==ReactiveView.IMAGE||type==ReactiveView.VISUALIZER){
                String uri=reactive.getImageUri(id);
                if(uri==null||uri.isEmpty())continue;
                try{reactive.setLayerImage(id,decodeImage(Uri.parse(uri),2048),uri);}
                catch(Exception e){status.setText("Sebagian gambar preset tidak tersedia lagi");}
            }else if(type==ReactiveView.VIDEO){
                String uri=reactive.getVideoUri(id);
                if(uri==null||uri.isEmpty())continue;
                try{reactive.setLayerVideoUri(id,uri);}
                catch(Exception e){status.setText("Sebagian video preset tidak tersedia lagi");}
            }
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
            saveSession();
            toast("Proyek tersimpan offline");
        }catch(Exception e){status.setText("Preset gagal: "+e.getMessage());}
    }
    private void createPortableProject(){
        if(exporting){toast("Selesaikan ekspor terlebih dahulu");return;}
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE,"ranneki-project.rnszip");
        startActivityForResult(intent,PICK_BUNDLE_SAVE);
    }
    private void savePortableProject(Uri location){
        if(exporting)return;
        exporting=true;exportCancel.set(false);
        final String json=sessionJson();
        final Uri audio=musicUri,background=backgroundUri;
        status.setText("Menyimpan media ke proyek ZIP…");
        exportWorker.execute(()->{
            try{
                ProjectBundle.save(getApplicationContext(),location,json,audio,background);
                runOnUiThread(()->status.setText("Proyek + media tersimpan (offline)."));
            }catch(Exception e){
                runOnUiThread(()->status.setText("Gagal menyimpan ZIP: "+e.getMessage()));
            }finally{exporting=false;}
        });
    }
    private void openPortableProject(Uri location){
        if(exporting){toast("Selesaikan ekspor terlebih dahulu");return;}
        exporting=true;
        status.setText("Membaca dan memverifikasi proyek ZIP…");
        exportWorker.execute(()->{
            try{
                ProjectBundle.Loaded loaded=ProjectBundle.open(getApplicationContext(),location);
                AudioMixProject loadedMix=AudioMixProject.from(new org.json.JSONObject(loaded.json).optJSONArray("audioClips"));
                runOnUiThread(()->{
                    String original=reactive.exportProjectJson();
                    if(!reactive.importProjectJson(loaded.json)){
                        reactive.importProjectJson(original);
                        status.setText("Format proyek tidak didukung");return;
                    }
                    restoreLayerImages();refreshLayerEditor();
                    for(int i=0;i<4;i++)if(!reactive.getStemUri(i).isEmpty())loadStem(i,Uri.parse(reactive.getStemUri(i)));
                    audioMix=loadedMix;
                    if(loaded.background!=null)loadImage(loaded.background);
                    if(loaded.music!=null)loadAudio(loaded.music);
                    saveSession();
                    status.setText("Proyek ZIP lengkap dimuat: "+reactive.getLayerCount()+" layer.");
                });
            }catch(Exception e){runOnUiThread(()->status.setText("Impor proyek ZIP gagal: "+e.getMessage()));}
            finally{exporting=false;}
        });
    }
    private void createProjectFile() {
        if(exporting){toast("Selesaikan ekspor terlebih dahulu");return;}
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,"ranneki-studio-project.json");
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent,PICK_PRESET_SAVE);
    }
    private void saveProjectFile(Uri uri) {
        try {
            String data=reactive.exportProjectJson();
            try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {
                if(out==null)throw new java.io.IOException("Tidak dapat menulis file");
                out.write(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.flush();
            }
            status.setText("Preset JSON tersimpan. Media sumber perlu disimpan secara terpisah.");
        } catch(Exception e){status.setText("Gagal menyimpan preset: "+e.getMessage());}
    }
    private void importProjectFile(Uri uri) {
        if(exporting){toast("Selesaikan ekspor terlebih dahulu");return;}
        try {
            byte[] data;
            try(InputStream in=getContentResolver().openInputStream(uri)) {
                if(in==null)throw new java.io.IOException("File tidak bisa dibaca");
                java.io.ByteArrayOutputStream buf=new java.io.ByteArrayOutputStream();
                byte[] block=new byte[8192];int read;
                while((read=in.read(block))!=-1){
                    if(buf.size()+read>1024*1024)throw new java.io.IOException("Preset maksimal 1 MB");
                    buf.write(block,0,read);
                }
                data=buf.toByteArray();
            }
            if(data.length==0)throw new java.io.IOException("File kosong");
            String json=new String(data,java.nio.charset.StandardCharsets.UTF_8);
            org.json.JSONObject parsed=new org.json.JSONObject(json);
            org.json.JSONArray layers=parsed.getJSONArray("layers");
            if(layers.length()>ReactiveView.MAX_LAYERS)
                throw new IllegalArgumentException("Lebih dari 48 layer");
            for(int i=0;i<layers.length();i++) {
                org.json.JSONObject item=layers.getJSONObject(i);
                int type=item.getInt("type");
                if(type<0 || type>=ReactiveView.LAYER_TYPE_COUNT)
                    throw new IllegalArgumentException("Tipe layer tidak dikenal");
            }
            String previous=reactive.exportProjectJson();
            if(!reactive.importProjectJson(json)){
                reactive.importProjectJson(previous);
                throw new java.io.IOException("Isi preset tidak valid");
            }
            restoreLayerImages();
            refreshLayerEditor();
            saveSession();
            status.setText("Preset berhasil diimpor: "+reactive.getLayerCount()+" layer. Tambahkan media bila diperlukan.");
        }catch(Exception e){status.setText("Impor preset gagal: "+e.getMessage());}
    }
    private void refreshLayerEditor() {
        if(effectContainer==null)return;
        if(chromaticSlider!=null)chromaticSlider.setProgress(reactive.getChromatic());
        if(vignetteSlider!=null)vignetteSlider.setProgress(reactive.getVignette());
        for(int i=0;i<panels.length;i++) {
            LinearLayout card=panels[i];
            if(card!=null && card.getParent()==effectContainer)effectContainer.removeView(card);
            panels[i]=null;toggleButtons[i]=null;mappingButtons[i]=null;
        }
        for(int id:reactive.getLayerOrder())addEffectPanel(effectContainer,id);
        reorderPanels();
        if(timelineView!=null){timelineView.setScene(reactive);timelineView.refresh();}
        ratioButton.setText(reactive.isPortrait()?"9:16":"16:9");
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
    /** Custom GLES 2.0 fragment shader. Runs at export time on-device, not on the Canvas preview. */
    private void editGpuShader() {
        if(exporting){toast("Tunggu ekspor selesai");return;}
        final EditText input=new EditText(this);
        input.setText(reactive.getShaderSource());
        input.setTextSize(11);
        input.setTextColor(Color.WHITE);
        input.setBackgroundColor(0xff151b30);
        input.setSingleLine(false);
        input.setMinLines(12);
        input.setMaxLines(22);
        input.setGravity(Gravity.TOP);
        final ScrollView editor=new ScrollView(this);
        editor.addView(input);
        String help="GLES 2.0 fragment shader untuk EKSPOR MP4. " +
                "Input: varying vTex, sampler2D uTexture; " +
                "uniform float uTime, uKick, uBass, uSnare, uTreble, " +
                "uPulse, uChromatic, uVignette; uniform vec2 uResolution. " +
                "uTime dalam detik, level audio 0-1. Preview Canvas tidak menampilkan shader. " +
                "Loop dilarang demi keamanan GPU HP.";
        new AlertDialog.Builder(this).setTitle("GPU GLSL • Offline")
                .setMessage(help).setView(editor)
                .setPositiveButton("Simpan",(dialog,which)->{
                    try {
                        reactive.setShaderSource(input.getText().toString());
                        savePreset();
                        toast("Shader disimpan di proyek. Ekspor MP4 untuk melihat hasilnya.");
                    }catch(Exception err){
                        status.setText("Shader ditolak: "+err.getMessage());
                    }
                })
                .setNeutralButton("Contoh",(dialog,which)->{
                    final String[] options={"Classic", "Neon reactive", "Monochrome"};
                    new AlertDialog.Builder(this).setTitle("Pilih preset GPU")
                            .setItems(options,(d,index)->{
                                String fragment=index==1?ShaderSource.NEON:
                                        index==2?ShaderSource.MONOCHROME:ShaderSource.CLASSIC;
                                reactive.setShaderSource(fragment);savePreset();
                                toast("Shader preset tersimpan; berlaku saat ekspor MP4");
                            }).show();
                })
                .setNegativeButton("Batal",(dialog,which)->{}).show();
    }

    private void chooseExport() {
        if(musicUri==null || !analyzed) {toast("Impor lagu dan tunggu analisis dulu");return;}
        if(exporting) {toast("Ekspor sedang berjalan");return;}
        String[] sizes={"540p 30FPS • Cepat", "720p 30FPS • Seimbang", 
                "1080p 30FPS • Kualitas penuh", "1080p 30FPS • Hemat 65%", 
                "1080p 60FPS • Eksperimental (berat)"};
        new AlertDialog.Builder(this).setTitle("Ekspor MP4 • GPU post FX")
                .setSingleChoiceItems(sizes,1,(dialog,which)->{
                    int shortSide=which==0?540:which==1?720:1080;
                    exportFps=which==4?60:30;
                    exportRasterPercent=which==0?100:which==1?75:which==2?100:which==3?65:75;
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
        reactive.copyAnalysisTo(renderCopy);
        Bitmap bg=reactive.getBackdrop();
        if(bg!=null && !bg.isRecycled())renderCopy.setBackdrop(bg.copy(Bitmap.Config.ARGB_8888,false));
        reactive.copyImageLayersTo(renderCopy);
        try{reactive.copyVideoLayersTo(renderCopy);}
        catch(Exception e){
            renderCopy.recycleLayerImages();renderCopy.closeVideoLayers();exporting=false;
            status.setText("Video layer tidak dapat dibaca: "+e.getMessage());return;
        }
        renderCopy.setEconomyMode(false);
        final Uri input=musicUri;
        final int length=durationMs;
        final ExportProfile profile=new ExportProfile(exportWidth,exportHeight,exportFps,
                exportRasterPercent,reactive.getChromatic(),reactive.getVignette());
        status.setText("Ekspor MP4 dimulai — tekan status untuk membatalkan");
        status.setOnClickListener(v->{if(exporting){exportCancel.set(true);status.setText("Membatalkan ekspor...");}});
        exportWorker.execute(()->{
            try {
                String report=Mp4Exporter.export(getApplicationContext(),input,outputUri,renderCopy,
                        profile,length,exportCancel,(progress,stage)->{
                            runOnUiThread(()->status.setText(stage+" "+progress+"% • ketuk untuk batal"));
                        });
                lastExportReport=report;
                runOnUiThread(()->{status.setText("MP4 tersimpan: H.264 + AAC | ketuk Performa untuk laporan");toast("Video berhasil diekspor");});
            }catch(Exception e){
                runOnUiThread(()->status.setText("Ekspor gagal: "+e.getMessage()));
            }finally {
                Bitmap b=renderCopy.getBackdrop();if(b!=null && !b.isRecycled())b.recycle();
                renderCopy.recycleLayerImages();renderCopy.closeVideoLayers();
                exporting=false;
                runOnUiThread(()->status.setOnClickListener(null));
            }
        });
    }
    /** Measures the complete offline H.264/AAC pipeline on the actual Android device. */
    private void runBenchmark() {
        if(musicUri==null||!analyzed||reactive.getTrack()==null) {
            toast("Impor lagu dan tunggu analisis dulu");return;
        }
        if(exporting) {toast("Render lain sedang berjalan");return;}
        if(player!=null)try{player.pause();playButton.setText("▶ Play");}catch(Exception ignored){}
        final ReactiveView scene=new ReactiveView(this);
        if(!scene.importProjectJson(reactive.exportProjectJson())){
            status.setText("Benchmark gagal: scene tidak valid");return;
        }
        reactive.copyAnalysisTo(scene);
        Bitmap background=reactive.getBackdrop();
        if(background!=null&&!background.isRecycled())scene.setBackdrop(background.copy(Bitmap.Config.ARGB_8888,false));
        reactive.copyImageLayersTo(scene);
        try{reactive.copyVideoLayersTo(scene);}
        catch(Exception e){
            scene.recycleLayerImages();scene.closeVideoLayers();
            status.setText("Video layer tidak dapat dibaca: "+e.getMessage());return;
        }
        scene.setEconomyMode(false);
        final ExportProfile profile=new ExportProfile(720,1280,30,75,25,20);
        final Uri input=musicUri;
        exporting=true;exportCancel.set(false);
        status.setText("Benchmark MP4 8s dimulai • ketuk status untuk membatalkan");
        status.setOnClickListener(v->{if(exporting){exportCancel.set(true);status.setText("Membatalkan benchmark...");}});
        exportWorker.execute(()->{
            try {
                String report=Mp4Exporter.benchmark(getApplicationContext(),input,scene,profile,
                        Math.min(durationMs,8000),exportCancel,(percent,stage)->
                            runOnUiThread(()->status.setText("Benchmark "+stage+" "+percent+"% • ketuk batal")));
                lastExportReport=report;
                runOnUiThread(()->{status.setText("Benchmark selesai • buka Performa untuk hasil");showPerformance();});
            }catch(Exception error){
                runOnUiThread(()->status.setText("Benchmark gagal: "+error.getMessage()));
            }finally{
                Bitmap copied=scene.getBackdrop();if(copied!=null&&!copied.isRecycled())copied.recycle();
                scene.recycleLayerImages();scene.closeVideoLayers();exporting=false;
                runOnUiThread(()->status.setOnClickListener(null));
            }
        });
    }

    /** Three real H264/AAC hardware workloads on the phone; no fake Snapdragon scores. */
    private void runPocoBenchmark() {
        if(musicUri==null||!analyzed||reactive.getTrack()==null||durationMs<8000) {
            toast("Impor dan analisis musik minimal 8 detik terlebih dahulu");return;
        }
        if(exporting){toast("Ekspor sedang berjalan");return;}
        if(player!=null)try{player.pause();playButton.setText("▶ Play");}catch(Exception ignored){}
        ReactiveView scene=new ReactiveView(this);
        if(!scene.importProjectJson(reactive.exportProjectJson())){
            status.setText("Benchmark gagal: proyek tidak valid");return;
        }
        reactive.copyAnalysisTo(scene);
        Bitmap original=reactive.getBackdrop();
        if(original!=null&&!original.isRecycled())scene.setBackdrop(original.copy(Bitmap.Config.ARGB_8888,false));
        reactive.copyImageLayersTo(scene);
        try{reactive.copyVideoLayersTo(scene);}catch(Exception err){
            scene.recycleLayerImages();scene.closeVideoLayers();
            status.setText("Media benchmark tidak tersedia: "+err.getMessage());return;
        }
        scene.setEconomyMode(false);
        exporting=true;exportCancel.set(false);
        final Uri input=musicUri;
        final int startBattery=batteryPercent();
        status.setText("Benchmark Poco F6: 3 ekspor GPU • ketuk untuk batal");
        status.setOnClickListener(v->{exportCancel.set(true);status.setText("Menghentikan uji performa...");});
        exportWorker.execute(()->{
            StringBuilder report=new StringBuilder("RANNEKI ON-DEVICE GPU BENCHMARK\n")
                    .append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
                    .append(" / Android ").append(android.os.Build.VERSION.RELEASE)
                    .append("\nWorkloads: 8 seconds each, original soundtrack, real MediaCodec H.264/AAC\n");
            try{
                for(int index=0;index<PocoBenchmarkPlan.CASES.length;index++){
                    Mp4Exporter.check(exportCancel);
                    final PocoBenchmarkPlan.Case benchmark=PocoBenchmarkPlan.CASES[index];
                    final int run=index+1;
                    report.append("\n# ").append(run).append(' ').append(benchmark.label).append('\n');
                    try{
                        String result=Mp4Exporter.benchmark(getApplicationContext(),input,scene,
                                benchmark.profile(),8000,exportCancel,(percent,stage)->
                                    runOnUiThread(()->status.setText("GPU "+run+"/3 "+stage+" "+percent+"% • ketuk batal")));
                        report.append(result).append('\n');
                    }catch(InterruptedException interrupted){throw interrupted;}
                    catch(Exception err){
                        report.append("SKIPPED/FAILED: ").append(err.getMessage()).append('\n');
                    }
                }
            }catch(InterruptedException ignored){report.append("\nCancelled by user.\n");}
            finally{
                report.append("Battery before: ").append(startBattery).append("% / after: ")
                        .append(batteryPercent()).append("% (Android estimate)\n");
                lastExportReport=report.toString();
                Bitmap bg=scene.getBackdrop();if(bg!=null&&!bg.isRecycled())bg.recycle();
                scene.recycleLayerImages();scene.closeVideoLayers();
                exporting=false;
                runOnUiThread(()->{
                    status.setOnClickListener(null);
                    status.setText("Uji GPU selesai • Performa untuk laporan (salin)");
                    showPerformance();
                });
            }
        });
    }
    private int batteryPercent(){
        android.os.BatteryManager battery=(android.os.BatteryManager)getSystemService(BATTERY_SERVICE);
        return battery==null?-1:battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    /** Actual device metrics require running the app; no Poco F6 hardware is available in CI. */
    private void benchmarkPreview() {
        if(player==null || !analyzed) {toast("Impor dan analisis musik dulu");return;}
        if(exporting) {toast("Tunggu ekspor selesai");return;}
        try {
            player.seekTo(0);player.start();playButton.setText("⏸ Pause");
        }catch(Exception ex) {toast("Gagal mulai benchmark: "+ex.getMessage());return;}
        reactive.resetPreviewStats();
        toast("Benchmark preview 15 detik — jangan pindah aplikasi");
        ui.postDelayed(()->{
            if(isFinishing()||isDestroyed())return;
            try {if(player!=null)player.pause();}catch(Exception ignored){}
            playButton.setText("▶ Play");
            showPerformance();
        },15000);
    }

    private void showPerformance() {
        Runtime runtime=Runtime.getRuntime();
        long used=(runtime.totalMemory()-runtime.freeMemory())/(1024*1024);
        ThermalInfo probe=new ThermalInfo(this);probe.sample();
        String summary="MODEL: "+Build.MANUFACTURER+" "+Build.MODEL+"\n"+
                "Android: "+Build.VERSION.RELEASE+"\n"+
                "Thermal status: "+ThermalInfo.label(probe.peak())+"\n"+
                "Java heap used: "+used+" MB\n\n"+
                "PREVIEW (saat musik diputar)\n"+reactive.previewReport()+"\n"+(gpuPreview==null?"":gpuPreview.report())+
                "\n\nLAST EXPORT\n"+lastExportReport+
                "\n\nCatatan: status termal tidak menunjukkan suhu °C. " +
                "Angka nyata hanya valid setelah dijalankan di perangkat.";
        new AlertDialog.Builder(this).setTitle("Ranneki • Performance")
                .setMessage(summary)
                .setPositiveButton("Salin laporan",(d,w)->{
                    android.content.ClipboardManager clipboard=
                            (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Ranneki diagnostics",summary));
                    toast("Laporan disalin");
                }).setNegativeButton("Tutup",(d,w)->{}).show();
    }

    private void saveImage() {
        if(exporting){toast("Tunggu ekspor selesai");return;}
        ReactiveView copy=new ReactiveView(this);
        if(!copy.importProjectJson(reactive.exportProjectJson())){toast("Proyek tidak valid");return;}
        reactive.copyAnalysisTo(copy);reactive.copyImageLayersTo(copy);
        Bitmap backdrop=reactive.getBackdrop();if(backdrop!=null&&!backdrop.isRecycled())copy.setBackdrop(backdrop.copy(Bitmap.Config.ARGB_8888,false));
        try{reactive.copyVideoLayersTo(copy);}catch(Exception e){copy.recycleLayerImages();copy.closeVideoLayers();toast("Video tidak dapat dibuka");return;}
        final int width=reactive.isPortrait()?1080:1920,height=reactive.isPortrait()?1920:1080;
        final long position=currentPosition();exporting=true;status.setText("Menyimpan PNG 1080p…");
        exportWorker.execute(()->{
            Bitmap bitmap=null;
            try{
                bitmap=GpuImageRenderer.render(copy,width,height,position);
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

                runOnUiThread(()->{status.setText("PNG tersimpan dengan efek GPU");toast("Frame PNG tersimpan");});
            }catch(Exception e){runOnUiThread(()->status.setText("PNG gagal: "+e.getMessage()));}
            finally{if(bitmap!=null)bitmap.recycle();copy.recycleLayerImages();copy.closeVideoLayers();
                Bitmap bg=copy.getBackdrop();if(bg!=null&&!bg.isRecycled())bg.recycle();exporting=false;}
        });
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
        if(stressRunning){stressRunning=false;if(player!=null)player.setLooping(false);ui.removeCallbacks(stressTick);getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(player!=null)player.setLooping(false);status.setText("Uji dihentikan karena aplikasi meninggalkan layar");}
        ui.removeCallbacks(pendingSave);saveSession();
        if(gpuPreview!=null)gpuPreview.onPause();
        super.onPause();
        if(player!=null) {
            try {player.pause();} catch(Exception ignored) { }
        }
        if(playButton!=null) playButton.setText("▶ Play");
    }
    @Override protected void onResume(){
        super.onResume();
        if(gpuPreview!=null)gpuPreview.onResume();
    }
    @Override protected void onDestroy() {
        studioCancel.set(true);studioWorker.shutdownNow();stressRunning=false;ui.removeCallbacks(stressTick);
        if(gpuPreview!=null)gpuPreview.detach();
        generation.incrementAndGet();
        ui.removeCallbacks(timeUpdater);ui.removeCallbacks(pendingSave);
        reactive.setEditListener(null);
        if(analysisTask!=null) analysisTask.cancel(true);
        analysisWorker.shutdownNow();
        exportCancel.set(true);exportWorker.shutdownNow();
        if(player!=null) {player.release();player=null;}
        if(reactive!=null){reactive.closeVideoLayers();reactive.recycleLayerImages();}
        super.onDestroy();
    }
}
