package app.quietreader;

import android.app.*;
import android.os.Bundle;
import android.webkit.CookieManager;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Real CookieManager and Repository headers; controlled HTTP connections, no remote login. */
final class ReaderImageSessionProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC HTTPS transport; real CookieManager URL matching and Repository redirect/header path\n");
    private final List<Map<String,String>> requests=new ArrayList<>();
    private final String prefix="/qr-image-"+System.nanoTime();
    // Public numeric addresses avoid DNS and pass the normal public-address guard.
    // URL handlers below intercept every connection: no packets are sent to either host.
    private final String first="https://93.184.216.34"+prefix+"/one/image",second="https://93.184.216.34"+prefix+"/two/image",last="https://1.1.1.1"+prefix+"/cdn/image",page="https://1.1.1.1"+prefix+"/article";
    private boolean rotate;
    private byte[] imageBytes;
    ReaderImageSessionProbe(Instrumentation test){this.test=test;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private void cookie(String url,String name,String value,String path)throws Exception{
        CountDownLatch done=new CountDownLatch(1);boolean[] accepted={false};test.runOnMainSync(()->CookieManager.getInstance().setCookie(url,name+"="+value+"; Path="+path+"; Secure"+(value.isEmpty()?"; Max-Age=0":""),ok->{accepted[0]=ok;done.countDown();}));
        if(!done.await(3,TimeUnit.SECONDS)||!accepted[0])throw new IOException("Synthetic scoped cookie setup failed");
    }
    private void source(String value)throws Exception{cookie(page,"qr_source",value,prefix+"/article");}
    private boolean hasOnly(Map<String,String> request,String wanted){String cookie=request.get("cookie");return cookie!=null&&cookie.contains(wanted+"=")&&Arrays.stream(new String[]{"qr_one","qr_two","qr_cdn","qr_source"}).filter(name->!name.equals(wanted)).noneMatch(name->cookie.contains(name+"="));}
    void run(){
        ReaderImages cache=new ReaderImages();
        boolean acceptedCookies=CookieManager.getInstance().acceptCookie();
        try{
            android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(20,10,android.graphics.Bitmap.Config.ARGB_8888);image.eraseColor(0xff82aacc);ByteArrayOutputStream bytes=new ByteArrayOutputStream();image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);image.recycle();imageBytes=bytes.toByteArray();
            test.runOnMainSync(()->CookieManager.getInstance().setAcceptCookie(true));cookie(first,"qr_one","one",prefix+"/one");cookie(second,"qr_two","two",prefix+"/two");cookie(last,"qr_cdn","cdn",prefix+"/cdn");source("account-a");
            URL.setURLStreamHandlerFactory(protocol->!protocol.equals("https")?null:new URLStreamHandler(){protected URLConnection openConnection(URL url)throws IOException{
                String target=url.toString();if(!List.of(first,second,last).contains(target))throw new IOException("Unexpected synthetic image target");
                return new HttpURLConnection(url){
                    public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
                    @Override public int getResponseCode(){Map<String,String> request=new HashMap<>();request.put("url",target);request.put("cookie",getRequestProperty("Cookie"));request.put("referer",getRequestProperty("Referer"));request.put("cache",Boolean.toString(getUseCaches()));requests.add(request);return target.equals(last)?200:302;}
                    @Override public String getHeaderField(String key){return key.equalsIgnoreCase("Location")?(target.equals(first)?second:last):null;}
                    @Override public long getContentLengthLong(){return imageBytes.length;}
                    @Override public InputStream getInputStream()throws IOException{if(rotate){rotate=false;try{source("account-c");}catch(Exception e){throw new IOException("Synthetic rotation failed");}}return new ByteArrayInputStream(imageBytes);}
                };
            }});
            check(ImageType.detect(cache.load(first,page))!=null,"production image download accepts controlled image bytes");check(requests.size()==3,"production redirect loop visits all three scoped URLs");
            check(hasOnly(requests.get(0),"qr_one"),"first request carries only its path-matched image cookie");check(hasOnly(requests.get(1),"qr_two"),"same-host new-path redirect reselects cookies instead of forwarding old path cookie");check(hasOnly(requests.get(2),"qr_cdn"),"cross-host redirect uses destination cookie and never source article cookie");
            check(requests.stream().allMatch(r->page.equals(r.get("referer"))&&"false".equals(r.get("cache"))),"referer preserved and URLConnection cache bypassed at every hop");
            cache.load(first,page);check(requests.size()==3,"unchanged sessions reuse memory image without HTTP");
            source("account-b");cache.load(first,page);check(requests.size()==6,"article account switch invalidates otherwise unchanged CDN image");
            cookie(second,"qr_two","new",prefix+"/two");cache.load(first,page);check(requests.size()==9,"intermediate redirect cookie change invalidates cached bytes");
            cookie(last,"qr_cdn","new",prefix+"/cdn");cache.load(first,page);check(requests.size()==12,"final CDN cookie change invalidates cached bytes");
            source("");cache.load(first,page);check(requests.size()==15,"logout does not reuse account-bound image");
            cache.remove(first);rotate=true;boolean rejected=false;try{cache.load(first,page);}catch(IOException changed){rejected=changed.getMessage().contains("状态已变化");}check(rejected,"account change while image arrives rejects late bytes");
            int before=requests.size();cache.load(first,page);check(requests.size()==before+3,"rejected late bytes were not stored under new account");
        }catch(Throwable e){failures++;report.append("ERROR ").append(e.getClass().getSimpleName()).append(':').append(e.getMessage()).append('\n');}
        finally{cache.evictAll();try{cookie(first,"qr_one","",prefix+"/one");cookie(second,"qr_two","",prefix+"/two");cookie(last,"qr_cdn","",prefix+"/cdn");source("");}catch(Exception e){failures++;report.append("ERROR synthetic cookie cleanup\n");}test.runOnMainSync(()->CookieManager.getInstance().setAcceptCookie(acceptedCookies));}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
