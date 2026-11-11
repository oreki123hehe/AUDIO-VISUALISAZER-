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
import android.widget.HorizontalScrollView;
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
import java.util.concurrent.atomic.AtomicInteger;

/** Native offline Android audio-reactive visualizer editor. */
public class MainActivity extends Activity {
    private static final int PICK_AUDIO=101, PICK_IMAGE=102;
    private static final int BG=0xff0a0e1d, PANEL=0xff141a30, ACCENT=0xffa999ff;
    private final String[] effectNames={"Debu • Dust", "Api • Fire", "Hujan • Rain", "Air • Ripple"};
    private final Button[] switches=new Button[4],sources=new Button[4];
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private ReactiveView view;
    private MediaPlayer player;
    private Button playButton, ratioButton;
    private TextView status, timer;
    private SeekBar progress;
    private int durationMs;
    private boolean ready;
    private SharedPreferences settings;
    private final ReactiveView.AudioClock clock=new ReactiveView.AudioClock(){
        @Override public long positionMs(){
            if(player==null)return 0;
            try{return player.getCurrentPosition();}catch(Exception e){return 0;}
        }
        @Override public boolean playing(){
            try{return player!=null && player.isPlaying();}catch(Exception e){return false;}
        }
    };
    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            if(timer!=null){
                int position=player==null?0:(int)clock.positionMs();
                timer.setText(time(position)+" / "+time(durationMs));
                if(durationMs>0)progress.setProgress((int)(position*1000L/durationMs));
            }
            ui.postDelayed(this,140);
        }
    };
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        settings=getSharedPreferences("preset",MODE_PRIVATE);
        view=new ReactiveView(this);
        view.setClock(clock);
        readPreset();
        buildUi();
        ui.post(ticker);
    }
    private void buildUi(){
        LinearLayout root=column();
        root.setBackgroundColor(BG);
        root.setPadding(dp(10),dp(7),dp(10),dp(8));
        setContentView(root);
        root.addView(label("RANNEKI  /  VISUALIZER STUDIO",17,ACCENT,true),
            new LinearLayout.LayoutParams(-1,dp(36)));
        HorizontalScrollView scrollButtons=new HorizontalScrollView(this);
        scrollButtons.setHorizontalScrollBarEnabled(false);
        LinearLayout buttons=row();
        scrollButtons.addView(buttons);
        addButton(buttons,"♪ Lagu",v->pick("audio/*",PICK_AUDIO));
        addButton(buttons,"▣ Background",v->pick("image/*",PICK_IMAGE));
        playButton=addButton(buttons,"▶ Play",v->togglePlay());
        ratioButton=addButton(buttons,view.isPortrait()?"9:16":"16:9",v->{
            view.setPortrait(!view.isPortrait());
            ratioButton.setText(view.isPortrait()?"9:16":"16:9");
        });
        addButton(buttons,"▤ Simpan PNG",v->savePng());
        addButton(buttons,"✓ Preset",v->savePreset());
        root.addView(scrollButtons,new LinearLayout.LayoutParams(-1,dp(46)));
        FrameLayout preview=new FrameLayout(this);
        preview.setBackgroundColor(0xff030513);
        preview.addView(view,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout.LayoutParams previewParams=new LinearLayout.LayoutParams(-1,0,1f);
        previewParams.topMargin=dp(4);
        root.addView(preview,previewParams);
        LinearLayout seekRow=row();
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        timer=label("00:00 / 00:00",11,0xffbac4e5,false);
        seekRow.addView(timer,new LinearLayout.LayoutParams(dp(101),dp(34)));
        progress=new SeekBar(this);
        progress.setMax(1000);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        progress.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar s,int p,boolean fromUser){
                if(fromUser && player!=null && durationMs>0)
                    try{player.seekTo((int)(durationMs*p/1000f));}catch(Exception ignored){}
            }
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){}
        });
        seekRow.addView(progress,new LinearLayout.LayoutParams(0,dp(35),1));
        root.addView(seekRow,new LinearLayout.LayoutParams(-1,dp(37)));
        status=label("Pilih lagu untuk membaca kick, bass dan treble.",11,0xffbac4e5,false);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(35)));
        ScrollView settingsScroll=new ScrollView(this);
        LinearLayout editor=column();
        editor.setPadding(dp(3),dp(4),dp(3),dp(20));
        settingsScroll.addView(editor);
        root.addView(settingsScroll,new LinearLayout.LayoutParams(-1,dp(262)));
        editor.addView(label("AUDIO REACTIVE  •  CUSTOM FX",13,ACCENT,true));
        for(int i=0;i<4;i++)addEffectCard(editor,i);
        TextView info=label("Semua analisis offline. Estimasi kick/snare belum memakai stem AI. V1 menyimpan frame PNG; ekspor MP4 belum tersedia.",11,0xff9ca6c7,false);
        info.setPadding(0,dp(10),0,dp(8));editor.addView(info);
    }
    private void addEffectCard(LinearLayout editor,int idx){
        final int id=idx;
        LinearLayout card=column();
        card.setPadding(dp(10),dp(8),dp(10),dp(8));
        card.setBackground(rounded(PANEL,12));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.topMargin=dp(9);editor.addView(card,cp);
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label(effectNames[id],14,Color.WHITE,true),
            new LinearLayout.LayoutParams(0,dp(38),1));
        switches[id]=addButton(header,view.isEnabled(id)?"ON":"OFF",v->{
            view.setEnabled(id,!view.isEnabled(id));
            switches[id].setText(view.isEnabled(id)?"ON":"OFF");
        });
        sources[id]=addButton(header,"→ "+ReactiveView.SOURCES[view.getSource(id)],v->{
            view.setSource(id,view.getSource(id)+1);
            sources[id].setText("→ "+ReactiveView.SOURCES[view.getSource(id)]);
        });
        card.addView(header);
        addSlider(card,"Ukuran",view.getSize(id),value->view.setSize(id,value));
        addSlider(card,"Jumlah / Density",view.getDensity(id),value->view.setDensity(id,value));
        addSlider(card,"Reaktivitas",view.getStrength(id),value->view.setStrength(id,value));
    }
    private interface ValueChange{void change(int v);}
    private void addSlider(LinearLayout parent,String title,int initial,ValueChange handler){
        LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);
        line.addView(label(title,11,0xffc0c9e6,false),
            new LinearLayout.LayoutParams(dp(120),dp(33)));
        SeekBar seek=new SeekBar(this);seek.setMax(100);seek.setProgress(initial);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        line.addView(seek,new LinearLayout.LayoutParams(0,dp(34),1));
        TextView count=label(Integer.toString(initial),11,Color.WHITE,false);
        count.setGravity(Gravity.CENTER);
        line.addView(count,new LinearLayout.LayoutParams(dp(30),dp(33)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int n,boolean fromUser){
                handler.change(n);count.setText(Integer.toString(n));
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        parent.addView(line);
    }
    private void pick(String type,int request){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(type);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,request);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null)return;
        Uri uri=data.getData();
        try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}
        catch(Exception ignored){}
        if(req==PICK_AUDIO)loadAudio(uri);
        else if(req==PICK_IMAGE)loadBackground(uri);
    }
    private void loadAudio(Uri uri){
        final int version=generation.incrementAndGet();
        ready=false;durationMs=0;
        if(player!=null){player.release();player=null;}
        view.setTrack(null);playButton.setText("▶ Play");
        try{
            MediaPlayer next=new MediaPlayer();
            next.setDataSource(this,uri);next.prepare();
            next.setOnCompletionListener(p->{playButton.setText("▶ Play");view.invalidate();});
            player=next;durationMs=next.getDuration();
        }catch(Exception e){
            status.setText("Gagal membuka lagu: "+e.getMessage());
            if(player!=null){player.release();player=null;}return;
        }
        status.setText("Analisis musik: bass / kick / snare / treble…");
        worker.execute(()->{
            try{
                AudioAnalyzer.Track analysis=AudioAnalyzer.analyze(getApplicationContext(),uri);
                runOnUiThread(()->{
                    if(version!=generation.get() || isFinishing() || isDestroyed())return;
                    ready=true;view.setTrack(analysis);
                    status.setText("Siap! Tekan Play dan atur layer reaktif.");
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    if(version!=generation.get() || isFinishing() || isDestroyed())return;
                    status.setText("Analisis gagal: "+e.getMessage());
                });
            }
        });
    }
    private void loadBackground(Uri uri){
        try{
            BitmapFactory.Options bounds=new BitmapFactory.Options();
            bounds.inJustDecodeBounds=true;
            try(InputStream in=getContentResolver().openInputStream(uri)){
                BitmapFactory.decodeStream(in,null,bounds);
            }
            int scale=1;
            while(bounds.outWidth/scale>2048 || bounds.outHeight/scale>2048)scale*=2;
            BitmapFactory.Options opt=new BitmapFactory.Options();opt.inSampleSize=scale;
            Bitmap image;
            try(InputStream in=getContentResolver().openInputStream(uri)){
                image=BitmapFactory.decodeStream(in,null,opt);
            }
            if(image==null)throw new IllegalArgumentException("Gambar tidak terbaca");
            view.setBackdrop(image);status.setText("Background berhasil dipasang.");
        }catch(Exception e){status.setText("Gagal membuka gambar: "+e.getMessage());}
    }
    private void togglePlay(){
        if(player==null){toast("Impor lagu dulu");return;}
        if(!ready){toast("Analisis lagu belum selesai");return;}
        try{
            if(player.isPlaying()){player.pause();playButton.setText("▶ Play");}
            else{player.start();playButton.setText("Ⅱ Pause");}
            view.invalidate();
        }catch(Exception e){status.setText("Playback gagal: "+e.getMessage());}
    }
    private void readPreset(){
        view.setPortrait(settings.getBoolean("portrait",true));
        int[] mapping={2,1,4,0};
        for(int i=0;i<4;i++){
            view.setEnabled(i,settings.getBoolean("enabled"+i,true));
            view.setSource(i,settings.getInt("source"+i,mapping[i]));
            view.setSize(i,settings.getInt("size"+i,55));
            view.setDensity(i,settings.getInt("density"+i,60));
            view.setStrength(i,settings.getInt("strength"+i,80));
        }
    }
    private void savePreset(){
        SharedPreferences.Editor edit=settings.edit();
        edit.putBoolean("portrait",view.isPortrait());
        for(int i=0;i<4;i++){
            edit.putBoolean("enabled"+i,view.isEnabled(i));
            edit.putInt("source"+i,view.getSource(i));
            edit.putInt("size"+i,view.getSize(i));
            edit.putInt("density"+i,view.getDensity(i));
            edit.putInt("strength"+i,view.getStrength(i));
        }
        edit.apply();toast("Preset tersimpan di HP");
    }
    private void savePng(){
        Bitmap bitmap=null;
        try{
            int w=view.isPortrait()?720:1280,h=view.isPortrait()?1280:720;
            bitmap=view.snapshot(w,h);
            String name="Ranneki_"+System.currentTimeMillis()+".png";
            if(Build.VERSION.SDK_INT>=29){
                ContentValues values=new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME,name);
                values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/RannekiStudio");
                Uri dst=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
                if(dst==null)throw new IllegalStateException("Galeri tidak tersedia");
                try(OutputStream out=getContentResolver().openOutputStream(dst)){
                    if(out==null || !bitmap.compress(Bitmap.CompressFormat.PNG,100,out))
                        throw new IllegalStateException("Gagal menulis PNG");
                }
            }else{
                File folder=new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES),"RannekiStudio");
                if(!folder.exists() && !folder.mkdirs())throw new IllegalStateException("Gagal membuat folder");
                try(OutputStream out=new FileOutputStream(new File(folder,name))){
                    bitmap.compress(Bitmap.CompressFormat.PNG,100,out);
                }
            }
            toast("Frame PNG berhasil disimpan");
        }catch(Exception e){status.setText("Gagal simpan PNG: "+e.getMessage());}
        finally{if(bitmap!=null && !bitmap.isRecycled())bitmap.recycle();}
    }
    private static String time(int ms){
        int s=Math.max(0,ms/1000);
        return String.format(java.util.Locale.US,"%02d:%02d",s/60,s%60);
    }
    private LinearLayout column(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;
    }
    private LinearLayout row(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;
    }
    private TextView label(String text,int sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(sp);t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private Button addButton(LinearLayout row,String text,View.OnClickListener listener){
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(text);b.setTextSize(11);b.setTextColor(Color.WHITE);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);
        b.setPadding(dp(8),0,dp(8),0);b.setBackground(rounded(0xff252c48,9));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(34));
        lp.setMargins(dp(2),0,dp(3),0);
        row.addView(b,lp);b.setOnClickListener(listener);return b;
    }
    private GradientDrawable rounded(int color,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);
        d.setCornerRadius(dp(radius));d.setStroke(dp(1),0xff303953);return d;
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    @Override protected void onPause(){
        super.onPause();
        try{if(player!=null)player.pause();}catch(Exception ignored){}
        if(playButton!=null)playButton.setText("▶ Play");
    }
    @Override protected void onDestroy(){
        generation.incrementAndGet();ui.removeCallbacks(ticker);worker.shutdownNow();
        if(player!=null){player.release();player=null;}
        super.onDestroy();
    }
}
