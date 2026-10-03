package app.quietreader;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import static app.quietreader.Models.*;

/** Bounded anonymous integration observation, not an authentication or availability guarantee. */
public final class AggregateLiveProbe {
    static String get(String url,String referer)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(5000);c.setReadTimeout(7000);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");c.setRequestProperty("Referer",referer);
        try{if(c.getResponseCode()!=200)throw new Exception("HTTP "+c.getResponseCode());return new String(c.getInputStream().readNBytes(4*1024*1024),StandardCharsets.UTF_8);}finally{c.disconnect();}
    }
    public static void main(String[] args)throws Exception{
        System.out.println("ANONYMOUS AGGREGATE LIVE OBSERVATION "+java.time.Instant.now());
        ExecutorService workers=Executors.newFixedThreadPool(3);List<Future<String>> runs=new ArrayList<>();
        for(Source source:Source.aggregateSources())runs.add(workers.submit(()->{
            try{
                List<Item> items=SourceParser.list(source,get(source.endpoint,source.referer()));
                String summary=source+" board="+items.size();if(items.isEmpty())return summary;
                Item item=items.get(0);Document doc;
                try{
                    String topic=AdditionalSources.doubanTopicId(item.url);
                    if(source==Source.DOUBAN&&!topic.isEmpty())doc=AdditionalSources.doubanTopic(item,get("https://m.douban.com/rexxar/api/v2/gallery/topic/"+topic+"/items?from_web=1&sort=hot&start=0&count=20&status_full_text=1&guest_only=0",item.url));
                    else if(source==Source.WALLSTREET){String id=item.url.substring(item.url.lastIndexOf('/')+1);JSONObject data=new JSONObject(get("https://api-one-wscn.awtmt.com/apiv1/content/articles/"+id+"?extract=0",item.url)).getJSONObject("data");doc=SourceParser.article(source,"<article>"+data.optString("content")+"</article>",item.url);}
                    else doc=SourceParser.article(source,get(item.url,item.url),item.url);
                    summary+=" first="+item.url+" blocks="+doc.blocks.size()+" related="+doc.related.size()+" filteredVideo="+doc.filteredVideo;
                    if(source==Source.DOUBAN&&!doc.related.isEmpty()){
                        Item post=doc.related.get(0);Document body=SourceParser.article(source,get(post.url,post.url),post.url);summary+=" nested="+post.url+" nestedBlocks="+body.blocks.size();
                    }
                }catch(Exception e){summary+=" readerUnavailable="+e.getMessage();}
                return summary;
            }catch(Exception e){return source+" unavailable="+e.getMessage();}
        }));
        workers.shutdown();for(Future<String> run:runs)System.out.println(run.get(90,TimeUnit.SECONDS));
    }
}
