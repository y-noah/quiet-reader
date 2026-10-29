package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Real parser, bounded asynchronous body writer and disk restore; no network or account changes. */
final class QueuedArticleIdentityProbe {
    private final Instrumentation test;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    QueuedArticleIdentityProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private Section child(Document document){return document.sections.stream().filter(s->s.nestedReply&&!s.sourceChildId.isEmpty()).findFirst().orElse(null);}
    private Document fixture(String url){
        String html="<div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>主帖正文</div></div>"
            +"<div class=pc-pb-reply-list><div class=pb-comment-list><div class=pb-comment-item data-id='7711'><a class=user-name>楼层作者</a>"
            +"<div class=comment-content><div class=pb-rich-text>楼层正文</div></div><div class=pb-lzl-item data-id='8822'><a class=head-name>楼内作者</a>"
            +"<div class=comment-content><div class=pb-rich-text>明确身份的楼内回复</div></div></div><div class=pb-lzl-item><a class=head-name>另一位作者</a>"
            +"<div class=comment-content><div class=pb-rich-text>无来源编号的楼内回复</div></div></div></div></div></div></div>";
        return SourceParser.article(Source.TIEBA,html,url);
    }
    private void delete(File file,File root)throws Exception{
        if(Files.isSymbolicLink(file.toPath()))throw new IOException("Unexpected test symlink");
        String path=file.getCanonicalPath(),base=root.getCanonicalPath();if(!path.equals(base)&&!path.startsWith(base+File.separator))throw new IOException("Test path escape");
        File[] children=file.listFiles();if(children!=null)for(File value:children)delete(value,root);if(file.exists()&&!file.delete())throw new IOException("Test cleanup failed");
    }
    void run(){Context base=test.getTargetContext();String name="test-queued-identity-"+UUID.randomUUID();File root=new File(base.getCacheDir(),name);
        Context context=new ContextWrapper(base){@Override public Context getApplicationContext(){return this;}@Override public File getCacheDir(){return root;}@Override public SharedPreferences getSharedPreferences(String key,int mode){return super.getSharedPreferences(name+key,mode);}};
        CountDownLatch release=new CountDownLatch(1);ThreadPoolExecutor writer=null;
        try{
            Field field=ArticleDiskCache.class.getDeclaredField("writer");field.setAccessible(true);writer=(ThreadPoolExecutor)field.get(null);
            Document direct=fixture("https://tieba.baidu.com/p/9900771122"),queued=fixture("https://tieba.baidu.com/p/9900771133");
            test.runOnMainSync(()->{if(!new ArticleSession(context,direct.url).stamp(direct)||!new ArticleSession(context,queued.url).stamp(queued))throw new IllegalStateException("Fixture stamp failed");});
            Section original=child(queued);check(original!=null&&original.sourceChildId.equals("8822")&&TiebaReplyMedia.explicit(original),"real source parser captures explicit child identity and its parent");
            if(original==null)throw new IllegalStateException("Missing parser child");
            String expected=ReadingHistory.encode(queued).toString(),parent=original.parentSectionId;
            ArticleDiskCache disk=new ArticleDiskCache(context);disk.write(direct,direct.cachedNamespace,direct.cachedSession,direct.cachedAt);
            ArticleDiskCache.Hit control=disk.read(direct.url,direct.cachedNamespace,direct.cachedSession,false,System.currentTimeMillis());
            check(control!=null&&child(control.document)!=null&&TiebaReplyMedia.explicit(child(control.document)),"direct disk serialization preserves the explicit identity control");
            CountDownLatch entered=new CountDownLatch(1);writer.execute(()->{entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Writer gate timeout");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
            if(!entered.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Writer gate did not start");
            test.runOnMainSync(()->disk.enqueue(queued,queued.cachedNamespace,queued.cachedSession,queued.cachedAt));
            // Mutating the caller after enqueue must not mutate the queued section snapshot.
            original.sourceChildId="9933";original.parentSectionId="changed-after-enqueue";
            release.countDown();writer.submit(()->{}).get(8,TimeUnit.SECONDS);
            ArticleDiskCache.Hit restored=disk.read(queued.url,queued.cachedNamespace,queued.cachedSession,false,System.currentTimeMillis());
            check(restored!=null,"asynchronous production writer produces a restorable body");
            Section saved=restored==null?null:child(restored.document);
            check(saved!=null&&saved.sourceChildId.equals("8822")&&saved.parentSectionId.equals(parent),"queued snapshot preserves original child ID and DOM parent independently of caller mutation");
            check(saved!=null&&TiebaReplyMedia.explicit(saved),"disk-restored child retains source identity eligibility for exact media reconciliation");
            check(restored!=null&&ReadingHistory.encode(restored.document).toString().equals(expected),"queued disk round trip preserves all encoded article and relationship fields");
            check(restored!=null&&restored.document.sections.stream().anyMatch(s->s.nestedReply&&s.sourceChildId.isEmpty()),"ID-less reply remains ID-less rather than acquiring an inferred source identity");
            if(restored!=null){
                File target=new File(root,"article-bodies-v1/"+app.quietreader.discovery.HotCandidate.hash(queued.cachedNamespace+"\n"+queued.cachedSession+"\n"+queued.url)+".json");
                long inode=android.system.Os.stat(target.getPath()).st_ino;byte[] originalBytes=Files.readAllBytes(target.toPath());
                test.runOnMainSync(()->disk.enqueue(restored.document,queued.cachedNamespace,queued.cachedSession,queued.cachedAt));writer.submit(()->{}).get(8,TimeUnit.SECONDS);
                check(android.system.Os.stat(target.getPath()).st_ino==inode&&Arrays.equals(originalBytes,Files.readAllBytes(target.toPath())),"identical queued acquisition reuses the existing file instead of replacing it");
                restored.document.title="同获取时间但正文标题已更新";
                test.runOnMainSync(()->disk.enqueue(restored.document,queued.cachedNamespace,queued.cachedSession,queued.cachedAt));writer.submit(()->{}).get(8,TimeUnit.SECONDS);
                ArticleDiskCache.Hit changed=disk.read(queued.url,queued.cachedNamespace,queued.cachedSession,false,System.currentTimeMillis());
                check(changed!=null&&changed.document.title.equals(restored.document.title)&&changed.at==queued.cachedAt,"changed body with the same acquisition time is still persisted");
                long reacquiredAt=System.currentTimeMillis();test.runOnMainSync(()->disk.enqueue(restored.document,queued.cachedNamespace,queued.cachedSession,reacquiredAt));writer.submit(()->{}).get(8,TimeUnit.SECONDS);
                ArticleDiskCache.Hit reacquired=disk.read(queued.url,queued.cachedNamespace,queued.cachedSession,false,System.currentTimeMillis());
                check(reacquiredAt>queued.cachedAt&&reacquired!=null&&reacquired.at==reacquiredAt,"genuine newer acquisition of the same body still updates freshness");
            }
            if(restored!=null){Repository repository=new Repository(context);try{
                CountDownLatch delivered=new CountDownLatch(1);Document[] opened={null};
                test.runOnMainSync(()->repository.restoreArticle(new Item(Source.TIEBA,queued.title,queued.url,""),doc->{opened[0]=doc;delivered.countDown();}));
                if(!delivered.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Repository disk restore timed out");
                ReadingHistory history=new ReadingHistory(context);test.runOnMainSync(()->{if(opened[0]!=null)history.save(opened[0].url,opened[0],Set.of(parent),123);});org.json.JSONObject state=history.get(queued.url);Document reopened=state!=null&&state.has("document")?ReadingHistory.restore(state):null;
                check(reopened!=null&&child(reopened)!=null&&child(reopened).sourceChildId.equals("8822")&&state.optInt("position")==123,"history saved from actual Repository disk restoration retains child identity and reading marker");
            }finally{test.runOnMainSync(()->{repository.releaseArticleMemory();repository.close();});}}
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{release.countDown();try{if(writer!=null)writer.submit(()->{}).get(8,TimeUnit.SECONDS);delete(root,root);base.deleteSharedPreferences(name+"article-cache");base.deleteSharedPreferences(name+"reading-history");check(!root.exists(),"isolated cache files and preference fixtures cleaned");}catch(Exception error){failures++;report.append("CLEANUP ").append(error.getClass().getSimpleName()).append('\n');}}
        report.append("scope=controlled parser and real queued disk persistence; not live permission or device energy\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
