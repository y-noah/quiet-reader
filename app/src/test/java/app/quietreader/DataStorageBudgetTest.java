package app.quietreader;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class DataStorageBudgetTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private void write(File root,String path,int bytes)throws Exception{File file=new File(root,path);file.getParentFile().mkdirs();Files.write(file.toPath(),new byte[bytes]);}
    @Test public void includesPrivateWebDataModelsAndCompiledCode()throws Exception{
        File root=temporary.newFolder();write(root,"cache/article-bodies-v1/body.json",10);write(root,"databases/feed.db-wal",20);write(root,"shared_prefs/login.xml",30);write(root,"app_webview/Default/Cookies",40);write(root,"files/local-models/revision/model",500);write(root,"code_cache/compiler.bin",600);
        DataStorageBudget.Snapshot value=DataStorageBudget.measure(root);assertTrue(value.complete);assertEquals(1200,value.bytes);
        assertEquals(10,value.bodyBytes);assertEquals(20,value.databaseBytes);assertEquals(40,value.webBytes);assertEquals(1130,value.otherBytes);
        assertEquals(value.bytes,value.bodyBytes+value.databaseBytes+value.webBytes+value.otherBytes);
        assertTrue(value.description().contains("不是全部可清理缓存"));
        assertTrue(new File(root,"shared_prefs/login.xml").exists());
    }
    @Test public void exhaustedInventoryIsStillReportedIncomplete()throws Exception{
        File root=temporary.newFolder();write(root,"cache/body",10);DataStorageBudget.Snapshot value=DataStorageBudget.measure(root,1);assertFalse(value.complete);
        assertTrue(value.description().contains("未统计完整"));
    }
    @Test public void incompleteInventoryDoesNotClaimKnownFreeSpaceBlocksPrefetch(){
        DataStorageBudget.Snapshot value=new DataStorageBudget.Snapshot(3_000_000_000L,DataStorageBudget.MIN_FREE+1,false);
        assertFalse(value.complete);
        assertTrue(value.description().contains("可继续主动预读"));
        assertFalse(value.description().contains("统计未完成，暂缓主动预读"));
    }
    @Test public void thresholdsReserveSpaceAndRequireVerifiedFreeSpace(){
        long free=DataStorageBudget.MIN_FREE;
        assertTrue(new DataStorageBudget.FreeSpace(free,true).allowsPrefetch());
        assertTrue(new DataStorageBudget.Snapshot(3_000_000_000L,free,false).description().contains("可继续主动预读"));
        assertFalse(new DataStorageBudget.FreeSpace(free-1,true).allowsPrefetch());
        assertFalse(new DataStorageBudget.FreeSpace(free,false).allowsPrefetch());
    }
    @Test public void freeSpaceCheckDoesNotEnumeratePrivateFiles()throws Exception{
        File directory=temporary.newFolder();
        File root=new File(directory.getAbsolutePath()){
            @Override public File getCanonicalFile(){return this;}
            @Override public File[] listFiles(){throw new AssertionError("Prefetch must not enumerate private files");}
            @Override public long getUsableSpace(){return DataStorageBudget.MIN_FREE+1;}
        };
        DataStorageBudget.FreeSpace value=DataStorageBudget.freeSpace(root);assertTrue(value.verified);assertTrue(value.allowsPrefetch());
    }
    @Test public void missingOrUnavailableDataDirectoryCannotAuthorizePrefetch()throws Exception{
        File absent=new File(temporary.getRoot(),"missing");assertFalse(DataStorageBudget.freeSpace(absent).allowsPrefetch());assertFalse(absent.exists());
        File unavailable=new File(temporary.newFolder().getAbsolutePath()){
            @Override public File getCanonicalFile(){return this;}
            @Override public long getUsableSpace(){throw new SecurityException("Synthetic unavailable space query");}
        };
        assertFalse(DataStorageBudget.freeSpace(unavailable).verified);assertFalse(DataStorageBudget.freeSpace(unavailable).allowsPrefetch());
    }
    @Test public void imagesBelongToReadingCacheAndDoNotHideWebSessions()throws Exception{
        File root=temporary.newFolder();write(root,"cache/reader-images-v1/record.img",100);write(root,"cache/article-bodies-v1/body.json",30);write(root,"app_webview/Default/Cookies",40);
        DataStorageBudget.Snapshot value=DataStorageBudget.measure(root);assertTrue(value.complete);assertEquals(130,value.bodyBytes);assertEquals(40,value.webBytes);assertEquals(170,value.bytes);assertTrue(value.description().contains("正文及图片缓存"));
    }
}

