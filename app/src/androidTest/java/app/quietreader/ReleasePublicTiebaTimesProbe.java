package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Public source -> actual R8 reader -> normal history. Android APIs only. */
final class ReleasePublicTiebaTimesProbe {
    private final Instrumentation test;
    private Activity source,reader;
    private final boolean wideAcquisition;
    private Application.ActivityLifecycleCallbacks viewportHook;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder("R8 PUBLIC TIEBA; anonymous loaded rows only, no login or full-pagination certification\n");
    private final String url="https://tieba.baidu.com/p/11139792603?qr_release_times="+UUID.randomUUID();
    ReleasePublicTiebaTimesProbe(Instrumentation test){this(test,false);}
    ReleasePublicTiebaTimesProbe(Instrumentation test,boolean wideAcquisition){this.test=test;this.wideAcquisition=wideAcquisition;}
    private void ui(Runnable work){test.runOnMainSync(work);}
    private void check(boolean ok,String label){checks++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');if(!ok)throw new IllegalStateException(label);}
    private static WebView find(View view,boolean own){
        if(view instanceof WebView){String url=((WebView)view).getUrl();if(url!=null&&(own?url.startsWith("https://quiet-reader.invalid/"):url.startsWith("https://tieba.baidu.com/p/11139792603")))return (WebView)view;}
        if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){WebView found=find(((ViewGroup)view).getChildAt(n),own);if(found!=null)return found;}return null;
    }
    private WebView page(Activity owner,boolean own){WebView[] result={null};ui(()->result[0]=find(owner.getWindow().getDecorView(),own));return result[0];}
    private JSONObject js(WebView page,String script)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] raw={null};ui(()->{boolean enabled=page.getSettings().getJavaScriptEnabled();page.getSettings().setJavaScriptEnabled(true);page.evaluateJavascript(script,value->{raw[0]=value;page.getSettings().setJavaScriptEnabled(enabled);done.countDown();});});
        if(!done.await(5,TimeUnit.SECONDS))throw new IllegalStateException("DOM callback missing");Object value=new JSONTokener(raw[0]).nextValue();return value instanceof String?new JSONObject((String)value):new JSONObject();
    }
    private static String identity(String key)throws Exception{return "tieba-post-"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))).substring(0,24);}
    private static JSONObject section(JSONArray sections,String id)throws Exception{JSONObject found=null;for(int n=0;n<sections.length();n++)if(id.equals(sections.getJSONObject(n).optString("id"))){if(found!=null)throw new IllegalStateException("Duplicate saved identity");found=sections.getJSONObject(n);}return found;}
    private static final String SOURCE="""
        JSON.stringify((()=>{
          const rows=[],images=[];
          for(const floor of Array.from(document.querySelectorAll('.pc-pb-reply-list .pb-comment-item[data-id]')).slice(0,32)){
            const parent=floor.getAttribute('data-id');
            for(const node of Array.from(floor.querySelectorAll('.pb-lzl-item')).slice(0,128)){
              const c=node.__vue__,p=c&&c.$props,d=p&&p.commentData;
              if(c&&c.$el===node&&d&&String(p.pid)===parent&&Number.isSafeInteger(d.id)&&Number.isSafeInteger(d.time))rows.push({parent:parent,id:String(d.id),time:d.time});
            }
            const own=Array.from(floor.querySelectorAll('.comment-content>.image-card-wrapper img')).filter(i=>!i.closest('.pb-lzl-item')).map(i=>{for(const a of ['src','data-src','data-original']){const v=i.getAttribute(a)||'';if(v.startsWith('https://'))return v;}return '';}).filter(Boolean);
            if(own.length)images.push({parent:parent,urls:own});
          }
          return {ready:location.pathname==='/p/11139792603'&&rows.length>0,rows:rows,images:images,restricted:!!document.querySelector('.pc-pb-reply-list .login-guard-mask'),viewport:{width:innerWidth,height:innerHeight,dpr:devicePixelRatio,meta:document.querySelector('meta[name=viewport]')?.content||''}};
        })())
        """;
    void run(){Context context=test.getTargetContext();SharedPreferences prefs=context.getSharedPreferences("source-session",0);boolean had=prefs.contains("TIEBA-web"),previous=prefs.getBoolean("TIEBA-web",false);
        try{
            check(context.getPackageName().equals("app.quietreader.discoveryprobe")&&Build.HARDWARE.equals("ranchu"),"public probe is confined to the isolated emulator target");
            check((context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"actual target is a non-debuggable minified release");
            check(!context.getSharedPreferences("reading-history",0).contains(url),"unique public reading identity has no existing history");
            ui(()->prefs.edit().putBoolean("TIEBA-web",true).commit());
            source=test.startActivitySync(new Intent().setClassName(context.getPackageName(),"app.quietreader.LoginActivity").putExtra("source","TIEBA").putExtra("url",url).putExtra("board",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            JSONObject expected=null;long deadline=SystemClock.elapsedRealtime()+45000;
            while(SystemClock.elapsedRealtime()<deadline){WebView origin=page(source,false);if(origin!=null){expected=js(origin,SOURCE);if(expected.optBoolean("ready"))break;}Thread.sleep(150);}
            check(expected!=null&&expected.optBoolean("ready"),"independent real public source provides exact rooted child IDs, parent IDs and creation seconds");
            JSONArray rows=expected.getJSONArray("rows"),images=expected.getJSONArray("images");
            check(rows.length()>0&&images.length()>0,"public source includes nested replies and a source-owned image floor");
            report.append("SOURCE children=").append(rows.length()).append(" imageFloors=").append(images.length()).append(" restricted=").append(expected.optBoolean("restricted")).append(" viewport=").append(expected.optJSONObject("viewport")).append('\n');
            ui(source::finish);
            if(wideAcquisition){
                viewportHook=new Application.ActivityLifecycleCallbacks(){
                    public void onActivityCreated(Activity owner,Bundle state){if(!owner.getClass().getName().equals("app.quietreader.MainActivity"))return;ViewGroup host=owner.findViewById(android.R.id.content);host.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){
                        public void onChildViewRemoved(View p,View c){}
                        public void onChildViewAdded(View p,View c){if(c instanceof WebView&&((WebView)c).getSettings().getBlockNetworkImage()){WebView view=(WebView)c;view.getSettings().setUseWideViewPort(true);view.getSettings().setLoadWithOverviewMode(true);}}
                    });}
                    public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
                };ui(()->((Application)context.getApplicationContext()).registerActivityLifecycleCallbacks(viewportHook));
            }
            JSONObject item=new JSONObject().put("source","TIEBA").put("title","公开贴吧读取验证").put("url",url).put("detail","").put("video",false);long openedAt=System.currentTimeMillis();
            reader=test.startActivitySync(new Intent().setClassName(context.getPackageName(),"app.quietreader.MainActivity").putExtra("direct_reader",true).putExtra("discovery_item",item.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            JSONObject layout=null,acquisition=null;deadline=SystemClock.elapsedRealtime()+45000;
            while(SystemClock.elapsedRealtime()<deadline){WebView origin=page(reader,false);if(origin!=null){JSONObject sample=js(origin,SOURCE);if(sample.optBoolean("ready")){int[] bounds=new int[2];ui(()->{bounds[0]=origin.getWidth();bounds[1]=origin.getHeight();});sample.put("nativeWidth",bounds[0]).put("nativeHeight",bounds[1]);acquisition=sample;}}WebView own=page(reader,true);if(own!=null){layout=js(own,"JSON.stringify({parts:document.querySelectorAll('main .part').length,nested:document.querySelectorAll('main .nested-reply').length,images:document.querySelectorAll('main .part img').length,overflow:document.documentElement.scrollWidth>innerWidth+2})");if(layout.optInt("nested")>0)break;}Thread.sleep(150);}
            report.append("READER layout=").append(layout).append('\n');
            check(layout!=null&&layout.optInt("nested")>0,"actual R8 reader renders structured nested replies from its source window");
            check(acquisition!=null,"actual acquisition WebView source metadata was observed through the normal view hierarchy before extraction finished");
            JSONArray acquiredRows=acquisition.getJSONArray("rows");Set<String> acquiredIds=new HashSet<>();for(int n=0;n<acquiredRows.length();n++)acquiredIds.add(identity("lzl:"+acquiredRows.getJSONObject(n).getString("id")));
            report.append("ACQUISITION observedChildren=").append(acquiredRows.length()).append(" wideSettingsExperiment=").append(wideAcquisition).append(" viewport=").append(acquisition.optJSONObject("viewport")).append(" nativePixels=").append(acquisition.optInt("nativeWidth")).append('x').append(acquisition.optInt("nativeHeight")).append('\n');
            check(!layout.optBoolean("overflow"),"actual structured reader has no horizontal overflow");
            check(test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME),"normal Home action pauses the reader and saves reading state");
            JSONObject saved=null;deadline=SystemClock.elapsedRealtime()+7000;
            while(SystemClock.elapsedRealtime()<deadline){String raw=context.getSharedPreferences("reading-history",0).getString(url,"");if(!raw.isEmpty()){saved=new JSONObject(raw);if(saved.optLong("bodyAt")>=openedAt&&saved.optJSONObject("document")!=null)break;}Thread.sleep(100);}
            check(saved!=null&&saved.optLong("bodyAt")>=openedAt&&saved.optJSONObject("document")!=null,"normal production history contains a newly acquired body rather than a seeded snapshot");
            JSONObject document=saved.getJSONObject("document");JSONArray sections=document.getJSONArray("sections");
            check(document.optInt("tiebaReplyRevision")==4,"saved public body uses the current reply relationship revision");
            int matched=0,matchedImages=0;Set<String> parents=new HashSet<>();Map<String,JSONObject> sourceChildren=new HashMap<>();
            for(int n=0;n<rows.length();n++){JSONObject row=rows.getJSONObject(n);if(sourceChildren.put(identity("lzl:"+row.getString("id")),row)!=null)throw new IllegalStateException("Independent source repeated a child identity");}
            for(int n=0;n<sections.length();n++){
                JSONObject child=sections.getJSONObject(n);if(!child.optBoolean("nestedReply"))continue;
                JSONObject row=sourceChildren.get(child.optString("id"));if(row==null||!acquiredIds.remove(child.optString("id")))throw new IllegalStateException("Saved child was not observed on both independent and actual acquisition source windows");
                String parentId=identity("post_content_"+row.getString("parent"));JSONObject parent=section(sections,parentId);
                if(parent==null||parent.optBoolean("nestedReply")||!row.getString("parent").equals(parent.optString("sourcePostId"))||child==null||!child.optBoolean("nestedReply")||!parentId.equals(child.optString("parentSectionId")))throw new IllegalStateException("Saved public child-parent identity differs from actual source");
                JSONObject time=child.optJSONObject("sourceReplyEvidence");if(time==null||!"tieba-child-created-v1".equals(time.optString("version"))||time.optLong("createdAt")!=row.getLong("time")*1000)throw new IllegalStateException("Saved public child creation time differs from actual source");
                if(parent.has("sourceReplyEvidence"))throw new IllegalStateException("Parent borrowed a child time");parents.add(parentId);matched++;
            }
            check(matched==layout.optInt("nested"),"every rendered R8 child has an independently verified source identity, parent and exact creation time in normal history");
            check(acquiredIds.isEmpty(),"no child observed in the actual acquisition source window was lost by R8 extraction");
            for(int n=0;n<images.length();n++){
                JSONObject floor=images.getJSONObject(n),parent=section(sections,identity("post_content_"+floor.getString("parent")));if(parent==null)continue;
                Set<String> actual=new HashSet<>();JSONArray blocks=parent.getJSONArray("blocks");for(int b=0;b<blocks.length();b++)if("image".equals(blocks.getJSONObject(b).optString("type")))actual.add(blocks.getJSONObject(b).optString("value"));
                JSONArray urls=floor.getJSONArray("urls");for(int i=0;i<urls.length();i++){if(!actual.contains(urls.getString(i)))throw new IllegalStateException("Source floor image is missing from its own saved parent");matchedImages++;}
            }
            check(matchedImages>0,"source-owned pictures stay on their actual parent floor through the R8 reader and history");
            if(expected.optBoolean("restricted"))check("login".equals(document.optString("more")),"public continuation login restriction remains intact");
            report.append("MATCHED children=").append(matched).append(" parents=").append(parents.size()).append(" floorImages=").append(matchedImages).append(" sourceRowsOutsideReaderWindow=").append(rows.length()-matched).append("; virtual source windows can differ; no full-pagination claim or source text/account/cookie export\n");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{ui(()->{if(viewportHook!=null)((Application)context.getApplicationContext()).unregisterActivityLifecycleCallbacks(viewportHook);if(source!=null&&!source.isFinishing())source.finish();if(reader!=null&&!reader.isFinishing())reader.finish();SharedPreferences.Editor edit=prefs.edit();if(had)edit.putBoolean("TIEBA-web",previous);else edit.remove("TIEBA-web");edit.commit();});test.waitForIdleSync();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
