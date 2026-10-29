package app.quietreader;

import android.app.*;
import android.os.*;
import org.json.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import app.quietreader.discovery.HotCandidate;

/** Voluntary current-list labeling, separate from explicit interest training. */
final class RecommendationTrialUi implements AutoCloseable {
    private final Activity activity;
    private final DiscoveryController controller;
    private final RecommendationTrialStore store;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private AlertDialog dialog;
    private final TrialExportUi export;
    private String session;
    private boolean closed,suspended;
    private int epoch;
    RecommendationTrialUi(Activity activity,DiscoveryController controller){this.activity=activity;this.controller=controller;String diagnostic=activity.getIntent().getStringExtra("test_database");store=(activity.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0&&diagnostic!=null&&diagnostic.matches("test-ui-[a-z0-9-]+\\.db")?new RecommendationTrialStore(activity,"test-trial-"+diagnostic):new RecommendationTrialStore(activity);export=new TrialExportUi(activity,store.getDatabaseName());}
    private <T>void work(Callable<T> task,Consumer<T> then){int request=epoch;worker.execute(()->{try{T value=task.call();main.post(()->{if(!closed&&!suspended&&!activity.isFinishing()&&!activity.isDestroyed()&&request==epoch)then.accept(value);});}catch(Exception error){main.post(()->{if(!closed&&!suspended&&request==epoch&&!activity.isFinishing()&&!activity.isDestroyed()){dismiss();dialog=new AlertDialog.Builder(activity).setTitle("评价暂未保存").setMessage("请稍后重试，已保存的选择会保留。").setPositiveButton("关闭",null).show();}});}});}
    private void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;}}
    void menu(){
        epoch++;session=null;work(()->{JSONObject baseline=store.protocol();return new String[]{store.summary(),store.unfinished(),store.latestCompleted(),baseline==null?null:baseline.toString()};},state->{
            dismiss();java.util.List<String> actions=new java.util.ArrayList<>();
            if(state[1]!=null)actions.add("继续上次评价");actions.add("评价发现页列表");actions.add("对比推荐效果");actions.add("准备新话题评价");if(state[3]!=null)actions.add("评价准备后的新话题");
            if(state[2]!=null)actions.add("查看最近完成评价");actions.add("导出评价记录");actions.add("清除评价记录");
            dialog=new AlertDialog.Builder(activity).setTitle("本轮推荐评价").setMessage(state[0]+"\n列表评价最多20项；对比评价会将三种顺序的前排合并去重、打乱展示，最多60项，可随时中断后继续。记录只保存在本机，不用于调教兴趣。").setPositiveButton("选择操作",(d,w)->{
                dialog=new AlertDialog.Builder(activity).setTitle("本轮推荐评价").setItems(actions.toArray(new String[0]),(picker,n)->{
                    String action=actions.get(n);if("继续上次评价".equals(action))resume(state[1]);else if("导出评价记录".equals(action))export.begin();else if("清除评价记录".equals(action))clear();else if("对比推荐效果".equals(action))compare();else if("准备新话题评价".equals(action))prepareProspective();else if("评价准备后的新话题".equals(action)){try{compare(new JSONObject(state[3]));}catch(JSONException error){throw new IllegalStateException(error);}}else if("查看最近完成评价".equals(action))review(state[2]);else start();
                }).setNegativeButton("取消",null).show();
            }).setNegativeButton("关闭",null).show();
        });
    }
    private void review(String id){
        epoch++;work(()->store.read(id),value->{
            if(value==null){menu();return;}
            try{dismiss();String date=java.text.DateFormat.getDateTimeInstance().format(new java.util.Date(value.getLong("frozen_at_ms")));
                dialog=new AlertDialog.Builder(activity).setTitle("最近完成的评价").setMessage(date+"\n\n"+RecommendationTrialStore.completedSummary(value)).setPositiveButton("关闭",null).show();
            }catch(JSONException error){throw new IllegalStateException(error);}
        });
    }
    private void prepareProspective(){
        dismiss();dialog=new AlertDialog.Builder(activity).setTitle("准备新话题评价")
            .setMessage("先记住当前偏好和已有话题，再刷新来源，从之后新出现的话题里对比三种顺序。期间偏好记录变化或过期，需要重新准备。已完成的评价保留；新准备会替换上次准备。选择只保存在本机，不用于训练。")
            .setNegativeButton("取消",null).setPositiveButton("开始准备",(d,w)->{
                epoch++;int request=epoch;dismiss();
                controller.prepareProspectiveTrial(value->{if(!available(request))return;work(()->{store.protocol(value);return true;},ignored->{dismiss();dialog=new AlertDialog.Builder(activity).setTitle("已准备新话题评价").setMessage("返回发现页刷新来源后，可从本轮推荐评价中选择“评价准备后的新话题”。已有话题和本组用过的候选会跳过；如果偏好变化，系统会提示重新准备。自动筛选仍不能证明不同平台的话题属于独立事件。").setPositiveButton("知道了",null).show();});},message->{if(!available(request))return;dismiss();dialog=new AlertDialog.Builder(activity).setTitle("暂未准备").setMessage(message).setPositiveButton("关闭",null).show();});
            }).show();
    }
    private void compare(){compare(null);}
    private void compare(JSONObject baseline){
        epoch++;int request=epoch;dismiss();dialog=new AlertDialog.Builder(activity).setTitle("准备对比内容").setMessage("从最多100个热门候选中整理三种顺序。请保持此页在前台；准备完成后，同一话题只需评价一次。").setNegativeButton("取消",(d,w)->cancelComparison()).create();dialog.setOnCancelListener(d->cancelComparison());dialog.show();
        controller.trialComparison(baseline,value->{if(!available(request))return;long at=System.currentTimeMillis();work(()->store.create(RecommendationTrialStore.freezeComparison(value,at,new java.security.SecureRandom())),this::resume);},message->{if(!available(request))return;dismiss();dialog=new AlertDialog.Builder(activity).setTitle("本轮对比未开始").setMessage(message).setPositiveButton("关闭",null).show();});
    }
    private boolean available(int request){return request==epoch&&!closed&&!suspended&&!activity.isFinishing()&&!activity.isDestroyed();}
    private void cancelComparison(){epoch++;controller.cancelTrialComparison();}
    private void start(){JSONObject frozen=controller.trialSnapshot();if(frozen.optJSONArray("items").length()==0){dismiss();dialog=new AlertDialog.Builder(activity).setTitle("发现页没有可评价内容").setMessage("回到发现页获取内容后再试。").setPositiveButton("关闭",null).show();return;}epoch++;work(()->store.create(frozen),this::resume);}
    void resume(String id){epoch++;session=id;work(()->store.read(id),value->{if(value==null){session=null;menu();return;}render(value);});}
    private void render(JSONObject value){
        try{int index=RecommendationTrialStore.next(value),total=value.getJSONArray("items").length();dismiss();
            if(index==total){session=null;dialog=new AlertDialog.Builder(activity).setTitle("本轮评价已保存").setMessage(RecommendationTrialStore.completedSummary(value)).setPositiveButton("完成",null).show();return;}
            HotCandidate item=HotCandidate.fromJson(value.getJSONArray("items").getJSONObject(index).getJSONObject("candidate"));String id=session;
            dialog=new AlertDialog.Builder(activity).setTitle("本轮评价 · "+(index+1)+"/"+total).setMessage(item.title+"\n\n来源："+item.source.label+(item.snippet.isEmpty()?"":"\n\n"+item.snippet)+"\n\n只按当前信息判断即可；拿不准可以选不确定，也可以返回，稍后继续。")
                .setPositiveButton("想看",(d,w)->record(id,index,"want")).setNegativeButton("不想看",(d,w)->record(id,index,"skip")).setNeutralButton("不确定",(d,w)->record(id,index,"unsure")).create();
            dialog.setOnCancelListener(d->{epoch++;session=null;});dialog.show();
        }catch(JSONException error){throw new IllegalStateException(error);}
    }
    private void record(String id,int index,String choice){epoch++;work(()->{store.label(id,index,choice,System.currentTimeMillis());return store.read(id);},this::render);}
    private void clear(){dialog=new AlertDialog.Builder(activity).setTitle("清除试用评价？").setMessage("清除本轮推荐评价记录及新话题评价的准备状态，阅读偏好、收藏和登录保留。").setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->{epoch++;session=null;work(()->{store.clear();return true;},ignored->menu());}).show();}
    void pause(){suspended=true;cancelComparison();dismiss();}
    void foreground(){if(suspended){suspended=false;if(session!=null)resume(session);}}
    void saveExport(Bundle state){export.save(state);}
    void restoreExport(Bundle state){export.restore(state);}
    void exportResult(int result,android.content.Intent data){export.result(result,data);}
    String activeSession(){return session;}
    @Override public void close(){if(closed)return;closed=true;export.close();cancelComparison();dismiss();main.removeCallbacksAndMessages(null);worker.execute(store::close);worker.shutdown();}
}
