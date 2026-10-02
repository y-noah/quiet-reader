package app.quietreader;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jsoup.Jsoup;
import static app.quietreader.Models.*;

/** Manual anonymous network check. Reports failures honestly; not a login simulation. */
public final class LiveProbe {
    private static String get(String url) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");
        c.setRequestProperty("Referer",url);
        try { if(c.getResponseCode()!=200)throw new Exception("HTTP "+c.getResponseCode()); return new String(c.getInputStream().readNBytes(4*1024*1024),StandardCharsets.UTF_8); } finally{c.disconnect();}
    }
    public static void main(String[] args)throws Exception {
        if(args.length>0) {
            String raw=get(args[0]);org.jsoup.nodes.Document d=Jsoup.parse(raw,args[0]);
            System.out.println("title="+d.title());
            if(args[0].contains("bbs.hupu.com")) {
                Document parsed=SourceParser.article(Source.HUPU,raw,args[0]);
                System.out.println("next="+parsed.nextUrl+" blocks="+parsed.blocks.size());
                System.out.println("unsupportedVideo="+parsed.unsupportedVideo+" notice="+parsed.notice);
                for(Section section:parsed.sections)System.out.println("section="+section.label+" blocks="+section.blocks.size());
            }
            if(args[0].equals(Source.SMZDM.endpoint)) {
                System.out.println("observedAt="+java.time.Instant.now()+" source=official-public-three-hour");
                for(Item item:SourceParser.list(Source.SMZDM,raw))System.out.println(item.title+" | "+item.detail+" | "+item.url);
            }
            if(args[0].contains("tieba.baidu.com")) {
                for(org.jsoup.nodes.Element a:d.select("a[href]"))if(a.absUrl("href").matches("https://tieba.baidu.com/p/\\d+.*")){
                    String post=a.absUrl("href");System.out.println("post="+post);
                    String body=get(post);org.jsoup.nodes.Document page=Jsoup.parse(body,post);
                    System.out.println("postTitle="+page.title());System.out.println("text="+page.body().text().substring(0,Math.min(1800,page.body().text().length())));
                    Path snapshot=Paths.get("build/reports/anonymous-tieba-post.html");Files.createDirectories(snapshot.getParent());Files.write(snapshot,body.getBytes(StandardCharsets.UTF_8));break;
                }
            }
            for(org.jsoup.nodes.Element e:d.select(".thread-content-detail img,.thread-content img,article img"))System.out.println(e.outerHtml());
            return;
        }
        List<String> report=new ArrayList<>();
        report.add("Anonymous live probe "+java.time.Instant.now());
        for(Source s:Source.values()) {
            try {
                List<Item> items=SourceParser.list(s,get(s.endpoint));
                String line=s.name()+": board="+items.size();report.add(line);System.out.println(line);
                Item first=items.get(0);String raw=get(first.url);
                Document doc=SourceParser.article(s,raw,first.url);
                line="  firstUrl="+first.url+" blocks="+doc.blocks.size()+" links="+doc.related.size()+" notice="+doc.notice;
                report.add(line);System.out.println(line);
                if(!doc.hasContent()&&doc.related.isEmpty()) {
                    String info="  htmlTitle="+Jsoup.parse(raw).title()+" articleNodes="+Jsoup.parse(raw).select("article").size();report.add(info);System.out.println(info);
                }
            } catch(Exception e) {String line=s.name()+": FAILED "+e.getClass().getSimpleName()+" "+e.getMessage();report.add(line);System.out.println(line);}
        }
        Path path=Paths.get("build/reports/live-probe.txt"); Files.createDirectories(path.getParent());Files.write(path,report,StandardCharsets.UTF_8);
    }
}
