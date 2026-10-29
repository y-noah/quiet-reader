package app.quietreader.discovery;

import app.quietreader.Models.Source;
import java.util.*;

/** A view of a frozen order. Filtering never changes scores or feedback. */
public final class SourceFilter {
    private SourceFilter(){}
    public static String source(String value){try{String name=value.split("\\|",-1)[0];return HotSourceCatalog.eligible(Source.valueOf(name))?name:"all";}catch(Exception ignored){return "all";}}
    public static ContentCategory category(String value){if(value==null)return ContentCategory.ALL;String[] parts=value.split("\\|",-1);return parts.length==2?ContentCategory.parse(parts[1]):ContentCategory.ALL;}
    public static String combine(String source,ContentCategory category){return source(source)+(category==ContentCategory.ALL?"":"|"+category.name());}
    public static String normalize(String value){return combine(source(value),category(value));}
    public static List<FeedStore.Row> apply(List<FeedStore.Row> rows,String filter){
        filter=normalize(filter);if("all".equals(filter))return new ArrayList<>(rows);
        String name=source(filter);Source source="all".equals(name)?null:Source.valueOf(name);ContentCategory category=category(filter);List<FeedStore.Row> selected=new ArrayList<>();
        for(FeedStore.Row row:rows){
            HotCandidate match=null;
            for(HotCandidate entry:row.entries)if(entry.id.equals(row.candidate.id)&&entry.textHash.equals(row.candidate.textHash)&&matches(entry,source,category)){match=entry;break;}
            // Filtering must retain the chosen reader entry when it qualifies,
            // including after SQLite has deserialized main/entries separately.
            if(match==null)for(HotCandidate entry:row.entries)if(matches(entry,source,category)){match=entry;break;}
            if(match!=null)selected.add(new FeedStore.Row(row.eventId,match,row.reason,row.entries,row.interestBand,row.refreshBasePosition));
        }
        return selected;
    }
    private static boolean matches(HotCandidate entry,Source source,ContentCategory category){return (source==null||entry.source==source)&&(category==ContentCategory.ALL||ContentCategory.of(entry)==category);}
}
