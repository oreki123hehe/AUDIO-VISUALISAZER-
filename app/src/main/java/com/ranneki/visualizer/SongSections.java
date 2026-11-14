package com.ranneki.visualizer;
import java.util.*;

/** Editable energy/novelty estimates, not guaranteed musical labels. */
final class SongSections {
    static final String[] NAMES={"Intro","Verse / tenang","Build","Drop / ramai","Outro"};
    static final class Section {long start,end;int type;Section(long a,long b,int t){start=a;end=b;type=t;}}
    final ArrayList<Section> sections=new ArrayList<>();
    static SongSections detect(long[] time,float[] energy,float[] bass,int bpm){
        SongSections out=new SongSections();if(time.length==0)return out;
        long duration=time[time.length-1]+30;int n=(int)(duration/1000)+1;
        float[] e=new float[n],low=new float[n];int[] count=new int[n];
        for(int i=0;i<time.length;i++){int k=(int)Math.min(n-1,time[i]/1000);e[k]+=energy[i];low[k]+=bass[i];count[k]++;}
        float mean=0;for(int i=0;i<n;i++){e[i]/=Math.max(1,count[i]);low[i]/=Math.max(1,count[i]);mean+=e[i]/n;}
        int span=Math.max(4,bpm>0?Math.round(240f/bpm*4):8);int last=-1;
        for(int start=0;start<n;start+=span){int end=Math.min(n,start+span);float now=average(e,start,end),before=average(e,Math.max(0,start-span),start),after=average(e,end,Math.min(n,end+span));
            int type=start==0?0:end==n&&now<mean*.9f?4:now>mean*1.08f&&average(low,start,end)>.35f?3:after>now*1.18f&&now>=before*.9f?2:1;
            if(type==last)out.sections.get(out.sections.size()-1).end=Math.min(duration,end*1000L);
            else out.sections.add(new Section(start*1000L,Math.min(duration,end*1000L),type));last=type;
        }return out;
    }
    private static float average(float[] x,int a,int b){float sum=0;for(int i=a;i<b;i++)sum+=x[i];return sum/Math.max(1,b-a);}
    float intensity(long ms){float[] levels={.45f,.7f,.9f,1.3f,.4f};for(int i=0;i<sections.size();i++){Section s=sections.get(i);if(ms>=s.start&&ms<s.end){float prev=i==0?levels[s.type]:levels[sections.get(i-1).type];float t=Math.min(1,(ms-s.start)/650f);t=t*t*(3-2*t);return prev+(levels[s.type]-prev)*t;}}return 1;}
    String label(long ms){for(Section s:sections)if(ms>=s.start&&ms<s.end)return NAMES[s.type];return "";}
    String encode(){StringBuilder b=new StringBuilder();for(Section s:sections){if(b.length()>0)b.append(';');b.append(s.start).append(',').append(s.end).append(',').append(s.type);}return b.toString();}
    static SongSections decode(String text){SongSections out=new SongSections();if(text==null||text.isEmpty())return out;long end=0;for(String line:text.split(";")){String[] p=line.split(",");if(p.length!=3||out.sections.size()>=1000)throw new IllegalArgumentException("Bagian lagu invalid");Section s=new Section(Long.parseLong(p[0]),Long.parseLong(p[1]),Integer.parseInt(p[2]));if(s.start<end||s.end<=s.start||s.type<0||s.type>=5)throw new IllegalArgumentException("Bagian lagu invalid");out.sections.add(s);end=s.end;}return out;}
}
