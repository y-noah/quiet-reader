package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import java.util.concurrent.*;
import org.json.*;

/** Real phone/source comparison. No cookies/viewer IDs; failed main-text checks save only the whitelisted public post body. */
final class ReleasePhoneSourceProbe {
    private final Instrumentation test;
    private Activity source,reader;
    private int checks,failures;
    private boolean selectionReported;
    private final StringBuilder report=new StringBuilder("PHONE HUPU: real source and ordinary reader, no preference marker or fixture\n");
    ReleasePhoneSourceProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean ok,String label){checks++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');if(!ok)throw new IllegalStateException(label);}
    private static boolean readerOrigin(WebView view){try{android.net.Uri uri=android.net.Uri.parse(view.getUrl());return "https".equals(uri.getScheme())&&"quiet-reader.invalid".equals(uri.getHost())&&"/".equals(uri.getPath())&&uri.getQueryParameter("render")!=null;}catch(Exception invalid){return false;}}
    private static WebView web(View v,boolean readerOnly){if(v instanceof WebView&&(!readerOnly||readerOrigin((WebView)v)))return (WebView)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){WebView w=web(((ViewGroup)v).getChildAt(n),readerOnly);if(w!=null)return w;}return null;}
    private WebView page(Activity owner){WebView[] result={null};ui(()->{View root=owner.getWindow().getDecorView();result[0]=web(root,owner==reader);if(owner==reader&&result[0]!=null&&!selectionReported){report.append("VIEW_SELECTION firstIsReader=").append(web(root,false)==result[0]).append(" chosenReaderOrigin=").append(readerOrigin(result[0])).append('\n');selectionReported=true;}});return result[0];}
    private JSONObject js(WebView page,String script)throws Exception{
        CountDownLatch latch=new CountDownLatch(1);String[] value={null};
        ui(()->{boolean enabled=page.getSettings().getJavaScriptEnabled();page.getSettings().setJavaScriptEnabled(true);page.evaluateJavascript(script,result->{page.getSettings().setJavaScriptEnabled(enabled);value[0]=result;latch.countDown();});});
        if(!latch.await(5,TimeUnit.SECONDS))throw new IllegalStateException("DOM callback timeout");
        Object result=new JSONTokener(value[0]).nextValue();return result instanceof String?new JSONObject((String)result):new JSONObject();
    }
    private static String norm(String text){return text.replaceAll("[\\s\\p{Z}]+","");}
    private Context authorized(Bundle args){
        Context context=test.getTargetContext();
        check("app.quietreader".equals(context.getPackageName())&&"PHY110".equals(Build.MODEL)&&Build.VERSION.SDK_INT==36&&"true".equals(args.getString("phone")),"explicit diagnostic confined to authorized ordinary Find X7 Ultra");
        check((context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"ordinary installed target is non-debuggable");return context;
    }
    private static final String READER_SNAPSHOT="JSON.stringify((()=>{const first=document.querySelector('main .part'),copy=first?.cloneNode(true);copy?.querySelectorAll('.section-label,.action,.section-nav,figcaption,style,script').forEach(e=>e.remove());return {text:document.body?.innerText||'',parts:document.querySelectorAll('main .part').length,replies:document.querySelectorAll('main .part.discussion').length,folded:!!first?.querySelector('.action[aria-expanded=false]'),mainFolded:!!first?.querySelector('.action[aria-expanded=false]'),main:copy?.textContent||'',overflow:document.documentElement.scrollWidth>innerWidth+2}})())";
    private static void nativeText(View view,StringBuilder text){if(view instanceof android.widget.TextView)text.append(((android.widget.TextView)view).getText()).append('\n');if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)nativeText(((ViewGroup)view).getChildAt(n),text);}
    private static View find(View view,String label){if(label.equals(String.valueOf(view.getContentDescription()))||view instanceof android.widget.TextView&&label.equals(String.valueOf(((android.widget.TextView)view).getText())))return view;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){View result=find(((ViewGroup)view).getChildAt(n),label);if(result!=null)return result;}return null;}
    private void reread()throws Exception{
        long focusDeadline=SystemClock.elapsedRealtime()+5000;boolean[] focused={false};
        do{ui(()->focused[0]=reader.hasWindowFocus());if(!focused[0])Thread.sleep(50);}while(!focused[0]&&SystemClock.elapsedRealtime()<focusDeadline);
        test.waitForIdleSync();ui(()->{View more=find(reader.getWindow().getDecorView(),"更多选项");if(more==null||!more.performClick())throw new IllegalStateException("Reader menu unavailable");});
        boolean clicked=false;long deadline=SystemClock.elapsedRealtime()+5000;
        while(!clicked&&SystemClock.elapsedRealtime()<deadline){android.view.accessibility.AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText("重新读取")){if(!"重新读取".equals(String.valueOf(node.getText())))continue;while(node!=null&&!node.isClickable())node=node.getParent();if(node!=null)clicked=node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);if(clicked)break;}if(!clicked)Thread.sleep(50);}
        check(clicked,"explicit product reread requests fresh source acquisition");
    }
    private void verifyDetached(WebView origin,Bundle args)throws Exception{
        if(!"true".equals(args.getString("teardown")))return;
        boolean[] detached={false};long end=SystemClock.elapsedRealtime()+5000;
        do{ui(()->detached[0]=origin.getParent()==null);if(!detached[0])Thread.sleep(50);}while(!detached[0]&&SystemClock.elapsedRealtime()<end);
        check(detached[0],"closed source WebView is removed from its parent before destruction");
    }
    void run(Bundle args){
        try{
            Context context=authorized(args);
            String id=args.getString("thread","643330244");check(id.equals("643330244")||id.equals("643294354"),"public thread identity is whitelisted");
            java.io.File priorDiff=new java.io.File(context.getExternalFilesDir(null),"phone-hupu-main-diff.json");
            if(priorDiff.exists()&&!priorDiff.delete())throw new IllegalStateException("Previous diagnostic file could not be retired");
            String url="https://bbs.hupu.com/"+id+".html";
            source=test.startActivitySync(new Intent().setClassName(context.getPackageName(),"app.quietreader.LoginActivity").putExtra("source","HUPU").putExtra("url",url).putExtra("board",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            WebView origin=page(source);check(origin!=null,"actual shared source WebView exists");
            JSONObject actual=null;long deadline=SystemClock.elapsedRealtime()+45000;
            String script="JSON.stringify((()=>{try{const p=JSON.parse(document.querySelector('#__NEXT_DATA__')?.textContent||'{}').props?.pageProps,d=p?.detail,t=d?.thread;const main=document.querySelector('[class*=post-content_main-post-info] .thread-content-detail');const replies=Array.from(document.querySelectorAll('.reply-list-wrapper .thread-content-detail')).filter(e=>e.getClientRects().length&&getComputedStyle(e).visibility!=='hidden').map(e=>e.innerText);return {ready:!!t&&!!main&&!!main.innerText,tid:t?.tid||'',signedIn:/^[1-9][0-9]*$/.test(String(d?.user?.puid||'')),range:d?.visibleRange||'',threadRange:t?.visibleRange||'',status:t?.status,success:p?.detailErrorInfo?.code===200,main:main?.innerText||'',replies};}catch(e){return {ready:false}}})())";
            while(SystemClock.elapsedRealtime()<deadline){actual=js(origin,script);if(actual.optBoolean("ready"))break;Thread.sleep(250);}
            check(actual!=null&&actual.optBoolean("ready")&&id.equals(actual.optString("tid"))&&actual.optBoolean("success"),"real source returns identity-matched rendered main content");
            boolean restricted=!actual.optString("range").equals("ALL_SEE")||!actual.optString("threadRange").equals("ALL_SEE");
            report.append("SOURCE signedIn=").append(actual.optBoolean("signedIn")).append(" restricted=").append(restricted).append(" mainChars=").append(actual.getString("main").length()).append(" renderedReplies=").append(actual.getJSONArray("replies").length()).append('\n');
            if(!restricted)report.append("LIMITATION public thread does not prove gated account permission equivalence\n");
            if(!actual.optBoolean("signedIn"))report.append("LIMITATION this source response does not identify a signed-in viewer\n");
            String expected=norm(actual.getString("main"));JSONArray replies=actual.getJSONArray("replies");
            ui(source::finish);
            JSONObject item=new JSONObject().put("source","HUPU").put("url",url).put("title","虎扑公开帖子").put("detail","").put("video",false);
            reader=test.startActivitySync(new Intent().setClassName(context.getPackageName(),"app.quietreader.MainActivity").putExtra("discovery_item",item.toString()).putExtra("direct_reader",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));verifyDetached(origin,args);
            if("true".equals(args.getString("refresh")))reread();
            JSONObject body=null;WebView reading=null;deadline=SystemClock.elapsedRealtime()+45000;
            while(SystemClock.elapsedRealtime()<deadline){reading=page(reader);if(reading!=null){body=js(reading,READER_SNAPSHOT);if(body.optInt("parts")>0)break;}Thread.sleep(250);}
            if(body==null||body.optInt("parts")==0){
                StringBuilder nativeLabels=new StringBuilder();ui(()->nativeText(reader.getWindow().getDecorView(),nativeLabels));
                for(String label:new String[]{"状态已变化","来源状态发生变化","需要来源页面协助","需要来源登录","尚未取得","正在读取","重新读取","网络","超时","来源跳转"})report.append("DIAGNOSTIC nativeContains[").append(label).append("]=").append(nativeLabels.indexOf(label)>=0).append('\n');
                android.graphics.Bitmap shot=test.getUiAutomation().takeScreenshot();if(shot!=null)try(java.io.FileOutputStream file=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),"phone-hupu-failure.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);}finally{shot.recycle();}
            }
            check(body!=null&&body.optInt("parts")>0,"ordinary automatic reader obtains structured real thread");
            if(body.optBoolean("folded")){js(reading,"JSON.stringify((()=>{document.querySelector('main .part .action[aria-expanded=false]')?.click();return {clicked:true}})())");Thread.sleep(500);body=js(reading,READER_SNAPSHOT);}
            String renderedMain=norm(body.optString("main")),renderedAll=norm(body.optString("text"));
            report.append("MAIN_DIAGNOSTIC expectedChars=").append(expected.length()).append(" renderedMainChars=").append(renderedMain.length()).append(" mainFolded=").append(body.optBoolean("mainFolded")).append(" prefix80=").append(renderedAll.contains(expected.substring(0,Math.min(80,expected.length())))).append(" suffix80=").append(renderedAll.contains(expected.substring(Math.max(0,expected.length()-80)))).append('\n');
            if(!renderedMain.contains(expected)){
                // Only this whitelisted public post's main prose, excluding account/header/replies.
                JSONObject diagnostic=new JSONObject().put("publicThread",id).put("sourceMain",actual.getString("main")).put("readerMain",body.optString("main"));
                try(java.io.FileOutputStream file=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),"phone-hupu-main-diff.json"))){file.write(diagnostic.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            }
            check(renderedMain.contains(expected),"reader main prose contains the complete currently rendered source main text after excluding renderer controls");
            check(replies.length()==0||body.optInt("replies")>0,"source replies are represented as reader discussion sections");
            int matched=0,considered=0;String rendered=norm(body.optString("text"));
            for(int n=0;n<Math.min(3,replies.length());n++){String text=norm(replies.getString(n));if(text.isEmpty())continue;considered++;if(rendered.contains(text.substring(0,Math.min(80,text.length()))))matched++;}
            report.append("READER parts=").append(body.optInt("parts")).append(" replySections=").append(body.optInt("replies")).append(" firstReplyPrefixesMatched=").append(matched).append('/').append(considered).append("; existing caches permitted, no cold acquisition or complete pagination claim\n");
            check(!body.optBoolean("overflow"),"real reader has no horizontal overflow");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage()).append('\n');}
        finally{ui(()->{if(source!=null&&!source.isFinishing())source.finish();if(reader!=null)reader.finish();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }

    void runZhihu(Bundle args){
        report.setLength(0);report.append("PHONE ZHIHU: shared real source and reader, no cookie export or preference marker\n");
        try{
            Context context=authorized(args);String id=args.getString("question","2098500549041547203");
            check(id.equals("2098500549041547203"),"public question identity is whitelisted");String url="https://www.zhihu.com/question/"+id;
            source=test.startActivitySync(new Intent().setClassName(context.getPackageName(),"app.quietreader.LoginActivity").putExtra("source","ZHIHU").putExtra("url",url).putExtra("board",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            WebView origin=page(source);check(origin!=null,"actual shared Zhihu source WebView exists");
            String sourceScript="JSON.stringify((()=>{const visible=e=>!!e&&e.getClientRects().length>0&&getComputedStyle(e).visibility!=='hidden';let pending=false;for(const b of Array.from(document.querySelectorAll('.QuestionRichText .QuestionRichText-more,.QuestionHeader-detail .QuestionRichText-more,.AnswerItem button.ContentItem-more')).slice(0,20)){if(visible(b)&&/显示全部|展开阅读全文|阅读全文|展开全部/.test(b.textContent)){if(!b.__newProbeExpanded){b.__newProbeExpanded=Date.now();b.click()}if(Date.now()-b.__newProbeExpanded<6000)pending=true}}const q=document.querySelector('.QuestionRichText'),a=Array.from(document.querySelectorAll('.AnswerItem:not(.VideoAnswer):not([data-content-type=video]) .RichContent-inner .RichText')).find(e=>visible(e)&&e.innerText.length>100);const gate=Array.from(document.querySelectorAll('.Modal-wrapper .SignFlow,.Modal .SignFlow,.SignContainer-content')).some(visible);const profile=Array.from(document.querySelectorAll('.AppHeader-profile .Avatar,.AppHeader-profileAvatar,.AppHeader-userInfo')).some(visible);return {pathMatches:location.pathname==='/question/"+id+"'||location.pathname==='/question/"+id+"/',ready:!!document.querySelector('.QuestionHeader-title')&&!!a&&!pending,expanding:pending,gate,profileEvidence:profile,supplement:q?.innerText||'',answer:a?.innerText||'',answerZop:a?.closest('.AnswerItem')?.dataset.zop||'{}',answerCount:document.querySelectorAll('.AnswerItem').length}})())";
            JSONObject actual=null;long deadline=SystemClock.elapsedRealtime()+45000;
            while(SystemClock.elapsedRealtime()<deadline){actual=js(origin,sourceScript);if(actual.optBoolean("ready")&&actual.optBoolean("pathMatches"))break;Thread.sleep(250);}
            report.append("SOURCE_DIAGNOSTIC pathMatches=").append(actual!=null&&actual.optBoolean("pathMatches")).append(" profileEvidence=").append(actual!=null&&actual.optBoolean("profileEvidence")).append(" visibleLoginGate=").append(actual!=null&&actual.optBoolean("gate")).append(" expanding=").append(actual!=null&&actual.optBoolean("expanding")).append(" answerCount=").append(actual==null?0:actual.optInt("answerCount")).append('\n');
            check(actual!=null&&actual.optBoolean("ready")&&actual.optBoolean("pathMatches"),"real source question and expanded text answer are available");
            String supplement=norm(actual.getString("supplement")),answer=norm(actual.getString("answer"));
            String answerId=new JSONObject(actual.getString("answerZop")).optString("itemId");check(answerId.matches("[1-9][0-9]{0,30}"),"source answer has an explicit public item identity");
            String answerAnchor="reading-"+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(answerId.getBytes(java.nio.charset.StandardCharsets.UTF_8))+"-";
            check(answer.length()<=50000&&supplement.length()<=50000,"source comparison is bounded to one answer and question supplement");
            report.append("SOURCE supplementChars=").append(supplement.length()).append(" firstAnswerChars=").append(answer.length()).append("; profile evidence is not gated permission certification\n");
            ui(source::finish);JSONObject item=new JSONObject().put("source","ZHIHU").put("url",url).put("title","知乎公开问题").put("detail","").put("video",false);
            reader=test.startActivitySync(new Intent().setClassName(context.getPackageName(),"app.quietreader.MainActivity").putExtra("discovery_item",item.toString()).putExtra("direct_reader",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));verifyDetached(origin,args);
            if("true".equals(args.getString("refresh")))reread();
            String readerScript="JSON.stringify((()=>{const all=Array.from(document.querySelectorAll('main .part')),q=all.find(e=>e.dataset.sectionLabel==='问题补充'),answers=all.filter(e=>e.dataset.sectionLabel?.startsWith('回答')),matches=answers.filter(e=>(e.textContent||'').replace(/[\\s\\p{Z}]+/gu,'').includes("+JSONObject.quote(answer.substring(0,Math.min(40,answer.length())))+")),a=matches.length===1?matches[0]:null;const prose=e=>{if(!e)return '';const c=e.cloneNode(true);c.querySelectorAll('.section-label,.action,.section-nav,figcaption,style,script').forEach(n=>n.remove());return c.textContent};return {parts:all.length,supplement:prose(q),answer:prose(a),answerCount:answers.length,selected:matches.length===1,identityMatches:!!a?.querySelector('[id^='+"+JSONObject.quote(answerAnchor)+"+']'),folded:!!a?.querySelector('.action[aria-expanded=false]'),overflow:document.documentElement.scrollWidth>innerWidth+2}})())";
            JSONObject body=null;WebView reading=null;deadline=SystemClock.elapsedRealtime()+45000;
            while(SystemClock.elapsedRealtime()<deadline){reading=page(reader);if(reading!=null){body=js(reading,readerScript);if(body.optInt("answerCount")>0)break;}Thread.sleep(250);}
            check(body!=null&&body.optInt("answerCount")>0,"ordinary automatic reader obtains actual structured answers");
            check(body.optBoolean("selected"),"exact source-answer prefix identifies one acquired reader section");
            if(body.optBoolean("folded")){js(reading,"JSON.stringify((()=>{const a=Array.from(document.querySelectorAll('main .part')).find(e=>e.dataset.sectionLabel?.startsWith('回答')&&(e.textContent||'').replace(/[\\s\\p{Z}]+/gu,'').includes("+JSONObject.quote(answer.substring(0,Math.min(40,answer.length())))+"));a?.querySelector('.action[aria-expanded=false]')?.click();return {clicked:true}})())");Thread.sleep(500);body=js(reading,readerScript);}
            String visibleSupplement=norm(body.optString("supplement")),visibleAnswer=norm(body.optString("answer"));
            report.append("READER supplementChars=").append(visibleSupplement.length()).append(" firstAnswerChars=").append(visibleAnswer.length()).append(" answerCount=").append(body.optInt("answerCount")).append(" folded=").append(body.optBoolean("folded")).append('\n');
            check(supplement.isEmpty()||visibleSupplement.contains(supplement),"complete source question supplement is present in the reader");
            check(body.optBoolean("identityMatches"),"expanded reader anchors preserve the exact public source answer identity");
            check(!body.optBoolean("folded")&&visibleAnswer.contains(answer),"expanded reader answer contains the complete source first text answer");
            check(!body.optBoolean("overflow"),"real Zhihu reader has no horizontal overflow");
            report.append("LIMITATION existing cache permitted; first-answer order may change; source UI profile is not proof of LOGIN_SEE permissions\n");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage()).append('\n');}
        finally{ui(()->{if(source!=null&&!source.isFinishing())source.finish();if(reader!=null)reader.finish();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }

    /** Bounded source-permission search over this App's Hupu history only. */
    void runHupuHistory(Bundle args){
        report.setLength(0);report.append("PHONE HUPU HISTORY: up to4 recent source posts, flags/counts only; no cookie/user/body export\n");
        try{
            Context context=authorized(args);
            java.util.Map<String,Long> recent=new java.util.HashMap<>();
            for(java.util.Map.Entry<String,?> entry:context.getSharedPreferences("reading-history",0).getAll().entrySet()){
                String url=entry.getKey();if(!url.matches("https://bbs\\.hupu\\.com/[1-9][0-9]{0,19}(?:-[1-9][0-9]{0,5})?\\.html"))continue;
                JSONObject state=new JSONObject(String.valueOf(entry.getValue()));String id=url.substring(url.lastIndexOf('/')+1).replaceFirst("(?:-[0-9]+)?\\.html$","");
                recent.put(id,Math.max(recent.getOrDefault(id,0L),state.optLong("at")));
            }
            java.util.List<String> ids=new java.util.ArrayList<>(recent.keySet());ids.sort((a,b)->Long.compare(recent.get(b),recent.get(a)));
            report.append("HISTORY distinctSourcePosts=").append(ids.size()).append(" limit=4\n");
            check(!ids.isEmpty(),"at least one actual previously read Hupu post is available");
            int ready=0,signed=0,loginAudience=0,restrictedRendered=0;
            for(String id:ids.subList(0,Math.min(4,ids.size()))){
                String url="https://bbs.hupu.com/"+id+".html";
                source=test.startActivitySync(new Intent().setClassName(context.getPackageName(),"app.quietreader.LoginActivity").putExtra("source","HUPU").putExtra("url",url).putExtra("board",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                WebView origin=page(source);JSONObject actual=null;long deadline=SystemClock.elapsedRealtime()+25000;
                String script="JSON.stringify((()=>{try{const p=JSON.parse(document.querySelector('#__NEXT_DATA__')?.textContent||'{}').props?.pageProps,d=p?.detail,t=d?.thread,m=document.querySelector('[class*=post-content_main-post-info] .thread-content-detail'),visible=e=>!!e&&e.getClientRects().length>0&&getComputedStyle(e).visibility!=='hidden';return {ready:!!t,tid:String(t?.tid||''),signedIn:/^[1-9][0-9]*$/.test(String(d?.user?.puid||'')),detailRange:d?.visibleRange||'',threadRange:t?.visibleRange||'',success:p?.detailErrorInfo?.code===200,mainVisible:visible(m)&&!!m.innerText,mainChars:visible(m)?m.innerText.length:0,visibleReplies:Array.from(document.querySelectorAll('.reply-list-wrapper .thread-content-detail')).filter(visible).length}}catch(e){return {ready:false}}})())";
                while(SystemClock.elapsedRealtime()<deadline){if(origin!=null){actual=js(origin,script);if(actual.optBoolean("ready"))break;}Thread.sleep(250);}
                boolean matched=actual!=null&&actual.optBoolean("ready")&&id.equals(actual.optString("tid"));
                boolean logged=matched&&actual.optBoolean("signedIn"),login=matched&&(actual.optString("detailRange").equals("LOGIN_SEE")||actual.optString("threadRange").equals("LOGIN_SEE"));
                if(matched)ready++;if(logged)signed++;if(login)loginAudience++;
                boolean permitted=login&&logged&&actual.optBoolean("success")&&actual.optBoolean("mainVisible");if(permitted)restrictedRendered++;
                report.append("SOURCE publicPost=").append(id).append(" identityMatched=").append(matched).append(" signedIn=").append(logged).append(" loginAudience=").append(login).append(" permittedMain=").append(permitted).append(" mainChars=").append(actual==null?0:actual.optInt("mainChars")).append(" renderedReplies=").append(actual==null?0:actual.optInt("visibleReplies")).append('\n');
                ui(source::finish);source=null;
            }
            report.append("PERMISSION_SEARCH ready=").append(ready).append(" signedIn=").append(signed).append(" loginAudience=").append(loginAudience).append(" restrictedRendered=").append(restrictedRendered).append('\n');
            check(ready>0,"at least one recent Hupu source response is identity matched");
            report.append("LIMITATION source-only search; public responses cannot prove gated reader permissions; unavailable samples stay open\n");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage()).append('\n');}
        finally{ui(()->{if(source!=null&&!source.isFinishing())source.finish();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }

    private static String tiebaSection(String key)throws Exception{byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder hex=new StringBuilder();for(byte b:bytes)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return "tieba-post-"+hex.substring(0,24);}
    private static String sectionText(JSONObject section)throws Exception{StringBuilder text=new StringBuilder();JSONArray blocks=section.optJSONArray("blocks");if(blocks!=null)for(int n=0;n<blocks.length();n++){JSONObject block=blocks.getJSONObject(n);if(block.optString("type").equals("text"))text.append(block.optString("value"));}return norm(text.toString()).replace("\ufffc","");}
    void runTieba(Bundle args){
        report.setLength(0);report.append("PHONE TIEBA: explicit source floor identities and saved reader relations; no preference marker\n");
        try{
            Context context=authorized(args);String url="https://tieba.baidu.com/p/11137152004";int priorNested=0;long chosenAt=-1;
            // Inspect only Tieba history to select a previously observed nested-reply case.
            for(java.util.Map.Entry<String,?> entry:context.getSharedPreferences("reading-history",0).getAll().entrySet()){
                if(!entry.getKey().startsWith("https://tieba.baidu.com/"))continue;
                try{JSONObject saved=new JSONObject(String.valueOf(entry.getValue())),doc=saved.optJSONObject("document");if(doc==null)continue;String post=saved.optString("bodySource");if(!post.matches("https://tieba\\.baidu\\.com/p/[1-9][0-9]{0,19}"))continue;JSONArray parts=doc.optJSONArray("sections");int count=0;if(parts!=null)for(int n=0;n<parts.length();n++)if(parts.getJSONObject(n).optBoolean("nestedReply"))count++;if(count>0&&saved.optLong("at")>chosenAt){url=post;priorNested=count;chosenAt=saved.optLong("at");}}catch(JSONException ignored){}
            }
            report.append("SELECTION priorNested=").append(priorNested).append(" previouslyRead=").append(chosenAt>=0).append(" publicPost=").append(url.substring(url.lastIndexOf('/')+1)).append('\n');
            source=test.startActivitySync(new Intent().setClassName(context.getPackageName(),"app.quietreader.LoginActivity").putExtra("source","TIEBA").putExtra("url",url).putExtra("board",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));WebView origin=page(source);check(origin!=null,"actual shared Tieba source WebView exists");
            String script="JSON.stringify((()=>{const box=document.querySelector('.pc-pb-box'),rows=[];for(const body of Array.from(document.querySelectorAll('.pc-pb-reply-list .comment-content>.pb-rich-text')).slice(0,60)){const sub=body.closest('.pb-lzl-item'),floor=body.closest('.pb-comment-item');if(!floor)continue;let text=body.innerText||'',target='';const link=body.querySelector('a[href]');if(link){try{const u=new URL(link.href),name=link.innerText.trim(),plain=text.replace(/\\s+/g,'');if(u.hostname==='tieba.baidu.com'&&u.pathname==='/home/main'&&name&&(plain.startsWith('回复'+name+':')||plain.startsWith('回复'+name+'：'))){target=name;const mark=text.indexOf(name)+name.length;const tail=text.slice(mark).replace(/^\\s*[:：]\\s*/,'');text=tail}}catch(e){}}rows.push({nested:!!sub,parent:floor.dataset.id||'',child:sub?.dataset.id||'',text,target})}return {ready:!!box&&!!document.querySelector('.image-text>.pb-content-wrap')&&rows.length>0,modern:!!box,rows,gate:!!document.querySelector('.pc-pb-reply-list .login-guard-mask'),path:location.pathname}})())";
            JSONObject actual=null;long deadline=SystemClock.elapsedRealtime()+45000;
            while(SystemClock.elapsedRealtime()<deadline){actual=js(origin,script);if(actual.optBoolean("ready"))break;Thread.sleep(250);}
            report.append("SOURCE_DIAGNOSTIC modern=").append(actual!=null&&actual.optBoolean("modern")).append(" loginGuard=").append(actual!=null&&actual.optBoolean("gate")).append(" rows=").append(actual==null||actual.optJSONArray("rows")==null?0:actual.getJSONArray("rows").length()).append('\n');
            check(actual!=null&&actual.optBoolean("ready")&&actual.optString("path").equals(android.net.Uri.parse(url).getPath()),"identity-matched actual modern Tieba source has loaded replies");
            JSONArray expected=actual.getJSONArray("rows");int sourceNested=0;for(int n=0;n<expected.length();n++)if(expected.getJSONObject(n).optBoolean("nested"))sourceNested++;report.append("SOURCE nestedRows=").append(sourceNested).append('\n');
            ui(source::finish);JSONObject item=new JSONObject().put("source","TIEBA").put("url",url).put("title","贴吧已读帖子").put("detail","").put("video",false);
            long openedAt=System.currentTimeMillis();reader=test.startActivitySync(new Intent().setClassName(context.getPackageName(),"app.quietreader.MainActivity").putExtra("discovery_item",item.toString()).putExtra("direct_reader",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));verifyDetached(origin,args);reread();
            JSONObject body=null;WebView reading=null;deadline=SystemClock.elapsedRealtime()+45000;
            String layout="JSON.stringify((()=>{const nested=Array.from(document.querySelectorAll('main .part.nested-reply'));return {parts:document.querySelectorAll('main .part').length,nested:nested.length,compact:nested.every(e=>{const m=e.querySelector('.section-label');return !m||parseFloat(getComputedStyle(m).fontSize)<=14}),indented:nested.every(e=>parseFloat(getComputedStyle(e).marginLeft)>=10),overflow:document.documentElement.scrollWidth>innerWidth+2}})())";
            while(SystemClock.elapsedRealtime()<deadline){reading=page(reader);if(reading!=null){body=js(reading,layout);if(body.optInt("parts")>0)break;}Thread.sleep(250);}
            check(body!=null&&body.optInt("parts")>0,"ordinary Tieba reader obtains structured post content");check(!body.optBoolean("overflow"),"actual Tieba reader has no horizontal overflow");
            int initialParts=body.optInt("parts");Thread.sleep(1800);
            if("true".equals(args.getString("gestures"))){
                // Exercise real Activity touch/scroll handling, including canceling
                // history restoration. JS scrolling does not deliver those events.
                int[] bounds=new int[4];final WebView target=reading;ui(()->{int[] location=new int[2];target.getLocationOnScreen(location);bounds[0]=location[0];bounds[1]=location[1];bounds[2]=target.getWidth();bounds[3]=target.getHeight();});
                for(int swipe=0;swipe<4;swipe++){
                    float x=bounds[0]+bounds[2]*.5f,from=bounds[1]+bounds[3]*(swipe==0?.25f:.8f),to=bounds[1]+bounds[3]*(swipe==0?.8f:.25f);long time=SystemClock.uptimeMillis();
                    for(int step=0;step<=12;step++){int action=step==0?android.view.MotionEvent.ACTION_DOWN:step==12?android.view.MotionEvent.ACTION_UP:android.view.MotionEvent.ACTION_MOVE;android.view.MotionEvent event=android.view.MotionEvent.obtain(time,time+step*25,action,x,from+(to-from)*step/12f,0);try{test.sendPointerSync(event);}finally{event.recycle();}if(step<12)Thread.sleep(25);}
                    Thread.sleep(400);
                }
                report.append("SCROLL_INPUT real touch gestures including reverse then forward; no direct continuation call\n");
            }else js(reading,"JSON.stringify((()=>{window.scrollTo(0,document.documentElement.scrollHeight);return {scrolled:true}})())");
            deadline=SystemClock.elapsedRealtime()+23000;while(SystemClock.elapsedRealtime()<deadline){WebView current=page(reader);if(current!=null){reading=current;body=js(reading,layout);if(body.optInt("parts")>initialParts)break;}Thread.sleep(250);}
            report.append("CONTINUATION initialParts=").append(initialParts).append(" afterParts=").append(body.optInt("parts")).append("; real reader scroll event, no direct loadMore call\n");
            report.append("CONTINUATION_DIAGNOSTIC ").append(js(reading,"JSON.stringify((()=>{const c=document.querySelector('#reader-continuation');return {y:scrollY,height:innerHeight,contentHeight:document.documentElement.scrollHeight,state:c?.dataset.continuation||'',notice:c?.innerText||'',parts:document.querySelectorAll('main .part').length}})())")).append('\n');
            WebView[] hidden={null};ui(()->{WebView first=web(reader.getWindow().getDecorView(),false);if(first!=null&&!readerOrigin(first))hidden[0]=first;});
            if(hidden[0]!=null)report.append("SOURCE_WINDOW_DIAGNOSTIC ").append(js(hidden[0],"JSON.stringify((()=>{const box=document.querySelector('.pc-pb-box'),list=box?.querySelector('.pc-pb-reply-list .pb-comment-list'),rows=list?Array.from(list.querySelectorAll('.pb-comment-item[data-id]')).map(e=>e.getBoundingClientRect()).filter(r=>r.height>0):[],b=box?.getBoundingClientRect(),l=list?.getBoundingClientRect();return {path:location.pathname,box:!!box,list:!!list,rows:rows.length,y:box?.scrollTop,height:box?.clientHeight,contentHeight:box?.scrollHeight,firstTop:rows.length?Math.min(...rows.map(r=>r.top)):null,lastBottom:rows.length?Math.max(...rows.map(r=>r.bottom)):null,boxTop:b?.top,boxBottom:b?.bottom,listTop:l?.top,listBottom:l?.bottom,loginGuard:!!document.querySelector('.pc-pb-reply-list .login-guard-mask'),end:Array.from(box?.querySelectorAll('.pc-pb-reply-list .loading')||[]).some(e=>/已加载全部评论/.test(e.textContent))}})())")).append('\n');
            check(body.optInt("parts")>initialParts,"actual reader bottom scroll automatically appends source content");
            // Save through the real pause lifecycle; do not guess source-specific
            // authenticated disk-cache filenames or invoke private reader methods.
            check(test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME),"normal Home action pauses the reader for a reading-history snapshot");
            deadline=SystemClock.elapsedRealtime()+5000;JSONObject saved=null;final String historyUrl=url;int observedParts=body.optInt("parts");
            while(SystemClock.elapsedRealtime()<deadline){String state=context.getSharedPreferences("reading-history",0).getString(historyUrl,"");if(!state.isEmpty()){saved=new JSONObject(state);JSONObject snapshot=saved.optJSONObject("document");if(saved.optLong("at")>=openedAt&&saved.optLong("bodyAt")>=openedAt&&snapshot!=null&&snapshot.getJSONArray("sections").length()>=observedParts)break;}Thread.sleep(100);}
            check(saved!=null&&saved.optLong("at")>=openedAt&&saved.optLong("bodyAt")>=openedAt&&saved.optJSONObject("document")!=null&&saved.getJSONObject("document").getJSONArray("sections").length()>=observedParts,"normal pause saves a fresh reading-history body including automatically appended sections");JSONObject doc=saved.getJSONObject("document");check(doc.optInt("tiebaReplyRevision")==TiebaReplyRelations.REVISION,"saved real Tieba body has current relation revision");JSONArray parts=doc.getJSONArray("sections");
            java.util.Set<String> sourceFloors=new java.util.LinkedHashSet<>(),cachedFloors=new java.util.LinkedHashSet<>();for(int n=0;n<expected.length();n++)sourceFloors.add(expected.getJSONObject(n).optString("parent"));for(int n=0;n<parts.length();n++){String post=parts.getJSONObject(n).optString("sourcePostId");if(!post.isEmpty())cachedFloors.add(post);}report.append("FLOOR_DIAGNOSTIC sourceIds=").append(sourceFloors).append(" savedIds=").append(cachedFloors).append(" savedParts=").append(parts.length()).append('\n');
            int floorChecked=0,childrenChecked=0,notObserved=0,ambiguous=0,outsideWindow=0;
            for(int n=0;n<expected.length();n++){
                JSONObject row=expected.getJSONObject(n);String parent=row.optString("parent");if(!parent.matches("[1-9][0-9]{0,19}"))continue;
                if(!row.optBoolean("nested")){for(int j=0;j<parts.length();j++){JSONObject part=parts.getJSONObject(j);if(parent.equals(part.optString("sourcePostId"))){check(!part.optBoolean("nestedReply")&&part.optString("parentSectionId").isEmpty(),"source explicit top-level floor stays top-level");floorChecked++;break;}}continue;}
                String text=norm(row.optString("text")).replace("\ufffc","");if(text.isEmpty())continue;String child=row.optString("child"),expectedId=child.matches("[1-9][0-9]{0,19}")?tiebaSection("lzl:"+child):"";JSONObject matched=null;int matches=0;
                String parentIdentity=tiebaSection("post_content_"+parent);boolean parentSaved=false;for(int j=0;j<parts.length();j++)if(parentIdentity.equals(parts.getJSONObject(j).optString("id")))parentSaved=true;
                if(expectedId.isEmpty()){if(!parentSaved){outsideWindow++;continue;}int sourceMatches=0;for(int j=0;j<expected.length();j++)if(text.equals(norm(expected.getJSONObject(j).optString("text")).replace("\ufffc","")))sourceMatches++;if(text.length()<12||sourceMatches!=1){ambiguous++;continue;}}
                for(int j=0;j<parts.length();j++){JSONObject part=parts.getJSONObject(j);if(!expectedId.isEmpty()?expectedId.equals(part.optString("id")):text.equals(sectionText(part))){matched=part;matches++;}}
                if(matches==0){notObserved++;continue;}if(matches!=1){ambiguous++;continue;}
                report.append("CHILD_DIAGNOSTIC explicitChildId=").append(!expectedId.isEmpty()).append(" textChars=").append(text.length()).append(" matchedNested=").append(matched.optBoolean("nestedReply")).append(" expectedParentSaved=").append(parentSaved).append(" parentPresent=").append(!matched.optString("parentSectionId").isEmpty()).append(" parentMatches=").append(parentIdentity.equals(matched.optString("parentSectionId"))).append('\n');
                check(matched.optBoolean("nestedReply")&&parentIdentity.equals(matched.optString("parentSectionId")),"actual source child retains its explicit parent floor identity");check(row.optString("target").equals(matched.optString("replyToAuthor")),"only an explicit source reply target becomes target metadata");childrenChecked++;
            }
            report.append("RELATIONS sourceNested=").append(sourceNested).append(" topFloorsChecked=").append(floorChecked).append(" childrenChecked=").append(childrenChecked).append(" notObserved=").append(notObserved).append(" ambiguous=").append(ambiguous).append(" outsideWindow=").append(outsideWindow).append(" renderedNested=").append(body.optInt("nested")).append('\n');
            check(floorChecked>0,"at least one actual source floor identity was compared");check(sourceNested>0&&childrenChecked>0,"at least one actual nested child-parent relation was compared");check(body.optInt("nested")>0&&body.optBoolean("compact")&&body.optBoolean("indented"),"actual nested replies use compact labels and a visible indentation");
            report.append("LIMITATION only loaded source rows and uniquely matched children; no complete pagination, login-exclusive or emoji-only relation claim\n");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage()).append('\n');}
        finally{ui(()->{if(source!=null&&!source.isFinishing())source.finish();if(reader!=null&&!reader.isFinishing())reader.finish();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
