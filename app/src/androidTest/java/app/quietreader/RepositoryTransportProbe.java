package app.quietreader;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Production native transport with intercepted connections; no packets or real credentials. */
final class RepositoryTransportProbe {
    private static final String ROOT="https://93.184.216.34", OTHER="https://1.1.1.1";
    private static final String COOKIE="qr_synthetic_session=transport-probe";
    private final Instrumentation test;
    private final Map<String,Reply> replies=new HashMap<>();
    private final List<HttpURLConnection> opened=new ArrayList<>();
    private final Set<HttpURLConnection> disconnected=Collections.newSetFromMap(new IdentityHashMap<>());
    private final StringBuilder report=new StringBuilder("Intercepted HTTPS; real Repository redirect/header/limit/cancellation path; no network packets\n");
    private int checks,failures;
    private record Reply(int code,String location,long declared,byte[] body){}
    RepositoryTransportProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void reply(String url,int code,String location,long declared,String body){replies.put(url,new Reply(code,location,declared,body.getBytes(StandardCharsets.UTF_8)));}
    private void reset(){replies.clear();opened.clear();disconnected.clear();}
    private byte[] load(String path,int limit)throws Exception{return Repository.bytes(ROOT+path,COOKIE,ROOT+"/article",limit);}
    private boolean rejected(String path,int limit,Class<? extends Throwable> type,String message){
        try{load(path,limit);return false;}catch(Throwable e){return type.isInstance(e)&&(message==null||String.valueOf(e.getMessage()).contains(message));}
    }
    private void closed(String label){check(disconnected.size()==opened.size(),label+": all opened connections disconnected");}
    void run(){
        try{
            URL.setURLStreamHandlerFactory(protocol->!"https".equals(protocol)?null:new URLStreamHandler(){
                protected URLConnection openConnection(URL url)throws IOException{
                    Reply response=replies.get(url.toString());if(response==null)throw new IOException("Unexpected synthetic destination");
                    HttpURLConnection connection=new HttpURLConnection(url){
                        public void connect(){}public boolean usingProxy(){return false;}
                        public void disconnect(){disconnected.add(this);}
                        public int getResponseCode(){return response.code();}
                        public String getHeaderField(String key){return "Location".equalsIgnoreCase(key)?response.location():"Retry-After".equalsIgnoreCase(key)?"3":null;}
                        public long getContentLengthLong(){return response.declared();}
                        public InputStream getInputStream(){return new ByteArrayInputStream(response.body());}
                    };opened.add(connection);return connection;
                }
            });
            reply(ROOT+"/start",302,"/next",0,"");reply(ROOT+"/next",302,OTHER+"/foreign",0,"");
            reply(OTHER+"/foreign",302,ROOT+"/return",0,"");reply(ROOT+"/return",200,null,2,"ok");
            check(Arrays.equals(load("/start",64),"ok".getBytes(StandardCharsets.UTF_8)),"relative and cross-host redirects return expected bytes");
            check(opened.size()==4,"four controlled destinations visited");
            check(COOKIE.equals(opened.get(0).getRequestProperty("Cookie"))&&COOKIE.equals(opened.get(1).getRequestProperty("Cookie")),"original exact-host redirects retain supplied session");
            check(opened.get(2).getRequestProperty("Cookie")==null,"foreign host never receives original session");
            check(COOKIE.equals(opened.get(3).getRequestProperty("Cookie")),"return to original exact host restores original session");
            check(opened.stream().noneMatch(HttpURLConnection::getInstanceFollowRedirects),"automatic redirect forwarding disabled at every hop");closed("successful chain");

            reset();reply(ROOT+"/local",302,"https://127.0.0.1/private",0,"");
            check(rejected("/local",64,SecurityException.class,"本地网络"),"redirect to loopback rejected before opening destination");check(opened.size()==1,"only initial public connection opened for loopback redirect");closed("loopback rejection");
            reset();reply(ROOT+"/scheme",302,"file:///private",0,"");
            check(rejected("/scheme",64,SecurityException.class,"HTTPS"),"non-HTTPS redirect rejected before destination connection");closed("scheme rejection");
            reset();reply(ROOT+"/loop",302,"/loop",0,"");
            check(rejected("/loop",64,IOException.class,"Too many redirects"),"redirect cycle terminates with an error");check(opened.size()==5,"redirect cycle bounded to five connections");closed("redirect cycle");
            reset();reply(ROOT+"/declared",200,null,65,"ok");
            check(rejected("/declared",64,IOException.class,"Response too large"),"declared oversized body rejected");closed("declared limit");
            reset();reply(ROOT+"/stream",200,null,-1,"123456789");
            check(rejected("/stream",8,IOException.class,"Response too large"),"unknown-length streamed body cannot exceed byte limit");closed("streamed limit");
            reset();reply(ROOT+"/busy",503,null,0,"");
            check(rejected("/busy",64,Repository.HttpFailure.class,null),"non-success status returns typed HTTP failure");closed("HTTP failure");
            reset();try(RequestQueue queue=new RequestQueue()){
                RequestQueue.Ticket ticket=queue.register();queue.cancel(ticket);boolean cancelled=false;
                try{Repository.fetch(ROOT+"/cancelled",COOKIE,ROOT+"/article",ticket,Repository.DESKTOP_UA);}catch(InterruptedIOException expected){cancelled=true;}
                check(cancelled&&opened.isEmpty(),"already cancelled request opens no connection");
            }
        }catch(Throwable e){failures++;report.append("ERROR ").append(e.getClass().getSimpleName()).append(':').append(e.getMessage()).append('\n');}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');
        Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
