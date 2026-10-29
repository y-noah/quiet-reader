package app.quietreader;

import static app.quietreader.Models.*;

/** Only the whole topic's observed type, never an embedded clip or a video reply. */
final class BodyVideoEvidence {
    static boolean classify(Source source,Document doc){
        if(doc.loginRequired||doc.sourceUnavailable)return false;
        if(source==Source.WEIBO&&VideoPolicy.weiboTopic(doc.url))return false;
        return doc.videoTopic||doc.filteredVideo;
    }
    /** Absence of a video tag is not evidence. Require substantial primary prose;
     * replies, summaries, notices and later thread pages cannot reclassify a topic. */
    static boolean textTopic(Source source,Document doc){
        if(doc.loginRequired||doc.sourceUnavailable||classify(source,doc)||doc.videoTopic||doc.filteredVideo)return false;
        if(source==Source.WEIBO&&VideoPolicy.weiboTopic(doc.url))return false;
        if(source==Source.HUPU&&java.net.URI.create(doc.url).getPath().matches("/\\d+-\\d+\\.html"))return false;
        int letters=0;
        for(Section section:doc.sections){
            if(section.answer||!(section.label.equals("正文")||section.label.startsWith("正文 · ")||section.label.equals("主帖")||section.label.startsWith("主帖 · ")||section.label.equals("问题补充")))continue;
            for(Block block:section.blocks)if(block.type.equals("text")){
                for(int offset=0;offset<block.value.length();){int cp=block.value.codePointAt(offset);offset+=Character.charCount(cp);if(Character.isLetter(cp)&&++letters>=200)return true;}
            }
        }
        return false;
    }
}
