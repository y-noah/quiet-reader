package app.quietreader;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class TopicPublicationTest {
    @Test public void refreshKeepsReadableScreenUntilTheEntireReplacementIsReady(){
        List<String> old=List.of("old"),next=List.of("new-a","new-b");Set<String> ready=new HashSet<>(List.of("old","new-a"));
        assertEquals(old,TopicPublication.select(next,old,ready::contains));
        ready.add("new-b");assertEquals(next,TopicPublication.select(next,old,ready::contains));
    }
    @Test public void coldStartCannotExposeAnUnpreparedTitle(){
        assertEquals(List.of("ready"),TopicPublication.select(List.of("pending","ready"),List.of(),"ready"::equals));
    }
    @Test public void invalidatedOldScreenCannotKeepAnUnusableTitle(){
        assertEquals(List.of("ready"),TopicPublication.select(List.of("pending","ready"),List.of("old"),"ready"::equals));
    }
    @Test public void noResultsClearsPreviousQuery(){assertTrue(TopicPublication.select(List.of(),List.of("old"),x->true).isEmpty());}
}
