package app.quietreader;

import java.text.Normalizer;
import java.util.*;
import static app.quietreader.Models.*;

/** A transparent editorial index, not an estimate of comparable platform view counts. */
final class AggregateRanker {
    static final int MAX_RESULTS=50;
    static final long MAX_AGE=24L*60*60*1000;
    static final class Feed {
        final Source source; final List<Item> items; final long fetchedAt;
        Feed(Source source,List<Item> items,long fetchedAt){this.source=source;this.items=items;this.fetchedAt=fetchedAt;}
    }
    static final class Entry {
        final Item primary; final List<Item> alternatives; final double score; final String detail;
        Entry(Item primary,List<Item> alternatives,double score,String detail){this.primary=primary;this.alternatives=Collections.unmodifiableList(alternatives);this.score=score;this.detail=detail;}
    }
    private static final class Vote {Item item;double score;int rank;Vote(Item i,double s,int r){item=i;score=s;rank=r;}}
    private static String key(String title){
        String normalized=Normalizer.normalize(title,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        String letters=normalized.replaceAll("[^\\p{L}\\p{N}]","");if(letters.isEmpty())return "";
        StringBuilder numbers=new StringBuilder();java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("[+−-]?\\d+(?:[.,:/-]\\d+)*%?").matcher(normalized);
        while(matcher.find())numbers.append('|').append(matcher.group().replace('−','-'));
        return letters+numbers;
    }
    // Editorial reading priorities, NOT measured audience sizes or comparable heat.
    static double weight(Source s){
        switch(s){
            case ZHIHU:case WEIBO:return 1;
            case HUPU:return .7;
            case CLS:return .65;
            case IFANR:return .2;
            default:return 0;
        }
    }
    static int sourceLimit(Source s){
        switch(s){
            case ZHIHU:case WEIBO:return 20;
            case HUPU:case CLS:return 12;
            case IFANR:return 8;
            default:return 0;
        }
    }
    private static int sourceOrder(Source s){Source[] order=Source.aggregateSources();for(int n=0;n<order.length;n++)if(order[n]==s)return n;return 100;}
    static List<Entry> rank(List<Feed> feeds,long now){
        Map<Source,Feed> unique=new EnumMap<>(Source.class);
        for(Feed f:feeds)if(f.source.visible()&&f.fetchedAt>0&&now-f.fetchedAt<=MAX_AGE){
            Feed old=unique.get(f.source);if(old==null||f.fetchedAt>old.fetchedAt)unique.put(f.source,f);
        }
        Map<String,Map<Source,Vote>> groups=new LinkedHashMap<>();
        for(Source source:Source.aggregateSources()){
            Feed feed=unique.get(source);if(feed==null)continue;
            Set<String> urls=new HashSet<>();int position=0;
            for(Item item:feed.items){
                if(position>=50)break;
                if(item.source!=source||item.video||!urls.add(item.url))continue;
                String title=key(item.title);if(title.isEmpty())continue;
                position++;
                // Sub-minute endpoint completion differences must not decide tied rankings.
                long ageBucket=Math.max(0,now-feed.fetchedAt)/(15*60*1000);
                double score=1100.0/(10+position)*weight(source)*Math.pow(.5,ageBucket/48.0);
                Map<Source,Vote> votes=groups.computeIfAbsent(title,k->new EnumMap<>(Source.class));
                if(!votes.containsKey(source))votes.put(source,new Vote(item,score,position));
            }
        }
        List<Entry> ranked=new ArrayList<>();
        Comparator<Vote> order=Comparator.<Vote>comparingDouble(v->-v.score).thenComparingInt(v->sourceOrder(v.item.source)).thenComparing(v->v.item.url);
        for(Map<Source,Vote> group:groups.values()){
            List<Vote> votes=new ArrayList<>(group.values());votes.sort(order);
            Vote best=votes.get(0);double score=best.score;List<Item> alternatives=new ArrayList<>();StringBuilder detail=new StringBuilder();
            for(int n=0;n<votes.size();n++){Vote v=votes.get(n);alternatives.add(v.item);if(n>0){score+=.4*v.score;detail.append(" · ");}detail.append(v.item.source.label).append(" #").append(v.rank);}
            if(votes.size()>1)detail.append(" · ").append(votes.size()).append("个平台");
            if(best.item.source==Source.IFANR)detail.append(" · ").append(best.item.source.category);
            ranked.add(new Entry(best.item,alternatives,score,detail.toString()));
        }
        ranked.sort(Comparator.<Entry>comparingDouble(e->-e.score).thenComparingInt(e->sourceOrder(e.primary.source)).thenComparing(e->e.primary.url));
        List<Entry> result=new ArrayList<>();Map<Source,Integer> quota=new EnumMap<>(Source.class);
        for(Entry e:ranked){int count=quota.getOrDefault(e.primary.source,0);if(count>=sourceLimit(e.primary.source))continue;quota.put(e.primary.source,count+1);result.add(e);if(result.size()==MAX_RESULTS)break;}
        return result;
    }
}
