package app.quietreader;

import org.junit.*;
import app.quietreader.discovery.HotCandidate;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class ImageDiskCacheTest {
    @Test public void batchPruneKeepsSharedOwnerAndStaleCleanupCannotDeleteAnything()throws Exception{
        File root=temporary.newFolder();ImageDiskCache cache=cache(root,10);long now=100000;
        Map<String,String> first=new LinkedHashMap<>(sessions());first.put("https://example.com/old",HotCandidate.hash("account"));
        Map<String,String> next=new LinkedHashMap<>(sessions());next.put("https://example.com/new",HotCandidate.hash("account"));
        cache.write("old",URL,first,image(),now,()->true);cache.write("new",URL+"new",next,image(),now,()->true);
        assertTrue(cache.retainOwners(Set.of(),()->false));assertEquals(2,root.list().length);
        assertTrue(cache.retainOwners(Set.of("https://example.com/new"),()->true));assertEquals(1,root.list().length);
        assertNull(cache.read("old",URL,now+1));assertNotNull(cache.read("new",URL+"new",now+1));
    }
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private static final String URL="https://images.example.com/a.gif",PAGE="https://example.com/article";
    private static byte[] image(){return "GIF89a123456789".getBytes(StandardCharsets.US_ASCII);}
    private ImageDiskCache cache(File root,int max){return new ImageDiskCache(root,8192,max,()->DataStorageBudget.MIN_FREE+100000);}
    private Map<String,String> sessions(){return Collections.singletonMap(URL,app.quietreader.discovery.HotCandidate.hash("cookie"));}
    private static final class InventoryRoot extends File {
        int inventories,modifiedReads;
        InventoryRoot(File root){super(root.getPath());}
        @Override public File[] listFiles(){
            inventories++;File[] files=super.listFiles();if(files==null)return null;
            for(int n=0;n<files.length;n++){String path=files[n].getPath();files[n]=new File(path){@Override public long lastModified(){modifiedReads++;return super.lastModified();}};}
            return files;
        }
    }
    @Test public void belowBudgetWriteUsesOneInventoryAndDoesNotSortExistingRecords()throws Exception{
        File actual=temporary.newFolder();long at=100000;
        ImageDiskCache seed=new ImageDiskCache(actual,1024*1024,1000,()->DataStorageBudget.MIN_FREE+2*1024*1024);
        for(int n=0;n<64;n++)seed.write("key"+n,URL+n,sessions(),image(),at+n,()->true);
        InventoryRoot observed=new InventoryRoot(actual);
        ImageDiskCache cache=new ImageDiskCache(observed,1024*1024,1000,()->DataStorageBudget.MIN_FREE+2*1024*1024);
        cache.write("next",URL+"next",sessions(),image(),at+100,()->true);
        assertEquals("One bounded inventory for this admitted write",1,observed.inventories);
        assertEquals("Each existing record needs its age once, without comparator metadata reads",64,observed.modifiedReads);
        assertEquals(65,actual.list().length);assertNotNull(cache.read("next",URL+"next",at+101));
    }
    @Test public void repeatedPressurePreservesRecentlyReadImageAndExactCountBudget()throws Exception{
        File root=temporary.newFolder();ImageDiskCache cache=cache(root,3);long at=100000;
        cache.write("kept",URL,sessions(),image(),at++,()->true);
        for(int n=0;n<20;n++){
            assertNotNull(cache.read("kept",URL,at++));
            cache.write("key"+n,URL+n,sessions(),image(),at++,()->true);
            assertTrue(root.list().length<=3);long bytes=0;for(File file:root.listFiles())bytes+=file.length();assertTrue(bytes<=8192);
            assertNotNull(cache.read("key"+n,URL+n,at++));
        }
        assertNotNull(cache.read("kept",URL,at++));assertNull(cache.read("key0",URL+0,at));
    }
    @Test public void replacementAtSingleEntryLimitPublishesFreshImage()throws Exception{
        File root=temporary.newFolder();ImageDiskCache cache=cache(root,1);long at=100000;
        cache.write("key",URL,sessions(),image(),at,()->true);
        byte[] updated=image();updated[updated.length-1]='z';
        cache.write("key",URL,sessions(),updated,at+1000,()->true);
        assertEquals(1,root.list().length);
        assertArrayEquals(updated,cache.read("key",URL,at+1001).bytes);
        assertNotNull(cache.read("key",URL,at+ImageDiskCache.MAX_AGE+1));
    }
    @Test public void replacementAtFullEntryLimitKeepsUnrelatedCachedImage()throws Exception{
        File root=temporary.newFolder();ImageDiskCache cache=cache(root,2);long at=100000;
        cache.write("a",URL,sessions(),image(),at,()->true);
        cache.write("b",URL+"b",sessions(),image(),at+1,()->true);
        cache.write("a",URL,sessions(),image(),at+2,()->true);
        assertEquals(2,root.list().length);
        assertNotNull(cache.read("b",URL+"b",at+3));
        assertNotNull(cache.read("a",URL,at+3));
    }
    @Test public void recordsSurviveNewInstanceButExpireByAcquisitionTime()throws Exception{
        File root=temporary.newFolder();long at=100000;ImageDiskCache cache=cache(root,10);cache.write("key",URL,sessions(),image(),at,()->true);
        assertArrayEquals(image(),cache(root,10).read("key",URL,at+1).bytes);assertEquals(sessions(),cache.read("key",URL,at+2).sessions);
        assertNull(cache.read("key",URL,at+ImageDiskCache.MAX_AGE+1));assertEquals(0,root.list().length);
    }
    @Test public void corruptAndTruncatedPayloadsAreDeleted()throws Exception{
        File root=temporary.newFolder();ImageDiskCache cache=cache(root,10);cache.write("key",URL,sessions(),image(),100000,()->true);File file=root.listFiles()[0];byte[] bytes=Files.readAllBytes(file.toPath());bytes[bytes.length-1]^=1;Files.write(file.toPath(),bytes);assertNull(cache.read("key",URL,100001));assertFalse(file.exists());
        cache.write("key",URL,sessions(),image(),100000,()->true);file=root.listFiles()[0];Files.write(file.toPath(),new byte[]{0,1});assertNull(cache.read("key",URL,100001));assertFalse(file.exists());
    }
    @Test public void lruAndLowSpaceNeverDeleteUnrelatedData()throws Exception{
        File root=temporary.newFolder();ImageDiskCache cache=cache(root,2);cache.write("a",URL,sessions(),image(),100000,()->true);cache.write("b",URL+"b",sessions(),image(),100001,()->true);assertNotNull(cache.read("a",URL,100002));cache.write("c",URL+"c",sessions(),image(),100003,()->true);
        assertNotNull(cache.read("a",URL,100004));assertNull(cache.read("b",URL+"b",100004));assertNotNull(cache.read("c",URL+"c",100004));
        File other=new File(root,"preserve-user-data");Files.write(other.toPath(),new byte[]{1});assertFalse(cache.clearFiles());assertTrue(other.isFile());
        ImageDiskCache low=new ImageDiskCache(temporary.newFolder(),8192,10,()->0);low.write("key",URL,sessions(),image(),100000,()->true);assertNull(low.read("key",URL,100001));
    }
    @Test public void changedNamespaceDuringWriteCannotPublishRecord()throws Exception{
        File root=temporary.newFolder();ImageDiskCache cache=cache(root,10);AtomicInteger checks=new AtomicInteger();cache.write("key",URL,sessions(),image(),100000,()->checks.incrementAndGet()==1);assertEquals(0,root.list().length);
    }
    @Test public void failedAbandonedPartialDeletionStillConsumesItsActualBytes()throws Exception{
        File actual=temporary.newFolder(),partial=new File(actual,"image-stuck.partial");
        Files.write(partial.toPath(),new byte[8192]);assertTrue(partial.setLastModified(1));AtomicInteger refused=new AtomicInteger();
        File observed=new File(actual.getPath()){
            @Override public File[] listFiles(){File[] files=super.listFiles();if(files!=null)for(int n=0;n<files.length;n++)if(files[n].equals(partial))files[n]=new File(partial.getPath()){@Override public boolean delete(){refused.incrementAndGet();return false;}};return files;}
        };
        ImageDiskCache cache=cache(observed,10);long now=2*60*60_000L;
        cache.write("key",URL,sessions(),image(),now,()->true);
        assertTrue("The expired partial cleanup was actually attempted",refused.get()>0);
        assertTrue(partial.isFile());assertNull("A failed deletion cannot create extra budget",cache.read("key",URL,now+1));
        long used=0;for(File file:actual.listFiles())used+=file.length();assertEquals(8192,used);
        assertTrue(partial.delete());cache.write("key",URL,sessions(),image(),now+2,()->true);
        assertNotNull("Admission recovers once physical space is released",cache.read("key",URL,now+3));
    }
    @Test public void offlineReaderRestoresImageWithoutFetcherAndRejectsAccountChange()throws Exception{
        File root=temporary.newFolder();Map<String,String> cookies=new HashMap<>();cookies.put(PAGE,"account=a");AtomicInteger calls=new AtomicInteger();
        ReaderImages online=new ReaderImages(cookies::get,(url,ref,scoped)->{calls.incrementAndGet();return image();},8192);online.attach(cache(root,10),()->"first");online.load(URL,PAGE);assertEquals(1,calls.get());
        ReaderImages offline=new ReaderImages(cookies::get,(url,ref,scoped)->{calls.incrementAndGet();throw new IOException("offline");},8192);offline.attach(cache(root,10),()->"first");assertArrayEquals(image(),offline.load(URL,PAGE));assertEquals(1,calls.get());
        cookies.put(PAGE,"account=b");try{offline.load(URL,PAGE);fail("old account image restored");}catch(IOException expected){assertEquals("offline",expected.getMessage());}assertEquals(2,calls.get());
    }
    @Test public void retryAndNamespaceClearCannotRestoreOldDiskImage()throws Exception{
        File root=temporary.newFolder();AtomicInteger calls=new AtomicInteger();AtomicReference<String> namespace=new AtomicReference<>("first");ReaderImages online=new ReaderImages(url->null,(url,ref,scoped)->{calls.incrementAndGet();return image();},8192);online.attach(cache(root,10),namespace::get);online.load(URL,PAGE);online.evictAll();online.remove(URL);online.load(URL,PAGE);assertEquals(2,calls.get());
        namespace.set("second");online.load(URL,PAGE);assertEquals(3,calls.get());online.evictAll();assertArrayEquals(image(),online.load(URL,PAGE));assertEquals(3,calls.get());assertTrue(cache(root,10).clearFiles());
    }
}
