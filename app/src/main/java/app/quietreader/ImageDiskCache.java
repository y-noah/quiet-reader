package app.quietreader;

import app.quietreader.discovery.HotCandidate;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.util.zip.CRC32;

/** Private derivative images. Worker-only callers; atomic records, no raw cookies. */
final class ImageDiskCache {
    static final long MAX_BYTES=128L*1024*1024,MAX_AGE=7L*24*60*60*1000;
    static final int MAX_ENTRY=8*1024*1024,MAX_META=64*1024;
    private static final int MAGIC=0x51524931;
    private static final Object IO=new Object();
    private final File root;private final long budget;private final int maxFiles;private final LongSupplier free;
    static final class Hit {final byte[] bytes;final Map<String,String> sessions;Hit(byte[] bytes,Map<String,String> sessions){this.bytes=bytes;this.sessions=sessions;}}
    static final class PayloadLimitException extends IOException {PayloadLimitException(){super("Image response too large");}}
    ImageDiskCache(File root){this(root,MAX_BYTES,1000,root::getUsableSpace);}
    ImageDiskCache(File root,long budget,int maxFiles,LongSupplier free){this.root=root;this.budget=budget;this.maxFiles=maxFiles;this.free=free;}
    private static boolean record(File file){return file.getName().matches("[a-f0-9]{64}-[a-f0-9]{64}\\.img");}
    private File file(String key,String url){return new File(root,HotCandidate.hash(url)+"-"+HotCandidate.hash(key)+".img");}
    Hit read(String key,String url,long now){
        try{return read(key,url,now,MAX_ENTRY);}catch(PayloadLimitException impossible){return null;}
    }
    Hit read(String key,String url,long now,int limit)throws PayloadLimitException{
        synchronized(IO){File file=file(key,url);try{
            if(Files.isSymbolicLink(root.toPath())||!file.isFile()||Files.isSymbolicLink(file.toPath()))return null;
            if(file.length()>MAX_ENTRY+MAX_META+32L)throw new IOException("Image record too large");
            try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))){
                if(in.readInt()!=MAGIC)throw new IOException("Unknown image record");int metaSize=in.readInt(),size=in.readInt();long checksum=in.readLong();
                if(metaSize<1||metaSize>MAX_META||size<12||size>MAX_ENTRY||file.length()!=20L+metaSize+size)throw new IOException("Invalid image record size");
                // A speculative caller may have a smaller budget than the reader.
                // Reject before allocating its payload; retain the reader's record.
                if(size>limit)throw new PayloadLimitException();
                byte[] metadata=new byte[metaSize];in.readFully(metadata);JSONObject meta=new JSONObject(new String(metadata,StandardCharsets.UTF_8));long age=now-meta.getLong("at");
                if(age<0||age>MAX_AGE||!HotCandidate.hash(key).equals(meta.getString("identity")))throw new IOException("Expired or mismatched image record");
                JSONObject source=meta.getJSONObject("sessions");if(source.length()<1||source.length()>16)throw new IOException("Invalid image sessions");Map<String,String> sessions=new LinkedHashMap<>();Iterator<String> names=source.keys();while(names.hasNext()){String name=names.next(),hash=source.getString(name);if(!hash.matches("[a-f0-9]{64}"))throw new IOException("Invalid session hash");sessions.put(name,hash);}
                byte[] bytes=new byte[size];in.readFully(bytes);CRC32 crc=new CRC32();crc.update(bytes);if(crc.getValue()!=checksum||ImageType.detect(bytes)==null)throw new IOException("Corrupt image bytes");
                file.setLastModified(now);return new Hit(bytes,sessions);
            }
        }catch(PayloadLimitException overBudget){throw overBudget;}catch(Exception invalid){file.delete();return null;}}
    }
    void write(String key,String url,Map<String,String> sessions,byte[] bytes,long now,BooleanSupplier valid){
        if(bytes.length>MAX_ENTRY||ImageType.detect(bytes)==null)return;
        synchronized(IO){File partial=null;try{
            if(!valid.getAsBoolean()||sessions.isEmpty()||sessions.size()>16)return;
            JSONObject marks=new JSONObject();for(Map.Entry<String,String> entry:sessions.entrySet())marks.put(entry.getKey(),entry.getValue());
            byte[] metadata=new JSONObject().put("identity",HotCandidate.hash(key)).put("at",now).put("sessions",marks).toString().getBytes(StandardCharsets.UTF_8);
            long incoming=20L+metadata.length+bytes.length;if(metadata.length>MAX_META||incoming>budget)return;
            if(!root.isDirectory()&&!root.mkdirs())return;
            if(Files.isSymbolicLink(root.toPath())||free.getAsLong()<DataStorageBudget.MIN_FREE+incoming)return;
            File target=file(key,url);File[] list=root.listFiles();if(list==null)return;long used=0;int count=0;boolean replacing=false;List<File> evictable=new ArrayList<>();
            for(File file:list){if(!file.isFile()||Files.isSymbolicLink(file.toPath()))return;
                if(file.getName().startsWith("image-")&&file.getName().endsWith(".partial")){if(now-file.lastModified()<=60*60_000||!file.delete())used+=file.length();continue;}
                if(!record(file))return;
                if(now-file.lastModified()>MAX_AGE&&file.delete())continue;
                used+=file.length();count++;if(file.equals(target))replacing=true;else evictable.add(file);
            }
            // A replacement needs temporary bytes, but no additional final record slot.
            int allowedExisting=maxFiles-(replacing?0:1);
            if(used>budget-incoming||count>allowedExisting){
                evictable.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
                for(File file:evictable){if(used<=budget-incoming&&count<=allowedExisting)break;long size=file.length();if(file.delete()){used-=size;count--;}}
            }
            if(used>budget-incoming||count>allowedExisting)return;
            partial=File.createTempFile("image-",".partial",root);CRC32 crc=new CRC32();crc.update(bytes);
            try(FileOutputStream stream=new FileOutputStream(partial);DataOutputStream out=new DataOutputStream(new BufferedOutputStream(stream))){out.writeInt(MAGIC);out.writeInt(metadata.length);out.writeInt(bytes.length);out.writeLong(crc.getValue());out.write(metadata);out.write(bytes);out.flush();stream.getFD().sync();}
            if(!valid.getAsBoolean())return;
            try{Files.move(partial.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException fallback){Files.move(partial.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}
            target.setLastModified(now);
        }catch(Exception ignored){}finally{if(partial!=null)partial.delete();}}
    }
    void removeUrl(String url){synchronized(IO){if(Files.isSymbolicLink(root.toPath()))return;String prefix=HotCandidate.hash(url)+"-";File[] files=root.listFiles();if(files!=null)for(File file:files)if(record(file)&&file.getName().startsWith(prefix))file.delete();}}
    /** Read only small ownership metadata, never image payloads during a batch swap. */
    boolean retainOwners(Set<String> owners,BooleanSupplier current){synchronized(IO){
        if(!current.getAsBoolean())return true;if(Files.isSymbolicLink(root.toPath()))return false;
        File[] files=root.listFiles();if(files==null)return !root.exists();boolean ok=true;
        for(File file:files){
            if(!current.getAsBoolean())return ok;
            if(!record(file)||!file.isFile()||Files.isSymbolicLink(file.toPath()))continue;
            boolean keep=false;
            try(DataInputStream in=new DataInputStream(new BufferedInputStream(new FileInputStream(file)))){
                if(in.readInt()!=MAGIC)throw new IOException("Unknown record");int size=in.readInt();in.readInt();in.readLong();
                if(size<1||size>MAX_META)throw new IOException("Invalid metadata");byte[] bytes=new byte[size];in.readFully(bytes);
                JSONObject sessions=new JSONObject(new String(bytes,StandardCharsets.UTF_8)).getJSONObject("sessions");
                for(String owner:owners)if(sessions.has(owner)){keep=true;break;}
            }catch(Exception invalid){/* Unreadable derivatives are disposable. */}
            if(!current.getAsBoolean())return ok;
            if(!keep&&!file.delete())ok=false;
        }
        return ok;
    }}
    boolean clearFiles(){synchronized(IO){if(Files.isSymbolicLink(root.toPath()))return false;File[] files=root.listFiles();if(files==null)return !root.exists();boolean ok=true;for(File file:files){if(file.isFile()&&(record(file)||file.getName().startsWith("image-")&&file.getName().endsWith(".partial"))){if(!file.delete())ok=false;}else ok=false;}return ok;}}
}
