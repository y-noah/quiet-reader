package app.quietreader;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class ImagePrefetchTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private static final String PAGE="https://www.zhihu.com/question/123",IMAGE="https://images.example.com/first.gif";
    private byte[] image(){return "GIF89a123456789".getBytes(StandardCharsets.US_ASCII);}
    private ImageDiskCache disk(File root){return new ImageDiskCache(root,8192,20,()->DataStorageBudget.MIN_FREE+100000);}
    @Test public void questionAndInitialAnswerFiguresExcludeCommentsLaterAnswersAndVideoCovers(){
        Document doc=new Document();doc.url=PAGE;doc.videoPoster=IMAGE+"poster";
        Section question=new Section("question","问题补充",false);question.blocks.add(new Block("image",IMAGE));question.blocks.add(new Block("image",doc.videoPoster));question.blocks.add(new Block("text","表情",List.of(new InlineImage(0,2,IMAGE+"emoji","笑"))));
        Section answer=new Section("answer","回答一",true);answer.blocks.add(new Block("image",IMAGE));answer.blocks.add(new Block("image",IMAGE+"answer"));
        Section hidden=new Section("hidden","回答二",true);hidden.blocks.add(new Block("image",IMAGE+"hidden"));Section comment=new Section("comment","评论",false);comment.blocks.add(new Block("image",IMAGE+"comment"));
        doc.sections.addAll(List.of(question,answer,hidden,comment));assertEquals(List.of(IMAGE,IMAGE+"answer"),ImagePreloader.select(doc));
    }
    @Test public void bodyFiguresAreBoundedAndUnsafeSchemesAreSkipped(){
        Document doc=new Document();doc.blocks.add(new Block("image","data:image/png;base64,none"));doc.blocks.add(new Block("image","http://example.org/a.png"));for(int n=0;n<7;n++)doc.blocks.add(new Block("image",IMAGE+n));
        assertEquals(List.of(IMAGE+0,IMAGE+1,IMAGE+2,IMAGE+3,IMAGE+4,IMAGE+5,IMAGE+6),ImagePreloader.select(doc));
        for(int n=7;n<40;n++)doc.blocks.add(new Block("image",IMAGE+n));
        assertEquals(24,ImagePreloader.select(doc).size());
    }
    @Test public void speculativePayloadGoesToDiskWithoutRetainingMemoryAndReaderReusesIt()throws Exception{
        File root=temporary.newFolder();AtomicInteger calls=new AtomicInteger();ReaderImages cache=new ReaderImages(u->null,(url,ref,cookies,ticket,limit)->{assertEquals(1024,limit);calls.incrementAndGet();return image();},8192);cache.attach(disk(root),()->"scope");
        cache.load(IMAGE,PAGE,null,()->true,1024,false);assertEquals(1,root.list().length);
        // A vanished file must trigger a download: speculative load did not populate RAM.
        assertTrue(disk(root).clearFiles());cache.load(IMAGE,PAGE,null,()->true,1024,false);assertEquals(2,calls.get());
        ReaderImages reader=new ReaderImages(u->null,(url,ref,cookies)->{throw new IOException("offline");},8192);reader.attach(disk(root),()->"scope");assertArrayEquals(image(),reader.load(IMAGE,PAGE));assertEquals(2,calls.get());
    }
    @Test public void eligibilityChangeWhileDownloadingCannotPublishToRamOrDisk()throws Exception{
        File root=temporary.newFolder();AtomicBoolean allowed=new AtomicBoolean(true);AtomicInteger calls=new AtomicInteger();ReaderImages cache=new ReaderImages(u->null,(url,ref,cookies,ticket,limit)->{calls.incrementAndGet();allowed.set(false);return image();},8192);cache.attach(disk(root),()->"scope");
        try{cache.load(IMAGE,PAGE,null,allowed::get,1024,false);fail("late image accepted");}catch(InterruptedIOException expected){}assertEquals(0,root.list().length);
        try{cache.load(IMAGE,PAGE,null,allowed::get,1024,false);fail("ineligible request started");}catch(InterruptedIOException expected){}assertEquals(1,calls.get());
    }
    @Test public void cancelledTicketRejectsTransportThatIgnoresInterruption()throws Exception{
        File root=temporary.newFolder();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(1);AtomicBoolean rejected=new AtomicBoolean();
        ReaderImages cache=new ReaderImages(u->null,(url,ref,cookies,ticket,limit)->{assertNotNull(ticket);entered.countDown();boolean waiting=true;while(waiting)try{release.await();waiting=false;}catch(InterruptedException ignored){}return image();},8192);cache.attach(disk(root),()->"scope");
        try(RequestQueue queue=new RequestQueue()){
            queue.submit(ticket->{try{cache.load(IMAGE,PAGE,ticket,()->true,1024,false);}catch(InterruptedIOException expected){rejected.set(true);}catch(Exception error){throw new IllegalStateException(error);}finally{done.countDown();}});
            assertTrue(entered.await(3,TimeUnit.SECONDS));queue.cancelPending();release.countDown();assertTrue(done.await(3,TimeUnit.SECONDS));assertTrue(rejected.get());assertEquals(0,root.list().length);
        }finally{release.countDown();}
    }
    @Test public void speculativeLimitCannotBeBypassedByTransport()throws Exception{
        File root=temporary.newFolder();ReaderImages cache=new ReaderImages(u->null,(url,ref,cookies,ticket,limit)->image(),8192);cache.attach(disk(root),()->"scope");
        try{cache.load(IMAGE,PAGE,null,()->true,12,false);fail("oversize speculative response accepted");}catch(IOException expected){assertTrue(expected.getMessage().contains("too large"));}assertEquals(0,root.list().length);
    }
    @Test public void speculativeLimitAlsoAppliesToExistingMemoryPayloadWithoutDiscardingReaderCache()throws Exception{
        AtomicInteger calls=new AtomicInteger();ReaderImages cache=new ReaderImages(u->null,(url,ref,cookies)->{calls.incrementAndGet();return image();},8192);
        byte[] original=cache.load(IMAGE,PAGE);
        try{cache.load(IMAGE,PAGE,null,()->true,12,false);fail("oversize RAM payload accepted by speculation");}catch(IOException expected){assertTrue(expected.getMessage().contains("too large"));}
        assertSame(original,cache.load(IMAGE,PAGE));assertEquals(1,calls.get());
    }
    @Test public void speculativeLimitAlsoAppliesToDiskWithoutEvictionTouchOrRedownload()throws Exception{
        File root=temporary.newFolder();ImageDiskCache disk=disk(root);AtomicInteger calls=new AtomicInteger();ReaderImages writer=new ReaderImages(u->null,(url,ref,cookies)->image(),8192);writer.attach(disk,()->"scope");writer.load(IMAGE,PAGE);
        File record=root.listFiles()[0];long stamp=System.currentTimeMillis()-10000;assertTrue(record.setLastModified(stamp));stamp=record.lastModified();
        ReaderImages reader=new ReaderImages(u->null,(url,ref,cookies)->{calls.incrementAndGet();throw new IOException("offline");},8192);reader.attach(disk,()->"scope");
        try{reader.load(IMAGE,PAGE,null,()->true,12,false);fail("oversize disk payload accepted by speculation");}catch(IOException expected){assertTrue(expected.getMessage().contains("too large"));}
        assertTrue(record.isFile());assertEquals(stamp,record.lastModified());assertEquals(0,calls.get());assertArrayEquals(image(),reader.load(IMAGE,PAGE));assertEquals(0,calls.get());
    }
}
