package com.ranneki.visualizer;
public final class SceneHistoryTest {
    public static void main(String[] args){
        SceneHistory h=new SceneHistory();h.record("a");h.record("b");h.record("b");
        if(!"a".equals(h.undo())||!"b".equals(h.redo()))throw new AssertionError("History navigation");
        h.undo();h.record("c");if(h.redo()!=null)throw new AssertionError("Redo after divergent edit");
        for(int i=0;i<50;i++)h.record("state"+i);
        int n=0;while(h.undo()!=null)n++;if(n!=29)throw new AssertionError("History bound");
        System.out.println("PASS: undo/redo, coalescing, branch truncation and history bound");
    }
}
