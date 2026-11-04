package app.quietreader.discovery;

import java.text.Normalizer;
import java.util.*;
import org.json.*;

/** Bounded, local metadata search. Never fetches articles or changes feed/preferences. */
public final class HotPoolSearch {
    public static final int MAX_QUERY=80;
    private HotPoolSearch(){}
    /** Explicit lookup may recover a topic marked "enough"; explicit blocks still apply. */
    public static List<FeedStore.Row> pool(List<HotCandidate> candidates,JSONArray feedback){
        JSONArray constraints=new JSONArray();for(int n=0;n<feedback.length();n++){
            JSONObject entry=feedback.optJSONObject(n);if(entry!=null&&!"ENOUGH".equals(entry.optString("action")))constraints.put(entry);
        }
        return CandidateOrder.forModels(candidates,constraints);
    }
    public static final class Result {
        public final int poolSize;
        public final List<FeedStore.Row> rows;
        Result(int poolSize,List<FeedStore.Row> rows){this.poolSize=poolSize;this.rows=Collections.unmodifiableList(rows);}
    }
    private static final class Hit {
        final FeedStore.Row row;final int score;
        Hit(FeedStore.Row row,int score){this.row=row;this.score=score;}
    }
    public static Result search(List<FeedStore.Row> pool,String input){
        String query=Normalizer.normalize(HotCandidate.clean(input,MAX_QUERY),Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        String compact=normalize(query);List<String> terms=new ArrayList<>();
        for(String part:query.split("[^\\p{L}\\p{N}]+")){String term=normalize(part);if(!term.isEmpty())terms.add(term);}
        int count=0;for(FeedStore.Row row:pool)count+=row.entries.size();
        if(compact.isEmpty())return new Result(count,new ArrayList<>());
        List<Hit> hits=new ArrayList<>();
        for(FeedStore.Row row:pool){
            int best=0;HotCandidate selected=row.candidate;
            for(HotCandidate member:row.entries){
                int score=score(normalize(member.title),compact,terms,true);
                score=Math.max(score,score(normalize(member.snippet),compact,terms,false));
                if(score>best){best=score;selected=member;}
            }
            if(best>0)hits.add(new Hit(selected==row.candidate?row:new FeedStore.Row(row.eventId,selected,row.reason,row.entries),best));
        }
        // Stable ties preserve the existing pool order; raw cross-platform heat is never compared.
        hits.sort(Comparator.comparingInt((Hit hit)->hit.score).reversed());
        List<FeedStore.Row> rows=new ArrayList<>();for(Hit hit:hits)rows.add(hit.row);
        return new Result(count,rows);
    }
    private static String normalize(String value){
        String text=Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);StringBuilder out=new StringBuilder();
        text.codePoints().filter(Character::isLetterOrDigit).forEach(out::appendCodePoint);return out.toString();
    }
    private static int score(String text,String query,List<String> terms,boolean title){
        if(text.isEmpty())return 0;
        if(text.equals(query))return title?120:85;
        if(text.contains(query))return title?110:80;
        if(terms.size()>1&&terms.stream().allMatch(text::contains))return title?100:70;
        int length=query.codePointCount(0,query.length());
        // Short names/numbers require exact matching: one shared character is not evidence.
        if(length<4||length>32||query.codePoints().anyMatch(Character::isDigit))return 0;
        int[] needle=query.codePoints().toArray(),haystack=text.codePoints().toArray();
        if(scattered(haystack,needle))return title?75:45;
        if(approximate(haystack,needle,length>=9?2:1))return title?65:35;
        return 0;
    }
    /** Allow words separated by a short phrase, while requiring adjacent-word evidence. */
    private static boolean scattered(int[] text,int[] query){
        int adjacent=0;for(int n=1;n<query.length;n++)for(int j=1;j<text.length;j++)if(query[n-1]==text[j-1]&&query[n]==text[j]){adjacent++;break;}
        if(adjacent<(query.length-1+1)/2)return false;
        for(int start=0;start<text.length;start++)if(text[start]==query[0]){
            int matched=1,end=Math.min(text.length,start+query.length+Math.min(12,query.length*2));
            for(int at=start+1;at<end;at++)if(text[at]==query[matched]&&++matched==query.length)return true;
        }return false;
    }
    /** Levenshtein substring match, O(title length * bounded query length), two rows only. */
    private static boolean approximate(int[] text,int[] query,int edits){
        int[] prior=new int[query.length+1],next=new int[query.length+1];for(int j=0;j<prior.length;j++)prior[j]=j;
        for(int cp:text){next[0]=0;for(int j=1;j<next.length;j++)next[j]=Math.min(Math.min(prior[j]+1,next[j-1]+1),prior[j-1]+(cp==query[j-1]?0:1));
            if(next[query.length]<=edits)return true;int[] swap=prior;prior=next;next=swap;
        }return false;
    }
}
