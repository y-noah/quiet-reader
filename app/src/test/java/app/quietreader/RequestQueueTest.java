package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class RequestQueueTest {
    private static final class SlowConnection extends HttpURLConnection {
        final CountDownLatch disconnected=new CountDownLatch(1);
        volatile String cancellationThread="";
        SlowConnection()throws Exception{super(new URL("https://example.invalid/controlled-test-only"));}
        @Override public void connect(){}
        @Override public boolean usingProxy(){return false;}
        @Override public void disconnect(){cancellationThread=Thread.currentThread().getName();disconnected.countDown();}
        void waitForDisconnect(){boolean complete=false;while(!complete)try{complete=disconnected.await(2,TimeUnit.SECONDS);if(!complete)throw new AssertionError("Connection never cancelled");}catch(InterruptedException ignored){/* Simulate socket I/O that does not stop merely because the worker was interrupted. */}}
    }
    @Test public void baselineFixedPoolQueuesNewScreenBehindOldWork()throws Exception{
        ExecutorService old=Executors.newFixedThreadPool(3);CountDownLatch occupied=new CountDownLatch(3),release=new CountDownLatch(1),newScreen=new CountDownLatch(1);
        try{for(int n=0;n<3;n++)old.execute(()->{occupied.countDown();try{release.await();}catch(InterruptedException ignored){}});assertTrue(occupied.await(1,TimeUnit.SECONDS));old.execute(newScreen::countDown);assertFalse("Baseline should expose waiting behind stale tasks",newScreen.await(150,TimeUnit.MILLISECONDS));}
        finally{release.countDown();old.shutdownNow();}
    }
    @Test public void newScreenDisconnectsOldSocketAndStartsPromptly()throws Exception{
        try(RequestQueue queue=new RequestQueue()){
            SlowConnection slow=new SlowConnection();CountDownLatch started=new CountDownLatch(1),finished=new CountDownLatch(1),fresh=new CountDownLatch(1);AtomicBoolean stalePublished=new AtomicBoolean();
            queue.submit(ticket->{try{ticket.bind(slow);started.countDown();slow.waitForDisconnect();if(!ticket.cancelled())stalePublished.set(true);}catch(Exception e){throw new AssertionError(e);}finally{ticket.unbind(slow);finished.countDown();}});
            assertTrue(started.await(1,TimeUnit.SECONDS));queue.submit(ticket->fresh.countDown());
            assertTrue("New screen should not wait for stale socket timeout",fresh.await(1,TimeUnit.SECONDS));assertTrue(finished.await(1,TimeUnit.SECONDS));assertFalse(stalePublished.get());assertEquals("reader-cancel",slow.cancellationThread);
        }
    }
    @Test public void cancellationBeforeConnectionBindingCannotLeaveSocketAlive()throws Exception{
        try(RequestQueue queue=new RequestQueue()){
            CountDownLatch entered=new CountDownLatch(1),resume=new CountDownLatch(1),finished=new CountDownLatch(1);AtomicBoolean rejected=new AtomicBoolean();SlowConnection late=new SlowConnection();
            queue.submit(ticket->{entered.countDown();while(resume.getCount()>0)try{resume.await();}catch(InterruptedException ignored){}try{ticket.bind(late);}catch(java.io.InterruptedIOException expected){rejected.set(true);}finally{finished.countDown();}});
            assertTrue(entered.await(1,TimeUnit.SECONDS));queue.cancelPending();resume.countDown();assertTrue(finished.await(1,TimeUnit.SECONDS));assertTrue(rejected.get());assertEquals(0,late.disconnected.getCount());
        }
    }
    @Test public void closeRejectsFurtherRequests(){RequestQueue queue=new RequestQueue();queue.close();try{queue.submit(ticket->{});fail("Closed queue accepted request");}catch(RejectedExecutionException expected){}}
}
