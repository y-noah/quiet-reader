package app.quietreader;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class ArticleSessionSnapshotTest {
    @Test public void repeatedAndNestedValidationReadsEachUrlOnce(){
        AtomicInteger reads=new AtomicInteger();
        ArticleSessionSnapshot.run(()->{
            assertEquals("1",ArticleSessionSnapshot.read("a",()->""+reads.incrementAndGet()));
            ArticleSessionSnapshot.run(()->assertEquals("1",ArticleSessionSnapshot.read("a",()->""+reads.incrementAndGet())));
            assertEquals("2",ArticleSessionSnapshot.read("b",()->""+reads.incrementAndGet()));
        });
        assertEquals(2,reads.get());
        assertEquals("changed-account",ArticleSessionSnapshot.read("a",()->"changed-account"));
    }
    @Test public void failedOperationDoesNotRetainOldAccount(){
        try{ArticleSessionSnapshot.run(()->{ArticleSessionSnapshot.read("a",()->"old");throw new IllegalStateException();});fail();}
        catch(IllegalStateException expected){}
        assertEquals("new",ArticleSessionSnapshot.within(()->ArticleSessionSnapshot.read("a",()->"new")));
    }
}
