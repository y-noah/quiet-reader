package app.quietreader;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.webkit.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.json.*;
import static app.quietreader.Models.*;

/** Synthetic source DOM, actual official PNG bytes, production reader and cache.
 * Live mode additionally uses the production network path to the official CDN. */
final class TiebaEmojiProbe {
    private final Instrumentation test;private final boolean dark,live;
    private MainActivity reader;private Repository repo;private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    TiebaEmojiProbe(Instrumentation test,boolean dark,boolean live){this.test=test;this.dark=dark;this.live=live;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private void invoke(String name,Class<?>[] types,Object...args){try{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(reader,args);}catch(Exception e){throw new IllegalStateException(e);}}
    private WebView web(){return (WebView)field(reader,"readerWeb");}
    private String js(String code)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={""};ui(()->{WebView w=web();boolean enabled=w.getSettings().getJavaScriptEnabled();w.getSettings().setJavaScriptEnabled(true);w.evaluateJavascript(code,value->{result[0]=value;w.getSettings().setJavaScriptEnabled(enabled);done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IOException("Emoji DOM callback timeout");return result[0];}
    private void await(String label,BooleanSupplier ready)throws Exception{long end=SystemClock.elapsedRealtime()+10000;while(!ready.getAsBoolean()){if(SystemClock.elapsedRealtime()>end)throw new IOException("Emoji wait: "+label);Thread.sleep(45);}}
    private void decoded(int count)throws Exception{await("current official PNG document decode",()->{try{return "true".equals(js("location.href==="+JSONObject.quote((String)field(reader,"readerPageUrl"))+"&&document.readyState==='complete'&&document.querySelectorAll('img.emoji').length==="+count+"&&Array.from(document.querySelectorAll('img.emoji')).every(i=>i.complete&&i.naturalWidth>0&&i.naturalHeight>0)"));}catch(Exception e){throw new IllegalStateException(e);}});}
    private void font(int value,int count)throws Exception{String previous=(String)field(reader,"readerPageUrl");ui(()->invoke("changeFont",new Class<?>[]{int.class},value));await("font reload committed",()->!previous.equals(field(reader,"readerPageUrl")));decoded(count);frame();}
    private void frame()throws Exception{CountDownLatch visible=new CountDownLatch(1);ui(()->web().postVisualStateCallback(SystemClock.elapsedRealtime(),new WebView.VisualStateCallback(){public void onComplete(long id){visible.countDown();}}));if(!visible.await(3,TimeUnit.SECONDS))throw new IOException("Emoji visible frame timeout");}
    private String image(String url,String kind,String label){return "<img class='"+kind+"' src='"+url+"'"+(label==null?"":" alt='"+label+"'")+">";}
    private Document desktop(String url,String old,String modern){return SourceParser.article(Source.TIEBA,"<h1>贴吧表情与行内排版检查</h1><div id=j_p_postlist><div class=l_post><a class=d_name>楼主</a><div class=d_post_content id=post_content_991081><p>主帖：旧版"+image(old,"BDE_Smiley","微笑")+"新版"+image(modern,"emoji-img","开心")+"无说明<span class=pb-emot>"+image(modern,"",null)+"</span>文字继续。</p><p>懒加载<img class=BDE_Smiley src='data:placeholder' data-src='data:placeholder' data-original='"+old+"' title='笑脸'>仍在原来的句子里。</p></div></div><div class=l_post><a class=d_name>普通读者</a><div class=d_post_content id=post_content_991082>回复："+image(modern,"emoji-img","开心")+"和文字同行。</div><div class=lzl_single_post><a class=j_user_card>子回复读者</a><span class=lzl_content_main>楼中楼：<span class=pb-emot>"+image(old,"","赞同")+"</span>不会变成正文配图。</span></div></div></div>",url);}
    private Document modern(String url,String old,String modern){return SourceParser.article(Source.TIEBA,"<div class=pc-pb-box><div class=image-text><a class=head-name>楼主</a><span class=pb-title>新版贴吧表情检查</span><div class=pb-content-wrap><div class=richtext-item>主帖<span class=pb-emot>"+image(modern,"emoji-img","开心")+"</span>正常阅读。</div></div></div><div class=pc-pb-reply-list><div class=pb-comment-list><a class=head-name>普通读者</a><div class=comment-content><div class=pb-rich-text>回复"+image(old,"emoji-img","微笑")+"正常换行。</div><div class=lzl-wrapper><div class=pb-lzl-item><a class=head-name>子回复读者</a><div class=comment-content><div class=pb-rich-text>回复<div><a href='/home/main?un=parent'>普通读者</a></div><div>：楼中楼</div><span class=pb-emot>"+image(modern,"",null)+"</span>继续阅读。</div></div></div></div></div></div></div></div>",url);}
    private void geometry(String label)throws Exception{
        check("true".equals(js("Array.from(document.querySelectorAll('img.emoji')).every(i=>{const r=i.getBoundingClientRect(),p=i.closest('p'),f=parseFloat(getComputedStyle(p).fontSize);return !!p&&getComputedStyle(i).display==='inline-block'&&r.width>=f*.7&&r.width<=f*1.8&&Math.abs(r.width-r.height)<1&&r.height<60&&!i.closest('figure')})")),label+" uses small square inline images rather than figure cards");
        JSONObject layout=new JSONObject(js("(()=>({width:innerWidth,root:document.documentElement.scrollWidth,body:document.body.scrollWidth,font:parseFloat(getComputedStyle(document.querySelector('.part p')).fontSize),emoji:document.querySelector('img.emoji').getBoundingClientRect().width,offenders:Array.from(document.querySelectorAll('main,.part,p,img.emoji')).filter(e=>e.getBoundingClientRect().right>innerWidth+1).map(e=>({tag:e.tagName,width:e.getBoundingClientRect().width,right:e.getBoundingClientRect().right}))}))()"));
        report.append("LAYOUT ").append(label).append(' ').append(layout.toString()).append('\n');
        check(layout.getInt("root")<=layout.getInt("width")+1&&layout.getInt("body")<=layout.getInt("width")+1,label+" has no horizontal overflow");
        check("true".equals(js("!document.body.innerText.includes('[表情]')&&!document.body.innerText.includes('\\ufffc')&&Array.from(document.querySelectorAll('img.emoji')).every(i=>i.alt.length>0)")),label+" keeps accessible labels without visible generic placeholders");
    }
    private void screenshot(String name)throws Exception{
        frame();
        Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null)throw new IOException("Emoji screenshot unavailable");try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
    void run(){String suffix=Long.toString(System.currentTimeMillis()),url="https://tieba.baidu.com/p/"+suffix;
        String old=live?"https://tb2.bdstatic.com/tb/editor/images/client/image_emoticon1.png":"https://qr-emoji-"+suffix+".invalid/legacy.png",modern=live?"https://tb3.bdstatic.com/emoji/image_emoticon33@2x.png":"https://qr-emoji-"+suffix+".invalid/modern.png";
        SharedPreferences prefs=test.getTargetContext().getSharedPreferences("appearance",0);boolean had=prefs.contains("mode");int prior=prefs.getInt("mode",0),oldFont=19;
        try{
            report.append("SYNTHETIC Tieba source DOM; official PNGs; liveCDN=").append(live).append(" dark=").append(dark).append('\n');prefs.edit().putInt("mode",dark?2:1).commit();
            ui(()->repo=new Repository(test.getTargetContext()));
            if(!live){Map<String,byte[]> assets=new HashMap<>();for(String name:List.of("legacy-1.png","modern-33.png"))try(InputStream in=test.getContext().getAssets().open("tieba-emoji/"+name)){assets.put(name,in.readAllBytes());}
                ReaderImages acquired=new ReaderImages(u->CookieManager.getInstance().getCookie(u),(u,referer,cookies)->{if(!referer.equals(url))throw new IOException("Unexpected emoji referer");cookies.apply(u);return assets.get(u.equals(old)?"legacy-1.png":"modern-33.png");},64*1024);acquired.attach(test.getTargetContext());
                check(ImageType.detect(acquired.load(old,url)).equals("image/png")&&ImageType.detect(acquired.load(modern,url)).equals("image/png"),"actual official PNG bytes acquired through production disk-cache writer");
            }
            Document doc=desktop(url,old,modern);Item item=new Item(Source.TIEBA,doc.title,url,"");ArticleVersion.capture(item,doc);ui(()->repo.cacheArticle(doc));
            ArticleDiskCache bodies=new ArticleDiskCache(test.getTargetContext());await("body persisted",()->bodies.read(url,bodies.namespace(),ArticleDiskCache.session(url),true,System.currentTimeMillis())!=null);
            ReaderImages.shared.evictAll();ui(repo::releaseArticleMemory);
            check(repo.cachedArticle(item)==null,"reader starts without the in-memory body and image cache");
            reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));oldFont=(int)field(reader,"font");
            await("reader WebView",()->web()!=null);decoded(6);font(19,6);geometry("desktop main/reply/nested");double normalFont=Double.parseDouble(js("parseFloat(getComputedStyle(document.querySelector('.part p')).fontSize)"));
            check("true".equals(js("document.querySelectorAll('.part').length===3&&document.querySelectorAll('.discussion img.emoji').length===2")),"desktop main, reply and nested reply retain separate discussion ownership");
            check(field(reader,"dynamic")==null,"disk-restored body never starts the source-page WebView");
            screenshot("tieba-emoji-"+(live?"live":dark?"dark":"light")+"-normal.png");
            font(28,6);geometry("large reader font");check(Double.parseDouble(js("parseFloat(getComputedStyle(document.querySelector('.part p')).fontSize)"))>normalFont*1.35,"actual text size increases after the asynchronous font reload commits");Thread.sleep(500);check(Double.parseDouble(js("scrollY"))<2,"font increase at the article top keeps the complete title visible");
            screenshot("tieba-emoji-"+(live?"live":dark?"dark":"light")+"-large.png");
            Document restored=ReadingHistory.decode(ReadingHistory.encode(doc));ArticleVersion.capture(item,restored);ReaderImages.shared.evictAll();ui(()->invoke("render",new Class<?>[]{Document.class},restored));decoded(6);geometry("history snapshot restore");
            check(restored.sections.stream().flatMap(s->s.blocks.stream()).mapToInt(b->b.inlineImages.size()).sum()==6,"history snapshot retains every inline emoji span and URL");
            Document lines=SourceParser.article(Source.TIEBA,"<h1>连续表情行保留原文换行</h1><div id=j_p_postlist><div class=l_post><a class=d_name>读者</a><div class=d_post_content id=post_content_991089><p>文字保持正常段距。</p><p>"+image(old,"BDE_Smiley","微笑")+"</p><p>"+image(modern,"emoji-img","开心")+"</p><p>"+image(old,"BDE_Smiley","微笑")+"</p></div></div></div>",url);
            ArticleVersion.capture(item,lines);ui(()->invoke("render",new Class<?>[]{Document.class},lines));decoded(3);geometry("separate emoji paragraphs");
            check("true".equals(js("document.querySelectorAll('.part p.emoji-line').length===3&&document.querySelectorAll('.part p[data-reading-anchor]').length===4&&new Set(Array.from(document.querySelectorAll('.part p'),p=>p.id)).size===4")),"compact emoji lines preserve all source paragraphs and distinct reading anchors");
            check("true".equals(js("Array.from(document.querySelectorAll('p.emoji-line')).every(p=>parseFloat(getComputedStyle(p).marginBottom)<=4.1)&&parseFloat(getComputedStyle(document.querySelector('.part p:not(.emoji-line)')).marginBottom)>=17.9")),"emoji-only paragraph gaps shrink while prose paragraph spacing stays unchanged");
            screenshot("tieba-emoji-"+(live?"live":dark?"dark":"light")+"-compact-lines.png");
            Document next=modern(url,old,modern);ArticleVersion.capture(item,next);ui(()->invoke("render",new Class<?>[]{Document.class},next));decoded(3);geometry("modern main/reply/nested");
            check("true".equals(js("document.querySelectorAll('.part').length===3&&document.querySelectorAll('.discussion img.emoji').length===2")),"modern DOM retains one main, one reply and one nested reply without emoji promotion");
            check("true".equals(js("document.querySelectorAll('.nested-reply').length===1&&document.querySelector('#part-1 .section-label').textContent==='普通读者'&&document.querySelector('#part-2 .section-label').textContent.includes('楼内回复')&&document.querySelector('#part-2').getBoundingClientRect().left>document.querySelector('#part-1').getBoundingClientRect().left+10")),"author is not mislabeled as a reply target and nested reply is visibly indented");
            check("true".equals(js("document.querySelector('#part-2 .section-label').textContent==='子回复读者 · 楼内回复 · 回复 普通读者'&&parseFloat(getComputedStyle(document.querySelector('#part-2 .section-label')).fontSize)<parseFloat(getComputedStyle(document.querySelector('#part-2 p')).fontSize)&&!Array.from(document.querySelectorAll('#part-2 p')).some(e=>e.textContent.includes('普通读者')||e.textContent.trim()==='回复')")),"explicit target stays in small metadata without an oversized reply/name paragraph");
            screenshot("tieba-emoji-"+(live?"live":dark?"dark":"light")+"-modern.png");
            report.append("METRICS ").append(js("JSON.stringify({width:innerWidth,font:parseFloat(getComputedStyle(document.querySelector('.part p')).fontSize),emoji:document.querySelector('img.emoji').getBoundingClientRect().width,natural:Array.from(document.querySelectorAll('img.emoji')).map(i=>[i.naturalWidth,i.naturalHeight])})")).append('\n');
            if(repo.offline())throw new IOException("Legacy cache check requires an active emulator network");
            JSONObject oldSnapshot=ReadingHistory.encode(doc);oldSnapshot.remove("tiebaReplyRevision");
            JSONArray oldSections=oldSnapshot.getJSONArray("sections");for(int n=0;n<oldSections.length();n++){JSONObject part=oldSections.getJSONObject(n);part.remove("nestedReply");part.remove("parentSectionId");part.remove("replyToAuthor");}
            Document legacy=ReadingHistory.decode(oldSnapshot);legacy.cachedAt=System.currentTimeMillis();legacy.cachedSession=ArticleDiskCache.session(url);legacy.cachedNamespace=bodies.namespace();
            ui(()->repo.cacheArticle(legacy));
            check(repo.cachedArticle(item)==null,"online memory lookup rejects a legacy flat-comment body");
            await("legacy snapshot written without silently upgrading its structure",()->{ArticleDiskCache.Hit hit=bodies.read(url,bodies.namespace(),ArticleDiskCache.session(url),true,System.currentTimeMillis());return hit!=null&&hit.document.tiebaReplyRevision==0;});
            check(bodies.read(url,bodies.namespace(),ArticleDiskCache.session(url),false,System.currentTimeMillis())==null,"online disk lookup requests reacquisition of legacy structure");
            ArticleDiskCache.Hit offlineHit=bodies.read(url,bodies.namespace(),ArticleDiskCache.session(url),true,System.currentTimeMillis());
            check(offlineHit!=null&&offlineHit.document.hasContent()&&offlineHit.document.tiebaReplyRevision==0,"offline disk reading keeps the old body without inventing relationships");
            ReadingHistory history=new ReadingHistory(test.getTargetContext());history.save(url,legacy,Set.of(legacy.sections.get(0).id),321);
            JSONObject marker=history.get(url);
            check(marker!=null&&!marker.has("document")&&marker.optInt("position")==321&&marker.getJSONArray("expanded").length()==1,"online history refresh preserves position and expanded markers");
            check(new JSONObject(test.getTargetContext().getSharedPreferences("reading-history",0).getString(url,"{}")).has("document"),"history lookup preserves stored offline body instead of clearing user data");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{int savedFont=oldFont;ui(()->{if(reader!=null){invoke("changeFont",new Class<?>[]{int.class},savedFont);reader.finish();}if(repo!=null)repo.close();});test.waitForIdleSync();prefs.edit().remove("mode").apply();if(had)prefs.edit().putInt("mode",prior).commit();for(String image:List.of(old,modern))if(!live){ReaderImages.shared.remove(image);new ImageDiskCache(new File(test.getTargetContext().getCacheDir(),"reader-images-v1")).removeUrl(image);}test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove(url).commit();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
