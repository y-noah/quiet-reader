package app.quietreader;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class ReaderImageScopeTest {
    private static final String URL="https://image.example/a.gif",PAGE="https://example/article/1";
    private static byte[] bytes(){return "GIF89a123456789".getBytes(StandardCharsets.US_ASCII);}
    private static int subscribers(ReaderImages cache)throws Exception{
        synchronized(cache){Field flights=ReaderImages.class.getDeclaredField("flights");flights.setAccessible(true);int count=0;for(Object flight:((Map<?,?>)flights.get(cache)).values()){Field owners=flight.getClass().getDeclaredField("owners");owners.setAccessible(true);for(Object number:((Map<?,?>)owners.get(flight)).values())count+=(Integer)number;}return count;}
    }
    private static void waitSubscribers(ReaderImages cache,int count)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(subscribers(cache)!=count){if(System.nanoTime()>end)fail("Subscriber count did not settle at "+count);Thread.sleep(5);}}
    private static void rejected(Future<byte[]> future)throws Exception{try{future.get(3,TimeUnit.SECONDS);fail("Inactive or failed image accepted");}catch(ExecutionException expected){assertTrue(expected.getCause() instanceof IOException);}}
    @Test public void identicalRequestsShareOneFetchAndClosingOneOwnerPreservesTheOther()throws Exception{
        AtomicInteger calls=new AtomicInteger();CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        ReaderImages cache=new ReaderImages(url->null,(ReaderImages.CancellableFetcher)(url,ref,cookies,ticket,limit)->{calls.incrementAndGet();started.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));ticket.check();return bytes();},4096);
        ExecutorService work=Executors.newFixedThreadPool(2);ReaderImages.Scope first=cache.openScope(()->true),second=cache.openScope(()->true);
        try{Future<byte[]> a=work.submit(()->first.load(URL,PAGE));assertTrue(started.await(2,TimeUnit.SECONDS));Future<byte[]> b=work.submit(()->second.load(URL,PAGE));waitSubscribers(cache,2);first.close();release.countDown();rejected(a);assertArrayEquals(bytes(),b.get(3,TimeUnit.SECONDS));assertEquals(1,calls.get());assertArrayEquals(bytes(),second.load(URL,PAGE));assertEquals(1,calls.get());}
        finally{first.close();second.close();release.countDown();work.shutdownNow();}
    }
    @Test public void lastOwnerDisconnectsTheSocketAndLateBytesDoNotEnterTheCache()throws Exception{
        CountDownLatch started=new CountDownLatch(1),disconnected=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        HttpURLConnection connection=new HttpURLConnection(new URL(URL)){public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){disconnected.countDown();}};
        ReaderImages cache=new ReaderImages(url->null,(ReaderImages.CancellableFetcher)(url,ref,cookies,ticket,limit)->{if(calls.incrementAndGet()==1){ticket.bind(connection);started.countDown();assertTrue(disconnected.await(3,TimeUnit.SECONDS));ticket.unbind(connection);}return bytes();},4096);
        ExecutorService work=Executors.newSingleThreadExecutor();ReaderImages.Scope old=cache.openScope(()->true),fresh=cache.openScope(()->true);
        try{Future<byte[]> pending=work.submit(()->old.load(URL,PAGE));assertTrue(started.await(2,TimeUnit.SECONDS));old.close();assertTrue(disconnected.await(2,TimeUnit.SECONDS));rejected(pending);assertArrayEquals(bytes(),fresh.load(URL,PAGE));assertEquals(2,calls.get());}
        finally{old.close();fresh.close();disconnected.countDown();work.shutdownNow();}
    }
    @Test public void interruptedDuplicateInTheSameScopeDoesNotCancelItsLeader()throws Exception{
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();AtomicBoolean cancelled=new AtomicBoolean();
        ReaderImages cache=new ReaderImages(url->null,(ReaderImages.CancellableFetcher)(url,ref,cookies,ticket,limit)->{calls.incrementAndGet();started.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));cancelled.set(ticket.cancelled());return bytes();},1);
        ExecutorService work=Executors.newFixedThreadPool(2);ReaderImages.Scope owner=cache.openScope(()->true);
        try{Future<byte[]> first=work.submit(()->owner.load(URL,PAGE));assertTrue(started.await(2,TimeUnit.SECONDS));Future<byte[]> duplicate=work.submit(()->owner.load(URL,PAGE));waitSubscribers(cache,2);duplicate.cancel(true);waitSubscribers(cache,1);release.countDown();assertArrayEquals(bytes(),first.get(3,TimeUnit.SECONDS));assertFalse(cancelled.get());assertEquals(1,calls.get());}
        finally{owner.close();release.countDown();work.shutdownNow();}
    }
    @Test public void redirectedSessionChangesRejectEveryJoinedReader()throws Exception{
        Map<String,String> cookies=new ConcurrentHashMap<>();String redirect="https://cdn.example/final.gif";cookies.put(redirect,"a");CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        ReaderImages cache=new ReaderImages(cookies::get,(ReaderImages.CancellableFetcher)(url,ref,scoped,ticket,limit)->{scoped.apply(redirect);started.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));return bytes();},4096);
        ExecutorService work=Executors.newFixedThreadPool(2);ReaderImages.Scope first=cache.openScope(()->true),second=cache.openScope(()->true);
        try{Future<byte[]> a=work.submit(()->first.load(URL,PAGE));assertTrue(started.await(2,TimeUnit.SECONDS));Future<byte[]> b=work.submit(()->second.load(URL,PAGE));waitSubscribers(cache,2);cookies.put(redirect,"b");release.countDown();rejected(a);rejected(b);assertEquals(0,subscribers(cache));}
        finally{first.close();second.close();release.countDown();work.shutdownNow();}
    }
    @Test public void failedSharedFetchAllowsANewAttempt()throws Exception{
        AtomicInteger calls=new AtomicInteger();ReaderImages cache=new ReaderImages(url->null,(url,ref,cookies)->{if(calls.incrementAndGet()==1)throw new IOException("Synthetic failure");return bytes();},4096);
        try(ReaderImages.Scope owner=cache.openScope(()->true)){try{owner.load(URL,PAGE);fail("Failure accepted");}catch(IOException expected){}assertArrayEquals(bytes(),owner.load(URL,PAGE));assertEquals(2,calls.get());}
    }
    @Test public void differentArticleReferersDoNotShareTheirAcquisition()throws Exception{
        AtomicInteger calls=new AtomicInteger();ReaderImages cache=new ReaderImages(url->null,(url,ref,cookies)->{calls.incrementAndGet();return bytes();},4096);
        try(ReaderImages.Scope owner=cache.openScope(()->true)){owner.load(URL,PAGE);owner.load(URL,PAGE+"/other");assertEquals(2,calls.get());}
    }
    @Test public void cacheInvalidationDisconnectsAnActiveFetchBeforeAFreshAttempt()throws Exception{
        CountDownLatch started=new CountDownLatch(1),disconnected=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        HttpURLConnection connection=new HttpURLConnection(new URL(URL)){public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){disconnected.countDown();}};
        ReaderImages cache=new ReaderImages(url->null,(ReaderImages.CancellableFetcher)(url,ref,cookies,ticket,limit)->{if(calls.incrementAndGet()==1){ticket.bind(connection);started.countDown();assertTrue(disconnected.await(3,TimeUnit.SECONDS));ticket.unbind(connection);}return bytes();},4096);
        ExecutorService work=Executors.newSingleThreadExecutor();ReaderImages.Scope owner=cache.openScope(()->true);
        try{Future<byte[]> pending=work.submit(()->owner.load(URL,PAGE));assertTrue(started.await(2,TimeUnit.SECONDS));cache.evictAll();assertTrue(disconnected.await(2,TimeUnit.SECONDS));rejected(pending);assertArrayEquals(bytes(),owner.load(URL,PAGE));assertEquals(2,calls.get());}
        finally{owner.close();disconnected.countDown();work.shutdownNow();}
    }
    @Test public void manyDistinctFiguresHaveBoundedConcurrentNetworkAcquisition()throws Exception{
        CountDownLatch tasksEntered=new CountDownLatch(9),eightStarted=new CountDownLatch(8),release=new CountDownLatch(1);AtomicInteger active=new AtomicInteger(),peak=new AtomicInteger(),calls=new AtomicInteger();
        ReaderImages cache=new ReaderImages(url->null,(ReaderImages.CancellableFetcher)(url,ref,cookies,ticket,limit)->{calls.incrementAndGet();int count=active.incrementAndGet();peak.accumulateAndGet(count,Math::max);eightStarted.countDown();try{assertTrue(release.await(3,TimeUnit.SECONDS));return bytes();}finally{active.decrementAndGet();}},1);
        ExecutorService work=Executors.newFixedThreadPool(9);ReaderImages.Scope owner=cache.openScope(()->true);List<Future<byte[]>> pending=new ArrayList<>();
        try{for(int n=0;n<9;n++){String url=URL+"?figure="+n;pending.add(work.submit(()->{tasksEntered.countDown();return owner.load(url,PAGE);}));}assertTrue(tasksEntered.await(2,TimeUnit.SECONDS));assertTrue(eightStarted.await(2,TimeUnit.SECONDS));Thread.sleep(100);assertEquals(8,calls.get());release.countDown();for(Future<byte[]> result:pending)assertArrayEquals(bytes(),result.get(3,TimeUnit.SECONDS));assertEquals(9,calls.get());assertEquals(8,peak.get());}
        finally{owner.close();release.countDown();work.shutdownNow();}
    }
    @Test public void newlyImportedBytesSupersedeAnOlderPendingNetworkRequest()throws Exception{
        CountDownLatch started=new CountDownLatch(1),disconnected=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();
        HttpURLConnection connection=new HttpURLConnection(new URL(URL)){public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){disconnected.countDown();}};
        ReaderImages cache=new ReaderImages(url->null,(ReaderImages.CancellableFetcher)(url,ref,cookies,ticket,limit)->{calls.incrementAndGet();ticket.bind(connection);started.countDown();assertTrue(disconnected.await(3,TimeUnit.SECONDS));ticket.unbind(connection);throw new IOException("Obsolete network failure");},4096);
        ExecutorService work=Executors.newSingleThreadExecutor();ReaderImages.Scope owner=cache.openScope(()->true);
        try{Future<byte[]> obsolete=work.submit(()->owner.load(URL,PAGE));assertTrue(started.await(2,TimeUnit.SECONDS));cache.put(URL,PAGE,bytes());assertArrayEquals(bytes(),owner.load(URL,PAGE));assertTrue(disconnected.await(2,TimeUnit.SECONDS));rejected(obsolete);assertEquals(1,calls.get());}
        finally{owner.close();disconnected.countDown();work.shutdownNow();}
    }
}
