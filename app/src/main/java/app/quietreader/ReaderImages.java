package app.quietreader;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

/** Bounded image caches scoped to every observed request/session URL. */
final class ReaderImages {
    static final ReaderImages shared=new ReaderImages();
    interface Fetcher {byte[] fetch(String url,String referer,Function<String,String> cookies)throws Exception;}
    interface CancellableFetcher {byte[] fetch(String url,String referer,Function<String,String> cookies,RequestQueue.Ticket ticket,int limit)throws Exception;}
    private static final class Entry {
        final String url;final byte[] bytes;final Map<String,String> sessions;final long weight;
        Entry(String url,String referer,byte[] bytes,Map<String,String> sessions){
            this.url=url;this.bytes=bytes;this.sessions=new LinkedHashMap<>(sessions);
            long size=bytes.length+256L+4L*(url.length()+(referer==null?0:referer.length()));
            for(String target:sessions.keySet())size+=2L*target.length()+192;
            weight=size;
        }
    }
    private final Function<String,String> cookies;
    private final CancellableFetcher fetcher;
    private final int budget;
    private long size;
    private long epoch;
    private volatile ImageDiskCache disk;
    private volatile java.util.function.Supplier<String> namespace=()->"memory";
    private final LinkedHashSet<String> invalidated=new LinkedHashSet<>();
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>(16,.75f,true);
    private final RequestQueue sockets=new RequestQueue();
    private final Map<String,Flight> flights=new HashMap<>();
    private static final int MAX_FLIGHTS=8;
    private final class Flight {
        final String key,scope,url;final long revision;final RequestQueue.Ticket ticket;
        final Map<Scope,Integer> owners=new HashMap<>();Entry result;Exception failure;boolean done;
        Flight(String key,String scope,String url,long revision){this.key=key;this.scope=scope;this.url=url;this.revision=revision;ticket=sockets.register();}
    }
    final class Scope implements AutoCloseable {
        private final java.util.function.BooleanSupplier allowed;
        private final Set<Flight> pending=new HashSet<>();private volatile boolean closed;
        private Scope(java.util.function.BooleanSupplier allowed){this.allowed=allowed;}
        byte[] load(String url,String referer)throws Exception{return scoped(this,url,referer);}
        boolean stop(){synchronized(ReaderImages.this){if(closed)return false;boolean interrupted=!pending.isEmpty();closed=true;for(Flight flight:new ArrayList<>(pending)){flight.owners.remove(this);cancelUnused(flight);}pending.clear();ReaderImages.this.notifyAll();return interrupted;}}
        @Override public void close(){stop();}
    }
    Scope openScope(java.util.function.BooleanSupplier allowed){return new Scope(allowed);}
    private void checkScope(Scope owner)throws IOException{if(owner.closed||!owner.allowed.getAsBoolean()||Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Image reader is inactive");}
    private void cancelUnused(Flight flight){if(!flight.done&&flight.owners.isEmpty()){if(flights.get(flight.key)==flight)flights.remove(flight.key);sockets.cancel(flight.ticket);}}
    private boolean needed(Flight flight){synchronized(this){for(Scope owner:flight.owners.keySet())if(!owner.closed&&owner.allowed.getAsBoolean())return true;return false;}}
    private byte[] scoped(Scope owner,String url,String referer)throws Exception{
        Flight flight;boolean leader;
        while(true){
            checkScope(owner);String scope=namespace.get(),identity=scope+'\0'+key(url,referer)+'\0'+fingerprint(url)+'\0'+(referer==null?"":fingerprint(referer));
            synchronized(this){
                checkScope(owner);String key=epoch+"\0"+identity;flight=flights.get(key);leader=flight==null;
                if(leader&&flights.size()>=MAX_FLIGHTS){try{wait(100);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new java.io.InterruptedIOException("Image reader interrupted");}continue;}
                if(leader){flight=new Flight(key,scope,url,epoch);flights.put(key,flight);}
                flight.owners.put(owner,flight.owners.getOrDefault(owner,0)+1);owner.pending.add(flight);break;
            }
        }
        final Flight shared=flight;
        try{
            if(leader){
                Entry result=null;Exception failure=null;
                try{result=acquire(url,referer,shared.ticket,()->needed(shared),8*1024*1024,true);}catch(Exception error){failure=error;}
                finally{synchronized(this){shared.result=result;shared.failure=failure;shared.done=true;if(flights.get(shared.key)==shared)flights.remove(shared.key);sockets.release(shared.ticket);notifyAll();}}
            }else synchronized(this){while(!shared.done){checkScope(owner);try{wait(100);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new java.io.InterruptedIOException("Image reader interrupted");}}}
            checkScope(owner);if(shared.failure!=null)throw shared.failure;
            if(shared.result==null||!shared.scope.equals(namespace.get())||!unchanged(shared.revision)||!current(shared.result.sessions))throw new IOException("图片登录或缓存状态已变化，请重试。");
            return shared.result.bytes;
        }finally{synchronized(this){Integer count=shared.owners.get(owner);if(count!=null){if(count>1)shared.owners.put(owner,count-1);else{shared.owners.remove(owner);owner.pending.remove(shared);}}cancelUnused(shared);notifyAll();}}
    }
    ReaderImages(){this(url->android.webkit.CookieManager.getInstance().getCookie(url),(CancellableFetcher)Repository::imageBytes,2*1024*1024);}
    ReaderImages(Function<String,String> cookies,Fetcher fetcher,int budget){this(cookies,(url,ref,scoped,ticket,limit)->fetcher.fetch(url,ref,scoped),budget);}
    ReaderImages(Function<String,String> cookies,CancellableFetcher fetcher,int budget){this.cookies=cookies;this.fetcher=fetcher;this.budget=budget;}
    void attach(android.content.Context context){android.content.Context app=context.getApplicationContext();attach(new ImageDiskCache(new java.io.File(app.getCacheDir(),"reader-images-v1")),()->app.getSharedPreferences("article-cache",0).getString("namespace","initial"));}
    void attach(ImageDiskCache disk,java.util.function.Supplier<String> namespace){this.disk=disk;this.namespace=namespace;}
    private String fingerprint(String url){return fingerprintCookie(cookies.apply(url));}
    private static String fingerprintCookie(String cookie){return app.quietreader.discovery.HotCandidate.hash(cookie==null?"":cookie);}
    private boolean current(Map<String,String> sessions){for(Map.Entry<String,String> mark:sessions.entrySet())if(!mark.getValue().equals(fingerprint(mark.getKey())))return false;return true;}
    private static String key(String url,String referer){return url+'\0'+(referer==null?"":referer);}
    byte[] load(String url,String referer)throws Exception{
        return load(url,referer,null,()->true,8*1024*1024,true);
    }
    private static void check(RequestQueue.Ticket ticket,java.util.function.BooleanSupplier allowed)throws IOException{
        if(ticket!=null)ticket.check();
        if(!allowed.getAsBoolean())throw new java.io.InterruptedIOException("Image acquisition paused");
    }
    byte[] load(String url,String referer,RequestQueue.Ticket ticket,java.util.function.BooleanSupplier allowed,int limit,boolean retain)throws Exception{
        return acquire(url,referer,ticket,allowed,limit,retain).bytes;
    }
    private Entry acquire(String url,String referer,RequestQueue.Ticket ticket,java.util.function.BooleanSupplier allowed,int limit,boolean retain)throws Exception{
        check(ticket,allowed);
        String scope=namespace.get(),key=scope+'\0'+key(url,referer);Entry hit;long revision;boolean retry;
        synchronized(this){hit=entries.get(key);revision=epoch;retry=invalidated.remove(url);}
        if(hit!=null&&current(hit.sessions)&&scope.equals(namespace.get())&&unchanged(revision)){check(ticket,allowed);if(hit.bytes.length>limit)throw new ImageDiskCache.PayloadLimitException();return hit;}
        if(hit!=null)synchronized(this){if(entries.get(key)==hit){entries.remove(key);size-=hit.weight;}}
        ImageDiskCache storage=disk;
        if(storage!=null){
            if(retry)storage.removeUrl(url);
            ImageDiskCache.Hit saved=retry?null:storage.read(key,url,System.currentTimeMillis(),limit);
            if(saved!=null){
                if(saved.sessions.containsKey(url)&&(referer==null||referer.isEmpty()||saved.sessions.containsKey(referer))&&current(saved.sessions)&&scope.equals(namespace.get())&&unchanged(revision)){check(ticket,allowed);Entry restored=new Entry(url,referer,saved.bytes,saved.sessions);if(retain)store(key,restored);return restored;}
                storage.removeUrl(url);
            }
        }
        Map<String,String> sessions=new LinkedHashMap<>();sessions.put(url,fingerprint(url));if(referer!=null&&!referer.isEmpty())sessions.put(referer,fingerprint(referer));
        boolean[] changed={false};
        check(ticket,allowed);
        byte[] bytes=fetcher.fetch(url,referer,target->{String raw=cookies.apply(target),mark=fingerprintCookie(raw);String prior=sessions.putIfAbsent(target,mark);if(prior!=null&&!prior.equals(mark))changed[0]=true;return raw;},ticket,limit);
        check(ticket,allowed);
        if(changed[0]||!current(sessions)||!scope.equals(namespace.get())||!unchanged(revision))throw new IOException("图片登录或缓存状态已变化，请重试。");
        if(ImageType.detect(bytes)==null)throw new IOException("Not a supported image");
        if(bytes.length>limit)throw new IOException("Image response too large");
        Entry acquired=new Entry(url,referer,bytes,sessions);
        if(retain)synchronized(this){check(ticket,allowed);store(key,acquired);}
        if(storage!=null)storage.write(key,url,sessions,bytes,System.currentTimeMillis(),()->(ticket==null||!ticket.cancelled())&&allowed.getAsBoolean()&&scope.equals(namespace.get())&&unchanged(revision)&&current(sessions));
        check(ticket,allowed);
        if(!scope.equals(namespace.get())||!unchanged(revision)||!current(sessions))throw new IOException("图片登录或缓存状态已变化，请重试。");
        return acquired;
    }
    // Import already-acquired bytes with the caller's current URL/session scope.
    void put(String url,String referer,byte[] bytes){
        if(ImageType.detect(bytes)==null)throw new IllegalArgumentException("Not a supported image");
        Map<String,String> sessions=new LinkedHashMap<>();sessions.put(url,fingerprint(url));if(referer!=null&&!referer.isEmpty())sessions.put(referer,fingerprint(referer));
        synchronized(this){Iterator<Flight> pending=flights.values().iterator();while(pending.hasNext()){Flight flight=pending.next();if(flight.url.equals(url)){sockets.cancel(flight.ticket);pending.remove();}}store(namespace.get()+'\0'+key(url,referer),new Entry(url,referer,bytes,sessions));notifyAll();}
    }
    private synchronized void store(String key,Entry entry){
        Entry old=entries.remove(key);if(old!=null)size-=old.weight;
        if(entry.weight>budget)return;
        entries.put(key,entry);size+=entry.weight;
        Iterator<Entry> iterator=entries.values().iterator();while((size>budget||entries.size()>128)&&iterator.hasNext()){Entry evicted=iterator.next();size-=evicted.weight;iterator.remove();}
    }
    private synchronized boolean unchanged(long revision){return epoch==revision;}
    private void invalidateFlights(){for(Flight flight:flights.values())sockets.cancel(flight.ticket);flights.clear();notifyAll();}
    synchronized void remove(String url){epoch++;invalidateFlights();invalidated.add(url);if(invalidated.size()>128)invalidated.remove(invalidated.iterator().next());Iterator<Entry> iterator=entries.values().iterator();while(iterator.hasNext()){Entry entry=iterator.next();if(entry.url.equals(url)){size-=entry.weight;iterator.remove();}}}
    synchronized void evictAll(){epoch++;invalidateFlights();entries.clear();size=0;}
    synchronized Runnable retainBatch(Set<String> urls){
        epoch++;invalidateFlights();long captured=epoch;Set<String> keep=new HashSet<>(urls);
        Iterator<Entry> iterator=entries.values().iterator();while(iterator.hasNext()){
            Entry entry=iterator.next();boolean retained=false;for(String url:keep)if(entry.sessions.containsKey(url)){retained=true;break;}
            if(!retained){size-=entry.weight;iterator.remove();}
        }
        ImageDiskCache storage=disk;
        return ()->{if(storage!=null)storage.retainOwners(keep,()->unchanged(captured));};
    }
}
