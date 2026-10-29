package app.quietreader.discovery;

import java.util.*;

/** Bounded retrieval for event verification, not an equivalence classifier.
 * Reuses cached candidate vectors; never starts a model or changes a feed.
 * A task's score only controls work priority, never authorizes a merge. */
public final class EventVerificationPlan {
    public static final String VERSION="event-verification-plan-v7-rate-decision";
    public static final int MAX_CANDIDATES=384,MAX_PAIRS=24;
    private static final double RETRIEVAL_FLOOR=0.75;
    private EventVerificationPlan(){}
    public static final class Task {
        public final HotCandidate left,right;
        public final double similarity;
        public final String modelRevision;
        /** Actual immutable model inputs; display titles remain untouched. */
        public final String leftInput,rightInput;
        /** Raw bidirectional title logits may be reused; admission must run again. */
        public final String cacheKey;
        /** Full current evidence identity, never a permanent equivalence proof. */
        public final String evidenceKey;
        private Task(HotCandidate a,HotCandidate b,double similarity,String revision){
            if(a.id.compareTo(b.id)>0){HotCandidate swap=a;a=b;b=swap;}
            left=a;right=b;this.similarity=similarity;
            modelRevision=revision;
            String leftStatement=InstitutionStatementEvidence.modelTitle(a),rightStatement=InstitutionStatementEvidence.modelTitle(b);
            String leftRate=RateDecisionEvidence.modelTitle(a),rightRate=RateDecisionEvidence.modelTitle(b);
            boolean rateInput=!leftRate.equals(a.title)||!rightRate.equals(b.title);
            leftInput=rateInput?leftRate:EventQuestionFrame.title(leftStatement);rightInput=rateInput?rightRate:EventQuestionFrame.title(rightStatement);
            List<String> titles=new ArrayList<>(Arrays.asList(leftInput,rightInput));Collections.sort(titles);
            String inputVersion=leftInput.equals(a.title)&&rightInput.equals(b.title)?"event-title-bidirectional-v1":"event-title-bidirectional-v2-"+InstitutionStatementEvidence.VERSION;
            if(!leftInput.equals(leftStatement)||!rightInput.equals(rightStatement))inputVersion="event-title-bidirectional-v3-"+EventQuestionFrame.VERSION+"-"+InstitutionStatementEvidence.VERSION;
            if(rateInput)inputVersion="event-title-bidirectional-v4-"+RateDecisionEvidence.VERSION;
            cacheKey=HotCandidate.hash(inputVersion+"\n"+revision+"\n"+HotCandidate.hash(titles.get(0))+"\n"+HotCandidate.hash(titles.get(1)));
            evidenceKey=HotCandidate.hash(VERSION+"\n"+EventClaimEvidence.VERSION+"\n"+EventGroups.VERSION+"\n"+cacheKey+"\n"+fingerprint(a)+"\n"+fingerprint(b));
        }
        public boolean matches(HotCandidate a,HotCandidate b){
            if(a.id.compareTo(b.id)>0){HotCandidate swap=a;a=b;b=swap;}
            return fingerprint(left).equals(fingerprint(a))&&fingerprint(right).equals(fingerprint(b));
        }
    }
    private static String fingerprint(HotCandidate c){
        return HotCandidate.hash(c.id+"\n"+c.textHash+"\n"+c.observedAt+"\n"+c.publishedAt+"\n"+c.video);
    }
    private static double squaredNorm(float[] vector){
        if(vector==null||vector.length!=512)return 0;
        double norm=0;for(float value:vector){if(!Float.isFinite(value))return 0;norm+=(double)value*value;}
        return norm;
    }
    private static double cosine(float[] a,float[] b,double na,double nb){
        double dot=0;for(int n=0;n<a.length;n++)dot+=(double)a[n]*b[n];
        return Math.max(-1,Math.min(1,dot/Math.sqrt(na*nb)));
    }
    private static boolean substantialContainment(String a,String b){
        String shorter=a.length()<=b.length()?a:b,longer=a.length()<=b.length()?b:a;
        return shorter.codePointCount(0,shorter.length())>=10&&longer.contains(shorter);
    }
    private static Set<String> bigrams(String headline){
        int[] points=headline.codePoints().toArray();Set<String> result=new HashSet<>();
        if(points.length>=7)for(int n=0;n+1<points.length;n++)result.add(new String(points,n,2));
        return result;
    }
    private static boolean substantialOverlap(Set<String> a,Set<String> b){
        // Retrieval only: extra source excerpts and inserted title details must
        // not hide an otherwise concrete claim from the actual verifier.
        // Distinct bigrams avoid repetitive punctuation/text inflating overlap.
        if(a.size()<5||b.size()<5)return false;
        Set<String> small=a.size()<=b.size()?a:b,large=small==a?b:a;int shared=0;
        for(String token:small)if(large.contains(token))shared++;
        return shared>=5&&shared*5>=small.size()*4;
    }
    public static List<Task> create(List<HotCandidate> input,Map<String,float[]> vectors,String modelRevision){
        return create(input,vectors,modelRevision,()->false);
    }
    public static List<Task> create(List<HotCandidate> input,Map<String,float[]> vectors,String modelRevision,java.util.function.BooleanSupplier canceled){
        if(modelRevision==null||modelRevision.trim().isEmpty())throw new IllegalArgumentException("Model revision required");
        Objects.requireNonNull(canceled);
        // Input order is the current candidate ordering. Do not scan an unbounded
        // history or encode missing vectors solely to populate this queue.
        List<HotCandidate> candidates=new ArrayList<>();List<float[]> cached=new ArrayList<>();double[] norms=new double[Math.min(input.size(),MAX_CANDIDATES)];List<String> headlines=new ArrayList<>();List<Set<String>> headlineBigrams=new ArrayList<>();List<EventClaimEvidence.Prepared> prepared=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int n=0;n<Math.min(input.size(),MAX_CANDIDATES);n++){
            if(Thread.currentThread().isInterrupted()||canceled.getAsBoolean())return Collections.emptyList();
            HotCandidate c=input.get(n);if(!seen.add(c.id)||c.video)continue;
            float[] vector=vectors.get(SemanticOrder.key(c.modelText()));
            // Validate and measure once per cached vector, not twice per pair.
            double norm=squaredNorm(vector);
            if(norm>0){norms[candidates.size()]=norm;candidates.add(c);cached.add(vector);String headline=HeadlineIdentity.core(c.title);headlines.add(headline);headlineBigrams.add(bigrams(headline));prepared.add(new EventClaimEvidence.Prepared(c));}
        }
        Comparator<Task> priority=Comparator.comparingDouble((Task task)->-task.similarity).thenComparing(task->task.evidenceKey);
        // Keep only the bounded best work while scanning, not all O(n²) pairs.
        PriorityQueue<Task> best=new PriorityQueue<>(MAX_PAIRS,priority.reversed());
        for(int i=0;i<candidates.size();i++)for(int j=i+1;j<candidates.size();j++){
            if(Thread.currentThread().isInterrupted()||canceled.getAsBoolean())return Collections.emptyList();
            HotCandidate a=candidates.get(i),b=candidates.get(j);
            // A rejected pair cannot become work regardless of cosine/overlap.
            // Reuse the existing pre-parsed evidence before doing 512-D math.
            if(EventClaimEvidence.evaluate(prepared.get(i),prepared.get(j))!=EventClaimEvidence.Decision.CANDIDATE)continue;
            if(AppointmentEventEvidence.same(prepared.get(i),prepared.get(j)))continue;
            double score=cosine(cached.get(i),cached.get(j),norms[i],norms[j]);
            // Source excerpts can make full-input vectors diverge even when
            // one substantial headline is contained literally in the other.
            // This adds retrieval only; all facts and later verification remain.
            // A complete, compatible bank decision already supplies a literal
            // shared main event. Extra dissent prose need not also pass the
            // full-title vector floor. It still competes for the same 24 slots
            // and requires the unchanged bidirectional model/assembly checks.
            boolean rateDecision=prepared.get(i).rateDecision!=null&&prepared.get(j).rateDecision!=null;
            if(score<RETRIEVAL_FLOOR&&!rateDecision&&!substantialContainment(headlines.get(i),headlines.get(j))&&!substantialOverlap(headlineBigrams.get(i),headlineBigrams.get(j)))continue;
            Task task=new Task(a,b,score,modelRevision);if(best.size()<MAX_PAIRS)best.add(task);
            else if(priority.compare(task,best.peek())<0){best.poll();best.add(task);}
        }
        if(Thread.currentThread().isInterrupted()||canceled.getAsBoolean())return Collections.emptyList();
        List<Task> result=new ArrayList<>(best);result.sort(priority);return Collections.unmodifiableList(result);
    }
}
