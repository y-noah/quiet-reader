package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Synthetic presentation journey: real activity, native clicks and reader WebView.
 * Injects parsed documents at the presentation boundary; does not claim live platform/API success.
 * No cookies, credentials, or source storage are inspected.
 */
public final class UserJourneyRepairInstrumentation extends Instrumentation {
    private MainActivity activity; private File output; private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    private static final String URL="https://www.zhihu.com/question/999999991";
    interface Task {void run()throws Exception;}
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    private void ui(Task action)throws Exception {Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception(error[0]);}
    private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private void call(String name,Class<?>[] types,Object... args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}
    private void check(boolean value,String name){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(name).append('\n');}
    private void quiet()throws Exception{((Repository)field("repo")).cancelPending();((AggregateLoader)field("aggregateLoader")).cancel();((ArticlePreloader)field("preloader")).pause();((Handler)field("preloadHandler")).removeCallbacksAndMessages(null);}
    private View find(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),text);if(found!=null)return found;}return null;}
    private String nativeText(View view){StringBuilder s=new StringBuilder();if(view instanceof TextView)s.append(((TextView)view).getText()).append('\n');if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)s.append(nativeText(((ViewGroup)view).getChildAt(i)));return s.toString();}
    private JSONObject dom()throws Exception{
        for(int i=0;i<30;i++){
            CountDownLatch latch=new CountDownLatch(1);String[] raw={"null"};
            ui(()->{WebView w=(WebView)field("readerWeb");w.getSettings().setJavaScriptEnabled(true);w.evaluateJavascript("JSON.stringify({ready:!!document.querySelector('meta[name=quiet-reader-source]'),body:document.body?document.body.innerText:'',parts:document.querySelectorAll('section.part').length,overflow:document.documentElement.scrollWidth>document.documentElement.clientWidth+2})",s->{raw[0]=s;w.getSettings().setJavaScriptEnabled(false);latch.countDown();});});
            if(latch.await(2,TimeUnit.SECONDS)){Object decoded=new JSONTokener(raw[0]).nextValue();if(decoded instanceof String){JSONObject d=new JSONObject((String)decoded);if(d.optBoolean("ready"))return d;}}
            Thread.sleep(100);
        }throw new IOException("Reader DOM not ready");
    }
    private void shot(String name)throws Exception{Thread.sleep(2400);Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap==null)throw new IOException("Screenshot missing");try(FileOutputStream out=new FileOutputStream(new File(output,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
    private JSONObject anchor(String phase)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);String[] raw={"null"};float[] scale={0};int[] scroll={0},webTop={0};
        ui(()->{WebView w=(WebView)field("readerWeb");scale[0]=w.getScale();scroll[0]=w.getScrollY();int[] location=new int[2];w.getLocationOnScreen(location);webTop[0]=location[1];w.getSettings().setJavaScriptEnabled(true);w.evaluateJavascript("(function(){var a=document.querySelectorAll('[data-reading-anchor]');for(var i=0;i<a.length;i++){var r=a[i].getBoundingClientRect();if(r.bottom>0&&r.top<innerHeight)return JSON.stringify({id:a[i].id,top:r.top,bottom:r.bottom,text:a[i].innerText.slice(0,80)});}return null;})()",s->{raw[0]=s;w.getSettings().setJavaScriptEnabled(false);latch.countDown();});});
        if(!latch.await(3,TimeUnit.SECONDS))throw new IOException("Anchor observation timed out");Object decoded=new JSONTokener(raw[0]).nextValue();if(!(decoded instanceof String))throw new IOException("No visible reading anchor");JSONObject value=new JSONObject((String)decoded);value.put("scale",scale[0]);value.put("scrollY",scroll[0]);value.put("webTop",webTop[0]);report.append("OBSERVE ").append(phase).append(" ").append(value).append('\n');return value;
    }
    private Document fixture(int count){Document d=new Document();d.title="合成使用者回归 · 非真实知乎内容";d.url=URL;for(int i=1;i<=count;i++){Section s=new Section("synthetic-answer-"+i,"合成回答 "+i,true);for(int p=0;p<6;p++)s.blocks.add(new Block("text","SYNTHETIC_BODY_"+i+" 这是独立使用者检查用的合成文字，不代表平台内容。第 "+p+" 段用于验证继续阅读和滚动位置保持。"));d.sections.add(s);d.blocks.addAll(s.blocks);}return d;}
    private void show(Item item,Document document)throws Exception{ui(()->{quiet();call("closeAnswers",new Class<?>[0]);set("current",item);set("primaryPending",true);call("frame",new Class<?>[]{String.class,String.class},item.source.label,"news");call("scroller",new Class<?>[0]);call("render",new Class<?>[]{Document.class},document);});}
    private void navigation()throws Exception{
        ui(()->{check(field("selected")==Source.AGGREGATE,"fresh activity opens total ranking");quiet();LinearLayout row=(LinearLayout)field("platformNavigation");check(row!=null&&row.getChildCount()==6,"six navigation targets");String[] expected={"总榜","知乎","微博","虎扑","财联社","爱范儿"};for(int i=0;i<expected.length;i++){View v=row.getChildAt(i);check(expected[i].contentEquals(v.getContentDescription()),"navigation order "+expected[i]);check(v.getWidth()>0&&Math.abs(v.getWidth()-row.getWidth()/6)<=2,"equal visible navigation width "+expected[i]);}check(find(activity.getWindow().getDecorView(),"什么值得买")==null,"removed shopping source absent from home");});shot("01-total-ranking-six-tabs");
        ui(()->{find(activity.getWindow().getDecorView(),"微博").performClick();check(field("selected")==Source.WEIBO,"native Weibo navigation click");quiet();find(activity.getWindow().getDecorView(),"总榜").performClick();quiet();check(field("selected")==Source.AGGREGATE,"native return to total ranking");});
    }
    private void continuation()throws Exception{
        Document before=fixture(2);show(new Item(Source.ZHIHU,before.title,URL,"合成"),before);JSONObject first=dom();check(first.optString("body").contains("SYNTHETIC_BODY_1")&&first.optInt("parts")==2,"initial synthetic answers visibly rendered");
        CountDownLatch expandedClick=new CountDownLatch(1);ui(()->{WebView w=(WebView)field("readerWeb");w.getSettings().setJavaScriptEnabled(true);w.evaluateJavascript("(function(){var a=document.querySelector('a[href*=\"toggle?index=0\"]');if(a)a.click();return !!a;})()",s->{w.getSettings().setJavaScriptEnabled(false);check("true".equals(s),"real reader expand link clicked");expandedClick.countDown();});});check(expandedClick.await(3,TimeUnit.SECONDS),"expand action completed");Thread.sleep(450);JSONObject expanded=dom();check(expanded.optString("body").contains("第 5 段"),"expanded full answer body visible in DOM");
        ui(()->{set("suppressAutoUntil",System.currentTimeMillis()+60000);TextView status=(TextView)field("readerStatus");status.setText("正在加载下一批回答…你可以继续阅读已加载内容");status.setVisibility(View.VISIBLE);set("loadingMore",true);});Thread.sleep(250);
        ui(()->{WebView w=(WebView)field("readerWeb");w.scrollTo(0,500);check(w.getScrollY()>100,"reading position is genuinely scrolled before append");});Thread.sleep(150);JSONObject beforeAnchor=anchor("before-append");shot("02a-before-append-anchor");
        Document last=fixture(3);last.moreStatus="end";last.notice="来源已返回本次可见回答的末页，共保留 3 条。登录状态或来源内容变化后可重试。";
        ui(()->call("appendAtReadingPosition",new Class<?>[]{Document.class,int.class,Set.class},last,(int)field("generation"),AnswerStream.answerIds(before)));
        Thread.sleep(900);JSONObject end=dom();check(end.optInt("parts")==3&&end.optString("body").contains("合成回答 3"),"new final-page answer present in actual reader DOM");check(end.optString("body").contains("第 5 段"),"existing expanded answer remains expanded after append");check(end.optString("body").contains("重新检查后续回答")&&end.optString("body").contains("末页"),"final-page explanation and retry in reader");check(!end.optBoolean("overflow"),"no horizontal reader overflow");
        JSONObject afterAnchor=anchor("after-append");check(beforeAnchor.getString("id").equals(afterAnchor.getString("id")),"continuation keeps same first visible paragraph");check(Math.abs(beforeAnchor.getDouble("top")-afterAnchor.getDouble("top"))<=2,"continuation preserves paragraph offset within 2 CSS pixels");
        ui(()->{String status=((TextView)field("readerStatus")).getText().toString();check(status.contains("末页")&&!status.contains("本次没有新回答"),"native final-page status does not falsely deny additions");check(!(boolean)field("loadingMore")&&(boolean)field("morePaused"),"final page stops busy/automatic repeat");});shot("02-last-page-added");
        Document login=AnswerStream.copy(last);login.moreStatus="login";login.notice="合成登录限制：已读回答保留，请登录后重试。";
        ui(()->call("appendAtReadingPosition",new Class<?>[]{Document.class,int.class,Set.class},login,(int)field("generation"),AnswerStream.answerIds(last)));Thread.sleep(500);JSONObject denied=dom();check(denied.optInt("parts")==3&&denied.optString("body").contains("需先登录来源"),"login-restricted retry retains all read answers");shot("03-login-retry-keeps-answers");
        ui(()->{find(activity.getWindow().getDecorView(),"返回").performClick();quiet();check(field("current")==null&&field("selected")==Source.AGGREGATE,"reader back returns to total ranking");});
    }
    private void douban()throws Exception{
        String url="https://www.douban.com/gallery/topic/999999991/";Item item=new Item(Source.DOUBAN,"合成豆瓣仅视频话题",url,"");Document empty=AdditionalSources.doubanTopic(item,"{\"items\":[{\"target\":{\"title\":\"合成视频\",\"url\":\"https://www.douban.com/topic/999999993/\",\"video_info\":{}}}]}");check(empty.filteredVideos==1&&empty.sourceUnavailable,"all-video fixture has no readable public content");show(item,empty);
        ui(()->{String text=nativeText(activity.getWindow().getDecorView());check(text.contains("当前未返回")&&!text.contains("正在加载动态内容"),"empty Douban topic explains result instead of endless spinner");View retry=find(activity.getWindow().getDecorView(),"重试");check(retry!=null&&retry.isClickable(),"Douban native retry entry exists");check(find(activity.getWindow().getDecorView(),"来源页 / 登录后重新读取")!=null,"Douban source/login recovery entry exists");check(((Repository)field("repo")).cachedArticle(item)==null,"empty topic is not cached as a successful article");});shot("04-douban-empty-recovery");
        Document recovered=new Document();recovered.url=url;recovered.title="合成豆瓣恢复话题";recovered.notice="合成恢复结果，不是实网请求";recovered.related.add(new Item(Source.DOUBAN,"合成讨论入口","https://www.douban.com/group/topic/999999992/","合成"));
        // Cache a synthetic recovery result so the real retry click can deterministically render it.
        ui(()->{((Repository)field("repo")).cacheArticle(recovered);find(activity.getWindow().getDecorView(),"重试").performClick();});
        ui(()->{String text=nativeText(activity.getWindow().getDecorView());check(text.contains("合成讨论入口")&&text.contains("选择一篇内容继续阅读"),"real retry click reaches synthetic recovered discussion list");check(field("dynamic")==null,"topic recovery does not fall through to dynamic spinner");});shot("05-douban-recovered-list");
    }
    @Override public void onStart(){Bundle result=new Bundle();try{
        output=new File(getTargetContext().getExternalFilesDir(null),"user-journey-repair/run-"+System.currentTimeMillis());output.mkdirs();report.append("SYNTHETIC PRESENTATION TEST. Real activity/WebView/native handlers, documents injected; no live API success claim.\n");
        activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(900);
        navigation();continuation();douban();
    }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}finally{try{if(activity!=null)ui(()->{quiet();((Repository)field("repo")).invalidateArticles();activity.finish();});report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');try(FileOutputStream out=new FileOutputStream(new File(output,"report.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}}catch(Exception e){failures++;}result.putString("stream",report+"\nOutput: "+output);finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}}
}
