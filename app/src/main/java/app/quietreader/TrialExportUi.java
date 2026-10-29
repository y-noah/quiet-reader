package app.quietreader;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.widget.Toast;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.*;

/** User-initiated SAF export; no storage permission, network, or automatic sharing. */
final class TrialExportUi implements AutoCloseable {
    static final int REQUEST=304;
    private static final String STATE="trialExportFile",PREFIX="trial-observations-";
    private final Activity activity;
    private final String database;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private String pending;
    private boolean busy,closed;
    TrialExportUi(Activity activity,String database){this.activity=activity;this.database=database;}
    private File folder(){return new File(activity.getCacheDir(),"trial-exports");}
    private File pendingFile(){return pending==null?null:new File(folder(),pending);}
    void save(Bundle state){if(pending!=null)state.putString(STATE,pending);}
    void restore(Bundle state){String name=state==null?null:state.getString(STATE);if(name!=null&&name.matches("trial-observations-[a-f0-9-]{36}\\.db")){pending=name;}}
    private void toast(String message){Toast.makeText(activity.getApplicationContext(),message,Toast.LENGTH_LONG).show();}
    void begin(){
        if(busy||pending!=null){toast("评价记录正在导出，请稍候。");return;}
        new AlertDialog.Builder(activity).setTitle("导出评价记录")
            .setMessage("导出评价选择、匿名话题编号、时间与推荐版本，供核对效果，包含未完成的评价。不含标题、网址、正文、登录信息或兴趣反馈原文。文件仅写入你选择的位置，不会自动上传。匿名记录仍可能反映阅读活动，请妥善保管。")
            .setNegativeButton("取消",null).setPositiveButton("选择保存位置",(d,w)->prepare()).show();
    }
    private void prepare(){
        if(busy||pending!=null||closed)return;busy=true;
        worker.execute(()->{
            File file=null;
            try{
                File dir=folder();if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Export cache unavailable");
                File[] stale=dir.listFiles();if(stale!=null)for(File old:stale)if(old.getName().matches("trial-observations-[a-f0-9-]{36}\\.db")&&old.lastModified()<System.currentTimeMillis()-86400000L)android.database.sqlite.SQLiteDatabase.deleteDatabase(old);
                file=new File(dir,PREFIX+UUID.randomUUID()+".db");
                TrialObservationSnapshot.create(activity.getDatabasePath(database),file);File ready=file;
                main.post(()->{
                    busy=false;if(closed||activity.isFinishing()||activity.isDestroyed()){android.database.sqlite.SQLiteDatabase.deleteDatabase(ready);return;}
                    pending=ready.getName();Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/vnd.sqlite3").putExtra(Intent.EXTRA_TITLE,"recommendation-observations.db");
                    try{activity.startActivityForResult(intent,REQUEST);}catch(RuntimeException error){discard();toast("未能打开保存位置，请稍后重试。");}
                });
            }catch(Exception error){if(file!=null)android.database.sqlite.SQLiteDatabase.deleteDatabase(file);main.post(()->{busy=false;if(!closed)toast("暂无可导出的有效评价记录，请先完成部分评价后再试。");});}
        });
    }
    void result(int result,Intent data){
        File file=pendingFile();pending=null;
        if(file==null)return;
        Uri destination=data==null?null:data.getData();
        if(result!=Activity.RESULT_OK){android.database.sqlite.SQLiteDatabase.deleteDatabase(file);return;}
        if(destination==null||!"content".equals(destination.getScheme())){android.database.sqlite.SQLiteDatabase.deleteDatabase(file);toast("未取得有效保存位置，请重新导出。");return;}
        busy=true;ContentResolver resolver=activity.getApplicationContext().getContentResolver();
        worker.execute(()->{
            boolean success=false;
            try(InputStream input=new FileInputStream(file);OutputStream output=resolver.openOutputStream(destination,"wt")){
                if(output==null)throw new IOException("Export destination unavailable");byte[] buffer=new byte[32768];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);output.flush();success=true;
            }catch(Exception ignored){success=false;}finally{android.database.sqlite.SQLiteDatabase.deleteDatabase(file);}
            boolean saved=success;main.post(()->{busy=false;toast(saved?"评价记录已导出。":"导出未完成，请重新导出；目标文件可能不完整。");});
        });
    }
    private void discard(){File file=pendingFile();pending=null;if(file!=null)android.database.sqlite.SQLiteDatabase.deleteDatabase(file);}
    @Override public void close(){closed=true;worker.shutdown();}
}
