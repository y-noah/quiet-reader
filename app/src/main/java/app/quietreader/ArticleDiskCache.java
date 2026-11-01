package app.quietreader;

import android.content.Context;
import android.os.Looper;
import android.webkit.CookieManager;
import app.quietreader.discovery.HotCandidate;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Private, bounded derivative cache. No cookies or remote HTML are serialized. */
final class ArticleDiskCache {
    static final long MAX_BYTES=96L*1024*1024,MAX_AGE=7L*24*60*60*1000;
    static final int MAX_ENTRY=2*1024*1024;
    private static final Object IO_LOCK=new Object();
    private static volatile Retention retention;
    private static volatile Retention published;
    void publishItems(Collection<Item> items){
        Set<String> files=new HashSet<>();Map<String,Set<String>> versions=new HashMap<>();String namespace=namespace();
        for(Item item:items){String name=file(item.url,namespace,session(item.url)).getName();files.add(name);versions.computeIfAbsent(name,k->new HashSet<>()).add(ArticleVersion.hash(item));}
        published=new Retention(root().getAbsolutePath(),files,versions);
    }
    boolean pinned(String url,String namespace,String session,String hash){
        Retention current=published;return current!=null&&current.root.equals(root().getAbsolutePath())&&current.accepts(file(url,namespace,session).getName(),hash);
    }
    static final class Retention {
        final String root;final Set<String> files;final Map<String,Set<String>> versions;
        Retention(String root,Set<String> files){this(root,files,Collections.emptyMap());}
        Retention(String root,Set<String> files,Map<String,Set<String>> versions){this.root=root;this.files=Collections.unmodifiableSet(new HashSet<>(files));this.versions=versions;}
        boolean accepts(String file,String hash){Set<String> allowed=versions.get(file);return files.contains(file)&&(allowed==null||allowed.contains(hash));}
    }
    private final class WriteJob implements Runnable {
        final Document document;final String namespace,session,target,root;final long at;final Retention owner;
        WriteJob(Document document,String namespace,String session,long at,Retention owner){this.document=document;this.namespace=namespace;this.session=session;this.at=at;this.owner=owner;target=file(document.url,namespace,session).getName();root=root().getAbsolutePath();}
        public void run(){write(document,namespace,session,at,owner);}
    }
    // Keep a small single-writer queue; speculative acquisition yields before it fills.
    private static final ThreadPoolExecutor writer=new ThreadPoolExecutor(0,1,20,TimeUnit.SECONDS,new ArrayBlockingQueue<>(24),r->{Thread t=new Thread(r,"article-cache-write");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    static boolean acceptsPrefetch(){return writer.getQueue().remainingCapacity()>4;}
    private final Context context;
    private final long budget;
    ArticleDiskCache(Context context){this(context,MAX_BYTES);}
    ArticleDiskCache(Context context,long budget){this.context=context.getApplicationContext();this.budget=Math.max(1,Math.min(MAX_BYTES,budget));}
    String namespace(){return context.getSharedPreferences("article-cache",0).getString("namespace","initial");}
    void invalidate(){context.getSharedPreferences("article-cache",0).edit().putString("namespace",UUID.randomUUID().toString()).apply();}
    static String session(String url){return ArticleCookies.fingerprint(url,CookieManager.getInstance().getCookie(url));}
    private File root(){return new File(context.getCacheDir(),"article-bodies-v1");}
    private File file(String url,String namespace,String session){return new File(root(),HotCandidate.hash(namespace+"\n"+session+"\n"+url)+".json");}
    static final class Hit {final Document document;final long at;Hit(Document document,long at){this.document=document;this.at=at;}}
    Hit read(String url,String namespace,String session,boolean offline,long now){
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Disk cache must run on worker");
        File file=file(url,namespace,session);
        try{
            if(!namespace.equals(namespace())||!file.isFile())return null;
            if(file.length()>MAX_ENTRY){file.delete();return null;}
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[8192];int count;while((count=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())return null;if(bytes.size()+count>MAX_ENTRY)return null;bytes.write(buffer,0,count);}}
            JSONObject value=new JSONObject(bytes.toString("UTF-8"));long at=value.getLong("at"),age=now-at;
            if(age<0||age>MAX_AGE){file.delete();return null;}
            Document doc=ReadingHistory.decode(value.getJSONObject("document"));JSONArray related=value.optJSONArray("related");if(related!=null)doc.related.addAll(Models.fromJson(related));
            if(!offline&&age>10*60_000&&!pinned(url,namespace,session,doc.metadataHash))return null;
            if(doc.loginRequired||doc.sourceUnavailable||doc.filteredVideo||!url.equals(doc.url)||!ArticleSession.contentCurrent(doc)||!TiebaReplyRelations.canReuse(doc,offline)||!HupuDiscussion.canReuse(doc,offline)||(!doc.hasContent()&&!doc.unsupportedVideo)||!namespace.equals(namespace()))return null;
            file.setLastModified(now);return new Hit(doc,at);
        }catch(Exception ex){file.delete();return null;}
    }
    void enqueue(Document doc,String namespace,String session,long at){
        if((!doc.hasContent()&&!doc.unsupportedVideo)||doc.filteredVideo||doc.loginRequired||doc.sourceUnavailable||DocumentSize.estimate(doc)>MAX_ENTRY||!DocumentSize.serializationFits(doc,MAX_ENTRY))return;
        Document copy=snapshot(doc);
        Retention owner=retention;
        try{writer.execute(new WriteJob(copy,namespace,session,at,owner));}catch(RejectedExecutionException full){/* Admission verifies persistence separately; no false receipt. */}
    }
    static Document snapshot(Document doc){
        // Snapshot mutable section lists before handing work to the bounded IO queue.
        Document copy=new Document();copy.url=doc.url;copy.title=doc.title;copy.byline=doc.byline;copy.notice=doc.notice;copy.nextUrl=doc.nextUrl;copy.moreStatus=doc.moreStatus;copy.unsupportedVideo=doc.unsupportedVideo;copy.filteredVideos=doc.filteredVideos;copy.filteredSectionIds.addAll(doc.filteredSectionIds);copy.related.addAll(doc.related);
        copy.videoPoster=doc.videoPoster;
        copy.videoUrl=doc.videoUrl;
        copy.videos.addAll(doc.videos);
        copy.videoTopic=doc.videoTopic;
        copy.metadataHash=doc.metadataHash;
        copy.loginRequired=doc.loginRequired;copy.sourceUnavailable=doc.sourceUnavailable;copy.filteredVideo=doc.filteredVideo;
        copy.sourceModifiedAt=doc.sourceModifiedAt;copy.sourceUpdateEvidence=doc.sourceUpdateEvidence;
        copy.topic=doc.topic;
        copy.discussionPage=doc.discussionPage;
        copy.virtualDiscussion=doc.virtualDiscussion;
        copy.tiebaReplyRevision=doc.tiebaReplyRevision;
        copy.hupuBodyRevision=doc.hupuBodyRevision;
        for(Section section:ReaderHtml.sections(doc)){Section cloned=new Section(section.id,section.label,section.answer);cloned.continuationUrl=section.continuationUrl;cloned.sourcePostId=section.sourcePostId;cloned.sourceChildId=section.sourceChildId;cloned.sourceReplyAt=section.sourceReplyAt;cloned.sourceReplyEvidence=section.sourceReplyEvidence;cloned.nestedReply=section.nestedReply;cloned.parentSectionId=section.parentSectionId;cloned.replyToAuthor=section.replyToAuthor;cloned.blocks.addAll(section.blocks);copy.sections.add(cloned);copy.blocks.addAll(section.blocks);}
        return copy;
    }
    void write(Document doc,String namespace,String session,long at){
        write(doc,namespace,session,at,retention);
    }
    private void write(Document doc,String namespace,String session,long at,Retention owner){
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Disk cache must run on worker");
        synchronized(IO_LOCK){
        File partial=null;
        try{
            if(doc.loginRequired||doc.sourceUnavailable||doc.filteredVideo||!namespace.equals(namespace()))return;
            File root=root(),target=file(doc.url,namespace,session);Retention current=retention;
            if(current!=null&&current.root.equals(root.getAbsolutePath())&&((current!=owner&&!current.files.contains(target.getName()))||(current.versions.containsKey(target.getName())&&!current.accepts(target.getName(),doc.metadataHash))))return;
            if(!DocumentSize.serializationFits(doc,MAX_ENTRY))return;
            byte[] bytes=new JSONObject().put("at",at).put("document",ReadingHistory.encode(doc)).put("related",Models.toJson(doc.related)).toString().getBytes(StandardCharsets.UTF_8);
            if(bytes.length>MAX_ENTRY||bytes.length>budget)return;
            if(!root.isDirectory()&&!root.mkdirs())return;
            // Reopening history may offer the exact acquisition again. Keep its
            // existing file instead of reserving, syncing and replacing it.
            // Comparing the complete record also preserves real body/time updates.
            if(sameRecord(target,bytes)){target.setLastModified(System.currentTimeMillis());return;}
            if(root.getUsableSpace()<bytes.length+16L*1024*1024||!reserve(root,target,bytes.length,System.currentTimeMillis()))return;
            partial=File.createTempFile("body-",".partial",root);try(FileOutputStream out=new FileOutputStream(partial)){out.write(bytes);out.getFD().sync();}
            if(!namespace.equals(namespace()))return;
            if(!partial.renameTo(target))return;target.setLastModified(at);
        }catch(Exception ignored){}finally{if(partial!=null&&partial.exists())partial.delete();}
        }
    }
    /** Main-thread capture only: cookies and paths, no filesystem inventory. */
    Retention retainBatch(Collection<String> urls){
        Set<String> files=new HashSet<>();String namespace=namespace();
        for(String url:urls)files.add(file(url,namespace,session(url)).getName());
        Retention next=new Retention(root().getAbsolutePath(),files);retention=next;
        writer.getQueue().removeIf(job->job instanceof ArticleDiskCache.WriteJob&&((WriteJob)job).root.equals(next.root)&&!next.files.contains(((WriteJob)job).target));
        return next;
    }
    Retention retainItems(Collection<Item> items){
        Set<String> files=new HashSet<>();Map<String,Set<String>> versions=new HashMap<>();String namespace=namespace();
        for(Item item:items){String name=file(item.url,namespace,session(item.url)).getName();files.add(name);versions.computeIfAbsent(name,k->new HashSet<>()).add(ArticleVersion.hash(item));}
        Retention next=new Retention(root().getAbsolutePath(),files,versions);retention=next;
        writer.getQueue().removeIf(job->job instanceof ArticleDiskCache.WriteJob&&((WriteJob)job).root.equals(next.root)&&!next.accepts(((WriteJob)job).target,((WriteJob)job).document.metadataHash));
        return next;
    }
    /** A durable receipt requires the actual serialized record to read back successfully. */
    void verify(Document source,java.util.function.Consumer<Hit> receipt){verify(source,false,receipt);}
    void verify(Document source,boolean offline,java.util.function.Consumer<Hit> receipt){
        if(source.cachedSession==null||source.cachedNamespace==null||!source.hasContent()||source.filteredVideo||source.loginRequired||source.sourceUnavailable){receipt.accept(null);return;}
        Document copy=snapshot(source);String namespace=source.cachedNamespace,session=source.cachedSession;long at=source.cachedAt;Retention owner=retention;
        try{writer.execute(()->{
            write(copy,namespace,session,at,owner);
            Hit hit=read(copy.url,namespace,session,offline,System.currentTimeMillis());
            receipt.accept(hit!=null&&hit.at==at&&Objects.equals(hit.document.metadataHash,copy.metadataHash)?hit:null);
        });}catch(RejectedExecutionException full){receipt.accept(null);}
    }
    /** Same IO lock as writes; stale pruning cannot delete a newer batch. */
    boolean pruneBatch(Retention owner){
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Disk cache must run on worker");
        synchronized(IO_LOCK){
            if(retention!=owner)return true;
            File root=root();if(java.nio.file.Files.isSymbolicLink(root.toPath()))return false;
            File[] files=root.listFiles();if(files==null)return !root.exists();boolean ok=true;
            for(File file:files){if(retention!=owner)return ok;if(file.getName().matches("[a-f0-9]{64}\\.json")&&!owner.files.contains(file.getName())&&!publishedFile(file)&&file.isFile()&&!java.nio.file.Files.isSymbolicLink(file.toPath())&&!file.delete())ok=false;}
            return ok;
        }
    }
    private boolean publishedFile(File file){Retention current=published;return current!=null&&current.root.equals(root().getAbsolutePath())&&current.files.contains(file.getName());}
    private static boolean sameRecord(File target,byte[] bytes){
        if(!target.isFile()||java.nio.file.Files.isSymbolicLink(target.toPath())||target.length()!=bytes.length)return false;
        try(InputStream in=new BufferedInputStream(new FileInputStream(target))){
            byte[] buffer=new byte[8192];int offset=0,count;
            while((count=in.read(buffer))!=-1){
                if(offset+count>bytes.length)return false;
                for(int n=0;n<count;n++)if(buffer[n]!=bytes[offset+n])return false;
                offset+=count;
            }
            return offset==bytes.length;
        }catch(IOException|SecurityException unavailable){return false;}
    }
    /** Caller invalidates the namespace first; serialization waits out any old writer. */
    boolean clearFiles(){
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Disk cache must run on worker");
        synchronized(IO_LOCK){
            File[] files=root().listFiles();if(files==null)return !root().exists();boolean cleared=true;
            for(File file:files)if(file.isFile()&&(file.getName().endsWith(".json")||file.getName().endsWith(".partial")))if(!file.delete())cleared=false;
            return cleared;
        }
    }
    /** Reserve room for the temporary copy too; preserve the previous target until rename. */
    private boolean reserve(File root,File target,int incoming,long now){
        if(incoming>budget)return false;
        File[] files=root.listFiles();if(files==null)return false;
        long used=0;int entries=0;boolean replacing=false;List<File> evictable=new ArrayList<>();
        for(File file:files){
            if(!file.isFile()||java.nio.file.Files.isSymbolicLink(file.toPath()))return false;
            // Recover expired derivatives in the same inventory used for room
            // reservation. The previous target survives until atomic replacement.
            boolean record=file.getName().endsWith(".json");
            boolean abandoned=file.getName().startsWith("body-")&&file.getName().endsWith(".partial")&&now-file.lastModified()>60*60_000;
            if(!file.equals(target)&&(abandoned||record&&now-file.lastModified()>MAX_AGE)&&file.delete())continue;
            used+=file.length();
            if(record){entries++;if(file.equals(target))replacing=true;else if(!publishedFile(file))evictable.add(file);}
        }
        int allowedExisting=1000-(replacing?0:1);
        if(used<=budget-incoming&&entries<=allowedExisting)return true;
        evictable.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        for(File file:evictable){
            if(used<=budget-incoming&&entries<=allowedExisting)break;
            long size=file.length();if(file.delete()){used-=size;entries--;}
        }
        return used<=budget-incoming&&entries<=allowedExisting;
    }
}
