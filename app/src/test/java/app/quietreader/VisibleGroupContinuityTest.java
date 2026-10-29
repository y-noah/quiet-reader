package app.quietreader;

import app.quietreader.discovery.*;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class VisibleGroupContinuityTest {
    private HotCandidate item(int n,long at){return new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/"+(9900900+n),"独立标题"+n,"","热度"+n,false,n+1,null,at);}
    private List<FeedStore.Row> flat(HotCandidate... items){List<FeedStore.Row> rows=new ArrayList<>();for(HotCandidate c:items)rows.add(new FeedStore.Row(c.id,c,"base"));return rows;}
    private FeedStore.Row group(HotCandidate a,HotCandidate b){return new FeedStore.Row("group-"+HotCandidate.hash(a.id+b.id),b,"accepted",Arrays.asList(a,b));}
    @Test public void splittingOneAcceptedGroupKeepsOtherGroupRepresentativeAndHeat()throws Exception{
        HotCandidate a=item(0,100),b=item(1,100),c=item(2,100),d=item(3,100);FeedStore.Row first=group(a,b),other=group(c,d);
        JSONArray split=new JSONArray().put(new JSONObject().put("scope","event").put("action","SEPARATE").put("target",EventTargets.capture(first,false)));
        List<FeedStore.Row> result=CandidateOrder.preserveVisibleGroups(flat(d,b,c,a),Arrays.asList(first,other),split);
        assertEquals(3,result.size());assertSame(other,result.get(2));assertSame(d,result.get(2).candidate);assertEquals("热度3",result.get(2).candidate.rawHeat);assertEquals(2,result.get(2).entries.size());
        Set<String> ids=new HashSet<>();for(FeedStore.Row row:result)for(HotCandidate member:row.entries)assertTrue(ids.add(member.id));assertEquals(4,ids.size());
    }
    @Test public void changedMetadataOrMissingMemberCannotReuseAnAcceptedMerge(){
        HotCandidate a=item(0,100),b=item(1,100);FeedStore.Row accepted=group(a,b);
        assertEquals(1,CandidateOrder.preserveVisibleGroups(flat(a,b),List.of(accepted),new JSONArray()).size());
        assertEquals(2,CandidateOrder.preserveVisibleGroups(flat(a,item(1,101)),List.of(accepted),new JSONArray()).size());
        List<FeedStore.Row> missing=CandidateOrder.preserveVisibleGroups(flat(a),List.of(accepted),new JSONArray());assertEquals(1,missing.size());assertEquals(1,missing.get(0).entries.size());
    }
    @Test public void changedBaseGroupingDoesNotLoseOrInventMembers(){
        HotCandidate a=item(0,100),b=item(1,100),c=item(2,100);FeedStore.Row accepted=group(a,b),newBase=group(b,c);
        List<FeedStore.Row> result=CandidateOrder.preserveVisibleGroups(Arrays.asList(new FeedStore.Row(a.id,a,"base"),newBase),List.of(accepted),new JSONArray());
        assertEquals(2,result.size());Set<String> ids=new HashSet<>();for(FeedStore.Row row:result)for(HotCandidate member:row.entries)assertTrue(ids.add(member.id));assertEquals(3,ids.size());
    }
}
