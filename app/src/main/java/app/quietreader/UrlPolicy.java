package app.quietreader;

import java.net.URI;
import java.net.InetAddress;
import java.util.Locale;

public final class UrlPolicy {
    private UrlPolicy() {}
    public static String host(String url) {
        try { URI u=new URI(url); return u.getHost()==null?"":u.getHost().toLowerCase(Locale.ROOT); } catch(Exception e) { return ""; }
    }
    public static boolean https(String url) {
        try { URI u=new URI(url); return "https".equalsIgnoreCase(u.getScheme()) && u.getUserInfo()==null && (u.getPort()==-1||u.getPort()==443) && !host(url).isEmpty(); } catch(Exception e) { return false; }
    }
    public static boolean domain(String host,String domain) { return host.equals(domain)||host.endsWith("."+domain); }
    public static boolean belongs(Models.Source source,String url) {
        if(!https(url)) return false;
        String h=host(url);
        switch(source) {
            case WEIBO: return domain(h,"weibo.com")||domain(h,"weibo.cn");
            case ZHIHU: return domain(h,"zhihu.com");
            case HUPU: return domain(h,"hupu.com");
            case CLS: return domain(h,"cls.cn");
            case IFANR: return domain(h,"ifanr.com");
            default: return false;
        }
    }
    public static boolean loginAllowed(Models.Source source,String url) {
        if(belongs(source,url))return true;
        if(!https(url))return false;
        String h=host(url);
        if(source!=Models.Source.WEIBO)return false;
        return h.equals("login.sina.com.cn")||h.equals("passport.sina.cn")||h.equals("passport.sina.com.cn")||h.equals("passport.sinaimg.cn");
    }
    /** Upgrade only ordinary HTTP platform links, never custom schemes or authentication exceptions.
     * Preserve encoded path/query bytes: decoding and rebuilding can change search or signed URLs.
     * The caller must still cancel the HTTP navigation and explicitly load the HTTPS result.
     */
    public static String upgradePlatformNavigation(Models.Source source,String url) {
        try {
            URI u=new URI(url);
            if(!"http".equalsIgnoreCase(u.getScheme())||u.getUserInfo()!=null||
                    (u.getPort()!=-1&&u.getPort()!=80)||host(url).isEmpty())return "";
            String upgraded="https://"+host(url)+(u.getRawPath()==null?"":u.getRawPath())+
                    (u.getRawQuery()==null?"":"?"+u.getRawQuery())+
                    (u.getRawFragment()==null?"":"#"+u.getRawFragment());
            return belongs(source,upgraded)?upgraded:"";
        }catch(Exception ignored){return "";}
    }
    public static String normalize(String base,String raw) {
        try {
            if(raw==null||raw.trim().isEmpty()||raw.startsWith("#")) return "";
            String url=new URI(base).resolve(raw.replace(" ","%20")).toString();
            if(url.startsWith("http://")) url="https://"+url.substring(7);
            return https(url)?url:"";
        } catch(Exception e) { return ""; }
    }
    public static boolean sameThread(Models.Source source,String current,String next) {
        return !current.equals(next)&&sameForumPost(source,current,next);
    }
    /** Known forum post identity, allowing canonical query/pagination changes but not another post. */
    public static boolean sameForumPost(Models.Source source,String current,String next) {
        if(!belongs(source,current)||!belongs(source,next))return false;
        String pattern=source==Models.Source.HUPU?"^/(\\d+)(?:-\\d+)?\\.html$":"";
        if(pattern.isEmpty())return false;
        try {
            java.util.regex.Pattern regex=java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher a=regex.matcher(new URI(current).getPath()),b=regex.matcher(new URI(next).getPath());
            return a.matches()&&b.matches()&&a.group(1).equals(b.group(1));
        }catch(Exception e){return false;}
    }
    public static void requirePublicHost(String url) throws Exception {
        if(!https(url)) throw new SecurityException("仅允许 HTTPS");
        for(InetAddress a:InetAddress.getAllByName(host(url))) {
            byte[] bytes=a.getAddress();
            if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress()||(bytes.length==16&&(bytes[0]&0xfe)==0xfc))
                throw new SecurityException("拒绝本地网络地址");
        }
    }
}
