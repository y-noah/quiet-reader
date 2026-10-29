package app.quietreader;
import app.quietreader.discovery.*;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class SemanticOrderTest {
    @Test public void quickExitIsWeakNegativeAndNeverOverridesExplicitContentChoice()throws Exception{
        FeedStore.Row item=row(1,"标题吸引但内容不满意"),other=row(2,"其他内容");String text=item.candidate.modelText();
        JSONArray events=new JSONArray().put(feedback("QUICK_EXIT",text));SemanticOrder.Profile profile=new SemanticOrder.Profile(events);
        List<SemanticOrder.Scored> ranked=SemanticOrder.rank(List.of(item,other),profile,Map.of(SemanticOrder.key(text),axis(0)));
        SemanticOrder.Scored changed=ranked.stream().filter(s->s.row.eventId.equals(item.eventId)).findFirst().get();
        assertEquals(1,changed.category);assertEquals(-0.245,changed.score,0.00001);
        events.put(feedback("MORE",text));events.put(feedback("QUICK_EXIT",text));
        assertTrue(new SemanticOrder.Profile(events).samples.get(0).positive);assertFalse(new SemanticOrder.Profile(events).samples.get(0).weak);
        assertNotEquals(profile.signature,new SemanticOrder.Profile(new JSONArray().put(feedback("READ",text))).signature);
    }
    @Test public void exactGuardRetainsOlderDislikeWithoutExpandingModelBudget()throws Exception{
        FeedStore.Row old=row(1,"较早的明确少看"),unknown=row(2,"未知内容");JSONArray events=new JSONArray().put(feedback("LESS",old.candidate.modelText()));for(int n=0;n<40;n++)events.put(feedback("LESS","其他负样本"+n));
        SemanticOrder.Profile profile=new SemanticOrder.Profile(events);assertEquals(32,profile.samples.size());assertTrue(profile.samples.stream().noneMatch(sample->sample.key.equals(SemanticOrder.key(old.candidate.modelText()))));
        assertEquals(2,SemanticOrder.rank(List.of(old,unknown),profile,Collections.emptyMap()).get(1).category);
        events.put(feedback("MORE",old.candidate.modelText()));SemanticOrder.Profile changed=new SemanticOrder.Profile(events);assertNotEquals(profile.signature,changed.signature);assertEquals(old.eventId,SemanticOrder.rank(List.of(old,unknown),changed,Collections.emptyMap()).get(0).row.eventId);
    }
    @Test public void exactDislikeNeedsNoVectorAndCannotBePromotedBySourcePreference()throws Exception{
        FeedStore.Row disliked=row(1,"明确少看的内容"),unknown=row(2,"尚无偏好的内容");
        JSONArray events=new JSONArray().put(feedback("LESS",disliked.candidate.modelText())).put(new JSONObject().put("action","MORE").put("scope","source").put("target",disliked.candidate.source.name()));
        SemanticOrder.Profile profile=new SemanticOrder.Profile(events);
        for(Map<String,float[]> vectors:Arrays.asList(Collections.<String,float[]>emptyMap(),Collections.singletonMap(SemanticOrder.key("未参与的样本"),axis(0)))){
            List<SemanticOrder.Scored> ranked=SemanticOrder.rank(List.of(disliked,unknown),profile,vectors);
            assertEquals(unknown.eventId,ranked.get(0).row.eventId);assertEquals(2,ranked.get(1).category);assertEquals("根据你的少看反馈调整",ranked.get(1).row.reason);assertTrue(SemanticOrder.rerankGroups(ranked).isEmpty());
            assertEquals(disliked.eventId,SemanticOrder.fuse(ranked,Collections.emptyMap(),profile.platforms).get(1).eventId);
        }
    }
    @Test public void laterExplicitMoreClearsExactDislikeButWeakSaveDoesNotWithoutVectors()throws Exception{
        FeedStore.Row disliked=row(1,"有明确反馈的内容"),unknown=row(2,"其他内容");JSONArray events=new JSONArray().put(feedback("LESS",disliked.candidate.modelText())).put(feedback("SAVED",disliked.candidate.modelText()));
        assertEquals(2,SemanticOrder.rank(List.of(disliked,unknown),new SemanticOrder.Profile(events),Collections.emptyMap()).get(1).category);
        events.put(feedback("MORE",disliked.candidate.modelText()));List<SemanticOrder.Scored> restored=SemanticOrder.rank(List.of(disliked,unknown),new SemanticOrder.Profile(events),Collections.emptyMap());assertEquals(disliked.eventId,restored.get(0).row.eventId);assertTrue(restored.stream().allMatch(value->value.category==1));
    }
    @Test public void saveIsWeakerAndCannotOverrideExplicitNegative()throws Exception{
        SemanticOrder.Profile profile=new SemanticOrder.Profile(new JSONArray().put(feedback("LESS","足球")).put(feedback("SAVED","足球")).put(feedback("SAVED","天文")));
        assertFalse(profile.samples.stream().filter(s->s.text.equals("足球")).findFirst().get().positive);
        Map<String,float[]> vectors=new HashMap<>();vectors.put(SemanticOrder.key("足球"),axis(0));vectors.put(SemanticOrder.key("天文"),axis(1));
        List<FeedStore.Row> rows=Arrays.asList(row(1,"足球"),row(2,"天文"));
        List<SemanticOrder.Scored> weak=SemanticOrder.rank(rows,profile,vectors);
        assertEquals(2,weak.get(1).category);assertEquals(0.75,weak.get(0).score,0.00001);
        SemanticOrder.Profile explicit=new SemanticOrder.Profile(new JSONArray().put(feedback("MORE","天文")));
        assertEquals(1,SemanticOrder.rank(Collections.singletonList(rows.get(1)),explicit,vectors).get(0).score,0.00001);
        assertNotEquals(profile.signature,explicit.signature);
    }
    @Test public void manySavedSamplesDoNotEvictExplicitInterests()throws Exception{
        JSONArray events=new JSONArray().put(feedback("MORE","长期兴趣"));for(int n=0;n<40;n++)events.put(feedback("SAVED","临时内容"+n));
        SemanticOrder.Profile profile=new SemanticOrder.Profile(events);
        assertEquals(8,profile.samples.stream().filter(s->s.weak).count());
        assertTrue(profile.samples.stream().anyMatch(s->!s.weak&&s.text.equals("长期兴趣")));
    }
    private float[] axis(int index){float[] v=new float[512];v[index]=1;return v;}
    @Test public void readsAreWeakerThanSavesAndExplicitNegativeAlwaysWins()throws Exception{
        SemanticOrder.Profile read=new SemanticOrder.Profile(new JSONArray().put(feedback("READ","天文")));
        Map<String,float[]> vectors=Collections.singletonMap(SemanticOrder.key("天文"),axis(0));
        assertEquals(0.65,SemanticOrder.rank(Collections.singletonList(row(1,"天文")),read,vectors).get(0).score,0.0001);
        SemanticOrder.Profile saved=new SemanticOrder.Profile(new JSONArray().put(feedback("SAVED","天文")).put(feedback("READ","天文")));
        assertFalse(saved.samples.get(0).read);
        SemanticOrder.Profile negative=new SemanticOrder.Profile(new JSONArray().put(feedback("LESS","天文")).put(feedback("READ","天文")));
        assertFalse(negative.samples.get(0).positive);
        JSONArray events=new JSONArray();for(int n=0;n<20;n++)events.put(feedback("READ","话题"+n));assertEquals(4,new SemanticOrder.Profile(events).samples.size());
    }
    private JSONObject feedback(String action,String text)throws Exception{return new JSONObject().put("id",action+text).put("action",action).put("sample",text);}
    private FeedStore.Row row(int n,String text){HotCandidate c=new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/"+n,text,"","",false,n,null,1);return new FeedStore.Row(c.id,c,"中性顺序");}
    @Test public void independentPositiveInterestsAreNotAveragedTogether()throws Exception{
        SemanticOrder.Profile p=new SemanticOrder.Profile(new JSONArray().put(feedback("MORE","天文")).put(feedback("MORE","足球")));
        List<FeedStore.Row> rows=Arrays.asList(row(1,"其他"),row(2,"足球"),row(3,"天文"));Map<String,float[]> vectors=new HashMap<>();vectors.put(SemanticOrder.key("其他"),axis(2));vectors.put(SemanticOrder.key("足球"),axis(1));vectors.put(SemanticOrder.key("天文"),axis(0));
        List<SemanticOrder.Scored> result=SemanticOrder.rank(rows,p,vectors);assertEquals(0,result.get(0).category);assertEquals(0,result.get(1).category);assertEquals("其他",result.get(2).row.candidate.title);assertEquals(2,SemanticOrder.rerankGroups(result).size());
    }
    @Test public void duplicateFeedbackDoesNotMultiplyWeightAndLatestPolarityWins()throws Exception{
        SemanticOrder.Profile p=new SemanticOrder.Profile(new JSONArray().put(feedback("MORE","足球")).put(feedback("MORE","足球")).put(feedback("LESS","足球")));
        assertEquals(1,p.samples.size());assertFalse(p.samples.get(0).positive);
        Map<String,float[]> vectors=Collections.singletonMap(SemanticOrder.key("足球"),axis(0));List<SemanticOrder.Scored> result=SemanticOrder.rank(Arrays.asList(row(1,"足球"),row(2,"未知")),p,vectors);assertEquals("未知",result.get(0).row.candidate.title);assertEquals(2,result.get(1).category);
    }
    @Test public void partialRerankingDoesNotPromoteUnscoredItemsOrMixQueryScales()throws Exception{
        SemanticOrder.Profile p=new SemanticOrder.Profile(new JSONArray().put(feedback("MORE","天文")));
        Map<String,float[]> vectors=new HashMap<>();for(String text:new String[]{"天文","甲","乙","丙"})vectors.put(SemanticOrder.key(text),axis(0));
        List<SemanticOrder.Scored> ranked=SemanticOrder.rank(Arrays.asList(row(1,"甲"),row(2,"乙"),row(3,"丙")),p,vectors);
        Map<String,Float> fine=new HashMap<>();fine.put(ranked.get(0).row.eventId,-2f);fine.put(ranked.get(2).row.eventId,3f);
        List<FeedStore.Row> fused=SemanticOrder.fuse(ranked,Collections.singletonMap("天文",fine));assertEquals("丙",fused.get(0).candidate.title);assertEquals("乙",fused.get(1).candidate.title);assertEquals("甲",fused.get(2).candidate.title);
    }
    @Test public void nonExactNeutralMatchesStillDriveRetrievalAndCanReachReranker()throws Exception{
        SemanticOrder.Profile profile=new SemanticOrder.Profile(new JSONArray().put(feedback("MORE","软件开发")));
        Map<String,float[]> vectors=new HashMap<>();vectors.put(SemanticOrder.key("软件开发"),axis(0));
        float[] near=axis(2);near[0]=0.5f;near[2]=(float)Math.sqrt(0.75);float[] far=axis(2);far[0]=0.15f;far[2]=(float)Math.sqrt(1-0.15*0.15);
        vectors.put(SemanticOrder.key("较相关的新话题"),near);vectors.put(SemanticOrder.key("较远话题"),far);
        List<SemanticOrder.Scored> ranked=SemanticOrder.rank(Arrays.asList(row(1,"较远话题"),row(2,"较相关的新话题"),row(3,"尚无向量")),profile,vectors);
        assertEquals("较相关的新话题",ranked.get(0).row.candidate.title);assertTrue(ranked.stream().allMatch(value->value.category==1));
        assertEquals("按兴趣相关性初筛",ranked.get(0).row.reason);assertEquals(2,SemanticOrder.rerankGroups(ranked).get("软件开发").size());
        Map<String,Float> logits=new HashMap<>();logits.put(ranked.get(0).row.eventId,-2f);logits.put(ranked.get(1).row.eventId,3f);
        List<FeedStore.Row> refined=SemanticOrder.fuse(ranked,Collections.singletonMap("软件开发",logits));assertEquals("较远话题",refined.get(0).candidate.title);assertEquals("尚无向量",refined.get(2).candidate.title);
    }
    @Test public void fineScoresCannotPullNeutralAcrossConfidentOrExplicitNegativeBuckets()throws Exception{
        SemanticOrder.Profile profile=new SemanticOrder.Profile(new JSONArray().put(feedback("MORE","技术")).put(feedback("LESS","八卦")));
        Map<String,float[]> vectors=new HashMap<>();vectors.put(SemanticOrder.key("技术"),axis(0));vectors.put(SemanticOrder.key("八卦"),axis(1));vectors.put(SemanticOrder.key("明确相关"),axis(0));
        float[] neutral=axis(2);neutral[0]=0.5f;neutral[2]=(float)Math.sqrt(0.75);vectors.put(SemanticOrder.key("待确认"),neutral);
        List<SemanticOrder.Scored> ranked=SemanticOrder.rank(Arrays.asList(row(1,"待确认"),row(2,"八卦"),row(3,"明确相关")),profile,vectors);
        assertEquals(0,ranked.get(0).category);assertEquals(1,ranked.get(1).category);assertEquals(2,ranked.get(2).category);
        List<SemanticOrder.Scored> group=SemanticOrder.rerankGroups(ranked).get("技术");assertEquals(1,group.size());assertEquals(0,group.get(0).category);
        Map<String,Float> logits=new HashMap<>();for(SemanticOrder.Scored item:ranked)logits.put(item.row.eventId,item.category==0?-100f:100f);
        List<FeedStore.Row> refined=SemanticOrder.fuse(ranked,Collections.singletonMap("技术",logits));assertEquals("明确相关",refined.get(0).candidate.title);assertEquals("待确认",refined.get(1).candidate.title);assertEquals("八卦",refined.get(2).candidate.title);
    }
    @Test public void positiveFineQueryCannotUndoStrongerNegativeEvidence()throws Exception{
        SemanticOrder.Profile profile=new SemanticOrder.Profile(new JSONArray().put(feedback("MORE","婚姻家庭" )).put(feedback("LESS","明星绯闻")));
        Map<String,float[]> vectors=new HashMap<>();vectors.put(SemanticOrder.key("婚姻家庭"),axis(0));vectors.put(SemanticOrder.key("明星绯闻"),axis(1));
        float[] conflict=axis(2);conflict[0]=0.43f;conflict[1]=0.53f;conflict[2]=(float)Math.sqrt(1-0.43*0.43-0.53*0.53);vectors.put(SemanticOrder.key("明星婚恋"),conflict);
        float[] related=axis(2);related[0]=0.5f;related[1]=0.1f;related[2]=(float)Math.sqrt(0.74);vectors.put(SemanticOrder.key("社会婚姻统计"),related);vectors.put(SemanticOrder.key("普通家庭问题"),related);
        List<SemanticOrder.Scored> coarse=SemanticOrder.rank(Arrays.asList(row(1,"明星婚恋"),row(2,"社会婚姻统计"),row(3,"普通家庭问题")),profile,vectors);
        assertEquals(1,coarse.get(2).category);assertFalse(coarse.get(2).positiveLead);assertEquals(2,SemanticOrder.rerankGroups(coarse).get("婚姻家庭").size());
        Map<String,Float> logits=new HashMap<>();logits.put(coarse.get(0).row.eventId,-10f);logits.put(coarse.get(1).row.eventId,1f);logits.put(coarse.get(2).row.eventId,100f);
        List<FeedStore.Row> finalRows=SemanticOrder.fuse(coarse,Collections.singletonMap("婚姻家庭",logits));assertEquals("明星婚恋",finalRows.get(2).candidate.title);assertEquals("普通家庭问题",finalRows.get(0).candidate.title);
    }
}
