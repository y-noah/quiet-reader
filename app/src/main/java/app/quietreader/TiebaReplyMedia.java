package app.quietreader;

import java.util.*;
import static app.quietreader.Models.*;

/** Fill only missing inline media on the same explicitly identified source reply.
 * No text edits, image replacement, inferred identity or source request. */
final class TiebaReplyMedia {
    private TiebaReplyMedia(){}
    static boolean explicit(Section section){
        String key;
        if(section.nestedReply){
            if(section.parentSectionId.isEmpty()||!section.sourceChildId.matches("[1-9][0-9]{0,19}"))return false;
            key="lzl:"+section.sourceChildId;
        }else{
            if(!section.sourcePostId.matches("[1-9][0-9]{0,19}"))return false;
            key="post_content_"+section.sourcePostId;
        }
        return section.id.equals("tieba-post-"+app.quietreader.discovery.HotCandidate.hash(key).substring(0,24));
    }
    private static boolean same(InlineImage a,InlineImage b){return a.start==b.start&&a.end==b.end&&a.url.equals(b.url)&&a.alt.equals(b.alt);}
    private static boolean sameLinks(Block a,Block b){
        if(a.inlineLinks.size()!=b.inlineLinks.size())return false;
        for(int n=0;n<a.inlineLinks.size();n++){InlineLink x=a.inlineLinks.get(n),y=b.inlineLinks.get(n);if(x.start!=y.start||x.end!=y.end||!x.url.equals(y.url))return false;}
        return true;
    }
    static Section enrich(Section old,Section fresh){
        if(!explicit(fresh)||!ReaderHtml.discussion(old)||!old.id.equals(fresh.id)||old.answer!=fresh.answer||old.nestedReply!=fresh.nestedReply
            ||!old.label.equals(fresh.label)||!old.parentSectionId.equals(fresh.parentSectionId)||!old.replyToAuthor.equals(fresh.replyToAuthor)
            ||!old.continuationUrl.equals(fresh.continuationUrl)||!old.sourcePostId.equals(fresh.sourcePostId)
            ||!old.sourceChildId.isEmpty()&&!old.sourceChildId.equals(fresh.sourceChildId)||old.blocks.size()!=fresh.blocks.size())return old;
        List<Block> blocks=new ArrayList<>();boolean added=false;
        for(int n=0;n<old.blocks.size();n++){
            Block a=old.blocks.get(n),b=fresh.blocks.get(n);
            if(!a.type.equals(b.type)||!a.value.equals(b.value)||!sameLinks(a,b))return old;
            for(InlineImage previous:a.inlineImages)if(b.inlineImages.stream().noneMatch(next->same(previous,next)))return old;
            boolean extra=b.inlineImages.size()>a.inlineImages.size();
            if(extra){
                if(!a.type.equals("text"))return old;
                int end=0;
                for(InlineImage image:b.inlineImages){
                    if(image.start<end||image.end<=image.start||image.end>b.value.length()||!UrlPolicy.https(image.url))return old;
                    end=image.end;
                }
                added=true;blocks.add(b);
            }else blocks.add(a);
        }
        if(!added)return old;
        Section result=new Section(old.id,old.label,old.answer);result.continuationUrl=old.continuationUrl;result.sourcePostId=old.sourcePostId;
        result.sourceChildId=fresh.sourceChildId;result.nestedReply=old.nestedReply;result.parentSectionId=old.parentSectionId;result.replyToAuthor=old.replyToAuthor;
        result.sourceReplyAt=old.sourceReplyAt;result.sourceReplyEvidence=old.sourceReplyEvidence;result.blocks.addAll(blocks);return result;
    }
}
