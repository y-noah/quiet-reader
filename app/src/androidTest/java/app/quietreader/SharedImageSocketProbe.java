package app.quietreader;

import android.app.*;
import android.graphics.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;

/** Actual Android TLS through production image scopes and caches; test-only loopback fetcher. */
final class SharedImageSocketProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("Shared image scope + real loopback TLS; no public-source, Activity or energy claim\n");
    SharedImageSocketProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String label){checks++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');if(!good)throw new IllegalStateException(label);}
    private int subscribers(ReaderImages cache)throws Exception{synchronized(cache){java.lang.reflect.Field flights=ReaderImages.class.getDeclaredField("flights");flights.setAccessible(true);int count=0;for(Object flight:((Map<?,?>)flights.get(cache)).values()){java.lang.reflect.Field owners=flight.getClass().getDeclaredField("owners");owners.setAccessible(true);for(Object number:((Map<?,?>)owners.get(flight)).values())count+=(Integer)number;}return count;}}
    private void awaitSubscribers(ReaderImages cache,int expected)throws Exception{long until=SystemClock.elapsedRealtime()+4000;while(subscribers(cache)!=expected){if(SystemClock.elapsedRealtime()>until)throw new IllegalStateException("subscriber deadline "+expected);Thread.sleep(10);}}
    private boolean rejected(Future<byte[]> future)throws Exception{try{future.get(4,TimeUnit.SECONDS);return false;}catch(ExecutionException expected){return expected.getCause() instanceof IOException;}}
    void run(){File directory=new File(test.getTargetContext().getCacheDir(),"test-shared-image-"+UUID.randomUUID());
        ExecutorService accept=Executors.newSingleThreadExecutor(),servers=Executors.newFixedThreadPool(8),clients=Executors.newFixedThreadPool(9);
        ReaderImages.Scope first=null,second=null;SSLServerSocket listener=null;AtomicBoolean ending=new AtomicBoolean();
        try{
            LoopbackTestTls tls=new LoopbackTestTls(test);Bitmap bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff396e51);ByteArrayOutputStream encoded=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,encoded);bitmap.recycle();byte[] png=encoded.toByteArray();
            CountDownLatch entered=new CountDownLatch(8),othersClosed=new CountDownLatch(7),allClosed=new CountDownLatch(8);
            AtomicBoolean sharedClosed=new AtomicBoolean(),recovery=new AtomicBoolean();AtomicInteger calls=new AtomicInteger();ConcurrentLinkedQueue<Throwable> errors=new ConcurrentLinkedQueue<>();
            listener=(SSLServerSocket)tls.context.getServerSocketFactory().createServerSocket(0,16,InetAddress.getByName("127.0.0.1"));listener.setSoTimeout(20000);SSLServerSocket source=listener;
            accept.submit(()->{try{for(int n=0;n<9;n++){SSLSocket peer=(SSLSocket)source.accept();servers.submit(()->{try(SSLSocket socket=peer){
                socket.setSoTimeout(15000);socket.startHandshake();InputStream input=socket.getInputStream();ByteArrayOutputStream request=new ByteArrayOutputStream();int tail=0,b;
                while((b=input.read())!=-1){request.write(b);tail=(tail<<8)|b;if(tail==0x0d0a0d0a)break;if(request.size()>4096)throw new IOException("headers too large");}
                if(b<0)throw new EOFException("missing request");String headers=request.toString("US-ASCII");boolean shared=headers.startsWith("GET /image/0.png ");
                if(recovery.get()){socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Length: "+png.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(png);socket.getOutputStream().flush();return;}
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 100000\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(png,0,8);socket.getOutputStream().flush();
                try{if(input.read()!=-1)throw new IOException("unexpected request bytes");}catch(SocketException closed){}
                if(shared)sharedClosed.set(true);else othersClosed.countDown();allClosed.countDown();
            }catch(Throwable error){if(!ending.get())errors.add(error);}});}}catch(Throwable error){if(!ending.get())errors.add(error);}});
            String base="https://127.0.0.1:"+listener.getLocalPort(),page="https://example.invalid/article",scope="test-namespace";
            ImageDiskCache disk=new ImageDiskCache(directory);
            ReaderImages.CancellableFetcher fetch=(url,ref,cookies,ticket,limit)->{calls.incrementAndGet();cookies.apply(url);HttpsURLConnection connection=(HttpsURLConnection)new URL(url).openConnection();connection.setSSLSocketFactory(tls.context.getSocketFactory());connection.setConnectTimeout(5000);connection.setReadTimeout(15000);connection.setUseCaches(false);ticket.bind(connection);
                try{if(connection.getResponseCode()!=200)throw new IOException("unexpected status");try(InputStream input=connection.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[1024];int total=0,n;while(total<8){n=input.read(buffer,0,8-total);if(n<0)throw new EOFException("missing PNG signature");bytes.write(buffer,0,n);total+=n;}
                    if(!recovery.get())entered.countDown();while((n=input.read(buffer))!=-1){ticket.check();bytes.write(buffer,0,n);if(bytes.size()>limit)throw new IOException("over image limit");}ticket.check();return bytes.toByteArray();
                }}finally{ticket.unbind(connection);connection.disconnect();}};
            ReaderImages cache=new ReaderImages(url->"",fetch,4096);cache.attach(disk,()->scope);first=cache.openScope(()->true);second=cache.openScope(()->true);ReaderImages.Scope owner=first,joined=second;
            List<Future<byte[]>> pending=new ArrayList<>();for(int n=0;n<8;n++){String image=base+"/image/"+n+".png";pending.add(clients.submit(()->owner.load(image,page)));}
            check(entered.await(10,TimeUnit.SECONDS),"eight different images reach actual stalled Android TLS body reads");
            Future<byte[]> duplicate=clients.submit(()->joined.load(base+"/image/0.png",page));awaitSubscribers(cache,9);
            check(calls.get()==8,"second image owner joins one existing TLS acquisition");
            long began=SystemClock.elapsedRealtime();test.runOnMainSync(first::close);long firstMs=SystemClock.elapsedRealtime()-began;
            check(othersClosed.await(3,TimeUnit.SECONDS)&&!sharedClosed.get(),"closing first owner disconnects seven peers while shared image remains connected");
            check(firstMs<500,"first owner cancellation does not block main thread");
            for(int n=1;n<8;n++)check(rejected(pending.get(n)),"exclusive canceled image "+n+" does not return bytes");
            check(!duplicate.isDone()&&calls.get()==8,"remaining owner keeps its shared download without restart");
            began=SystemClock.elapsedRealtime();test.runOnMainSync(second::close);long lastMs=SystemClock.elapsedRealtime()-began;
            check(allClosed.await(3,TimeUnit.SECONDS)&&sharedClosed.get(),"last owner disconnects final actual TLS peer");
            check(rejected(pending.get(0))&&rejected(duplicate),"both stopped subscribers reject the canceled shared image");
            check(lastMs<500&&subscribers(cache)==0,"last owner promptly clears every image subscription");
            check(!directory.exists()||Objects.requireNonNull(directory.list()).length==0,"canceled acquisitions leave no disk image or partial record");
            recovery.set(true);try(ReaderImages.Scope fresh=cache.openScope(()->true)){byte[] bytes=fresh.load(base+"/image/0.png",page);Bitmap decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.length);check(Arrays.equals(png,bytes)&&decoded!=null&&decoded.getWidth()==16,"next foreground owner fetches and decodes a new complete PNG");if(decoded!=null)decoded.recycle();}
            check(calls.get()==9,"canceled shared bytes did not satisfy the later image from RAM");
            ReaderImages restored=new ReaderImages(url->"",(url,ref,cookies)->{throw new IOException("disk recovery must not fetch");},4096);restored.attach(new ImageDiskCache(directory),()->scope);
            try(ReaderImages.Scope fresh=restored.openScope(()->true)){check(Arrays.equals(png,fresh.load(base+"/image/0.png",page)),"successful later acquisition remains reusable from disk");}
            check(errors.isEmpty(),"loopback server observed expected request and cancellation paths");report.append("TIMING firstOwnerMs=").append(firstMs).append(" lastOwnerMs=").append(lastMs).append('\n');
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{ending.set(true);if(first!=null)first.close();if(second!=null)second.close();if(listener!=null)try{listener.close();}catch(IOException ignored){}accept.shutdownNow();servers.shutdownNow();clients.shutdownNow();try{accept.awaitTermination(2,TimeUnit.SECONDS);servers.awaitTermination(3,TimeUnit.SECONDS);clients.awaitTermination(3,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}File[] files=directory.listFiles();boolean clean=true;if(files!=null)for(File file:files)clean&=file.isFile()&&file.delete();clean&=!directory.exists()||directory.delete();checks++;if(!clean)failures++;report.append(clean?"PASS ":"FAIL ").append("isolated image test directory removed\n");}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
