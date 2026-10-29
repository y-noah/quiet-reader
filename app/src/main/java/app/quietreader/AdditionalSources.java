package app.quietreader;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.util.*;
import static app.quietreader.Models.*;

/** Adapters only consume observed public lists. Signed note URLs are preserved. */
final class AdditionalSources {
    static boolean supports(Source s){return s==Source.IFANR||s==Source.V2EX||s==Source.BAIDU||s==Source.TIEBA||s==Source.SSPAI||s==Source.DOUYIN||s==Source.BILIBILI||s==Source.XHS;}
    static List<Item> list(Source source,String raw)throws Exception{
        if(!supports(source))throw new IllegalArgumentException("不支持此榜单来源");
        List<Item> out=new ArrayList<>();String trimmed=raw.trim();
        if(source==Source.TIEBA&&trimmed.startsWith("{"))return TiebaHotBoard.parse(trimmed);
        if(source==Source.BILIBILI&&trimmed.startsWith("{")){
            JSONObject response=new JSONObject(raw);if(response.optInt("code",-1)!=0)throw new IllegalStateException("B站榜单暂不可用");
            JSONArray rows=response.getJSONObject("data").getJSONArray("list");
            for(int n=0;n<rows.length();n++){JSONObject o=rows.getJSONObject(n);String id=o.optString("bvid");if(id.matches("BV[0-9A-Za-z]+"))add(out,source,o.optString("title"),"https://www.bilibili.com/video/"+id,"综合榜 #"+(n+1),true);}
        }else{
            org.jsoup.nodes.Document doc=Jsoup.parse(raw,source.boardPage());
            if(source==Source.BAIDU){
                for(Element row:doc.select("div[class*=category-wrap_]")){
                    Element rank=row.selectFirst("[class*=index_]");Element title=row.selectFirst("[class*=c-single-text-ellipsis]");
                    if(rank==null||!rank.text().matches("[1-9]\\d*")||title==null)continue;
                    Element a=title.closest("a[href]");Element heat=row.selectFirst("[class*=hot-index_]");
                    if(a!=null)add(out,source,title.text(),a.attr("href"),heat==null?"热搜 #"+rank.text():heat.text()+" 热搜指数",false);
                }
            }else if(source==Source.TIEBA){
                for(Element a:doc.select("a[href*=/hottopic/browse/hottopic?]")){
                    String title=query(a.attr("href"),"topic_name");if(title.isEmpty())title=a.text().replaceFirst("^\\d+\\s+","");
                    if(query(a.attr("href"),"topic_id").matches("[1-9]\\d*"))add(out,source,title,a.attr("href"),"热议 #"+(out.size()+1),false);
                }
            }else if(source==Source.DOUYIN){
                for(Element a:doc.select("a[href*=/hot/]:has(h3)")){
                    Element row=a.parent();for(int n=0;n<2&&row!=null&&!row.text().contains("热度");n++)row=row.parent();
                    if(row==null||!row.text().matches("(?s).*\\d+(?:\\.\\d+)?万?\\s*热度.*")||row.select("a[href*=/hot/]").size()!=1)continue;
                    add(out,source,a.selectFirst("h3").text(),a.attr("href"),"热榜 #"+(out.size()+1),true);
                }
            }else if(source==Source.XHS){
                Set<String> videos=XhsArticle.videoNoteIds(doc);
                for(Element row:doc.select("section.note-item")){
                    Element a=row.selectFirst("a[href*=/explore/][href*=xsec_token]");Element title=row.selectFirst(".title");Element likes=row.selectFirst(".like-wrapper .count");
                    if(a!=null&&title!=null)add(out,source,title.text(),a.attr("href"),source.category+(likes==null?"":" · "+likes.text()+" 赞"),VideoPolicy.row(row)||videos.contains(XhsArticle.id(UrlPolicy.normalize(source.boardPage(),a.attr("href")))));
                }
            }else if(source==Source.SSPAI){
                for(Element row:doc.select("article.article__card")){
                    Element a=row.selectFirst("a.article__card__link[href]");Element title=row.selectFirst(".article__card__title");
                    if(a!=null&&title!=null&&ReaderLinks.readerSource(UrlPolicy.normalize(source.endpoint,a.attr("href")))==source)add(out,source,title.text(),a.attr("href"),source.category,false);
                }
            }else if(source==Source.V2EX){
                for(Element a:doc.select("#Main .cell.item .item_title a[href]"))if(ReaderLinks.readerSource(UrlPolicy.normalize(source.endpoint,a.attr("href")))==source){
                    int before=out.size();add(out,source,a.text(),a.attr("href"),source.category,false);
                    if(out.size()>before){Element row=a.closest(".cell.item"),node=row==null?null:row.selectFirst("a.node[href]");String tag="";
                        if(node!=null)try{java.net.URI uri=new java.net.URI(node.absUrl("href"));String path=uri.getPath();
                            if("https".equals(uri.getScheme())&&("www.v2ex.com".equals(uri.getHost())||"v2ex.com".equals(uri.getHost()))&&uri.getUserInfo()==null&&path!=null&&path.matches("/go/[a-z0-9_]+"))tag="node:"+path.substring(4);
                        }catch(Exception ignored){}
                        Item item=out.get(before);out.set(before,new Item(item.source,item.title,item.url,item.detail,item.video,item.snippet,item.publishedAt,tag));
                    }
                }
            }else if(source==Source.BILIBILI){
                for(Element a:doc.select(".rank-item a.title[href]"))add(out,source,a.text(),a.attr("href"),"综合榜 #"+(out.size()+1),true);
            }else{
                for(Element a:doc.select(".article-info h3 a[href],a.js-title-transform[href]")){
                    if(ReaderLinks.readerSource(UrlPolicy.normalize(source.endpoint,a.attr("href")))!=source)continue;
                    Element row=a.closest("li,article,.article-info");
                    add(out,source,Jsoup.parse(a.hasAttr("title")?a.attr("title"):a.text()).text(),a.attr("href"),source.category,VideoPolicy.row(row==null?a:row));
                }
            }
        }
        out=VideoPolicy.filter(out);if(out.isEmpty())throw new IllegalStateException("平台暂未返回可用列表，可进入来源页登录后重试");
        return out;
    }
    private static String query(String url,String key){try{String q=new java.net.URI(url).getRawQuery();if(q!=null)for(String p:q.split("&")){String[] kv=p.split("=",2);if(kv.length==2&&kv[0].equals(key))return java.net.URLDecoder.decode(kv[1],"UTF-8");}}catch(Exception ignored){}return "";}
    private static void add(List<Item> out,Source source,String title,String raw,String detail,boolean video){
        String url=UrlPolicy.normalize(source.boardPage(),raw);String clean=title.trim();
        if(out.size()>=150||clean.isEmpty()||!UrlPolicy.belongs(source,url)||out.stream().anyMatch(i->i.url.equals(url)))return;
        out.add(new Item(source,clean,url,detail,video));
    }
}
