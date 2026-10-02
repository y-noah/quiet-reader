package app.quietreader;

import java.net.URI;
import java.util.regex.*;
import static app.quietreader.Models.*;

/** Observed mobile gallery uses mw2000 for the same JPEG as its orj360 thumbnail. */
final class WeiboImage {
    private WeiboImage(){}
    static String preview(Source source,String thumbnail){
        if(source!=Source.WEIBO||!UrlPolicy.https(thumbnail))return thumbnail;
        try{
            URI uri=new URI(thumbnail);
            if(!UrlPolicy.host(thumbnail).matches("wx[1-4]\\.sinaimg\\.cn")||uri.getRawQuery()!=null||uri.getRawFragment()!=null)return thumbnail;
            Matcher image=Pattern.compile("^/orj360/([A-Za-z0-9_-]{1,160}\\.jpg)$").matcher(uri.getRawPath());
            if(image.matches())return "https://"+UrlPolicy.host(thumbnail)+"/mw2000/"+image.group(1);
        }catch(Exception ignored){}
        return thumbnail;
    }
}
