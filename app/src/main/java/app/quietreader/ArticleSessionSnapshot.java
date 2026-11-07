package app.quietreader;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** Reuse cookie fingerprints only within one synchronous validation operation. */
final class ArticleSessionSnapshot {
    private static final ThreadLocal<Map<String,String>> current=new ThreadLocal<>();
    static <T> T within(Supplier<T> action){
        if(current.get()!=null)return action.get();
        current.set(new HashMap<>());
        try{return action.get();}finally{current.remove();}
    }
    static void run(Runnable action){within(()->{action.run();return null;});}
    static String read(String url,Supplier<String> acquire){
        Map<String,String> values=current.get();
        return values==null?acquire.get():values.computeIfAbsent(url,key->acquire.get());
    }
}
