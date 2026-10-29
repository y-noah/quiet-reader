package app.quietreader;

import android.app.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;

/** Real Android TLS sockets, loopback only. Does not relax production URL or trust policy. */
final class SocketCancellationProbe {
    private final Instrumentation test;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    SocketCancellationProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private SSLContext context()throws Exception{
        return new LoopbackTestTls(test).context;
    }
    private void scenario(SSLContext tls,boolean body,boolean external)throws Exception{
        String name=(external?"external ":"queued ")+(body?"body":"headers");
        CountDownLatch waiting=new CountDownLatch(1),bodyEntered=new CountDownLatch(1),disconnected=new CountDownLatch(1),finished=new CountDownLatch(1);
        AtomicReference<Throwable> serverError=new AtomicReference<>(),clientError=new AtomicReference<>();AtomicBoolean published=new AtomicBoolean();
        ExecutorService server=Executors.newSingleThreadExecutor(),interceptor=Executors.newSingleThreadExecutor();
        try(RequestQueue queue=new RequestQueue();SSLServerSocket listener=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            listener.setSoTimeout(8000);
            server.submit(()->{
                try(SSLSocket socket=(SSLSocket)listener.accept()){
                    socket.setSoTimeout(8000);socket.startHandshake();InputStream in=socket.getInputStream();int tail=0,b;
                    while((b=in.read())!=-1){tail=(tail<<8)|b;if(tail==0x0d0a0d0a)break;}
                    if(b<0)throw new EOFException("Client closed before sending headers");
                    if(body){socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 100000\r\nConnection: close\r\n\r\nx".getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();}
                    waiting.countDown();
                    // Nothing else is sent. Only peer EOF/socket closure proves cancellation.
                    try{if(in.read()==-1)disconnected.countDown();else throw new IOException("Unexpected next request bytes");}
                    catch(SocketException expected){disconnected.countDown();}
                }catch(Throwable error){serverError.set(error);waiting.countDown();}
            });
            HttpsURLConnection connection=(HttpsURLConnection)new URL("https://127.0.0.1:"+listener.getLocalPort()+"/blocked").openConnection();
            connection.setSSLSocketFactory(tls.getSocketFactory());connection.setConnectTimeout(5000);connection.setReadTimeout(12000);connection.setUseCaches(false);
            java.util.function.Consumer<RequestQueue.Ticket> read=ticket->{try{
                ticket.bind(connection);ticket.check();int status=connection.getResponseCode();ticket.check();
                if(status!=200)throw new IOException("Unexpected status "+status);
                try(InputStream in=connection.getInputStream()){
                    if(body){if(in.read()!='x')throw new IOException("Missing initial body byte");bodyEntered.countDown();}
                    while(in.read()!=-1)ticket.check();
                }
                ticket.check();published.set(true);
            }catch(Throwable error){clientError.set(error);}finally{ticket.unbind(connection);connection.disconnect();finished.countDown();}};
            if(external){RequestQueue.Ticket ticket=queue.register();interceptor.submit(()->{try{read.accept(ticket);}finally{queue.release(ticket);}});}else queue.submit(read);
            if(!waiting.await(8,TimeUnit.SECONDS)||serverError.get()!=null)throw new IllegalStateException("Loopback setup "+name,serverError.get());
            if(body&&!bodyEntered.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Client did not reach the stalled body "+name,clientError.get());
            // Let the reader enter its blocking read, not just cancellation-before-bind.
            Thread.sleep(150);check(finished.getCount()==1,name+" is actually blocked before cancellation");
            long began=SystemClock.elapsedRealtime();test.runOnMainSync(queue::cancelPending);long uiMs=SystemClock.elapsedRealtime()-began;
            boolean ended=finished.await(2500,TimeUnit.MILLISECONDS),closed=disconnected.await(500,TimeUnit.MILLISECONDS);
            report.append("TIMING ").append(name).append(" uiMs=").append(uiMs).append(" observedCloseMs=").append(SystemClock.elapsedRealtime()-began).append(" finished=").append(ended).append(" peerClosed=").append(closed).append(" error=").append(clientError.get()==null?"none":clientError.get().getClass().getSimpleName()).append('\n');
            check(uiMs<500,name+" cancellation returns promptly on main thread");
            check(ended,name+" real blocked Android network read exits before read timeout");
            check(closed,name+" TLS peer observes connection close");
            check(!published.get(),name+" canceled response cannot publish");
            CountDownLatch next=new CountDownLatch(1);queue.submit(ticket->next.countDown());check(next.await(1,TimeUnit.SECONDS),name+" leaves capacity for the next action");
        }finally{server.shutdownNow();interceptor.shutdownNow();server.awaitTermination(9,TimeUnit.SECONDS);interceptor.awaitTermination(13,TimeUnit.SECONDS);}
    }
    /** Many WebView image interceptors may be blocked at once when HOME closes a reader. */
    private void concurrent(SSLContext tls,boolean closeQueue)throws Exception{
        final int count=8;String name=closeQueue?"concurrent close":"concurrent pause";
        CountDownLatch entered=new CountDownLatch(count),finished=new CountDownLatch(count),peersClosed=new CountDownLatch(count);
        AtomicInteger published=new AtomicInteger();ConcurrentLinkedQueue<Throwable> errors=new ConcurrentLinkedQueue<>();
        ExecutorService acceptor=Executors.newSingleThreadExecutor(),servers=Executors.newFixedThreadPool(count),clients=Executors.newFixedThreadPool(count);
        RequestQueue queue=new RequestQueue();
        try(SSLServerSocket listener=(SSLServerSocket)tls.getServerSocketFactory().createServerSocket(0,count,InetAddress.getByName("127.0.0.1"))){
            listener.setSoTimeout(8000);
            acceptor.submit(()->{try{for(int n=0;n<count;n++){SSLSocket socket=(SSLSocket)listener.accept();servers.submit(()->{try(SSLSocket peer=socket){
                peer.setSoTimeout(8000);peer.startHandshake();InputStream input=peer.getInputStream();int tail=0,value;
                while((value=input.read())!=-1){tail=(tail<<8)|value;if(tail==0x0d0a0d0a)break;}
                if(value<0)throw new EOFException("No request headers");
                peer.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 100000\r\nConnection: close\r\n\r\nx".getBytes(StandardCharsets.US_ASCII));peer.getOutputStream().flush();
                try{if(input.read()==-1)peersClosed.countDown();else errors.add(new IOException("Unexpected client bytes"));}catch(SocketException disconnected){peersClosed.countDown();}
            }catch(Throwable error){errors.add(error);}});}}catch(Throwable error){errors.add(error);}});
            for(int n=0;n<count;n++){
                RequestQueue.Ticket ticket=queue.register();
                clients.submit(()->{HttpsURLConnection connection=null;try{
                    connection=(HttpsURLConnection)new URL("https://127.0.0.1:"+listener.getLocalPort()+"/blocked").openConnection();
                    connection.setSSLSocketFactory(tls.getSocketFactory());connection.setConnectTimeout(5000);connection.setReadTimeout(12000);connection.setUseCaches(false);
                    ticket.bind(connection);ticket.check();if(connection.getResponseCode()!=200)throw new IOException("Unexpected status");
                    try(InputStream input=connection.getInputStream()){if(input.read()!='x')throw new IOException("Missing first byte");entered.countDown();while(input.read()!=-1)ticket.check();}
                    ticket.check();published.incrementAndGet();
                }catch(Throwable error){if(!ticket.cancelled())errors.add(error);}
                finally{if(connection!=null){ticket.unbind(connection);connection.disconnect();}queue.release(ticket);finished.countDown();}});
            }
            if(!entered.await(8,TimeUnit.SECONDS)||!errors.isEmpty())throw new IllegalStateException(name+" setup: "+errors);
            Thread.sleep(150);check(finished.getCount()==count,name+" has eight real stalled Android TLS body reads");
            long started=SystemClock.elapsedRealtime();test.runOnMainSync(closeQueue?queue::close:queue::cancelPending);long uiMs=SystemClock.elapsedRealtime()-started;
            boolean ended=finished.await(3000,TimeUnit.MILLISECONDS),closed=peersClosed.await(500,TimeUnit.MILLISECONDS);
            report.append("TIMING ").append(name).append(" uiMs=").append(uiMs).append(" observedAllCloseMs=").append(SystemClock.elapsedRealtime()-started).append(" finished=").append(count-finished.getCount()).append(" peerClosed=").append(count-peersClosed.getCount()).append('\n');
            check(uiMs<500,name+" never waits for eight disconnects on the main thread");
            check(ended,name+" all eight readers exit before read timeout");check(closed,name+" all eight TLS peers observe closure");
            check(published.get()==0&&errors.isEmpty(),name+" canceled bodies never publish and setup has no hidden errors");
            if(!closeQueue){CountDownLatch next=new CountDownLatch(1);queue.submit(ticket->next.countDown());check(next.await(1,TimeUnit.SECONDS),name+" permits the next real action");}
        }finally{queue.close();acceptor.shutdownNow();servers.shutdownNow();clients.shutdownNow();acceptor.awaitTermination(9,TimeUnit.SECONDS);servers.awaitTermination(9,TimeUnit.SECONDS);clients.awaitTermination(13,TimeUnit.SECONDS);}
    }
    void run(){
        try{SSLContext tls=context();scenario(tls,false,false);scenario(tls,true,false);scenario(tls,true,true);concurrent(tls,false);concurrent(tls,true);}
        catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
