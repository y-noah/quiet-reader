package app.quietreader;

import android.app.*;
import android.os.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.*;
import android.widget.*;
import android.webkit.WebView;
import java.lang.reflect.*;
import java.io.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Device tests owned by this project, without external UI automation dependencies. */
public class SmokeInstrumentation extends Instrumentation {
    private int assertions=0;
    private MainActivity activity;
    private boolean probe,streamProbe;
    private String diagnoseUrl,diagnoseSource,probeSource;
    @Override public void onCreate(Bundle args) { super.onCreate(args);probe=args!=null&&"true".equals(args.getString("probe"));if(args!=null){streamProbe="true".equals(args.getString("stream"));diagnoseUrl=args.getString("diagnoseUrl");diagnoseSource=args.getString("diagnoseSource","TIEBA");probeSource=args.getString("probeSource");} start(); }
    private void check(boolean truth,String message) { assertions++; if(!truth)throw new AssertionError(message); }
    private Object field(String name)throws Exception { Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity); }
    private void call(String name,Class<?>[] types,Object... values)throws Exception { Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,values); }
    private void main(Throwing runnable)throws Exception { final Throwable[] err={null};runOnMainSync(()->{try{runnable.run();}catch(Throwable e){err[0]=e;}});if(err[0]!=null)throw new Exception(err[0]);waitForIdleSync(); }
    interface Throwing { void run()throws Exception; }
    private String text(View v) { StringBuilder s=new StringBuilder();if(v instanceof TextView)s.append(((TextView)v).getText());if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)s.append(text(((ViewGroup)v).getChildAt(i))).append('\n');return s.toString(); }
    private void screenshot(String name)throws Exception { Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new Exception("Screenshot unavailable");File dir=new File(getTargetContext().getExternalFilesDir(null),"qa");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle(); }
    private String readerState()throws Exception {
        CountDownLatch latch=new CountDownLatch(1);String[] state={""};
        // Test-only DOM inspection of our escaped, CSP-restricted document. Production keeps JS off.
        main(()->{WebView web=(WebView)field("readerWeb");web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("JSON.stringify({title:document.title,width:innerWidth,scroll:document.documentElement.scrollWidth,images:Array.from(document.images).map(i=>({complete:i.complete,width:i.naturalWidth}))})",v->{web.getSettings().setJavaScriptEnabled(false);state[0]=v;latch.countDown();});});
        latch.await(5,TimeUnit.SECONDS);return state[0];
    }
    private void probeSources()throws Exception {
        StringBuilder report=new StringBuilder();
        for(Source source:Source.values()) {
            if(!source.visible()&&probeSource==null)continue;
            if(probeSource!=null&&!source.name().equals(probeSource))continue;
            long started=System.currentTimeMillis();
            main(()->{Field f=MainActivity.class.getDeclaredField("selected");f.setAccessible(true);f.set(activity,source);call("home",new Class<?>[]{boolean.class},true);});
            Repository repository=(Repository)field("repo");boolean ready=false;
            for(int i=0;i<45;i++){Thread.sleep(1000);if(repository.cachedAt(source)>=started){ready=true;break;}}
            report.append(source.name()).append(" board=").append(ready?repository.cached(source).size():"unavailable");
            screenshot("probe-"+source.name()+"-board");
            if(ready) {
                Item first=repository.cached(source).get(0);main(()->call("open",new Class<?>[]{Item.class,boolean.class},first,true));
                Document[] doc={null};
                for(int i=0;i<45;i++){Thread.sleep(1000);main(()->doc[0]=(Document)field("reading"));if(doc[0]!=null)break;}
                report.append(" firstUrl=").append(first.url).append(" first=").append(doc[0]==null?"unavailable":"blocks:"+doc[0].blocks.size()+",links:"+doc[0].related.size()+",sections:"+doc[0].sections.size()).append(" elapsedMs=").append(System.currentTimeMillis()-started);
                if(streamProbe&&source==Source.ZHIHU&&doc[0]!=null){
                    int before=AnswerStream.answers(doc[0]);main(()->call("loadMore",new Class<?>[]{}));Thread.sleep(1200);screenshot("stream-loading");
                    for(int i=0;i<25;i++){Thread.sleep(1000);boolean[] busy={false};main(()->busy[0]=(Boolean)field("loadingMore"));if(!busy[0])break;}
                    main(()->doc[0]=(Document)field("reading"));report.append(" streamBefore=").append(before).append(" streamAfter=").append(AnswerStream.answers(doc[0])).append(" notice=").append(doc[0].notice);
                    CountDownLatch captured=new CountDownLatch(1);String[] sourceHtml={""};
                    main(()->{Object loader=field("answers");Field wf=AnswerStream.class.getDeclaredField("web");wf.setAccessible(true);((WebView)wf.get(loader)).evaluateJavascript("document.documentElement.outerHTML",v->{sourceHtml[0]=v;captured.countDown();});});
                    if(captured.await(5,TimeUnit.SECONDS)){String html=(String)new org.json.JSONTokener(sourceHtml[0]).nextValue();File dir=new File(getTargetContext().getExternalFilesDir(null),"qa");dir.mkdirs();try(FileOutputStream output=new FileOutputStream(new File(dir,"stream-source.html"))){output.write(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
                    screenshot("stream-after");
                }
                if(doc[0]!=null&&!doc[0].hasContent()&&!doc[0].related.isEmpty()) {
                    Item article=doc[0].related.get(0);main(()->call("open",new Class<?>[]{Item.class,boolean.class},article,true));
                    doc[0]=null;
                    for(int i=0;i<45;i++){Thread.sleep(1000);main(()->doc[0]=(Document)field("reading"));if(doc[0]!=null)break;}
                    report.append(" articleUrl=").append(article.url).append(" article=").append(doc[0]==null?"unavailable":"blocks:"+doc[0].blocks.size());
                }
                Thread.sleep(2500);
                screenshot("probe-"+source.name()+"-reader");
            }
            report.append('\n');Bundle progress=new Bundle();progress.putString("stream",report.toString());sendStatus(0,progress);
        }
        Bundle result=new Bundle();result.putString("stream",report.toString()+"Anonymous device observations; authentication not certified.");finish(Activity.RESULT_OK,result);
    }
    private void diagnose()throws Exception {
        Source source=Source.valueOf(diagnoseSource);
        LoginActivity page=(LoginActivity)startActivitySync(new Intent(getTargetContext(),LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("source",source.name()).putExtra("url",diagnoseUrl));
        Thread.sleep(15000);
        CountDownLatch latch=new CountDownLatch(1);String[] raw={""},url={""};
        main(()->{Field f=LoginActivity.class.getDeclaredField("web");f.setAccessible(true);WebView web=(WebView)f.get(page);url[0]=web.getUrl();web.evaluateJavascript("document.documentElement.outerHTML",value->{raw[0]=value;latch.countDown();});});
        check(latch.await(5,TimeUnit.SECONDS),"DOM inspection timeout");
        String html=(String)new org.json.JSONTokener(raw[0]).nextValue();
        File dir=new File(getTargetContext().getExternalFilesDir(null),"qa");dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,"diagnostic-"+source.name()+".html"))){out.write(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        org.jsoup.nodes.Document parsed=org.jsoup.Jsoup.parse(html);String body=parsed.body().text();
        screenshot("diagnostic-"+source.name());main(page::finish);
        Bundle result=new Bundle();result.putString("stream","url="+url[0]+" title="+parsed.title()+" body="+body.substring(0,Math.min(1400,body.length())));finish(Activity.RESULT_OK,result);
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            if(diagnoseUrl!=null){diagnose();return;}
            Intent launch=new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=(MainActivity)startActivitySync(launch);waitForIdleSync();
            if(probe){probeSources();return;}
            main(()->{check(field("boardSearch") instanceof EditText,"Compact home search missing");check(!text(activity.getWindow().getDecorView()).contains("收藏"),"Removed favorites entry is visible");});
            screenshot("01-home");
            Document fixture=new Document(); fixture.title="排版测试：让热搜回到阅读本身，长标题也应该自然换行";fixture.url="https://www.toutiao.com/article/7691554429752902184/";
            fixture.notice="这是自动化排版测试内容，不是真实新闻。";
            for(int i=0;i<12;i++)fixture.blocks.add(new Block("text","第 "+(i+1)+" 段。一个好的阅读界面应该让文字成为主角。段落有足够的呼吸空间，长句自然换行，不被下载提示、推荐列表或浮动操作打断。"));
            main(()->{LoginActivity.pendingDocument=fixture;activity.onActivityResult(100,Activity.RESULT_OK,new Intent().putExtra("source",Source.TOUTIAO.name()));});
            Thread.sleep(1200);
            main(()->{check(field("readerWeb") instanceof WebView,"Own reader missing");WebView w=(WebView)field("readerWeb");check(!w.getSettings().getJavaScriptEnabled(),"Reader JS must be disabled");check(!w.getSettings().getAllowFileAccess(),"File access must be disabled");});
            screenshot("02-reader-fixture");
            ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
            main(()->activity.recreate());
            Activity restored=waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);
            check(restored instanceof MainActivity,"Activity recreation did not finish");activity=(MainActivity)restored;
            main(()->check(((Document)field("reading")).title.equals(fixture.title),"Reading lost after recreation"));
            main(()->check(!text(activity.getWindow().getDecorView()).contains("收藏"),"Reader unexpectedly exposes removed favorites entry"));
            screenshot("03-restored-reader-fixture");
            main(()->call("home",new Class<?>[]{boolean.class},false));
            Item live=new Item(Source.HUPU,"虎扑实网阅读","https://bbs.hupu.com/642690580.html","");
            main(()->call("open",new Class<?>[]{Item.class,boolean.class},live,true));
            boolean loaded=false;
            for(int i=0;i<35;i++) {Thread.sleep(1000);final boolean[] ready={false};main(()->ready[0]=field("reading")!=null);if(ready[0]){loaded=true;break;}}
            check(loaded,"Live Hupu article did not load");
            main(()->check(((Document)field("reading")).hasContent(),"Live source has no content"));
            // Image decoding check explicitly expands real extracted sections; collapsed previews intentionally omit images.
            main(()->{Document doc=(Document)field("reading");java.util.Set<String> open=(java.util.Set<String>)field("expanded");for(Section section:ReaderHtml.sections(doc))open.add(section.id);call("reloadReader",new Class<?>[]{Document.class},doc);});
            Thread.sleep(1200);
            // Traverse the document to trigger native lazy-image loading rather than disabling it for the test.
            for(int n=0;n<80;n++){int[] before={0},after={0};main(()->{WebView web=(WebView)field("readerWeb");before[0]=web.getScrollY();web.scrollBy(0,Math.max(1,web.getHeight()-100));after[0]=web.getScrollY();});Thread.sleep(250);if(n>3&&before[0]==after[0])break;}
            Thread.sleep(5000);screenshot("04-hupu-live");
            String telemetry=readerState();
            org.json.JSONObject metrics=new org.json.JSONObject((String)new org.json.JSONTokener(telemetry).nextValue());
            check(metrics.getInt("width")==metrics.getInt("scroll"),"Reader overflows viewport");
            org.json.JSONArray images=metrics.getJSONArray("images");check(images.length()>0,"No real article images");
            for(int i=0;i<images.length();i++)check(images.getJSONObject(i).getInt("width")>0,"Article image failed to decode");
            result.putString("stream","PASS "+assertions+" assertions; reader="+telemetry+"; screenshots in externalFiles/qa. Login-only sources not certified.");
            finish(Activity.RESULT_OK,result);
        } catch(Throwable e) { result.putString("stream","FAIL after "+assertions+" assertions: "+e.toString()); finish(Activity.RESULT_CANCELED,result); }
    }
}
