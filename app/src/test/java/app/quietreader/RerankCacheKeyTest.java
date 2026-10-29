package app.quietreader;

import app.quietreader.discovery.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RerankCacheKeyTest {
    @Test public void modelAndBothModelInputsAreIndependentInvalidationBoundaries(){
        String key=RerankCacheKey.of("model-a","空间站实验","中国空间站新的科学实验");
        assertNotEquals(key,RerankCacheKey.of("model-b","空间站实验","中国空间站新的科学实验"));
        assertNotEquals(key,RerankCacheKey.of("model-a","城市交通","中国空间站新的科学实验"));
        assertNotEquals(key,RerankCacheKey.of("model-a","空间站实验","月球探测实验的新进展"));
        assertNotEquals(RerankCacheKey.of("m","ab","c"),RerankCacheKey.of("m","a","bc"));
    }
    @Test public void reuseFollowsTheSameNormalizedTextThatIsSubmitted(){
        assertEquals(RerankCacheKey.of("m","空间站\n实验","cafe\u0301 <video> 🙂"),RerankCacheKey.of("m","空间站 实验","café <video> 🙂"));
        String prefix="中".repeat(1000);
        assertEquals(RerankCacheKey.of("m","实验",SemanticOrder.input(prefix+"甲")),RerankCacheKey.of("m","实验",prefix+"乙"));
        assertNotEquals(RerankCacheKey.of("m","实验","a < b"),RerankCacheKey.of("m","实验","a b"));
    }
    @Test(expected=IllegalArgumentException.class) public void unknownModelCannotShareScores(){RerankCacheKey.of("","实验","内容");}
}
