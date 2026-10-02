package app.quietreader;

import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Only the newest screen request matters; obsolete sockets are disconnected off the UI thread. */
final class RequestQueue implements AutoCloseable {
    private final ThreadPoolExecutor workers=(ThreadPoolExecutor)Executors.newFixedThreadPool(3,r->{Thread t=new Thread(r,"reader-request");t.setDaemon(true);return t;});
    private final ExecutorService cancellations=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"reader-cancel");t.setDaemon(true);return t;});
    private Ticket latest;
    private boolean closed;

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
        workers.purge();
    }
    @Override public synchronized void close(){if(closed)return;cancelPending();closed=true;workers.shutdownNow();cancellations.shutdown();}
}
