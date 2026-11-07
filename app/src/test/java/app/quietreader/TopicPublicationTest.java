package app.quietreader;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class TopicPublicationTest {
    @Test public void slowTopicDoesNotBlockPreparedReplacementRows(){
        List<String> old=List.of("old"),next=List.of("new-a","new-b");Set<String> ready=new HashSet<>(List.of("old","new-a"));
        assertEquals(List.of("new-a"),TopicPublication.select(next,old,ready::contains));
        ready.add("new-b");assertEquals(next,TopicPublication.select(next,old,ready::contains));
    }
    @Test public void entirelyUnpreparedReplacementKeepsReadableScreen(){
        assertEquals(List.of("old"),TopicPublication.select(List.of("pending"),List.of("old"),"old"::equals));
    }
    @Test public void repeatedRefreshDoesNotKeepTheFirstBatchBehindAnotherSlowTopic(){
        List<String> shown=List.of("old");Set<String> ready=Set.of("old","new-a","new-c");
        shown=TopicPublication.select(List.of("new-a","pending-b"),shown,ready::contains);
        assertEquals(List.of("new-c"),TopicPublication.select(List.of("pending-d","new-c"),shown,ready::contains));
    }
    @Test public void coldStartCannotExposeAnUnpreparedTitle(){
        assertEquals(List.of("ready"),TopicPublication.select(List.of("pending","ready"),List.of(),"ready"::equals));
    }
    @Test public void invalidatedOldScreenCannotKeepAnUnusableTitle(){
        assertEquals(List.of("ready"),TopicPublication.select(List.of("pending","ready"),List.of("old"),"ready"::equals));
    }
    @Test public void noResultsClearsPreviousQuery(){assertTrue(TopicPublication.select(List.of(),List.of("old"),x->true).isEmpty());}
}
