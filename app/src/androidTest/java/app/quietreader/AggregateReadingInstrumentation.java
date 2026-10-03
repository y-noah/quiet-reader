package app.quietreader;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebView;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Live diagnostic: observed boards only, no synthetic documents, no account operations.
 * Reflection observes state and opens supplemental source samples through the actual reader.
 * The Top50 pass uses real production card click handlers; CSS inspection is read-only.
 */
public final class AggregateReadingInstrumentation extends Instrumentation {
    private MainActivity activity;private File output;private final JSONArray results=new JSONArray();
    private SharedPreferences appearance;private boolean hadMode;private int oldMode;private int sequence;
    private final Map<Source,Item> readable=new EnumMap<>(Source.class);
    private String only="";private int limit=50;
    interface Checked{void run()throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null){only=args.getString("source","");limit=Integer.parseInt(args.getString("limit","50"));}start();}
    @Override public void callActivityOnResume(Activity a){super.callActivityOnResume(a);if(a instanceof MainActivity)activity=(MainActivity)a;}
    private void ui(Checked work)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});waitForIdleSync();if(error[0]!=null)throw new Exception(error[0]);}
    private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private void invoke(String name,Class<?>[] types,Object... args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}
    private void launch()throws Exception{activity=(MainActivity)startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"app.quietreader.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));waitForIdleSync();}
    @Override public void onStart(){
        Bundle result=new Bundle();int code=Activity.RESULT_OK;
        try{
            output=new File(getTargetContext().getExternalFilesDir(null),"aggregate-reading/run-"+System.currentTimeMillis());if(!output.mkdirs())throw new IOException("Cannot save evidence");
            appearance=getTargetContext().getSharedPreferences("appearance",0);hadMode=appearance.contains("mode");oldMode=appearance.getInt("mode",0);
            appearance.edit().putInt("mode",2).commit();launch();aggregate(true);SystemClock.sleep(23500);shot("00-dark-aggregate");
            String[] details={""};ui(()->details[0]=(String)field("aggregateDetail"));write("sources.txt",details[0]);
            List<Item> snapshot=new ArrayList<>();ui(()->snapshot.addAll((List<Item>)field("boardItems")));write("snapshot.json",Models.toJson(snapshot).toString(2));
            int count=0;Set<Source> visited=EnumSet.noneOf(Source.class);
            for(Item item:snapshot){if(!included(item.source))continue;if(count++>=limit)break;visited.add(item.source);audit(item,"dark-top50",true);}
            // Sources outside the scored Top50 are tested separately and never called Top50 clicks.
            for(Source source:Source.aggregateSources()){
                if(!included(source))continue;if(readable.containsKey(source))continue;
                List<Item> candidates=new ArrayList<>();ui(()->candidates.addAll(((Repository)field("repo")).cached(source)));
                int tried=0;for(Item item:candidates){if(synthetic(item))continue;audit(item,"dark-source-sample",false);if(readable.containsKey(source)||++tried>=2)break;}
            }
            ui(()->{appearance.edit().putInt("mode",1).commit();activity.recreate();});SystemClock.sleep(1200);aggregate(false);SystemClock.sleep(700);shot("80-light-aggregate");
            for(Item item:new ArrayList<>(readable.values()))audit(item,"light-source-sample",false);
            write("results.json",results.toString(2));result.putString("stream","AUDIT COMPLETE "+results.length()+" observations. This is an observation report, not an all-sources-pass assertion.\nOutput: "+output.getAbsolutePath()+"\n");
        }catch(Throwable error){code=Activity.RESULT_CANCELED;try{write("fatal.txt",error.toString());}catch(Exception ignored){}result.putString("stream","AUDIT ERROR "+error+"\nOutput: "+output+"\n");}
        finally{try{if(appearance!=null){SharedPreferences.Editor e=appearance.edit();if(hadMode)e.putInt("mode",oldMode);else e.remove("mode");e.commit();}if(activity!=null)ui(()->activity.recreate());}catch(Exception ignored){}}
        finish(code,result);
    }
    private boolean synthetic(Item item){return item.title.contains("合成")||item.title.contains("测试榜单")||item.url.contains("999999999");}
    private boolean included(Source source){return only.isEmpty()||Arrays.asList(only.split(",")).contains(source.name());}
    private void aggregate(boolean refresh)throws Exception{ui(()->{set("selected",Source.AGGREGATE);invoke("home",new Class<?>[]{boolean.class},refresh);});}
    private View card(View node,String url){if(url.equals(node.getTag()))return node;if(node instanceof ViewGroup)for(int n=0;n<((ViewGroup)node).getChildCount();n++){View child=card(((ViewGroup)node).getChildAt(n),url);if(child!=null)return child;}return null;}
    private void audit(Item item,String phase,boolean fromBoard)throws Exception{
        JSONObject row=new JSONObject();row.put("sequence",++sequence);row.put("phase",phase);row.put("source",item.source.name());row.put("title",item.title);row.put("url",item.url);results.put(row);
        if(synthetic(item)){row.put("state","excluded-old-test-fixture");checkpoint(row);return;}
        String prefix=String.format(Locale.ROOT,"%02d-%s-%s",sequence,phase,item.source.name());
        try{
            aggregate(false);SystemClock.sleep(120);boolean[] clicked={false};int[] before={0};
            ui(()->{
                if(fromBoard){View target=card(activity.getWindow().getDecorView(),item.url);if(target!=null){ScrollView scroll=(ScrollView)field("scroll");scroll.scrollTo(0,Math.max(0,target.getTop()-30));before[0]=scroll.getScrollY();target.performClick();clicked[0]=true;}}
                else{invoke("open",new Class<?>[]{Item.class,boolean.class},item,true);clicked[0]=true;}
            });
            if(!clicked[0]){row.put("state","left-current-ranking");checkpoint(row);return;}
            SystemClock.sleep(200);selectFirstSource();long started=SystemClock.elapsedRealtime();
            String state="timeout";
            while(SystemClock.elapsedRealtime()-started<36000){
                Object[] values=new Object[4];ui(()->{values[0]=field("readerWeb");values[1]=field("reading");values[2]=field("current");values[3]=nativeText(activity.getWindow().getDecorView());});
                if(values[2]==null){state="filtered-video-or-returned";break;}
                Document document=(Document)values[1];
                if(values[0]!=null&&document!=null){state="reader";break;}
                if(document!=null&&!document.related.isEmpty()){state="topic-links";break;}
                String text=(String)values[3];if(text.contains("需要来源页面协助")||text.contains("暂时没读到正文")||text.contains("当前没有网络")){state="blocked";row.put("message",text);break;}
                SystemClock.sleep(250);
            }
            row.put("state",state);row.put("elapsedMs",SystemClock.elapsedRealtime()-started);
            if(state.equals("reader")){
                dom("({ready:!!document.querySelector('meta[name=quiet-reader-source]')})");
                // Activate an actual collapsed answer/section, not just its preview.
                JSONObject expanded=dom("(function(){var a=Array.from(document.querySelectorAll('a.action')).find(e=>e.textContent.includes('展开阅读'));if(a)a.click();return {expanded:!!a};})()");
                if(expanded.optBoolean("expanded")){SystemClock.sleep(500);dom("({ready:!!document.querySelector('meta[name=quiet-reader-source]')})");}
                row.put("expandedSection",expanded.optBoolean("expanded"));
                SystemClock.sleep(700);JSONObject style=dom("(function(){var p=document.querySelector('.part p:not(.preview)'),h=document.querySelector('h1'),a=document.querySelector('a.content-link'),b=getComputedStyle(document.body),s=p?getComputedStyle(p):null;return {title:h?h.textContent:'',source:document.querySelector('meta[name=quiet-reader-source]').content,bodyChars:Array.from(document.querySelectorAll('.part p:not(.preview)')).reduce((n,e)=>n+e.textContent.length,0),paragraphs:document.querySelectorAll('.part p:not(.preview)').length,bg:b.backgroundColor,ink:b.color,font:s?s.fontSize:'',family:s?s.fontFamily:'',lineHeight:s?s.lineHeight:'',link:a?getComputedStyle(a).color:'',links:document.querySelectorAll('a.content-link').length,overflow:document.documentElement.scrollWidth>innerWidth+2,images:document.images.length,loadedImages:Array.from(document.images).filter(i=>i.naturalWidth>0).length,failedImages:Array.from(document.images).filter(i=>i.complete&&!i.naturalWidth).length};})()");
                style.put("overflow",dom("({overflow:document.documentElement.scrollWidth>document.documentElement.clientWidth+2})").getBoolean("overflow"));
                row.put("render",style);row.put("screenshot",prefix+".png");shot(prefix);
                ui(()->{Document doc=(Document)field("reading");row.put("answerSections",AnswerStream.answers(doc));row.put("notice",doc.notice);row.put("scrollbarInset",((WebView)field("readerWeb")).getScrollBarStyle()==View.SCROLLBARS_INSIDE_INSET);});
                if(!phase.equals("dark-top50")&&style.optInt("images")>0){
                    SystemClock.sleep(2500);JSONObject images=dom("(function(){var a=Array.from(document.images).filter(i=>{var r=i.getBoundingClientRect();return r.bottom>0&&r.top<innerHeight});return {visible:a.length,loaded:a.filter(i=>i.naturalWidth>0).length,failed:a.filter(i=>i.complete&&!i.naturalWidth).length};})()");row.put("visibleImagesAfterWait",images);shot(prefix+"-settled");
                }
                if(style.optInt("bodyChars")>=50&&!style.optBoolean("overflow"))readable.putIfAbsent(item.source,item);
                // Observe one viewport down as well; no external link is followed or submitted.
                ui(()->{WebView web=(WebView)field("readerWeb");web.scrollTo(0,web.getHeight());});SystemClock.sleep(220);
                if(phase.equals("light-source-sample")||!phase.equals("dark-top50")){
                    SystemClock.sleep(800);
                    row.put("scrollWidths",dom("({inner:innerWidth,client:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth,body:document.body.scrollWidth,x:scrollX,visual:visualViewport.width,scale:visualViewport.scale,wide:Array.from(document.querySelectorAll('main,.part,p,figure,img,figcaption')).filter(e=>e.getBoundingClientRect().right>document.documentElement.clientWidth+2).slice(0,5).map(e=>({tag:e.tagName,right:e.getBoundingClientRect().right}))})"));
                    shot(prefix+"-scroll");row.put("scrollScreenshot",prefix+"-scroll.png");
                }
            }else{SystemClock.sleep(700);row.put("screenshot",prefix+".png");shot(prefix);if(state.equals("topic-links")){readable.putIfAbsent(item.source,item);if(phase.contains("source-sample"))inspectTopicPost(row,prefix);}}
            ui(()->{if(field("current")!=null)activity.onBackPressed();});SystemClock.sleep(220);
            boolean[] returned={false};int[] after={0};ui(()->{returned[0]=field("current")==null&&field("selected")==Source.AGGREGATE;after[0]=((ScrollView)field("scroll")).getScrollY();});row.put("returnedAggregate",returned[0]);if(fromBoard){row.put("scrollBefore",before[0]);row.put("scrollAfter",after[0]);}
        }catch(Throwable error){row.put("state","test-or-app-error");row.put("error",error.toString());try{shot(prefix+"-error");}catch(Exception ignored){}}
        checkpoint(row);
    }
    private void selectFirstSource(){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root==null)return;if(root.findAccessibilityNodeInfosByText("选择阅读来源").isEmpty())return;for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(" · ")){AccessibilityNodeInfo target=node;while(target!=null&&!target.isClickable())target=target.getParent();if(target!=null){target.performAction(AccessibilityNodeInfo.ACTION_CLICK);break;}}}
    private void inspectTopicPost(JSONObject row,String prefix)throws Exception{
        Item[] child={null};ui(()->{Document doc=(Document)field("reading");child[0]=doc.related.get(0);View link=card(activity.getWindow().getDecorView(),child[0].url);if(link==null)throw new IOException("Topic link missing");link.performClick();});
        JSONObject nested=new JSONObject();nested.put("url",child[0].url);row.put("nested",nested);long until=SystemClock.elapsedRealtime()+36000;
        while(SystemClock.elapsedRealtime()<until){
            Object[] state=new Object[3];ui(()->{state[0]=field("readerWeb");state[1]=field("reading");state[2]=nativeText(activity.getWindow().getDecorView());});
            if(state[0]!=null&&state[1]!=null){
                nested.put("state","reader");nested.put("render",dom("(function(){var b=getComputedStyle(document.body);return {chars:Array.from(document.querySelectorAll('.part p')).reduce((n,p)=>n+p.textContent.length,0),title:document.querySelector('h1').textContent,bg:b.backgroundColor,ink:b.color,overflow:document.documentElement.scrollWidth>innerWidth+2};})()"));break;
            }
            if(((String)state[2]).contains("需要来源页面协助")||((String)state[2]).contains("暂时没读到正文")){nested.put("state","blocked");nested.put("message",state[2]);break;}SystemClock.sleep(300);
        }
        if(!nested.has("state"))nested.put("state","timeout");SystemClock.sleep(1000);shot(prefix+"-nested");nested.put("screenshot",prefix+"-nested.png");ui(()->activity.onBackPressed());SystemClock.sleep(200);
    }
    private JSONObject dom(String expression)throws Exception{
        for(int attempt=0;attempt<20;attempt++){
            String[] out={null};CountDownLatch done=new CountDownLatch(1);
            ui(()->{WebView web=(WebView)field("readerWeb");web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("(function(){try{if(!document.querySelector('meta[name=quiet-reader-source]'))return null;return JSON.stringify("+expression+");}catch(e){return null;}})()",raw->{web.getSettings().setJavaScriptEnabled(false);out[0]=raw;done.countDown();});});
            if(!done.await(5,TimeUnit.SECONDS))throw new IOException("DOM timeout");Object decoded=new JSONTokener(out[0]).nextValue();if(decoded instanceof String)return new JSONObject((String)decoded);SystemClock.sleep(300);
        }
        throw new IOException("Reader document not ready after bounded wait");
    }
    private String nativeText(View view){StringBuilder text=new StringBuilder();if(view instanceof TextView)text.append(((TextView)view).getText()).append('\n');if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)text.append(nativeText(((ViewGroup)view).getChildAt(n)));return text.toString();}
    private void shot(String name)throws Exception{waitForIdleSync();Bitmap image=getUiAutomation().takeScreenshot();if(image==null)throw new IOException("Screenshot unavailable");try(FileOutputStream out=new FileOutputStream(new File(output,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    private void write(String name,String value)throws Exception{try(FileOutputStream out=new FileOutputStream(new File(output,name))){out.write(value.getBytes(StandardCharsets.UTF_8));}}
    private void checkpoint(JSONObject row)throws Exception{write("results.json",results.toString(2));Bundle progress=new Bundle();progress.putString("stream","OBSERVE "+row.optInt("sequence")+" "+row.optString("phase")+" "+row.optString("source")+" "+row.optString("state")+"\n");sendStatus(0,progress);}
}
