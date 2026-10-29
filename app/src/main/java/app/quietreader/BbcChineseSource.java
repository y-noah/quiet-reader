package app.quietreader;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.util.*;
import static app.quietreader.Models.*;

/** BBC's Chinese most-read data and identity-bound public article blocks only. */
final class BbcChineseSource {
    private BbcChineseSource(){}
    static String articleId(String url){
        if(!UrlPolicy.belongs(Source.BBC,url))return "";
        try{java.util.regex.Matcher m=java.util.regex.Pattern.compile("/zhongwen/articles/(c[a-z0-9]{10,20})/(simp|trad)").matcher(java.net.URI.create(url).getPath());return m.matches()?m.group(1):"";}catch(Exception ignored){return "";}
    }
    private static JSONObject props(org.jsoup.nodes.Document page)throws JSONException{
        Element state=page.selectFirst("script#__NEXT_DATA__");if(state==null)throw new IllegalStateException("BBC中文页面数据缺失");
        JSONObject p=new JSONObject(state.data()).getJSONObject("props").getJSONObject("pageProps");
        if(!"zhongwen".equals(p.optString("service"))||!Set.of("simp","trad").contains(p.optString("variant"))||p.optInt("status")!=200)throw new IllegalStateException("BBC中文页面暂不可用");
        return p;
    }
    static List<Item> list(String raw)throws JSONException{
        JSONObject p=props(Jsoup.parse(raw,Source.BBC.endpoint));
        if(!"mostRead".equals(p.optString("pageType"))||!"simp".equals(p.optString("variant")))throw new IllegalStateException("未取得BBC中文热门阅读榜");
        JSONArray curations=p.getJSONObject("pageData").getJSONArray("curations");JSONObject hot=null;
        for(int n=0;n<curations.length();n++){JSONObject c=curations.optJSONObject(n);if(c!=null&&c.optJSONObject("mostRead")!=null){hot=c.getJSONObject("mostRead");break;}}
        if(hot==null)throw new IllegalStateException("BBC中文热门榜缺失");
        JSONArray entries=hot.getJSONArray("items");List<JSONObject> ranked=new ArrayList<>();
        for(int n=0;n<entries.length()&&n<100;n++){JSONObject item=entries.optJSONObject(n);if(item!=null&&item.optInt("rank")>0)ranked.add(item);}
        ranked.sort(Comparator.comparingInt(i->i.optInt("rank")));
        Set<String> seen=new HashSet<>();List<Item> out=new ArrayList<>();
        for(JSONObject item:ranked){
            String url=item.optString("href"),id=articleId(url),title=app.quietreader.discovery.HotCandidate.clean(item.optString("title"),240);
            if(id.isEmpty()||!url.endsWith("/simp")||!item.optString("id").equals("urn:bbc:optimo:asset:"+id)||title.isEmpty()||!seen.add(id))continue;
            // Never substitute the editorial homepage or infer traffic counts.
            Long published=null;try{published=java.time.Instant.parse(item.optString("timestamp")).toEpochMilli();if(published<=0)published=null;}catch(Exception ignored){}
            out.add(new Item(Source.BBC,title,url,"热门阅读 #"+item.getInt("rank"),false,"",published));
        }
        if(out.isEmpty())throw new IllegalStateException("BBC中文未返回可用热门文章");return out;
    }
    static Document article(org.jsoup.nodes.Document page,String url){
        Document out=new Document();out.url=url;
        try{
            String id=articleId(url);JSONObject p=props(page),data=p.getJSONObject("pageData"),meta=data.getJSONObject("metadata");
            String path=java.net.URI.create(url).getPath(),variant=path.endsWith("/simp")?"simp":"trad";
            if(id.isEmpty()||!path.equals(p.optString("id"))||!"article".equals(p.optString("pageType"))||!"article".equals(meta.optString("type"))||!variant.equals(p.optString("variant"))||!(variant.equals("simp")?"zh-hans":"zh-hant").equals(meta.optString("language"))||!meta.getJSONObject("locators").optString("optimoUrn").equals("urn:bbc:optimo:asset:"+id))throw new IllegalStateException();
            JSONArray blocks=data.getJSONObject("content").getJSONObject("model").getJSONArray("blocks");
            org.jsoup.nodes.Document clean=Jsoup.parse("",url);Element body=clean.body().appendElement("article");
            for(int n=0;n<blocks.length();n++){
                JSONObject block=blocks.optJSONObject(n);if(block==null)continue;
                switch(block.optString("type")){
                    case "headline":out.title=plain(block,0).trim();break;
                    case "byline":out.byline=plain(block,0).trim();break;
                    case "text":case "orderedList":case "unorderedList":case "list":append(body,block,0);break;
                    case "subheadline":body.appendElement("h2").text(plain(block,0));break;
                    case "image":image(body,block);break;
                    case "video":video(body,block);break;
                    case "social":social(body,block);break;
                    case "oEmbed":chart(body,block);break;
                    // These observed blocks are outside the article prose.
                    case "disclaimer":case "timestamp":case "continueReading":case "mpu":case "wsoj":case "podcastPromo":case "relatedContent":case "links":break;
                    // A new content schema must not silently produce a truncated article.
                    default:throw new IllegalStateException("Unsupported BBC article block");
                }
            }
            if(out.title.isEmpty()||body.select("p,li").stream().noneMatch(e->!e.text().trim().isEmpty()))throw new IllegalStateException();
            SourceParser.appendRegion(out,body,"bbc-article-"+id,"正文",false,url);
            out.sourceModifiedAt=ArticleUpdateEvidence.bbcModified(meta,url,System.currentTimeMillis());
            return out;
        }catch(Exception ignored){out.blocks.clear();out.sections.clear();out.sourceUnavailable=true;out.notice="尚未取得BBC中文文章正文，可在来源页检查访问状态";return out;}
    }
    private static JSONObject model(JSONObject block){JSONObject m=block.optJSONObject("model");return m==null?new JSONObject():m;}
    private static String plain(JSONObject block,int depth){
        if(depth>16)return "";JSONObject m=model(block);String text=m.optString("text");if(!text.isEmpty())return text;
        StringBuilder out=new StringBuilder();JSONArray children=m.optJSONArray("blocks");if(children!=null)for(int n=0;n<children.length();n++){JSONObject child=children.optJSONObject(n);if(child!=null)out.append(plain(child,depth+1));}return out.toString();
    }
    private static void append(Element parent,JSONObject block,int depth){
        if(depth>16)return;JSONObject m=model(block);String type=block.optString("type");Element target=parent;
        switch(type){
            case "paragraph":target=parent.appendElement("p");break;
            case "listItem":target=parent.appendElement("li");break;
            case "orderedList":target=parent.appendElement("ol");break;
            case "unorderedList":case "list":target=parent.appendElement("ul");break;
            case "urlLink":String url=UrlPolicy.normalize(parent.baseUri(),m.optString("locator"));if(ReaderLinks.allowed(url))target=parent.appendElement("a").attr("href",url);break;
            case "fragment":parent.appendText(m.optString("text"));return;
            case "text":break;
            default:throw new IllegalStateException("Unsupported BBC text block");
        }
        JSONArray children=m.optJSONArray("blocks");if(children==null||children.length()==0){target.appendText(m.optString("text"));return;}
        for(int n=0;n<children.length();n++){JSONObject child=children.optJSONObject(n);if(child!=null)append(target,child,depth+1);}
    }
    private static JSONObject findModel(JSONObject block,String type,int depth){
        if(depth>16)return null;if(type.equals(block.optString("type")))return model(block);
        JSONArray children=model(block).optJSONArray("blocks");if(children!=null)for(int n=0;n<children.length();n++){JSONObject child=children.optJSONObject(n);if(child==null)continue;JSONObject found=findModel(child,type,depth+1);if(found!=null)return found;}return null;
    }
    private static void social(Element parent,JSONObject block){
        JSONObject data=model(block);String url=UrlPolicy.normalize("https://www.bbc.com/",data.optString("source"));
        if(!UrlPolicy.https(url)||!ReaderLinks.allowed(url))throw new IllegalStateException("Missing social source");
        JSONObject embed=findModel(block,"aresOEmbed",0);
        if(embed!=null){
            String html=embed.optJSONObject("oembed")==null?"":embed.optJSONObject("oembed").optString("html");
            if(html.length()>100_000)throw new IllegalStateException("Social quote too large");
            org.jsoup.nodes.Document quote=Jsoup.parseBodyFragment(html,url);quote.select("script,style,iframe,form,nav,aside").remove();
            for(Element paragraph:quote.select("blockquote p")){
                Element prose=paragraph.clone();prose.select("a").remove();
                if(!prose.text().trim().isEmpty())parent.appendElement("p").text(paragraph.text());
            }
        }
        String provider=app.quietreader.discovery.HotCandidate.clean(data.optString("providerName"),32);
        parent.appendElement("p").appendElement("a").attr("href",url).text(provider.isEmpty()?"原帖":provider+" 原帖");
    }
    private static void chart(Element parent,JSONObject block){
        JSONObject embed=model(block).optJSONObject("oembed");
        if(embed==null||!"rich".equals(embed.optString("type"))||!"aresFlourish".equals(embed.optString("oEmbedType"))||!"Flourish".equals(embed.optString("provider_name")))throw new IllegalStateException("Unsupported BBC embed");
        java.net.URI source=java.net.URI.create(embed.optString("source"));
        if(!"https".equals(source.getScheme())||!"public.flourish.studio".equals(source.getHost())||source.getPort()!=-1||source.getUserInfo()!=null||source.getRawQuery()!=null||source.getRawFragment()!=null||!source.getRawPath().matches("/visualisation/[0-9]{1,20}"))throw new IllegalStateException("Invalid BBC chart source");
        // Preserve an explicit entry for opaque interactive data. Never copy its
        // iframe/resize scripts or silently discard the surrounding article.
        parent.appendElement("p").appendElement("a").attr("href",source.toString()).text("查看互动图表 · Flourish");
    }
    private static void video(Element parent,JSONObject block){
        JSONObject media=findModel(block,"aresMediaMetadata",0);String poster="";
        if(media!=null){String raw=media.optString("imageUrl").replace("$recipe","800x450");String url=UrlPolicy.normalize("https://www.bbc.com/",raw.startsWith("ichef.bbci.co.uk/")?"https://"+raw:raw);if(UrlPolicy.host(url).equals("ichef.bbci.co.uk"))poster=url;}
        // Source player is opaque: retain its position/poster, never fetch a player API or media file.
        parent.appendElement("video").attr("poster",poster);
        JSONObject caption=findModel(block,"caption",0);if(caption!=null){JSONObject wrapper=new JSONObject();try{wrapper.put("type","text").put("model",caption);}catch(JSONException ignored){}String text=plain(wrapper,0);if(!text.isEmpty())parent.appendElement("p").text(text);}
    }
    private static void image(Element parent,JSONObject block){
        JSONArray children=model(block).optJSONArray("blocks");if(children==null)return;JSONObject raw=null;String alt="",caption="";
        for(int n=0;n<children.length();n++){JSONObject child=children.optJSONObject(n);if(child==null)continue;switch(child.optString("type")){case "rawImage":raw=model(child);break;case "altText":alt=plain(child,0);break;case "caption":caption=plain(child,0);break;default:break;}}
        if(raw==null||!"cpsprodpb".equals(raw.optString("originCode")))return;
        String locator=raw.optString("locator");if(!locator.matches("[A-Za-z0-9/_\\.-]{1,240}")||locator.contains("..")||locator.startsWith("/"))return;
        Element figure=parent.appendElement("figure");figure.appendElement("img").attr("src","https://ichef.bbci.co.uk/ace/ws/800/cpsprodpb/"+locator).attr("alt",alt).attr("width",Integer.toString(raw.optInt("width"))).attr("height",Integer.toString(raw.optInt("height")));
        if(!caption.isEmpty())figure.appendElement("figcaption").text(caption);
    }
}
