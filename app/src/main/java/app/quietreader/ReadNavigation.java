package app.quietreader;

/** Reject old completion callbacks/snapshots while a replacement source is navigating. */
final class ReadNavigation {
    private String pending="";
    private long revision;
    void begin(String url){pending=url;revision++;}
    long revision(){return revision;}
    boolean pending(){return !pending.isEmpty();}
    boolean ready(String callback,String current){
        if(callback==null||callback.isEmpty()||!callback.equals(current))return false;
        if(pending()&&!pending.equals(callback))return false;
        pending="";return true;
    }
    boolean current(long observed){return !pending()&&revision==observed;}
}
