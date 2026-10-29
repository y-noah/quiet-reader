package app.quietreader;

import org.junit.Test;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public final class TiebaReplyMediaTest {
    private static final String IMAGE="https://tb3.bdstatic.com/emoji/image_emoticon1.png";
    private Section reply(boolean loaded){
        String id="tieba-post-"+app.quietreader.discovery.HotCandidate.hash("lzl:101").substring(0,24);
        Section s=new Section(id,"楼内作者",true);s.nestedReply=true;s.sourcePostId="11";s.sourceChildId="101";
        s.parentSectionId="parent11";s.replyToAuthor="被回复者";
        s.blocks.add(new Block("text","正文表情",loaded?List.of(new InlineImage(2,4,IMAGE,"表情")):List.of()));return s;
    }
    @Test public void sourceIdentityAndUnchangedContextAreRequired(){
        List<Consumer<Section>> changes=List.of(s->s.sourceChildId="",s->s.sourceChildId="102",s->s.sourcePostId="12",
            s->s.parentSectionId="parent12",s->s.replyToAuthor="其他人",s->s.nestedReply=false,s->s.continuationUrl="https://tieba.baidu.com/p/789");
        for(Consumer<Section> change:changes){Section old=reply(false),fresh=reply(true);change.accept(fresh);assertSame(old,TiebaReplyMedia.enrich(old,fresh));}
        Section old=reply(false),fresh=reply(true);fresh.blocks.set(0,new Block("text","改动正文",fresh.blocks.get(0).inlineImages));assertSame(old,TiebaReplyMedia.enrich(old,fresh));
        fresh=reply(true);fresh.blocks.set(0,new Block("text","正文表情",fresh.blocks.get(0).inlineImages,List.of(new InlineLink(0,2,"https://tieba.baidu.com/p/789"))));assertSame(old,TiebaReplyMedia.enrich(old,fresh));
    }
    @Test public void existingImageCannotBeRemovedOrReplaced(){
        Section old=reply(true);assertSame(old,TiebaReplyMedia.enrich(old,reply(false)));
        Section fresh=reply(true);fresh.blocks.set(0,new Block("text","正文表情",List.of(new InlineImage(2,4,IMAGE.replace("1.png","2.png"),"表情"))));
        assertSame(old,TiebaReplyMedia.enrich(old,fresh));assertSame(old,TiebaReplyMedia.enrich(old,reply(true)));
    }
    @Test public void invalidOrOverlappingImageRangesCannotEnrich(){
        for(List<InlineImage> images:List.of(List.of(new InlineImage(-1,2,IMAGE,"表情")),List.of(new InlineImage(2,5,IMAGE,"表情")),
            List.of(new InlineImage(2,4,"http://tb3.bdstatic.com/a.png","表情")),List.of(new InlineImage(2,4,IMAGE,"表情"),new InlineImage(3,4,IMAGE,"情")))){
            Section old=reply(false),fresh=reply(true);fresh.blocks.set(0,new Block("text","正文表情",images));assertSame(old,TiebaReplyMedia.enrich(old,fresh));
        }
    }
    @Test public void legacyHashedChildCanGainMediaWithoutChangingReadMetadata(){
        Section old=reply(false);old.sourceChildId="";old.sourceReplyAt=1700000000000L;old.sourceReplyEvidence="source-time";
        Section result=TiebaReplyMedia.enrich(old,reply(true));assertNotSame(old,result);assertEquals("101",result.sourceChildId);
        assertEquals(old.sourceReplyAt,result.sourceReplyAt);assertEquals(old.sourceReplyEvidence,result.sourceReplyEvidence);
        assertTrue(old.blocks.get(0).inlineImages.isEmpty());assertEquals(1,result.blocks.get(0).inlineImages.size());
    }
    @Test public void historyAcceptsOnlyChildIdMatchingItsCanonicalSourceIdentity()throws Exception{
        Document doc=new Document();doc.url="https://tieba.baidu.com/p/456";doc.sections.add(reply(true));doc.blocks.addAll(doc.sections.get(0).blocks);
        assertEquals("101",ReadingHistory.decode(ReadingHistory.encode(doc)).sections.get(0).sourceChildId);
        for(String invalid:List.of("","0","102","bad")){
            org.json.JSONObject json=ReadingHistory.encode(doc);json.getJSONArray("sections").getJSONObject(0).put("sourceChildId",invalid);
            assertEquals("",ReadingHistory.decode(json).sections.get(0).sourceChildId);
        }
        org.json.JSONObject legacy=ReadingHistory.encode(doc);legacy.getJSONArray("sections").getJSONObject(0).remove("sourceChildId");
        assertEquals("",ReadingHistory.decode(legacy).sections.get(0).sourceChildId);
        org.json.JSONObject foreign=ReadingHistory.encode(doc);foreign.put("url","https://www.zhihu.com/question/456");
        assertEquals("",ReadingHistory.decode(foreign).sections.get(0).sourceChildId);
    }
}
