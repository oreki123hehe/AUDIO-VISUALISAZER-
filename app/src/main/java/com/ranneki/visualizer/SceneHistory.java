package com.ranneki.visualizer;

/** Bounded project history; media stays referenced by URI, never duplicated in history. */
final class SceneHistory {
    private final java.util.ArrayList<String> states=new java.util.ArrayList<>();
    private int index=-1;
    void record(String state){
        if(index>=0&&states.get(index).equals(state))return;
        while(states.size()>index+1)states.remove(states.size()-1);
        states.add(state);if(states.size()>30)states.remove(0);index=states.size()-1;
    }
    String undo(){return index>0?states.get(--index):null;}
    String redo(){return index+1<states.size()?states.get(++index):null;}
}
