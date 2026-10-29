package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.WebView;
import app.quietreader.discovery.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Real discovery viewport -> production prefetch/HTTP/cache -> actual common reader.
 * All source data and HTTPS responses in this probe are controlled, never public samples. */
final class ImagePrefetchProbe {
    private final Instrumentation test;
    private final boolean batteryPolicy;
    private boolean batteryChanged;
    private DiscoveryActivity discovery;private MainActivity reader;private DiscoveryController controller;private Repository repo;
    private ExecutorService storage;private byte[] png;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    private final List<String> requested=new CopyOnWriteArrayList<>();
    private final Map<String,HotCandidate> imageTopics=new HashMap<>();
    private final AtomicBoolean release=new AtomicBoolean(),disconnected=new AtomicBoolean(),releaseLater=new AtomicBoolean(),disconnectedLater=new AtomicBoolean();
    private final String prefix="https://9.9.9.9/qr-prefetch-"+System.currentTimeMillis()+"/";
    ImagePrefetchProbe(Instrumentation test){this(test,false);}
    ImagePrefetchProbe(Instrumentation test,boolean batteryPolicy){this.test=test;this.batteryPolicy=batteryPolicy;}
    private Object field(Object target,String name){try{Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable r){test.runOnMainSync(r);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(String label,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+10000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Image prefetch deadline: "+label);Thread.sleep(25);}}
    @SuppressWarnings("unchecked") private List<FeedStore.Row> rows(){return (List<FeedStore.Row>)field(controller,"rows");}
    private void shell(String command)throws Exception{try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] buffer=new byte[1024];while(in.read(buffer)!=-1){}}}
    private void transport(){URL.setURLStreamHandlerFactory(protocol->!protocol.equals("https")?null:new URLStreamHandler(){
        @Override protected URLConnection openConnection(URL url)throws IOException{
            String target=url.toString();if(!target.startsWith(prefix))throw new IOException("Unexpected synthetic image URL");
            return new HttpURLConnection(url){
                private int ordinal;
                public void connect(){}public boolean usingProxy(){return false;}
                public void disconnect(){if(ordinal==3)disconnected.set(true);if(ordinal==6)disconnectedLater.set(true);}
                @Override public int getResponseCode(){requested.add(target);ordinal=requested.size();if(ordinal==3||ordinal==6){AtomicBoolean gate=ordinal==3?release:releaseLater;while(!gate.get())try{Thread.sleep(10);}catch(InterruptedException ignored){}}return 200;}
                @Override public long getContentLengthLong(){return png.length;}
                @Override public InputStream getInputStream(){return new ByteArrayInputStream(png);}
            };
        }
    });}
    void run(){String database="test-ui-image-prefetch-"+UUID.randomUUID()+".db";List<HotCandidate> candidates=new ArrayList<>();
        try{
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(500,900,android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff648bab);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();png=bytes.toByteArray();transport();
            long now=System.currentTimeMillis();
            long base=9000000000L+(now%100000000L)*100;
            for(int n=0;n<24;n++){
                HotCandidate candidate=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(base+n),"配图主动缓存受控中文话题第"+n+"项","","",false,n+1,null,now);candidates.add(candidate);
                imageTopics.put(prefix+n+"-first.png",candidate);imageTopics.put(prefix+n+"-second.png",candidate);
            }
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){for(Source source:Source.values())store.enable(source,source==Source.THEPAPER);store.upsertSnapshot(Source.THEPAPER,store.beginRefresh(Source.THEPAPER),candidates,now);}
            ui(()->{repo=new Repository(test.getTargetContext());for(int n=0;n<candidates.size();n++){
                Item item=candidates.get(n).readerItem();Document doc=new Document();doc.url=item.url;doc.title=item.title;
                Section body=new Section("body","正文",false);body.blocks.add(new Block("text","这是用于检查主动缓存的中文正文，真实推荐效果不能由合成样本证明。".repeat(20)));body.blocks.add(new Block("image",prefix+n+"-first.png"));body.blocks.add(new Block("image",prefix+n+"-second.png"));
                doc.sections.add(body);doc.blocks.addAll(body.blocks);ArticleVersion.capture(item,doc);repo.cacheArticle(doc);
            }});
            discovery=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_database",database));
            controller=(DiscoveryController)field(discovery,"controller");storage=(ExecutorService)field(controller,"storage");
            await("three image requests",()->requested.size()>=3);
            check(requested.get(0).endsWith("-first.png")&&requested.get(1).endsWith("-first.png")&&requested.get(2).endsWith("-first.png")&&new HashSet<>(requested).size()==3,"current viewport spreads first figures across topics before downloading second figures");
            check(onUi(()->!((ArticlePreloader)field(controller,"preloader")).busy()),"body acquisition completes before image requests start");
            check(storage.submit(()->{FeedStore store=(FeedStore)field(controller,"store");return store.activeFeedback(System.currentTimeMillis()).length()==0;}).get(5,TimeUnit.SECONDS),"proactive body and image acquisition does not create interest feedback");
            String first=requested.get(0),abandoned=requested.get(2);HotCandidate selected=onUiCandidate(first);
            ReaderImages diskReader=new ReaderImages(u->android.webkit.CookieManager.getInstance().getCookie(u),(url,ref,cookies)->{throw new IOException("Network must not run for prepared image");},8192);diskReader.attach(test.getTargetContext());
            check(Arrays.equals(png,diskReader.load(first,selected.originalUrl)),"before any card click a new image cache instance restores the proactive bytes from disk");
            if(batteryPolicy){
                batteryChanged=true;shell("dumpsys battery set level 10");await("low battery cancellation",disconnected::get);release.set(true);
                check(!onUi(()->((ImagePreloader)field(controller,"imagePreloader")).busy()),"low battery signal cancels image speculation during an actual blocked request");
                int before=requested.size();Thread.sleep(600);check(requested.size()==before,"low battery does not start further image requests");
                shell("dumpsys battery reset");batteryChanged=false;Thread.sleep(500);check(requested.size()==before,"battery recovery alone does not wake speculative downloads");
                ui(()->controller.viewport(0));
            }else{ui(()->controller.viewport(rows().size()-1));await("old viewport disconnected",disconnected::get);release.set(true);}
            await("shifted viewport image requests",()->requested.size()>=6);
            if(batteryPolicy){check(requested.get(3).equals(abandoned),"next eligible viewport retries the cancelled image without a five-minute false failure penalty");}
            else{
                HotCandidate[] expectedViewport={null};ui(()->expectedViewport[0]=rows().get(rows().size()-1).candidate);
                check(onUiCandidate(requested.get(3)).id.equals(expectedViewport[0].id),"moving to the end of the finite batch reprioritizes its actual viewport");
                boolean abandonedMiss=false;try{diskReader.load(abandoned,onUiCandidate(abandoned).originalUrl);}catch(IOException expectedError){abandonedMiss=true;}
                check(abandonedMiss,"late payload from the abandoned viewport is not published");
            }
            String blocked=requested.get(5);
            // A real HOME must disconnect the in-flight connection and leave its late payload unpublished.
            shell("input keyevent 3");await("background cancellation",()->!(boolean)field(controller,"foreground")&&disconnectedLater.get());releaseLater.set(true);Thread.sleep(200);
            boolean missing=false;HotCandidate third=onUiCandidate(blocked);try{diskReader.load(blocked,third.originalUrl);}catch(IOException expected){missing=true;}
            check(missing&&!onUi(()->((ImagePreloader)field(controller,"imagePreloader")).busy()),"HOME disconnects a blocked production connection and rejects late image bytes");
            int before=requested.size();Thread.sleep(800);check(requested.size()==before,"background page does not continue downloading images");
            Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);
            try{
                ui(()->{try{Method open=DiscoveryActivity.class.getDeclaredMethod("open",HotCandidate.class);open.setAccessible(true);open.invoke(discovery,selected);}catch(Exception e){throw new IllegalStateException(e);}});
                Activity next=test.waitForMonitorWithTimeout(monitor,8000);if(!(next instanceof MainActivity))throw new IllegalStateException("Unified reader missing");reader=(MainActivity)next;
            }finally{test.removeMonitor(monitor);}
            await("reader visible",()->field(reader,"readerWeb")!=null&&field(reader,"reading")!=null&&(boolean)field(field(reader,"engagement"),"ready"));
            AtomicBoolean decoded=new AtomicBoolean();
            long end=SystemClock.elapsedRealtime()+7000;while(!decoded.get()&&SystemClock.elapsedRealtime()<end){ui(()->{WebView web=(WebView)field(reader,"readerWeb");web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("Array.from(document.images).some(i=>i.src==="+org.json.JSONObject.quote(first)+"&&i.complete&&i.naturalWidth===500)",value->{decoded.set("true".equals(value));web.getSettings().setJavaScriptEnabled(false);});});Thread.sleep(80);}
            check(decoded.get(),"production card opens the unified reader and decodes the proactively cached image");
            check(Collections.frequency(requested,first)==1,"opening the prepared image does not download it a second time");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{
            if(batteryChanged)try{shell("dumpsys battery reset");}catch(Exception ignored){}
            release.set(true);releaseLater.set(true);ui(()->{if(reader!=null)reader.finish();if(discovery!=null)discovery.finish();if(repo!=null)repo.close();});test.waitForIdleSync();if(storage!=null)try{storage.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            ImageDiskCache disk=new ImageDiskCache(new File(test.getTargetContext().getCacheDir(),"reader-images-v1"));for(int n=0;n<candidates.size();n++)for(String suffix:List.of("-first.png","-second.png")){String image=prefix+n+suffix;ReaderImages.shared.remove(image);disk.removeUrl(image);}for(HotCandidate candidate:candidates)test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove(candidate.originalUrl).commit();test.getTargetContext().deleteDatabase(database);
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private HotCandidate onUiCandidate(String image){HotCandidate candidate=imageTopics.get(image);if(candidate==null)throw new IllegalStateException("Image topic missing");return candidate;}
}
