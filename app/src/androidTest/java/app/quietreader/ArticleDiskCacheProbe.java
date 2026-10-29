package app.quietreader;
import android.app.Instrumentation;
import android.content.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import static app.quietreader.Models.*;

final class ArticleDiskCacheProbe {
    static void run(Instrumentation test,BiConsumer<Boolean,String> check)throws Exception{
        String name="test-article-disk-"+UUID.randomUUID();File isolated=new File(test.getTargetContext().getCacheDir(),name);
        Context context=new ContextWrapper(test.getTargetContext()){
            @Override public Context getApplicationContext(){return this;}
            @Override public File getCacheDir(){return isolated;}
            @Override public SharedPreferences getSharedPreferences(String key,int mode){return super.getSharedPreferences(name+key,mode);}
        };
        try{
            long now=System.currentTimeMillis();ArticleDiskCache disk=new ArticleDiskCache(context,130_000);String namespace=disk.namespace();
            Item item=new Item(Source.ZHIHU,"磁盘缓存测试","https://www.zhihu.com/question/99009900","");Document doc=new Document();doc.url=item.url;doc.title=item.title;doc.notice="来源提示";doc.moreStatus="paused";doc.filteredSectionIds.add("video-answer");
            Section answer=new Section("answer-1","回答",true);answer.continuationUrl=item.url+"?page=2";answer.blocks.add(new Block("text","已经提前缓存的正文。"));doc.sections.add(answer);doc.blocks.addAll(answer.blocks);doc.related.add(item);
            doc.unsupportedVideo=true;doc.videoUrl="https://video.zhimg.com/one.mp4";doc.videoPoster="https://pic.zhimg.com/one.jpg";
            doc.videos.add(new VideoAttachment(doc.videoUrl,doc.videoPoster));doc.videos.add(new VideoAttachment("https://video.zhimg.com/two.mp4","https://pic.zhimg.com/two.jpg"));
            ArticleVersion.capture(item,doc);String session=ArticleDiskCache.session(item.url);disk.write(doc,namespace,session,now);
            ArticleDiskCache.Hit hit=new ArticleDiskCache(context).read(item.url,namespace,session,false,now+1);
            check.accept(hit!=null&&hit.document.sections.get(0).continuationUrl.equals(answer.continuationUrl)&&hit.document.related.size()==1&&hit.document.filteredSectionIds.contains("video-answer"),"disk reopen preserves sections continuation related entries and filtered IDs");
            check.accept(hit!=null&&hit.document.videos.size()==2&&hit.document.videos.get(1).url.equals(doc.videos.get(1).url)&&hit.document.containsImage(doc.videos.get(1).poster),"disk reopen preserves each attachment and authorized poster");
            check.accept(hit!=null&&ArticleVersion.matches(item,hit.document),"disk reopen preserves acquisition metadata version");
            check.accept(disk.read(item.url,namespace,"other-session",false,now)==null,"different cookie fingerprint cannot read earlier session body");
            check.accept(disk.read(item.url,namespace,session,false,now+11*60_000)==null&&disk.read(item.url,namespace,session,true,now+11*60_000)!=null,"online freshness expires while bounded offline snapshot remains available");
            Repository[] repo={null};CountDownLatch restored=new CountDownLatch(1);boolean[] loaded={false};
            test.runOnMainSync(()->{repo[0]=new Repository(context);repo[0].releaseArticleMemory();repo[0].restoreArticle(item,value->{loaded[0]=value!=null&&repo[0].cachedArticle(item)!=null;restored.countDown();});});
            check.accept(restored.await(5,TimeUnit.SECONDS)&&loaded[0],"repository restores body from disk after memory eviction without network");test.runOnMainSync(()->repo[0].close());
            test.runOnMainSync(()->{
                Repository cache=new Repository(context);ReadingHistory history=new ReadingHistory(context);
                Document body=new Document();body.url="https://example.org/cache-session-test";body.title="会话测试";body.blocks.add(new Block("text","已缓存正文"));
                Document changing=new Document();changing.url="https://example.org/mutable-memory";changing.title="可增长正文";changing.blocks.add(new Block("text","短正文"));
                cache.cacheArticle(changing);changing.blocks.add(new Block("text",String.join("",Collections.nCopies(100_000,"字"))));
                Item changingItem=new Item(Source.ZHIHU,changing.title,changing.url,"");
                check.accept(cache.cachedArticle(changingItem)==changing,"mutated cached document is remeasured without corrupting LRU accounting");
                changing.blocks.add(new Block("text","继续加载的回答"));cache.cacheArticle(changing);cache.releaseArticleMemory();
                check.accept(cache.cachedArticle(changingItem)==null,"recaching grown body and clearing memory completes without negative LRU size");
                cache.cacheArticle(body);long fetched=body.cachedAt;
                history.save(body.url,body,Collections.singleton("answer"),123);
                org.json.JSONObject state=history.get(body.url);
                check.accept(state!=null&&state.has("document")&&state.optInt("position")==123,"history keeps fresh body and reading marker");
                try{
                    Document reopened=ReadingHistory.restore(state);cache.cacheArticle(reopened);history.save(body.url,reopened,Collections.emptySet(),124);
                    check.accept(history.get(body.url).optLong("bodyAt")==fetched,"reopening history does not renew body freshness");
                    body.cachedAt=System.currentTimeMillis()-ArticleDiskCache.MAX_AGE-1000;history.save(body.url,body,Collections.emptySet(),125);
                    check.accept(!history.get(body.url).has("document")&&history.get(body.url).optInt("position")==125,"expired history preserves position without stale body");
                    body.cachedAt=fetched;body.cachedSession="obsolete-session";history.save(body.url,body,Collections.emptySet(),126);
                    check.accept(!history.get(body.url).has("document")&&!cache.cacheArticle(body,Repository.articleCacheEpoch()),"old document cannot be relabeled with current login session");
                    body.cachedSession=ArticleDiskCache.session(body.url);history.save(body.url,body,Collections.emptySet(),127);cache.invalidateArticles();
                    check.accept(!history.get(body.url).has("document")&&!cache.cacheArticle(body,Repository.articleCacheEpoch()),"invalidated displayed body cannot repopulate current namespace");
                }catch(Exception error){throw new RuntimeException(error);}finally{cache.close();}
            });
            test.runOnMainSync(()->{
                ReadingHistory history=new ReadingHistory(context);Document large=new Document();large.url="https://example.org/large-reading-marker";
                large.title="超长正文";large.blocks.add(new Block("text",String.join("",Collections.nCopies(400_000,"中"))));
                new ArticleSession(context,large.url).stamp(large);history.save(large.url,large,Collections.singleton("long-answer"),456);
                try{
                    org.json.JSONObject state=new org.json.JSONObject(context.getSharedPreferences("reading-history",0).getString(large.url,"{}"));
                    check.accept(!state.has("document")&&state.optInt("position")==456&&state.getJSONArray("expanded").getString(0).equals("long-answer"),"oversized body retains position and expansion without a history body copy");
                    large.blocks.clear();large.blocks.add(new Block("text","更新后的短正文"));history.save(large.url,large,Collections.emptySet(),457);
                    check.accept(history.get(large.url).has("document")&&history.get(large.url).optInt("position")==457,"same reading entry can cache a later small body normally");
                }catch(Exception ex){throw new RuntimeException(ex);}
            });
            disk.invalidate();check.accept(disk.read(item.url,namespace,session,true,now)==null,"namespace invalidation rejects previous disk body");
            disk.write(doc,namespace,session,now);check.accept(disk.read(item.url,disk.namespace(),session,true,now)==null,"late old namespace write cannot populate current disk cache");
            namespace=disk.namespace();String payload=new String(new char[50_000]).replace('\0','x');
            for(int n=0;n<5;n++){Document large=new Document();large.url=item.url+"?n="+n;large.title="测试";large.blocks.add(new Block("text",payload));disk.write(large,namespace,session,now+n);}
            long bytes=0;File folder=new File(isolated,"article-bodies-v1");File[] files=folder.listFiles();if(files!=null)for(File file:files)bytes+=file.length();
            check.accept(bytes<=130_000&&disk.read(item.url+"?n=4",namespace,session,false,now+10)!=null&&disk.read(item.url+"?n=0",namespace,session,false,now+10)==null,"disk cache prunes old bodies to configured byte budget");
            String key=app.quietreader.discovery.HotCandidate.hash(namespace+"\n"+session+"\n"+item.url)+".json";
            File damaged=new File(folder,key);
            try(FileOutputStream out=new FileOutputStream(damaged)){out.write("{\"at\":".getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            check.accept(disk.read(item.url,namespace,session,false,now)==null&&!damaged.exists(),"truncated body is discarded as a recoverable cache miss");
            disk.write(doc,namespace,session,now);
            check.accept(disk.read(item.url,namespace,session,false,now)!=null,"a damaged cache entry can be populated again");
            disk.write(doc,namespace,session,now+60_000);
            check.accept(disk.read(item.url,namespace,session,false,now)==null&&!damaged.exists(),"future timestamp is rejected and removed");
            File abandoned=new File(folder,"body-abandoned.partial"),recent=new File(folder,"body-recent.partial");
            try(FileOutputStream out=new FileOutputStream(abandoned)){out.write(1);}
            try(FileOutputStream out=new FileOutputStream(recent)){out.write(1);}
            abandoned.setLastModified(now-2*60*60_000);
            File expired=new File(folder,"expired-peer.json");try(FileOutputStream out=new FileOutputStream(expired)){out.write(1);}expired.setLastModified(now-ArticleDiskCache.MAX_AGE-60_000);
            disk.write(doc,namespace,session,now);
            check.accept(!abandoned.exists()&&recent.exists(),"write recovers abandoned temporary files without deleting a recent writer");
            check.accept(!expired.exists(),"single reservation inventory removes an expired peer before admitting fresh content");
            // A fresh partial belongs to a potentially live writer and must consume budget.
            try(RandomAccessFile partialFile=new RandomAccessFile(recent,"rw")){partialFile.setLength(130_000);}
            String blockedUrl=doc.url+"?reserved-test";Document blocked=new Document();blocked.url=blockedUrl;blocked.title="预算准入";blocked.blocks.add(new Block("text","不能越过磁盘上限写入"));
            disk.write(blocked,namespace,session,now);
            check.accept(disk.read(blockedUrl,namespace,session,false,now)==null&&recent.exists(),"fresh temporary bytes prevent new cache admission without deleting the pending writer");
            if(!recent.delete())throw new IOException("Could not remove test partial");
            disk.write(blocked,namespace,session,now);
            check.accept(disk.read(blockedUrl,namespace,session,false,now)!=null,"disk cache resumes writes when temporary reservation is released");
            ArticleDiskCache tiny=new ArticleDiskCache(context,1);tiny.write(blocked,namespace,session,now);
            check.accept(disk.read(blockedUrl,namespace,session,false,now)!=null,"oversized replacement leaves the previous valid cached body intact");
            cookieSession(test,context,check);
            String retired=disk.namespace();disk.write(doc,retired,session,now);disk.invalidate();
            check.accept(disk.clearFiles()&&folder.listFiles()!=null&&folder.listFiles().length==0,"explicit body cleanup removes disk and partial files");
            disk.write(doc,retired,session,now);
            check.accept(folder.listFiles().length==0,"old namespace write cannot recreate files after explicit cleanup");
            quotaReplacement(context,now,check);
            tiebaNestedIdentity(test,context,check);
            hupuBodyRevision(test,context,check,0);
            hupuBodyRevision(test,context,check,1);
            hupuBodyRevision(test,context,check,3);
            ReadingHistory history=new ReadingHistory(context);
            test.runOnMainSync(()->{new ArticleSession(context,doc.url).stamp(doc);history.save(doc.url,doc,Collections.singleton("answer-1"),321);});
            check.accept(history.clearBodies(),"history body cleanup commits successfully");
            org.json.JSONObject marker=history.get(doc.url);
            org.json.JSONObject raw=new org.json.JSONObject(context.getSharedPreferences("reading-history",0).getString(doc.url,"{}"));
            check.accept(history.seen(doc.url)&&marker.optInt("position")==321&&marker.getJSONArray("expanded").getString(0).equals("answer-1")&&!raw.has("document"),"body cleanup physically removes history snapshot but preserves reading marker");
        }finally{delete(isolated,isolated);test.getTargetContext().deleteSharedPreferences(name+"article-cache");test.getTargetContext().deleteSharedPreferences(name+"reading-history");}
    }
    private static void cookieSession(Instrumentation test,Context context,BiConsumer<Boolean,String> check)throws Exception{
        String url="https://example.org/cache-session-test",cookie="qr_probe_"+UUID.randomUUID().toString().replace("-","");
        ArticleSession[] ticket={null};Repository[] cache={null};ReadingHistory[] history={null};
        Document document=new Document();document.url=url;document.title="会话隔离";document.blocks.add(new Block("text","测试正文"));
        Item item=new Item(Source.ZHIHU,document.title,url,"");
        try{
            setCookie(test,url,cookie+"=first; Path=/; Secure");
            test.runOnMainSync(()->{
                ticket[0]=new ArticleSession(context,url);cache[0]=new Repository(context);history[0]=new ReadingHistory(context);
                check.accept(ticket[0].stamp(document),"unchanged WebView cookie session accepts a completed document");
                cache[0].cacheArticle(document);history[0].save(url,document,Collections.emptySet(),222);
                check.accept(cache[0].cachedArticle(item)!=null&&history[0].get(url).has("document"),"same cookie session reuses memory and reading history");
                document.cachedAt-=60_000;
                Document continuation=AnswerStream.copy(document);
                cache[0].cacheArticle(continuation);
                check.accept(cache[0].cachedArticle(item)==continuation&&continuation.cachedAt==document.cachedAt,"continuation retains original body freshness instead of renewing old answers");
            });
            setCookie(test,url,cookie+"=second; Path=/; Secure");
            test.runOnMainSync(()->{
                check.accept(!ticket[0].stamp(document),"cookie change during extraction rejects the earlier document");
                check.accept(cache[0].cachedArticle(item)==null&&!history[0].get(url).has("document")&&history[0].get(url).optInt("position")==222,"actual cookie change rejects cached bodies but preserves reading position");
                Document continuation=AnswerStream.copy(document);
                check.accept(!cache[0].cacheArticle(continuation,Repository.articleCacheEpoch())&&cache[0].cachedArticle(item)==null,"continuation cannot relabel old-session answers with the current cookie session");
                ArticleSession fresh=new ArticleSession(context,url);Document next=new Document();next.url=url;
                check.accept(fresh.stamp(next),"a new extraction can use the changed cookie session");
                cache[0].invalidateArticles();check.accept(!fresh.stamp(next),"cache invalidation during extraction rejects late completion");
            });
        }finally{
            test.runOnMainSync(()->{if(cache[0]!=null){cache[0].releaseArticleMemory();cache[0].close();}});
            setCookie(test,url,cookie+"=; Max-Age=0; Path=/; Secure");
        }
    }
    private static Document tiebaWindow(String floor){
        String html="<div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>主帖</div></div><div class=pc-pb-reply-list><div class=pb-comment-list><div class=pb-comment-item data-id='"+floor+"'><a class=user-name>楼层作者</a><div class=comment-content><div class=pb-rich-text>楼层正文</div></div><div class=pb-lzl-item><a class=head-name>楼内作者</a><div class=comment-content><div class=pb-rich-text>相同楼内回复</div></div></div></div></div></div></div>";
        return SourceParser.article(Source.TIEBA,html,"https://tieba.baidu.com/p/990099001");
    }
    private static void tiebaNestedIdentity(Instrumentation test,Context context,BiConsumer<Boolean,String> check)throws Exception{
        ArticleDiskCache disk=new ArticleDiskCache(context);Document first=tiebaWindow("11"),second=tiebaWindow("12");
        Document merged=TiebaReplies.merge(first,second);
        check.accept(merged.sections.size()==5&&!first.sections.get(2).id.equals(second.sections.get(2).id),"Android virtual windows retain identical ID-less children in distinct source floors");
        check.accept(merged.sections.get(2).parentSectionId.equals(merged.sections.get(1).id)&&merged.sections.get(4).parentSectionId.equals(merged.sections.get(3).id),"Android merged nested replies retain their exact DOM parents");
        test.runOnMainSync(()->{if(!new ArticleSession(context,merged.url).stamp(merged))throw new IllegalStateException("Tieba fixture stamp failed");});
        disk.write(merged,merged.cachedNamespace,merged.cachedSession,merged.cachedAt);
        ArticleDiskCache.Hit hit=disk.read(merged.url,merged.cachedNamespace,merged.cachedSession,false,System.currentTimeMillis());
        check.accept(hit!=null&&DocumentFingerprint.of(merged).equals(DocumentFingerprint.of(hit.document)),"disk round trip preserves new Tieba relationship revision identities and parent metadata");
        check.accept(hit!=null&&TiebaReplies.merge(hit.document,second).sections.size()==5,"replaying a virtual window after actual disk restore cannot duplicate nested replies");
        ReadingHistory history=new ReadingHistory(context);
        test.runOnMainSync(()->history.save(merged.url,merged,Set.of(merged.sections.get(1).id),177));
        org.json.JSONObject state=history.get(merged.url);
        check.accept(state!=null&&state.has("document")&&DocumentFingerprint.of(merged).equals(DocumentFingerprint.of(ReadingHistory.restore(state))),"history restores all parent relations for current Tieba snapshots");
        Document old=AnswerStream.copy(merged);old.tiebaReplyRevision=1;
        disk.write(old,old.cachedNamespace,old.cachedSession,old.cachedAt);
        check.accept(disk.read(old.url,old.cachedNamespace,old.cachedSession,false,System.currentTimeMillis())==null,"online disk rejects the older parent-unscoped Tieba revision");
        ArticleDiskCache.Hit offline=disk.read(old.url,old.cachedNamespace,old.cachedSession,true,System.currentTimeMillis());
        check.accept(offline!=null&&offline.document.tiebaReplyRevision==1&&offline.document.sections.size()==5,"offline disk preserves older Tieba prose without laundering its revision");
        test.runOnMainSync(()->history.save(old.url,old,Set.of(old.sections.get(1).id),188));
        org.json.JSONObject legacyState=history.get(old.url);
        check.accept(legacyState!=null&&!legacyState.has("document")&&legacyState.optInt("position")==188&&legacyState.getJSONArray("expanded").length()==1,"online history rejects old Tieba body while retaining position and expanded floor");
        org.json.JSONObject raw=new org.json.JSONObject(context.getSharedPreferences("reading-history",0).getString(old.url,"{}"));
        check.accept(raw.getJSONObject("document").getInt("tiebaReplyRevision")==1,"rejecting online history does not rewrite an old snapshot as newly parsed");
        check.accept(disk.clearFiles(),"Tieba nested identity fixture clears its isolated disk files");
    }
    private static void hupuBodyRevision(Instrumentation test,Context context,BiConsumer<Boolean,String> sink,int legacy)throws Exception{
        BiConsumer<Boolean,String> check=(good,label)->sink.accept(good,"Hupu legacy revision"+legacy+": "+label);
        Document fresh=new Document();fresh.url="https://bbs.hupu.com/99007773322.html";fresh.title="受控虎扑图片缓存";Section main=new Section("hupu-main-99007773322","主帖",false);main.blocks.add(new Block("image","https://i1.hoopchina.com.cn/test-only/image.png"));fresh.sections.add(main);fresh.blocks.addAll(main.blocks);
        test.runOnMainSync(()->{if(!new ArticleSession(context,fresh.url).stamp(fresh))throw new IllegalStateException("Hupu fixture stamp");});
        ArticleDiskCache disk=new ArticleDiskCache(context);disk.write(fresh,fresh.cachedNamespace,fresh.cachedSession,fresh.cachedAt);
        ArticleDiskCache.Hit current=disk.read(fresh.url,fresh.cachedNamespace,fresh.cachedSession,false,System.currentTimeMillis());
        check.accept(current!=null&&current.document.hupuBodyRevision==HupuDiscussion.BODY_REVISION&&DocumentFingerprint.of(current.document).equals(DocumentFingerprint.of(fresh)),"new Hupu media revision survives actual disk serialization");
        Document old=AnswerStream.copy(fresh);old.hupuBodyRevision=legacy;disk.write(old,old.cachedNamespace,old.cachedSession,old.cachedAt);
        check.accept(disk.read(old.url,old.cachedNamespace,old.cachedSession,false,System.currentTimeMillis())==null,"online disk refuses Hupu body predating the current parser revision");
        ArticleDiskCache.Hit offline=disk.read(old.url,old.cachedNamespace,old.cachedSession,true,System.currentTimeMillis());
        check.accept(offline!=null&&offline.document.hupuBodyRevision==legacy&&offline.document.containsImage(main.blocks.get(0).value),"offline disk preserves old Hupu media without upgrading its revision");
        ReadingHistory history=new ReadingHistory(context);test.runOnMainSync(()->history.save(old.url,old,Set.of(main.id),199));org.json.JSONObject saved=history.get(old.url);
        check.accept(saved!=null&&!saved.has("document")&&saved.optInt("position")==199&&saved.getJSONArray("expanded").length()==1,"online Hupu history refresh retains reading position and expanded section");
        org.json.JSONObject raw=new org.json.JSONObject(context.getSharedPreferences("reading-history",0).getString(old.url,"{}"));check.accept(raw.getJSONObject("document").getInt("hupuBodyRevision")==legacy,"history rejection never relabels an old Hupu snapshot as newly parsed");
        disk.clearFiles();disk.enqueue(old,old.cachedNamespace,old.cachedSession,old.cachedAt);
        ArticleDiskCache.Hit queued=null;long until=android.os.SystemClock.elapsedRealtime()+5000;
        while(queued==null&&android.os.SystemClock.elapsedRealtime()<until){queued=disk.read(old.url,old.cachedNamespace,old.cachedSession,true,System.currentTimeMillis());if(queued==null)Thread.sleep(20);}
        check.accept(queued!=null&&queued.document.hupuBodyRevision==legacy,"asynchronous body snapshot preserves old Hupu revision rather than initializing a new one");
        check.accept(disk.read(old.url,old.cachedNamespace,old.cachedSession,false,System.currentTimeMillis())==null,"old Hupu body remains online-ineligible after actual queued disk write");
        Repository repository=new Repository(context);try{test.runOnMainSync(()->{repository.cacheArticle(old);check.accept(repository.cachedArticle(new Item(Source.HUPU,old.title,old.url,""))==null,"online memory cache also refuses an old Hupu media revision");});}finally{test.runOnMainSync(()->{repository.releaseArticleMemory();repository.close();});}
        disk.invalidate(); // Retire any asynchronous test-only memory-cache write before cleanup.
        check.accept(disk.clearFiles(),"Hupu media fixture removes its isolated disk records");
    }
    private static void quotaReplacement(Context context,long now,BiConsumer<Boolean,String> check)throws Exception{
        ArticleDiskCache cache=new ArticleDiskCache(context);String namespace=cache.namespace(),session="quota-session";
        File root=new File(context.getCacheDir(),"article-bodies-v1");
        Document doc=new Document();doc.url="https://example.org/quota-replacement";doc.title="旧缓存";doc.blocks.add(new Block("text","可更新的正文"));
        cache.write(doc,namespace,session,now);
        if(root.listFiles()==null||root.listFiles().length!=1)throw new IOException("Quota fixture must start with one real record");
        for(int n=0;n<999;n++){
            File filler=new File(root,app.quietreader.discovery.HotCandidate.hash("quota-"+n)+".json");
            try(FileOutputStream out=new FileOutputStream(filler)){out.write(1);}filler.setLastModified(now);
        }
        doc.title="更新后的缓存";cache.write(doc,namespace,session,now+1);
        ArticleDiskCache.Hit hit=cache.read(doc.url,namespace,session,false,now+2);
        check.accept(root.listFiles().length==1000&&hit!=null&&"更新后的缓存".equals(hit.document.title),"replacement at full body entry limit updates content without evicting an unrelated entry");
        boolean replacements=true;
        for(int n=0;n<12;n++){
            doc.title="连续更新"+n;cache.write(doc,namespace,session,now+10+n);
            ArticleDiskCache.Hit current=cache.read(doc.url,namespace,session,false,now+30+n);
            replacements&=root.listFiles().length==1000&&current!=null&&doc.title.equals(current.document.title);
        }
        check.accept(replacements,"repeated full-count replacements retain all unrelated records and publish the latest complete body");
        Document next=new Document();next.url="https://example.org/quota-new";next.blocks.add(new Block("text","新增正文"));cache.write(next,namespace,session,now+3);
        check.accept(root.listFiles().length==1000&&cache.read(next.url,namespace,session,false,now+4)!=null,"new body at full entry limit still evicts exactly one old entry");
        check.accept(cache.clearFiles()&&root.listFiles().length==0,"quota fixture cleanup removes only isolated body cache files");
    }
    private static void setCookie(Instrumentation test,String url,String cookie)throws Exception{
        CountDownLatch done=new CountDownLatch(1);boolean[] accepted={false};
        test.runOnMainSync(()->android.webkit.CookieManager.getInstance().setCookie(url,cookie,ok->{accepted[0]=ok;done.countDown();}));
        if(!done.await(5,TimeUnit.SECONDS)||!accepted[0])throw new IOException("Test cookie update failed");
    }
    private static void delete(File file,File root)throws IOException{if(!file.getCanonicalPath().equals(root.getCanonicalPath())&&!file.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("Test path escape");File[] files=file.listFiles();if(files!=null)for(File child:files)delete(child,root);if(file.exists()&&!file.delete())throw new IOException("Test cleanup failed");}
}



