package app.quietreader;

import app.quietreader.discovery.HotCandidate;
import java.net.URI;
import java.util.regex.*;
import static app.quietreader.Models.Source;

/** Original article identity, deliberately independent of topic grouping and title. */
public final class ArticleReadIdentity {
    private ArticleReadIdentity(){}
    public static String key(Source source,String url){
        String canonical=HotCandidate.canonical(url);
        URI uri=URI.create(canonical);
        String pattern=source==Source.V2EX?"^/t/(\\d+)/?$":source==Source.TIEBA?"^/p/(\\d+)/?$":source==Source.HUPU?"^/(\\d+)(?:-\\d+)?\\.html$":"";
        if(!pattern.isEmpty()&&UrlPolicy.belongs(source,canonical)){
            Matcher match=Pattern.compile(pattern).matcher(uri.getPath());
            if(match.matches())return HotCandidate.hash(source.name()+"\npost:"+match.group(1));
        }
        // Preserve unknown hash routes and query IDs; ordinary in-page anchors
        // do not turn a previously read article into a new original.
        String fragment=uri.getRawFragment();
        if(fragment!=null&&!fragment.startsWith("/")&&!fragment.startsWith("!"))canonical=canonical.substring(0,canonical.indexOf('#'));
        return HotCandidate.hash(source.name()+"\n"+canonical);
    }
    public static String key(HotCandidate candidate){return key(candidate.source,candidate.originalUrl);}
}
