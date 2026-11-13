package app.quietreader.discovery;

import app.quietreader.Models.Source;
import java.util.*;

/** Supply-independent source shares, followed by source-local relevance/history order. */
public final class FeedSourceBalance {
    private FeedSourceBalance(){}
    public static List<FeedStore.Row> arrange(List<FeedStore.Row> rows){return arrange(rows,null,0);}
    public static List<FeedStore.Row> arrange(List<FeedStore.Row> rows,PlatformPreference preference){return arrange(rows,preference,0);}
    public static List<FeedStore.Row> arrange(List<FeedStore.Row> rows,PlatformPreference preference,long turn){
        return arrange(rows,preference,turn,false);
    }
    static List<FeedStore.Row> arrangeDelivered(List<FeedStore.Row> rows,PlatformPreference preference,long turn){
        return spread(rows);
    }
    /** Once membership is fixed, distribute its actual source totals across the
     * whole window. Reallocating equal shares per page would exhaust small
     * sources first and leave the large source as an all-source final page. */
    public static List<FeedStore.Row> spread(List<FeedStore.Row> rows){
        List<FeedStore.Row> usable=new ArrayList<>(),negative=new ArrayList<>();
        for(FeedStore.Row row:rows)(row.interestBand==2?negative:usable).add(row);
        List<FeedStore.Row> result=spreadRows(usable);result.addAll(spreadRows(negative));return result;
    }
    private static List<FeedStore.Row> spreadRows(List<FeedStore.Row> rows){
        Map<Source,ArrayDeque<FeedStore.Row>> queues=new EnumMap<>(Source.class);
        Map<Source,Integer> totals=new EnumMap<>(Source.class),used=new EnumMap<>(Source.class),first=new EnumMap<>(Source.class);
        for(int n=0;n<rows.size();n++){
            FeedStore.Row row=rows.get(n);Source source=row.candidate.source;
            queues.computeIfAbsent(source,k->new ArrayDeque<>()).add(row);totals.merge(source,1,Integer::sum);first.putIfAbsent(source,n);
        }
        List<FeedStore.Row> result=new ArrayList<>();
        while(!queues.isEmpty()){
            Source chosen=null;double earliest=Double.MAX_VALUE;int position=Integer.MAX_VALUE;
            for(Source source:queues.keySet()){
                double due=(used.getOrDefault(source,0)+0.5)/totals.get(source);
                if(due<earliest||due==earliest&&first.get(source)<position){chosen=source;earliest=due;position=first.get(source);}
            }
            ArrayDeque<FeedStore.Row> queue=queues.get(chosen);result.add(queue.remove());used.merge(chosen,1,Integer::sum);if(queue.isEmpty())queues.remove(chosen);
        }
        return result;
    }
    private static List<FeedStore.Row> arrange(List<FeedStore.Row> rows,PlatformPreference preference,long turn,boolean deliveryOrdered){
        List<FeedStore.Row> result=new ArrayList<>();
        Map<Integer,Map<Source,ArrayDeque<Integer>>> bands=new TreeMap<>();
        for(int n=0;n<rows.size();n++){
            FeedStore.Row row=rows.get(n);int band=row.interestBand==2?2:0;
            bands.computeIfAbsent(band,k->new EnumMap<>(Source.class)).computeIfAbsent(row.candidate.source,k->new ArrayDeque<>()).add(n);
        }
        // Positive and neutral content share source budgets; otherwise a large
        // source could bypass its budget merely by filling an entire quality band.
        // Content-negative rows remain a separate fallback after usable content.
        // Once delivery history has ordered usable rows, do not promote an old
        // positive above a fresh neutral again. Negative content stays last.
        if(!deliveryOrdered)for(Map<Source,ArrayDeque<Integer>> queues:bands.values())for(Source source:queues.keySet()){
            List<Integer> ordered=new ArrayList<>(queues.get(source));ordered.sort(Comparator.comparingInt(n->rows.get(n).interestBand==0?0:1));
            queues.put(source,new ArrayDeque<>(ordered));
        }
        for(Map<Source,ArrayDeque<Integer>> queues:bands.values())while(!queues.isEmpty()){
            int remaining=0;for(ArrayDeque<Integer> queue:queues.values())remaining+=queue.size();
            int slots=Math.min(FeedBatch.SIZE-result.size()%FeedBatch.SIZE,remaining);
            Map<Source,Integer> quotas=allocate(queues,preference,slots,turn+result.size()/FeedBatch.SIZE);
            Map<Source,Integer> emitted=new EnumMap<>(Source.class);
            for(int slot=0;slot<slots;slot++){
                Source chosen=null;double best=Double.MAX_VALUE;int first=Integer.MAX_VALUE;
                for(Map.Entry<Source,Integer> entry:quotas.entrySet()){
                    Source source=entry.getKey();int used=emitted.getOrDefault(source,0),quota=entry.getValue();if(used>=quota)continue;
                    double due=(used+0.5)/quota;int position=queues.get(source).peek();
                    if(due<best||due==best&&position<first){chosen=source;best=due;first=position;}
                }
                ArrayDeque<Integer> queue=queues.get(chosen);result.add(rows.get(queue.remove()));emitted.merge(chosen,1,Integer::sum);
                if(queue.isEmpty())queues.remove(chosen);
            }
        }
        return result;
    }
    private static Map<Source,Integer> allocate(Map<Source,ArrayDeque<Integer>> queues,PlatformPreference preference,int slots,long turn){
        Map<Source,Integer> quotas=new EnumMap<>(Source.class);Map<Source,Double> weights=new EnumMap<>(Source.class);
        for(Source source:queues.keySet())weights.put(source,preference==null?1:1+0.75*Math.max(0,preference.score(source)));
        int left=slots;
        // Water-fill only when a source cannot supply its share. Pool length is
        // a capacity bound, never a source's weight or a popularity signal.
        while(!weights.isEmpty()){
            double total=weights.values().stream().mapToDouble(Double::doubleValue).sum();List<Source> shortSources=new ArrayList<>();
            for(Source source:weights.keySet())if(queues.get(source).size()<left*weights.get(source)/total)shortSources.add(source);
            if(shortSources.isEmpty())break;
            for(Source source:shortSources){int size=queues.get(source).size();quotas.put(source,size);left-=size;weights.remove(source);}
        }
        if(left==0)return quotas;
        double total=weights.values().stream().mapToDouble(Double::doubleValue).sum(),fractionSum=0;int extra=left;
        Map<Source,Double> fractions=new EnumMap<>(Source.class);
        for(Source source:weights.keySet()){
            double ideal=left*weights.get(source)/total;int base=(int)Math.floor(ideal);quotas.put(source,base);extra-=base;
            fractions.put(source,ideal-base);fractionSum+=ideal-base;
        }
        // Systematic fractional allocation keeps the batch exact and rotates
        // indivisible slots across actual presentations, including small shares.
        double phase=(0.5+Math.floorMod(turn,1_000_003L)*0.6180339887498949)%1;
        Iterator<Map.Entry<Source,Double>> iterator=fractions.entrySet().iterator();Map.Entry<Source,Double> current=iterator.hasNext()?iterator.next():null;
        double end=current==null?0:current.getValue();
        for(int n=0;n<extra;n++){
            double point=(n+phase)*fractionSum/extra;
            while(point>=end&&iterator.hasNext()){current=iterator.next();end+=current.getValue();}
            quotas.merge(current.getKey(),1,Integer::sum);
        }
        return quotas;
    }
}
