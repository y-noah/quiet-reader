package app.quietreader;

import java.util.*;
import java.util.function.Predicate;

/** Prepare off-screen; never replace a readable presentation with an unfinished one. */
final class TopicPublication {
    private TopicPublication(){}
    static <T> List<T> select(List<T> pending,List<T> current,Predicate<T> prepared){
        if(pending.stream().allMatch(prepared))return pending;
        if(!current.isEmpty()&&current.stream().allMatch(prepared))return current;
        List<T> available=new ArrayList<>();for(T row:pending)if(prepared.test(row))available.add(row);
        return Collections.unmodifiableList(available);
    }
}
