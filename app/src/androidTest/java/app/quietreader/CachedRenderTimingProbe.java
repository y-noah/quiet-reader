package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import static app.quietreader.Models.*;

/** Separate visible cached text from the document's load event, with real source bodies. */
final class CachedRenderTimingProbe {
    private final Instrumentation test;private Repository repo;private MainActivity reader;
    CachedRenderTimingProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private Object field(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(reader);}catch(Exception e){throw new IllegalStateException(e);}}
    void run(){int failures=0;StringBuilder report=new StringBuilder();
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")||!Build.HARDWARE.equals("ranchu"))throw new IllegalStateException("Isolated emulator only");
            ui(()->repo=new Repository(test.getTargetContext()));
            for(String[] target:new String[][]{{"118317","社区速递 164 | 1500 元圆梦昔日卡皇与派友们做的 AI 平替"},{"118122","TDS REVIEW｜Apple AirPods 5 半入耳降噪真无线耳机体验"}}){
                Item item=new Item(Source.SSPAI,target[1],"https://sspai.com/post/"+target[0],"");
                CountDownLatch prepared=new CountDownLatch(1);Document[] doc={null};
                ui(()->repo.article(item,new Repository.Result<Document>(){public void success(Document value){doc[0]=value;prepared.countDown();}public void failure(String message){prepared.countDown();}}));
                if(!prepared.await(20,TimeUnit.SECONDS)||doc[0]==null||!doc[0].hasContent())throw new IllegalStateException("Source body preparation failed");
                String marker=doc[0].blocks.stream().filter(b->b.type.equals("text")&&b.value.length()>=20).findFirst().orElseThrow().value.substring(0,20);
                for(int repeat=1;repeat<=2;repeat++){
                    long start=SystemClock.elapsedRealtime();reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("discovery_item",item.json().toString()).putExtra("direct_reader",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    long textMs=-1,visualMs=-1,completeMs=-1;JSONObject first=null,last=null;
                    while(SystemClock.elapsedRealtime()-start<25000){
                        WebView[] web={null};String[] page={""};ui(()->{web[0]=(WebView)field("readerWeb");page[0]=(String)field("readerPageUrl");});
                        if(web[0]!=null){CountDownLatch sampled=new CountDownLatch(1);String[] value={""};
                            String script="JSON.stringify({valid:location.href==="+JSONObject.quote(page[0])+",text:!!document.body&&document.body.innerText.includes("+JSONObject.quote(marker)+"),state:document.readyState,pending:[...document.images].filter(i=>!i.complete).length,emoji:document.querySelectorAll('img.emoji').length})";
                            ui(()->{boolean enabled=web[0].getSettings().getJavaScriptEnabled();web[0].getSettings().setJavaScriptEnabled(true);web[0].evaluateJavascript(script,result->{value[0]=result;web[0].getSettings().setJavaScriptEnabled(enabled);sampled.countDown();});});
                            if(sampled.await(2,TimeUnit.SECONDS)){last=new JSONObject((String)new JSONTokener(value[0]).nextValue());if(last.optBoolean("valid")&&last.optBoolean("text")){
                                if(textMs<0){textMs=SystemClock.elapsedRealtime()-start;first=last;CountDownLatch shown=new CountDownLatch(1);long[] at={0};ui(()->web[0].postVisualStateCallback(1,new WebView.VisualStateCallback(){public void onComplete(long id){at[0]=SystemClock.elapsedRealtime();shown.countDown();}}));if(shown.await(3,TimeUnit.SECONDS))visualMs=at[0]-start;}
                                if(last.optString("state").equals("complete")){completeMs=SystemClock.elapsedRealtime()-start;break;}
                            }}
                        }Thread.sleep(25);
                    }
                    String line="TIMING target="+target[0]+" repeat="+repeat+" textMs="+textMs+" visualMs="+visualMs+" completeMs="+completeMs+" first="+first+" last="+last;report.append(line).append('\n');Bundle status=new Bundle();status.putString("stream",line+"\n");test.sendStatus(1,status);
                    if(textMs<0||visualMs<0||completeMs<0)failures++;
                    WebView[] owned={null};ui(()->owned[0]=(WebView)field("readerWeb"));CountDownLatch guarded=new CountDownLatch(1);boolean[] blocked={false};
                    ui(()->owned[0].evaluateJavascript("(()=>{window.__qrSourceScript=0;const s=document.createElement('script');s.textContent='window.__qrSourceScript=1';document.body.appendChild(s);const b=document.createElement('button');b.setAttribute('onclick','window.__qrSourceScript=2');document.body.appendChild(b);b.click();b.remove();s.remove();return window.__qrSourceScript===0})()",value->{blocked[0]="true".equals(value);guarded.countDown();}));
                    if(!guarded.await(3,TimeUnit.SECONDS)||!blocked[0])failures++;
                    report.append("CSP scriptAndHandlerBlocked=").append(blocked[0]).append('\n');
                    ui(reader::finish);reader=null;test.waitForIdleSync();
                }
            }
        }catch(Throwable error){failures++;report.append(android.util.Log.getStackTraceString(error));}
        finally{ui(()->{if(reader!=null)reader.finish();if(repo!=null)repo.close();});}
        Bundle result=new Bundle();result.putString("stream",report+"failures="+failures+"\n");test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
