package app.quietreader;

import org.jsoup.nodes.Element;
import static app.quietreader.Models.*;

/** Only source-observed public progressive files; never player APIs, manifests or DRM. */
final class ReaderMedia {
    static final int MAX_ATTACHMENTS=16;
    private ReaderMedia(){}
    static boolean allowed(String page,String media){
        if(!UrlPolicy.https(media)||media.length()>8192)return false;
        String path=java.net.URI.create(media).getPath();
        if(path==null||!path.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mp4|webm)"))return false;
        return trustedHost(page,media);
    }
    private static boolean trustedHost(String page,String media){
        String host=UrlPolicy.host(media);
        for(Source source:Source.values())if(UrlPolicy.belongs(source,page)){
            switch(source){
                case BBC:return host.equals("ichef.bbci.co.uk");
                case CLS:return UrlPolicy.domain(host,"cls.cn")||UrlPolicy.domain(host,"cailianpress.com");
                case SSPAI:return UrlPolicy.domain(host,"sspai.com")||UrlPolicy.domain(host,"sspai.net");
                case IFANR:return UrlPolicy.domain(host,"ifanr.com")||UrlPolicy.domain(host,"ifanrusercontent.com");
                case HUPU:return UrlPolicy.domain(host,"hupu.com")||UrlPolicy.domain(host,"hupucdn.com");
                case ZHIHU:return UrlPolicy.domain(host,"zhihu.com")||UrlPolicy.domain(host,"zhimg.com");
                default:return false;
            }
        }
        return false;
    }
    static boolean allowedPoster(String page,String poster){return UrlPolicy.https(poster)&&poster.length()<=8192&&trustedHost(page,poster);}
    /** Stable across attachment-array merges and later answer/reply loads. */
    static String key(VideoAttachment video){return app.quietreader.discovery.HotCandidate.hash("reader-media-v1\n"+video.url+"\n"+video.poster);}
    static int index(Document doc,String key){
        java.util.List<VideoAttachment> videos=attachments(doc);
        for(int n=0;n<videos.size();n++)if(key(videos.get(n)).equals(key))return n;
        return -1;
    }
    static java.util.List<VideoAttachment> attachments(Document doc){
        if(!doc.videos.isEmpty())return java.util.Collections.unmodifiableList(doc.videos);
        return java.util.Collections.singletonList(new VideoAttachment(doc.videoUrl,doc.videoPoster));
    }
    static void add(Document out,String url,String poster){
        for(VideoAttachment old:out.videos)if(!url.isEmpty()?old.url.equals(url):old.url.isEmpty()&&old.poster.equals(poster))return;
        if(out.videos.size()<MAX_ATTACHMENTS)out.videos.add(new VideoAttachment(url,poster));
    }
    static void capture(Document out,Element region){
        Element owned=region.clone();owned.select(VideoPolicy.EXCLUDED).remove();
        for(Element video:owned.select("video")){
            String poster=UrlPolicy.normalize(out.url,video.attr("poster"));
            if(!allowedPoster(out.url,poster))poster="";
            String selected="";
            java.util.List<String> candidates=new java.util.ArrayList<>();candidates.add(video.attr("src"));
            for(Element child:video.select("source[src]"))if(child.attr("type").isEmpty()||child.attr("type").matches("video/(mp4|webm)"))candidates.add(child.attr("src"));
            for(String raw:candidates){String url=UrlPolicy.normalize(out.url,raw);if(allowed(out.url,url)){selected=url;break;}}
            add(out,selected,poster);
            if(out.videoUrl.isEmpty()&&!selected.isEmpty()){out.videoUrl=selected;out.videoPoster=poster;}
            else if(out.videoUrl.isEmpty()&&out.videoPoster.isEmpty())out.videoPoster=poster;
        }
    }
    /** Replace only observed players with private structural markers before HTML is stripped.
     * No source-provided custom marker may create a block. Only existing validated media
     * identities are emitted; the marker stores no source HTML or executable attributes. */
    static void position(Document out,Element region){
        region.select("quiet-reader-media").remove();
        capture(out,region);
        for(Element player:new java.util.ArrayList<>(region.select(VideoPolicy.PLAYERS))){
            if(player.closest(VideoPolicy.EXCLUDED)!=null){player.remove();continue;}
            boolean nested=false;
            for(Element parent:player.parents()){
                if(parent==region)break;
                if(parent.is(VideoPolicy.PLAYERS)){nested=true;break;}
            }
            if(nested||player==region)continue;
            Document owned=new Document();owned.url=out.url;capture(owned,player);
            // Opaque/unsupported player still has a compact source fallback identity.
            if(owned.videos.isEmpty())add(owned,"","");
            Element markers=new Element("div");
            for(VideoAttachment video:owned.videos){
                add(out,video.url,video.poster);
                String key=key(video);
                if(index(out,key)>=0)markers.appendElement("quiet-reader-media").attr("data-key",key);
            }
            player.replaceWith(markers);
        }
    }
}
