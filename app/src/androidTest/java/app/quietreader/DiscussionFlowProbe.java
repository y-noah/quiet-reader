package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.MotionEvent;
import android.webkit.*;
import java.io.ByteArrayInputStream;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Controlled pages; actual reader DOM actions, stream ownership and cache IO. */
final class DiscussionFlowProbe {
    private final Instrumentation test;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC discussion pages; not live account or virtual-list certification\n");
    private String clickedAnchor;
    private double clickedAnchorTop;
    DiscussionFlowProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String message){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(message).append('\n');}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private static void set(Object owner,String name,Object value){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}catch(Exception error){throw new IllegalStateException(error);}}
    private void await(java.util.function.BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+9000;while(true){boolean[] ready={false};ui(()->ready[0]=condition.getAsBoolean());if(ready[0])return;if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Discussion flow timeout at "+Thread.currentThread().getStackTrace()[2]);Thread.sleep(40);}}
    private String js(MainActivity reader,String code)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] value={""};ui(()->{WebView web=(WebView)field(reader,"readerWeb");web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(code,result->{value[0]=result;done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Reader JS timeout");return value[0];}
    private void dom(MainActivity reader)throws Exception{
        long end=SystemClock.elapsedRealtime()+8000;
        while(SystemClock.elapsedRealtime()<end){String[] expected={""};ui(()->expected[0]=(String)field(reader,"readerPageUrl"));if(js(reader,"location.href==="+org.json.JSONObject.quote(expected[0])+"&&document.readyState==='complete'").equals("true"))return;Thread.sleep(50);}
        throw new IllegalStateException("Current reader DOM not committed");
    }
    private int click(MainActivity reader)throws Exception{
        dom(reader);
        if(js(reader,"!!document.querySelector('#reader-continuation[data-continuation=ready]')").equals("true")){
            await(()->System.currentTimeMillis()>(long)field(reader,"suppressAutoUntil"));
            ui(()->{WebView web=(WebView)field(reader,"readerWeb");web.scrollBy(0,-40);});
            org.json.JSONObject point=new org.json.JSONObject(js(reader,"(()=>{const target=Math.max(0,document.documentElement.scrollHeight-innerHeight),delta=target-scrollY;for(const a of document.querySelectorAll('[data-reading-anchor]')){const r=a.getBoundingClientRect();if(r.bottom>delta&&r.top<innerHeight+delta)return {id:a.id,top:r.top-delta,target};}return null})()"));
            clickedAnchor=point.getString("id");clickedAnchorTop=point.getDouble("top");int[] position={0};
            ui(()->{WebView web=(WebView)field(reader,"readerWeb");position[0]=Math.round((float)point.optDouble("target")*web.getScale());web.scrollTo(0,position[0]);report.append("AUTO css=").append(point).append(" nativeY=").append(web.getScrollY()).append(" content=").append(web.getContentHeight()).append(" height=").append(web.getHeight()).append(" scale=").append(web.getScale()).append(" paused=").append(field(reader,"morePaused")).append(" pending=").append(field(reader,"pendingViewport")!=null).append(" stopped=").append(field(reader,"wasStopped")).append('\n');});return position[0];
        }
        long end=SystemClock.elapsedRealtime()+8000;
        while(SystemClock.elapsedRealtime()<end){boolean[] current={false};ui(()->current[0]=field(reader,"readerPageUrl").equals(((WebView)field(reader,"readerWeb")).getUrl()));if(current[0]&&js(reader,"document.readyState==='complete'&&!!document.querySelector('a[href=\""+ReaderHtml.ACTION+"more\"]')").equals("true")){
            await(()->field(reader,"pendingViewport")==null&&!(boolean)field(reader,"viewportRestoreRunning")&&System.currentTimeMillis()>(long)field(reader,"suppressAutoUntil"));
            js(reader,"document.querySelector('a[href=\""+ReaderHtml.ACTION+"more\"]').scrollIntoView({block:'center'})");
            Thread.sleep(100);
            org.json.JSONObject point=new org.json.JSONObject(js(reader,"(()=>{const r=document.querySelector('a[href=\""+ReaderHtml.ACTION+"more\"]').getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,width:innerWidth,height:innerHeight}})()"));
            org.json.JSONObject anchor=new org.json.JSONObject(js(reader,"(()=>{for(const a of document.querySelectorAll('[data-reading-anchor]')){const r=a.getBoundingClientRect();if(r.bottom>0&&r.top<innerHeight)return {id:a.id,top:r.top}}return null})()"));clickedAnchor=anchor.getString("id");clickedAnchorTop=anchor.getDouble("top");int[] position={0};float[] xy={0,0};
            ui(()->{WebView web=(WebView)field(reader,"readerWeb");int[] location=new int[2];web.getLocationOnScreen(location);xy[0]=location[0]+(float)(point.optDouble("x")*web.getWidth()/point.optDouble("width"));xy[1]=location[1]+(float)(point.optDouble("y")*web.getHeight()/point.optDouble("height"));position[0]=web.getScrollY();report.append("RETRY css=").append(point).append(" native=").append(xy[0]).append(',').append(xy[1]).append(" web=").append(web.getWidth()).append('x').append(web.getHeight()).append(" scale=").append(web.getScale()).append('\n');});
            if(point.optDouble("x")<0||point.optDouble("y")<0||point.optDouble("x")>point.optDouble("width")||point.optDouble("y")>point.optDouble("height")){Thread.sleep(80);continue;}
            await(reader::hasWindowFocus);long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,xy[0],xy[1],0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,xy[0],xy[1],0);try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}return position[0];
        }Thread.sleep(50);}
        throw new IllegalStateException("Current reader DOM has no retry action");
    }
    private String reply(Source source,int id,String text){return source==Source.V2EX?"<div class=cell id=r_"+id+"><strong><a href='/member/reader"+id+"'>读者"+id+"</a></strong><div class=reply_content>"+text+"</div></div>":"<div class=l_post><a class=d_name>读者"+id+"</a><div class=d_post_content id=post_content_"+id+">"+text+"</div></div>";}
    private String reply(Source source,int id,String text,boolean metadata){return source!=Source.TIEBA||!metadata?reply(source,id,text):"<div class=l_post data-field='{\"content\":{\"post_id\":"+id+",\"post_no\":"+id+"}}'><a class=d_name>读者"+id+"</a><div class=d_post_content>"+text+"</div></div>";}
    private String html(Source source,int page,String next){
        String main="<p>这是始终保留的主帖段落。</p>".repeat(45);
        String rows=reply(source,10,"第一条已读回复",page>=2)+(page>=2?reply(source,11,"第二条追加回复",page>=3):"")+(page>=3?reply(source,12,"第三条追加回复"):"");
        return "<html><head><meta charset=utf-8></head><body><h1>统一讨论合成标题</h1>"+(source==Source.V2EX?"<div class=topic_content>"+main+"</div>"+rows:"<div id=j_p_postlist><div class=l_post data-field='{\"content\":{\"post_no\":1}}'><div class=d_post_content id=post_content_1>"+main+"</div></div>"+rows+"</div>")+(next.isEmpty()?"":"<div class=l_pager><a rel=next href='"+next+"'>下一页</a></div>")+"</body></html>";
    }
    private String targetPage(Source source,String url,int page){return source==Source.HUPU?url.replace(".html",page==1?".html":"-"+page+".html"):url+"?"+(source==Source.V2EX?"p":"pn")+"="+page;}
    private String html(Source source,int page,String next,String url){
        if(source!=Source.HUPU)return html(source,page,next);
        try{
            org.json.JSONArray rows=new org.json.JSONArray();for(int n=10;n<=Math.min(12,9+page);n++)rows.put(new org.json.JSONObject().put("pid",Integer.toString(n)).put("author",new org.json.JSONObject().put("puname","读者"+n)).put("content","<p>第"+n+"条合成回复</p>"));
            org.json.JSONObject thread=new org.json.JSONObject().put("tid",HupuDiscussion.threadId(url)).put("url",url).put("title","统一讨论合成标题").put("content","<p>这是始终保留的主帖段落。</p>".repeat(45)).put("hasVideo",false).put("status",0).put("visibleRange","ALL_SEE");
            org.json.JSONObject detail=new org.json.JSONObject().put("thread",thread).put("visibleRange","ALL_SEE").put("lights",new org.json.JSONArray()).put("replies",new org.json.JSONObject().put("current",page).put("list",rows));
            return "<h1>统一讨论合成标题</h1><script id='__NEXT_DATA__'>"+new org.json.JSONObject().put("props",new org.json.JSONObject().put("pageProps",new org.json.JSONObject().put("detail",detail)))+"</script>"+(next.isEmpty()?"":"<ul class=hupu-rc-pagination><li><a href='"+next+"'>下一页</a></li></ul>");
        }catch(Exception error){throw new IllegalStateException(error);}
    }
    private static final class Controlled implements DiscussionStream.Loader {
        int loads,cancels;Item item;Repository.Result<Document> callback;
        public void load(Item page,Repository.Result<Document> callback){loads++;item=page;this.callback=callback;}
        public void cancel(){cancels++;}
    }
    void run(){MainActivity reader=null;Activity cover=null;
        try{
            for(Source source:List.of(Source.V2EX,Source.TIEBA,Source.HUPU)){
                String url=source==Source.HUPU?"https://bbs.hupu.com/"+System.currentTimeMillis()+".html":source==Source.V2EX?"https://www.v2ex.com/t/"+System.currentTimeMillis():"https://tieba.baidu.com/p/"+System.currentTimeMillis();
                Item item=new Item(source,"统一讨论原始标题",url,"",false,"原始摘要",null);Document first=SourceParser.article(source,html(source,1,targetPage(source,url,2),url),url);ArticleVersion.capture(item,first);
                if(source==Source.TIEBA){Section original=first.sections.get(1),legacy=new Section("tieba-post-"+app.quietreader.discovery.HotCandidate.hash("10").substring(0,24),original.label,false);legacy.blocks.addAll(original.blocks);first.sections.set(1,legacy);}
                ui(()->{Repository repo=new Repository(test.getTargetContext());repo.cacheArticle(first);repo.close();});
                reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()));MainActivity owner=reader;
                await(()->field(owner,"readerWeb")!=null&&field(owner,"reading")==first);
                await(()->((WebView)field(owner,"readerWeb")).getContentHeight()>500);
                Controlled loader=new Controlled();ui(()->set(owner,"discussions",new DiscussionStream(owner,item,loader)));
                ui(()->((WebView)field(owner,"readerWeb")).scrollTo(0,350));
                check(js(owner,"document.querySelectorAll('#reader-continuation[data-continuation=ready]').length").equals("1"),source+": one continuation action in actual reader DOM");
                int beforeScroll=click(owner);String expectedAnchor=clickedAnchor;double expectedTop=clickedAnchorTop;await(()->loader.loads==1);ui(()->{try{Method m=MainActivity.class.getDeclaredMethod("loadMore");m.setAccessible(true);m.invoke(owner);}catch(Exception error){throw new IllegalStateException(error);}});
                check(loader.loads==1,source+": repeated action while pending creates only one source request");
                Document second=SourceParser.readingArticle(source,html(source,2,targetPage(source,url,3),url),loader.item.url);
                ui(()->{new ArticleSession(owner,loader.item).stamp(second);loader.callback.success(second);});
                await(()->((Document)field(owner,"reading")).discussionPage==2&&!((Boolean)field(owner,"loadingMore")));
                dom(owner);
                long anchorEnd=SystemClock.elapsedRealtime()+8000;double actualTop=Double.NaN;
                while(SystemClock.elapsedRealtime()<anchorEnd){String top=js(owner,"(()=>{const a=document.getElementById("+org.json.JSONObject.quote(expectedAnchor)+");return a?a.getBoundingClientRect().top:null})()");try{actualTop=Double.parseDouble(top);}catch(Exception ignored){}if(Math.abs(actualTop-expectedTop)<3)break;Thread.sleep(50);}
                check(Math.abs(actualTop-expectedTop)<3,source+": visible paragraph anchor stays in place after append (CSS top "+expectedTop+" -> "+actualTop+")");
                check(js(owner,"document.querySelectorAll('.discussion').length").equals("2"),source+": actual DOM appends one new reply without duplicate overlap");
                if(source==Source.TIEBA)ui(()->{Document current=(Document)field(owner,"reading");check(current.sections.get(1).id.equals(first.sections.get(1).id)&&current.sections.get(2).sourcePostId.equals("11"),"TIEBA: old numeric cache identity remains visible while canonical source IDs deduplicate incoming overlap");});
                ui(()->{Document shown=(Document)field(owner,"reading");check(shown.url.equals(url)&&shown.sections.get(0)==first.sections.get(0)&&shown.metadataHash.equals(first.metadataHash)&&shown.cachedAt==first.cachedAt,source+": original main post and acquisition identity remain unchanged");check(((Repository)field(owner,"repo")).cachedArticle(item)==shown,source+": merged discussion uses shared original-URL cache");});
                check(js(owner,"document.querySelectorAll('#reader-continuation[data-continuation=ready]').length").equals("1"),source+": next page still has one continuation action");
                click(owner);await(()->loader.loads==2);Repository.Result<Document> failed=loader.callback;ui(()->failed.failure("合成网络失败"));
                await(()->((Document)field(owner,"reading")).moreStatus.equals("error"));
                ui(()->check(((Document)field(owner,"reading")).sections.size()==3&&((Document)field(owner,"reading")).nextUrl.equals(targetPage(source,url,3)),source+": failure retains main, replies and retry cursor"));
                click(owner);try{await(()->loader.loads==3);}catch(Exception error){ui(()->report.append("DIAGNOSTIC ").append(source).append(" retry loads=").append(loader.loads).append(" loading=").append(field(owner,"loadingMore")).append(" busy=").append(((DiscussionStream)field(owner,"discussions")).busy()).append(" offline=").append(((Repository)field(owner,"repo")).offline()).append(" status=").append(((Document)field(owner,"reading")).moreStatus).append('\n'));throw error;}
                ui(()->{failed.success(second);check(((Document)field(owner,"reading")).discussionPage==2,source+": late failed-attempt callback cannot change active reading");});
                Repository.Result<Document> cancelled=loader.callback;
                cover=test.startActivitySync(new Intent(test.getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Activity overlay=cover;
                await(()->field(owner,"discussions")==null&&(boolean)field(owner,"discussionInterrupted"));check(loader.cancels>=3,source+": real background lifecycle closes pending discussion request");
                ui(overlay::finish);test.waitForIdleSync();cover=null;
                await(()->owner.hasWindowFocus()&&((Document)field(owner,"reading")).moreStatus.equals("error"));
                Controlled resumed=new Controlled();ui(()->{cancelled.success(second);set(owner,"discussions",new DiscussionStream(owner,item,resumed));});
                click(owner);await(()->resumed.loads==1);Document third=SourceParser.readingArticle(source,html(source,3,"",url),resumed.item.url);
                ui(()->{new ArticleSession(owner,resumed.item).stamp(third);resumed.callback.success(third);});
                await(()->((Document)field(owner,"reading")).discussionPage==3);
                dom(owner);
                check(js(owner,"document.querySelectorAll('.discussion').length===3&&!document.querySelector('a[href=\""+ReaderHtml.ACTION+"more\"]')").equals("true"),source+": end page keeps all replies and removes continuation action");
                ui(()->check(((Document)field(owner,"reading")).notice.contains("不代表"),source+": source lacking next page is not advertised as complete history"));
                persist(owner,item);if(source==Source.TIEBA)defaultAcquisition(owner,item,first);
                ui(owner::finish);test.waitForIdleSync();reader=null;
            }
            loadedTiebaNestedUi();emptyTiebaFloorUi();zhihuFastAppend();zhihuAnswerBudget();rejectedAcquisitions();redirectPageGuards();filteredDiscussion();filteredDiscussionUi();
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{MainActivity end=reader;Activity overlay=cover;ui(()->{if(overlay!=null)overlay.finish();if(end!=null)end.finish();});test.waitForIdleSync();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void filteredDiscussionUi()throws Exception{
        String url="https://bbs.hupu.com/"+System.currentTimeMillis()+".html";Item item=new Item(Source.HUPU,"合成全视频页继续文字",url,"");Document first=SourceParser.article(Source.HUPU,html(Source.HUPU,1,targetPage(Source.HUPU,url,2),url),url);ArticleVersion.capture(item,first);
        ui(()->{Repository repo=new Repository(test.getTargetContext());repo.cacheArticle(first);repo.close();});
        MainActivity owner=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()));
        try{
            await(()->field(owner,"readerWeb")!=null&&field(owner,"reading")==first);dom(owner);Controlled loader=new Controlled();ui(()->set(owner,"discussions",new DiscussionStream(owner,item,loader)));
            click(owner);await(()->loader.loads==1);Document second=SourceParser.article(Source.HUPU,html(Source.HUPU,2,targetPage(Source.HUPU,url,3),url),loader.item.url);second.sections.clear();second.blocks.clear();second.filteredSectionIds.add("hupu-reply-11");second.filteredVideos=1;
            ui(()->{new ArticleSession(owner,loader.item).stamp(second);loader.callback.success(second);});await(()->((Document)field(owner,"reading")).discussionPage==2);dom(owner);
            check(js(owner,"document.querySelectorAll('.discussion').length===1&&document.querySelectorAll('#reader-continuation[data-continuation=ready]').length===1&&!document.querySelector('video,iframe')").equals("true"),"actual tap over video-only page keeps text reply, one continuation and no player in DOM");
            check(js(owner,"document.querySelector('.continuation-status').textContent.trim()===''&&!document.querySelector('.notice')").equals("true")&&((Document)field(owner,"reading")).filteredVideos==1,"actual reader retains video filtering without an explanatory footer or empty failure");
            click(owner);await(()->loader.loads==2);Document third=SourceParser.article(Source.HUPU,html(Source.HUPU,3,"",url),loader.item.url);
            ui(()->{new ArticleSession(owner,loader.item).stamp(third);loader.callback.success(third);});await(()->((Document)field(owner,"reading")).discussionPage==3);dom(owner);
            check(js(owner,"document.querySelectorAll('.discussion').length===2&&!document.querySelector('a[href=\""+ReaderHtml.ACTION+"more\"]')&&!document.querySelector('video,iframe')").equals("true"),"second actual tap displays new text after filtered page, without resurrecting filtered reply or final continuation");
        }finally{ui(owner::finish);test.waitForIdleSync();}
    }
    private void filteredDiscussion()throws Exception{
        String url="https://bbs.hupu.com/"+System.currentTimeMillis()+".html";Item item=new Item(Source.HUPU,"合成视频回复边界",url,"");Document[] merged={null};Controlled loader=new Controlled();DiscussionStream[] stream={null};Repository repo=new Repository(test.getTargetContext());
        try{
            Document first=SourceParser.article(Source.HUPU,html(Source.HUPU,1,targetPage(Source.HUPU,url,2),url),url);ArticleVersion.capture(item,first);
            ui(()->{repo.cacheArticle(first);stream[0]=new DiscussionStream(test.getTargetContext(),item,loader);stream[0].more(first,new Repository.Result<>(){public void success(Document value){merged[0]=value;}public void failure(String reason){throw new IllegalStateException(reason);}});});
            Document video=SourceParser.article(Source.HUPU,html(Source.HUPU,2,targetPage(Source.HUPU,url,3),url),loader.item.url);video.sections.clear();video.blocks.clear();video.filteredSectionIds.add("hupu-reply-11");video.filteredVideos=1;
            ui(()->{new ArticleSession(test.getTargetContext(),loader.item).stamp(video);loader.callback.success(video);});
            check(merged[0]!=null&&merged[0].discussionPage==2&&merged[0].sections.equals(first.sections)&&merged[0].filteredVideos==1&&!stream[0].busy(),"video-only next page advances real stream, retains read content and releases request ownership");
            Document second=merged[0];
            ui(()->{repo.cacheArticle(second);stream[0].more(second,new Repository.Result<>(){public void success(Document value){merged[0]=value;}public void failure(String reason){throw new IllegalStateException(reason);}});});
            Document third=SourceParser.article(Source.HUPU,html(Source.HUPU,3,"",url),loader.item.url);
            ui(()->{new ArticleSession(test.getTargetContext(),loader.item).stamp(third);loader.callback.success(third);repo.cacheArticle(merged[0]);});
            check(merged[0].discussionPage==3&&merged[0].sections.size()==3&&merged[0].sections.stream().noneMatch(s->s.id.equals("hupu-reply-11"))&&merged[0].filteredSectionIds.contains("hupu-reply-11"),"subsequent text page adds new text while previously filtered reply cannot return");
            check(!merged[0].videoTopic&&!merged[0].filteredVideo&&merged[0].videos.isEmpty(),"video replies neither classify whole text topic as video nor create playable attachments");
            Field writer=ArticleDiskCache.class.getDeclaredField("writer");writer.setAccessible(true);((ExecutorService)writer.get(null)).submit(()->{}).get(5,TimeUnit.SECONDS);CountDownLatch done=new CountDownLatch(1);Document[] restored={null};
            ui(()->{repo.releaseArticleMemory();repo.restoreArticle(item,value->{restored[0]=value;done.countDown();});});
            check(done.await(5,TimeUnit.SECONDS)&&restored[0]!=null&&restored[0].discussionPage==3&&restored[0].filteredVideos==1&&restored[0].filteredSectionIds.contains("hupu-reply-11")&&restored[0].sections.size()==3,"actual disk restore retains filtered identity, cursor and valid discussion content");
        }finally{ui(()->{if(stream[0]!=null)stream[0].close();repo.close();});}
    }
    private void redirectPageGuards()throws Exception{
        String database="test-ui-"+UUID.randomUUID()+".db";Activity owner=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        try{ui(()->{
            for(Source source:List.of(Source.V2EX,Source.TIEBA,Source.HUPU)){
                String base=source==Source.V2EX?"https://www.v2ex.com/t/123":source==Source.TIEBA?"https://tieba.baidu.com/p/123":"https://bbs.hupu.com/123.html",url=targetPage(source,base,2);
                DynamicReader dynamic=new DynamicReader(owner,new Item(source,"页码保护",url,""),new Repository.Result<>(){public void success(Document value){}public void failure(String reason){}},true);
                try{Method expected=DynamicReader.class.getDeclaredMethod("expectedSource",String.class);expected.setAccessible(true);
                    check((boolean)expected.invoke(dynamic,url+"#reply")&&!(boolean)expected.invoke(dynamic,base)&&!(boolean)expected.invoke(dynamic,url.replace("123","456")),source+": browser continuation accepts only the same post and requested page; redirect to page one is rejected");
                }catch(Exception error){throw new IllegalStateException(error);}finally{dynamic.close();}
            }
        });}finally{ui(owner::finish);test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
    }
    private void zhihuFastAppend()throws Exception{
        Item item=new Item(Source.ZHIHU,"知乎快速续读合成问题","https://www.zhihu.com/question/"+System.currentTimeMillis(),"");Document first=new Document();first.url=item.url;first.title=item.title;Section main=new Section("question-main","问题补充",false);for(int n=0;n<45;n++)main.blocks.add(new Block("text","原有问题补充段落，快速续读时应保持屏幕位置。第"+n+"段。"));first.sections.add(main);first.blocks.addAll(main.blocks);ArticleVersion.capture(item,first);
        ui(()->{Repository repo=new Repository(test.getTargetContext());repo.cacheArticle(first);repo.close();});MainActivity owner=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()));
        try{
            await(()->field(owner,"readerWeb")!=null&&field(owner,"reading")==first);dom(owner);
            // This probe stages an immediate callback; automatic trigger/request tests are separate.
            ui(()->{set(owner,"morePaused",true);WebView web=(WebView)field(owner,"readerWeb");web.scrollTo(0,Math.max(0,Math.round(web.getContentHeight()*web.getScale())-web.getHeight()));});Thread.sleep(100);
            org.json.JSONObject anchor=new org.json.JSONObject(js(owner,"(()=>{for(const a of document.querySelectorAll('[data-reading-anchor]')){const r=a.getBoundingClientRect();if(r.bottom>0&&r.top<innerHeight)return {id:a.id,top:r.top}}return null})()"));
            StringBuilder expanded=new StringBuilder("<div class=QuestionRichText>");for(Block block:main.blocks)expanded.append("<p>").append(block.value).append("</p>");expanded.append("<p>展开后才取得的补充尾部标记</p></div><div class=AnswerItem data-zop='{\"itemId\":9876}'><div class=RichContent-inner><div class=RichText>追加的真实结构合成回答</div></div></div>");
            Document after=AnswerStream.copy(first);AnswerStream.merge(after,SourceParser.article(Source.ZHIHU,expanded.toString(),item.url));after.moreStatus="loaded";
            ui(()->{try{set(owner,"loadingMore",true);Method loading=MainActivity.class.getDeclaredMethod("showContinuationLoading");loading.setAccessible(true);loading.invoke(owner);Method append=MainActivity.class.getDeclaredMethod("appendAtReadingPosition",Document.class,int.class,Set.class);append.setAccessible(true);append.invoke(owner,after,(int)field(owner,"generation"),Collections.emptySet());}catch(Exception error){throw new IllegalStateException(error);}});
            await(()->field(owner,"reading")==after);dom(owner);long end=SystemClock.elapsedRealtime()+6000;double top=Double.NaN;
            while(SystemClock.elapsedRealtime()<end){try{top=Double.parseDouble(js(owner,"document.getElementById("+org.json.JSONObject.quote(anchor.getString("id"))+").getBoundingClientRect().top"));}catch(Exception ignored){}if(Math.abs(top-anchor.getDouble("top"))<3)break;Thread.sleep(50);}
            check(Math.abs(top-anchor.getDouble("top"))<3,"Zhihu fast answer append preserves same visible paragraph through shared anchor code");
            check(js(owner,"document.querySelectorAll('section[data-section-label=\"问题补充\"]').length===1&&!!document.getElementById("+org.json.JSONObject.quote(ReaderHtml.anchorId(main.id,0))+")&&document.body.textContent.includes('展开后才取得的补充尾部标记')").equals("true"),"Zhihu expanded supplement replaces old cached preview once and preserves its original DOM anchor");
            ui(()->check(((Document)field(owner,"reading")).sections.size()==2&&((Document)field(owner,"reading")).metadataHash.equals(first.metadataHash)&&field(owner,"answers")==null,"Zhihu append retains original provenance and creates no speculative source browser"));
        }finally{ui(owner::finish);test.waitForIdleSync();}
    }
    private void zhihuAnswerBudget()throws Exception{
        Item item=new Item(Source.ZHIHU,"连续回答预算合成问题","https://www.zhihu.com/question/"+System.currentTimeMillis(),"");
        Document previous=new Document();previous.title=item.title;previous.url=item.url;
        for(int i=0;i<599;i++){Section section=new Section("answer-"+i,"回答",true);section.blocks.add(new Block("text","已读内容"+i));previous.sections.add(section);previous.blocks.addAll(section.blocks);}
        ArticleVersion.capture(item,previous);Document limited=AnswerStream.copy(previous),last=new Document();Section section=new Section("answer-599","回答",true);section.blocks.add(new Block("text","最后可保留的回答"));last.sections.add(section);last.blocks.addAll(section.blocks);AnswerStream.mergeWithinBudget(limited,last);
        ui(()->{Repository repo=new Repository(test.getTargetContext());repo.cacheArticle(limited);repo.close();});
        MainActivity owner=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()));
        AnswerStream[] stream={null};WebView[] source={null};
        try{
            await(()->field(owner,"readerWeb")!=null&&field(owner,"reading")==limited);dom(owner);
            check(js(owner,"document.querySelectorAll('a[href=\""+ReaderHtml.ACTION+"login\"]').length===1&&!document.querySelector('a[href=\""+ReaderHtml.ACTION+"more\"]')").equals("true"),"Zhihu budget: actual reader shows one source action and no continuation");
            ui(()->{try{Method more=MainActivity.class.getDeclaredMethod("loadMore");more.setAccessible(true);more.invoke(owner);check(field(owner,"answers")==null&&field(owner,"reading")==limited&&!((Boolean)field(owner,"loadingMore")),"Zhihu budget: direct continuation cannot create a source or replace the cached reading");}catch(Exception error){throw new IllegalStateException(error);}});
            CountDownLatch pageReady=new CountDownLatch(1),done=new CountDownLatch(1);Document[] result={null};int[] callbacks={0};
            ui(()->{
                source[0]=new WebView(owner);SourceSession.configure(owner,Source.ZHIHU,source[0]);source[0].getSettings().setJavaScriptEnabled(true);
                String page="<html><body><div class=AnswerItem data-zop='{\"itemId\":900001}'><div class=RichContent-inner><div class=RichText>第一条超量回答</div></div></div><div class=AnswerItem data-zop='{\"itemId\":900002}'><div class=RichContent-inner><div class=RichText>第二条超量回答</div></div></div></body></html>";
                source[0].setWebViewClient(new WebViewClient(){@Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest request){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(page.getBytes(StandardCharsets.UTF_8)));}@Override public void onPageFinished(WebView w,String url){if(url.equals(item.url))pageReady.countDown();}});
                source[0].loadUrl(item.url);
            });
            if(!pageReady.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Budget source fixture did not load");
            ui(()->{stream[0]=new AnswerStream(owner,item,source[0]);stream[0].more(previous,new Repository.Result<>(){public void success(Document doc){result[0]=doc;callbacks[0]++;done.countDown();}public void failure(String why){callbacks[0]+=100;done.countDown();}});});
            check(done.await(8,TimeUnit.SECONDS)&&result[0]!=null&&result[0].moreStatus.equals("limit")&&result[0].sections.size()==599,"Zhihu budget: actual source snapshot rejects the entire overflowing batch and keeps prior answers");
            ui(()->report.append("DIAGNOSTIC budget callbacks=").append(callbacks[0]).append(" result=").append(result[0]==null?"null":result[0].moreStatus+"/"+result[0].sections.size()+"/"+result[0].notice).append(" closed=").append(field(stream[0],"closed")).append(" attempts=").append(field(stream[0],"attempts")).append(" source=").append(source[0].getUrl()).append('\n'));
            Thread.sleep(400);
            ui(()->{
                check(callbacks[0]==1&&(boolean)field(stream[0],"closed")&&((ExecutorService)field(stream[0],"parser")).isShutdown(),"Zhihu budget: one callback closes source and parser at the boundary");
                check(field(stream[0],"nextApi").equals("")&&(int)field(stream[0],"apiPages")==0&&result[0]!=null&&result[0].metadataHash.equals(previous.metadataHash),"Zhihu budget: rejected source batch does not request API pages or change acquisition metadata");
                stream[0].more(previous,new Repository.Result<>(){public void success(Document ignored){callbacks[0]++;}public void failure(String why){callbacks[0]++;}});
                check(callbacks[0]==1,"Zhihu budget: closed source rejects repeated continuation");
            });
        }finally{ui(()->{if(stream[0]!=null)stream[0].close();else if(source[0]!=null)source[0].destroy();owner.finish();});test.waitForIdleSync();}
    }
    private void persist(MainActivity owner,Item item)throws Exception{
        Document[] expected={null},restored={null};CountDownLatch done=new CountDownLatch(1);
        ui(()->expected[0]=(Document)field(owner,"reading"));Field f=ArticleDiskCache.class.getDeclaredField("writer");f.setAccessible(true);((ExecutorService)f.get(null)).submit(()->{}).get(5,TimeUnit.SECONDS);
        ui(()->{Repository repo=(Repository)field(owner,"repo");repo.releaseArticleMemory();repo.restoreArticle(item,doc->{restored[0]=doc;done.countDown();});});
        check(done.await(5,TimeUnit.SECONDS)&&restored[0]!=null&&restored[0].discussionPage==3&&restored[0].sections.size()==4&&restored[0].metadataHash.equals(expected[0].metadataHash),item.source+": real disk restore keeps appended replies, cursor and original metadata");
    }
    private void defaultAcquisition(MainActivity owner,Item item,Document first)throws Exception{
        String key=item.source.name()+"-web";
        android.content.SharedPreferences prefs=owner.getSharedPreferences("source-session",0);boolean existed=prefs.contains(key),previous=prefs.getBoolean(key,false);DiscussionStream[] stream={null};Document[] result={null};String[] error={""};CountDownLatch done=new CountDownLatch(1);
        try{
            ui(()->{
                SourceSession.preferWeb(owner,item.source);stream[0]=new DiscussionStream(owner,item);stream[0].more(first,new Repository.Result<>(){public void success(Document doc){result[0]=doc;done.countDown();}public void failure(String why){error[0]=why;done.countDown();}});
                DynamicReader dynamic=(DynamicReader)field(field(stream[0],"loader"),"dynamic");WebView web=(WebView)field(dynamic,"web");WebViewClient delegate=web.getWebViewClient();
                web.setWebViewClient(new WebViewClient(){
                    @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html(item.source,2,targetPage(item.source,item.url,3)).getBytes(StandardCharsets.UTF_8)));}
                    @Override public void onPageCommitVisible(WebView v,String u){delegate.onPageCommitVisible(v,u);}
                    @Override public void onPageFinished(WebView v,String u){delegate.onPageFinished(v,u);}
                });
            });
            check(done.await(12,TimeUnit.SECONDS)&&result[0]!=null&&result[0].discussionPage==2,"default production WebView loader extracts and merges controlled source next page: "+error[0]);
            ui(()->check(stream[0]!=null&&!stream[0].busy(),"default source request releases stream ownership after completion"));
        }finally{ui(()->{if(stream[0]!=null)stream[0].close();android.content.SharedPreferences.Editor edit=prefs.edit();if(existed)edit.putBoolean(key,previous);else edit.remove(key);edit.apply();});}
    }
    private void rejectedAcquisitions(){ui(()->{
        String url="https://qr-comments-"+System.nanoTime()+".v2ex.com/t/123";Item item=new Item(Source.V2EX,"合成讨论",url,"");Repository repo=new Repository(test.getTargetContext());
        try{for(String reason:List.of("cookie","namespace","wrong-page","close")){
            Document before=SourceParser.article(Source.V2EX,html(Source.V2EX,1,"?p=2"),url);ArticleVersion.capture(item,before);repo.cacheArticle(before);Controlled loader=new Controlled();DiscussionStream stream=new DiscussionStream(test.getTargetContext(),item,loader);int[] callbacks={0};
            stream.more(before,new Repository.Result<>(){public void success(Document doc){callbacks[0]+=100;}public void failure(String why){callbacks[0]++;}});
            Document page=SourceParser.article(Source.V2EX,html(Source.V2EX,2,"?p=3"),loader.item.url);new ArticleSession(test.getTargetContext(),loader.item).stamp(page);
            if(reason.equals("cookie"))CookieManager.getInstance().setCookie(url,"qr_comment_fixture=changed; Path=/; Secure");if(reason.equals("namespace"))repo.invalidateArticles();if(reason.equals("wrong-page"))page.url=url.replace("/123","/456")+"?p=2";if(reason.equals("close"))stream.close();
            loader.callback.success(page);check(callbacks[0]==(reason.equals("close")?0:1)&&before.discussionPage==1&&before.sections.size()==2,"discussion rejects "+reason+" result without altering original body");stream.close();
        }}finally{CookieManager.getInstance().setCookie(url,"qr_comment_fixture=; Max-Age=0; Path=/; Secure");repo.close();}
    });}
    private void loadedTiebaNestedUi()throws Exception{
        String url="https://tieba.baidu.com/p/"+System.currentTimeMillis();Item item=new Item(Source.TIEBA,"已加载楼中楼受控讨论",url,"");
        String nested="<li class=lzl_single_post data-field='{\"spid\":101}'><a class=j_user_card>子回复甲</a><span class=lzl_content_main>独立子回复文字</span></li>";
        String html="<h1>已加载楼中楼受控讨论</h1><div class=pc-pb-box><div class=l_post><div class=d_post_content id=post_content_1>主帖文字"+nested+"</div></div><div class=l_post><a class=d_name>外层乙</a><div class=d_post_content id=post_content_2>外层回复</div></div><div class=pc-pb-reply-list><div class=pb-comment-item><a class=head-name>现代丙</a><div class=comment-content><div class=pb-rich-text>现代回复</div></div></div></div></div><a rel=next href='?pn=2'>下一页</a>";
        CountDownLatch done=new CountDownLatch(1);Document[] extracted={null};Throwable[] error={null};WebView[] origin={null};boolean[] exact={false};
        ui(()->{origin[0]=new WebView(test.getTargetContext());origin[0].getSettings().setJavaScriptEnabled(true);origin[0].setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream((request.getUrl().toString().equals(url)?html:"").getBytes(StandardCharsets.UTF_8)));}
            @Override public void onPageFinished(WebView view,String actual){if(!url.equals(actual)||done.getCount()==0)return;exact[0]=url.equals(view.getUrl());view.evaluateJavascript("document.documentElement.outerHTML",value->{try{extracted[0]=SourceParser.readingArticle(Source.TIEBA,(String)new org.json.JSONTokener(value).nextValue(),actual);}catch(Throwable failure){error[0]=failure;}finally{done.countDown();}});}
        });origin[0].loadUrl(url);});
        try{if(!done.await(8,TimeUnit.SECONDS)||error[0]!=null||extracted[0]==null)throw new IllegalStateException("Controlled Tieba source snapshot failed",error[0]);}
        finally{ui(()->origin[0].destroy());}
        Document first=extracted[0];ArticleVersion.capture(item,first);
        check(exact[0]&&first.sections.size()==4,"TIEBA: actual source WebView snapshot retains desktop nested and mixed modern replies");
        check(first.byline.isEmpty()&&first.sections.get(0).label.equals("正文")&&first.sections.get(1).label.equals("回复 · 子回复甲"),"TIEBA: missing main author cannot borrow the nested reply author");
        check(first.sections.get(0).blocks.stream().noneMatch(block->block.value.contains("独立子回复文字")),"TIEBA: nested text is not duplicated inside the main body");
        ui(()->{Repository repository=new Repository(test.getTargetContext());repository.cacheArticle(first);repository.close();});
        MainActivity reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()));
        try{
            await(()->field(reader,"readerWeb")!=null&&field(reader,"reading")!=null);dom(reader);
            check(js(reader,"document.querySelectorAll('.part').length===4&&document.querySelectorAll('.discussion').length===3&&document.querySelector('.part').getAttribute('data-section-label')==='正文'").equals("true"),"TIEBA: common reader separates all loaded replies and keeps unknown main attribution");
            Controlled loader=new Controlled();ui(()->set(reader,"discussions",new DiscussionStream(reader,item,loader)));click(reader);await(()->loader.loads==1);
            String next="<div id=j_p_postlist><div class=l_post><div class=d_post_content id=post_content_2>外层回复</div>"+nested+"<li class=lzl_single_post data-field='{\"spid\":102}'><a class=j_user_card>新回复丁</a><span class=lzl_content_main>后续子回复</span></li></div></div>";
            Document incoming=SourceParser.article(Source.TIEBA,next,loader.item.url);ui(()->{new ArticleSession(reader,loader.item).stamp(incoming);loader.callback.success(incoming);});await(()->((Document)field(reader,"reading")).discussionPage==2);dom(reader);
            ui(()->{Document merged=(Document)field(reader,"reading");check(merged.sections.size()==5&&merged.sections.get(4).label.equals("回复 · 新回复丁")&&merged.sections.get(1).id.equals(first.sections.get(1).id),"TIEBA: actual continuation tap deduplicates stable sub-post IDs while retaining old reply identity");});
        }finally{ui(reader::finish);test.waitForIdleSync();}
    }
    private void emptyTiebaFloorUi()throws Exception{
        String url="https://tieba.baidu.com/p/"+System.currentTimeMillis();Item item=new Item(Source.TIEBA,"空首楼边界",url,"");
        String html="<h1>空首楼边界</h1><div id=j_p_postlist><div class=l_post><a class=d_name>楼主甲</a><div class=d_post_content id=post_content_1><video src='/clip.mp4'></video></div></div><div class=l_post><a class=d_name>读者乙</a><div class=d_post_content id=post_content_2>应当放在讨论区的文字回复</div></div></div>";
        Document doc=SourceParser.readingArticle(Source.TIEBA,html,url);ArticleVersion.capture(item,doc);
        ui(()->{Repository repo=new Repository(test.getTargetContext());repo.cacheArticle(doc);repo.close();});
        MainActivity reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()));
        try{
            await(()->field(reader,"readerWeb")!=null&&field(reader,"reading")!=null);dom(reader);
            check(js(reader,"(()=>{const d=document.querySelector('.discussion'),a=d&&d.querySelector('.section-label');return document.querySelectorAll('.part').length===1&&document.querySelectorAll('.discussion').length===1&&d.getAttribute('data-section-label')==='回复 · 读者乙'&&a&&a.textContent==='读者乙'&&d.textContent.includes('应当放在讨论区的文字回复')})()").equals("true"),"TIEBA: empty video first floor leaves text in the discussion region with the compact correct author");
            ui(()->{Document displayed=(Document)field(reader,"reading");check(displayed.byline.equals("楼主甲")&&displayed.unsupportedVideo&&field(reader,"dynamic")==null,"TIEBA: cached reader retains main author and video fallback without reloading source");});
        }finally{ui(reader::finish);test.waitForIdleSync();}
    }
}
