package app.quietreader;

import android.app.*;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import static app.quietreader.Models.*;

/** Inspect the retained real board/body gap. Diagnosis success is not complete source coverage. */
final class BbcCacheDiagnosisProbe {
    private final Instrumentation test;private Repository repo;private int checks,failures;private final StringBuilder report=new StringBuilder();
    BbcCacheDiagnosisProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private void check(boolean value,String label){checks++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');if(!value)throw new IllegalStateException(label);}
    private void write(String name,String content)throws IOException{try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),name))){out.write(content.getBytes(StandardCharsets.UTF_8));}}
    private JSONObject flags(Document doc)throws JSONException{return new JSONObject().put("hasContent",doc.hasContent()).put("unavailable",doc.sourceUnavailable).put("login",doc.loginRequired).put("filteredVideo",doc.filteredVideo).put("videoTopic",doc.videoTopic).put("sections",doc.sections.size()).put("blocks",doc.blocks.size()).put("notice",doc.notice);}
    void run(){try{
        check(test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")&&Build.HARDWARE.equals("ranchu"),"isolated emulator only");
        List<Item> board=Models.fromJson(new JSONArray(test.getTargetContext().getSharedPreferences("boards",0).getString(Source.BBC.name(),"[]")));
        check(board.size()>=8,"retained raw public BBC board is available without replacing its cache");ui(()->repo=new Repository(test.getTargetContext()));JSONArray inventory=new JSONArray();int prepared=0,missing=0;
        for(Item item:board){
            Document[] cached={null};boolean[] hidden={false};CountDownLatch restored=new CountDownLatch(1);ui(()->{hidden[0]=repo.hiddenVideo(item);repo.restoreArticle(item,doc->{cached[0]=doc;restored.countDown();});});
            if(!restored.await(10,TimeUnit.SECONDS))throw new IOException("Disk restore timeout");
            boolean ready=cached[0]!=null&&cached[0].hasContent()&&ArticleVersion.matches(item,cached[0]);JSONObject row=new JSONObject().put("url",item.url).put("title",item.title).put("prepared",ready).put("hiddenVideo",hidden[0]);
            if(cached[0]!=null)row.put("cached",flags(cached[0]));
            if(ready)prepared++;else{
                missing++;String name="bbc-cache-diagnosis-"+BbcChineseSource.articleId(item.url);
                try{
                    String html=Repository.fetch(item.url,null,item.url);write(name+".html",html);Document current=SourceParser.article(Source.BBC,html,item.url);row.put("currentNative",flags(current));
                    org.jsoup.nodes.Element state=org.jsoup.Jsoup.parse(html).selectFirst("script#__NEXT_DATA__");
                    if(state!=null){JSONObject props=new JSONObject(state.data()).getJSONObject("props").getJSONObject("pageProps");row.put("pageType",props.optString("pageType")).put("status",props.optInt("status"));JSONObject data=props.optJSONObject("pageData");if(data!=null){JSONObject content=data.optJSONObject("content");if(content!=null){JSONObject model=content.optJSONObject("model");if(model!=null){JSONArray blocks=model.optJSONArray("blocks"),types=new JSONArray();if(blocks!=null)for(int n=0;n<blocks.length();n++)types.put(blocks.getJSONObject(n).optString("type"));row.put("topBlockTypes",types);}}}}
                    row.put("rawFile",name+".html");
                }catch(Exception error){row.put("currentReadError",error.getClass().getSimpleName()+": "+error.getMessage());}
            }
            inventory.put(row);
        }
        JSONObject summary=new JSONObject().put("board",board.size()).put("prepared",prepared).put("missing",missing).put("items",inventory);write("bbc-cache-diagnosis.json",summary.toString(2));report.append(summary.toString(2)).append('\n');check(true,"retained coverage and current public gap response recorded; not a full-coverage pass");
    }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
    finally{ui(()->{if(repo!=null){repo.releaseArticleMemory();repo.close();}});}
    report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
