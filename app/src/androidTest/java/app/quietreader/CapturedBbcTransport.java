package app.quietreader;

import android.app.Instrumentation;
import android.os.Build;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Test process only: exact public capture at the transport boundary, never a cached Document. */
final class CapturedBbcTransport {
    private CapturedBbcTransport(){}
    static AtomicInteger install(Instrumentation test,String target)throws Exception{
        if(!"app.quietreader.discoveryprobe".equals(test.getTargetContext().getPackageName())||!"ranchu".equals(Build.HARDWARE))throw new IllegalStateException("Isolated emulator only");
        final byte[] response;try(InputStream in=test.getContext().getAssets().open("bbc-social-response-20261027.html")){response=in.readAllBytes();}
        AtomicInteger fetched=new AtomicInteger();
        URL.setURLStreamHandlerFactory(protocol->!"https".equals(protocol)?null:new URLStreamHandler(){
            @Override protected URLConnection openConnection(URL url)throws IOException{
                if(!target.equals(url.toExternalForm()))throw new IOException("Capture transport excludes other URLs");
                return new HttpURLConnection(url){
                    @Override public void connect(){connected=true;}
                    @Override public boolean usingProxy(){return false;}
                    @Override public void disconnect(){connected=false;}
                    @Override public int getResponseCode(){connect();return 200;}
                    @Override public long getContentLengthLong(){return response.length;}
                    @Override public String getContentType(){return "text/html; charset=utf-8";}
                    @Override public InputStream getInputStream(){fetched.incrementAndGet();return new ByteArrayInputStream(response);}
                };
            }
        });return fetched;
    }
}
