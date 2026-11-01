package app.quietreader;
import android.app.*;
import android.os.*;
import app.quietreader.discovery.*;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static app.quietreader.Models.*;
/** Public captured hot lists only, isolated emulator corpus preparation. */
final class PersonaCorpusProbe {
    private final Instrumentation test;
    PersonaCorpusProbe(Instrumentation test){this.test=test;}
    void run(){JSONArray rows=new JSONArray(),sources=new JSONArray();int failures=0;
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")||!Build.MODEL.startsWith("sdk_gphone"))throw new IllegalStateException("isolated emulator only");
            File root=new File(test.getTargetContext().getExternalFilesDir(null),"persona-campaign");long now=System.currentTimeMillis();
            for(Source source:HotSourceCatalog.TEXT_HOT){JSONObject result=new JSONObject().put("source",source.name());try{
                String raw=new String(Files.readAllBytes(new File(root,"boards/"+source.name()+".txt").toPath()),StandardCharsets.UTF_8);List<Item> items=SourceParser.list(source,raw);int rank=0,kept=0;
                for(Item item:items){rank++;if(item.video||!ContentLanguage.eligible(item.title,item.snippet,ContentLanguage.Body.UNKNOWN))continue;
                    HotCandidate candidate=new HotCandidate(source,item.url,item.title,item.snippet,item.detail,item.video,rank,item.publishedAt,now);rows.put(candidate.json().put("id",candidate.id));kept++;}
                result.put("rows",kept);
            }catch(Throwable error){result.put("error",error.toString());}sources.put(result);}
            if(rows.length()<100)throw new IllegalStateException("too little observed public supply");
            Files.write(new File(root,"corpus.json").toPath(),rows.toString().getBytes(StandardCharsets.UTF_8));
        }catch(Throwable error){failures++;try{sources.put(new JSONObject().put("error",error.toString()));}catch(Exception ignored){}}
        Bundle result=new Bundle();result.putString("stream","CORPUS "+sources+"\nrows="+rows.length()+" failures="+failures+"\n");test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
