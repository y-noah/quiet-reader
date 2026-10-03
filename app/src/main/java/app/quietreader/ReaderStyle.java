package app.quietreader;

/** Small validated preferences, shared by native chrome and the script-free reader. */
final class ReaderStyle {
    final int palette,accent,spacing,face;
    ReaderStyle(int palette,int accent,int spacing,int face){this.palette=bound(palette,2);this.accent=bound(accent,3);this.spacing=bound(spacing,2);this.face=bound(face,1);}
    static ReaderStyle defaults(){return new ReaderStyle(0,0,1,0);}
    private static int bound(int value,int max){return Math.max(0,Math.min(max,value));}
    String background(boolean dark){return dark?new String[]{"#1f2025","#25221f","#101112"}[palette]:new String[]{"#fafafa","#f7f0e3","#ffffff"}[palette];}
    String ink(boolean dark){return dark?new String[]{"#cdcfd5","#dfd5c7","#eeeeee"}[palette]:new String[]{"#30323a","#40382e","#222222"}[palette];}
    String accent(boolean dark){return (dark?new String[]{"#77b8eb","#88c9ad","#e7bd7c","#b8a9ec"}:new String[]{"#247bc1","#277456","#8a5919","#6f54a1"})[accent];}
    String lineHeight(){return new String[]{"1.55","1.8","2.05"}[spacing];}
    String css(boolean dark){return ":root{--bg:"+background(dark)+";--ink:"+ink(dark)+";--green:"+accent(dark)+"}.part p{line-height:"+lineHeight()+"}p,.preview{font-family:"+(face==1?"serif":"system-ui,sans-serif")+"}";}
}
