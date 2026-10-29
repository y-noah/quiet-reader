package app.quietreader;

import android.app.*;
import android.os.*;
import app.quietreader.ai.*;
import java.io.*;
import java.util.concurrent.*;

/** Mutates only the dedicated discoveryprobe model copy, then runs real IPC/native inference. */
final class ModelRecoveryProbe {
    private final Instrumentation test;private RankingClient client;
    ModelRecoveryProbe(Instrumentation test){this.test=test;}
    void run(){StringBuilder report=new StringBuilder();int checks=0,failures=0;File vocab=null;byte original=0;boolean changed=false;
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe"))throw new IllegalStateException("Recovery fault injection requires isolated package");
            ModelFiles files=new ModelFiles(test.getTargetContext());files.install(null);
            vocab=ModelFiles.child(files.directory(),"embedding/upstream/vocab.txt");
            try(RandomAccessFile file=new RandomAccessFile(vocab,"rw")){original=file.readByte();file.seek(0);file.writeByte(original^1);file.getFD().sync();changed=true;}
            checks++;if(files.installed())report.append("PASS same-length corruption passes installation size check\n");else{failures++;report.append("FAIL size fixture\n");}
            CountDownLatch done=new CountDownLatch(1);boolean[] vector={false},recovered={false};String[] error={""};
            test.runOnMainSync(()->{
                client=new RankingClient(test.getTargetContext(),new RankingClient.Listener(){
                    public void onResult(int kind,Bundle data){
                        if(kind==RankingService.ITEM){float[] value=data.getFloatArray("vector");double norm=0;if(value!=null)for(float x:value)norm+=(double)x*x;vector[0]=value!=null&&value.length==512&&Math.abs(norm-1)<0.001;}
                        if(kind==RankingService.DONE){recovered[0]=data.getBoolean("recovered")&&data.getInt("completed")==1;done.countDown();}
                        if(kind==RankingService.ERROR){error[0]=data.getString("code");done.countDown();}
                    }
                    public void onClosed(){done.countDown();}
                });
                Bundle request=new Bundle();request.putString("kind","embedding");request.putStringArray("ids",new String[]{"recovery"});request.putStringArray("texts",new String[]{"模型资源损坏后应自动修复并恢复本地推荐"});request.putLong("budgetMs",2000);client.submit(request);
            });
            boolean complete=done.await(45,TimeUnit.SECONDS);checks++;if(complete&&vector[0]&&recovered[0])report.append("PASS checksum failure automatically repairs and retries real embedding inference\n");else{failures++;report.append("FAIL automatic recovery complete=").append(complete).append(" vector=").append(vector[0]).append(" recovered=").append(recovered[0]).append(" code=").append(error[0]).append('\n');}
            checks++;try(RandomAccessFile file=new RandomAccessFile(vocab,"r")){if(file.readByte()==original)report.append("PASS automatic recovery restores corrupted tokenizer byte\n");else{failures++;report.append("FAIL tokenizer restoration\n");}}
        }catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}
        finally{
            test.runOnMainSync(()->{if(client!=null)client.close();});
            if(changed&&vocab!=null)try(RandomAccessFile file=new RandomAccessFile(vocab,"rw")){file.seek(0);file.writeByte(original);file.getFD().sync();}catch(IOException e){failures++;report.append("ERROR fixture restoration\n");}
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle output=new Bundle();output.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,output);
    }
}
