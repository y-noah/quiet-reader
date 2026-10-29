package app.quietreader;

import app.quietreader.discovery.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class InterestSetupTest {
    private FeedStore.Row row(Models.Source source,int n,String title){HotCandidate candidate=new HotCandidate(source,"https://example.org/"+source+"/"+n,title,"","",false,n+1,null,1);return new FeedStore.Row(candidate.id,candidate,"");}
    @Test public void boundedSourceSampleDoesNotRewriteFeedOrInventCandidates(){
        List<FeedStore.Row> rows=new ArrayList<>();for(int n=0;n<30;n++)rows.add(row(Models.Source.ZHIHU,n,"知乎真实测试条目"+n));rows.add(row(Models.Source.WEIBO,0,"微博测试条目"));rows.add(row(Models.Source.V2EX,0,"社区测试条目"));
        List<HotCandidate> selected=InterestSetup.candidates(rows);assertEquals(20,selected.size());assertEquals(Models.Source.WEIBO,selected.get(1).source);assertEquals(Models.Source.V2EX,selected.get(2).source);assertEquals("知乎真实测试条目1",rows.get(1).candidate.title);
        for(HotCandidate candidate:selected)assertTrue(rows.stream().anyMatch(row->row.candidate==candidate));
    }
    @Test public void sameTextAcrossSourcesIsNotAskedTwiceAndSmallPoolsRemainSmall(){
        List<FeedStore.Row> rows=Arrays.asList(row(Models.Source.ZHIHU,0,"同一标题"),row(Models.Source.WEIBO,0,"同一标题"));assertEquals(1,InterestSetup.candidates(rows).size());assertTrue(InterestSetup.candidates(Collections.emptyList()).isEmpty());
    }
}
