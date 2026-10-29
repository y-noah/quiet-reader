package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import app.quietreader.ai.ModelFiles;
import org.json.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Real bundled bytes, isolated files directory; never damages installed user models. */
final class ModelRepairProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    ModelRepairProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private static String hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(Locale.ROOT,"%02x",b));return out.toString();}
    private static void clean(File file,File root)throws IOException{
        if(!file.getCanonicalPath().startsWith(root.getCanonicalPath()+File.separator)&&!file.getCanonicalFile().equals(root.getCanonicalFile()))throw new IOException("Outside test directory");
        File[] children=file.listFiles();if(children!=null)for(File child:children)clean(child,root);if(file.exists()&&!file.delete())throw new IOException("Test cleanup failed");
    }
    void run(){File isolated=new File(test.getTargetContext().getCacheDir(),"test-model-repair-"+UUID.randomUUID());
        Context context=new ContextWrapper(test.getTargetContext()){@Override public Context getApplicationContext(){return this;}@Override public File getFilesDir(){return isolated;}};
        try{
            ModelFiles files=new ModelFiles(context);interruptFirstInstall(files);files.install(null);check(files.installed(),"bundled models install in isolated directory");
            List<JSONObject> entries=new ArrayList<>();JSONArray models=new JSONObject(files.manifest()).getJSONArray("models");for(int i=0;i<models.length();i++){JSONArray list=models.getJSONObject(i).getJSONArray("files");for(int j=0;j<list.length();j++)entries.add(list.getJSONObject(j));}
            entries.sort(Comparator.comparingLong(e->e.optLong("bytes")));JSONObject small=entries.get(0),large=entries.get(entries.size()-1);
            File damaged=ModelFiles.child(files.directory(),small.getString("path")),healthy=ModelFiles.child(files.directory(),large.getString("path"));
            long expectedTime=1_600_000_000_000L;if(!healthy.setLastModified(expectedTime))throw new IOException("Cannot set test timestamp");
            try(RandomAccessFile f=new RandomAccessFile(damaged,"rw")){int first=f.read();f.seek(0);f.write(first^1);}
            check(!hash(damaged).equals(small.getString("sha256")),"fixture contains same-length hash corruption");
            File parent=files.directory().getParentFile();
            File oldest=oldRevision(parent,'a',"ready",1_500_000_000_000L),fallback=oldRevision(parent,'b',"ready",1_600_000_000_000L),partial=oldRevision(parent,'d',"installing",1_650_000_000_000L);
            files.repair();check(hash(damaged).equals(small.getString("sha256"))&&files.installed(),"explicit repair restores same-length corruption from trusted bundled bytes");
            check(!oldest.exists()&&!partial.exists()&&fallback.isDirectory(),"successful verified repair removes obsolete and partial revisions but retains one fallback");
            check(healthy.lastModified()==expectedTime,"healthy large model is verified without recopying");
            if(!damaged.delete())throw new IOException("Cannot remove fixture");check(!files.installed(),"missing file invalidates ready marker fast path");
            files.install(null);check(files.installed()&&hash(damaged).equals(small.getString("sha256")),"missing file resumes installation while retaining healthy files");
            boolean cancelled=false;Thread.currentThread().interrupt();try{files.repair();}catch(InterruptedIOException expected){cancelled=true;}finally{Thread.interrupted();}
            check(cancelled&&files.installed(),"cancelled repair preserves previously valid installation");
        }catch(Throwable ex){failures++;report.append("ERROR ").append(ex).append('\n');}
        finally{try{clean(isolated,isolated);}catch(Exception ex){failures++;report.append("ERROR cleanup ").append(ex).append('\n');}}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void interruptFirstInstall(ModelFiles files)throws Exception{
        File partial=new File(files.directory(),"reranker/model_int8.onnx.partial");
        File completed=new File(files.directory(),"embedding/model_quantized.onnx_data");
        java.util.concurrent.atomic.AtomicReference<Throwable> failure=new java.util.concurrent.atomic.AtomicReference<>();
        Thread installer=new Thread(()->{try{files.install(null);}catch(Throwable ex){failure.set(ex);}},"test-first-model-install");
        installer.start();boolean observed=false;long completedTime=0;
        try{
            long deadline=android.os.SystemClock.elapsedRealtime()+20_000;
            while(installer.isAlive()&&android.os.SystemClock.elapsedRealtime()<deadline){
                if(partial.length()>=1024*1024){observed=true;completedTime=completed.lastModified();break;}
                Thread.sleep(1);
            }
            check(observed&&completed.isFile(),"interrupt fixture observes real partial copy after embedding committed");
        }finally{installer.interrupt();installer.join(5000);}
        check(!installer.isAlive(),"active model copy stops within cancellation deadline");
        if(installer.isAlive())throw new IOException("Installer did not stop; cannot safely continue fixture");
        check(failure.get() instanceof InterruptedIOException,"active installation reports interruption");
        check(!files.installed()&&!new File(files.directory(),"ready").exists(),"cancelled first installation never advertises ready");
        check(!partial.exists()&&new File(files.directory(),"installing").isFile(),"cancelled copy removes partial bytes and retains resumable marker");
        if(!observed)throw new IOException("Did not observe copy in progress");
        files.install(null);
        check(files.installed()&&!new File(files.directory(),"installing").exists(),"resumed installation commits complete model revision");
        check(completed.lastModified()==completedTime,"resume verifies and reuses already committed embedding bytes");
    }
    private static File oldRevision(File root,char c,String marker,long time)throws Exception{
        String id=String.valueOf(c).repeat(64);File dir=new File(root,id);if(!dir.mkdir())throw new IOException("Cannot create old revision fixture");
        File flag=new File(dir,marker);try(FileOutputStream out=new FileOutputStream(flag)){out.write(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        if(!flag.setLastModified(time))throw new IOException("Cannot date old revision");return dir;
    }
}

