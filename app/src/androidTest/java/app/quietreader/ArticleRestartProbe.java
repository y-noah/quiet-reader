package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import java.io.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Two instrumentation invocations separated by an external process stop. */
final class ArticleRestartProbe {
    static void run(Instrumentation test,String phase,String id){
        Bundle result=new Bundle();StringBuilder report=new StringBuilder();boolean passed=false;
        try{
            if(id==null||!id.matches("[a-f0-9]{32}"))throw new IOException("Invalid probe identity");
            String name="test-article-restart-"+id;
            File root=new File(test.getTargetContext().getCacheDir(),name);
            Context context=new ContextWrapper(test.getTargetContext()){
                @Override public Context getApplicationContext(){return this;}
                @Override public File getCacheDir(){return root;}
                @Override public SharedPreferences getSharedPreferences(String key,int mode){return super.getSharedPreferences(name+key,mode);}
            };
            Item item=new Item(Source.ZHIHU,"进程重启正文测试","https://example.org/restart/"+id,"");
            ArticleDiskCache disk=new ArticleDiskCache(context);SharedPreferences state=context.getSharedPreferences("probe",0);
            if("cache-restart-write".equals(phase)){
                Document doc=new Document();doc.url=item.url;doc.title=item.title;
                Section section=new Section("answer-one","已预读的回答",true);section.continuationUrl=item.url+"?page=2";section.blocks.add(new Block("text","进程退出后仍可直接读到的测试正文"));doc.sections.add(section);doc.blocks.addAll(section.blocks);
                long at=System.currentTimeMillis();disk.write(doc,disk.namespace(),ArticleDiskCache.session(item.url),at);
                if(disk.read(item.url,disk.namespace(),ArticleDiskCache.session(item.url),false,at)==null)throw new IOException("Initial disk write missing");
                if(!state.edit().putInt("pid",android.os.Process.myPid()).putLong("at",at).commit())throw new IOException("Probe state not persisted");
                report.append("PASS body persisted before process stop; pid=").append(android.os.Process.myPid()).append('\n');
            }else{
                int old=state.getInt("pid",-1);if(old<0||old==android.os.Process.myPid())throw new IOException("A different writer process is required");
                report.append("PASS new process; writer=").append(old).append(" reader=").append(android.os.Process.myPid()).append('\n');
                Repository[] repo={null};Document[] loaded={null};boolean[] empty={false};CountDownLatch done=new CountDownLatch(1);
                test.runOnMainSync(()->{repo[0]=new Repository(context);empty[0]=repo[0].cachedArticle(item)==null;repo[0].restoreArticle(item,doc->{loaded[0]=doc;done.countDown();});});
                try{
                    if(!done.await(5,TimeUnit.SECONDS)||!empty[0]||loaded[0]==null)throw new IOException("Cold process disk restore failed");
                    Document doc=loaded[0];if(doc.cachedAt!=state.getLong("at",-1)||!doc.sections.get(0).continuationUrl.equals(item.url+"?page=2"))throw new IOException("Freshness or continuation lost");
                    report.append("PASS empty process memory restores body, original timestamp and continuation without a network loader\n");
                }finally{test.runOnMainSync(()->{repo[0].releaseArticleMemory();repo[0].close();});}
                clean(root,root);test.getTargetContext().deleteSharedPreferences(name+"probe");test.getTargetContext().deleteSharedPreferences(name+"article-cache");
            }
            passed=true;
        }catch(Throwable error){report.append("FAIL ").append(error).append('\n');}
        result.putString("stream",report.toString());test.finish(passed?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private static void clean(File file,File root)throws IOException{
        if(!file.getCanonicalFile().equals(root.getCanonicalFile())&&!file.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("Test path escape");
        File[] children=file.listFiles();if(children!=null)for(File child:children)clean(child,root);if(file.exists()&&!file.delete())throw new IOException("Cleanup failed");
    }
}
