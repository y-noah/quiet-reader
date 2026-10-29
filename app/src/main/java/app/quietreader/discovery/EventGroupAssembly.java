package app.quietreader.discovery;

import java.util.*;
import org.json.*;

/** Joint-policy grouping assembly, not a universal event-equivalence classifier.
 * Input rows must already pass source/language eligibility. Work is opt-in:
 * no model runs, no cache writes, and no existing snapshot is mutated here. */
public final class EventGroupAssembly {
    public static final String VERSION="joint-event-assembly-v3-confidence-first";
    // Provisional conservative policy; low-scoring paraphrases remain separate.
    private static final float MIN_BIDIRECTIONAL_LOGIT=7.0f;
    private EventGroupAssembly(){}
    public static final class ScoredPair {
        public final EventVerificationPlan.Task task;
        public final float forward,backward;
        public final boolean completeInputs;
        public ScoredPair(EventVerificationPlan.Task task,float forward,float backward){this(task,forward,backward,false);}
        public ScoredPair(EventVerificationPlan.Task task,float forward,float backward,boolean completeInputs){this.task=Objects.requireNonNull(task);this.forward=forward;this.backward=backward;this.completeInputs=completeInputs;}
    }
    private static String pairKey(HotCandidate a,HotCandidate b){return a.id.compareTo(b.id)<0?a.id+":"+b.id:b.id+":"+a.id;}
    private static boolean separated(FeedStore.Row row,Set<String> targets){
        if(targets.contains(row.eventId))return true;
        for(HotCandidate member:row.entries)if(targets.contains(member.id)||!Collections.disjoint(targets,EventGroups.aliases(member)))return true;
        return false;
    }
    private static boolean permitted(HotCandidate a,HotCandidate b,Map<String,ScoredPair> scores,String revision){
        if(EventClaimEvidence.evaluate(a,b)!=EventClaimEvidence.Decision.CANDIDATE)return false;
        ScoredPair scored=scores.get(pairKey(a,b));
        return scored!=null&&scored.completeInputs&&revision.equals(scored.task.modelRevision)&&scored.task.matches(a,b)
            &&Float.isFinite(scored.forward)&&Float.isFinite(scored.backward)
            &&Math.min(scored.forward,scored.backward)>=MIN_BIDIRECTIONAL_LOGIT;
    }
    private static final class Join {
        final int left,right;final float confidence;
        Join(int left,int right,float confidence){this.left=left;this.right=right;this.confidence=confidence;}
    }
    private static int root(int[] parent,int node){while(parent[node]!=node){parent[node]=parent[parent[node]];node=parent[node];}return node;}
    private static float confidence(List<HotCandidate> a,List<HotCandidate> b,Map<String,Float> verified){
        float minimum=Float.POSITIVE_INFINITY;
        for(HotCandidate left:a)for(HotCandidate right:b){Float score=verified.get(pairKey(left,right));if(score==null)return Float.NEGATIVE_INFINITY;minimum=Math.min(minimum,score);}
        return minimum;
    }
    public static List<FeedStore.Row> apply(List<FeedStore.Row> ordered,JSONArray feedback,List<ScoredPair> scored,String modelRevision){
        if(modelRevision==null||modelRevision.trim().isEmpty())throw new IllegalArgumentException("Model revision required");
        if(scored.size()>EventVerificationPlan.MAX_PAIRS)throw new IllegalArgumentException("Too many verification pairs");
        // Reapply captured member feedback before any new joins. Flattening also
        // makes an explicit split work on a previously assembled input row.
        List<FeedStore.Row> flat=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(FeedStore.Row row:ordered){
            List<HotCandidate> members=new ArrayList<>(row.entries);
            // A source-filtered or restored row may choose a member that is not
            // entries[0]. Preserve that reader choice when rebuilding buckets.
            members.sort(Comparator.comparingInt(member->member.id.equals(row.candidate.id)?0:1));
            for(HotCandidate member:members)if(seen.add(member.id))flat.add(new FeedStore.Row(member.id,member,row.reason));
        }
        List<FeedStore.Row> base=EventGroups.group(flat,feedback);
        Set<String> separate=new HashSet<>();
        for(int n=0;n<feedback.length();n++){JSONObject f=feedback.optJSONObject(n);if(f!=null&&"event".equals(f.optString("scope"))&&"SEPARATE".equals(f.optString("action")))separate.addAll(EventTargets.expand(f.optString("target")));}
        Map<String,ScoredPair> pairs=new HashMap<>();Set<String> ambiguous=new HashSet<>();
        for(ScoredPair pair:scored){String key=pairKey(pair.task.left,pair.task.right);ScoredPair previous=pairs.putIfAbsent(key,pair);
            // Conflicting duplicate deliveries never promote an earlier score.
            if(previous!=null&&(!previous.task.evidenceKey.equals(pair.task.evidenceKey)||Float.compare(previous.forward,pair.forward)!=0||Float.compare(previous.backward,pair.backward)!=0||previous.completeInputs!=pair.completeInputs))ambiguous.add(key);}
        for(String key:ambiguous)pairs.remove(key);
        Map<String,Integer> rowOf=new HashMap<>();Map<String,HotCandidate> current=new HashMap<>();
        int[] parent=new int[base.size()];List<List<HotCandidate>> members=new ArrayList<>();boolean[] split=new boolean[base.size()];
        for(int i=0;i<base.size();i++){parent[i]=i;members.add(new ArrayList<>(base.get(i).entries));split[i]=separated(base.get(i),separate);for(HotCandidate member:base.get(i).entries){rowOf.put(member.id,i);current.put(member.id,member);}}
        // Revalidate every delivered pair against current source facts once.
        // Later complete-link joins are bounded map lookups, not repeated regex work.
        Map<String,Float> verified=new HashMap<>();Set<Long> edges=new HashSet<>();
        for(ScoredPair pair:pairs.values()){
            HotCandidate a=current.get(pair.task.left.id),b=current.get(pair.task.right.id);
            if(a==null||b==null||!permitted(a,b,pairs,modelRevision))continue;
            verified.put(pairKey(a,b),Math.min(pair.forward,pair.backward));
            int left=rowOf.get(a.id),right=rowOf.get(b.id);if(left>right){int swap=left;left=right;right=swap;}
            if(left!=right&&!split[left]&&!split[right])edges.add(((long)left<<32)|(right&0xffffffffL));
        }
        List<Join> joins=new ArrayList<>();
        for(long edge:edges){int left=(int)(edge>>>32),right=(int)edge;float value=confidence(members.get(left),members.get(right),verified);if(value>=MIN_BIDIRECTIONAL_LOGIT)joins.add(new Join(left,right,value));}
        // At most24 initial edges and24 joins. Recompute current cluster
        // strength after each join; sorting singleton edges only once lets an
        // expanded, weaker cluster preempt a stronger remaining pair.
        for(int round=0;round<joins.size();round++){
            Join best=null;
            for(Join join:joins){
                int left=root(parent,join.left),right=root(parent,join.right);if(left==right)continue;
                if(left>right){int swap=left;left=right;right=swap;}
                // Complete-link validation remains mandatory: A~B~C alone
                // cannot bridge a missing or weak A~C (including V2EX threads).
                float value=confidence(members.get(left),members.get(right),verified);if(value<MIN_BIDIRECTIONAL_LOGIT)continue;
                if(best==null||value>best.confidence||value==best.confidence&&(left<best.left||left==best.left&&right<best.right))best=new Join(left,right,value);
            }
            if(best==null)break;
            parent[best.right]=best.left;members.get(best.left).addAll(members.get(best.right));members.get(best.right).clear();
        }
        Map<Integer,List<HotCandidate>> assembled=new LinkedHashMap<>();
        for(int i=0;i<base.size();i++)assembled.computeIfAbsent(root(parent,i),ignored->new ArrayList<>()).addAll(base.get(i).entries);
        List<FeedStore.Row> result=new ArrayList<>();
        for(Map.Entry<Integer,List<HotCandidate>> group:assembled.entrySet()){
            // Presentation order and representative still come from the first
            // original card. Added sources never add heat or ranking weight.
            FeedStore.Row first=base.get(group.getKey());result.add(new FeedStore.Row(first.eventId,first.candidate,first.reason,group.getValue()));
        }
        return Collections.unmodifiableList(result);
    }
}
