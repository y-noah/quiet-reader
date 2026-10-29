package app.quietreader;

import java.util.*;
import static app.quietreader.Models.*;

/** Conservative cache accounting, including section-only text, links and object overhead. */
final class DocumentSize {
    static int estimate(Document doc){
        long bytes=1024+string(doc.title)+string(doc.byline)+string(doc.url)+string(doc.notice)+string(doc.nextUrl)+string(doc.moreStatus)+string(doc.videoPoster)+string(doc.videoUrl);
        Set<Block> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        bytes+=string(doc.metadataHash)+8+string(doc.sourceUpdateEvidence);
        if(doc.topic!=null){bytes+=256+string(doc.topic.topicUrl)+string(doc.topic.postUrl)+string(doc.topic.postSession);for(Item item:doc.topic.choices)bytes+=128+string(item.title)+string(item.url)+string(item.detail)+string(item.snippet);}
        for(VideoAttachment video:doc.videos)bytes+=96+string(video.url)+string(video.poster);
        for(Block block:doc.blocks)bytes+=block(block,seen);
        for(Section part:doc.sections){bytes+=240+string(part.sourceReplyEvidence)+string(part.id)+string(part.label)+string(part.continuationUrl)+string(part.sourcePostId)+string(part.sourceChildId)+string(part.parentSectionId)+string(part.replyToAuthor)+8L*part.blocks.size();for(Block block:part.blocks)bytes+=block(block,seen);}
        for(Item item:doc.related)bytes+=128+string(item.title)+string(item.url)+string(item.detail)+string(item.snippet)+string(item.sourceTag);
        for(String id:doc.filteredSectionIds)bytes+=64+string(id);
        return (int)Math.min(Integer.MAX_VALUE,bytes);
    }
    /** JSON repeats shared blocks; heap identity accounting cannot bound serialization. */
    static boolean serializationFits(Document doc,long limit){
        long bytes=1056+json(doc.title)+json(doc.byline)+json(doc.url)+json(doc.notice)+json(doc.nextUrl)+json(doc.moreStatus)+json(doc.videoPoster)+json(doc.videoUrl);
        for(String id:doc.filteredSectionIds){bytes+=json(id)+1;if(bytes>limit)return false;}
        bytes+=json(doc.metadataHash);
        if(ArticleUpdateEvidence.VERSION.equals(doc.sourceUpdateEvidence)&&doc.sourceModifiedAt>0)bytes+=128+json(doc.sourceUpdateEvidence);
        if(doc.topic!=null){bytes+=256+json(doc.topic.topicUrl)+json(doc.topic.postUrl)+json(doc.topic.postSession);for(Item item:doc.topic.choices)bytes+=256+json(item.title)+json(item.url)+json(item.detail)+json(item.snippet);}
        for(VideoAttachment video:doc.videos){bytes+=96+json(video.url)+json(video.poster);if(bytes>limit)return false;}
        for(Item item:doc.related){bytes+=256+json(item.title)+json(item.url)+json(item.detail)+json(item.snippet)+json(item.sourceTag);if(bytes>limit)return false;}
        for(Section section:ReaderHtml.sections(doc)){
            bytes+=512+json(section.sourceReplyEvidence)+json(section.id)+json(section.label)+json(section.continuationUrl)+json(section.sourcePostId)+json(section.parentSectionId)+json(section.replyToAuthor);
            for(Block b:section.blocks){
                bytes+=256+json(b.type)+json(b.value);
                for(InlineImage image:b.inlineImages){bytes+=128+json(image.url)+json(image.alt);if(bytes>limit)return false;}
                for(InlineLink link:b.inlineLinks){bytes+=128+json(link.url);if(bytes>limit)return false;}
                if(bytes>limit)return false;
            }
            if(bytes>limit)return false;
        }
        return bytes<=limit;
    }
    private static long json(String value){
        long bytes=2;
        for(int n=0;n<value.length();n++){
            char c=value.charAt(n);
            bytes+=c<32?6:c=='"'||c=='\\'||c=='/'?2:c<128?1:c<2048?2:3;
        }
        return bytes;
    }
    private static long string(String value){return 48L+2L*value.length();}
    private static long block(Block b,Set<Block> seen){
        if(!seen.add(b))return 8;
        long bytes=160+string(b.type)+string(b.value);
        for(InlineImage i:b.inlineImages)bytes+=80+string(i.url)+string(i.alt);
        for(InlineLink link:b.inlineLinks)bytes+=64+string(link.url);
        return bytes;
    }
}
