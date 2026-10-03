package app.quietreader;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;

/** Black-box checks of the exact minified, personally signed release APK. No app reflection. */
public final class ReleaseInstrumentation extends Instrumentation {
    private int assertions;
    private boolean offline;
    private Activity foreground;
    private String mode="";
    private String sourceFilter="all";
    private boolean wallstreetOriginal;
    private boolean fixedTieba;
    private boolean weiboProbe;
    private boolean weiboFulltextProbe;
    private boolean weiboReaderJourney;
    private boolean smzdmImageProbe;
    private boolean smzdmImageJourney;
    private WebView weiboSourceWeb;
    private final StringBuffer weiboSourceEvents=new StringBuffer();
    private long weiboSourceBegan;
    @Override public void callActivityOnCreate(Activity activity,Bundle state){
        super.callActivityOnCreate(activity,state);
        if(weiboProbe&&activity.getClass().getName().equals("app.quietreader.LoginActivity"))attachWeiboObserver(activity);
    }
    private String outputDirectory="release-qa";
    private final StringBuilder readerTapDiagnostics=new StringBuilder();
    @Override public void callActivityOnResume(Activity activity){super.callActivityOnResume(activity);foreground=activity;}
    private View nativeView(View view,String needle,boolean exact){
        if(view==null||!view.isShown())return null;
        String text=view instanceof android.widget.TextView?((android.widget.TextView)view).getText().toString():"";
        String desc=view.getContentDescription()==null?"":view.getContentDescription().toString();
        if(exact?(text.equals(needle)||desc.equals(needle)):(text.contains(needle)||desc.contains(needle)))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=nativeView(((ViewGroup)view).getChildAt(i),needle,exact);if(found!=null)return found;}
        return null;
    }
    private AccessibilityNodeInfo snapshot() {
        // API 35 may retain the removed WebView's accessibility subtree after a page/font change.
        if(android.os.Build.VERSION.SDK_INT>=35)getUiAutomation().clearCache();
        return getUiAutomation().getRootInActiveWindow();
    }
    private WebView findWeb(View view) {
        if(view instanceof WebView)return (WebView)view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){WebView web=findWeb(((ViewGroup)view).getChildAt(i));if(web!=null)return web;}
        return null;
    }
    private WebView findOwnReader(View view){
        if(view instanceof WebView){String url=((WebView)view).getUrl();if(url!=null&&url.startsWith("https://quiet-reader.invalid/?render="))return (WebView)view;}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){WebView found=findOwnReader(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;
    }
@Override public void onCreate(Bundle args){super.onCreate(args);smzdmImageJourney=args!=null&&"1".equals(args.getString("smzdmImageJourney","0"));smzdmImageProbe=args!=null&&"1".equals(args.getString("smzdmImageProbe","0"));weiboReaderJourney=args!=null&&"1".equals(args.getString("weiboReaderJourney","0"));mode=args==null?"":args.getString("mode","");sourceFilter=args==null?"all":args.getString("source","all");wallstreetOriginal=args!=null&&"1".equals(args.getString("wallstreetOriginal","0"));fixedTieba=args!=null&&"1".equals(args.getString("fixedTieba","0"));weiboFulltextProbe=args!=null&&"1".equals(args.getString("weiboFulltextProbe","0"));weiboProbe=weiboFulltextProbe||args!=null&&"1".equals(args.getString("weiboProbe","0"));offline=mode.equals("offline");if(mode.equals("sources")||mode.equals("normal")||mode.equals("branding")){String runId=args==null?"":args.getString("runId","");if(!runId.matches("[0-9]{10,20}"))runId=Long.toString(System.currentTimeMillis());outputDirectory+=(mode.equals("sources")?"/sources-":"/normal-")+runId;}start();}
    private org.json.JSONObject ownReaderDom(String expression)throws Exception{
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);String[] raw={null};WebView[] target={null};Throwable[] error={null};
        runOnMainSync(()->{try{WebView web=findOwnReader(foreground.getWindow().getDecorView());check(web!=null,"Inspection restricted to own escaped reader, not hidden source/login page");check(!web.getSettings().getJavaScriptEnabled(),"Reader scripting off before independent content inspection");target[0]=web;web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("JSON.stringify("+expression+")",value->{web.getSettings().setJavaScriptEnabled(false);raw[0]=value;done.countDown();});}catch(Throwable failure){error[0]=failure;done.countDown();}});
        try{check(done.await(5,java.util.concurrent.TimeUnit.SECONDS),"Reader DOM inspection timeout");if(error[0]!=null)throw new Exception(error[0]);return new org.json.JSONObject((String)new org.json.JSONTokener(raw[0]).nextValue());}finally{runOnMainSync(()->{if(target[0]!=null)target[0].getSettings().setJavaScriptEnabled(false);});}
    }
    private static final class SectionTarget {
        final boolean secondAnswer;
        final boolean requireMain;
        int sectionIndex;
        String id="",key="",source="",initialLabel="",anchor="";
        SectionTarget(boolean secondAnswer){this(secondAnswer,false);}
        SectionTarget(boolean secondAnswer,boolean requireMain){this.secondAnswer=secondAnswer;this.requireMain=requireMain;}
    }
    private static boolean substantiveText(String text){
        // A real short answer is valid; emoji, punctuation, whitespace and keycap emojis alone are not.
        for(int offset=0;offset<text.length();){int cp=text.codePointAt(offset);offset+=Character.charCount(cp);
            int next=offset;if(next<text.length()&&text.codePointAt(next)==0xFE0F)next+=Character.charCount(0xFE0F);
            if(next<text.length()&&text.codePointAt(next)==0x20E3)continue;
            if(Character.isLetterOrDigit(cp))return true;
        }return false;
    }
    private static boolean meaningfulBody(org.json.JSONObject body,boolean requireText){return body.optInt("substantiveParagraphs")>0||!requireText&&body.optInt("loadedContentImages")>0;}
    private void sourceContentPolicyChecks()throws Exception{
        check(!substantiveText("🐎 😂 👩‍👩‍👧‍👦 ❤️ 👍🏽"),"Emoji-only reactions are not substantive article text");
        check(!substantiveText("  …。！？——\n"),"Punctuation and whitespace do not certify body text");
        check(!substantiveText("1️⃣ 2⃣ #️⃣"),"Keycap emoji digits are not substantive text");
        check(substantiveText("好")&&substantiveText("No")&&substantiveText("42"),"Genuine short textual replies are not discarded by arbitrary length thresholds");
        check(substantiveText("🐎 已收藏"),"Mixed emoji and real text retains its textual contribution");
        org.json.JSONObject emoji=new org.json.JSONObject().put("paragraphs",1).put("loadedImages",1).put("substantiveParagraphs",0).put("loadedContentImages",0);
        check(!meaningfulBody(emoji,false),"A paragraph or decoded inline emoji alone cannot certify readable body");
        emoji.put("loadedContentImages",1);
        check(meaningfulBody(emoji,false)&&!meaningfulBody(emoji,true),"A decoded content image qualifies general body but not the required actual-answer text");
    }
    private static final class SourceContentUnavailable extends Exception {
        final org.json.JSONObject observation;
        SourceContentUnavailable(org.json.JSONObject observation){super(observation.optString("contentOutcome")+"; selected main-post content is not certified; notice="+observation.optString("notice"));this.observation=observation;}
    }
    private org.json.JSONObject revealReadableContent(String source)throws Exception{
        SectionTarget target=new SectionTarget(false,source.equals("HUPU"));org.json.JSONObject body=readBoundSection(target);
        if(!body.optString("contentOutcome").isEmpty())throw new SourceContentUnavailable(body);
        check(meaningfulBody(body,false),"Selected own-reader section must contain substantive non-preview text or a decoded non-emoji image");
        body.put("expandedControl",target.initialLabel);body.put("verifiedSection",target.id);return body;
    }
    private org.json.JSONObject verifyFixedTiebaSecondFloor()throws Exception{
        SectionTarget target=new SectionTarget(false);target.sectionIndex=1;
        org.json.JSONObject body=readBoundSection(target);
        check(body.optString("sourceHref").equals("https://tieba.baidu.com/p/11061609054"),"Tieba regression must remain on exact observed source URL");
        check(body.optString("id").equals("part-1")&&body.optInt("substantiveParagraphs")>0,"Second real floor must be expanded with non-preview text, not another floor");
        check(body.optString("firstText").contains("杰克第18话"),"Observed second-floor body identity must match, not a substituted sample");
        check(body.optInt("contentImages")>0,"Second floor must retain its article images; decode is observed separately");
        org.json.JSONObject clean=ownReaderDom("(function(){var p=document.querySelector('meta[name=quiet-reader-source]'),e=document.getElementById('part-1'),text=e?e.textContent:'';return {source:p?p.content:'',ready:document.readyState,hasSection:!!e,paragraphs:e?e.querySelectorAll('p:not(.preview)').length:0,badgePollution:/贴吧成长等级|本吧头衔/.test(text)};})()");
        check(clean.optString("source").equals("https://tieba.baidu.com/p/11061609054")&&clean.optString("ready").equals("complete")&&clean.optBoolean("hasSection")&&clean.optInt("paragraphs")>0,"Positive ready/content identity required before absence check");
        check(!clean.optBoolean("badgePollution"),"Observed second floor must no longer expose badge tooltip text");
        body.put("metadataObservation",clean);return body;
    }
    private org.json.JSONObject inspectAndTapSection(SectionTarget section,int attempt)throws Exception{
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        org.json.JSONObject[] result={null};Throwable[] error={null};WebView[] target={null};
        java.util.concurrent.atomic.AtomicBoolean ownsScriptWindow=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean inspectionActive=new java.util.concurrent.atomic.AtomicBoolean(true);
        // Bind the selected section, not "any body anywhere". ID + immutable control label
        // + source URL survive own-reader revisions; an expanded anchor adds another guard.
        String expression="(function(){var all=document.querySelectorAll('section.part'),a=[],main=null,roles=[],replies=0;"
            +"for(var i=0;i<all.length;i++){var h=all[i].querySelector('a.action'),role=all[i].getAttribute('data-section-label')||(h?h.textContent:'');roles.push(role);if(/^回答/.test(role))a.push(all[i]);if(!main&&/^主帖/.test(role))main=all[i];if(/^回复/.test(role))replies++;}"
            +"var bound="+org.json.JSONObject.quote(section.id)+",e=bound?document.getElementById(bound):("+section.secondAnswer+"?(a.length>1?a[1]:null):("+section.requireMain+"?main:(all["+section.sectionIndex+"]||null)));"
            +"var h=e?e.querySelector('a.action'):null,r=h?h.getBoundingClientRect():null,p=e?e.querySelectorAll('p:not(.preview)'):[],im=e?e.querySelectorAll('img'):[],n=0,first='',firstNode=null,loaded=0,contentImages=0,loadedContent=0,details=[],texts=[];"
            +"for(var j=0;j<p.length;j++){var text=p[j].textContent.trim();if(text){n++;texts.push(text);if(!first){first=text.slice(0,200);firstNode=p[j];}}}"
            +"for(var k=0;k<im.length;k++){var emoji=im[k].classList.contains('emoji'),decoded=im[k].complete&&im[k].naturalWidth>0&&im[k].naturalHeight>0;if(decoded)loaded++;if(!emoji){contentImages++;if(decoded)loadedContent++;if(!firstNode)firstNode=im[k];}details.push({src:im[k].src,naturalWidth:im[k].naturalWidth,naturalHeight:im[k].naturalHeight,complete:im[k].complete,emoji:emoji});}"
            +"var fr=firstNode?firstNode.getBoundingClientRect():null,provenance=document.querySelector('meta[name=quiet-reader-source]'),source=provenance?provenance.content:'',notice=[],ns=document.querySelectorAll('.notice');for(var q=0;q<ns.length;q++)notice.push(ns[q].textContent);"
            +"var label=h?h.textContent:'',role=e?(e.getAttribute('data-section-label')||label):'',anchor=e?e.querySelector('[data-reading-anchor]'):null;"
            +"return {href:location.href,scrollY:scrollY,answerCards:a.length,isSecond:!!e&&a.length>1&&e===a[1],hasMain:!!main,replyCards:replies,sectionRoles:roles,role:role,id:e?e.id:'',label:label,key:role||label.replace(/ · (展开阅读 ↓|收起 ↑)$/,''),anchor:anchor?anchor.id:'',sourceHref:source,paragraphs:n,paragraphTexts:texts,firstText:first,images:im.length,loadedImages:loaded,contentImages:contentImages,loadedContentImages:loadedContent,imageDetails:details,notice:notice.join(' '),hasBody:!!firstNode,bodyTop:fr?fr.top:0,bodyBottom:fr?fr.bottom:0,left:r?r.left:0,top:r?r.top:0,right:r?r.right:0,bottom:r?r.bottom:0};})()";
        runOnMainSync(()->{try{
            WebView web=findOwnReader(foreground.getWindow().getDecorView());target[0]=web;
            if(web==null){result[0]=new org.json.JSONObject().put("retry","no-own-reader");done.countDown();return;}
            if(web.getSettings().getJavaScriptEnabled()){
                readerTapDiagnostics.append("attempt=").append(attempt).append(" own-reader native script window busy; did not change settings\n");
                result[0]=new org.json.JSONObject().put("retry","native-script-window-busy");done.countDown();return;
            }
            check(!web.getSettings().getJavaScriptEnabled(),"Reader scripting off before bound-section inspection");
            ownsScriptWindow.set(true);
            web.getSettings().setJavaScriptEnabled(true);
            web.evaluateJavascript("JSON.stringify("+expression+")",raw->{try{
                if(ownsScriptWindow.compareAndSet(true,false))web.getSettings().setJavaScriptEnabled(false);
                if(!inspectionActive.get())return; // A timed-out inspection must never click later.
                check(!web.getSettings().getJavaScriptEnabled(),"Own-reader scripting off after bound-section inspection");
                Object decoded=new org.json.JSONTokener(raw).nextValue();
                if(!(decoded instanceof String))throw new AssertionError("Bound-section DOM returned no JSON string");
                org.json.JSONObject box=new org.json.JSONObject((String)decoded);result[0]=box;
                org.json.JSONArray texts=box.optJSONArray("paragraphTexts");int substantive=0;
                if(texts!=null)for(int i=0;i<texts.length();i++)if(substantiveText(texts.optString(i)))substantive++;
                box.remove("paragraphTexts"); // Never dump complete article text into diagnostic logs.
                box.put("substantiveParagraphs",substantive);
                String url=web.getUrl(),observed=box.optString("href");
                boolean own=url!=null&&url.startsWith("https://quiet-reader.invalid/?render=");
                boolean same=own&&url.equals(observed)&&web==findOwnReader(foreground.getWindow().getDecorView());
                float scale=web.getScale();int nativeY=web.getScrollY();
                android.graphics.Rect visible=new android.graphics.Rect();int[] at=new int[2];web.getLocationOnScreen(at);
                boolean shown=web.isShown()&&web.getGlobalVisibleRect(visible)&&web.hasWindowFocus();
                float x=at[0]+(float)((box.optDouble("left")+box.optDouble("right"))/2)*scale;
                float y=at[1]+(float)((box.optDouble("top")+box.optDouble("bottom"))/2)*scale;
                boolean aligned=Math.abs(nativeY-box.optDouble("scrollY")*scale)<5;
                boolean inside=shown&&visible.contains((int)x,(int)y)&&x>visible.left+4&&x<visible.right-4&&y>visible.top+4&&y<visible.bottom-4;
                readerTapDiagnostics.append(section.secondAnswer?"second-answer":"first-section").append(" attempt=").append(attempt).append(" ownURL=").append(own?url:"<not-own-reader>")
                    .append(" sameRevision=").append(same).append(" nativeY=").append(nativeY).append(" domY=").append(box.optDouble("scrollY"))
                    .append(" scale=").append(scale).append(" point=").append(x).append(',').append(y).append(" visible=").append(visible.toShortString())
                    .append(" focused=").append(shown).append(" aligned=").append(aligned).append(" inside=").append(inside).append('\n');
                if(!same||!shown||!aligned){box.put("retry","revision-or-layout-changed");return;}
                if(section.requireMain&&!box.optBoolean("hasMain")){
                    box.put("contentOutcome",box.optString("notice").contains("视频")?"MEDIA_LIMITED_NO_MAIN_TEXT":box.optInt("replyCards")>0?"REPLIES_ONLY_NO_MAIN_CONTENT":"MAIN_CONTENT_IDENTITY_UNVERIFIED");return;
                }
                if(box.optString("id").isEmpty()||section.secondAnswer&&!box.optBoolean("isSecond")){box.put("retry","target-section-not-present");return;}
                if(section.id.isEmpty()){section.id=box.getString("id");section.key=box.getString("key");section.source=box.getString("sourceHref");section.initialLabel=box.getString("label");}
                check(section.id.equals(box.getString("id"))&&section.key.equals(box.getString("key"))&&section.source.equals(box.getString("sourceHref")),"Selected section identity changed during reflow; cannot count a different card as readable");
                if(!section.anchor.isEmpty())check(section.anchor.equals(box.getString("anchor")),"Expanded section anchor changed during inspection");
                if(!box.optString("anchor").isEmpty())section.anchor=box.getString("anchor");
                boolean readable=meaningfulBody(box,section.secondAnswer);
                if(readable){
                    float bodyTop=at[1]+(float)box.optDouble("bodyTop")*scale,bodyBottom=at[1]+(float)box.optDouble("bodyBottom")*scale;
                    if(bodyBottom<=visible.top+8||bodyTop>=visible.bottom-8){web.scrollTo(0,Math.max(0,(int)((box.optDouble("bodyTop")+box.optDouble("scrollY"))*scale)-120));box.put("retry","show-bound-body");}
                    return;
                }
                if(box.optBoolean("hasBody")&&!box.optString("label").contains("展开阅读")){
                    if(box.optInt("contentImages")==0){box.put("contentOutcome",section.requireMain&&box.optString("notice").contains("视频")?"MEDIA_LIMITED_NO_SUBSTANTIVE_MAIN_TEXT":"NO_SUBSTANTIVE_TEXT_OR_CONTENT_IMAGE");return;}
                    // Bring a lazy image into view, but keep the global attempt/time bound.
                    float imageTop=at[1]+(float)box.optDouble("bodyTop")*scale;
                    if(imageTop<visible.top||imageTop>visible.bottom-8)web.scrollTo(0,Math.max(0,(int)((box.optDouble("bodyTop")+box.optDouble("scrollY"))*scale)-120));
                    box.put("retry","await-bound-image-decode");return;
                }
                if(!inside){web.scrollTo(0,Math.max(0,(int)((box.optDouble("top")+box.optDouble("scrollY"))*scale)-120));box.put("retry","repositioned");return;}
                if(!box.optString("label").contains("展开阅读")){box.put("retry","await-expanded-body");return;}
                // Inject actual pointer events in this same UI callback after all geometry checks.
                // Async injection avoids waiting for the main thread to consume its own event.
                long now=android.os.SystemClock.uptimeMillis();
                android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,x,y,0);
                android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+60,android.view.MotionEvent.ACTION_UP,x,y,0);
                down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                try{boolean sentDown=getUiAutomation().injectInputEvent(down,false),sentUp=getUiAutomation().injectInputEvent(up,false);box.put("retry",sentDown&&sentUp?"pointer-injected":"pointer-not-accepted");}
                catch(SecurityException denied){box.put("retry","pointer-window-changed");readerTapDiagnostics.append("pointer injection rejected: window ownership changed\n");}
                finally{down.recycle();up.recycle();}
            }catch(Throwable failure){error[0]=failure;}finally{done.countDown();}});
        }catch(Throwable failure){error[0]=failure;done.countDown();}});
        try{check(done.await(5,java.util.concurrent.TimeUnit.SECONDS),"Bound-section geometry inspection timed out");if(error[0]!=null)throw new Exception(error[0]);return result[0];}
        finally{inspectionActive.set(false);runOnMainSync(()->{if(target[0]!=null&&ownsScriptWindow.compareAndSet(true,false))target[0].getSettings().setJavaScriptEnabled(false);});}
    }
    private org.json.JSONObject readBoundSection(SectionTarget target)throws Exception{
        org.json.JSONObject body=null;long deadline=android.os.SystemClock.elapsedRealtime()+15000;
        for(int attempt=1;attempt<=10;attempt++){
            body=inspectAndTapSection(target,attempt);
            if(!body.has("retry")&&(!body.optString("contentOutcome").isEmpty()||meaningfulBody(body,target.secondAnswer)))return body;
            if(android.os.SystemClock.elapsedRealtime()>=deadline)break;
            Thread.sleep(450);
        }
        throw new AssertionError("Bound "+(target.secondAnswer?"second actual answer":"first section")+" did not reveal readable body within bound; state="+(body==null?"no-snapshot":body.optString("retry")));
    }
    private org.json.JSONObject verifySecondZhihuAnswer()throws Exception{
        org.json.JSONObject answers=readBoundSection(new SectionTarget(true));
        check(answers.optInt("answerCards")>=2,"Question supplement is not an answer; at least two actual answer cards required");
        check(answers.optString("contentOutcome").isEmpty()&&answers.optInt("substantiveParagraphs")>0,"Second actual Zhihu answer must expand into substantive non-preview text, not question text or emoji alone");
        org.json.JSONObject body=new org.json.JSONObject().put("paragraphs",answers.getInt("paragraphs")).put("firstText",answers.getString("firstText"));
        return new org.json.JSONObject().put("answerCards",answers.getInt("answerCards")).put("id",answers.getString("id")).put("label",answers.getString("label")).put("secondAnswerBody",body);
    }
    /** Bounded, own-document-only image observation; no source scripts, forced fetch or cookie access. */
    private org.json.JSONObject inspectFirstArticleImage(org.json.JSONObject body,long remainingMs)throws Exception{
        return inspectFirstArticleImage(body,remainingMs,false);
    }
    private org.json.JSONObject inspectFirstArticleImage(org.json.JSONObject body,long remainingMs,boolean clickCaption)throws Exception{
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        org.json.JSONObject[] result={null};Throwable[] error={null};WebView[] target={null};
        java.util.concurrent.atomic.AtomicBoolean ownsScriptWindow=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean active=new java.util.concurrent.atomic.AtomicBoolean(true);
        String expression="(function(){var e=document.getElementById("+org.json.JSONObject.quote(body.getString("verifiedSection"))+"),h=e?e.querySelector('a.action'):null,im=e?e.querySelector('img:not(.emoji)'):null,r=im?im.getBoundingClientRect():null,f=im?im.closest('figure'):null,c=f?f.querySelector('figcaption a'):null,cr=c?c.getBoundingClientRect():null,provenance=document.querySelector('meta[name=quiet-reader-source]'),source=provenance?provenance.content:'';return {href:location.href,scrollY:scrollY,id:e?e.id:'',key:e?(e.getAttribute('data-section-label')||(h?h.textContent.replace(/ · (展开阅读 ↓|收起 ↑)$/,''):'')):'',sourceHref:source,present:!!im,src:im?im.src:'',naturalWidth:im?im.naturalWidth:0,naturalHeight:im?im.naturalHeight:0,complete:!!im&&im.complete,top:r?r.top:0,bottom:r?r.bottom:0,height:r?r.height:0,captionHref:c?c.href:'',captionText:c?c.textContent:'',captionLeft:cr?cr.left:0,captionRight:cr?cr.right:0,captionTop:cr?cr.top:0,captionBottom:cr?cr.bottom:0};})()";
        runOnMainSync(()->{try{
            WebView web=findOwnReader(foreground.getWindow().getDecorView());target[0]=web;
            if(web==null||web.getSettings().getJavaScriptEnabled()){result[0]=new org.json.JSONObject().put("retry","no-reader-or-native-script-busy");done.countDown();return;}
            check(!web.getSettings().getJavaScriptEnabled(),"Image inspection starts with own-reader JS off");
            ownsScriptWindow.set(true);web.getSettings().setJavaScriptEnabled(true);
            web.evaluateJavascript("JSON.stringify("+expression+")",raw->{try{
                if(ownsScriptWindow.compareAndSet(true,false))web.getSettings().setJavaScriptEnabled(false);
                if(!active.get())return;
                check(!web.getSettings().getJavaScriptEnabled(),"Image inspection returns own-reader JS off");
                Object decoded=new org.json.JSONTokener(raw).nextValue();if(!(decoded instanceof String))throw new AssertionError("Image observation returned no JSON string");
                org.json.JSONObject state=new org.json.JSONObject((String)decoded);result[0]=state;
                if(!state.optString("href").equals(web.getUrl())||web!=findOwnReader(foreground.getWindow().getDecorView())){state.put("retry","reader-revision-changed");return;}
                check(body.getString("verifiedSection").equals(state.optString("id"))&&body.getString("key").equals(state.optString("key"))&&body.getString("sourceHref").equals(state.optString("sourceHref")),"First image must belong to the previously verified section");
                if(!state.optBoolean("present")){state.put("retry","no-nonemoji-image");return;}
                android.graphics.Rect visible=new android.graphics.Rect();int[] at=new int[2];web.getLocationOnScreen(at);
                float scale=web.getScale();boolean shown=web.isShown()&&web.hasWindowFocus()&&web.getGlobalVisibleRect(visible);
                boolean aligned=Math.abs(web.getScrollY()-state.optDouble("scrollY")*scale)<5;
                float top=at[1]+(float)state.optDouble("top")*scale,bottom=at[1]+(float)state.optDouble("bottom")*scale;
                boolean inViewport=shown&&aligned&&top>=visible.top+4&&top<visible.bottom-24;
                state.put("inViewport",inViewport).put("visible",visible.toShortString()).put("nativeY",web.getScrollY());
                if(!shown||!aligned){state.put("retry","image-layout-changed");return;}
                if(clickCaption){
                    check(body.getString("expectedFirstImageSrc").equals(state.optString("src"))&&state.optString("captionHref").equals(state.optString("src"))&&state.optString("captionText").equals("查看大图"),"Real preview entry must be the observed first image caption, not a different link");
                    float x=at[0]+(float)((state.optDouble("captionLeft")+state.optDouble("captionRight"))/2)*scale,y=at[1]+(float)((state.optDouble("captionTop")+state.optDouble("captionBottom"))/2)*scale;
                    if(!visible.contains((int)x,(int)y)||y<visible.top+4||y>visible.bottom-4){web.scrollTo(0,Math.max(0,(int)((state.optDouble("captionTop")+state.optDouble("scrollY"))*scale)-120));state.put("retry","caption-repositioned");return;}
                    readerTapDiagnostics.append("first-image caption ownURL=").append(web.getUrl()).append(" point=").append(x).append(',').append(y).append(" screenVisible=").append(visible.toShortString()).append('\n');
                    long now=android.os.SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,x,y,0),up=android.view.MotionEvent.obtain(now,now+60,android.view.MotionEvent.ACTION_UP,x,y,0);down.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);up.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                    try{state.put("captionTapped",getUiAutomation().injectInputEvent(down,false)&&getUiAutomation().injectInputEvent(up,false));}finally{down.recycle();up.recycle();}
                    return;
                }
                if(!inViewport){web.scrollTo(0,Math.max(0,(int)((state.optDouble("top")+state.optDouble("scrollY"))*scale)-80));state.put("retry","image-repositioned");return;}
                state.put("decoded",state.optBoolean("complete")&&state.optInt("naturalWidth")>0&&state.optInt("naturalHeight")>0&&bottom>top);
            }catch(Throwable failure){error[0]=failure;}finally{done.countDown();}});
        }catch(Throwable failure){error[0]=failure;done.countDown();}});
        try{
            if(!done.await(Math.max(1,Math.min(2000,remainingMs)),java.util.concurrent.TimeUnit.MILLISECONDS))return new org.json.JSONObject().put("retry","image-observation-timeout");
            if(error[0]!=null)throw new Exception(error[0]);return result[0];
        }finally{active.set(false);runOnMainSync(()->{if(target[0]!=null&&ownsScriptWindow.compareAndSet(true,false))target[0].getSettings().setJavaScriptEnabled(false);});}
    }
    private org.json.JSONObject observeFirstArticleImage(org.json.JSONObject body,StringBuilder report)throws Exception{
        long start=android.os.SystemClock.elapsedRealtime(),deadline=start+12000;String firstSrc="";org.json.JSONObject state=null;
        for(int attempt=1;attempt<=26;attempt++){
            long remaining=deadline-android.os.SystemClock.elapsedRealtime();if(remaining<=0)break;
            state=inspectFirstArticleImage(body,remaining);
            String src=state.optString("src");if(!src.isEmpty()){if(firstSrc.isEmpty())firstSrc=src;check(firstSrc.equals(src),"Observed first image identity must not change");}
            sourceCheckpoint(report,"FIRST_IMAGE sample="+attempt+"; elapsedMs="+(android.os.SystemClock.elapsedRealtime()-start)+"; state="+(weiboReaderJourney?privateSafeImage(state):state));
            if(!state.has("retry")&&state.optBoolean("decoded"))return state;
            remaining=deadline-android.os.SystemClock.elapsedRealtime();if(remaining<=0)break;Thread.sleep(Math.min(500,remaining));
        }
        return (state==null?new org.json.JSONObject():state).put("observationExpired",true).put("decoded",false);
    }
    private void verifyReleaseImagePreview(org.json.JSONObject body,org.json.JSONObject image,StringBuilder report,int sourceNumber)throws Exception{
        body.put("expectedFirstImageSrc",image.getString("src"));org.json.JSONObject caption=null;
        for(int n=0;n<8;n++){caption=inspectFirstArticleImage(body,2000,true);if(caption.optBoolean("captionTapped"))break;Thread.sleep(300);}
        check(caption!=null&&caption.optBoolean("captionTapped"),"Exact first-image caption must accept a real bounded pointer tap");
        String readerHref=caption.getString("href");sourceCheckpoint(report,"IMAGE_PREVIEW caption tapped for "+(weiboReaderJourney?safeSourceAddress(image.getString("src")):image.getString("src"))+"; awaiting native decoded-image status");
        await("正文图片 · 双指缩放",true,8);await("图片已显示",false,15);
        if(weiboReaderJourney)verifyDecodedWeiboPreview(image,report);
        sourceCheckpoint(report,"IMAGE_PREVIEW native ready status visible; screenshot must independently show the actual image");screenshot("source-"+sourceNumber+"-image-preview");
        tap("关闭");
        if(weiboReaderJourney||smzdmImageJourney){
            // Capture BEFORE inspectFirstArticleImage can reposition the viewport.
            org.json.JSONObject closed=ownReaderDom("(function(){var e=document.getElementById("+org.json.JSONObject.quote(body.getString("verifiedSection"))+"),im=e?e.querySelector('img:not(.emoji)'):null,r=im?im.getBoundingClientRect():null;return {href:location.href,source:(document.querySelector('meta[name=quiet-reader-source]')||{}).content||'',src:im?im.src:'',scrollY:scrollY,top:r?r.top:null,width:r?r.width:0,height:r?r.height:0,naturalWidth:im?im.naturalWidth:0,naturalHeight:im?im.naturalHeight:0};})()");
            double scrollDelta=Math.abs(closed.getDouble("scrollY")-caption.getDouble("scrollY")),anchorDelta=Math.abs(closed.getDouble("top")-caption.getDouble("top"));
            sourceCheckpoint(report,"IMAGE_PREVIEW_UNMOVED_RETURN scrollDeltaCss="+scrollDelta+" imageTopDeltaCss="+anchorDelta+" cssWidth="+closed.getDouble("width")+" cssHeight="+closed.getDouble("height")+" naturalWidth="+closed.getInt("naturalWidth")+" naturalHeight="+closed.getInt("naturalHeight")+"; no observer scroll before this measurement");
            screenshot("source-"+sourceNumber+"-preview-closed-unmoved");
            check(readerHref.equals(closed.getString("href"))&&body.getString("sourceHref").equals(closed.getString("source"))&&image.getString("src").equals(closed.getString("src")),"Closing preview returns exact original image and source before observer movement");
            check(scrollDelta<50&&anchorDelta<50,"Closing preview preserves original position before any observer scroll");
        }
        org.json.JSONObject returned=null;
        for(int n=0;n<8;n++){returned=inspectFirstArticleImage(body,2000);if(!returned.has("retry"))break;Thread.sleep(250);}
        check(returned!=null&&readerHref.equals(returned.optString("href"))&&body.getString("sourceHref").equals(returned.optString("sourceHref"))&&image.getString("src").equals(returned.optString("src")),"Closing release preview must return to the same own document, article and first-image identity");
        sourceCheckpoint(report,"IMAGE_PREVIEW closed; original reader revision, article and first-image identity preserved");screenshot("source-"+sourceNumber+"-image-preview-returned");
    }
    private org.json.JSONObject privateSafeImage(org.json.JSONObject state)throws Exception{
        org.json.JSONObject safe=new org.json.JSONObject();for(String key:new String[]{"present","naturalWidth","naturalHeight","complete","height","top","bottom","decoded","inViewport","observationExpired","retry"})if(state.has(key))safe.put(key,state.get(key));
        if(state.has("src"))safe.put("imageLocation",safeSourceAddress(state.getString("src")));return safe;
    }
    private WebView findPreviewWeb(View view){
        if(view instanceof WebView){WebView w=(WebView)view;if("https://quiet-reader.invalid/image-preview".equals(w.getUrl())&&w.isShown())return w;}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){WebView found=findPreviewWeb(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;
    }
    private void verifyDecodedWeiboPreview(org.json.JSONObject inline,StringBuilder report)throws Exception{
        check(android.os.Build.VERSION.SDK_INT>=29,"Weibo preview public WindowInspector observation requires API29+");
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);WebView[] target={null};String[] raw={null};Throwable[] error={null};java.util.concurrent.atomic.AtomicBoolean active=new java.util.concurrent.atomic.AtomicBoolean(true),ownsJs=new java.util.concurrent.atomic.AtomicBoolean(false);
        runOnMainSync(()->{try{for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews()){WebView w=findPreviewWeb(root);if(w!=null){check(target[0]==null,"Only one visible exact own preview document");target[0]=w;}}check(target[0]!=null,"Actual preview window has exact own URL");WebView w=target[0];check(!w.getSettings().getJavaScriptEnabled(),"Ready preview has scripting disabled before independent observation");ownsJs.set(true);w.getSettings().setJavaScriptEnabled(true);
            w.evaluateJavascript("JSON.stringify((function(){var im=document.images[0],r=im?im.getBoundingClientRect():null;return {page:location.href,count:document.images.length,src:im?im.src:'',complete:im?im.complete:false,naturalWidth:im?im.naturalWidth:0,naturalHeight:im?im.naturalHeight:0,width:r?r.width:0,height:r?r.height:0};})())",value->{if(ownsJs.getAndSet(false))w.getSettings().setJavaScriptEnabled(false);if(active.get())raw[0]=value;done.countDown();});
        }catch(Throwable t){error[0]=t;done.countDown();}});
        try{check(done.await(5,java.util.concurrent.TimeUnit.SECONDS),"Own preview read-only observation returns within deadline");}finally{runOnMainSync(()->{active.set(false);if(ownsJs.getAndSet(false)&&target[0]!=null)target[0].getSettings().setJavaScriptEnabled(false);});}
        if(error[0]!=null)throw new Exception(error[0]);runOnMainSync(()->check(!target[0].getSettings().getJavaScriptEnabled(),"Preview scripting disabled after fixed inspection"));
        Object parsed=new org.json.JSONTokener(raw[0]==null?"null":raw[0]).nextValue();check(parsed instanceof String,"Own preview DOM result ready, not null");org.json.JSONObject p=new org.json.JSONObject((String)parsed);boolean same=imageBasename(inline.getString("src")).equals(imageBasename(p.optString("src")));
        sourceCheckpoint(report,"WEIBO_PREVIEW_DECODED image="+imagePathShape(p.optString("src"))+" sameBasename="+same+" complete="+p.optBoolean("complete")+" natural="+p.optInt("naturalWidth")+"x"+p.optInt("naturalHeight")+" css="+p.optDouble("width")+"x"+p.optDouble("height")+" inlineNatural="+inline.optInt("naturalWidth")+"x"+inline.optInt("naturalHeight")+" scriptingOff=true");
        check("https://quiet-reader.invalid/image-preview".equals(p.optString("page"))&&p.optInt("count")==1&&same&&p.optBoolean("complete")&&p.optInt("naturalHeight")>0,"Visible preview must actually decode the same original image");
        check(inline.optInt("naturalWidth")>0&&p.optInt("naturalWidth")>inline.optInt("naturalWidth")*1.5,"Weibo original-image preview must contain materially more pixels than decoded inline thumbnail");
    }
    private org.json.JSONObject privateSafeBody(org.json.JSONObject state)throws Exception{
        org.json.JSONObject safe=new org.json.JSONObject();for(String key:new String[]{"verifiedSection","substantiveParagraphs","paragraphs","contentImages","loadedContentImages","role"})if(state.has(key))safe.put(key,state.get(key));return safe;
    }
    private void weiboReaderJourney(org.json.JSONObject body,StringBuilder report)throws Exception{
        sourceCheckpoint(report,"WEIBO_JOURNEY expanded="+privateSafeBody(body)+"; first image actual decoding required, no full text/query emitted");
        StringBuilder failures=new StringBuilder();
        org.json.JSONObject image=observeFirstArticleImage(body,report);screenshot("source-0-first-image");
        if(!image.optBoolean("decoded")||!image.optBoolean("inViewport")){failures.append("inline-first-image-not-decoded; ");sourceCheckpoint(report,"WEIBO_JOURNEY INLINE_FAIL preserved; still attempt actual caption as independent recovery observation");}
        try{verifyReleaseImagePreview(body,image,report,0);sourceCheckpoint(report,"WEIBO_JOURNEY PREVIEW_SUCCESS does not erase inline failure");}
        catch(Throwable e){failures.append("image-preview-not-confirmed; ");sourceCheckpoint(report,"WEIBO_JOURNEY PREVIEW_FAIL type="+e.getClass().getSimpleName());screenshot("source-0-preview-failed");if(search(snapshot(),"关闭",true)!=null)tap("关闭");}
        try{verifyWeiboContinuation(body,report);}catch(Throwable e){failures.append("continuation-or-return-failed; ");sourceCheckpoint(report,"WEIBO_JOURNEY CONTINUATION_FAIL type="+e.getClass().getSimpleName()+" message="+e.getMessage());screenshot("source-0-continuation-failed");}
        observeWeiboSourceImage(image,report);
        check(failures.length()==0,"Weibo actual reading journey: "+failures);
    }
    private static String imageBasename(String url){try{String p=new java.net.URI(url).getPath();return p.substring(p.lastIndexOf('/')+1);}catch(Exception e){return "";}}
    private static String imagePathShape(String url){try{java.net.URI u=new java.net.URI(url);String p=u.getPath();return u.getScheme()+"://"+u.getHost()+p.substring(0,p.lastIndexOf('/')+1)+":image";}catch(Exception e){return "invalid";}}
    private void observeWeiboSourceImage(org.json.JSONObject inline,StringBuilder report)throws Exception{
        String basename=imageBasename(inline.optString("src"));check(!basename.isEmpty(),"Source image witness has exact basename");
        ActivityMonitor monitor=addMonitor("app.quietreader.LoginActivity",null,false);menu("来源 / 登录");Activity login=waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);check(login!=null,"Actual source menu opens production source page");
        WebView[] target={null};runOnMainSync(()->target[0]=findWeb(login.getWindow().getDecorView()));WebView web=target[0];check(web!=null,"Visible source WebView exists");
        String find="(function(){var a=document.querySelectorAll('.weibo-media-wraps img'),base="+org.json.JSONObject.quote(basename)+";for(var i=0;i<a.length;i++){var im=a[i];try{if(new URL(im.currentSrc||im.src).pathname.split('/').pop()!==base)continue;}catch(e){continue;}im.scrollIntoView({block:'center'});return {found:true,src:im.currentSrc||im.src,naturalWidth:im.naturalWidth,naturalHeight:im.naturalHeight};}return {found:false};})()";
        org.json.JSONObject found=new org.json.JSONObject();for(int n=0;n<15;n++){Thread.sleep(1500);found=sourceJson(web,find);if(found.optBoolean("found"))break;}
        if(!found.optBoolean("found")){sourceCheckpoint(report,"WEIBO_SOURCE_IMAGE UNCOVERED exact basename absent from current source; no replacement sample used");screenshot("source-0-original-image-unavailable");tap("返回");return;}
        sourceCheckpoint(report,"WEIBO_SOURCE_IMAGE sameBasename=true initial="+imagePathShape(found.getString("src"))+" naturalWidth="+found.optInt("naturalWidth")+" naturalHeight="+found.optInt("naturalHeight"));screenshot("source-0-original-image-before");
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);Throwable[] error={null};java.util.concurrent.atomic.AtomicBoolean active=new java.util.concurrent.atomic.AtomicBoolean(true);boolean[] sent={false};
        String locate="(function(){var a=document.querySelectorAll('.weibo-media-wraps img');for(var i=0;i<a.length;i++){var im=a[i];if(new URL(im.currentSrc||im.src).pathname.split('/').pop()!=="+org.json.JSONObject.quote(basename)+")continue;var r=im.getBoundingClientRect();return {page:location.href,x:r.left+r.width/2,y:r.top+r.height/2,width:r.width,height:r.height,innerWidth:innerWidth};}return {};})()";
        runOnMainSync(()->web.evaluateJavascript("JSON.stringify("+locate+")",raw->{if(!active.get())return;try{Object value=new org.json.JSONTokener(raw).nextValue();org.json.JSONObject p=new org.json.JSONObject((String)value);java.net.URI u=new java.net.URI(p.getString("page"));check("https".equals(u.getScheme())&&"m.weibo.cn".equals(u.getHost())&&p.getString("page").equals(web.getUrl()),"Real image pointer bound to current HTTPS mobile source");int[] at=new int[2];web.getLocationOnScreen(at);android.graphics.Rect bounds=new android.graphics.Rect();web.getGlobalVisibleRect(bounds);float scale=(float)(web.getWidth()/p.getDouble("innerWidth")),x=at[0]+(float)p.getDouble("x")*scale,y=at[1]+(float)p.getDouble("y")*scale;check(web.hasWindowFocus()&&p.getDouble("width")>0&&p.getDouble("height")>0&&bounds.contains((int)x,(int)y),"Actual source image pointer visible and focused");long now=android.os.SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,x,y,0),up=android.view.MotionEvent.obtain(now,now+60,1,x,y,0);try{sent[0]=getUiAutomation().injectInputEvent(down,false)&&getUiAutomation().injectInputEvent(up,false);}finally{down.recycle();up.recycle();}}catch(Throwable t){error[0]=t;}finally{done.countDown();}}));
        try{check(done.await(5,java.util.concurrent.TimeUnit.SECONDS),"Source image pointer timely");}finally{runOnMainSync(()->active.set(false));}if(error[0]!=null)throw new Exception(error[0]);check(sent[0],"Real source image tapped without script click");
        for(int n=0;n<8;n++){Thread.sleep(1500);org.json.JSONObject state=sourceJson(web,"(function(){var a=document.querySelectorAll('img'),out=[];for(var i=0;i<a.length;i++){var im=a[i],u;try{u=new URL(im.currentSrc||im.src);}catch(e){continue;}if(u.pathname.split('/').pop()!=="+org.json.JSONObject.quote(basename)+")continue;var r=im.getBoundingClientRect();out.push({src:im.currentSrc||im.src,naturalWidth:im.naturalWidth,naturalHeight:im.naturalHeight,width:r.width,height:r.height,visible:r.width>0&&r.height>0&&r.bottom>0&&r.top<innerHeight});}return {page:location.href,images:out};})()");org.json.JSONArray images=state.optJSONArray("images");StringBuilder details=new StringBuilder();if(images!=null)for(int i=0;i<images.length();i++){org.json.JSONObject im=images.getJSONObject(i);details.append(" {location=").append(imagePathShape(im.optString("src"))).append(",sameBasename=").append(basename.equals(imageBasename(im.optString("src")))).append(",natural=").append(im.optInt("naturalWidth")).append('x').append(im.optInt("naturalHeight")).append(",css=").append(im.optDouble("width")).append('x').append(im.optDouble("height")).append(",visible=").append(im.optBoolean("visible")).append('}');}sourceCheckpoint(report,"WEIBO_SOURCE_IMAGE_VIEWER elapsedMs="+((n+1)*1500)+" page="+safeSourceAddress(state.optString("page"))+details);}
        screenshot("source-0-original-image-viewer");tap("返回");
    }
    private org.json.JSONObject continuationState(String section)throws Exception{
        return ownReaderDom("(function(){var e=document.getElementById("+org.json.JSONObject.quote(section)+"),a=e?e.querySelector('a[href*=\"/post?index=\"]'):null,r=a?a.getBoundingClientRect():null;return {source:(document.querySelector('meta[name=quiet-reader-source]')||{}).content||'',href:location.href,present:!!a,action:a?a.href:'',left:r?r.left:0,right:r?r.right:0,top:r?r.top:0,bottom:r?r.bottom:0,scrollY:scrollY,paragraphs:e?e.querySelectorAll('p:not(.preview)').length:0};})()");
    }
    private void verifyWeiboContinuation(org.json.JSONObject body,StringBuilder report)throws Exception{
        String section=body.getString("verifiedSection");org.json.JSONObject state=continuationState(section);
        if(!state.optBoolean("present")){sourceCheckpoint(report,"WEIBO_JOURNEY CONTINUATION_UNCOVERED no actual original-post action in verified expanded card");return;}
        String topicSource=state.getString("source"),action=state.getString("action");
        for(int n=0;n<4;n++){final double y=state.getDouble("top")+state.getDouble("scrollY");runOnMainSync(()->{WebView w=findOwnReader(foreground.getWindow().getDecorView());if(w!=null)w.scrollTo(0,Math.max(0,(int)(y*w.getScale())-200));});Thread.sleep(300);state=continuationState(section);if(state.getDouble("top")>=0&&state.getDouble("bottom")<650)break;}
        screenshot("source-0-original-action");final org.json.JSONObject before=state;java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);Throwable[] error={null};boolean[] sent={false};
        // Fixed escaped-document read and pointer happen in one UI callback, no JS click.
        java.util.concurrent.atomic.AtomicBoolean pointerActive=new java.util.concurrent.atomic.AtomicBoolean(true),pointerOwnsJs=new java.util.concurrent.atomic.AtomicBoolean(false);WebView[] pointerWeb={null};
runOnMainSync(()->{if(!pointerActive.get()){done.countDown();return;}WebView w=findOwnReader(foreground.getWindow().getDecorView());if(w==null||w.getSettings().getJavaScriptEnabled()){error[0]=new AssertionError("Own reader busy/missing");done.countDown();return;}pointerWeb[0]=w;pointerOwnsJs.set(true);w.getSettings().setJavaScriptEnabled(true);w.evaluateJavascript("JSON.stringify((function(){var a=document.querySelector('a[href="+org.json.JSONObject.quote(action)+"]'),r=a?a.getBoundingClientRect():null;return {href:location.href,present:!!a,left:r?r.left:0,right:r?r.right:0,top:r?r.top:0,bottom:r?r.bottom:0,scrollY:scrollY};})())",raw->{if(pointerOwnsJs.getAndSet(false))w.getSettings().setJavaScriptEnabled(false);if(!pointerActive.get()){done.countDown();return;}try{Object decoded=new org.json.JSONTokener(raw).nextValue();org.json.JSONObject p=new org.json.JSONObject((String)decoded);check(p.optBoolean("present")&&p.getString("href").equals(w.getUrl())&&p.getString("href").equals(before.getString("href")),"Continuation remains bound to current own reader revision");int[] at=new int[2];w.getLocationOnScreen(at);android.graphics.Rect rect=new android.graphics.Rect();w.getGlobalVisibleRect(rect);float scale=w.getScale(),x=at[0]+(float)((p.getDouble("left")+p.getDouble("right"))/2)*scale,y=at[1]+(float)((p.getDouble("top")+p.getDouble("bottom"))/2)*scale;check(w.hasWindowFocus()&&rect.contains((int)x,(int)y)&&Math.abs(w.getScrollY()-p.getDouble("scrollY")*scale)<7,"Continuation pointer viewport and native scroll agree");long now=android.os.SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,x,y,0),up=android.view.MotionEvent.obtain(now,now+60,1,x,y,0);try{sent[0]=getUiAutomation().injectInputEvent(down,false)&&getUiAutomation().injectInputEvent(up,false);}finally{down.recycle();up.recycle();}}catch(Throwable t){error[0]=t;}finally{done.countDown();}});});
        try{check(done.await(5,java.util.concurrent.TimeUnit.SECONDS),"Continuation pointer callback timely");}finally{runOnMainSync(()->{pointerActive.set(false);if(pointerOwnsJs.getAndSet(false)&&pointerWeb[0]!=null)pointerWeb[0].getSettings().setJavaScriptEnabled(false);});}if(error[0]!=null)throw new Exception(error[0]);check(sent[0],"Continuation actual pointer sent");
        org.json.JSONObject detail=null;for(int n=0;n<60;n++){Thread.sleep(500);boolean[] own={false};runOnMainSync(()->own[0]=findOwnReader(foreground.getWindow().getDecorView())!=null);if(!own[0])continue;org.json.JSONObject candidate=ownReaderDom("({source:(document.querySelector('meta[name=quiet-reader-source]')||{}).content||''})");if(!candidate.optString("source").equals(topicSource)){detail=revealReadableContent("WEIBO");break;}}
        check(detail!=null&&!postId(detail.optString("sourceHref")).isEmpty(),"Original post must present actual status/detail content, not same topic shell");screenshot("source-0-original-reader");sourceCheckpoint(report,"WEIBO_JOURNEY ORIGINAL_READER source="+safeSourceAddress(detail.optString("sourceHref"))+" body="+privateSafeBody(detail));
        tap("返回");Thread.sleep(900);org.json.JSONObject restored=continuationState(section);check(topicSource.equals(restored.optString("source"))&&restored.optInt("paragraphs")>0,"Return preserves original topic identity and expanded paragraph");double delta=Math.abs(restored.optDouble("scrollY")-before.getDouble("scrollY"));sourceCheckpoint(report,"WEIBO_JOURNEY RETURN sourceSame=true expanded=true cssScrollDelta="+delta);screenshot("source-0-original-returned");check(delta<50,"Returning from original post must retain reading position within 50 CSS px");
    }
    private File saveSourceReport(StringBuilder report)throws Exception{
        File dir=new File(getTargetContext().getExternalFilesDir(null),outputDirectory);dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,"report.txt"))){out.write(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}
        return dir;
    }
    private void sourceCheckpoint(StringBuilder report,String line)throws Exception{
        report.append(line).append('\n');File dir=saveSourceReport(report);
        Bundle progress=new Bundle();progress.putString("stream",line+"\nOutput: "+dir+"\n");sendStatus(0,progress);
    }
    private void verifySmzdmBoard(String selectedCard,StringBuilder report)throws Exception{
        final String endpoint="https://faxian.smzdm.com/h2s0t0f0c0p1/";
        await("3小时最热·公开榜",false,5);
        android.content.SharedPreferences boards=getTargetContext().getSharedPreferences("boards",0);
        check(endpoint.equals(boards.getString("SMZDM_endpoint","")),"SMZDM live board must carry the exact official three-hour endpoint, not the old tag collection");
        org.json.JSONArray items=new org.json.JSONArray(boards.getString("SMZDM","[]"));
        check(items.length()>0,"Three-hour board must contain actual entries");
        java.util.Set<String> urls=new java.util.HashSet<>();
        for(int i=0;i<items.length();i++){
            org.json.JSONObject item=items.getJSONObject(i);String url=item.optString("url"),detail=item.optString("detail");
            check("SMZDM".equals(item.optString("source"))&&url.matches("https://(?:www\\.)?smzdm\\.com/p/\\d+/"),"Three-hour board entry "+i+" must be a SMZDM deal, not a tag/category/outbound shopping link");
            check(urls.add(url)&&!detail.contains("公开集合")&&!detail.contains("不等同 App 实时排序")&&!item.optString("title").contains("合成测试"),"Three-hour board entry "+i+" must not duplicate another deal or reuse the old collection fixture");
        }
        org.json.JSONObject first=items.getJSONObject(0);String detail=first.optString("detail");
        check(selectedCard.equals(first.getString("title")+"，"+first.optString("detail")+"，进入阅读"),"Selected real first card must be bound to the freshly stored three-hour entry including original metadata");
        check(!detail.trim().isEmpty(),"Real first three-hour card should retain available public offer metadata");
        boolean[] visible={false};runOnMainSync(()->visible[0]=nativeView(foreground.getWindow().getDecorView(),detail,false)!=null);
        check(visible[0],"First deal's stored price/shop/time/comment metadata must also be displayed on its native card");
        sourceCheckpoint(report,"什么值得买: VERIFIED_PUBLIC_3H_BOARD endpoint="+endpoint+"; entries="+items.length()+"; firstUrl="+first.getString("url")+"; firstDetail="+detail+". UI/cache binding only; field extraction has separate parser tests; App ordering not certified.");
    }
    private static String safeSourceAddress(String raw){
        if(raw==null)return "<null>";try{android.net.Uri u=android.net.Uri.parse(raw);String host=u.getHost();return host==null?"<non-http-page>":host+(u.getPath()==null?"/":u.getPath().replaceAll("/(status|detail)/[^/]+","/$1/:id"));}catch(Exception e){return "<invalid>";}
    }
    private static String safeNavigationFacts(String raw){
        try{android.net.Uri u=android.net.Uri.parse(raw);boolean valid;try{new java.net.URI(raw);valid=true;}catch(Exception e){valid=false;}
            return "scheme="+u.getScheme()+" address="+safeSourceAddress(raw)+" port="+u.getPort()+" userInfoPresent="+(u.getUserInfo()!=null)+" javaUriValid="+valid;
        }catch(Exception e){return "invalid-address";}
    }
    private org.json.JSONObject sourceShape(WebView web)throws Exception{
        java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);String[] raw={null};
        // Counts/booleans only. Never export source text, forms, values, HTML, query, storage or cookies.
        String js="JSON.stringify((function(){var b=document.body,t=b?b.innerText:'',s='.card-wrap .txt,.weibo-text,[class*=detail_wbtext]';return {ready:document.readyState,bodyChars:t.length,selectorCount:document.querySelectorAll(s).length,passwordFields:document.querySelectorAll('input[type=password]').length,loginWords:/扫码登录|短信登录|密码登录|登录微博/.test(t),captchaWords:/验证码|安全验证|访问频次|访问异常/.test(t),failureWords:/访问出错|无法访问|加载失败|出错了|403 Forbidden|Access Denied/.test(t)};})())";
        runOnMainSync(()->web.evaluateJavascript(js,v->{raw[0]=v;latch.countDown();}));
        if(!latch.await(4,java.util.concurrent.TimeUnit.SECONDS))return new org.json.JSONObject().put("observerTimeout",true);
        Object decoded=new org.json.JSONTokener(raw[0]==null?"null":raw[0]).nextValue();return decoded instanceof String?new org.json.JSONObject((String)decoded):new org.json.JSONObject().put("noDocument",true);
    }
    private org.json.JSONObject publicWeiboCardStructure(WebView web)throws Exception{
        java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);String[] raw={null};
        // Structure only: never serialize HTML, text, IDs, data payloads, URL queries or account fields.
        String js="JSON.stringify((function(){"
            +"if(location.protocol!=='https:'||location.hostname!=='m.weibo.cn'||location.pathname!=='/search')return {skipped:'not-mobile-public-search'};"
            +"function node(e){return {tag:e.tagName.toLowerCase(),cls:typeof e.className==='string'?e.className.replace(/[^A-Za-z0-9_ -]/g,'').slice(0,180):''};}"
            +"function chain(e,stop){var a=[];while(e&&a.length<9){a.push(node(e));if(e===stop)break;e=e.parentElement;}return a;}"
            +"function tree(root){var out=[];function walk(e,depth){if(out.length>=150||depth>9||/^(SCRIPT|STYLE|FORM|INPUT|TEXTAREA|SELECT|OPTION)$/.test(e.tagName))return;var n=node(e);n.depth=depth;out.push(n);for(var i=0;i<e.children.length;i++)walk(e.children[i],depth+1);}walk(root,0);return out;}"
            +"var texts=document.querySelectorAll('.weibo-text'),owners=[],cards=[];for(var i=0;i<texts.length&&cards.length<6;i++){var t=texts[i],o=t.closest('.card-wrap')||t.closest('.card')||t.parentElement;if(owners.indexOf(o)>=0)continue;owners.push(o);"
            +"var media=o.querySelectorAll('video,source,[class*=video],[class*=Video],[class*=play],[class*=Play]'),m=[];for(var j=0;j<media.length&&j<14;j++)m.push(chain(media[j],o));"
            +"var full=[],links=o.querySelectorAll('a');for(var k=0;k<links.length;k++)if(/全文|展开/.test(links[k].textContent)){var a=links[k],u;try{u=new URL(a.href,location.href);}catch(err){continue;}full.push({tagClass:node(a),scheme:u.protocol,host:u.hostname,path:u.pathname.replace(/[0-9]{4,}/g,':id'),hasQuery:!!u.search});}"
            +"cards.push({index:cards.length,owner:node(o),textAncestor:chain(t,o),textNodes:o.querySelectorAll('.weibo-text').length,videoTags:o.querySelectorAll('video').length,imageCount:o.querySelectorAll('img').length,mediaChains:m,quoteCount:o.querySelectorAll('.weibo-rp,[class*=repost],[class*=retweet],[class*=forward],[class*=quote]').length,fulltextLinks:full.slice(0,6),tree:tree(o)});}"
            +"return {source:'https://m.weibo.cn/search',totalTextNodes:texts.length,cards:cards};})())";
        runOnMainSync(()->web.evaluateJavascript(js,v->{raw[0]=v;latch.countDown();}));
        if(!latch.await(4,java.util.concurrent.TimeUnit.SECONDS))return new org.json.JSONObject().put("observerTimeout",true);
        Object decoded=new org.json.JSONTokener(raw[0]==null?"null":raw[0]).nextValue();return decoded instanceof String?new org.json.JSONObject((String)decoded):new org.json.JSONObject().put("noDocument",true);
    }
    private org.json.JSONObject weiboTextWitness(WebView web)throws Exception{
        java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);String[] raw={null};
        // Public post prefixes live only in instrumentation memory; never append this object to reports.
        String js="JSON.stringify((function(){var a=[],seen=[];if(location.protocol!=='https:'||location.hostname!=='m.weibo.cn'||location.pathname!=='/search')return {cards:a};var ts=document.querySelectorAll('.weibo-text');for(var i=0;i<ts.length&&a.length<24;i++){var o=ts[i].closest('.card-wrap');if(!o||seen.indexOf(o)>=0)continue;seen.push(o);var text=ts[i].textContent.replace(/\\s+/g,'').replace(/…?全文$/,'');if(text.length<40)continue;var quote=ts[i].closest('.weibo-rp,.retweeted_status,.repost');if(quote)continue;var media=o.querySelectorAll('.card-video.type-video,.mwb-video,video'),direct=false;for(var j=0;j<media.length;j++)if(!media[j].closest('.weibo-rp,.retweeted_status,.repost'))direct=true;a.push({prefix:text.slice(0,60),video:direct});}return {cards:a};})())";
        runOnMainSync(()->web.evaluateJavascript(js,v->{raw[0]=v;latch.countDown();}));
        if(!latch.await(4,java.util.concurrent.TimeUnit.SECONDS))return new org.json.JSONObject();
        Object decoded=new org.json.JSONTokener(raw[0]==null?"null":raw[0]).nextValue();return decoded instanceof String?new org.json.JSONObject((String)decoded):new org.json.JSONObject();
    }
    private void verifyWeiboVideoWitness(org.json.JSONObject witness,StringBuilder report)throws Exception{
        org.json.JSONArray cards=witness.optJSONArray("cards");
        if(cards==null){sourceCheckpoint(report,"WEIBO_VIDEO_COMPARISON UNVERIFIED: no public source witnesses");return;}
        org.json.JSONObject own=ownReaderDom("({source:(document.querySelector('meta[name=quiet-reader-source]')||{}).content||'',text:Array.prototype.map.call(document.querySelectorAll('section p'),function(e){return e.textContent;}).join('').replace(/\\s+/g,'')})");
        java.net.URI identity=new java.net.URI(own.optString("source"));check("https".equals(identity.getScheme())&&"m.weibo.cn".equals(identity.getHost())&&"/search".equals(identity.getPath()),"Video comparison belongs to same mobile topic source");
        String rendered=own.optString("text");int videos=0,videoPresent=0,texts=0,textRetained=0,ambiguous=0;
        for(int i=0;i<cards.length();i++){org.json.JSONObject c=cards.getJSONObject(i);String prefix=c.getString("prefix");boolean conflict=false;for(int j=0;j<cards.length();j++){org.json.JSONObject other=cards.getJSONObject(j);if(other.getBoolean("video")!=c.getBoolean("video")&&other.getString("prefix").equals(prefix)){conflict=true;break;}}if(conflict){ambiguous++;continue;}if(c.getBoolean("video")){videos++;if(rendered.contains(prefix))videoPresent++;}else{texts++;if(rendered.contains(prefix))textRetained++;}}
        sourceCheckpoint(report,"WEIBO_VIDEO_COMPARISON publicSourceVideos="+videos+" videoPrefixesPresent="+videoPresent+" publicSourceTextCards="+texts+" textPrefixesRetained="+textRetained+" ambiguousSkipped="+ambiguous+"; prefix witnesses stayed only in memory, not fulltext certification.");
        if(videos==0||texts==0){sourceCheckpoint(report,"WEIBO_VIDEO_COMPARISON UNVERIFIED: live mixed video/text positive control unavailable");return;}
        check(videoPresent==0,"Observed original video-card copy is absent from own rendered content and previews");check(textRetained>0,"At least one independently observed non-video source text remains in own reader");
    }
    private String sourceNativeStatus(View v){
        if(v instanceof WebView)return "";
        if(v instanceof android.widget.TextView){String t=((android.widget.TextView)v).getText().toString();if(t.startsWith("来源页")||t.contains("连接失败")||t.contains("证书异常")||t.startsWith("已阻止")||t.startsWith("页面")||t.startsWith("未找到")||t.startsWith("读取失败")||t.startsWith("仅允许"))return t;}
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){String s=sourceNativeStatus(((ViewGroup)v).getChildAt(i));if(!s.isEmpty())return s;}return "";
    }
    private String webInventory(View v){
        if(v instanceof WebView){WebView w=(WebView)v;return "web{address="+safeSourceAddress(w.getUrl())+",original="+safeSourceAddress(w.getOriginalUrl())+",attached="+w.isAttachedToWindow()+",shown="+w.isShown()+",visibility="+w.getVisibility()+",parent="+(w.getParent()!=null)+"};";}
        StringBuilder b=new StringBuilder();if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)b.append(webInventory(((ViewGroup)v).getChildAt(i)));return b.toString();
    }
    private void attachWeiboObserver(Activity login){
        WebView w=findWeb(login.getWindow().getDecorView());weiboSourceWeb=w;weiboSourceBegan=android.os.SystemClock.elapsedRealtime();weiboSourceEvents.setLength(0);
        final StringBuffer events=weiboSourceEvents;final long began=weiboSourceBegan;
        check(w!=null,"Production source WebView exists after onCreate");android.webkit.WebViewClient original=w.getWebViewClient();
            w.setWebViewClient(new android.webkit.WebViewClient(){
                private void event(String kind,String url){events.append(android.os.SystemClock.elapsedRealtime()-began).append("ms ").append(kind).append(' ').append(safeSourceAddress(url)).append('\n');}
                @Override public boolean shouldOverrideUrlLoading(WebView v,android.webkit.WebResourceRequest r){boolean handled=original.shouldOverrideUrlLoading(v,r);events.append(android.os.SystemClock.elapsedRealtime()-began).append("ms navigation ").append(safeNavigationFacts(r.getUrl().toString())).append(" mainFrame=").append(r.isForMainFrame()).append(" handled=").append(handled).append('\n');return handled;}
                @Override public boolean shouldOverrideUrlLoading(WebView v,String u){boolean handled=original.shouldOverrideUrlLoading(v,u);events.append(android.os.SystemClock.elapsedRealtime()-began).append("ms legacy-navigation ").append(safeNavigationFacts(u)).append(" handled=").append(handled).append('\n');return handled;}
                @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v,android.webkit.WebResourceRequest r){return original.shouldInterceptRequest(v,r);}
                @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v,String u){return original.shouldInterceptRequest(v,u);}
                @Override public void onPageStarted(WebView v,String u,Bitmap b){event("start",u);original.onPageStarted(v,u,b);}
                @Override public void onPageCommitVisible(WebView v,String u){event("commit",u);original.onPageCommitVisible(v,u);}
                @Override public void onPageFinished(WebView v,String u){event("finish",u);events.append("nativeBeforeFinish=").append(sourceNativeStatus(login.getWindow().getDecorView())).append('\n');original.onPageFinished(v,u);events.append("nativeAfterFinish=").append(sourceNativeStatus(login.getWindow().getDecorView())).append('\n');}
                @Override public void doUpdateVisitedHistory(WebView v,String u,boolean reload){event("history",u);original.doUpdateVisitedHistory(v,u,reload);}
                @Override public void onReceivedError(WebView v,android.webkit.WebResourceRequest r,android.webkit.WebResourceError e){if(r.isForMainFrame())event("main-error="+e.getErrorCode(),r.getUrl().toString());original.onReceivedError(v,r,e);}
                @Override public void onReceivedError(WebView v,int c,String d,String u){event("legacy-error="+c,u);original.onReceivedError(v,c,d,u);}
                @Override public void onReceivedHttpError(WebView v,android.webkit.WebResourceRequest r,android.webkit.WebResourceResponse p){if(r.isForMainFrame())event("main-http="+p.getStatusCode(),r.getUrl().toString());original.onReceivedHttpError(v,r,p);}
                @Override public void onReceivedSslError(WebView v,android.webkit.SslErrorHandler h,android.net.http.SslError e){event("ssl="+e.getPrimaryError(),e.getUrl());original.onReceivedSslError(v,h,e);}
                @Override public void onReceivedHttpAuthRequest(WebView v,android.webkit.HttpAuthHandler h,String host,String realm){original.onReceivedHttpAuthRequest(v,h,host,realm);}
                @Override public void onReceivedClientCertRequest(WebView v,android.webkit.ClientCertRequest r){original.onReceivedClientCertRequest(v,r);}
                @Override public void onLoadResource(WebView v,String u){original.onLoadResource(v,u);}
                @Override public void onScaleChanged(WebView v,float old,float now){original.onScaleChanged(v,old,now);}
                @Override public boolean onRenderProcessGone(WebView v,android.webkit.RenderProcessGoneDetail d){return original.onRenderProcessGone(v,d);}
                @Override public void onSafeBrowsingHit(WebView v,android.webkit.WebResourceRequest r,int threat,android.webkit.SafeBrowsingResponse response){original.onSafeBrowsingHit(v,r,threat,response);}
            });
    }
    private org.json.JSONObject sourceJson(WebView web,String expression)throws Exception{
        java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);String[] raw={null};
        runOnMainSync(()->web.evaluateJavascript("JSON.stringify("+expression+")",v->{raw[0]=v;latch.countDown();}));
        check(latch.await(4,java.util.concurrent.TimeUnit.SECONDS),"Source observation returns promptly");
        Object value=new org.json.JSONTokener(raw[0]==null?"null":raw[0]).nextValue();return value instanceof String?new org.json.JSONObject((String)value):new org.json.JSONObject();
    }
    private static String postId(String url){try{String path=new java.net.URI(url).getPath();return path!=null&&path.matches("/(status|detail)/[A-Za-z0-9]+/?")?path.replaceFirst("^/(status|detail)/","").replace("/",""):"";}catch(Exception e){return "";}}
    private void observeWeiboFulltext(WebView web,StringBuilder report)throws Exception{
        // Full public text and href kept only in memory for same-post/length comparisons.
        String select="(function(){if(location.protocol!=='https:'||location.hostname!=='m.weibo.cn'||location.pathname!=='/search')return {};var ts=document.querySelectorAll('.weibo-text');for(var i=0;i<ts.length;i++){var o=ts[i].closest('.card-wrap');if(!o||o.querySelector('.card-video.type-video,.mwb-video,video')||ts[i].closest('.weibo-rp,.retweeted_status,.repost'))continue;var links=ts[i].querySelectorAll('a');for(var j=0;j<links.length;j++){var a=links[j],u;try{u=new URL(a.href);}catch(e){continue;}if(/全文/.test(a.textContent)&&u.protocol==='https:'&&u.hostname==='m.weibo.cn'&&/^\\/(status|detail)\\/[A-Za-z0-9]+\\/?$/.test(u.pathname)){a.scrollIntoView({block:'center'});return {href:a.href,relative:!/^https?:/i.test(a.getAttribute('href')||''),text:ts[i].textContent.replace(/\\s+/g,''),index:i};}}}return {};})()";
        org.json.JSONObject initial=sourceJson(web,select);if(!initial.has("href")){sourceCheckpoint(report,"WEIBO_FULLTEXT UNVERIFIED no actual non-video fulltext link in loaded mobile source");return;}
        String href=initial.getString("href"),id=postId(href),summary=initial.getString("text");
        sourceCheckpoint(report,"WEIBO_FULLTEXT_CANDIDATE source=m.weibo.cn/search target="+safeSourceAddress(href)+" idType="+(id.matches("[0-9]+")?"numeric":"alphanumeric")+" idLength="+id.length()+" relativeAttribute="+initial.getBoolean("relative")+" summaryChars="+summary.length()+" nonVideoOwner=true");
        Thread.sleep(300);screenshot("source-0-fulltext-before");
        java.util.concurrent.CountDownLatch clicked=new java.util.concurrent.CountDownLatch(1);Throwable[] error={null};boolean[] injected={false};
        runOnMainSync(()->web.evaluateJavascript("JSON.stringify((function(){var links=document.querySelectorAll('.weibo-text a');for(var i=0;i<links.length;i++)if(links[i].href==="+org.json.JSONObject.quote(href)+"){var r=links[i].getBoundingClientRect();return {href:links[i].href,page:location.href,x:r.left+r.width/2,y:r.top+r.height/2,width:r.width,height:r.height,innerWidth:innerWidth,innerHeight:innerHeight};}return {};})())",raw->{try{
            Object v=new org.json.JSONTokener(raw).nextValue();org.json.JSONObject p=v instanceof String?new org.json.JSONObject((String)v):new org.json.JSONObject();
            check(href.equals(p.optString("href"))&&p.optString("page").equals(web.getUrl()),"Exact observed fulltext target and current page still match");
            java.net.URI current=new java.net.URI(p.getString("page"));check("https".equals(current.getScheme())&&"m.weibo.cn".equals(current.getHost())&&"/search".equals(current.getPath()),"Fulltext pointer stays on actual mobile source");
            int[] at=new int[2];web.getLocationOnScreen(at);android.graphics.Rect bounds=new android.graphics.Rect();web.getGlobalVisibleRect(bounds);float scale=(float)(web.getWidth()/p.getDouble("innerWidth"));float x=at[0]+(float)p.getDouble("x")*scale,y=at[1]+(float)p.getDouble("y")*scale;
            check(p.optDouble("width")>0&&p.optDouble("height")>0&&web.hasWindowFocus()&&bounds.contains((int)x,(int)y),"Fulltext link has visible focused pointer bounds");
            long now=android.os.SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,x,y,0),up=android.view.MotionEvent.obtain(now,now+60,1,x,y,0);try{injected[0]=getUiAutomation().injectInputEvent(down,false)&&getUiAutomation().injectInputEvent(up,false);}finally{down.recycle();up.recycle();}
        }catch(Throwable t){error[0]=t;}finally{clicked.countDown();}}));
        check(clicked.await(4,java.util.concurrent.TimeUnit.SECONDS),"Actual fulltext pointer attempt returns");if(error[0]!=null)throw new AssertionError("Fulltext pointer safety check failed",error[0]);check(injected[0],"Actual fulltext pointer injected, no Javascript click");
        org.json.JSONObject last=new org.json.JSONObject();String finalUrl="";
        for(int attempt=0;attempt<8;attempt++){Thread.sleep(1500);last=sourceJson(web,"(function(){var ss=['.weibo-text','[class*=detail_wbtext]'],out=[];for(var i=0;i<ss.length;i++){var es=document.querySelectorAll(ss[i]);for(var j=0;j<es.length&&j<8;j++)out.push({selector:ss[i],text:es[j].textContent.replace(/\\s+/g,'')});}var t=document.body?document.body.innerText:'';return {href:location.href,texts:out,bodyChars:t.length,loginWords:/扫码登录|短信登录|密码登录|登录微博/.test(t),captchaWords:/验证码|安全验证|访问频次/.test(t)};})()");finalUrl=last.optString("href");sourceCheckpoint(report,"WEIBO_FULLTEXT_OBSERVE elapsedMs="+((attempt+1)*1500)+" page="+safeSourceAddress(finalUrl)+" bodyChars="+last.optInt("bodyChars")+" selectors="+(last.optJSONArray("texts")==null?0:last.getJSONArray("texts").length())+" loginWords="+last.optBoolean("loginWords")+" captchaWords="+last.optBoolean("captchaWords"));}
        screenshot("source-0-fulltext-detail");org.json.JSONArray texts=last.optJSONArray("texts");String best="",selector="";if(texts!=null)for(int n=0;n<texts.length();n++){org.json.JSONObject t=texts.getJSONObject(n);if(t.optString("text").length()>best.length()){best=t.optString("text");selector=t.optString("selector");}}
        String prefix=summary.replaceAll("[.。…]*全文$","");String first=prefix.substring(0,Math.min(50,prefix.length()));String finalId=postId(finalUrl);
        sourceCheckpoint(report,"WEIBO_FULLTEXT_RESULT target="+safeSourceAddress(href)+" final="+safeSourceAddress(finalUrl)+" finalIdType="+(finalId.matches("[0-9]+")?"numeric":finalId.isEmpty()?"none":"alphanumeric")+" finalIdLength="+finalId.length()+" sameLiteralId="+id.equals(finalId)+" selector="+selector+" summaryChars="+summary.length()+" detailChars="+best.length()+" grew="+(best.length()>summary.length())+" originalPrefixRetained="+(!first.isEmpty()&&best.contains(first))+" endChanged="+(!best.endsWith(summary.substring(Math.max(0,summary.length()-30))))+" stillFulltextMarker="+best.contains("全文")+"; no text witnesses or IDs persisted, numeric/alphanumeric conversion not inferred.");
        sourceCheckpoint(report,"WEIBO_FULLTEXT_NAVIGATION_EVENTS\n"+weiboSourceEvents);weiboSourceEvents.setLength(0);
    }
    private void observeWeiboRecovery(StringBuilder report)throws Exception{
        sourceCheckpoint(report,"WEIBO_RECOVERY_BEGIN: actual source-assistance pointer; observer attaches same UI turn after production onCreate before queued page callbacks. Initial IO request may precede wrapper. No credentials/query/HTML/cookies collected.");
        ActivityMonitor monitor=addMonitor("app.quietreader.LoginActivity",null,false);
        tap("登录 / 加载后读取");Activity login=waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);check(login!=null,"Actual assistance button opens production source Activity");
        WebView[] target={weiboSourceWeb};StringBuffer events=weiboSourceEvents;long began=weiboSourceBegan;
        check(target[0]!=null,"onCreate attached source observer");
        sourceCheckpoint(report,"WEIBO_ACTIVITY class="+login.getClass().getName());
        for(int phase=0;phase<2;phase++){
            if(phase==1){if(foreground!=login)break;tap("桌面版");}
            for(int i=0;i<10;i++){
                Thread.sleep(2000);if(foreground!=login)break;org.json.JSONObject shape=sourceShape(target[0]);String[] address={""},status={""};int[] progress={0};
                runOnMainSync(()->{address[0]=webInventory(login.getWindow().getDecorView());progress[0]=target[0].getProgress();status[0]=sourceNativeStatus(login.getWindow().getDecorView());});
                sourceCheckpoint(report,"WEIBO_SOURCE phase="+(phase==0?"mobile":"desktop")+" ms="+(android.os.SystemClock.elapsedRealtime()-began)+" address="+address[0]+" progress="+progress[0]+" nativeStatus="+status[0]+" shape="+shape);
            }
            sourceCheckpoint(report,"WEIBO_SOURCE_EVENTS phase="+phase+"\n"+events);events.setLength(0);
            screenshot("source-0-recovery-"+(phase==0?"mobile":"desktop"));
            if(foreground!=login)break;
            if(weiboFulltextProbe){observeWeiboFulltext(target[0],report);break;}
            sourceCheckpoint(report,"WEIBO_PUBLIC_CARD_STRUCTURE phase="+phase+" "+publicWeiboCardStructure(target[0]));
            org.json.JSONObject mediaWitness=weiboTextWitness(target[0]);
            tap("读取到 News");Thread.sleep(1200);
            String[] status={""};runOnMainSync(()->status[0]=foreground==login?sourceNativeStatus(login.getWindow().getDecorView()):"Returned to app; body not certified by this diagnostic");
            sourceCheckpoint(report,"WEIBO_READ_ACTION phase="+phase+" remainsSource="+(foreground==login)+" nativeStatus="+status[0]);
            screenshot("source-0-after-read-"+(phase==0?"mobile":"desktop"));
            if(foreground!=login){
                verifyWeiboVideoWitness(mediaWitness,report);
                org.json.JSONObject recovered=revealReadableContent("WEIBO");
                java.net.URI recoveredUri=new java.net.URI(recovered.optString("sourceHref"));
                boolean allowed="https".equalsIgnoreCase(recoveredUri.getScheme())&&recoveredUri.getUserInfo()==null&&(recoveredUri.getPort()==-1||recoveredUri.getPort()==443)&&("m.weibo.cn".equalsIgnoreCase(recoveredUri.getHost())||"s.weibo.com".equalsIgnoreCase(recoveredUri.getHost()));
                check(allowed,"Recovered real Weibo reader retains allowed source identity");
                sourceCheckpoint(report,"WEIBO_RECOVERED_BODY: actual pointer-expanded selected section="+recovered.optString("verifiedSection")+" substantiveParagraphs="+recovered.optInt("substantiveParagraphs")+" contentImages="+recovered.optInt("contentImages")+" decodedImages="+recovered.optInt("loadedContentImages")+" allowedSource="+allowed+" source="+safeSourceAddress(recovered.optString("sourceHref")));
                screenshot("source-0-recovered-expanded");
                break;
            }
        }
        sourceCheckpoint(report,"WEIBO_RECOVERY_END: observations do not change original article failure or certify login.");
    }
    private void sourceChecks()throws Exception {
        if(smzdmImageProbe){smzdmImageProbe();return;}
        StringBuilder report=new StringBuilder();
        int unavailable=0;
        sourceCheckpoint(report,"RUNNING anonymous source observations; filter="+sourceFilter+"; wallstreetOriginal="+wallstreetOriginal+"; fixedTieba="+fixedTieba+"; incomplete until final PASS. No account authentication claim.");
        sourceContentPolicyChecks();
        sourceCheckpoint(report,"POLICY_SELF_CHECKS_PASS: pure synthetic strings only; not a live-source result.");
        String[] names={"知乎","爱范儿","财联社","什么值得买","虎扑","微博"};
        String[] sourceKeys={"ZHIHU","IFANR","CLS","SMZDM","HUPU","WEIBO"};
        for(int n=0;n<names.length;n++) {
            if(!sourceFilter.equals("all")&&!sourceFilter.equals(sourceKeys[n]))continue;
            readerTapDiagnostics.setLength(0);
            long boardStart=0,articleStart=0,boardMs=-1,articleMs=-1;String stage="launch",title="";
            try {
            sourceCheckpoint(report,names[n]+": START stage="+stage);
            startActivitySync(new Intent().setClassName("app.quietreader","app.quietreader.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await("News",true,15);selectSource(names[n]);stage="fresh-board";boardStart=android.os.SystemClock.elapsedRealtime();menu("刷新榜单");await("更新于",false,45);boardMs=android.os.SystemClock.elapsedRealtime()-boardStart;
            sourceCheckpoint(report,names[n]+": PROGRESS boardMs="+boardMs+"; board ready, before screenshot");
            screenshot("source-"+n+"-board");
            String cardNeedle=fixedTieba&&sourceFilter.equals("TIEBA")?"假期打分大会,奥特曼客串锐评":wallstreetOriginal&&sourceFilter.equals("WALLSTREET")?"通胀数据难挡美债压力，标普、道指三连跌":"，进入阅读";
            if(smzdmImageJourney){
                org.json.JSONArray board=new org.json.JSONArray(getTargetContext().getSharedPreferences("boards",0).getString("SMZDM","[]"));String selected="";for(int i=0;i<board.length();i++){org.json.JSONObject item=board.getJSONObject(i);if("https://www.smzdm.com/p/183314225/".equals(item.optString("url"))){selected=item.getString("title");cardNeedle=selected+"，"+item.optString("detail")+"，进入阅读";break;}}
                check(!selected.isEmpty(),"Fixed original SMZDM product no longer in real board: UNCOVERED, no substituted product");setBoardQuery(selected);sourceCheckpoint(report,"FIXED_SMZDM exactProduct183314225=true foundInActualRefreshedBoard=true; no fabricated article/card");screenshot("source-6-fixed-product-card");
            }
            AccessibilityNodeInfo card=await(cardNeedle,false,5);title=card.getContentDescription().toString();check(!title.contains("合成测试"),"Real source observation must not use synthetic cached board");
            if(sourceKeys[n].equals("SMZDM")){stage="three-hour-board";verifySmzdmBoard(title,report);}
            if(wallstreetOriginal&&sourceFilter.equals("WALLSTREET")||fixedTieba&&sourceFilter.equals("TIEBA")){
                final String selectedTitle=title;runOnMainSync(()->{View view=nativeView(foreground.getWindow().getDecorView(),selectedTitle,true);if(view!=null)view.requestRectangleOnScreen(new android.graphics.Rect(0,0,view.getWidth(),view.getHeight()),true);});
                Thread.sleep(350);sourceCheckpoint(report,"Fixed original sample selected from live board: "+title);screenshot("source-"+n+"-original-card");
            }
            stage="article";articleStart=android.os.SystemClock.elapsedRealtime();tap(title);
            String outcome="timeout";boolean topicOpened=false;
            for(int i=0;i<100;i++) {
                Thread.sleep(500);AccessibilityNodeInfo root=snapshot();
                // Accessibility can lag behind the rendered WebView. This is only an entry
                // candidate: revealReadableContent below still requires expanded real body.
                boolean[] ownCandidate={false};runOnMainSync(()->{
                    if(foreground!=null){WebView web=findOwnReader(foreground.getWindow().getDecorView());ownCandidate[0]=web!=null&&web.isShown();}
                });
                if(ownCandidate[0]||search(root,"内容来自原作者",false)!=null){outcome="own-reader";break;}
                if(search(root,"已过滤视频主题",true)!=null){outcome="FILTERED_VIDEO_TOPIC";break;}
                if(sourceKeys[n].equals("HUPU")&&search(root,"原文包含视频",false)!=null){outcome="MEDIA_LIMITED_NATIVE_NO_MAIN_TEXT";break;}
                if(search(root,"登录 / 加载后读取",true)!=null){outcome="source-assistance-required";break;}
                if(!topicOpened&&search(root,"选择一篇内容继续阅读",false)!=null) {
                    AccessibilityNodeInfo article=search(root,"，进入阅读",false);if(article!=null){tap(article.getContentDescription().toString());topicOpened=true;i=0;}
                }
            }
            check(outcome.equals("own-reader"),names[n]+" article outcome="+outcome+"; assistance/login/timeout is not readable content");
            stage="expand-body";sourceCheckpoint(report,names[n]+": PROGRESS own-reader candidate; bound-section body not yet verified");
            org.json.JSONObject body=revealReadableContent(sourceKeys[n]);articleMs=android.os.SystemClock.elapsedRealtime()-articleStart;
            sourceCheckpoint(report,names[n]+": PROGRESS selected-section body verified; section="+body.optString("verifiedSection")+"; role="+body.optString("role")+"; substantiveParagraphs="+body.optInt("substantiveParagraphs")+"; paragraphs="+body.optInt("paragraphs")+"; decodedNonEmojiImages="+body.optInt("loadedContentImages")+"; before screenshot");
            screenshot("source-"+n+"-expanded");if(names[n].equals("知乎")){stage="second-actual-answer";body.put("zhihuActualAnswers",verifySecondZhihuAnswer());sourceCheckpoint(report,names[n]+": PROGRESS second actual answer body verified; before screenshot");screenshot("source-"+n+"-second-answer");}
            if(sourceKeys[n].equals("CLS")||sourceKeys[n].equals("GEEKPARK")){
                check(body.optInt("substantiveParagraphs")>0,"New platform exposes actual text paragraphs");
                tap("返回");await("News",true,10);await(names[n],true,5);
                sourceCheckpoint(report,names[n]+": reader Back returned to the platform board");
            }
            if(fixedTieba&&sourceKeys[n].equals("TIEBA")){
                check(body.optString("sourceHref").equals("https://tieba.baidu.com/p/11061609054"),"Fixed Tieba article URL must match before second-floor inspection");
                stage="second-tieba-floor";body.put("tiebaSecondFloor",verifyFixedTiebaSecondFloor());
                sourceCheckpoint(report,"贴吧: SECOND_FLOOR real body identity and absence of observed badge pollution verified; before screenshot");screenshot("source-"+n+"-second-floor");
            }
            if(sourceKeys[n].equals("WALLSTREET")||sourceKeys[n].equals("SMZDM")){
                if(wallstreetOriginal)check(body.optString("sourceHref").equals("https://wallstreetcn.com/articles/3782819"),"Original WSCN article URL must match; changing sample is not a regression proof");
                sourceCheckpoint(report,names[n]+": TEXT_READABLE; boardMs="+boardMs+"; articleThroughExpansionMs="+articleMs+"; body="+body);
                stage="first-article-image";org.json.JSONObject image=observeFirstArticleImage(body,report);body.put("firstImageObservation",image);
                sourceCheckpoint(report,names[n]+": FIRST_IMAGE "+(image.optBoolean("decoded")?"DECODED_IN_VIEWPORT":"NOT_DECODED_WITHIN_BOUND")+"; state="+image+"; before screenshot");
                screenshot("source-"+n+"-first-image");
                if(wallstreetOriginal)check(image.optString("src").equals("https://wpimg-wscn.awtmt.com/fffc8c95-d213-444a-8e29-d4497aa75875.png"),"Original first-image URL must match, not another image");
                if(smzdmImageJourney){check("https://www.smzdm.com/p/183314225/".equals(body.optString("sourceHref")),"Fixed SMZDM own reader must preserve the original product identity");java.net.URI hero=new java.net.URI(image.optString("src"));check("https".equals(hero.getScheme())&&"qny.smzdm.com".equals(hero.getHost())&&hero.getPath().startsWith("/202104/20/"),"Actual observed product hero must be retained, not recommendation/avatar");sourceCheckpoint(report,"FIXED_SMZDM image="+imagePathShape(image.optString("src"))+" state="+privateSafeImage(image));}
                check(image.optBoolean("decoded")&&image.optBoolean("inViewport"),"Text readable, but first article image not decoded in viewport within 12-second observation");
                stage="image-preview";verifyReleaseImagePreview(body,image,report,n);
            }
            if(weiboReaderJourney&&sourceKeys[n].equals("WEIBO")){stage="weibo-reader-journey";weiboReaderJourney(body,report);}
            boolean mediaLimited=sourceKeys[n].equals("HUPU")&&body.optString("notice").contains("视频");
            if(mediaLimited)unavailable++;
            report.append(names[n]).append(mediaLimited?": MAIN_TEXT_READABLE_BUT_MEDIA_LIMITED; ":sourceKeys[n].equals("HUPU")?": MAIN_POST_READABLE sampled content; ":": READABLE sampled content; ").append("boardMs=").append(boardMs).append("; articleThroughExpansionMs=").append(articleMs).append("; title=").append(weiboReaderJourney?"[public topic omitted]":title).append("; body=").append(weiboReaderJourney?privateSafeBody(body):body).append('\n');
            }catch(Exception|AssertionError error){
                unavailable++;long now=android.os.SystemClock.elapsedRealtime();report.append(names[n]).append(": BLOCKED/ERROR stage=").append(stage).append("; boardMs=").append(boardMs<0&&boardStart>0?now-boardStart:boardMs).append("; articleMs=").append(articleStart>0?now-articleStart:-1).append("; title=").append(weiboReaderJourney?"[public topic omitted]":title).append("; reason=").append(error.getMessage()).append('\n');
                if(error instanceof SourceContentUnavailable)report.append("CONTENT_LIMITED observation=").append(((SourceContentUnavailable)error).observation).append('\n');
                // Persist the original failure before screenshot APIs can themselves fail.
                if(readerTapDiagnostics.length()>0){report.append("Own-reader pointer diagnostics:\n").append(readerTapDiagnostics);readerTapDiagnostics.setLength(0);}
                saveSourceReport(report);
                try{screenshot("source-"+n+"-blocked");}catch(Throwable captureFailure){report.append("Failure screenshot unavailable: ").append(captureFailure.getClass().getSimpleName()).append(": ").append(captureFailure.getMessage()).append('\n');saveSourceReport(report);}
                if(weiboProbe&&sourceKeys[n].equals("WEIBO")&&search(snapshot(),"登录 / 加载后读取",true)!=null){try{observeWeiboRecovery(report);}catch(Throwable probeFailure){sourceCheckpoint(report,"WEIBO_RECOVERY_OBSERVER_ERROR "+probeFailure.getClass().getSimpleName()+": "+probeFailure.getMessage());}}
            }
            if(readerTapDiagnostics.length()>0)report.append("Own-reader pointer diagnostics:\n").append(readerTapDiagnostics);
            saveSourceReport(report);
            Bundle progress=new Bundle();progress.putString("stream",report.toString());sendStatus(0,progress);
        }
        File dir=saveSourceReport(report);
        Bundle result=new Bundle();result.putString("stream",(unavailable==0?"PASS release source observations":"OBSERVATIONS filter="+sourceFilter+"; "+unavailable+" blocked/errors")+"\n"+report+"Output: "+dir+"\nThis is anonymous access, not authenticated-source certification.");finish(unavailable==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void smzdmImageProbe()throws Exception{
        final String url="https://www.smzdm.com/p/183314225/";StringBuilder report=new StringBuilder();Activity source=null;Bundle result=new Bundle();int code=Activity.RESULT_CANCELED;
        try{
            sourceCheckpoint(report,"SMZDM_FIXED_IMAGE_PROBE public product=/p/:id/ exact183314225=true; DOM only tag/class/image ownership and dimensions; no HTML/text/cookies exported. Static anonymous desktop-UA comparison is a separate request, not exact Repository response.");
            try{java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(url).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");c.setRequestProperty("Referer","https://www.smzdm.com/");
                try{int status=c.getResponseCode();String html="";if(status==200){try(java.io.InputStream in=c.getInputStream();java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>2*1024*1024)throw new java.io.IOException("Static response exceeds 2MiB observation limit");bytes.write(b,0,n);}html=bytes.toString("UTF-8");}}
                    java.util.regex.Matcher imgs=java.util.regex.Pattern.compile("<img\\b[^>]*>",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);int all=0,main=0;while(imgs.find()){all++;if(imgs.group().contains("main-img"))main++;}sourceCheckpoint(report,"STATIC_ANONYMOUS http="+status+" chars="+html.length()+" imgTags="+all+" mainImgTags="+main+" feedMainToken="+html.contains("feed-main")+" expectedArticleIdToken="+html.contains("3_183314225")+" ogImageToken="+html.contains("og:image")+"; regex token counts are not DOM ownership proof");
                }finally{c.disconnect();}
            }catch(Exception e){sourceCheckpoint(report,"STATIC_ANONYMOUS_UNAVAILABLE type="+e.getClass().getSimpleName());}
            source=startActivitySync(new Intent().setClassName("app.quietreader","app.quietreader.LoginActivity").putExtra("source","SMZDM").putExtra("url",url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final Activity page=source;WebView[] target={null};runOnMainSync(()->target[0]=findWeb(page.getWindow().getDecorView()));check(target[0]!=null,"Production SMZDM source WebView exists");WebView web=target[0];
String expression="(function(){var exact=location.protocol==='https:'&&location.hostname==='www.smzdm.com'&&location.pathname==='/p/183314225/';if(!exact)return {exact:false,login:/login|passport/.test(location.hostname+location.pathname)};function node(e){return e.tagName+(e.id?'#'+e.id:'')+(e.className?'.'+String(e.className).trim().replace(/\\s+/g,'.'):'');}function shape(s){try{var u=new URL(s,location.href);if(u.protocol!=='https:'&&u.protocol!=='http:')return u.protocol+'[omitted]';var p=u.pathname;return u.protocol+'//'+u.hostname+p.substring(0,p.lastIndexOf('/')+1)+':image';}catch(e){return 'invalid';}}var infos=document.querySelectorAll('#feed-main > .J_info[articleid]'),owners=[];for(var i=0;i<infos.length&&i<8;i++)owners.push({node:node(infos[i]),exactId:infos[i].getAttribute('articleid')==='3_183314225',idPrefix:(infos[i].getAttribute('articleid')||'').split('_')[0],images:infos[i].querySelectorAll('img').length,expectedHero:infos[i].querySelectorAll('.img-box > img.main-img').length});var imgs=document.querySelectorAll('img.main-img,.txt-detail img'),out=[],seen=[];for(var j=0;j<imgs.length&&out.length<24;j++){var im=imgs[j];if(seen.indexOf(im)>=0)continue;seen.push(im);var chain=[],p=im;for(var k=0;p&&k<6;k++,p=p.parentElement)chain.push(node(p));var owner=im.closest('[articleid]'),r=im.getBoundingClientRect();out.push({chain:chain,ownerExact:!!owner&&owner.getAttribute('articleid')==='3_183314225',ownerIdPrefix:owner?(owner.getAttribute('articleid')||'').split('_')[0]:'none',src:shape(im.currentSrc||im.src),hasSrc:!!im.getAttribute('src'),hasDataSrc:!!im.getAttribute('data-src'),hasSrcset:!!im.getAttribute('srcset'),complete:im.complete,naturalWidth:im.naturalWidth,naturalHeight:im.naturalHeight,width:r.width,height:r.height,top:r.top});}return {exact:true,ready:document.readyState,allImages:document.images.length,expectedHero:document.querySelectorAll('#feed-main > .J_info[articleid] .img-box > img.main-img').length,mainImgAll:document.querySelectorAll('img.main-img').length,articleImages:document.querySelectorAll('.item-preferential img,.item-box img').length,owners:owners,images:out};})()";
            org.json.JSONObject state=null;for(int n=0;n<10;n++){Thread.sleep(2000);state=sourceJson(web,expression);sourceCheckpoint(report,"DYNAMIC sample="+(n+1)+" "+state);if(state.optBoolean("login"))break;}
            screenshot("source-6-original-product");
            if(state!=null&&state.optBoolean("exact")){sourceJson(web,"(function(){var im=document.querySelector('img.main-img,.img-box img');if(im){im.scrollIntoView({block:'center'});return {scrolled:true};}return {scrolled:false};})()");Thread.sleep(1500);sourceCheckpoint(report,"AFTER_VISIBLE "+sourceJson(web,expression));screenshot("source-6-original-product-image");}
            sourceCheckpoint(report,"PASS release source observations; diagnostic completed only, not asserting product contains image or reader completeness");code=Activity.RESULT_OK;
        }catch(Throwable t){sourceCheckpoint(report,"PROBE_ERROR type="+t.getClass().getSimpleName()+" message="+t.getMessage());try{screenshot("source-6-probe-error");}catch(Exception ignored){}}
        finally{if(source!=null){final Activity closing=source;runOnMainSync(closing::finish);}result.putString("stream",report+"\nOutput: "+saveSourceReport(report).getAbsolutePath()+"\n");finish(code,result);}
    }
    private void paginationChecks(Activity home)throws Exception {
        String first="https://bbs.hupu.com/642690580.html",second="https://bbs.hupu.com/642690580-2.html";
        runOnMainSync(()->home.startActivityForResult(new Intent().setClassName("app.quietreader","app.quietreader.LoginActivity").putExtra("source","HUPU").putExtra("url",first),100));
        await("豆包这么厉害了吗",false,40);tap("读取到 News");await("内容来自原作者",false,15);
        if(mode.equals("reader-state")) {
            int[] position={0};
            runOnMainSync(()->{WebView web=findWeb(home.getWindow().getDecorView());check(web!=null&&!web.getSettings().getJavaScriptEnabled(),"Own reader must keep scripts off");web.scrollTo(0,900);position[0]=web.getScrollY();});
            check(position[0]>0,"Reader did not scroll");
            ActivityMonitor monitor=addMonitor("app.quietreader.MainActivity",null,false);runOnMainSync(home::recreate);
            Activity restored=waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);check(restored!=null,"Reader recreation failed");Thread.sleep(1500);
            runOnMainSync(()->{WebView web=findWeb(restored.getWindow().getDecorView());check(web!=null,"Reader lost after recreation");check(Math.abs(web.getScrollY()-position[0])<30,"Reader position lost: expected "+position[0]+" actual "+web.getScrollY());web.scrollTo(0,0);});
            await("内容来自原作者",false,15);screenshot("06-reader-restored");
            Bundle result=new Bundle();result.putString("stream","PASS "+assertions+" reader-recreation assertions including actual scroll position and script isolation.");finish(Activity.RESULT_OK,result);return;
        }
        tap("继续阅读下一页 →");await("内容来自原作者",false,40);
        ActivityMonitor monitor=addMonitor("app.quietreader.LoginActivity",null,false);
        menu("来源 / 登录");Activity page=waitForMonitorWithTimeout(monitor,10000);removeMonitor(monitor);
        check(page!=null&&second.equals(page.getIntent().getStringExtra("url")),"Next page did not remain on expected thread");
        Bundle result=new Bundle();result.putString("stream","PASS "+assertions+" real pagination assertions; next source URL matched the same thread's second page.");finish(Activity.RESULT_OK,result);
    }
    private void offlineChecks()throws Exception {
        await("缓存",false,15);check(true,"Dated cached board survived process restart");
        final String[] cookie={null};
        runOnMainSync(()->cookie[0]=CookieManager.getInstance().getCookie("https://quiet-reader.invalid/"));
        check(cookie[0]!=null&&cookie[0].contains("qaSession=persisted"),"Synthetic session cookie did not persist");
        check(getTargetContext().getSharedPreferences("MainActivity",0).getInt("font",0)==25,"Font preference did not persist");
        check(search(snapshot(),"收藏",false)==null,"Removed favorites entry must not return after process restart");verifySavedUnchanged();
        menu("刷新榜单");await("刷新失败 · 缓存",false,40);await("，进入阅读",false,10);check(true,"Failed offline refresh discarded cached board");
        screenshot("05-offline-cache");
        runOnMainSync(()->{CookieManager.getInstance().setCookie("https://quiet-reader.invalid/","qaSession=; Max-Age=0; Path=/; Secure");CookieManager.getInstance().flush();});
    }
    private void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private AccessibilityNodeInfo search(AccessibilityNodeInfo node,String needle,boolean exact){
        if(node==null)return null;
        if(android.os.Build.VERSION.SDK_INT<35)node.refresh();
        String text=node.getText()==null?"":node.getText().toString();
        String desc=node.getContentDescription()==null?"":node.getContentDescription().toString();
        if(exact?(text.equals(needle)||desc.equals(needle)):(text.contains(needle)||desc.contains(needle)))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=search(node.getChild(i),needle,exact);if(found!=null)return found;}
        return null;
    }
    private AccessibilityNodeInfo await(String text,boolean exact,int seconds)throws Exception {
        for(int i=0;i<seconds*2;i++){
            AccessibilityNodeInfo found=search(snapshot(),text,exact);
            if(found!=null)return found;
            AccessibilityNodeInfo[] nativeResult={null};runOnMainSync(()->{if(foreground!=null&&foreground.getWindow().getDecorView().hasWindowFocus()){View match=nativeView(foreground.getWindow().getDecorView(),text,exact);if(match!=null)nativeResult[0]=match.createAccessibilityNodeInfo();}});
            if(nativeResult[0]!=null)return nativeResult[0];Thread.sleep(500);
        }
        throw new AssertionError("Timed out waiting for "+text);
    }
    private void tap(String text)throws Exception {
        android.graphics.Rect rect=new android.graphics.Rect();boolean[] found={false};
        runOnMainSync(()->{if(foreground!=null&&foreground.getWindow().getDecorView().hasWindowFocus()){View match=nativeView(foreground.getWindow().getDecorView(),text,true);while(match!=null&&!match.isClickable())match=match.getParent() instanceof View?(View)match.getParent():null;if(match!=null)found[0]=match.getGlobalVisibleRect(rect);}});
        if(found[0]){long now=android.os.SystemClock.uptimeMillis();sendPointerSync(android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,rect.centerX(),rect.centerY(),0));sendPointerSync(android.view.MotionEvent.obtain(now,now+60,android.view.MotionEvent.ACTION_UP,rect.centerX(),rect.centerY(),0));check(true,"Native pointer tap: "+text);Thread.sleep(500);return;}
        AccessibilityNodeInfo node=await(text,true,10);
        while(node!=null&&!node.isClickable())node=node.getParent();
        check(node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK),"Cannot activate "+text);Thread.sleep(500);
    }
    private void menu(String action)throws Exception {tap("更多选项");tap(action);}
    private void setBoardQuery(String query)throws Exception{
        Bundle arguments=new Bundle();arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,query);
        runOnMainSync(()->{View input=nativeView(foreground.getWindow().getDecorView(),"搜索当前榜单",true);check(input!=null&&input.performAccessibilityAction(AccessibilityNodeInfo.ACTION_SET_TEXT,arguments),"Public search control accepts exact live title");});
        Thread.sleep(400);waitForIdleSync();
    }
    private void verifyBoardQuery(String query){
        runOnMainSync(()->{View input=nativeView(foreground.getWindow().getDecorView(),"搜索当前榜单",true);check(input instanceof android.widget.TextView&&query.contentEquals(((android.widget.TextView)input).getText()),"Release reader back retains exact live-title query");});
    }
    private String boundBoardTitle(String source,String description)throws Exception{org.json.JSONArray board=new org.json.JSONArray(getTargetContext().getSharedPreferences("boards",0).getString(source,"[]"));for(int i=0;i<board.length();i++){org.json.JSONObject item=board.getJSONObject(i);if(description.equals(item.getString("title")+"，"+item.optString("detail")+"，进入阅读"))return item.getString("title");}throw new AssertionError("Selected card does not match exact cached title/detail identity");}
    private void rememberSaved(){android.content.SharedPreferences actual=getTargetContext().getSharedPreferences("MainActivity",0);getContext().getSharedPreferences("release-qa",0).edit().putBoolean("savedPresent",actual.contains("saved")).putString("savedValue",actual.getString("saved","[]")).commit();}
    private void verifySavedUnchanged(){android.content.SharedPreferences actual=getTargetContext().getSharedPreferences("MainActivity",0),baseline=getContext().getSharedPreferences("release-qa",0);check(actual.contains("saved")==baseline.getBoolean("savedPresent",false)&&actual.getString("saved","[]").equals(baseline.getString("savedValue","[]")),"Legacy favorites preferences remain unchanged without exposing an entry");}
    private AccessibilityNodeInfo horizontal(AccessibilityNodeInfo node){
        if(node==null)return null;
        if("android.widget.HorizontalScrollView".contentEquals(node.getClassName()))return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=horizontal(node.getChild(i));if(found!=null)return found;}
        return null;
    }
    private void selectSource(String name)throws Exception {
        // A visible native tab can precede accessibility-tree refresh after Activity launch.
        // Use its real on-screen bounds and the existing pointer-tap path, not app internals.
        for(int attempt=0;attempt<10;attempt++){
            boolean[] visible={false};runOnMainSync(()->{if(foreground!=null){View tab=nativeView(foreground.getWindow().getDecorView(),name,true);android.graphics.Rect bounds=new android.graphics.Rect();visible[0]=tab!=null&&tab.getGlobalVisibleRect(bounds)&&bounds.width()>0;}});
            if(visible[0]){tap(name);return;}Thread.sleep(100);
        }
        for(int i=0;i<8;i++){AccessibilityNodeInfo row=horizontal(snapshot());if(row==null||!row.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))break;Thread.sleep(200);}
        for(int i=0;i<8;i++){
            AccessibilityNodeInfo node=search(snapshot(),name,true);
            if(node!=null&&node.isVisibleToUser()){tap(name);return;}
            AccessibilityNodeInfo row=horizontal(snapshot());if(row==null||!row.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))break;Thread.sleep(300);
        }
        throw new AssertionError("Source tab not reachable: "+name);
    }
    private void screenshot(String name)throws Exception {
        // Accessibility can expose DOM text before the WebView compositor presents its first frame.
        Thread.sleep(800);
        Bitmap bitmap=getUiAutomation().takeScreenshot();check(bitmap!=null,"Missing screenshot");
        File dir=new File(getTargetContext().getExternalFilesDir(null),outputDirectory);dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    private void verifyReaderAfterFont(Activity home)throws Exception {
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);String[] value={null};
        runOnMainSync(()->{
            WebView web=findOwnReader(home.getWindow().getDecorView());check(web!=null&&!web.getSettings().getJavaScriptEnabled(),"Own reader scripts must remain disabled");
            check(web.getSettings().getTextZoom()==Math.round(25*100f/19),"Selected font is not applied to reader");
            // Read our escaped document through the public WebView API, not app-private reflection.
            // WebView 69's accessibility subtree can remain stale after the native font dialog closes.
            web.getSettings().setJavaScriptEnabled(true);
            web.evaluateJavascript("!!document.querySelector('meta[name=quiet-reader-source]') && Array.from(document.querySelectorAll('.part p:not(.preview)')).some(function(p){return p.textContent.trim().length>0;})",raw->{web.getSettings().setJavaScriptEnabled(false);value[0]=raw;done.countDown();});
        });
        check(done.await(5,java.util.concurrent.TimeUnit.SECONDS),"Reader DOM inspection timed out");
        check("true".equals(value[0]),"Rendered own-reader actual body disappeared after font change");
    }
    private void brandingChecks(Activity home)throws Exception {
        android.content.pm.PackageManager pm=getTargetContext().getPackageManager();
        android.content.pm.ApplicationInfo info=pm.getApplicationInfo("app.quietreader",0);
        check("News".contentEquals(pm.getApplicationLabel(info)),"Installed app label is News");
        android.content.pm.PackageInfo version=pm.getPackageInfo(info.packageName,0);
        check("0.3.24".equals(version.versionName)&&version.versionCode==35,"Exact renamed release version");
        android.graphics.drawable.Drawable icon=pm.getApplicationIcon(info);
        check(icon instanceof android.graphics.drawable.AdaptiveIconDrawable,"Launcher icon supports system masks");
        android.graphics.drawable.AdaptiveIconDrawable adaptive=(android.graphics.drawable.AdaptiveIconDrawable)icon;
        Bitmap background=Bitmap.createBitmap(108,108,Bitmap.Config.ARGB_8888);
        adaptive.getBackground().setBounds(0,0,108,108);adaptive.getBackground().draw(new android.graphics.Canvas(background));
        check(background.getPixel(54,54)==android.graphics.Color.rgb(184,0,0),"Launcher background is requested red");background.recycle();
        Bitmap rendered=Bitmap.createBitmap(432,432,Bitmap.Config.ARGB_8888);
        icon.setBounds(0,0,432,432);icon.draw(new android.graphics.Canvas(rendered));
        int whites=0,greens=0;for(int y=0;y<432;y++)for(int x=0;x<432;x++){int c=rendered.getPixel(x,y);if(android.graphics.Color.alpha(c)<240)continue;int r=android.graphics.Color.red(c),g=android.graphics.Color.green(c),b=android.graphics.Color.blue(c);if(r>240&&g>240&&b>240)whites++;if(g>r+20&&g>b)greens++;}
        check(whites>1500&&greens==0,"White wordmark visible; old green artwork absent");
        File dir=new File(getTargetContext().getExternalFilesDir(null),outputDirectory);dir.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(dir,"icon.png"))){rendered.compress(Bitmap.CompressFormat.PNG,100,out);}rendered.recycle();
        runOnMainSync(()->{check(nativeView(home.getWindow().getDecorView(),"News",true)!=null,"Visible home brand is News");check(nativeView(home.getWindow().getDecorView(),"静读",true)==null,"Old home brand removed");});
        runOnMainSync(()->{
            check(nativeView(home.getWindow().getDecorView(),"设置",true)==null,"No duplicate standalone settings button");
            check(nativeView(home.getWindow().getDecorView(),"搜索当前榜单",true)==null,"No temporary title-filter search box");
            View first=nativeView(home.getWindow().getDecorView(),"知乎",true);check(first!=null&&first.isSelected(),"Fresh launch selects Zhihu, the first platform");
        });
        screenshot("01-brand-home");menu("阅读设置");await("返回 · 阅读设置",true,10);tap("返回 · 阅读设置");await("News",true,10);
        runOnMainSync(()->{
            View root=home.getWindow().getDecorView();int right=-1;
            for(String source:new String[]{"综合","知乎","爱范儿","财联社","什么值得买","虎扑","微博"}){
                View tab=nativeView(root,source,true);android.graphics.Rect bounds=new android.graphics.Rect();
                check(tab!=null&&tab.getGlobalVisibleRect(bounds)&&bounds.left>=right,"Requested platform order: "+source);right=bounds.right;
            }
            check(nativeView(root,"贴吧",true)==null&&nativeView(root,"华尔街见闻",true)==null&&nativeView(root,"极客公园",true)==null,"Retired platform entries absent");
        });
        for(String source:new String[]{"综合","知乎","爱范儿","财联社","什么值得买","虎扑","微博"}){
            tap(source);Thread.sleep(250);
            runOnMainSync(()->{View tab=nativeView(foreground.getWindow().getDecorView(),source,true);check(tab!=null&&tab.isSelected(),"Platform switching remains available: "+source);});
            if(source.equals("综合")){Thread.sleep(800);screenshot("02-brand-aggregate");runOnMainSync(()->check(nativeView(foreground.getWindow().getDecorView(),"综合推荐 Top100",false)!=null,"Released aggregate uses Top100 disclosure"));}
        }
        Bundle result=new Bundle();result.putString("stream","PASS "+assertions+" release UI assertions. News label/icon, compact header without search/duplicate settings, overflow settings return and aggregate plus six platform switches. No account data cleared.");finish(Activity.RESULT_OK,result);
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            if(mode.equals("sources")){sourceChecks();return;}
            Activity home=startActivitySync(new Intent().setClassName("app.quietreader","app.quietreader.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await("News",true,15);check(true,"Home");
            if(mode.equals("branding")){brandingChecks(home);return;}
            if(mode.equals("event-search-probe")){EventSearchProbe.run(this,home,sourceFilter);return;}
            if(mode.equals("pagination")||mode.equals("reader-state")){paginationChecks(home);return;}
            if(offline){offlineChecks();result.putString("stream","PASS "+assertions+" offline/restart assertions. Dated cache, legacy preference preservation, font and synthetic WebView cookie persistence; not real account login certification.");finish(Activity.RESULT_OK,result);return;}
            rememberSaved();check(search(snapshot(),"收藏",false)==null,"Home has no favorites entry");
            selectSource("虎扑");
            menu("刷新榜单");await("更新于",false,45);
            AccessibilityNodeInfo card=null;String title="";boolean readable=false;
            for(int candidate=0;candidate<5;candidate++){
                card=await("，进入阅读",false,45);String description=card.getContentDescription().toString();title=boundBoardTitle("HUPU",description);
                check(!title.contains("合成测试"),"Live release check must not consume synthetic board fixtures");
                screenshot("01-live-board");setBoardQuery(title);await(description,true,10);tap(description);
                boolean filtered=false;long deadline=android.os.SystemClock.elapsedRealtime()+45000;
                while(android.os.SystemClock.elapsedRealtime()<deadline){AccessibilityNodeInfo currentTree=snapshot();
                    if(search(currentTree,"搜索当前榜单",true)!=null&&search(currentTree,description,true)==null){filtered=true;break;}
                    boolean[] own={false};runOnMainSync(()->{WebView web=findOwnReader(foreground.getWindow().getDecorView());own[0]=web!=null&&web.isShown();});
                    if(own[0]){readable=true;break;}Thread.sleep(250);
                }
                if(!filtered)break; // Assistance, network failure or unreadable text must still fail.
                screenshot("video-filtered-"+candidate);await("搜索当前榜单",true,10);
                check(search(snapshot(),description,true)==null,"Confirmed video topic must disappear from its original filtered board");
                setBoardQuery("");check(search(snapshot(),description,true)==null,"Clearing search must not resurrect the confirmed video topic");
            }
            check(readable,"No own-reader text candidate within five entries; filtered videos are not counted as article-readable passes");await(title,false,10);
            org.json.JSONObject liveBody=revealReadableContent("HUPU");check(meaningfulBody(liveBody,true),"Real selected main post has substantive text, not only a reader shell");
            screenshot("02-live-reader");
            menu("阅读字号");tap("特大 · 25");verifyReaderAfterFont(home);
            screenshot("03-large-font");check(search(snapshot(),"收藏",false)==null,"Reader has no favorites entry");tap("返回");
            await("搜索当前榜单",true,10);verifyBoardQuery(title);await(card.getContentDescription().toString(),true,10);screenshot("04b-search-retained");
            setBoardQuery("");tap("更多选项");await("全部平台",true,10);check(search(snapshot(),"收藏",false)==null,"Overflow has no favorites entry");
            screenshot("04-home-overflow");sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);verifySavedUnchanged();
            ActivityMonitor sourceMonitor=addMonitor("app.quietreader.LoginActivity",null,false);
            menu("来源 / 登录");Activity sourceActivity=waitForMonitorWithTimeout(sourceMonitor,10000);removeMonitor(sourceMonitor);
            check(sourceActivity!=null,"Source activity missing");
            await("返回热榜",true,15);tap("手机版");await("桌面版",true,10);
            ActivityMonitor recreation=addMonitor("app.quietreader.LoginActivity",null,false);runOnMainSync(sourceActivity::recreate);
            Activity restored=waitForMonitorWithTimeout(recreation,10000);removeMonitor(recreation);check(restored!=null,"Source recreation failed");
            await("桌面版",true,10);check(true,"Source mode retained after recreation");tap("桌面版");await("手机版",true,10);
            tap("返回");await("搜索当前榜单",true,10);
            runOnMainSync(()->{CookieManager.getInstance().setCookie("https://quiet-reader.invalid/","qaSession=persisted; Max-Age=3600; Path=/; Secure");CookieManager.getInstance().flush();});
            verifySavedUnchanged();result.putString("stream","PASS "+assertions+" release UI assertions. Actual Hupu board/article, live-title search return, font selection, compact overflow, no favorites entry, navigation, source-view toggle. Platform account login not simulated.");finish(Activity.RESULT_OK,result);
        } catch(Throwable e){try{screenshot("failure");}catch(Exception ignored){}result.putString("stream","FAIL release after "+assertions+" assertions: "+e);finish(Activity.RESULT_CANCELED,result);}
    }
}
