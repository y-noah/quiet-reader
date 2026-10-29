package app.quietreader;

import android.webkit.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.*;
import static app.quietreader.Models.*;

/** Click-owned streaming. No video bytes enter article memory/disk caches. */
class ReaderVideoRequests implements AutoCloseable {
    private final RequestQueue sockets=new RequestQueue();
    private final String page,media,route;
    private volatile boolean closed;
    ReaderVideoRequests(String page,VideoAttachment video){
        if(!ReaderMedia.allowed(page,video.url))throw new IllegalArgumentException("Unsupported media");
        this.page=page;media=video.url;
        route=ReaderHtml.ACTION+"media/"+ReaderMedia.key(video)+"?play="+java.util.UUID.randomUUID();
    }
    String route(){return route;}
    boolean owns(String url){return !closed&&route.equals(url);}
    static boolean validRange(String range){return range==null||range.matches("bytes=(?:[0-9]{1,18}-[0-9]{0,18}|-[0-9]{1,18})");}
    WebResourceResponse response(String url,Map<String,String> requestHeaders)throws Exception{
        if(!owns(url))throw new IOException("Inactive playback");
        String range=null;
        for(Map.Entry<String,String> header:requestHeaders.entrySet())if(header.getKey().equalsIgnoreCase("Range"))range=header.getValue();
        if(!validRange(range))throw new IOException("Unsupported range");
        RequestQueue.Ticket ticket=sockets.register();HttpURLConnection connection=null;
        try{
            String target=media;
            for(int redirect=0;redirect<=4;redirect++){
                ticket.check();if(!owns(url)||!ReaderMedia.allowed(page,target))throw new IOException("Inactive or untrusted media");
                UrlPolicy.requirePublicHost(target);
                connection=(HttpURLConnection)new URL(target).openConnection();connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(10000);connection.setReadTimeout(10000);ticket.bind(connection);
                connection.setRequestProperty("Referer",page);connection.setRequestProperty("Accept-Encoding","identity");
                if(range!=null)connection.setRequestProperty("Range",range);
                String cookie=CookieManager.getInstance().getCookie(target);if(cookie!=null&&!cookie.isEmpty())connection.setRequestProperty("Cookie",cookie);
                int code=connection.getResponseCode();ticket.check();
                for(Map.Entry<String,List<String>> header:connection.getHeaderFields().entrySet())if("Set-Cookie".equalsIgnoreCase(header.getKey()))for(String value:header.getValue())CookieManager.getInstance().setCookie(target,value);
                if(code==301||code==302||code==303||code==307||code==308){
                    String next=UrlPolicy.normalize(target,connection.getHeaderField("Location"));
                    ticket.unbind(connection);connection.disconnect();connection=null;target=next;continue;
                }
                if(code!=200&&code!=206)throw new IOException("Media unavailable");
                String type=connection.getContentType();if(type!=null)type=type.split(";",2)[0].trim().toLowerCase(Locale.ROOT);
                if(type==null||type.equals("application/octet-stream"))type=URI.create(target).getPath().toLowerCase(Locale.ROOT).endsWith(".webm")?"video/webm":"video/mp4";
                if(!type.equals("video/mp4")&&!type.equals("video/webm"))throw new IOException("Not progressive video");
                Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");headers.put("Accept-Ranges","bytes");
                String length=connection.getHeaderField("Content-Length");if(length!=null&&length.matches("[0-9]{1,18}"))headers.put("Content-Length",length);
                String contentRange=connection.getHeaderField("Content-Range");
                if(code==206){if(contentRange==null||!contentRange.matches("bytes [0-9]{1,18}-[0-9]{1,18}/(?:[0-9]{1,18}|\\*)"))throw new IOException("Invalid media range");headers.put("Content-Range",contentRange);}
                final HttpURLConnection active=connection;
                InputStream stream=new FilterInputStream(connection.getInputStream()){
                    private boolean released;
                    private void check()throws IOException{ticket.check();if(!owns(url))throw new InterruptedIOException("Playback stopped");}
                    @Override public int read()throws IOException{check();try{int result=super.read();if(result<0)close();return result;}catch(IOException error){close();throw error;}}
                    @Override public int read(byte[] buffer,int offset,int count)throws IOException{check();try{int result=in.read(buffer,offset,count);if(result<0)close();return result;}catch(IOException error){close();throw error;}}
                    @Override public void close()throws IOException{if(released)return;released=true;try{super.close();}finally{ticket.unbind(active);active.disconnect();sockets.release(ticket);}}
                };
                connection=null;
                return new WebResourceResponse(type,null,code,code==206?"Partial Content":"OK",headers,stream);
            }
            throw new IOException("Too many media redirects");
        }catch(Exception error){if(connection!=null){ticket.unbind(connection);connection.disconnect();}sockets.release(ticket);throw error;}
    }
    @Override public void close(){closed=true;sockets.close();}
}
