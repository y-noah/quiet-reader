package app.quietreader;

import java.util.*;
import static app.quietreader.Models.*;

/** Conservative cache accounting, including section-only text, links and object overhead. */
final class DocumentSize {
    static int estimate(Document doc){
        long bytes=1024+string(doc.title)+string(doc.byline)+string(doc.url)+string(doc.notice)+string(doc.nextUrl)+string(doc.moreStatus);
        Set<Block> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Block block:doc.blocks)bytes+=block(block,seen);
        for(Section part:doc.sections){bytes+=192+string(part.id)+string(part.label)+string(part.continuationUrl)+8L*part.blocks.size();for(Block block:part.blocks)bytes+=block(block,seen);}
        for(Item item:doc.related)bytes+=128+string(item.title)+string(item.url)+string(item.detail);
        for(String id:doc.filteredSectionIds)bytes+=64+string(id);
        return (int)Math.min(Integer.MAX_VALUE,bytes);
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
