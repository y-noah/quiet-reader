package app.quietreader;

import app.quietreader.discovery.HotCandidate;
import org.jsoup.nodes.Element;
import org.json.*;
import java.time.*;
import java.util.*;
import static app.quietreader.Models.*;

/** Publisher timestamps are evidence of revision, never evidence of importance. */
final class ArticleUpdateEvidence {
    static final String VERSION="source-modified-v1";
    private ArticleUpdateEvidence(){}
    static boolean supported(Source source){return source==Source.BBC||source==Source.THEPAPER||source==Source.ITHOME||source==Source.CLS||source==Source.IFANR||source==Source.SSPAI;}
    private static boolean same(String left,String right){try{return HotCandidate.canonical(left).equals(HotCandidate.canonical(right));}catch(RuntimeException invalid){return false;}}
    static long modified(Source source,org.jsoup.nodes.Document page,String url,long now){
        if(!supported(source)||!UrlPolicy.belongs(source,url)||now<=0)return 0;
        // A page's sidebar or related NewsArticle must not timestamp the reader's article.
        List<Element> canonicals=page.select("link[rel=canonical]");boolean canonical=false;
        for(Element link:canonicals){if(!same(link.absUrl("href"),url))return 0;canonical=true;}
        Set<Long> times=new HashSet<>();
        if(canonical)for(Element meta:page.select("meta[property=article:modified_time],meta[name=article:modified_time],meta[itemprop=dateModified]")){
            if(times.size()>1)return 0;long value=parse(meta.attr("content"),now);if(value<=0)return 0;times.add(value);
        }
        int scanned=0;
        for(Element script:page.select("script[type=application/ld+json]")){
            if(++scanned>8)return 0;String raw=script.data();if(!boundedJson(raw))return 0;
            try{JSONTokener tokener=new JSONTokener(raw);Object value=tokener.nextValue();if(tokener.nextClean()!=0)return 0;int[] budget={64};if(!collect(value,url,now,times,0,budget))return 0;}catch(JSONException malformed){return 0;}
        }
        return times.size()==1?times.iterator().next():0;
    }
    private static boolean boundedJson(String raw){
        return boundedJson(raw,200000,12);
    }
    static boolean boundedJson(String raw,int maxLength,int maxDepth){
        if(raw.length()>maxLength)return false;int depth=0;boolean quoted=false,escaped=false;
        for(int n=0;n<raw.length();n++){char value=raw.charAt(n);if(quoted){if(escaped)escaped=false;else if(value=='\\')escaped=true;else if(value=='"')quoted=false;}
            else if(value=='"')quoted=true;else if(value=='{'||value=='['){if(++depth>maxDepth)return false;}else if(value=='}'||value==']'){if(--depth<0)return false;}}
        return !quoted&&depth==0;
    }
    private static boolean collect(Object value,String url,long now,Set<Long> times,int depth,int[] budget)throws JSONException{
        if(depth>8||--budget[0]<0)return false;
        if(value instanceof JSONArray){JSONArray array=(JSONArray)value;for(int n=0;n<array.length();n++)if(!collect(array.get(n),url,now,times,depth+1,budget))return false;}
        else if(value instanceof JSONObject){JSONObject object=(JSONObject)value;String type=object.optString("@type");
            boolean article=type.equals("NewsArticle")||type.equals("Article")||type.equals("ReportageNewsArticle");
            if(article){String identity=object.optString("url");Object entity=object.opt("mainEntityOfPage");if(identity.isEmpty())identity=entity instanceof JSONObject?((JSONObject)entity).optString("@id"):entity instanceof String?(String)entity:"";
                if(same(identity,url)&&object.has("dateModified")){long time=parse(object.optString("dateModified"),now);if(time<=0)return false;times.add(time);if(times.size()>1)return false;}}
            // Only schema graph containers; never recursive arbitrary related links.
            for(String key:new String[]{"@graph","mainEntity"})if(object.has(key)&&!collect(object.get(key),url,now,times,depth+1,budget))return false;
        }
        return true;
    }
    static long parse(String value,long now){
        if(value==null||value.length()>80)return 0;
        try{long time=OffsetDateTime.parse(value.trim()).toInstant().toEpochMilli();return time>=946684800000L&&time<=now?time:0;}catch(java.time.DateTimeException|ArithmeticException invalid){return 0;}
    }
    static long bbcModified(JSONObject metadata,String url,long now){
        JSONObject locators=metadata.optJSONObject("locators"),analytics=metadata.optJSONObject("atiAnalytics");Object modified=metadata.opt("lastPublished"),published=metadata.opt("firstPublished");
        if(locators==null||analytics==null||!same(locators.optString("canonicalUrl"),url)||!(modified instanceof Long||modified instanceof Integer)||!(published instanceof Long||published instanceof Integer))return 0;
        long time=((Number)modified).longValue(),first=((Number)published).longValue();
        return time>=first&&first>=946684800000L&&time<=now&&parse(analytics.optString("timeUpdated"),now)==time?time:0;
    }
}
