package app.quietreader.ai;

import android.os.Build;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Private installation receipt. Changed or unobservable identities require full hashing. */
final class ModelVerification {
    interface Check {void run()throws IOException;}
    static final class Result {
        final long bytesHashed;final int cacheHits;
        Result(long bytes,int hits){bytesHashed=bytes;cacheHits=hits;}
    }
    private final File root;
    private final String manifest;
    ModelVerification(File root,String manifest){this.root=root;this.manifest=manifest;}
    Result verify(String kind,Check check)throws Exception{
        check.run();
        String revision=hex(MessageDigest.getInstance("SHA-256").digest(manifest.getBytes(StandardCharsets.UTF_8)));
        AtomicFile receipt=new AtomicFile(new File(root,".verification-v1.json"));
        JSONObject records=read(receipt,revision),updated=new JSONObject(records.toString());
        long bytes=0;int hits=0;boolean found=false,changed=false;
        JSONArray models=new JSONObject(manifest).getJSONArray("models");
        for(int n=0;n<models.length();n++){
            JSONObject model=models.getJSONObject(n);if(!kind.equals(model.getString("id")))continue;found=true;
            JSONArray files=model.getJSONArray("files");
            for(int j=0;j<files.length();j++){
                check.run();JSONObject entry=files.getJSONObject(j);String path=entry.getString("path");
                File file=new File(root,path).getCanonicalFile();
                if(!file.getPath().startsWith(root.getCanonicalPath()+File.separator)||!file.isFile()||file.length()!=entry.getLong("bytes"))throw new IOException("模型文件缺失或大小不符");
                String before=identity(file),expected=entry.getString("sha256");
                if(before!=null&&(expected+":"+before).equals(records.optString(path,""))){hits++;continue;}
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(InputStream input=new FileInputStream(file)){
                    byte[] buffer=new byte[64*1024];int count;
                    while((count=input.read(buffer))!=-1){check.run();digest.update(buffer,0,count);bytes+=count;}
                }
                if(!hex(digest.digest()).equals(expected))throw new IOException("模型校验失败");
                String after=identity(file);
                if(before!=null&&!before.equals(after))throw new IOException("模型文件在校验时发生变化");
                if(before!=null){updated.put(path,expected+":"+before);changed=true;}
            }
        }
        if(!found)throw new IOException("模型清单缺失");
        check.run();
        // An optional receipt failure must never turn verified model data into
        // a model failure. A later process simply hashes those files again.
        if(changed)write(receipt,revision,updated);
        return new Result(bytes,hits);
    }
    private static JSONObject read(AtomicFile file,String revision){
        try(InputStream input=file.openRead();ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int count;
            while((count=input.read(buffer))!=-1){if(output.size()+count>64*1024)return new JSONObject();output.write(buffer,0,count);}
            JSONObject value=new JSONObject(output.toString("UTF-8"));
            if(value.optInt("schema")!=1||!revision.equals(value.optString("manifest")))return new JSONObject();
            return value.getJSONObject("files");
        }catch(Exception missingOrInvalid){return new JSONObject();}
    }
    private static void write(AtomicFile file,String revision,JSONObject records){
        FileOutputStream output=null;
        try{
            byte[] bytes=new JSONObject().put("schema",1).put("manifest",revision).put("files",records).toString().getBytes(StandardCharsets.UTF_8);
            if(bytes.length>64*1024)return;
            output=file.startWrite();output.write(bytes);file.finishWrite(output);
        }catch(Exception unavailable){if(output!=null)file.failWrite(output);}
    }
    private static String identity(File file){
        if(Build.VERSION.SDK_INT<27)return null;
        try{return Api27.identity(file);}catch(Exception unavailable){return null;}
    }
    @android.annotation.TargetApi(27)
    private static final class Api27 {
        static String identity(File file)throws Exception{
            StructStat value=Os.lstat(file.getPath());
            // Seconds-only file systems cannot safely identify rapid same-size
            // edits. Do not cache a zero-resolution ctime observation.
            if(!OsConstants.S_ISREG(value.st_mode)||value.st_ctim==null||value.st_mtim==null||value.st_ctim.tv_nsec==0)return null;
            return value.st_dev+":"+value.st_ino+":"+value.st_mode+":"+value.st_uid+":"+value.st_size+":"+value.st_mtim.tv_sec+":"+value.st_mtim.tv_nsec+":"+value.st_ctim.tv_sec+":"+value.st_ctim.tv_nsec;
        }
    }
    private static String hex(byte[] bytes){
        char[] chars=new char[bytes.length*2];char[] digits="0123456789abcdef".toCharArray();
        for(int n=0;n<bytes.length;n++){int value=bytes[n]&255;chars[n*2]=digits[value>>>4];chars[n*2+1]=digits[value&15];}
        return new String(chars);
    }
}
