package app.quietreader;

import android.app.*;
import android.os.Bundle;
import app.quietreader.discovery.HotCandidate;
import org.json.*;
import java.util.*;
import static app.quietreader.Models.*;

/** Real Android parser/codec/disk cache, synthetic revisions; no live-source quality claim. */
final class ArticleProgressProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    ArticleProgressProbe(Instrumentation test){this.test=test;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private String prose(int seed){Random random=new Random(seed);StringBuilder text=new StringBuilder();for(int n=0;n<300;n++)text.append((char)(0x4e00+random.nextInt(3000)));return text.toString();}
    private String html(String url,long modified,boolean update){StringBuilder html=new StringBuilder("<link rel=canonical href='").append(url).append("'><meta property='article:modified_time' content='").append(java.time.Instant.ofEpochMilli(modified)).append("'><h1>中文正文进展受控测试</h1><div id=paragraph class=post_content>");for(int n=0;n<6;n++)html.append("<p>").append(prose(n)).append("</p>");if(update)html.append("<p>【最新进展】").append(prose(90)).append("</p><p>").append(prose(91)).append("</p>");return html.append("</div>").toString();}
    void run(){long now=System.currentTimeMillis();String url="https://www.ithome.com/"+now+"/990/101.htm",scope=HotCandidate.hash("synthetic-scope"),namespace=null,session=null;java.io.File created=null;
        try{
            JSONObject captured;try(java.io.InputStream input=test.getContext().getAssets().open("bbc-chinese-20261026/bbc-article-0.json")){captured=new JSONObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
            JSONObject props=captured.getJSONObject("props").getJSONObject("pageProps"),metadata=props.getJSONObject("pageData").getJSONObject("metadata");String bbcUrl="https://www.bbc.com"+props.getString("id");
            Document bbc=SourceParser.article(Source.BBC,"<script id=__NEXT_DATA__ type=application/json>"+captured+"</script>",bbcUrl);
            check(bbc.hasContent()&&bbc.sourceModifiedAt==metadata.getLong("lastPublished")&&ArticleUpdateEvidence.VERSION.equals(bbc.sourceUpdateEvidence),"captured public BBC article exposes identity-bound matching revision fields on Android");
            metadata.getJSONObject("atiAnalytics").put("timeUpdated","2024-01-01T00:00:00Z");bbc=SourceParser.article(Source.BBC,"<script id=__NEXT_DATA__ type=application/json>"+captured+"</script>",bbcUrl);
            check(bbc.hasContent()&&!bbc.sourceUnavailable&&bbc.sourceModifiedAt==0,"conflicting publisher revision metadata keeps readable BBC prose while withholding progress evidence");
            long old=now-4000,latest=now-1000;Document before=SourceParser.article(Source.ITHOME,html(url,old,false),url);before.cachedAt=now-3000;
            check(before.hasContent()&&before.sourceModifiedAt==old&&ArticleUpdateEvidence.VERSION.equals(before.sourceUpdateEvidence),"actual Android unified news parser retains exact canonical publisher modification time");
            Document after=SourceParser.article(Source.ITHOME,html(url,latest,true),url);after.cachedAt=now;
            ArticleProgressEvidence.Snapshot a=ArticleProgressEvidence.capture(Source.ITHOME,before,scope,now),b=ArticleProgressEvidence.capture(Source.ITHOME,after,scope,now);
            check(a!=null&&b!=null&&ArticleProgressEvidence.verifiedProgress(a,b,now-2000,now)==latest,"actual Android shingles admit a substantial explicitly marked source revision");
            Document clockOnly=SourceParser.article(Source.ITHOME,html(url,latest,false),url);clockOnly.cachedAt=now;
            check(ArticleProgressEvidence.verifiedProgress(a,ArticleProgressEvidence.capture(Source.ITHOME,clockOnly,scope,now),now-2000,now)==0,"publisher clock changes without new prose cannot bypass cooldown");
            check(ArticleProgressEvidence.verifiedProgress(a,b,now-500,now)==0,"a revision predating most recent real presentation cannot reenter again");
            check(ArticleProgressEvidence.verifiedProgress(a,ArticleProgressEvidence.capture(Source.ITHOME,after,HotCandidate.hash("another-synthetic-session"),now),now-2000,now)==0,"another session scope cannot inherit body progress");
            check(ArticleProgressEvidence.verifiedProgress(ArticleProgressEvidence.Snapshot.read(a.json(),now),ArticleProgressEvidence.Snapshot.read(b.json(),now),now-2000,now)==latest,"Android JSON hexadecimal hash-only persistence preserves the decision");
            ArticleDiskCache disk=new ArticleDiskCache(test.getTargetContext());namespace=disk.namespace();session=ArticleDiskCache.session(url);created=new java.io.File(test.getTargetContext().getCacheDir(),"article-bodies-v1/"+HotCandidate.hash(namespace+"\n"+session+"\n"+url)+".json");
            disk.enqueue(after,namespace,session,now);after.sourceModifiedAt=0;after.sourceUpdateEvidence="";
            ArticleDiskCache.Hit hit=null;long until=android.os.SystemClock.elapsedRealtime()+10000;while(hit==null&&android.os.SystemClock.elapsedRealtime()<until){hit=disk.read(url,namespace,session,false,now+1);if(hit==null)Thread.sleep(20);}
            check(hit!=null&&hit.document.sourceModifiedAt==latest&&ArticleUpdateEvidence.VERSION.equals(hit.document.sourceUpdateEvidence),"actual asynchronous disk cache freezes versioned source evidence before caller mutation");
            if(hit!=null){hit.document.cachedAt=hit.at;check(ArticleProgressEvidence.verifiedProgress(a,ArticleProgressEvidence.capture(Source.ITHOME,hit.document,scope,now+1),now-2000,now+1)==latest,"disk-restored body produces the same bounded evidence as fresh parser output");}
            JSONObject legacy=ReadingHistory.encode(before);legacy.remove("sourceUpdateEvidence");check(ReadingHistory.decode(legacy).sourceModifiedAt==0,"legacy body cache lacks evidence rather than receiving a synthetic update time");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{if(created!=null&&created.exists())check(created.delete(),"only this probe's generated body cache entry is removed");}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
