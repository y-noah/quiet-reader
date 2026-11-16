package app.quietreader;

import java.net.URI;
import java.util.*;
import java.util.regex.*;
import org.jsoup.nodes.Element;
import static app.quietreader.Models.*;

/** Zhihu's noscript fallback and hydrated image are two representations of one figure. */
final class ZhihuImages {
    private ZhihuImages(){}
    private static final Pattern PATH=Pattern.compile("/(?:[0-9]+/)?v2-([0-9a-f]{32})(?:_(?:r|b|hd|[0-9]+w))?\\.(?:jpg|jpeg|png|webp|gif)",Pattern.CASE_INSENSITIVE);
    private static String family(String url){
        try{URI uri=new URI(url);String host=uri.getHost();if(!"https".equals(uri.getScheme())||host==null||!host.matches("pic[a-z0-9]*\\.zhimg\\.com")||uri.getUserInfo()!=null||uri.getPort()!=-1&&uri.getPort()!=443)return "";
            Matcher m=PATH.matcher(uri.getPath());return m.matches()?m.group(1).toLowerCase(Locale.ROOT):"";
        }catch(Exception invalid){return "";}
    }
    private static String identity(Element image,String base){String url=SourceParser.imageSource(image,base),id=family(url);return id.isEmpty()?url:id;}
    static void prepare(Element root,String base){
        for(Element figure:root.select("figure")){
            Set<String> live=new HashSet<>();
            for(Element image:figure.select("img"))if(image.closest("noscript")==null){String id=identity(image,base);if(!id.isEmpty())live.add(id);}
            for(Element fallback:figure.select("noscript")){
                for(Element image:new ArrayList<>(fallback.select("img")))if(live.contains(identity(image,base)))image.remove();
                if(fallback.children().isEmpty()&&fallback.text().isBlank())fallback.remove();
            }
        }
    }
    /** Old cache has no DOM provenance: repair only adjacent, different size URLs
     * of one known Zhihu image; never dedupe across prose, answers or identical URLs. */
    static void cleanStored(Document doc){
        if(!UrlPolicy.belongs(Source.ZHIHU,doc.url))return;
        if(doc.sections.isEmpty()){cleanBlocks(doc.blocks);return;}
        for(Section section:doc.sections)cleanBlocks(section.blocks);
        doc.blocks.clear();for(Section section:doc.sections)doc.blocks.addAll(section.blocks);
    }
    private static void cleanBlocks(List<Block> blocks){
        for(int i=1;i<blocks.size();i++){
            Block before=blocks.get(i-1),current=blocks.get(i);
            if(!before.type.equals("image")||!current.type.equals("image")||before.value.equals(current.value))continue;
            String id=family(current.value);if(!id.isEmpty()&&id.equals(family(before.value))){blocks.remove(i-1);i--;}
        }
    }
}
