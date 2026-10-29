package app.quietreader;

import android.app.*;
import android.os.*;
import app.quietreader.ai.*;
import app.quietreader.discovery.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Diagnostic only: neither transformed input nor resulting scores enter production caches. */
final class EventInputAblationProbe {
    private final Instrumentation test;
    private final boolean frozenTrailing;
    private final String frozenFixture,outputName;
    EventInputAblationProbe(Instrumentation test){this(test,false);}
    EventInputAblationProbe(Instrumentation test,boolean frozenTrailing){this(test,frozenTrailing,null,null);}
    EventInputAblationProbe(Instrumentation test,String fixture,String output){this(test,true,fixture,output);}
    private EventInputAblationProbe(Instrumentation test,boolean frozenTrailing,String fixture,String output){this.test=test;this.frozenTrailing=frozenTrailing;frozenFixture=fixture;outputName=output;}
    private static final Pattern COUNT=Pattern.compile("(?:(?<=连续)|(?<=第))(?:[二三四五六七八九]十[一二三四五六七八九]?|十[一二三四五六七八九]?|[零〇一二两三四五六七八九])(?=年|届|次)");
    private static int digit(char value){return value=='两'?2:value=='〇'?0:"零一二三四五六七八九".indexOf(value);}
    private static String input(String original,String mode){
        if(mode.equals("raw"))return original;
        Matcher matcher=COUNT.matcher(original);StringBuffer out=new StringBuffer();
        while(matcher.find()){String word=matcher.group();int ten=word.indexOf('十'),value=ten<0?digit(word.charAt(0)):(ten==0?1:digit(word.charAt(0)))*10;if(ten>=0&&ten+1<word.length())value+=digit(word.charAt(ten+1));matcher.appendReplacement(out,Integer.toString(value));}matcher.appendTail(out);
        String value=out.toString();if(mode.equals("digits"))return value;
        return value.replaceFirst("^(?:如何|怎样)(?:看待|评价)","").replaceFirst("[，,](?:如何|怎样)(?:看待|评价)(?:这一|这个)(?:结果|事件|决定)[？?]?$","");
    }
    private static void add(JSONArray pairs,String label,String left,String right)throws JSONException{pairs.put(new JSONObject().put("scope","synthetic-holdout").put("label",label).put("left",left).put("right",right));}
    void run(){StringBuilder report=new StringBuilder();int checks=0,failures=0;
        try{
            JSONObject source;try(InputStream in=test.getContext().getAssets().open(frozenFixture!=null?frozenFixture:frozenTrailing?"fixtures/trailing-question-ablation-20261029.json":"fixtures/observed-event-fresh-pairs-20261026.json")){source=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            JSONArray pairs=new JSONArray(),observed=source.getJSONArray("pairs");
            if(frozenTrailing)pairs=observed;
            else{
            for(int n=0;n<observed.length();n++){JSONObject pair=observed.getJSONObject(n);pairs.put(new JSONObject().put("scope","observed-development").put("label",pair.getString("label")).put("left",pair.getJSONObject("left").getString("title")).put("right",pair.getJSONObject("right").getString("title")));}
            add(pairs,"same","云帆科技连续第三年发布可持续发展报告","云帆科技发布可持续发展报告，连续第3年对外披露");
            add(pairs,"same","星河集团董事长人选公布，李明出任董事长","李明获任星河集团董事长");
            add(pairs,"same","蓝海集团总经理人选公布，周宁就任总经理","周宁出任蓝海集团总经理");
            add(pairs,"same","远山科技道歉并公布补偿方案","如何看待远山科技致歉并宣布补偿方案？");
            add(pairs,"different","星河集团任命李明出任董事长","星河集团任命王强出任董事长");
            add(pairs,"different","星河集团任命李明出任董事长","蓝海集团任命李明出任董事长");
            add(pairs,"different","青河市发现古代遗址","青河市发现恐龙化石");
            add(pairs,"different","天河科技发布折叠屏手机","天河科技发布智能运动手表");
            add(pairs,"different","云帆科技连续五年发布经营报告","云帆科技连续第6年发布经营报告");
            add(pairs,"different","星河集团李明计划出任董事长","星河集团李明出任董事长");
            }
            ModelFiles files=ModelFiles.prepare(test.getTargetContext());JSONArray output=new JSONArray();long at=System.currentTimeMillis();
            try(LocalModelEngine engine=new LocalModelEngine(files.directory(),files.manifest())){
                for(String mode:frozenTrailing?List.of("raw",frozenFixture==null?"frozen-trailing":"frozen-focus"):List.of("raw","digits","framing")){
                    int admittedSame=0,admittedDifferent=0,admittedUncertain=0;
                    for(int n=0;n<pairs.length();n++){
                        JSONObject pair=pairs.getJSONObject(n);String left=pair.getString("left"),right=pair.getString("right");
                        HotCandidate a=new HotCandidate(Models.Source.ZHIHU,"https://example.org/left/"+n,left,"","",false,1,null,at),b=new HotCandidate(Models.Source.WEIBO,"https://example.org/right/"+n,right,"","",false,1,null,at);
                        EventClaimEvidence.Decision admission=EventClaimEvidence.evaluate(a,b);String l=mode.startsWith("frozen-")?pair.getString("inputLeft"):input(left,mode),r=mode.startsWith("frozen-")?pair.getString("inputRight"):input(right,mode);
                        LocalModelEngine.RerankResult forward=engine.rerankResult(l,r),backward=engine.rerankResult(r,l);
                        boolean complete=forward.complete&&backward.complete,passes=complete&&Math.min(forward.logit,backward.logit)>=7,permitted=admission==EventClaimEvidence.Decision.CANDIDATE&&passes;
                        if(!Float.isFinite(forward.logit)||!Float.isFinite(backward.logit))throw new IllegalStateException("Nonfinite score");
                        if(permitted){switch(pair.getString("label")){case "same":admittedSame++;break;case "different":admittedDifferent++;break;default:admittedUncertain++;}}
                        output.put(new JSONObject().put("index",n).put("mode",mode).put("scope",pair.getString("scope")).put("label",pair.getString("label")).put("left",left).put("right",right).put("inputLeft",l).put("inputRight",r).put("admission",admission.name()).put("forward",forward.logit).put("backward",backward.logit).put("complete",complete).put("modelPass",passes).put("wouldPermit",permitted));
                    }
                    report.append("DIAGNOSTIC ").append(mode).append(" permittedSame=").append(admittedSame).append(" permittedDifferent=").append(admittedDifferent).append(" permittedUncertain=").append(admittedUncertain).append('\n');checks++;
                }
            }
            JSONObject result=new JSONObject().put("schema",1).put("scope","input ablation only; no production merge/cache or population accuracy").put("pairs",pairs).put("outputs",output).put("claimVersion",EventClaimEvidence.VERSION).put("modelRevision",files.revision());
            try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),outputName!=null?outputName:frozenTrailing?"event-trailing-ablation.json":"event-input-ablation.json"))){out.write(result.toString().getBytes(StandardCharsets.UTF_8));}
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        report.append("checks=").append(checks).append(" failures=").append(failures).append("\nScope: diagnostic score comparisons, semantic false permits are reported above, not counted as execution failures.\n");Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
