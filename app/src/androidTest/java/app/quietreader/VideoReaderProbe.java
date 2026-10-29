package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static app.quietreader.Models.*;

/** Real native decoding of a local CC0 fixture; no claim of platform-stream availability. */
final class VideoReaderProbe {
    private final Instrumentation test;private final MainActivity home;int checks,failures;
    final StringBuilder report=new StringBuilder("LOCAL CC0 VIDEO; real VideoView/decoder/dialog/lifecycle, source transport replaced by test asset; no source playback acceptance\n");
    private File file,folder;private VideoPreview preview;
    VideoReaderProbe(Instrumentation test,MainActivity home){this.test=test;this.home=home;}
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static void call(Object owner,String name,Class<?>[] types,Object...values)throws Exception{Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(owner,values);}
    private void ui(Runnable work){test.runOnMainSync(work);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private boolean await(java.util.concurrent.Callable<Boolean> condition,long ms)throws Exception{long until=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<until){boolean[] good={false};ui(()->{try{good[0]=condition.call();}catch(Exception e){throw new IllegalStateException(e);}});if(good[0])return true;Thread.sleep(100);}return false;}
    private static View find(View view,String label){if(view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View result=find(((ViewGroup)view).getChildAt(i),label);if(result!=null)return result;}return null;}
    private void screenshot(String name)throws Exception{test.waitForIdleSync();Thread.sleep(500);android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap!=null){try(FileOutputStream out=new FileOutputStream(new File(folder,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}}
    void run(){
        try{
            folder=new File(test.getTargetContext().getExternalFilesDir(null),"video-reader-contract/"+System.currentTimeMillis());folder.mkdirs();file=new File(test.getTargetContext().getCacheDir(),"test-video-"+System.nanoTime()+".mp4");
            try(InputStream in=test.getContext().getAssets().open("media/flower.mp4");OutputStream out=new FileOutputStream(file)){in.transferTo(out);}
            Document doc=SourceParser.article(Source.SSPAI,"<h1>统一视频组件验证</h1><div class=ss__article__body><p>正文保留</p><video src='https://media-fixture.sspai.com/clip.mp4'></video><p>正文后续</p></div>","https://sspai.com/post/9876501031");
            AtomicInteger loads=new AtomicInteger(),sources=new AtomicInteger();Object[] readerWeb={null};int[] position={0};
            ui(()->{try{
                ((Repository)field(home,"repo")).cancelPending();((ArticlePreloader)field(home,"preloader")).pause();((Handler)field(home,"preloadHandler")).removeCallbacksAndMessages(null);
                DynamicBoard board=(DynamicBoard)field(home,"dynamicBoard");if(board!=null)board.close();
                set(home,"current",new Item(Source.SSPAI,doc.title,doc.url,""));set(home,"primaryPending",true);call(home,"frame",new Class[]{String.class,String.class},"统一视频","news");call(home,"scroller",new Class[0]);call(home,"render",new Class[]{Document.class},doc);
                readerWeb[0]=field(home,"readerWeb");position[0]=((android.webkit.WebView)readerWeb[0]).getScrollY();
                check(field(home,"videoPreview")==null&&loads.get()==0,"render/preload creates no player or media load");
                call(home,"playVideo",new Class[0]);VideoPreview production=(VideoPreview)field(home,"videoPreview");check(production!=null,"production play action opens common player");call(home,"closeVideo",new Class[0]);check((boolean)field(production,"closed")&&field(production,"current")==null,"closing in opening UI turn releases before deferred transport");
                preview=new VideoPreview(home,doc.title,false,view->{loads.incrementAndGet();view.setVideoPath(file.getAbsolutePath());},()->{},sources::incrementAndGet);set(home,"videoPreview",preview);preview.show();
            }catch(Exception e){throw new IllegalStateException(e);}});
            check(await(()->{VideoView v=(VideoView)field(preview,"current");return v!=null&&v.isPlaying()&&v.getDuration()>0&&v.getCurrentPosition()>0;},10000),"actual native decoder starts only after explicit action");
            ui(()->{try{check(loads.get()==1&&((FrameLayout)field(preview,"stage")).getChildCount()==1,"single active view and one media source");((Button)field(preview,"full")).performClick();check((boolean)field(preview,"fullscreen"),"full-screen uses same player");((MediaController)field(preview,"controls")).hide();}catch(Exception e){throw new IllegalStateException(e);}});
            screenshot("fullscreen");test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);Thread.sleep(300);
            ui(()->{try{check(!(boolean)field(preview,"fullscreen")&&!(boolean)field(preview,"closed")&&field(home,"readerWeb")==readerWeb[0],"Back leaves full-screen without replacing reader/player");}catch(Exception e){throw new IllegalStateException(e);}});
            screenshot("inline-dialog");ui(()->{try{MediaController c=(MediaController)field(preview,"controls");if(c!=null)c.hide();}catch(Exception e){throw new IllegalStateException(e);}});test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);Thread.sleep(200);
            ui(()->{try{check((boolean)field(preview,"closed")&&field(preview,"current")==null,"Back closes dialog and releases decoder");check(field(home,"readerWeb")==readerWeb[0]&&((android.webkit.WebView)readerWeb[0]).getScrollY()==position[0],"reader instance and position survive playback/full-screen");
                preview=new VideoPreview(home,"受限媒体验证",true,view->{throw new IOException("Controlled unavailable source");},()->{},sources::incrementAndGet);set(home,"videoPreview",preview);preview.show();
            }catch(Exception e){throw new IllegalStateException(e);}});
            check(await(()->field(preview,"current")==null&&((Button)field(preview,"retry")).isEnabled(),3000),"load failure releases decoder and enables deliberate retry");
            ui(()->{try{check(((FrameLayout)field(preview,"stage")).getVisibility()==View.GONE&&!((Button)field(preview,"full")).isEnabled(),"unavailable media has compact fallback without an empty player frame");}catch(Exception e){throw new IllegalStateException(e);}});
            screenshot("unavailable-dark");ui(()->{try{find((View)field(preview,"layout"),"来源页").performClick();check(sources.get()==1&&(boolean)field(preview,"closed"),"unavailable source action closes before fallback");
            }catch(Exception e){throw new IllegalStateException(e);}});
            CountDownLatch checking=new CountDownLatch(1),unblock=new CountDownLatch(1);AtomicInteger lateLoads=new AtomicInteger();
            ui(()->{preview=new VideoPreview(home,"取消预检查",false,view->lateLoads.incrementAndGet(),()->{checking.countDown();unblock.await();},sources::incrementAndGet);preview.show();});
            check(checking.await(3,TimeUnit.SECONDS),"asynchronous source preflight actually started");
            ui(()->preview.dismiss());unblock.countDown();Thread.sleep(300);
            ui(()->{try{check(lateLoads.get()==0&&field(preview,"current")==null&&(boolean)field(preview,"closed"),"close cancels pending preflight and ignores late transport");}catch(Exception e){throw new IllegalStateException(e);}});
            AtomicInteger attempts=new AtomicInteger();
            ui(()->{try{preview=new VideoPreview(home,"超时与重试验证",false,view->{if(attempts.incrementAndGet()>1)view.setVideoPath(file.getAbsolutePath());},()->{},sources::incrementAndGet);set(home,"videoPreview",preview);preview.show();}catch(Exception e){throw new IllegalStateException(e);}});
            check(await(()->field(preview,"current")==null&&((Button)field(preview,"retry")).isEnabled(),24000),"actual 20-second preparation timeout releases stalled player");
            check(attempts.get()==1,"timeout does not retry or reload automatically");
            ui(()->{try{((Button)field(preview,"retry")).performClick();}catch(Exception e){throw new IllegalStateException(e);}});
            check(await(()->{VideoView v=(VideoView)field(preview,"current");return v!=null&&v.isPlaying();},7000)&&attempts.get()==2,"explicit retry starts a fresh native decoder after timeout");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);
            check(await(()->field(home,"videoPreview")==null&&(boolean)field(preview,"closed")&&field(preview,"current")==null,4000),"actual Home/background lifecycle releases player");
            report.append("screenshots=").append(folder).append('\n');
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{ui(()->{if(preview!=null)preview.dismiss();});if(file!=null)file.delete();test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove("https://sspai.com/post/9876501031").commit();}
        // The parent runner owns Activity cleanup and finishes the instrumentation.
    }
}
