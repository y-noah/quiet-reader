package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Real disk -> preloader -> topic publication while the emulator is offline. */
final class OfflineTopicCacheProbe {
    private final Instrumentation test;private final boolean expectOffline;
    OfflineTopicCacheProbe(Instrumentation test){this(test,true);}
    OfflineTopicCacheProbe(Instrumentation test,boolean offline){this.test=test;this.expectOffline=offline;}
    void run(){Activity home=null;TopicPreparation[] preparation={null};int checks=0,failures=0;StringBuilder report=new StringBuilder();String database="test-ui-offline-topic.db";
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader.topicprobe")||!Build.MODEL.startsWith("sdk_gphone"))throw new IllegalStateException("Isolated emulator required");
            home=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            // A freshly cleared target may not yet have its per-UID default network.
            long networkUntil=SystemClock.elapsedRealtime()+5000;
            while(ReadingNetwork.offline(test.getTargetContext())!=expectOffline&&SystemClock.elapsedRealtime()<networkUntil)Thread.sleep(50);
            if(ReadingNetwork.offline(test.getTargetContext())!=expectOffline)throw new IllegalStateException("Unexpected emulator network mode, expected offline="+expectOffline);
            report.append("networkOffline=").append(expectOffline).append("\n");
            Activity owner=home;String root="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=999991166";
            Item old=new Item(Source.TIEBA,"原话题标题",root,"",false,"原摘要",null,""),updated=new Item(Source.TIEBA,"更新后的话题标题",root,"",false,"新版摘要",null,"");
            long at=System.currentTimeMillis()-16*60_000;List<Item> posts=new ArrayList<>();for(int n=0;n<3;n++)posts.add(new Item(Source.TIEBA,"已缓存帖子 "+n,"https://tieba.baidu.com/p/"+(999991167+n),""));
            Document[] bundle={null};test.runOnMainSync(()->{
                for(Item post:posts){Document doc=new Document();doc.url=root;doc.title=old.title;doc.blocks.add(new Block("text",post.title+" 的持久化正文"));doc.topic=new TiebaTopic.Resolved(root,post.url,ArticleDiskCache.session(post.url),at,posts);new ArticleSession(owner,old).stamp(doc);doc.cachedAt=at;
                    if(bundle[0]==null)bundle[0]=doc;else TopicBundle.merge(bundle[0],doc);
                }
            });
            CountDownLatch saved=new CountDownLatch(1);ArticleDiskCache.Hit[] receipt={null};new ArticleDiskCache(owner).verify(bundle[0],true,hit->{receipt[0]=hit;saved.countDown();});
            if(!saved.await(10,TimeUnit.SECONDS)||receipt[0]==null)throw new IllegalStateException("Aged topic fixture was not persisted");
            checks++;report.append("PASS sixteen-minute-old topic persisted with three actual child bodies\n");
            ArticleDiskCache disk=new ArticleDiskCache(owner);ArticleDiskCache.Retention[] shifted={null};
            test.runOnMainSync(()->{disk.publishItems(List.of());shifted[0]=disk.retainItems(List.of(new Item(Source.V2EX,"下一批","https://www.v2ex.com/t/999991166","")));});
            disk.pruneBatch(shifted[0]);
            checks++;if(disk.read(old.url,bundle[0].cachedNamespace,bundle[0].cachedSession,expectOffline,System.currentTimeMillis())==null){failures++;report.append("FAIL previous batch body lost\n");}else report.append("PASS previous batch remains on disk after batch switch\n");
            test.runOnMainSync(()->disk.retainItems(List.of(updated)));
            CountDownLatch ready=new CountDownLatch(1);test.runOnMainSync(()->{new Repository(owner).releaseArticleMemory();preparation[0]=new TopicPreparation(owner,()->{},(item,doc)->ready.countDown());preparation[0].plan(List.of(updated));preparation[0].resume();});
            if(!ready.await(15,TimeUnit.SECONDS))throw new IllegalStateException("Offline topic was never published from disk");
            Document[] restored={null};test.runOnMainSync(()->restored[0]=preparation[0].ready(updated));
            checks++;if(restored[0]==null||!TopicBundle.prepared(restored[0],3)){failures++;report.append("FAIL full initial topic window restored in expected network mode\n");}else report.append("PASS full initial topic window restored in expected network mode\n");
            checks++;if(restored[0]==null||restored[0].cachedAt!=at||!restored[0].metadataHash.equals(ArticleVersion.hash(old))){failures++;report.append("FAIL original acquisition/version retained\n");}else report.append("PASS original acquisition time and metadata retained, never relabeled as a fresh version\n");
            checks++;if(restored[0]==null||!new ArticleSession(owner,updated).owns(restored[0])||!ArticleSession.contentCurrent(restored[0])){failures++;report.append("FAIL account ownership retained\n");}else report.append("PASS restored topic and each child retain current account ownership\n");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{Activity end=home;test.runOnMainSync(()->{if(preparation[0]!=null)preparation[0].close();if(end!=null)end.finish();});test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
