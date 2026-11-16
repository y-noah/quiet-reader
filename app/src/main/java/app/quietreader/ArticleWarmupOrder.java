package app.quietreader;

import java.util.*;
import java.util.function.Predicate;
import static app.quietreader.Models.*;

/** Changes acquisition order only; every visible item precedes future screens. */
final class ArticleWarmupOrder {
    private ArticleWarmupOrder(){}
    static List<Item> arrange(List<Item> visible,List<Item> future,Predicate<Source> browser){
        List<Item> ordered=new ArrayList<>(visible);
        // One long browser hydration must not hold all cheap visible bodies behind it.
        // Stable sort preserves reading order within each acquisition route.
        ordered.sort(Comparator.comparingInt(item->browser.test(item.source)?2:item.source.webReading()?1:0));
        ordered.addAll(future);
        return ordered;
    }
}
