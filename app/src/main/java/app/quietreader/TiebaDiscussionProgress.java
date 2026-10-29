package app.quietreader;

import java.util.*;
import app.quietreader.discovery.HotCandidate;
import static app.quietreader.Models.*;
import static app.quietreader.DiscussionProgressEvidence.*;

/** The candidate may be a topic; continuity must also name its actual selected post. */
final class TiebaDiscussionProgress {
    private TiebaDiscussionProgress(){}
    static Input freeze(Document doc,String scope,long now){
        if(doc==null||scope==null||!scope.matches("[0-9a-f]{64}")||!UrlPolicy.belongs(Source.TIEBA,doc.url)
                ||!DiscussionPages.supports(Source.TIEBA,doc.sourceUrl())||DiscussionPages.page(Source.TIEBA,doc.sourceUrl())!=1||doc.discussionPage>1
                ||doc.cachedAt<=0||doc.cachedAt>now||doc.loginRequired||doc.sourceUnavailable||doc.filteredVideo||doc.videoTopic
                ||doc.tiebaReplyRevision!=TiebaReplyRelations.REVISION||!doc.related.isEmpty()
                ||!Set.of("","loaded","end").contains(doc.moreStatus)
                ||!(doc.notice.isEmpty()||StructuredSources.TIEBA_PAGE_NOTICE.equals(doc.notice))
                ||doc.sections.isEmpty()||doc.sections.size()>MAX_SECTIONS)return null;
        if(doc.topic!=null&&!doc.url.equals(doc.topic.topicUrl)||doc.topic==null&&TiebaTopic.isTopic(doc.url))return null;
        Map<String,Section> sections=new HashMap<>();
        for(Section section:doc.sections)if(!section.id.matches("tieba-post-[0-9a-f]{24}")||sections.put(section.id,section)!=null)return null;
        List<Part> parts=new ArrayList<>();int mains=0,chars=0,blocks=0;
        for(Section section:doc.sections){
            boolean primary=!section.nestedReply&&!ReaderHtml.discussion(section)&&section.label.startsWith("正文");
            if(primary)mains++;
            if(section.answer||!section.continuationUrl.isEmpty()||section.blocks.size()>512-blocks)return null;
            if(!primary&&!ReaderHtml.discussion(section)||primary&&!section.parentSectionId.isEmpty())return null;
            if(section.nestedReply){
                Section parent=sections.get(section.parentSectionId);
                if(parent==null||parent.nestedReply||!parent.sourcePostId.matches("[1-9][0-9]{0,19}")
                        ||!parent.id.equals("tieba-post-"+HotCandidate.hash("post_content_"+parent.sourcePostId).substring(0,24)))return null;
            }else if(!section.parentSectionId.isEmpty())return null;
            blocks+=section.blocks.size();List<String> text=new ArrayList<>();
            for(Block block:section.blocks)if(block.type.equals("text")){
                if(block.value.length()>ArticleProgressEvidence.MAX_CHARS-chars)return null;
                chars+=block.value.length();text.add(block.value);
            }
            long at=0;
            if(!primary&&TiebaReplyTimes.VERSION.equals(section.sourceReplyEvidence)
                    &&TiebaReplyTimes.valid(doc.sourceUrl(),section,section.sourceReplyAt,now)){
                at=section.sourceReplyAt;
                // A later virtual window cannot silently advance the acquisition clock.
                if(at>doc.cachedAt)return null;
            }
            parts.add(new Part(section.id,at,primary,text));
        }
        return mains==1?new Input(Source.TIEBA,doc.url,scope,doc.cachedAt,parts,HotCandidate.hash("tieba-thread-v1\n"+HotCandidate.canonical(doc.sourceUrl())+"\n"+(doc.topic==null?"":doc.topic.postSession))):null;
    }
}
