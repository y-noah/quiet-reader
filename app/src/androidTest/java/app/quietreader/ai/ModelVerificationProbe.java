package app.quietreader.ai;

import android.app.*;
import android.os.Bundle;
import android.system.Os;
import android.system.StructStat;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Actual private files, stat nanoseconds, persisted receipts and atomic replacement. */
public final class ModelVerificationProbe {
    private final Instrumentation test;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    public ModelVerificationProbe(Instrumentation test){this.test=test;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private static void write(File file,byte[] bytes)throws Exception{try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);out.getFD().sync();}}
    private static String hash(byte[] bytes)throws Exception{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static String manifest(String path,byte[] bytes)throws Exception{return new JSONObject().put("models",new JSONArray().put(new JSONObject().put("id","embedding").put("files",new JSONArray().put(new JSONObject().put("path",path).put("bytes",bytes.length).put("sha256",hash(bytes)))))).toString();}
    private static ModelVerification.Result verify(File root,String manifest)throws Exception{return new ModelVerification(root,manifest).verify("embedding",()->{});}
    private static boolean rejects(File root,String manifest)throws Exception{try{verify(root,manifest);return false;}catch(IOException expected){return true;}}
    private static void remove(File file){if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)remove(child);}file.delete();}
    public void run(){
        File parent=new File(test.getTargetContext().getCacheDir(),"test-model-receipt-"+UUID.randomUUID()),root=new File(parent,"models");
        try{
            if(!root.mkdirs())throw new IOException("Fixture directory unavailable");
            File model=new File(root,"model.bin"),receipt=new File(root,".verification-v1.json");
            byte[] bytes="仅用于校验缓存测试，不是模型。".repeat(3000).getBytes(StandardCharsets.UTF_8);String manifest=manifest(model.getName(),bytes);long fixed=1700000000123L;
            write(model,bytes);if(!model.setLastModified(fixed))throw new IOException("Fixture mtime unavailable");
            ModelVerification.Result first=verify(root,manifest);
            check(first.bytesHashed==bytes.length&&first.cacheHits==0,"first verification hashes every actual byte against the supplied trusted digest");
            check(receipt.isFile()&&receipt.length()<4096,"successful verification writes a small private receipt");
            ModelVerification.Result repeated=verify(root,manifest);
            check(repeated.bytesHashed==0&&repeated.cacheHits==1,"a new verifier restores the persisted receipt without rereading an unchanged model");

            StructStat original=Os.stat(model.getPath());byte[] bad=bytes.clone();bad[0]^=1;write(model,bad);if(!model.setLastModified(fixed))throw new IOException("Restore mtime failed");StructStat edited=Os.stat(model.getPath());
            check(original.st_size==edited.st_size&&original.st_mtim.equals(edited.st_mtim)&&!original.st_ctim.equals(edited.st_ctim),"same-length edit restores the exact mtime while real ctime nanoseconds change");
            check(rejects(root,manifest),"same-size corruption with restored mtime cannot reuse the receipt");
            write(model,bytes);model.setLastModified(fixed);ModelVerification.Result repaired=verify(root,manifest);
            check(repaired.bytesHashed==bytes.length&&repaired.cacheHits==0,"restoring valid bytes must hash again before replacing a stale receipt");

            long inode=Os.stat(model.getPath()).st_ino;File replacement=new File(root,"replacement.bin");write(replacement,bytes);replacement.setLastModified(fixed);Os.rename(replacement.getPath(),model.getPath());
            check(Os.stat(model.getPath()).st_ino!=inode,"fixture genuinely replaces the file inode at the same path");
            ModelVerification.Result replaced=verify(root,manifest);
            check(replaced.bytesHashed==bytes.length&&replaced.cacheHits==0,"atomic replacement requires a fresh digest even with identical path, size and mtime");
            check(verify(root,manifest).bytesHashed==0,"a freshly revalidated replacement becomes reusable");

            write(receipt,"invalid receipt".getBytes(StandardCharsets.UTF_8));
            check(verify(root,manifest).bytesHashed==bytes.length,"malformed receipt falls back to hashing rather than trusting or rejecting valid model bytes");
            check(rejects(root,manifest(model.getName(),bad)),"a changed trusted manifest cannot reuse a previous manifest's receipt");
            JSONObject duplicate=new JSONObject(manifest);JSONObject other=new JSONObject(manifest(model.getName(),bad)).getJSONArray("models").getJSONObject(0);other.put("id","reranker");duplicate.getJSONArray("models").put(other);
            verify(root,duplicate.toString());boolean conflictingRejected=false;try{new ModelVerification(root,duplicate.toString()).verify("reranker",()->{});}catch(IOException expected){conflictingRejected=true;}
            check(conflictingRejected,"a conflicting expected digest for the same file in another model never shares a receipt hit");
            receipt.delete();int[] calls={0};boolean concurrentRejected=false;
            try{new ModelVerification(root,manifest).verify("embedding",()->{if(++calls[0]==3)try(RandomAccessFile edit=new RandomAccessFile(model,"rw")){edit.write(bad[0]);model.setLastModified(fixed);}});}catch(IOException expected){concurrentRejected=expected.getMessage().contains("发生变化");}
            check(concurrentRejected&&!receipt.exists(),"a mutation after a hash buffer was read fails the final identity check without publishing a receipt");
            write(model,Arrays.copyOf(bytes,bytes.length-1));check(rejects(root,manifest),"a truncated model remains rejected");
            model.delete();check(rejects(root,manifest),"a missing model remains rejected");
            File outside=new File(parent,"outside.bin");write(outside,bytes);check(rejects(root,manifest("../outside.bin",bytes)),"a correct digest never permits a file outside the private model root");
            boolean canceled=false;try{new ModelVerification(root,manifest).verify("embedding",()->{throw new InterruptedIOException("controlled cancel");});}catch(InterruptedIOException expected){canceled=true;}
            check(canceled,"verification cancellation propagates instead of publishing a receipt");
        }catch(Throwable error){check(false,android.util.Log.getStackTraceString(error));}
        finally{remove(parent);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
