package app.quietreader;

import java.util.*;

/** Cache identity, separate from the complete cookies still sent to the source. */
final class ArticleCookies {
    private ArticleCookies(){}
    static String fingerprint(String url,String raw){return app.quietreader.discovery.HotCandidate.hash(identity(url,raw));}
    static String identity(String url,String raw){
        if(raw==null)raw="";
        // Cookie header order and analytics/CSRF request context are not account
        // changes. Keep authentication, device/risk and every unknown cookie.
        // Stable sorting retains relative order of duplicate-name path cookies.
        if(zhihuContent(url)){
            boolean authenticated=false;
            for(String field:raw.split(";")){String value=field.trim();if(cookieName(value).equals("z_c0")&&value.indexOf('=')>=0&&!value.substring(value.indexOf('=')+1).trim().isEmpty())authenticated=true;}
            List<String> fields=new ArrayList<>();
            for(String field:raw.split(";")){
                String value=field.trim(),name=cookieName(value);
                if(name.equals("_xsrf")||name.equals("_zap")||name.equals("_gid")||name.equals("_gat")||name.equals("_ga")||name.startsWith("_ga_")||name.startsWith("_gat_"))continue;
                if(name.matches("Hm_(?:lpvt|lvt)_[a-fA-F0-9]{32}"))continue;
                // These page-context fields rotate on every ordinary authenticated
                // navigation. Only use the narrower boundary when z_c0 is present;
                // its complete value and all device/unknown fields remain identity.
                if(authenticated&&(name.equals("BEC")||name.equals("JOID")||name.equals("osd")||name.equals("SESSIONID")))continue;
                if(!value.isEmpty())fields.add(value);
            }
            fields.sort(Comparator.comparing(ArticleCookies::cookieName));
            return "zhihu-content-v2;"+String.join("; ",fields);
        }
        // Activity and recommendation-feed context can rotate during a read of
        // one fixed topic. Keep every account/unknown field, including risk tokens.
        if(DouyinArticle.sameContent(url,url)){
            List<String> fields=new ArrayList<>();
            for(String field:raw.split(";")){
                String value=field.trim();int at=value.indexOf('=');
                String name=at>0?value.substring(0,at).trim():"";
                if(name.equals("IsDouyinActive")||name.equals("stream_recommend_feed_params"))continue;
                if(!value.isEmpty())fields.add(value);
            }
            return "douyin-content-v2;"+String.join("; ",fields);
        }
        // Search rewrites page/risk/experiment fields on every load. Separate the
        // account/session identity from that request context; send ALL cookies as usual.
        boolean search=false,tieba=false;
        try{java.net.URI uri=java.net.URI.create(url);tieba=UrlPolicy.https(url)&&"tieba.baidu.com".equals(uri.getHost())&&(uri.getPath().matches("/p/\\d+/?")||TiebaTopic.isTopic(url));search=tieba||UrlPolicy.https(url)&&"www.baidu.com".equals(uri.getHost())&&"/s".equals(uri.getPath());}catch(Exception ignored){}
        if(!search)return raw;
        List<String> fields=new ArrayList<>();
        for(String field:raw.split(";")){
            int at=field.indexOf('=');if(at<=0)continue;String name=field.substring(0,at).trim().toUpperCase(Locale.ROOT);
            // Observed anonymous topic -> new-PC-post navigation rotates these
            // visitor/analytics fields. HMACCOUNT even contains the substring
            // ACCOUNT, but is a statistics identifier, not an account session.
            if(tieba&&Set.of("BAIDUID","BAIDUID_BFESS","BAIDU_WISE_UID","HMACCOUNT").contains(name))continue;
            if(name.matches("BDUSS.*|[PS]TOKEN.*|PASSID.*|.*(?:SESSION|AUTH|TOKEN|LOGIN|USER|ACCOUNT).*|.*UID|SID|SSON"))fields.add(field.trim());
        }
        Collections.sort(fields);return (tieba?"tieba-account-v2;":"baidu-account-v1;")+String.join("; ",fields);
    }
    private static String cookieName(String field){int at=field.indexOf('=');return at>0?field.substring(0,at).trim():field;}
    private static boolean zhihuContent(String url){
        try{java.net.URI uri=java.net.URI.create(url);return UrlPolicy.https(url)&&"www.zhihu.com".equals(uri.getHost())&&uri.getPort()==-1&&uri.getPath().matches("/question/[1-9][0-9]*(?:/answer/[1-9][0-9]*)?/?");}catch(Exception ignored){return false;}
    }
}
