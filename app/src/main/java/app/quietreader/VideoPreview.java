package app.quietreader;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Created only after a play action; closing always releases the decoder and source. */
final class VideoPreview {
    interface Loader {void load(VideoView view)throws Exception;}
    interface Preflight {void check()throws Exception;}
    private final Activity activity;private final Loader loader;private final Preflight preflight;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Dialog dialog;private final LinearLayout layout;private final FrameLayout stage;
    private final TextView status;private final Button retry,full;
    private VideoView current;private MediaController controls;private boolean closed,fullscreen;
    private Runnable timeout,backRelease;
    VideoPreview(Activity activity,Document doc,boolean dark,Runnable source){
        this(activity,doc.title,dark,view->view.setVideoURI(android.net.Uri.parse(doc.videoUrl),java.util.Collections.singletonMap("Referer",doc.url)),()->{
            if(!ReaderMedia.allowed(doc.url,doc.videoUrl))throw new SecurityException("Unsupported source media");
            UrlPolicy.requirePublicHost(doc.videoUrl);
        },source);
    }
    VideoPreview(Activity activity,String title,boolean dark,Loader loader,Preflight preflight,Runnable source){
        this.activity=activity;this.loader=loader;this.preflight=preflight;
        layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);layout.setBackgroundColor(dark?0xff1f2025:0xfffafafa);
        int padding=dp(12);layout.setPadding(padding,padding,padding,padding);
        TextView heading=new TextView(activity);heading.setText(title);heading.setTextSize(17);heading.setTextColor(dark?0xffcdcfd5:0xff30323a);heading.setMaxLines(2);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);layout.addView(heading);
        status=new TextView(activity);status.setTextSize(13);status.setTextColor(dark?0xff9b9da7:0xff696c76);status.setPadding(0,dp(8),0,dp(8));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);layout.addView(status);
        stage=new FrameLayout(activity);stage.setBackgroundColor(0xff101114);layout.addView(stage,new LinearLayout.LayoutParams(-1,dp(190)));
        LinearLayout actions=new LinearLayout(activity);retry=new Button(activity);retry.setText("重试");full=new Button(activity);full.setText("全屏");Button original=new Button(activity);original.setText("来源页");
        for(Button button:new Button[]{retry,full,original})actions.addView(button,new LinearLayout.LayoutParams(0,-2,1));layout.addView(actions);
        Button close=new Button(activity);close.setText("关闭");close.setOnClickListener(v->dismiss());layout.addView(close,new LinearLayout.LayoutParams(-1,-2));
        dialog=new Dialog(activity){
            @Override public void dismiss(){shutdown();super.dismiss();}
            @Override public void onBackPressed(){back();}
        };
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setContentView(layout);dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.setOnDismissListener(ignored->shutdown());
        retry.setOnClickListener(v->start());full.setOnClickListener(v->setFullscreen(!fullscreen));original.setOnClickListener(v->{dismiss();source.run();});
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    void show(){if(closed)return;dialog.show();dialog.getWindow().setLayout(-1,-2);if(Build.VERSION.SDK_INT>=33)backRelease=Back.register(dialog,this::back);start();}
    void dismiss(){dialog.dismiss();}
    private void back(){if(fullscreen)setFullscreen(false);else dismiss();}
    private void shutdown(){if(closed)return;closed=true;release();if(backRelease!=null){backRelease.run();backRelease=null;}worker.shutdownNow();}
    @androidx.annotation.RequiresApi(33)
    private static final class Back {
        static Runnable register(Dialog dialog,Runnable action){android.window.OnBackInvokedCallback callback=action::run;dialog.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY,callback);return ()->dialog.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);}
    }
    private boolean active(VideoView view){return !closed&&current==view;}
    private void release(){
        if(timeout!=null){handler.removeCallbacks(timeout);timeout=null;}
        if(controls!=null){controls.hide();controls=null;}
        VideoView old=current;current=null;
        if(old!=null){old.setKeepScreenOn(false);old.setOnPreparedListener(null);old.setOnCompletionListener(null);old.setOnErrorListener((p,w,e)->true);old.stopPlayback();stage.removeView(old);}
    }
    private void failed(VideoView view){if(!active(view))return;release();if(fullscreen)setFullscreen(false);stage.setVisibility(View.GONE);full.setEnabled(false);status.setText("视频暂时无法播放，可能已失效、需要来源权限或格式不支持。可重试或在来源页查看。");retry.setEnabled(true);}
    private void start(){
        if(closed)return;release();stage.setVisibility(View.VISIBLE);full.setEnabled(true);status.setText("正在准备视频…");retry.setEnabled(false);
        VideoView video=new VideoView(activity);current=video;stage.addView(video,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        controls=new MediaController(activity);controls.setAnchorView(video);video.setMediaController(controls);
        video.setOnPreparedListener(player->{if(!active(video))return;if(timeout!=null){handler.removeCallbacks(timeout);timeout=null;}status.setText("点击画面可显示播放控制");video.setKeepScreenOn(true);video.start();});
        video.setOnCompletionListener(player->{if(!active(video))return;video.setKeepScreenOn(false);status.setText("播放结束");retry.setEnabled(true);});
        video.setOnErrorListener((player,what,extra)->{failed(video);return true;});
        timeout=()->failed(video);handler.postDelayed(timeout,20000);
        worker.execute(()->{try{preflight.check();handler.post(()->{if(!active(video))return;try{loader.load(video);video.start();}catch(Exception error){failed(video);}});}catch(Exception error){handler.post(()->failed(video));}});
    }
    private void setFullscreen(boolean value){
        if(closed)return;fullscreen=value;full.setText(value?"退出全屏":"全屏");
        stage.setLayoutParams(value?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(-1,dp(190)));
        dialog.getWindow().setLayout(-1,value?-1:-2);
    }
}
