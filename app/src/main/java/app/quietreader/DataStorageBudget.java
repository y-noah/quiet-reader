package app.quietreader;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Worker-only, metadata-only conservative data accounting; never deletes anything. */
final class DataStorageBudget {
    static final long MIN_FREE=64L*1024*1024;
    static final class FreeSpace {
        final long available;final boolean verified;
        FreeSpace(long available,boolean verified){this.available=available;this.verified=verified;}
        boolean allowsPrefetch(){return verified&&available>=MIN_FREE;}
    }
    /** One filesystem-space query; prefetch must not traverse WebView/model files. */
    static FreeSpace freeSpace(File data){
        try{
            File root=data.getCanonicalFile();if(!root.isDirectory())return new FreeSpace(0,false);
            long available=root.getUsableSpace();return new FreeSpace(available,available>0);
        }catch(IOException|SecurityException unavailable){return new FreeSpace(0,false);}
    }
    static final class Snapshot {
        final long bytes,free,bodyBytes,databaseBytes,webBytes,otherBytes;final boolean complete;
        Snapshot(long bytes,long free,boolean complete){this(bytes,free,complete,new long[]{0,0,0,bytes});}
        Snapshot(long bytes,long free,boolean complete,long[] groups){this.bytes=bytes;this.free=free;this.complete=complete;bodyBytes=groups[0];databaseBytes=groups[1];webBytes=groups[2];otherBytes=groups[3];}
        String description(){return String.format(Locale.CHINA,"本机数据%s：%.1f MiB\n正文及图片缓存：%.1f MiB\n数据库（含收藏和反馈）：%.1f MiB\n网页数据（含登录）：%.1f MiB\n其他数据（含模型及编译缓存）：%.1f MiB\n不含安装包；以上不是全部可清理缓存。\n%s",complete?"":"（未统计完整）",bytes/1048576.0,bodyBytes/1048576.0,databaseBytes/1048576.0,webBytes/1048576.0,otherBytes/1048576.0,free<MIN_FREE?"可用空间偏低或暂无法确认，暂缓主动预读":"可继续主动预读");}
    }
    static Snapshot measure(File data){return measure(data,20_000);}
    static Snapshot measure(File data,int limit){
        long bytes=0;long[] groups=new long[4];boolean complete=true;int visited=0;
        long deadline=System.nanoTime()+2_000_000_000L;
        try{
            File root=data.getCanonicalFile();Deque<File> pending=new ArrayDeque<>();pending.push(root);
            while(!pending.isEmpty()){
                if(Thread.currentThread().isInterrupted()||++visited>limit||System.nanoTime()>deadline){complete=false;break;}
                File file=pending.pop();
                if(Files.isSymbolicLink(file.toPath())){complete=false;continue;}
                String relative=root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar,'/');
                if(!file.getCanonicalFile().equals(file.getAbsoluteFile())){complete=false;continue;}
                if(file.isDirectory()){
                    File[] children=file.listFiles();if(children==null){complete=false;continue;}
                    for(File child:children)pending.push(child);
                }else if(file.isFile()){long size=file.length();bytes+=size;int group=(relative.startsWith("cache/article-bodies-v1/")||relative.startsWith("cache/reader-images-v1/"))?0:relative.startsWith("databases/")?1:relative.startsWith("app_webview/")?2:3;groups[group]+=size;}
                else complete=false; // A changing/unreadable tree is not a proven budget.
            }
            return new Snapshot(bytes,root.getUsableSpace(),complete,groups);
        }catch(IOException unavailable){return new Snapshot(bytes,0,false,groups);}
    }
}
