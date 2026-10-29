package app.quietreader.ai;

import android.app.*;
import android.os.Bundle;
import org.json.*;
import java.io.*;
import java.util.*;

/** Controlled manifests exercise production selection; normal assets remain unchanged. */
public final class ModelRollbackProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    public ModelRollbackProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    public void run(){ModelFiles failed=null;
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe"))throw new IllegalStateException("Rollback fixture requires isolated application");
            ModelFiles previous=new ModelFiles(test.getTargetContext());previous.install(null);
            JSONObject manifest=new JSONObject(previous.manifest());manifest.put("probe_revision","intentional-failed-update");
            manifest.getJSONArray("models").getJSONObject(0).getJSONArray("files").getJSONObject(0).put("sha256",String.join("",Collections.nCopies(64,"0")));
            failed=new ModelFiles(test.getTargetContext(),manifest.toString());
            ModelFiles selected=ModelFiles.select(failed,Collections.singletonList(previous));
            check(selected.revision().equals(previous.revision())&&!selected.revision().equals(failed.revision()),"failed update selects the fully verified predecessor with its original revision");
            check(!new File(failed.directory(),"ready").exists(),"failed checksum never marks the new revision ready");
            long marker=new File(failed.directory(),"installing").lastModified();
            ModelFiles again=ModelFiles.select(failed,Collections.singletonList(previous));
            check(again.revision().equals(previous.revision())&&new File(failed.directory(),"installing").lastModified()==marker,"fallback cooldown avoids copying the failed update every ranking window");
            boolean rejected=false;try{ModelFiles.forRevision(test.getTargetContext(),failed.revision());}catch(IOException expected){rejected=true;}
            check(rejected,"worker revision selection rejects manifests absent from the APK allowlist");
            check(ModelFiles.forRevision(test.getTargetContext(),selected.revision()).directory().equals(previous.directory()),"worker resolves selected approved revision to the matching model directory");
            Thread.currentThread().interrupt();boolean cancelled=false;try{ModelFiles.select(failed,Collections.singletonList(previous));}catch(InterruptedIOException expected){cancelled=true;}finally{Thread.interrupted();}
            check(cancelled,"cancellation cannot trigger a fallback installation or hash scan");
            boolean noFallback=false;try{ModelFiles.select(failed,Collections.emptyList());}catch(IOException expected){noFallback=true;}
            check(noFallback,"failed update without a verified predecessor remains unavailable instead of claiming success");
            test.getTargetContext().getSharedPreferences("model-fallback",0).edit().remove(failed.revision()).commit();
            ModelFiles recovered=ModelFiles.afterExecutionFailure(failed,Collections.singletonList(previous),failed.revision());
            check(recovered!=null&&recovered.revision().equals(previous.revision()),"execution failure selects a fully verified predecessor without reinstalling primary");
            check(test.getTargetContext().getSharedPreferences("model-fallback",0).getLong(failed.revision(),0)>System.currentTimeMillis(),"execution failure holds failed primary for subsequent foreground windows");
            check(ModelFiles.afterExecutionFailure(failed,Collections.singletonList(previous),previous.revision())==null,"a failing predecessor cannot cycle back to primary");
            check(ModelFiles.afterExecutionFailure(previous,Collections.singletonList(previous),previous.revision())==null,"same revision cannot be its own execution fallback");
            check(ModelFiles.afterExecutionFailure(failed,Collections.emptyList(),failed.revision())==null,"execution failure without predecessor returns no replacement");
            File vocab=new File(previous.directory(),"embedding/upstream/vocab.txt");
            try(RandomAccessFile bytes=new RandomAccessFile(vocab,"rw")){
                int original=bytes.read();try{
                    bytes.seek(0);bytes.write(original^1);
                    check(previous.installed(),"same-length corruption still passes cheap installation check");
                    check(ModelFiles.afterExecutionFailure(failed,Collections.singletonList(previous),failed.revision())==null,"execution fallback hashes artifacts and refuses same-length corrupt predecessor");
                }finally{bytes.seek(0);bytes.write(original);bytes.getFD().sync();}
            }
            Thread.currentThread().interrupt();boolean executionCancelled=false;
            try{ModelFiles.afterExecutionFailure(failed,Collections.singletonList(previous),failed.revision());}catch(InterruptedIOException expected){executionCancelled=true;}finally{Thread.interrupted();}
            check(executionCancelled,"cancelled execution fallback exits before scanning predecessor");
        }catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}
        finally{
            if(failed!=null){test.getTargetContext().getSharedPreferences("model-fallback",0).edit().remove(failed.revision()).commit();
                // Failed first-file checksum leaves only this fixture's marker and empty directory.
                new File(failed.directory(),"embedding").delete();new File(failed.directory(),"installing").delete();failed.directory().delete();
            }
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
