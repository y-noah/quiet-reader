package app.quietreader;

import org.json.JSONObject;
import static app.quietreader.Models.*;

/** Geometry plus bounded content identity; no paragraph text or account credential is persisted. */
final class SavedReadingViewport {
    private static String hash(Block block){return app.quietreader.discovery.HotCandidate.hash(block.type+"\n"+block.value);}
    static JSONObject capture(Document doc,ReadingViewport viewport){
        if(viewport==null||doc==null||doc.url.length()>2048)return null;
        try{for(Section part:ReaderHtml.sections(doc))for(int n=0;n<part.blocks.size();n++)if(ReaderHtml.anchorId(part.id,n).equals(viewport.id)){
            Block block=part.blocks.get(n);if(!block.type.equals("text")&&!block.type.equals("image"))return null;
            return new JSONObject().put("version",1).put("url",doc.url).put("metadata",doc.metadataHash).put("section",part.id).put("block",hash(block)).put("geometry",viewport.encode());
        }}catch(Exception invalid){}return null;
    }
    static ReadingViewport restore(Document doc,JSONObject saved){
        if(doc==null||saved==null||saved.toString().length()>4096)return null;
        try{
            if(saved.getInt("version")!=1||!doc.url.equals(saved.getString("url"))||!doc.metadataHash.equals(saved.getString("metadata")))return null;
            String section=saved.getString("section"),blockHash=saved.getString("block");if(!blockHash.matches("[0-9a-f]{64}"))return null;
            ReadingViewport geometry=ReadingViewport.parse(saved.getJSONObject("geometry").toString());if(geometry==null)return null;
            String target=null;for(Section part:ReaderHtml.sections(doc))if(part.id.equals(section))for(int n=0;n<part.blocks.size();n++){
                Block block=part.blocks.get(n);if((block.type.equals("text")||block.type.equals("image"))&&blockHash.equals(hash(block))){if(target!=null)return null;target=ReaderHtml.anchorId(part.id,n);}
            }
            return target==null?null:geometry.at(target);
        }catch(Exception invalid){return null;}
    }
}
