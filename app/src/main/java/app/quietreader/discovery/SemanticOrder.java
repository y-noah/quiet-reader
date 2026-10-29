package app.quietreader.discovery;

import java.util.*;
import org.json.*;

/** Versioned local-example retrieval. Thresholds are provisional, not probabilities.
 * Independent nearest positive examples retain multiple interests; negatives are
 * scored separately and never turned into a positive instruction with a negation.
 */
public final class SemanticOrder {
    public static final String VERSION="examples-rrf-conflict-v8";
    public static final class Sample {
        public final String id,text,key;public final boolean positive,weak,read;
        Sample(String id,String text,boolean positive,boolean weak,boolean read){this.id=id;this.text=input(text);this.key=key(text);this.positive=positive;this.weak=weak;this.read=read;}
        public String query(){return HotCandidate.clean(text,240);}
    }
    public static final class Profile {
        private final String feedbackSnapshot;
        public final List<Sample> samples;public final String signature;
        private final Set<String> exactNegatives;
        public final PlatformPreference platforms;
        public Profile(JSONArray feedback){
            feedbackSnapshot=feedback.toString();
            platforms=new PlatformPreference(feedback);
            LinkedHashMap<String,Sample> unique=new LinkedHashMap<>();
            for(int n=0;n<feedback.length();n++){
                JSONObject f=feedback.optJSONObject(n);if(f==null)continue;String action=f.optString("action"),text=f.optString("sample");
                if("source".equals(f.optString("scope")))continue;
                if(!Arrays.asList("MORE","LESS","FOLLOW","SAVED","READ","QUICK_EXIT").contains(action)||text.trim().isEmpty())continue;
                boolean read=action.equals("READ")||action.equals("QUICK_EXIT");
                Sample sample=new Sample(f.optString("id"),text,!action.equals("LESS")&&!action.equals("QUICK_EXIT"),action.equals("SAVED")||read,read);Sample previous=unique.get(sample.key);
                if(previous!=null&&previous.read&&previous.positive&&!sample.positive&&sample.read)continue;
                if(previous!=null&&(!previous.weak&&sample.weak||previous.weak&&!previous.read&&sample.read))continue;unique.remove(sample.key);unique.put(sample.key,sample);
            }
            Set<String> knownNegatives=new HashSet<>();for(Sample sample:unique.values())if(!sample.positive&&!sample.weak)knownNegatives.add(sample.key);exactNegatives=Collections.unmodifiableSet(knownNegatives);
            List<Sample> newest=new ArrayList<>(unique.values());Collections.reverse(newest);List<Sample> chosen=new ArrayList<>();int positives=0,negatives=0;
            newest.sort(Comparator.comparingInt(sample->sample.read?2:sample.weak?1:0));int weak=0,reads=0;
            for(Sample sample:newest){if(sample.read&&reads++>=4)continue;if(sample.weak&&weak++>=8)continue;if(sample.positive?positives++<32:negatives++<32)chosen.add(sample);}
            samples=Collections.unmodifiableList(chosen);List<String> fingerprints=new ArrayList<>();for(Sample s:samples)fingerprints.add((s.read?"r":s.weak?"w":"e")+(s.positive?"+":"-")+s.key);for(String key:exactNegatives)fingerprints.add("exact-"+key);Collections.sort(fingerprints);signature=HotCandidate.hash(VERSION+"\n"+platforms.signature+"\n"+String.join("\n",fingerprints));
        }
        public boolean empty(){return samples.isEmpty();}
        public JSONArray feedback(){try{return new JSONArray(feedbackSnapshot);}catch(JSONException error){throw new IllegalStateException(error);}}
    }
    public static String input(String text){return HotCandidate.clean(text,1000);}
    public static String key(String text){return HotCandidate.hash("semantic-input-v1\n"+input(text));}
    public static final class Scored {
        public final FeedStore.Row row;public final Sample match;public final double score;public final int category;public final boolean positiveLead;
        Scored(FeedStore.Row row,Sample match,double score,int category,boolean positiveLead){this.row=row;this.match=match;this.score=score;this.category=category;this.positiveLead=positiveLead;}
    }
    public static List<Scored> rank(List<FeedStore.Row> neutral,Profile profile,Map<String,float[]> vectors){
        List<Scored> ranked=new ArrayList<>();Map<String,Integer> order=new HashMap<>();int index=0;
        for(FeedStore.Row row:neutral){order.put(row.eventId,index++);String key=key(row.candidate.modelText());float[] vector=vectors.get(key);Sample best=null;double positive=0,negative=0;
            // Known dislikes need neither vectors nor a place in the bounded model sample window.
            boolean exactNegative=profile.exactNegatives.contains(key);
            for(Sample sample:profile.samples){
                if(vector==null)continue;float[] example=vectors.get(sample.key);if(example==null)continue;double cosine=cosine(vector,example);
                double strength=cosine*(sample.read?(sample.positive?0.65:0.35):sample.weak?0.75:1);
                if(sample.positive&&strength>positive){positive=strength;best=sample;}else if(!sample.positive){negative=Math.max(negative,strength);}
            }
            int category=exactNegative||negative>=0.70&&negative>positive+0.04?2:best!=null&&positive>=0.60&&positive>negative+0.03?0:1;
            String reason=category==0?(best.read?"接近近期阅读内容：":best.weak?"接近历史参考内容：":"接近你选择的：")+HotCandidate.clean(best.text,36):category==2?"根据你的少看反馈调整":best!=null?"按兴趣相关性初筛":row.reason;
            ranked.add(new Scored(new FeedStore.Row(row.eventId,row.candidate,reason,row.entries,category),best,positive-0.7*negative,category,best!=null&&(negative==0||positive>negative+0.03)));
        }
        // Confidence categories are safeguards, not a gate that discards every
        // relative signal below an absolute similarity threshold.
        ranked.sort((a,b)->{int category=Integer.compare(a.category,b.category);if(category!=0)return category;int score=Double.compare(b.score,a.score);return score!=0?score:Integer.compare(order.get(a.row.eventId),order.get(b.row.eventId));});return ranked;
    }
    public static double cosine(float[] a,float[] b){if(a.length!=512||b.length!=512)return 0;double sum=0;for(int n=0;n<a.length;n++){if(!Float.isFinite(a[n])||!Float.isFinite(b[n]))return 0;sum+=(double)a[n]*b[n];}return Math.max(-1,Math.min(1,sum));}
    public static LinkedHashMap<String,List<Scored>> rerankGroups(List<Scored> ranked){
        LinkedHashMap<String,List<Scored>> groups=new LinkedHashMap<>();
        for(int n=0;n<Math.min(40,ranked.size());n++){
            // A positive-query-only reranker must not undo evidence that the item
            // is at least as close to an explicit dislike. Keep its coarse slot.
            Scored candidate=ranked.get(n);if(candidate.category==2||candidate.match==null||!candidate.positiveLead)continue;
            List<Scored> group=groups.computeIfAbsent(candidate.match.query(),ignored->new ArrayList<>());
            // One query's confidence bucket is fixed by its first (best) item.
            // Never fuse neutral and confident positions across category guards.
            if(group.isEmpty()||group.get(0).category==candidate.category)group.add(candidate);
        }
        return groups;
    }
    /** Raw reranker logits are compared only within one query and only among items
     * actually scored. Unscored positions stay put; stages never mix raw scales. */
    public static List<FeedStore.Row> fuse(List<Scored> ranked,Map<String,Map<String,Float>> logits){
        List<FeedStore.Row> result=new ArrayList<>();for(Scored c:ranked)result.add(c.row);
        for(Map.Entry<String,List<Scored>> group:rerankGroups(ranked).entrySet()){
            Map<String,Float> values=logits.getOrDefault(group.getKey(),Collections.emptyMap());List<Scored> scored=new ArrayList<>();List<Integer> positions=new ArrayList<>();
            for(int n=0;n<ranked.size();n++){Scored c=ranked.get(n);Float value=values.get(c.row.eventId);if(group.getValue().contains(c)&&value!=null&&Float.isFinite(value)){scored.add(c);positions.add(n);}}
            if(scored.size()<2)continue;Map<String,Integer> coarse=new HashMap<>(),fine=new HashMap<>();for(int n=0;n<scored.size();n++)coarse.put(scored.get(n).row.eventId,n+1);
            List<Scored> byLogit=new ArrayList<>(scored);byLogit.sort(Comparator.comparingDouble((Scored c)->-values.get(c.row.eventId)).thenComparingInt(c->coarse.get(c.row.eventId)));
            for(int n=0;n<byLogit.size();n++)fine.put(byLogit.get(n).row.eventId,n+1);
            scored.sort(Comparator.comparingDouble((Scored c)->-(1.0/(60+coarse.get(c.row.eventId))+1.5/(60+fine.get(c.row.eventId)))).thenComparingInt(c->coarse.get(c.row.eventId)));
            for(int n=0;n<scored.size();n++)result.set(positions.get(n),scored.get(n).row);
        }
        return result;
    }
    public static List<FeedStore.Row> fuse(List<Scored> ranked,Map<String,Map<String,Float>> logits,PlatformPreference platforms){
        Map<String,Integer> categories=new HashMap<>();for(Scored row:ranked)categories.put(row.row.eventId,row.category);
        return platforms.apply(fuse(ranked,logits),categories);
    }
}
