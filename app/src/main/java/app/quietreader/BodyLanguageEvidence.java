package app.quietreader;

import app.quietreader.discovery.ContentLanguage;
import java.util.*;
import static app.quietreader.Models.*;

/** Bounded evidence from extracted prose, never from a page title or error notice. */
final class BodyLanguageEvidence {
    static ContentLanguage.Body classify(Document doc){
        if(doc.loginRequired||doc.sourceUnavailable||doc.filteredVideo||doc.videoTopic)return ContentLanguage.Body.UNKNOWN;
        int han=0,foreign=0,kana=0,scanned=0;
        Set<Block> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Block block:doc.blocks){
            if(!"text".equals(block.type)||!seen.add(block))continue;
            for(int offset=0;offset<block.value.length()&&scanned<16000;){
                int cp=block.value.codePointAt(offset);offset+=Character.charCount(cp);scanned++;
                Character.UnicodeScript script=Character.UnicodeScript.of(cp);
                if(script==Character.UnicodeScript.HAN)han++;
                else if(script==Character.UnicodeScript.HIRAGANA||script==Character.UnicodeScript.KATAKANA||script==Character.UnicodeScript.HANGUL)kana++;
                else if(Character.isLetter(cp))foreign++;
            }
            if(scanned>=16000)break;
        }
        int letters=han+foreign+kana;
        if(letters<200)return ContentLanguage.Body.UNKNOWN;
        if(kana==0&&han>=80&&han*5>=letters)return ContentLanguage.Body.CHINESE;
        if(han==0&&foreign+kana>=200||kana>=100&&kana*2>letters)return ContentLanguage.Body.NON_CHINESE;
        return ContentLanguage.Body.UNKNOWN;
    }
}
