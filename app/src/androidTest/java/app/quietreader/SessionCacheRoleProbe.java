package app.quietreader;

import android.app.*;
import android.content.Context;
import android.os.*;
import android.webkit.CookieManager;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Recent changed-metadata bodies crossed with account switch/logout in real three-tier caches. */
final class SessionCacheRoleProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder("SESSION CACHE ROLE; synthetic cookies only\n");
    SessionCacheRoleProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void cookie(String url,String value)throws Exception{CountDownLatch done=new CountDownLatch(1);boolean[] accepted={false};String path=java.net.URI.create(url).getPath();ui(()->CookieManager.getInstance().setCookie(url,"qr_role_account="+value+"; Path="+path+"; Secure"+(value.isEmpty()?"; Max-Age=0":""),ok->{accepted[0]=ok;done.countDown();}));if(!done.await(5,TimeUnit.SECONDS)||!accepted[0])throw new IllegalStateException("Fixture cookie was not accepted");}
    private Document body(Item item){Document doc=new Document();doc.url=item.url;doc.title=item.title;Section main=new Section("session-role-body","正文",false);main.blocks.add(new Block("text","旧账户近期正文，只可在同一账户下复用。"));doc.sections.add(main);doc.blocks.addAll(main.blocks);ArticleVersion.capture(item,doc);return doc;}
    private Document restore(Repository repo,Item item)throws Exception{CountDownLatch done=new CountDownLatch(1);Document[] restored={null};ui(()->repo.restoreArticle(item,doc->{restored[0]=doc;done.countDown();}));if(!done.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Disk restoration did not settle");return restored[0];}
    private void flush()throws Exception{Field f=ArticleDiskCache.class.getDeclaredField("writer");f.setAccessible(true);((ExecutorService)f.get(null)).submit(()->{}).get(5,TimeUnit.SECONDS);}
    void run(){Context context=test.getTargetContext();Repository[] repo={null};List<String> urls=new ArrayList<>();
        try{
            for(Source source:new Source[]{Source.ZHIHU,Source.HUPU,Source.TIEBA})for(boolean logout:new boolean[]{false,true}){
                long id=System.currentTimeMillis();String url=source==Source.ZHIHU?"https://www.zhihu.com/question/"+id:source==Source.HUPU?"https://bbs.hupu.com/"+id+".html":"https://tieba.baidu.com/p/"+id;urls.add(url);cookie(url,"before");
                Item before=new Item(source,"旧标题",url,"",false,"旧简介",null,""),after=new Item(source,"更新后的标题",url,"",false,"更新后的简介",null,"");Document old=body(before);ArticleSession[] captured={null};ReadingHistory history=new ReadingHistory(context);
                ui(()->{repo[0]=new Repository(context);repo[0].retainBatch(List.of(url));captured[0]=new ArticleSession(context,before);check(captured[0].stamp(old),"old account acquisition is stamped: "+source+" logout="+logout);repo[0].cacheArticle(old);history.save(url,old,Set.of("session-role-body"),0);});flush();
                check(!ArticleVersion.matches(after,old)&&ArticleVersion.readableCached(after,old,System.currentTimeMillis(),false),"recent changed-metadata body is genuinely eligible before account change");
                ui(()->{check(repo[0].cachedArticle(after)==old,"RAM permits same-account recent old metadata");org.json.JSONObject saved=history.get(url);check(saved!=null&&saved.has("document"),"history permits same-account recent old metadata");});
                Document disk=restore(repo[0],after);check(disk!=null&&ArticleVersion.readableCached(after,disk,System.currentTimeMillis(),false)&&disk.metadataHash.equals(old.metadataHash),"disk permits same-account recent metadata without relabelling");
                cookie(url,logout?"":"after");
                ui(()->{check(repo[0].cachedArticle(after)==null,"changed account rejects old RAM even with reusable metadata");org.json.JSONObject saved=history.get(url);check(saved!=null&&!saved.has("document"),"changed account rejects old history body");check(!captured[0].stamp(body(before)),"late old-account acquisition cannot be restamped");repo[0].releaseArticleMemory();});
                check(restore(repo[0],after)==null,"changed account rejects old disk body after RAM eviction");
                Document current=body(after);ui(()->{repo[0].retainBatch(List.of(url));check(new ArticleSession(context,after).stamp(current),"new account can acquire its own body");repo[0].cacheArticle(current);check(repo[0].cachedArticle(after)==current,"new account body is immediately reusable");repo[0].close();});repo[0]=null;
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{ui(()->{if(repo[0]!=null){repo[0].releaseArticleMemory();repo[0].close();}Repository cleanup=new Repository(context);cleanup.releaseArticleMemory();cleanup.close();android.content.SharedPreferences.Editor edit=context.getSharedPreferences("reading-history",0).edit();for(String url:urls)edit.remove(url);edit.commit();});for(String url:urls)try{cookie(url,"");}catch(Exception error){failures++;report.append("ERROR fixture cookie cleanup\n");}}
        report.append("scope=actual CookieManager, Repository RAM/disk/history and acquisition tokens; no real account or live source claim\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
