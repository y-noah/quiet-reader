package app.quietreader;

import org.json.JSONObject;

/** A bounded position within one rendered block; independent of screen width and text wrapping. */
final class ReadingViewport {
    final String id;final double fraction,gap;
    private ReadingViewport(String id,double fraction,double gap){this.id=id;this.fraction=fraction;this.gap=gap;}
    JSONObject encode()throws org.json.JSONException{return new JSONObject().put("id",id).put("fraction",fraction).put("gap",gap);}
    ReadingViewport at(String target){try{return parse(new JSONObject().put("id",target).put("fraction",fraction).put("gap",gap).toString());}catch(Exception invalid){return null;}}
    static ReadingViewport parse(String value){
        try{
            JSONObject object=new JSONObject(value);String id=object.getString("id");double fraction=object.getDouble("fraction"),gap=object.optDouble("gap",0);
            if(id.length()>512||!id.matches("reading-[A-Za-z0-9_-]+")||!Double.isFinite(fraction)||fraction<0||fraction>1||!Double.isFinite(gap)||gap<0||gap>10000)return null;
            return new ReadingViewport(id,fraction,gap);
        }catch(Exception invalid){return null;}
    }
    int destination(int current,double top,double height,float scale,int fallback){
        if(!Double.isFinite(top)||!Double.isFinite(height)||height<=0||!Float.isFinite(scale)||scale<=0)return fallback;
        double target=current+(top+height*fraction-gap)*scale;
        return (int)Math.max(0,Math.min(Integer.MAX_VALUE,Math.round(target)));
    }
}
