package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import static app.quietreader.Models.*;

/** Reacquire the nine actual failed search URLs, then check reuse across navigations. */
final class BaiduCacheRecoveryProbe {
    private final Instrumentation test;
    private Activity host;private Repository repo;private DynamicReader dynamic;
    BaiduCacheRecoveryProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable task){test.runOnMainSync(task);}
    void run(){
        String database="test-ui-baidu-recovery-"+UUID.randomUUID()+".db";
        int failures=0;StringBuilder report=new StringBuilder();List<Item> items=new ArrayList<>();
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")||!Build.HARDWARE.equals("ranchu"))throw new IllegalStateException("Isolated emulator only");
            JSONArray targets;try(java.io.InputStream input=test.getContext().getAssets().open("baidu-failed-targets-20261103.json")){targets=new JSONArray(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
            host=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            ui(()->repo=new Repository(host));
            for(int n=0;n<targets.length();n++){
                JSONObject target=targets.getJSONObject(n);Item item=new Item(Source.BAIDU,target.getString("title"),target.getString("url"),"");items.add(item);
                CountDownLatch done=new CountDownLatch(1);Document[] body={null};boolean[] browser={false};String[] error={""};long start=SystemClock.elapsedRealtime();
                Repository.Result<Document> finish=new Repository.Result<Document>(){
                    public void success(Document doc){body[0]=doc;repo.cacheArticle(doc);done.countDown();}
                    public void failure(String message){error[0]=message;done.countDown();}
                };
                Runnable fallback=()->{browser[0]=true;dynamic=new DynamicReader(host,item,finish,true);};
                ui(()->repo.article(item,new Repository.Result<Document>(){
                    public void success(Document doc){if(doc.hasContent()&&!doc.loginRequired&&!doc.sourceUnavailable)finish.success(doc);else fallback.run();}
                    public void failure(String message){fallback.run();}
                }));
                boolean completed=done.await(35,TimeUnit.SECONDS);long elapsed=SystemClock.elapsedRealtime()-start;
                boolean[] memory={false};ui(()->{memory[0]=repo.cachedArticle(item)!=null;repo.cancelPending();if(dynamic!=null){dynamic.close();dynamic=null;}});
                boolean valid=completed&&body[0]!=null&&body[0].hasContent()&&memory[0]&&body[0].related.isEmpty();if(!valid)failures++;
                String line="TARGET index="+(n+1)+" readable="+valid+" ms="+elapsed+" browser="+browser[0]+" ram="+memory[0]+" title="+item.title+" error="+error[0];report.append(line).append('\n');Bundle status=new Bundle();status.putString("stream",line+"\n");test.sendStatus(1,status);
            }
            // Every preceding body must survive loading subsequent search pages.
            ArticleDiskCache disk=new ArticleDiskCache(host);int ram=0,persisted=0;
            for(Item item:items){boolean[] hit={false};String[] session={""},namespace={""};ui(()->{hit[0]=repo.cachedArticle(item)!=null;session[0]=ArticleDiskCache.session(item.url);namespace[0]=disk.namespace();});if(hit[0])ram++;
                ArticleDiskCache.Hit stored=null;long deadline=SystemClock.elapsedRealtime()+3000;
                while(stored==null&&SystemClock.elapsedRealtime()<deadline){stored=disk.read(item.url,namespace[0],session[0],false,System.currentTimeMillis());if(stored==null)Thread.sleep(100);}
                if(stored!=null&&stored.document.hasContent())persisted++;
            }
            report.append("AFTER_ALL ram=").append(ram).append('/').append(items.size()).append(" disk=").append(persisted).append('/').append(items.size()).append('\n');
            if(ram!=items.size()||persisted!=items.size())failures++;
        }catch(Throwable error){failures++;report.append(android.util.Log.getStackTraceString(error));}
        finally{ui(()->{if(dynamic!=null)dynamic.close();if(repo!=null)repo.close();if(host!=null)host.finish();});test.getTargetContext().deleteDatabase(database);}
        report.append("failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
