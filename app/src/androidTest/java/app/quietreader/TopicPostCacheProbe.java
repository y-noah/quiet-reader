package app.quietreader;

import android.app.Instrumentation;
import android.content.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import static app.quietreader.Models.*;

final class TopicPostCacheProbe {
    static void run(Instrumentation test,BiConsumer<Boolean,String> check)throws Exception{
        String name="topic-cache-"+UUID.randomUUID();File folder=new File(test.getTargetContext().getCacheDir(),name);
        Context context=new ContextWrapper(test.getTargetContext()){
            @Override public Context getApplicationContext(){return this;}
            @Override public File getCacheDir(){return folder;}
            @Override public SharedPreferences getSharedPreferences(String key,int mode){return super.getSharedPreferences(name+key,mode);}
        };
        String url="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=999991080";
        Item root=new Item(Source.TIEBA,"同一热议话题",url,"");List<Item> posts=List.of(new Item(Source.TIEBA,"甲","https://tieba.baidu.com/p/999991081",""),new Item(Source.TIEBA,"乙","https://tieba.baidu.com/p/999991082",""));
        List<Document> docs=new ArrayList<>();List<Item> requests=new ArrayList<>();Repository[] repo={null};ArticleDiskCache disk=new ArticleDiskCache(context);
        for(Item post:posts){Document doc=new Document();doc.url=post.url;doc.title=post.title;doc.tiebaReplyRevision=TiebaReplyRelations.REVISION;
            Section main=new Section("main","正文",false);main.blocks.add(new Block("text",post.title+"的独立正文"));Section comment=new Section("reply","回复 · 作者",false);comment.blocks.add(new Block("text",post.title+"的独立评论"));doc.sections.add(main);doc.sections.add(comment);doc.blocks.addAll(main.blocks);doc.blocks.addAll(comment.blocks);
            TiebaTopic.attach(url,posts,doc,ArticleDiskCache.session(post.url),System.currentTimeMillis());ArticleVersion.capture(root,doc);docs.add(doc);requests.add(TiebaTopic.select(root,doc.topic,post));}
        try{
            test.runOnMainSync(()->{repo[0]=new Repository(context);for(Document doc:docs)repo[0].cacheArticle(doc);for(int n=0;n<2;n++)check.accept(repo[0].cachedArticle(requests.get(n))!=null&&repo[0].cachedArticle(requests.get(n)).sourceUrl().equals(docs.get(n).sourceUrl()),"both topic posts survive in memory "+n);});
            for(Document doc:docs){CountDownLatch saved=new CountDownLatch(1);boolean[] ok={false};test.runOnMainSync(()->disk.verify(doc,true,hit->{ok[0]=hit!=null&&hit.document.sourceUrl().equals(doc.sourceUrl());saved.countDown();}));check.accept(saved.await(5,TimeUnit.SECONDS)&&ok[0],"independent post has durable disk receipt "+doc.title);}
            String namespace=disk.namespace(),session=ArticleDiskCache.session(url);
            ArticleDiskCache.Hit whole=disk.read(url,namespace,session,true,System.currentTimeMillis());
            check.accept(whole!=null&&TopicBundle.find(whole.document,posts.get(0).url)!=null&&TopicBundle.find(whole.document,posts.get(1).url)!=null,"one topic snapshot contains both complete bodies");
            File[] variants=new File(folder,"article-bodies-v1").listFiles();if(variants!=null)for(File variant:variants)if(variant.getName().matches("[a-f0-9]{64}-[a-f0-9]{64}\\.json"))variant.delete();
            for(Item request:requests)check.accept(disk.read(request,namespace,session,true,System.currentTimeMillis())!=null,"whole topic alone restores an answer after individual post files are removed "+request.selectedTopicPost);
            disk.write(docs.get(0),namespace,session,docs.get(0).cachedAt);
            File legacyVariant=new File(new File(folder,"article-bodies-v1"),app.quietreader.discovery.HotCandidate.hash(namespace+"\n"+session+"\n"+url)+"-"+app.quietreader.discovery.HotCandidate.hash(posts.get(0).url)+".json");
            if(!legacyVariant.delete())throw new IllegalStateException("legacy fixture variant missing");
            disk.read(url,namespace,session,true,System.currentTimeMillis());disk.write(docs.get(1),namespace,session,docs.get(1).cachedAt);
            ArticleDiskCache.Hit migrated=disk.read(requests.get(0),namespace,session,true,System.currentTimeMillis());
            check.accept(migrated!=null&&migrated.at==docs.get(0).cachedAt&&migrated.document.sourceUrl().equals(posts.get(0).url),"old last-post record migrates before topic slot is overwritten without renewing time");
            test.runOnMainSync(()->{repo[0].releaseArticleMemory();repo[0].close();repo[0]=new Repository(context);});
            for(int n=0;n<2;n++){Item request=requests.get(n);String expected=posts.get(n).title;CountDownLatch loaded=new CountDownLatch(1);boolean[] ok={false};test.runOnMainSync(()->repo[0].restoreArticle(request,doc->{ok[0]=doc!=null&&doc.title.equals(expected)&&doc.sections.get(1).blocks.get(0).value.equals(expected+"的独立评论");loaded.countDown();}));check.accept(loaded.await(5,TimeUnit.SECONDS)&&ok[0],"post and its comments restore after all RAM is discarded "+n);}
            ArticleDiskCache.Retention[] retention={null};test.runOnMainSync(()->{disk.publishItems(List.of(root));retention[0]=repo[0].retainItems(List.of(root));});disk.pruneBatch(retention[0]);
            for(Item request:requests){ArticleDiskCache.Hit hit=disk.read(request,disk.namespace(),ArticleDiskCache.session(url),true,System.currentTimeMillis());check.accept(hit!=null&&hit.document.sourceUrl().equals(request.selectedTopicPost),"returning to hot list retains child post disk cache "+request.selectedTopicPost);}
            ArticleDiskCache.Hit pinned=disk.read(requests.get(0),disk.namespace(),ArticleDiskCache.session(url),false,docs.get(0).cachedAt+11*60_000);check.accept(pinned!=null,"published topic pins child post beyond online refresh TTL");
            check.accept(disk.read(requests.get(0),disk.namespace(),"different-account",true,System.currentTimeMillis())==null,"child post cannot cross account scope");
            test.runOnMainSync(()->disk.pinTopic(docs.get(0)));
            test.runOnMainSync(()->{disk.publishItems(List.of());retention[0]=repo[0].retainItems(List.of());});disk.pruneBatch(retention[0]);
            for(Item request:requests)check.accept(disk.read(request,disk.namespace(),ArticleDiskCache.session(url),true,System.currentTimeMillis())!=null,"explicitly read topic survives outside the speculative hot-list window "+request.selectedTopicPost);
            test.runOnMainSync(()->disk.invalidate());
            check.accept(disk.pinnedTopicUrls().isEmpty(),"reader topic pins cannot cross invalidated cache namespaces");
            check.accept(disk.read(requests.get(0),disk.namespace(),ArticleDiskCache.session(url),true,System.currentTimeMillis())==null,"expired topic family can be pruned together");
        }finally{test.runOnMainSync(()->{if(repo[0]!=null)repo[0].close();disk.publishItems(List.of());disk.retainItems(List.of());});disk.clearFiles();folder.delete();}
    }
}
