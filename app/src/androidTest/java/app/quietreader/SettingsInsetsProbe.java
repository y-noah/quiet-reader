package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;

/** Inject side-bar insets into the real settings root; no account or network access. */
final class SettingsInsetsProbe {
    private final Instrumentation test;
    private final StringBuilder report=new StringBuilder();
    private int checks,failures;
    SettingsInsetsProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private void insets(Activity live,String page){
        test.runOnMainSync(()->{
            View root=((ViewGroup)live.findViewById(android.R.id.content)).getChildAt(0);
            int margin=Math.round(18*live.getResources().getDisplayMetrics().density);
            // Pixel insets deliberately differ on all sides; repeat and reset catch accumulation/stale padding.
            for(int[] side:new int[][]{{31,23,79,47},{79,23,31,47},{79,23,31,47},{0,23,0,47}}){
                WindowInsets value=new WindowInsets.Builder().setSystemWindowInsets(android.graphics.Insets.of(side[0],side[1],side[2],side[3])).build();
                root.dispatchApplyWindowInsets(value);
                String state=" "+page+" inset="+java.util.Arrays.toString(side);
                check(root.getPaddingLeft()==margin+side[0],"left preserves reading margin"+state);
                check(root.getPaddingRight()==margin+side[2],"right preserves reading margin"+state);
                check(root.getPaddingTop()==side[1]&&root.getPaddingBottom()==side[3],"top and bottom retained"+state);
            }
            root.requestApplyInsets();
        });
        test.waitForIdleSync();
    }
    void run(){
        Activity activity=null;
        Repository repo=null;
        try{
            if(!"app.quietreader.discoveryprobe".equals(test.getTargetContext().getPackageName()))throw new IllegalStateException("Isolated target only");
            activity=test.startActivitySync(new Intent().setClassName(test.getTargetContext(),"app.quietreader.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            insets(activity,"settings");
            Activity settings=activity;test.runOnMainSync(settings::finish);test.waitForIdleSync();
            Models.Item item=new Models.Item(Models.Source.V2EX,"边距测试正文","https://www.v2ex.com/t/"+System.currentTimeMillis(),"");
            Models.Document body=new Models.Document();body.url=item.url;body.title=item.title;body.blocks.add(new Models.Block("text","本地合成正文，无来源请求。"));ArticleVersion.capture(item,body);
            repo=new Repository(test.getTargetContext());repo.cacheArticle(body);
            activity=test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("discovery_item",item.json().toString()).putExtra("direct_reader",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            insets(activity,"reader");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{Activity end=activity;test.runOnMainSync(()->{if(end!=null)end.finish();});test.waitForIdleSync();if(repo!=null)repo.close();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');
        Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
