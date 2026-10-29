package app.quietreader;

import android.app.*;
import android.os.Bundle;
import org.json.*;
import static app.quietreader.Models.*;

/** Actual Android parser/history/async cache; public metadata with synthetic body text. */
final class DiscussionReplyEvidenceProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    DiscussionReplyEvidenceProbe(Instrumentation test){this.test=test;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    void run(){java.io.File created=null;
        try{
            long now=System.currentTimeMillis();JSONObject fixture;
            try(java.io.InputStream input=test.getContext().getAssets().open("fixtures/v2ex-reply-metadata-20261028.json")){fixture=new JSONObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
            String url="https://www.v2ex.com/t/"+now;JSONArray rows=fixture.getJSONArray("replies");StringBuilder html=new StringBuilder("<div class=topic_content>受控主帖</div>");
            for(int n=0;n<rows.length();n++){JSONObject row=rows.getJSONObject(n);html.append("<div class=cell id='").append(row.getString("id")).append("'><span class=ago title='").append(row.getString("title")).append("'>2 days ago</span><div class=reply_content>受控回复正文</div></div>");}
            Document doc=SourceParser.article(Source.V2EX,html.toString(),url);long expected=java.time.Instant.parse("2026-10-26T01:37:39Z").toEpochMilli();
            check(doc.sections.size()==4&&doc.sections.get(0).sourceReplyAt==0,"actual Android parser keeps main post separate from three public reply identities");
            Section first=doc.sections.get(1);
            check(first.id.equals("v2ex-r_18219649")&&first.sourceReplyAt==expected&&DiscussionReplyEvidence.VERSION.equals(first.sourceReplyEvidence),"captured explicit offset is converted to exact UTC creation time");
            Document restored=ReadingHistory.decode(ReadingHistory.encode(doc));check(restored.sections.get(1).sourceReplyAt==expected&&restored.sections.get(1).sourceReplyEvidence.equals(first.sourceReplyEvidence),"Android history JSON preserves stable identity and versioned time");
            Document relative=SourceParser.article(Source.V2EX,html.toString().replace("2026-10-26 09:37:39 +08:00","刚刚"),url);check(relative.sections.get(1).sourceReplyAt==0,"relative time is withheld rather than converted from acquisition clock");
            check(ArticleProgressEvidence.capture(Source.V2EX,doc,app.quietreader.discovery.HotCandidate.hash("test-scope"),now)==null,"reply creation metadata alone cannot enter the news progress cooldown bypass");
            ArticleDiskCache disk=new ArticleDiskCache(test.getTargetContext());String namespace=disk.namespace(),session=ArticleDiskCache.session(url);
            created=new java.io.File(test.getTargetContext().getCacheDir(),"article-bodies-v1/"+app.quietreader.discovery.HotCandidate.hash(namespace+"\n"+session+"\n"+url)+".json");
            disk.enqueue(doc,namespace,session,now);first.sourceReplyAt=0;first.sourceReplyEvidence="";
            ArticleDiskCache.Hit hit=null;long until=android.os.SystemClock.elapsedRealtime()+10000;
            while(hit==null&&android.os.SystemClock.elapsedRealtime()<until){hit=disk.read(url,namespace,session,false,now+1);if(hit==null)Thread.sleep(20);}
            check(hit!=null&&hit.document.sections.get(1).sourceReplyAt==expected&&DiscussionReplyEvidence.VERSION.equals(hit.document.sections.get(1).sourceReplyEvidence),"async disk enqueue freezes reply metadata before caller mutation");
            JSONObject legacy=ReadingHistory.encode(restored);legacy.getJSONArray("sections").getJSONObject(1).remove("sourceReplyEvidence");check(ReadingHistory.decode(legacy).sections.get(1).sourceReplyAt==0,"old cached replies do not acquire invented timestamps");
            check(DocumentSize.serializationFits(restored,ArticleDiskCache.MAX_ENTRY),"versioned reply metadata remains included in bounded cache accounting");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{if(created!=null&&created.exists())check(created.delete(),"only this probe's own temporary body file is removed");}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle output=new Bundle();output.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,output);
    }
}
