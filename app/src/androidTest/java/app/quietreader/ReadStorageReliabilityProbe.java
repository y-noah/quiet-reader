package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static app.quietreader.Models.*;

/** Physical cache/history faults and recovery, using a private emulator-only namespace. */
final class ReadStorageReliabilityProbe {
    private final Instrumentation test;
    private int rounds,failures;
    private final StringBuilder report=new StringBuilder();
    ReadStorageReliabilityProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new IllegalStateException(error[0]);}
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private Document body(Item item,int seed,String value){Document doc=new Document();doc.url=item.url;doc.title=item.title;Section section=new Section("main","正文",false);for(int n=0;n<1+seed%5;n++){Block block=new Block("text",value+"-"+seed+"-"+n+"。".repeat(1+seed%17));section.blocks.add(block);doc.blocks.add(block);}doc.sections.add(section);ArticleVersion.capture(item,doc);return doc;}
    private void save(ReadingHistory history,Document doc,int position){ui(()->history.save(doc.url,doc,Set.of("main"),position));}
    private JSONObject history(ReadingHistory history,String url){JSONObject[] result={null};ui(()->result[0]=history.get(url));return result[0];}
    private void scenario(Context context,File root,int number,int mode)throws Exception{
        ArticleDiskCache disk=new ArticleDiskCache(context,300_000);ReadingHistory history=new ReadingHistory(context);long now=System.currentTimeMillis();
        Item item=new Item(Source.THEPAPER,"存储恢复样本"+number,"https://www.thepaper.cn/newsDetail_forward_"+(now+number),"");
        Document original=body(item,number,"原正文");ui(()->require(new ArticleSession(context,item).stamp(original),"initial stamp failed"));now=original.cachedAt;String namespace=disk.namespace(),session=original.cachedSession;
        disk.write(original,namespace,session,original.cachedAt);save(history,original,100+number);
        require(disk.read(item.url,namespace,session,false,now+20)!=null,"initial durable body missing");require(history(history,item.url).has("document"),"initial historical body missing");
        File record=new File(new File(root,"article-bodies-v1"),app.quietreader.discovery.HotCandidate.hash(namespace+"\n"+session+"\n"+item.url)+".json");
        switch(mode){
            case 0 -> {Document loaded=ReadingHistory.restore(history(history,item.url));require(loaded.cachedAt==original.cachedAt&&ArticleVersion.matches(item,loaded),"history renewed timestamp or lost version");require(loaded.blocks.size()==original.blocks.size(),"history lost body sections");}
            case 1,2,3 -> {
                Document gate=body(item,number,"仅能看到摘要");gate.loginRequired=mode==1;gate.sourceUnavailable=mode==2;gate.filteredVideo=mode==3;ui(()->require(new ArticleSession(context,item).stamp(gate),"gate stamp failed"));save(history,gate,200+number);
                JSONObject state=history(history,item.url);require(!state.has("document")&&state.optInt("position")==200+number,"restricted preview retained as complete historical body");
                disk.write(gate,namespace,session,gate.cachedAt);ArticleDiskCache.Hit hit=disk.read(item.url,namespace,session,false,System.currentTimeMillis());require(hit!=null&&hit.document.blocks.get(0).value.startsWith("原正文"),"restricted result overwrote last good disk body");
                // A malformed record from an older writer must not enter the reader either.
                try(FileOutputStream out=new FileOutputStream(record)){out.write(new JSONObject().put("at",now).put("document",ReadingHistory.encode(gate)).toString().getBytes(StandardCharsets.UTF_8));}
                require(disk.read(item.url,namespace,session,false,System.currentTimeMillis())==null,"restricted disk record accepted");
            }
            case 4 -> {original.cachedSession="not-this-account";save(history,original,210);require(!history(history,item.url).has("document"),"wrong-account history accepted");require(disk.read(item.url,namespace,"not-this-account",false,now+20)==null,"wrong-account disk cache accepted");}
            case 5 -> {ui(disk::invalidate);require(!history(history,item.url).has("document"),"retired namespace history accepted");disk.write(original,namespace,session,now);require(disk.read(item.url,disk.namespace(),session,false,now+20)==null,"late retired write crossed namespace");}
            case 6 -> {Item updated=new Item(item.source,item.title+"新版本",item.url,"");ArticleDiskCache.Hit hit=disk.read(item.url,namespace,session,false,now+20);require(hit!=null&&!ArticleVersion.matches(updated,hit.document)&&ArticleVersion.matches(item,hit.document),"cache version identity was relabelled");}
            case 7 -> {require(disk.read(item.url,namespace,session,false,now+11*60_000)==null,"expired online body accepted");require(disk.read(item.url,namespace,session,true,now+11*60_000)!=null,"offline valid snapshot lost");}
            case 8 -> {disk.write(original,namespace,session,now+60_000);require(disk.read(item.url,namespace,session,false,now)==null&&!record.exists(),"future cache timestamp accepted or left behind");}
            case 9 -> {try(FileOutputStream out=new FileOutputStream(record)){out.write("{\"at\":".getBytes(StandardCharsets.UTF_8));}require(disk.read(item.url,namespace,session,false,now+20)==null&&!record.exists(),"corrupt cache not removed");}
            case 10 -> {Document tooLarge=body(item,number,"不应替换的正文");new ArticleDiskCache(context,1).write(tooLarge,namespace,session,now);ArticleDiskCache.Hit hit=disk.read(item.url,namespace,session,false,now+20);require(hit!=null&&hit.document.blocks.get(0).value.startsWith("原正文"),"failed quota admission destroyed previous body");}
            case 11 -> {JSONObject legacy=new JSONObject(context.getSharedPreferences("reading-history",0).getString(item.url,"{}"));legacy.remove("bodyFormat");context.getSharedPreferences("reading-history",0).edit().putString(item.url,legacy.toString()).commit();JSONObject state=history(history,item.url);require(!state.has("document")&&state.optInt("position")==100+number,"unsafe legacy snapshot accepted or reading position discarded");}
            default -> throw new IllegalArgumentException();
        }
        Document recovered=body(item,number,"恢复后的完整正文");ui(()->require(new ArticleSession(context,item).stamp(recovered),"recovery stamp failed"));disk.write(recovered,disk.namespace(),recovered.cachedSession,recovered.cachedAt);save(history,recovered,300+number);
        ArticleDiskCache.Hit hit=new ArticleDiskCache(context).read(item.url,disk.namespace(),recovered.cachedSession,false,System.currentTimeMillis());JSONObject state=history(new ReadingHistory(context),item.url);
        require(hit!=null&&hit.document.blocks.get(0).value.startsWith("恢复后的完整正文"),"disk did not recover after fault");require(state.has("document")&&ReadingHistory.restore(state).blocks.get(0).value.startsWith("恢复后的完整正文")&&state.optInt("position")==300+number,"history did not recover after fault");
    }
    void run(){String name="read-storage-"+UUID.randomUUID();File root=new File(test.getTargetContext().getCacheDir(),name);Context context=new ContextWrapper(test.getTargetContext()){
            @Override public Context getApplicationContext(){return this;}
            @Override public File getCacheDir(){return root;}
            @Override public android.content.SharedPreferences getSharedPreferences(String key,int mode){return super.getSharedPreferences(name+key,mode);}
        };
        File ledger=new File(test.getTargetContext().getFilesDir(),"read-storage-rounds.jsonl");
        try{
            require(test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")&&Build.HARDWARE.equals("ranchu"),"bulk storage tests forbidden on user phone");
            try(FileOutputStream output=new FileOutputStream(ledger)){for(int n=0;n<240;n++){int mode=n%12;long start=SystemClock.elapsedRealtime();String error="";
                try{scenario(context,root,n,mode);}catch(Throwable failure){error=failure.toString();failures++;}finally{ui(()->new ArticleDiskCache(context).invalidate());new ArticleDiskCache(context).clearFiles();context.getSharedPreferences("reading-history",0).edit().clear().commit();}
                rounds++;JSONObject row=new JSONObject().put("round",n+1).put("scenario",mode).put("seed",n).put("elapsedMs",SystemClock.elapsedRealtime()-start).put("pass",error.isEmpty()).put("error",error);output.write((row+"\n").getBytes(StandardCharsets.UTF_8));output.flush();if(!error.isEmpty())report.append("FAIL ").append(row).append('\n');
                if(n%20==0){Bundle progress=new Bundle();progress.putString("stream","storage rounds="+rounds+" failures="+failures+"\n");test.sendStatus(0,progress);}
            }}
        }catch(Throwable failure){failures++;report.append("INFRA ").append(failure).append('\n');}
        finally{new ArticleDiskCache(context).clearFiles();File folder=new File(root,"article-bodies-v1");folder.delete();root.delete();test.getTargetContext().deleteSharedPreferences(name+"article-cache");test.getTargetContext().deleteSharedPreferences(name+"reading-history");}
        report.append("rounds=").append(rounds).append(" failures=").append(failures).append("; physical isolated storage faults and recovery, no live sources\n");Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
