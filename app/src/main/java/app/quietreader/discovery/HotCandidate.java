package app.quietreader.discovery;

import app.quietreader.Models;
import org.json.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.*;

/** Source facts only. Fetch time is not publication time; heat remains source text. */
public final class HotCandidate {
    public static final String NORMALIZATION="hot-title-v2";
    public final String id,canonicalUrl,title,snippet,textHash,originalUrl,rawHeat;
    public final Models.Source source;
    public final boolean video;
    public final int sourceRank;
    public final Long publishedAt;
    public final long observedAt;
    public final ContentLanguage.Body bodyLanguage;
    public final long bodyLanguageAt;
    /** Time of observed whole-topic type, positive OR negative; zero means source metadata only. */
    public final long videoEvidenceAt;
    public final String sourceTag;

    public HotCandidate(Models.Source source,String originalUrl,String title,String snippet,String rawHeat,boolean video,int sourceRank,Long publishedAt,long observedAt){
        this(source,originalUrl,title,snippet,rawHeat,video,sourceRank,publishedAt,observedAt,ContentLanguage.Body.UNKNOWN);
    }
    public HotCandidate(Models.Source source,String originalUrl,String title,String snippet,String rawHeat,boolean video,int sourceRank,Long publishedAt,long observedAt,ContentLanguage.Body bodyLanguage){
        this(source,originalUrl,title,snippet,rawHeat,video,sourceRank,publishedAt,observedAt,bodyLanguage,"");
    }
    public HotCandidate(Models.Source source,String originalUrl,String title,String snippet,String rawHeat,boolean video,int sourceRank,Long publishedAt,long observedAt,ContentLanguage.Body bodyLanguage,String sourceTag){
        this(source,originalUrl,title,snippet,rawHeat,video,sourceRank,publishedAt,observedAt,bodyLanguage,sourceTag,bodyLanguage==ContentLanguage.Body.UNKNOWN?0:observedAt);
    }
    public HotCandidate(Models.Source source,String originalUrl,String title,String snippet,String rawHeat,boolean video,int sourceRank,Long publishedAt,long observedAt,ContentLanguage.Body bodyLanguage,String sourceTag,long bodyLanguageAt){
        this(source,originalUrl,title,snippet,rawHeat,video,sourceRank,publishedAt,observedAt,bodyLanguage,sourceTag,bodyLanguageAt,0);
    }
    public HotCandidate(Models.Source source,String originalUrl,String title,String snippet,String rawHeat,boolean video,int sourceRank,Long publishedAt,long observedAt,ContentLanguage.Body bodyLanguage,String sourceTag,long bodyLanguageAt,long videoEvidenceAt){
        this.videoEvidenceAt=Math.max(0,videoEvidenceAt);
        this.bodyLanguageAt=bodyLanguageAt;
        this.sourceTag=clean(sourceTag,80);
        this.bodyLanguage=Objects.requireNonNull(bodyLanguage);
        this.source=Objects.requireNonNull(source);this.originalUrl=Objects.requireNonNull(originalUrl);
        this.canonicalUrl=canonical(originalUrl);this.title=clean(title,240);this.snippet=clean(snippet,600);
        if(this.title.isEmpty()||sourceRank<1||observedAt<0)throw new IllegalArgumentException("Invalid candidate");
        this.rawHeat=clean(rawHeat,100);this.video=video;this.sourceRank=sourceRank;this.publishedAt=publishedAt;this.observedAt=observedAt;
        this.id=hash(source.name()+"\n"+canonicalUrl);this.textHash=metadataHash(this.title,this.snippet);
    }
    public static String metadataHash(String title,String snippet){return hash(NORMALIZATION+"\n"+clean(title,240)+"\n"+clean(snippet,600));}
    public String modelText(){return title+(snippet.isEmpty()||snippet.equals(title)?"":"\n"+snippet);}
    public Models.Item readerItem(){return new Models.Item(source,title,originalUrl,rawHeat,video,snippet,publishedAt,sourceTag);}
    public JSONObject json(){try{return new JSONObject().put("source",source.name()).put("url",originalUrl).put("title",title).put("snippet",snippet).put("heat",rawHeat).put("video",video).put("rank",sourceRank).put("publishedAt",publishedAt==null?JSONObject.NULL:publishedAt).put("observedAt",observedAt).put("sourceTag",sourceTag).put("bodyLanguage",bodyLanguage.name()).put("bodyLanguageAt",bodyLanguageAt).put("videoEvidenceAt",videoEvidenceAt).put("metadataLanguage",ContentLanguage.text(title).name()).put("languagePolicy",ContentLanguage.VERSION);}catch(JSONException e){throw new IllegalArgumentException(e);}}
    public static HotCandidate fromJson(JSONObject o)throws JSONException{return new HotCandidate(Models.Source.valueOf(o.getString("source")),o.getString("url"),o.getString("title"),o.optString("snippet"),o.optString("heat"),o.optBoolean("video"),o.getInt("rank"),o.isNull("publishedAt")?null:o.getLong("publishedAt"),o.getLong("observedAt"),bodyLanguage(o.optString("bodyLanguage")),o.optString("sourceTag"),o.optLong("bodyLanguageAt",0),o.optLong("videoEvidenceAt",0));}
    private static ContentLanguage.Body bodyLanguage(String value){try{return ContentLanguage.Body.valueOf(value);}catch(IllegalArgumentException ex){return ContentLanguage.Body.UNKNOWN;}}
    public static String clean(String value,int limit){
        // Adapters supply plain source text. Parsing it as HTML would erase literal
        // code such as <video>, comparisons and bracketed names in real titles.
        String raw=Normalizer.normalize(value==null?"":value,Normalizer.Form.NFC);
        StringBuilder out=new StringBuilder();raw.codePoints().forEach(cp->{int type=Character.getType(cp);if(type==Character.CONTROL||type==Character.FORMAT&&cp!=0x200d){if(Character.isWhitespace(cp))out.append(' ');return;}out.appendCodePoint(cp);});
        String text=out.toString().replaceAll("[\\s\\p{Z}]+"," ").trim();
        return text.codePointCount(0,text.length())>limit?text.substring(0,text.offsetByCodePoints(0,limit)):text;
    }
    public static String canonical(String value){
        try{
            if(value.length()>2048)throw new IllegalArgumentException("Source URL too long");
            URI uri=new URI(value.trim());String host=uri.getHost();
            if(!"https".equalsIgnoreCase(uri.getScheme())||host==null||uri.getUserInfo()!=null)throw new IllegalArgumentException("HTTPS source URL required");
            String query=uri.getRawQuery();
            if(query!=null){List<String> parts=new ArrayList<>();for(String part:query.split("&")){
                String key=part.split("=",2)[0];if(!Arrays.asList("utm_source","utm_medium","utm_campaign","utm_content","utm_term").contains(key))parts.add(part);
            }query=parts.isEmpty()?null:String.join("&",parts);}
            String port=uri.getPort()==-1||uri.getPort()==443?"":":"+uri.getPort();
            String path=uri.getRawPath();if(path==null||path.isEmpty())path="/";
            // Preserve content IDs, query order, pagination, signatures, and hash routes.
            return "https://"+host.toLowerCase(Locale.ROOT)+port+path+(query==null?"":"?"+query)+(uri.getRawFragment()==null?"":"#"+uri.getRawFragment());
        }catch(java.net.URISyntaxException e){throw new IllegalArgumentException("Invalid source URL",e);}
    }
    public static String hash(String value){try{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        // Cache/session checks use this on the reading path. Avoid constructing a
        // Formatter for each byte; preserve the existing lowercase SHA-256 identity.
        char[] hex=new char[digest.length*2];String digits="0123456789abcdef";
        for(int n=0;n<digest.length;n++){int b=digest[n]&255;hex[n*2]=digits.charAt(b>>>4);hex[n*2+1]=digits.charAt(b&15);}
        return new String(hex);
    }catch(Exception e){throw new IllegalStateException(e);}}
}
