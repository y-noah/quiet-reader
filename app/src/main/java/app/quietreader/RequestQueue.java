package app.quietreader;

import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Only the newest screen request matters; obsolete sockets are disconnected off the UI thread. */
final class RequestQueue implements AutoCloseable {
    private final ThreadPoolExecutor workers=pool(3,"reader-request");
    private final ThreadPoolExecutor cancellations=pool(1,"reader-cancel");
    private Ticket latest;
    private final java.util.Set<Ticket> external=new java.util.HashSet<>();
    private boolean closed;

    // Reading repositories can outlive a request. Release idle thread stacks without
    // scheduling a maintenance task; the next real request recreates a worker.
    private static ThreadPoolExecutor pool(int count,String name){
        ThreadPoolExecutor pool=new ThreadPoolExecutor(count,count,5,TimeUnit.SECONDS,new LinkedBlockingQueue<>(),r->{Thread t=new Thread(r,name);t.setDaemon(true);return t;});
        pool.allowCoreThreadTimeOut(true);return pool;
    }

    final class Ticket {
        private volatile boolean cancelled;
        private volatile Future<?> future;
        private HttpURLConnection connection;
        boolean cancelled(){return cancelled;}
        void check()throws InterruptedIOException {if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Request superseded");}
        synchronized void bind(HttpURLConnection next)throws InterruptedIOException {
            if(cancelled){next.disconnect();throw new InterruptedIOException("Request superseded");}
            connection=next;
        }
        synchronized void unbind(HttpURLConnection previous){if(connection==previous)connection=null;}
        private void cancel(){
            HttpURLConnection old;
            synchronized(this){cancelled=true;old=connection;connection=null;}
            Future<?> task=future;if(task!=null)task.cancel(true);
            if(old!=null)cancellations.execute(old::disconnect);
        }
    }
    synchronized Ticket submit(Consumer<Ticket> work){
        if(closed)throw new RejectedExecutionException("Request queue closed");
        cancelPending();Ticket ticket=new Ticket();latest=ticket;
        ticket.future=workers.submit(()->{if(!ticket.cancelled())work.accept(ticket);});
        return ticket;
    }
    synchronized void cancelPending(){
        if(latest!=null){latest.cancel();latest=null;}
        for(Ticket ticket:external)ticket.cancel();external.clear();
        workers.purge();
    }
    /** Socket cancellation for work already running on a WebView interception thread. */
    synchronized Ticket register(){if(closed)throw new RejectedExecutionException("Request queue closed");Ticket ticket=new Ticket();external.add(ticket);return ticket;}
    synchronized void release(Ticket ticket){external.remove(ticket);}
    synchronized void cancel(Ticket ticket){if(external.remove(ticket))ticket.cancel();}
    @Override public synchronized void close(){if(closed)return;cancelPending();closed=true;workers.shutdownNow();cancellations.shutdown();}
}
