package app.quietreader.discovery;

import java.util.*;
import app.quietreader.Models.Source;

/** Cross-source sample for explicit setup only; never changes production ranking weights. */
public final class InterestSetup {
    private InterestSetup(){}
    public static List<HotCandidate> candidates(List<FeedStore.Row> rows){
        LinkedHashMap<Source,Deque<HotCandidate>> sources=new LinkedHashMap<>();Set<String> texts=new HashSet<>();
        for(FeedStore.Row row:rows)if(texts.add(SemanticOrder.key(row.candidate.modelText())))sources.computeIfAbsent(row.candidate.source,key->new ArrayDeque<>()).add(row.candidate);
        List<HotCandidate> result=new ArrayList<>();boolean added=true;
        while(result.size()<20&&added){added=false;for(Deque<HotCandidate> source:sources.values())if(!source.isEmpty()){result.add(source.remove());added=true;if(result.size()==20)break;}}
        return result;
    }
}
