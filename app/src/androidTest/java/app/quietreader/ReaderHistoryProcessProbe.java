package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.WebView;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Two actual processes, persisted history and a changed app reading font. No source network. */
final class ReaderHistoryProcessProbe {
    private final Instrumentation test;private final boolean prepare;private final String id;
    private final String variant;
    private MainActivity reader;private int checks,failures;private final StringBuilder report=new StringBuilder();
    ReaderHistoryProcessProbe(Instrumentation test,boolean prepare,String id,String variant){this.test=test;this.prepare=prepare;this.id=id;this.variant=variant;}
    private Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception error){throw new IllegalStateException(error);}}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private void check(boolean okay,String label){checks++;if(!okay)failures++;report.append(okay?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(String label,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(true){boolean[] ready={false};ui(()->ready[0]=condition.getAsBoolean());if(ready[0])return;if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("History process deadline: "+label);Thread.sleep(40);}}
    private String js(String script)throws Exception{WebView web=(WebView)field(reader,"readerWeb");CountDownLatch done=new CountDownLatch(1);String[] value={""};ui(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(script,result->{value[0]=result;web.getSettings().setJavaScriptEnabled(false);done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("History DOM missing");return value[0];}
    private JSONObject position()throws Exception{return new JSONObject(js("(()=>{for(const a of document.querySelectorAll('[data-reading-anchor]')){let r=a.getBoundingClientRect();if(r.height>0&&r.bottom>0&&r.top<=1)return {id:a.id,fraction:-r.top/r.height,y:scrollY}}return {id:'',fraction:0,y:scrollY}})()"));}
    void run(){Context context=test.getTargetContext();android.content.SharedPreferences prefs=context.getSharedPreferences("history-process-"+id,0),appearance=context.getSharedPreferences("MainActivity",0);String url="https://www.thepaper.cn/newsDetail_forward_"+id;Item item=new Item(Source.THEPAPER,"跨进程段落位置合成样本",url,"");
        java.util.Map<String,?> historyBackup=null;int startFont="compact".equals(variant)?25:17,endFont="compact".equals(variant)?17:25;boolean refresh="refresh".equals(variant);
        try{
            if(prepare){prefs.edit().putBoolean("hadFont",appearance.contains("font")).putInt("font",appearance.getInt("font",19)).commit();appearance.edit().putInt("font",startFont).commit();
                Document doc=new Document();doc.url=url;doc.title=item.title;Section body=new Section("history-main","正文",false);for(int n=0;n<24;n++)body.blocks.add(new Block("text","第"+n+"段："+"跨进程重新排版后仍应恢复同一段落进度，不能把原字号的像素位置直接用于新字号。".repeat(12)));doc.sections.add(body);doc.blocks.addAll(body.blocks);ArticleVersion.capture(item,doc);ui(()->{Repository repo=new Repository(context);repo.cacheArticle(doc);repo.close();});
            }else{check(prefs.contains("position"),"preparation evidence exists before restore");check(prefs.getInt("pid",-1)!=android.os.Process.myPid(),"restore is in a different actual Android process");appearance.edit().putInt("font",endFont).commit();
                if(refresh){android.content.SharedPreferences histories=context.getSharedPreferences("reading-history",0);historyBackup=histories.getAll();Document fresh=ReadingHistory.restore(new ReadingHistory(context).get(url));
                    check(new ReadingHistory(context).clearBodies(),"production history body cleanup completes");JSONObject marker=new JSONObject(histories.getString(url,"{}"));check(!marker.has("document")&&marker.has("viewport"),"history body cleanup preserves the semantic marker independently of the old body");
                    android.content.SharedPreferences.Editor restore=histories.edit();for(java.util.Map.Entry<String,?> entry:historyBackup.entrySet())if(!entry.getKey().equals(url)&&entry.getValue() instanceof String)restore.putString(entry.getKey(),(String)entry.getValue());restore.commit();
                    fresh.sections.get(0).blocks.add(0,new Block("text","新取得正文在前方增加的一段。"));fresh.blocks.clear();for(Section section:fresh.sections)fresh.blocks.addAll(section.blocks);ui(()->{new ArticleSession(context,item).stamp(fresh);Repository repo=new Repository(context);repo.cacheArticle(fresh);repo.close();});
                }
            }
            reader=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));await("reading document",()->field(reader,"reading")!=null&&field(reader,"readerWeb")!=null);
            long readyBy=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<readyBy){if("true".equals(js("document.readyState==='complete'&&!!document.querySelector('[data-reading-anchor]')")))break;Thread.sleep(40);}Thread.sleep(600);
            if(prepare){String anchor=ReaderHtml.anchorId("history-main",8);js("(()=>{let a=document.getElementById('"+anchor+"'),r=a.getBoundingClientRect();scrollTo(0,scrollY+r.top+r.height*.35)})()");Thread.sleep(650);JSONObject position=position();report.append("prepare=").append(position).append('\n');check(anchor.equals(position.optString("id"))&&position.optInt("y")>0,"preparation reaches the middle of a known noninitial paragraph");
                prefs.edit().putString("position",position.toString()).putInt("pid",android.os.Process.myPid()).commit();
                try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand("input keyevent 3");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] bytes=new byte[512];while(in.read(bytes)!=-1){}}
                await("actual background before process stop",()->(boolean)field(reader,"wasStopped"));JSONObject saved=new JSONObject(context.getSharedPreferences("reading-history",0).getString(url,"{}"));check(saved.optInt("position")>0&&saved.has("document"),"actual onPause persists the prepared body and nonzero position");check(saved.optJSONObject("viewport")!=null,"history also persists a validated semantic reading marker");
            }else{JSONObject before=new JSONObject(prefs.getString("position","{}")),after=position();report.append("restore=").append(after).append('\n');String expected=refresh?ReaderHtml.anchorId("history-main",9):before.optString("id");check(expected.equals(after.optString("id"))&&Math.abs(before.optDouble("fraction")-after.optDouble("fraction"))<.08,"process restart with changed font restores the same content and within-paragraph progress");check((int)field(reader,"font")==endFont,"restoration uses a genuinely changed app reading font");check(field(reader,"dynamic")==null&&field(reader,"pendingArticle")==null&&ArticleVersion.matches(item,(Document)field(reader,"reading")),"current-session prepared history or fresh body restores without a source acquisition");check("true".equals(js("document.documentElement.scrollWidth<=innerWidth+1")),"restored reader fits its viewport");}
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{ui(()->{if(reader!=null)reader.finish();});test.waitForIdleSync();if(!prepare){android.content.SharedPreferences.Editor edit=appearance.edit();if(prefs.getBoolean("hadFont",false))edit.putInt("font",prefs.getInt("font",19));else edit.remove("font");edit.commit();prefs.edit().clear().commit();android.content.SharedPreferences.Editor histories=context.getSharedPreferences("reading-history",0).edit().remove(url);if(historyBackup!=null)for(java.util.Map.Entry<String,?> entry:historyBackup.entrySet())if(!entry.getKey().equals(url)&&entry.getValue() instanceof String)histories.putString(entry.getKey(),(String)entry.getValue());histories.commit();}}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
