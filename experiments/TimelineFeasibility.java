import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

/** Isolated prototype, deliberately not shipped. Dates are publication dates, not inferred event times. */
public final class TimelineFeasibility {
    static final String UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    static String get(String url)throws Exception {
        app.quietreader.UrlPolicy.requirePublicHost(url);
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(6000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent",UA);
        try {if(c.getResponseCode()!=200)throw new Exception("HTTP "+c.getResponseCode());try(java.io.InputStream in=c.getInputStream()){byte[] bytes=in.readNBytes(2*1024*1024+1);if(bytes.length>2*1024*1024)throw new Exception("response budget exceeded");return new String(bytes,StandardCharsets.UTF_8);}}finally{c.disconnect();}
    }
    static JSONObject record(String source,String title,String url,String day,String snippet){return new JSONObject().put("source",source).put("title",title).put("url",url).put("publishedDate",day).put("timeMeaning","publication, not event occurrence").put("excerpt",snippet).put("eventTime",JSONObject.NULL);}
    static String clean(String html){return Jsoup.parse(html).text();}
    static List<JSONObject> hupu(String q)throws Exception {
        List<JSONObject> out=new ArrayList<>();
        for(int page=1;page<=2;page++){
            String url="https://bbs.hupu.com/search?q="+URLEncoder.encode(q,StandardCharsets.UTF_8)+"&topicId=&sortby=general&page="+page;
            org.jsoup.nodes.Document dom=Jsoup.parse(get(url),url);
            if(!dom.title().contains(q))throw new Exception("Search identity missing");
            for(Element row:dom.select(".bbs-search-web-content .content-wrap")){
                Element link=row.selectFirst("a.content-wrap-span");if(link==null)continue;
                String target=link.absUrl("href");if(!target.matches("https://bbs\\.hupu\\.com/\\d+\\.html"))continue;
                String day="";for(Element span:row.select("span"))if(span.text().matches("\\d{4}-\\d{2}-\\d{2}")){day=LocalDate.parse(span.text()).toString();break;}
                out.add(record("HUPU",link.text(),target,day,""));
            }
        }return out;
    }
    static List<JSONObject> wallstreet(String q)throws Exception {
        List<JSONObject> out=new ArrayList<>();String cursor="";
        for(int page=0;page<2;page++){
            String url="https://api-one-wscn.awtmt.com/apiv1/search/article?query="+URLEncoder.encode(q,StandardCharsets.UTF_8)+"&limit=20&cursor="+URLEncoder.encode(cursor,StandardCharsets.UTF_8)+"&vip_type=";
            JSONObject body=new JSONObject(get(url));if(body.optInt("code")!=20000)throw new Exception("Search API error");JSONObject data=body.getJSONObject("data");JSONArray rows=data.optJSONArray("items");if(rows==null)break;
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);String raw=row.optString("uri"),target=raw.split("\\?",2)[0];if(!target.matches("https://wallstreetcn\\.com/articles/\\d+"))continue;
                long at=row.optLong("display_time",0);String day=at>0?Instant.ofEpochSecond(at).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString():"";
                out.add(record("WALLSTREET",clean(row.optString("title")),target,day,clean(row.optString("content"))).put("restricted",row.optBoolean("is_paid")||row.optBoolean("is_priced")));
            }String next=data.optString("next_cursor","");if(next.isEmpty()||next.equals(cursor))break;cursor=next;
        }return out;
    }
    static String normalized(String text){return text.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]","");}
    static Set<String> shingles(String text){String s=normalized(text);Set<String> out=new HashSet<>();for(int i=0;i<s.length()-1;i++)out.add(s.substring(i,i+2));return out;}
    static boolean duplicate(JSONObject a,JSONObject b){
        if(a.getString("url").equals(b.getString("url")))return true;
        String x=a.getString("title"),y=b.getString("title");
        // Conservative grouping: do not merge updates across dates, changed numbers or denials.
        if(!a.getString("publishedDate").equals(b.getString("publishedDate")))return false;
        if(normalized(x).equals(normalized(y)))return true;
        if(x.matches(".*(辟谣|否认|澄清|不实|回应|更正).*")||y.matches(".*(辟谣|否认|澄清|不实|回应|更正).*"))return false;
        if(!x.replaceAll("\\D","").equals(y.replaceAll("\\D","")))return false;
        Set<String> sa=shingles(x),sb=shingles(y),union=new HashSet<>(sa);union.addAll(sb);sa.retainAll(sb);return union.size()>10&&(double)sa.size()/union.size()>=0.88;
    }
    static void enrich(JSONObject row){
        if(row.optBoolean("restricted")){row.put("bodyStatus","restricted; no bypass");return;}
        try {
            app.quietreader.Models.Source source=app.quietreader.Models.Source.valueOf(row.getString("source"));String url=row.getString("url"),html;
            if(source==app.quietreader.Models.Source.WALLSTREET){String id=url.substring(url.lastIndexOf('/')+1);JSONObject data=new JSONObject(get("https://api-one-wscn.awtmt.com/apiv1/content/articles/"+id+"?extract=0")).getJSONObject("data");if(data.optBoolean("is_need_pay")||data.optBoolean("is_trial")){row.put("bodyStatus","restricted; no bypass");return;}html="<h1>"+app.quietreader.ReaderHtml.escape(row.getString("title"))+"</h1><article>"+data.optString("content")+"</article>";}
            else html=get(url);
            app.quietreader.Models.Document doc=app.quietreader.SourceParser.article(source,html,url);
            if(doc.filteredVideo){row.put("bodyStatus","video-only excluded");return;}
            List<app.quietreader.Models.Block> blocks=doc.sections.isEmpty()?doc.blocks:doc.sections.get(0).blocks;
            StringBuilder excerpt=new StringBuilder();int paragraphs=0;
            for(app.quietreader.Models.Block block:blocks)if(block.type.equals("text")&&!block.value.isBlank()){paragraphs++;if(excerpt.length()<700)excerpt.append(block.value).append('\n');}
            row.put("bodyStatus",paragraphs>0?"main-text retrieved":"no usable main text").put("bodyParagraphs",paragraphs).put("bodyExcerpt",excerpt.substring(0,Math.min(800,excerpt.length()))).put("excerptMethod","first main-post paragraphs, not a causal summary");
        } catch(Exception e){row.put("bodyStatus","unavailable: "+e.getClass().getSimpleName());}
    }
    static void checks(){
        JSONObject a=record("HUPU","事件已发生", "https://bbs.hupu.com/1.html","2026-09-01",""),b=record("WALLSTREET","事件已发生","https://wallstreetcn.com/articles/2","2026-09-01","");
        if(!duplicate(a,b))throw new AssertionError("identical titles");b.put("title","事件未发生，官方辟谣");if(duplicate(a,b))throw new AssertionError("contradiction must remain");
        a.put("title","投入100亿元建设项目");b.put("title","投入200亿元建设项目");if(duplicate(a,b))throw new AssertionError("changed quantities must remain");
        b.put("title",a.getString("title")).put("publishedDate","2026-09-02");if(duplicate(a,b))throw new AssertionError("same title on a later date may be a new development");
        if(!LocalDate.parse("2026-01-01").isBefore(LocalDate.parse("2026-10-01")))throw new AssertionError("date order");
    }
    public static void main(String[] args)throws Exception {
        checks();if(args.length>0&&args[0].equals("--selftest")){System.out.println("PASS 5 conservative grouping/date checks; not event-completeness certification");return;}String query=args.length>0?args[0]:"西贝";List<JSONObject> rows=new ArrayList<>();JSONObject status=new JSONObject();
        for(String source:List.of("HUPU","WALLSTREET")){try{List<JSONObject> found=source.equals("HUPU")?hupu(query):wallstreet(query);rows.addAll(found);status.put(source,new JSONObject().put("retrieved",found.size()).put("scope","max two search pages"));}catch(Exception e){status.put(source,new JSONObject().put("error",e.toString()));}}
        for(String source:List.of("TIEBA","WEIBO","ZHIHU","SMZDM"))status.put(source,new JSONObject().put("state","not integrated; see separate actual WebView access observations"));
        int raw=rows.size();List<JSONObject> groups=new ArrayList<>();
        for(JSONObject row:rows){JSONObject match=null;for(JSONObject other:groups)if(duplicate(row,other)){match=other;break;}if(match==null){row.put("sources",new JSONArray().put(new JSONObject().put("source",row.getString("source")).put("url",row.getString("url"))));groups.add(row);}else match.getJSONArray("sources").put(new JSONObject().put("source",row.getString("source")).put("url",row.getString("url")));}
        groups.sort(Comparator.comparing(o->o.optString("publishedDate").isEmpty()?"9999":o.optString("publishedDate")));
        Map<String,Integer> sampled=new HashMap<>();for(JSONObject row:groups){String source=row.getString("source");int n=sampled.getOrDefault(source,0);if(n<3){enrich(row);sampled.put(source,n+1);}}
        JSONObject report=new JSONObject().put("query",query).put("observedAt",Instant.now().toString()).put("status",status).put("rawCount",raw).put("groupCount",groups.size()).put("completeEventHistory",false).put("eventTimesInferred",false).put("warning","Sources are claims, not verified facts. Limited retrieved pages cannot certify origins, causal chain, outcome or latest progress.").put("entries",new JSONArray(groups));
        Path dest=Paths.get("artifacts","event-timeline-prototype-"+System.currentTimeMillis()+".json");Files.writeString(dest,report.toString(2),StandardCharsets.UTF_8);
        System.out.println("query="+query+" raw="+raw+" grouped="+groups.size()+" sources="+status+" report="+dest);
        for(JSONObject row:groups)if(row.has("bodyStatus"))System.out.println(row.getString("source")+" "+row.getString("publishedDate")+" "+row.getString("bodyStatus")+" "+row.getString("url"));
    }
}
