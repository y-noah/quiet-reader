package app.quietreader.ai;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Private, versioned model installation. Only the APK's manifest is trusted.
 * Never reads cookies, searches external storage, or downloads anything.
 */
public final class ModelFiles {
    private final Context context;
    private final String manifest,revision;
    private final List<JSONObject> entries=new ArrayList<>();
    public ModelFiles(Context context)throws Exception{
        this(context,asset(context,"local-ai-manifest.json"));
    }
    ModelFiles(Context context,String trustedManifest)throws Exception{
        this.context=context.getApplicationContext();
        manifest=trustedManifest;
        revision=hex(MessageDigest.getInstance("SHA-256").digest(manifest.getBytes(StandardCharsets.UTF_8)));
        JSONArray models=new JSONObject(manifest).getJSONArray("models");
        for(int i=0;i<models.length();i++){
            JSONArray files=models.getJSONObject(i).getJSONArray("files");
            for(int j=0;j<files.length();j++)entries.add(files.getJSONObject(j));
        }
    }
    private static String asset(Context context,String path)throws IOException{try(InputStream in=context.getAssets().open(path)){return read(in);}}
    private static List<ModelFiles> compatible(Context context)throws Exception{
        JSONArray values=new JSONObject(asset(context,"local-ai-compatible-models.json")).getJSONArray("manifests");
        if(values.length()>1)throw new IOException("Only one approved predecessor is supported");
        JSONObject current=new JSONObject(asset(context,"local-ai-manifest.json"));
        List<ModelFiles> result=new ArrayList<>();for(int i=0;i<values.length();i++){
            ModelFiles old=new ModelFiles(context,values.getString(i));JSONObject prior=new JSONObject(old.manifest);
            boolean matches=true;for(String key:Arrays.asList("schema","runtime","tokenizer_runtime","tokenizer_source_revision"))matches&=current.has(key)&&current.get(key).equals(prior.opt(key));
            if(matches)result.add(old);
        }return result;
    }
    /** Only APK-approved manifests can be requested across the process boundary. */
    public static ModelFiles forRevision(Context context,String requested)throws Exception{
        ModelFiles primary=new ModelFiles(context);if(requested.isEmpty()||primary.revision.equals(requested))return primary;
        for(ModelFiles old:compatible(context))if(old.revision.equals(requested))return old;
        throw new IOException("Model revision is not approved by this APK");
    }
    public static ModelFiles prepare(Context context)throws Exception{return select(new ModelFiles(context),compatible(context));}
    /** A worker failure may use only an installed, APK-approved predecessor.
     * Never retry the failed predecessor or treat cancellation as model failure. */
    public static ModelFiles afterExecutionFailure(Context context,String failedRevision)throws Exception{
        return afterExecutionFailure(new ModelFiles(context),compatible(context),failedRevision);
    }
    static ModelFiles afterExecutionFailure(ModelFiles primary,List<ModelFiles> predecessors,String failedRevision)throws Exception{
        interrupted();if(!primary.revision.equals(failedRevision))return null;
        List<ModelFiles> eligible=new ArrayList<>();for(ModelFiles old:predecessors)if(!old.revision.equals(failedRevision))eligible.add(old);
        ModelFiles old=verifiedPredecessor(eligible);interrupted();
        if(old!=null)primary.context.getSharedPreferences("model-fallback",0).edit().putLong(primary.revision,System.currentTimeMillis()+10*60_000).apply();
        return old;
    }
    static ModelFiles select(ModelFiles primary,List<ModelFiles> predecessors)throws Exception{
        interrupted();
        android.content.SharedPreferences state=primary.context.getSharedPreferences("model-fallback",0);
        long now=System.currentTimeMillis(),until=state.getLong(primary.revision,0);
        if(until>now&&until-now<=10*60_000){ModelFiles old=verifiedPredecessor(predecessors);if(old!=null)return old;}
        try{primary.install(null);state.edit().remove(primary.revision).apply();return primary;}
        catch(InterruptedIOException cancelled){throw cancelled;}
        catch(Exception failure){
            interrupted();ModelFiles old=verifiedPredecessor(predecessors);if(old==null)throw failure;
            // Avoid repeating a failed APK copy on every foreground ranking window.
            state.edit().putLong(primary.revision,now+10*60_000).apply();return old;
        }
    }
    private static ModelFiles verifiedPredecessor(List<ModelFiles> predecessors)throws Exception{
        for(ModelFiles old:predecessors){interrupted();if(!old.installed())continue;boolean verified=true;
            for(JSONObject entry:old.entries)if(!valid(child(old.directory(),entry.getString("path")),entry)){verified=false;break;}
            if(verified)return old;
        }return null;
    }
    public String manifest(){return manifest;}
    public String revision(){return revision;}
    public File directory(){return new File(context.getFilesDir(),"local-models/"+revision);}
    public boolean installed(){
        if(!new File(directory(),"ready").isFile())return false;
        try{for(JSONObject entry:entries){File file=child(directory(),entry.getString("path"));if(!file.isFile()||file.length()!=entry.getLong("bytes"))return false;}return true;}catch(Exception ex){return false;}
    }
    /** Explicit user repair rehashes every file; healthy files are not recopied. */
    public File repair()throws Exception{File result=install(null,true);context.getSharedPreferences("model-fallback",0).edit().remove(revision).apply();return result;}
    /** Caller must run on a worker. Null source selects bundled assets. */
    public File install(File source)throws Exception{return install(source,false);}
    private File install(File source,boolean repair)throws Exception{
        synchronized(ModelFiles.class){
            interrupted();if(!repair&&installed()){prune();return directory();}
            File root=directory();if(!root.isDirectory()&&!root.mkdirs())throw new IOException("无法建立模型目录");
            File marker=new File(root,"ready");if(marker.exists()&&!marker.delete())throw new IOException("无法更新模型状态");
            try(FileOutputStream out=new FileOutputStream(new File(root,"installing"))){out.write(revision.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
            for(JSONObject e:entries){
                interrupted();String path=e.getString("path");File target=child(root,path);
                if(valid(target,e))continue;
                if(root.getUsableSpace()<e.getLong("bytes")+32_000_000L)throw new IOException("模型安装空间不足");
                if(!target.getParentFile().isDirectory()&&!target.getParentFile().mkdirs())throw new IOException("无法建立模型子目录");
                File partial=new File(target.getPath()+".partial");
                MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0;
                try(InputStream in=source==null?context.getAssets().open("models/"+path):new FileInputStream(child(source,path));FileOutputStream out=new FileOutputStream(partial)){
                    byte[] buf=new byte[64*1024];int n;
                    while((n=in.read(buf))!=-1){interrupted();count+=n;if(count>e.getLong("bytes"))throw new IOException("模型文件大小不符");digest.update(buf,0,n);out.write(buf,0,n);}
                    out.getFD().sync();
                }catch(Exception ex){partial.delete();throw ex;}
                if(count!=e.getLong("bytes")||!hex(digest.digest()).equals(e.getString("sha256"))){partial.delete();throw new IOException("模型文件校验失败");}
                if(!partial.renameTo(target))throw new IOException("模型文件提交失败");
            }
            // The marker is committed only after every artifact has been verified.
            try(FileOutputStream out=new FileOutputStream(new File(root,"ready"))){out.write(revision.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
            new File(root,"installing").delete();prune();
            return root;
        }
    }
    private void prune()throws InterruptedIOException{
        try{Set<String> approved=new HashSet<>();for(ModelFiles old:compatible(context))approved.add(old.revision);ModelRevisions.prune(directory().getParentFile(),revision,approved);}
        catch(InterruptedIOException cancelled){throw cancelled;}
        catch(Exception ignored){/* Cleanup failure must not invalidate a verified installation. */}
    }
    private static boolean valid(File file,JSONObject entry)throws Exception{
        if(!file.isFile()||file.length()!=entry.getLong("bytes"))return false;
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] bytes=new byte[64*1024];int count;while((count=in.read(bytes))!=-1){interrupted();digest.update(bytes,0,count);}}
        return hex(digest.digest()).equals(entry.getString("sha256"));
    }
    public static File child(File root,String path)throws IOException{
        File file=new File(root,path).getCanonicalFile();
        if(!file.getPath().startsWith(root.getCanonicalPath()+File.separator))throw new IOException("Invalid model path");
        return file;
    }
    private static void interrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("模型安装已取消");}
    private static String read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
}
