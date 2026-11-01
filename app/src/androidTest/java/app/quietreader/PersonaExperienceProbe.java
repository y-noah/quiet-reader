package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import app.quietreader.discovery.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Simulated personas over captured public supply, actual home/reader UI, isolated emulator only. */
final class PersonaExperienceProbe {
    private final Instrumentation test;private DiscoveryActivity home;private Activity reader;private DiscoveryController controller;private ListView list;private ExecutorService serial;private FeedStore store;
    private int completed,failures;private File root;private final StringBuilder report=new StringBuilder();
    private static final String[] NAMES={"cold-start","developer","finance","society","international","sports","culture-science","community","low-entertainment","repeat-sensitive"};
    private static final String[] TOPICS={"","AI|代码|模型|手机|电脑|开发|芯片|软件","经济|公司|市场|投资|财政|企业|银行","社会|政策|公共|教育|事件|官方","国际|美国|欧洲|中国|战争|外交","篮球|足球|比赛|运动|球队|球员","文化|科学|研究|电影|文学|教育","经验|工作|生活|求助|分享|如何","科技|社会|研究|经济|教育",""};
    private static final Source[][] MORE={{},{Source.V2EX,Source.ITHOME},{Source.CLS},{Source.ZHIHU,Source.THEPAPER},{Source.BBC,Source.NYT},{Source.HUPU},{},{Source.V2EX,Source.ZHIHU},{},{}};
    private static final Source[][] LESS={{},{Source.WEIBO,Source.BAIDU},{Source.TIEBA,Source.WEIBO},{Source.BAIDU},{Source.WEIBO},{Source.CLS},{},{Source.BAIDU},{Source.WEIBO,Source.BAIDU},{}};
    PersonaExperienceProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String key){try{Field f=owner.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new IllegalStateException(error[0]);}
    private boolean onUi(BooleanSupplier action){boolean[] out={false};ui(()->out[0]=action.getAsBoolean());return out[0];}
    private void await(String label,BooleanSupplier action,long timeout)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;while(!onUi(action)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException(label);Thread.sleep(40);}}
    @SuppressWarnings("unchecked") private List<FeedStore.Row> feed(){List<FeedStore.Row>[] out=new List[]{null};ui(()->out[0]=new ArrayList<>((List<FeedStore.Row>)field(home,"feed")));return out[0];}
    private String read(File file)throws Exception{return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);}
    private void writeRound(int persona,JSONObject row)throws Exception{try(FileOutputStream out=new FileOutputStream(new File(root,"persona-"+persona+".jsonl"),true)){out.write((row+"\n").getBytes(StandardCharsets.UTF_8));}}
    private int interest(HotCandidate c,int persona){int score=Arrays.asList(MORE[persona]).contains(c.source)?3:Arrays.asList(LESS[persona]).contains(c.source)?-3:0;for(String term:TOPICS[persona].split("\\|"))if(!term.isEmpty()&&(c.title+" "+c.snippet).contains(term))score+=2;return score;}
    private WebView web(View node){if(node instanceof WebView&&String.valueOf(((WebView)node).getUrl()).startsWith("https://quiet-reader.invalid/?render="))return (WebView)node;if(node instanceof ViewGroup)for(int n=0;n<((ViewGroup)node).getChildCount();n++){WebView child=web(((ViewGroup)node).getChildAt(n));if(child!=null)return child;}return null;}
    private JSONObject dom(WebView view)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] value={"null"};ui(()->{boolean old=view.getSettings().getJavaScriptEnabled();view.getSettings().setJavaScriptEnabled(true);view.evaluateJavascript("JSON.stringify({parts:document.querySelectorAll('main .part').length,chars:(document.querySelector('main')?.textContent||'').length,images:document.querySelectorAll('main img').length,overflow:document.documentElement.scrollWidth>innerWidth+2,text:(document.querySelector('main')?.innerText||'').slice(0,160)})",result->{view.getSettings().setJavaScriptEnabled(old);value[0]=result;done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("reader DOM timeout");Object decoded=new JSONTokener(value[0]).nextValue();return decoded instanceof String?new JSONObject((String)decoded):new JSONObject();}
    private Document body(HotCandidate c){try{File file=new File(root,"bodies/"+c.id+".html");if(!file.exists())return null;Document d=SourceParser.readingArticle(c.source,read(file),c.originalUrl);int text=d.blocks.stream().filter(b->b.type.equals("text")).mapToInt(b->b.value.length()).sum();return !d.loginRequired&&!d.sourceUnavailable&&!d.filteredVideo&&text>=180?d:null;}catch(Throwable error){return null;}}
    private void screenshot(String name)throws Exception{android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();if(image!=null){try(FileOutputStream out=new FileOutputStream(new File(root,name))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}}
    private int paintedBody() {
        android.graphics.Bitmap shot=test.getUiAutomation().takeScreenshot();if(shot==null)return 0;
        try{Map<Integer,Integer> colors=new HashMap<>();int total=0;
            for(int y=shot.getHeight()/5;y<shot.getHeight()*9/10;y+=6)for(int x=shot.getWidth()/12;x<shot.getWidth()*11/12;x+=6){int pixel=shot.getPixel(x,y);int bucket=((pixel>>20)&15)<<8|((pixel>>12)&15)<<4|((pixel>>4)&15);colors.merge(bucket,1,Integer::sum);total++;}
            int background=colors.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey(),ink=0;
            for(Map.Entry<Integer,Integer> color:colors.entrySet()){int p=color.getKey();if(Math.abs((p>>8)-(background>>8))+Math.abs(((p>>4)&15)-((background>>4)&15))+Math.abs((p&15)-(background&15))>8)ink+=color.getValue();}
            return ink;
        }finally{shot.recycle();}
    }
    private int awaitPaint(WebView view)throws Exception{
        CountDownLatch drawn=new CountDownLatch(1);ui(()->view.postVisualStateCallback(SystemClock.uptimeMillis(),new WebView.VisualStateCallback(){@Override public void onComplete(long id){drawn.countDown();}}));
        if(!drawn.await(5,TimeUnit.SECONDS))throw new IllegalStateException("reader visual state timeout");
        long deadline=SystemClock.elapsedRealtime()+5000;int ink;
        do{test.waitForIdleSync();ink=paintedBody();if(ink>=50)return ink;Thread.sleep(100);}while(SystemClock.elapsedRealtime()<deadline);
        throw new IllegalStateException("reader DOM exists but screen body is blank");
    }
    void run(int persona){String database="test-ui-persona-"+persona+"-"+UUID.randomUUID()+".db";Instrumentation.ActivityMonitor monitor=null;
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")||!Build.MODEL.startsWith("sdk_gphone")||persona<0||persona>=10)throw new IllegalStateException("isolated persona emulator only");
            root=new File(test.getTargetContext().getExternalFilesDir(null),"persona-campaign");File log=new File(root,"persona-"+persona+".jsonl");if(log.exists())throw new IllegalStateException("archive previous persona evidence before rerun");
            JSONArray corpus=new JSONArray(read(new File(root,"corpus.json")));Map<Source,List<HotCandidate>> supply=new EnumMap<>(Source.class);Map<String,Document> bodies=new HashMap<>();long now=System.currentTimeMillis();
            for(int n=0;n<corpus.length();n++){HotCandidate original=HotCandidate.fromJson(corpus.getJSONObject(n));HotCandidate c=new HotCandidate(original.source,original.originalUrl,original.title,original.snippet,original.rawHeat,original.video,original.sourceRank,original.publishedAt,now);supply.computeIfAbsent(c.source,k->new ArrayList<>()).add(c);Document body=body(c);if(body!=null)bodies.put(c.id,body);}
            if(bodies.size()<20)throw new IllegalStateException("fewer than20 public readable captures: "+bodies.size());
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){
                seed.ensureCandidatePolicy();for(Source source:Source.values())seed.enable(source,supply.containsKey(source));
                for(Map.Entry<Source,List<HotCandidate>> entry:supply.entrySet())seed.upsertSnapshot(entry.getKey(),seed.beginRefresh(entry.getKey()),entry.getValue(),now);
                for(Source source:MORE[persona])seed.feedback(UUID.randomUUID().toString(),"MORE","source",source.name(),now,null,"simulated persona",source);
                for(Source source:LESS[persona])seed.feedback(UUID.randomUUID().toString(),"LESS","source",source.name(),now+1,null,"simulated persona",source);
                if(persona==6)for(List<HotCandidate> candidates:supply.values())for(HotCandidate c:candidates)if(interest(c,persona)>0)seed.feedback(UUID.randomUUID().toString(),"MORE","sample",CandidateOrder.versionTarget(c),now+2,null,c.modelText(),c.source);
                seed.finishInterestSetup(List.of(),Map.of(),now+3);seed.readLearningEnabled(false,now+4);
            }
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            controller=(DiscoveryController)field(home,"controller");list=(ListView)field(home,"list");serial=(ExecutorService)field(controller,"storage");store=(FeedStore)field(controller,"store");
            ui(()->home.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));await("home initial feed",()->home.hasWindowFocus()&&(boolean)field(controller,"ready")&&!((List<?>)field(home,"feed")).isEmpty()&&!ReaderStartup.pending(),15000);
            if(persona==6){
                ui(controller::requestRecommendation);
                await("content persona model preparation",()->field(controller,"recommendation")==null&&(long)field(controller,"pending")>0,180000);
                long[] pending={0};ui(()->pending[0]=(long)field(controller,"pending"));
                JSONObject model=serial.submit(()->store.snapshotInfo(pending[0])).get(5,TimeUnit.SECONDS);
                if(model==null||!"personal".equals(model.optString("mode")))throw new IllegalStateException("content persona needs a real model-ranked snapshot");
                report.append("content persona real model snapshot prepared\n");
            }
            Set<String> seen=new HashSet<>(),readIds=new HashSet<>();Repository cache=new Repository(home);
            for(int round=0;round<20;round++){
                JSONObject evidence=new JSONObject().put("persona",persona).put("name",NAMES[persona]).put("round",round+1).put("mode","public-board-and-body-replay");long start=SystemClock.elapsedRealtime();long before=onSnapshot();
                ui(()->((View)field(home,"updates")).performClick());await("refresh did not finish",()->!(boolean)field(controller,"explicitRefreshRunning")&&(long)field(controller,"snapshot")!=before,12000);serial.submit(()->{}).get(5,TimeUnit.SECONDS);
                evidence.put("refreshMs",SystemClock.elapsedRealtime()-start);List<FeedStore.Row> visible=feed();JSONArray cards=new JSONArray();JSONObject counts=new JSONObject();int repeats=0;FeedStore.Row selected=null;int best=Integer.MIN_VALUE;
                for(FeedStore.Row row:visible){HotCandidate c=row.candidate;boolean repeat=!seen.add(c.id);if(repeat)repeats++;counts.put(c.source.name(),counts.optInt(c.source.name())+1);cards.put(new JSONObject().put("id",c.id).put("source",c.source.name()).put("title",c.title).put("interest",interest(c,persona)).put("repeat",repeat));
                    int score=interest(c,persona)-(readIds.contains(c.id)?20:0);if(bodies.containsKey(c.id)&&score>best){best=score;selected=row;}}
                evidence.put("rankingMode",String.valueOf(field(controller,"visibleMode")));evidence.put("cards",cards).put("counts",counts).put("repeatCount",repeats).put("count",visible.size());
                if(selected==null){evidence.put("failure","no readable captured body in presented batch");writeRound(persona,evidence);throw new IllegalStateException("no observed readable body for round"+(round+1));}
                FeedStore.Row choice=selected;Document document=bodies.get(choice.candidate.id);ui(()->{if(!new ArticleSession(home,choice.candidate.readerItem()).stamp(document))throw new IllegalStateException("capture session mismatch");cache.cacheArticle(document);});
                int index=visible.indexOf(choice);ui(()->list.setSelectionFromTop(index,0));test.waitForIdleSync();int anchor=list.getFirstVisiblePosition();
                if(round==0)screenshot("persona-"+persona+"-home.png");monitor=test.addMonitor(MainActivity.class.getName(),null,false);long open=SystemClock.elapsedRealtime();
                ui(()->{View wrapper=list.getChildAt(index-list.getFirstVisiblePosition());if(!(wrapper instanceof ViewGroup))throw new IllegalStateException("selected row absent");((ViewGroup)wrapper).getChildAt(0).performClick();});
                reader=test.waitForMonitorWithTimeout(monitor,12000);if(reader==null){evidence.put("selected",choice.candidate.json()).put("failure","card did not open reader");ui(()->{try{evidence.put("openingReader",field(home,"openingReader")).put("startupPending",ReaderStartup.pending()).put("startupFailed",ReaderStartup.failed()).put("startupDeferred",ReaderStartup.deferred()).put("focus",home.hasWindowFocus());}catch(Exception ignored){}});screenshot("persona-"+persona+"-failure.png");writeRound(persona,evidence);throw new IllegalStateException("card did not open reader");}ui(()->reader.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
                JSONObject rendered=new JSONObject();WebView[] own={null};long deadline=SystemClock.elapsedRealtime()+12000;
                do{ui(()->own[0]=web(reader.getWindow().getDecorView()));if(own[0]!=null){rendered=dom(own[0]);if(rendered.optInt("parts")>0&&rendered.optInt("chars")>180)break;}Thread.sleep(80);}while(SystemClock.elapsedRealtime()<deadline);
                try{evidence.put("paintedBodySamples",awaitPaint(own[0]));}catch(Exception error){screenshot("persona-"+persona+"-failure.png");writeRound(persona,evidence.put("failure",error.toString()).put("selected",choice.candidate.json()).put("reader",rendered));throw error;}
                evidence.put("readMs",SystemClock.elapsedRealtime()-open).put("selected",choice.candidate.json()).put("reader",rendered).put("selectedInterest",best);
                if(rendered.optInt("parts")==0||rendered.optInt("chars")<=180||rendered.optBoolean("overflow")){writeRound(persona,evidence.put("failure","reader missing or overflow"));throw new IllegalStateException("reader missing or overflow");}
                ui(()->own[0].scrollBy(0,360));awaitPaint(own[0]);if(round==19)screenshot("persona-"+persona+"-reader.png");test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
                await("back did not return to same home",()->home.hasWindowFocus(),6000);reader=null;
                List<FeedStore.Row> returned=feed();boolean stable=visible.size()==returned.size();for(int n=0;stable&&n<visible.size();n++)stable=visible.get(n).eventId.equals(returned.get(n).eventId);
                evidence.put("returnStable",stable).put("anchorBefore",anchor).put("anchorAfter",list.getFirstVisiblePosition());if(!stable){writeRound(persona,evidence.put("failure","reader return changed home"));throw new IllegalStateException("reader return changed home");}
                readIds.add(choice.candidate.id);evidence.put("elapsedMs",SystemClock.elapsedRealtime()-start).put("complete",true);writeRound(persona,evidence);completed++;
                if(persona==9&&round==9)ui(()->controller.platformFeedback(choice.candidate.source,false));
            }
            cache.close();report.append("persona=").append(persona).append(" name=").append(NAMES[persona]).append(" readableCaptures=").append(bodies.size()).append(" uniqueReads=").append(readIds.size()).append('\n');
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{if(monitor!=null)test.removeMonitor(monitor);ui(()->{if(reader!=null)reader.finish();if(home!=null)home.finish();});if(serial!=null)try{serial.awaitTermination(5,TimeUnit.SECONDS);}catch(Exception ignored){}test.getTargetContext().deleteDatabase(database);}
        report.append("completedRounds=").append(completed).append(" failures=").append(failures).append('\n');Bundle out=new Bundle();out.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);
    }
    private long onSnapshot(){long[] value={0};ui(()->value[0]=(long)field(controller,"snapshot"));return value[0];}
}
