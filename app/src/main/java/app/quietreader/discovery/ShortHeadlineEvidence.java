package app.quietreader.discovery;

import app.quietreader.Models.Source;
import java.util.*;
import java.util.regex.Pattern;

/** Exact short cross-source headlines only; no semantic or missing-fact inference. */
final class ShortHeadlineEvidence {
    private static final long DAY=86_400_000L,OFFSET=8L*3600_000;
    private static final Pattern GENERIC=Pattern.compile("这个|这样|这些|那些|怎么|什么|大家|网友|好消息|大消息|最新|热搜|今日|今天|[吗呢]$");
    private ShortHeadlineEvidence(){}
    static Map<String,String> resolve(List<FeedStore.Row> rows){
        Map<String,List<HotCandidate>> families=new LinkedHashMap<>();
        for(FeedStore.Row row:rows){
            HotCandidate c=row.candidate;
            if(c.source==Source.V2EX||c.video||c.observedAt<=0||c.observedAt>Long.MAX_VALUE-OFFSET)continue;
            String title=HeadlineIdentity.core(c.title);int[] points=title.codePoints().toArray();
            if(points.length<8||points.length>9||GENERIC.matcher(title).find())continue;
            Set<Integer> distinct=new HashSet<>();boolean han=true;
            for(int point:points){han&=Character.UnicodeScript.of(point)==Character.UnicodeScript.HAN;distinct.add(point);}
            if(!han||distinct.size()<6)continue;
            long day=Math.floorDiv(c.observedAt+OFFSET,DAY);
            if(c.publishedAt!=null&&(c.publishedAt<=0||c.publishedAt>c.observedAt+600_000||Math.floorDiv(c.publishedAt+OFFSET,DAY)!=day))continue;
            families.computeIfAbsent(title+"\n"+day,k->new ArrayList<>()).add(c);
        }
        Map<String,String> result=new HashMap<>();
        for(Map.Entry<String,List<HotCandidate>> family:families.entrySet()){
            List<HotCandidate> entries=family.getValue();if(entries.size()<2)continue;
            Set<Source> sources=EnumSet.noneOf(Source.class);boolean unambiguous=true;
            for(HotCandidate c:entries){
                // Multiple independent posts on one platform or a substantive
                // excerpt require the existing stronger evidence/verifier path.
                if(!sources.add(c.source)||!c.snippet.isEmpty()&&!HeadlineIdentity.core(c.snippet).equals(HeadlineIdentity.core(c.title))){unambiguous=false;break;}
            }
            if(!unambiguous)continue;
            String key="group-"+HotCandidate.hash("short-cross-source-title-v1\n"+family.getKey());
            for(HotCandidate c:entries)result.put(c.id,key);
        }
        return result;
    }
}
