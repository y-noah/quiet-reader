package app.quietreader;

import java.util.*;
import java.util.function.Predicate;

/** Prepare off-screen; never replace a readable presentation with an unfinished one. */
final class TopicPublication {
    private TopicPublication(){}
    static <T> List<T> select(List<T> pending,List<T> current,Predicate<T> prepared){
        if(pending.isEmpty())return pending;
        List<T> available=new ArrayList<>();for(T row:pending)if(prepared.test(row))available.add(row);
        if(available.size()==pending.size())return pending;
        // A single slow topic must not hide the rest of an explicitly refreshed
        // batch. Keep the old screen only when none of the replacement is usable.
        if(available.isEmpty()&&!current.isEmpty()&&current.stream().allMatch(prepared))return current;
        return Collections.unmodifiableList(available);
    }
}
