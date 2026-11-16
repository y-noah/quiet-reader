package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.WebView;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import org.json.*;
import static app.quietreader.Models.*;

/** Capture public article regions only, never cookies, storage or account chrome. */
final class ZhihuImageCaptureProbe {
    private final Instrumentation test;
    ZhihuImageCaptureProbe(Instrumentation test){this.test=test;}
    void run(){
        String database="test-ui-image-capture-"+UUID.randomUUID()+".db";
        Activity[] activity={null};DynamicReader[] reader={null};WebView[] retained={null};
        CountDownLatch done=new CountDownLatch(1);String[] raw={""},error={""};Document[] body={null};
        Bundle result=new Bundle();
        try{
            activity[0]=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Item item=new Item(Source.ZHIHU,"图片重复样本","https://www.zhihu.com/question/2104954238514230174","");
            test.runOnMainSync(()->reader[0]=new DynamicReader(activity[0],item,new Repository.Result<Document>(){
                public void success(Document doc){
                    body[0]=doc;retained[0]=reader[0].takeSource();
                    if(retained[0]==null){error[0]="No retained question source";done.countDown();return;}
                    retained[0].evaluateJavascript("(()=>{const out=document.createElement('div');for(const n of document.querySelectorAll('.QuestionRichText,.AnswerItem')){const c=n.cloneNode(true);c.querySelectorAll('script,style,iframe,form,input,button').forEach(n=>n.remove());out.append(c);}return out.innerHTML})()",value->{raw[0]=value;done.countDown();});
                }
                public void failure(String why){error[0]=why;done.countDown();}
            },true));
            if(!done.await(40,TimeUnit.SECONDS))throw new IOException("Capture timeout");
            if(!error[0].isEmpty())throw new IOException(error[0]);
            Object decoded=new JSONTokener(raw[0]).nextValue();if(!(decoded instanceof String))throw new IOException("No source markup");
            File file=new File(test.getTargetContext().getExternalFilesDir(null),"zhihu-image-body.html");
            try(FileOutputStream out=new FileOutputStream(file)){out.write(((String)decoded).getBytes(StandardCharsets.UTF_8));}
            StringBuilder report=new StringBuilder("PUBLIC_ARTICLE_REGIONS_CAPTURED\n");
            if(body[0]!=null)for(Section section:ReaderHtml.sections(body[0])){
                report.append("SECTION ").append(section.id).append(' ').append(section.label).append('\n');
                for(Block block:section.blocks)if(block.type.equals("image"))report.append("IMAGE ").append(block.value).append('\n');
            }
            result.putString("stream",report.toString());
        }catch(Exception e){error[0]=e.getMessage();result.putString("stream","CAPTURE_FAILED "+error[0]+"\n");}
        finally{
            test.runOnMainSync(()->{if(reader[0]!=null)reader[0].close();if(retained[0]!=null)SourceSurface.release(retained[0]);if(activity[0]!=null)activity[0].finish();});
            test.getTargetContext().deleteDatabase(database);
        }
        test.finish(error[0].isEmpty()?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
