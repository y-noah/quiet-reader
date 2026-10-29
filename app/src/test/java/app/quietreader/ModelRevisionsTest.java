package app.quietreader;

import app.quietreader.ai.ModelRevisions;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

public class ModelRevisionsTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private String id(char c){return String.valueOf(c).repeat(64);}
    private File revision(File root,char c,String marker,long time)throws Exception{
        File dir=new File(root,id(c));assertTrue(dir.mkdir());File flag=new File(dir,marker);
        Files.write(flag.toPath(),id(c).getBytes(StandardCharsets.UTF_8));assertTrue(flag.setLastModified(time));
        Files.write(new File(dir,"model.bin").toPath(),new byte[]{1,2,3});return dir;
    }
    @Test public void retainsCurrentAndNewestFallbackOnlyAfterSuccess()throws Exception{
        File root=temporary.newFolder();File old=revision(root,'a',"ready",1000),fallback=revision(root,'b',"ready",2000),current=revision(root,'c',"installing",3000),partial=revision(root,'d',"installing",4000);
        ModelRevisions.prune(root,id('c'));assertTrue(old.exists());assertTrue(partial.exists());
        Files.write(new File(current,"ready").toPath(),id('c').getBytes(StandardCharsets.UTF_8));
        ModelRevisions.prune(root,id('c'));assertFalse(old.exists());assertFalse(partial.exists());assertTrue(fallback.exists());assertTrue(current.exists());
    }
    @Test public void leavesUnmarkedAndForeignFilesUntouched()throws Exception{
        File root=temporary.newFolder();revision(root,'a',"ready",1000);File unknown=new File(root,id('e'));assertTrue(unknown.mkdir());
        File unrelated=new File(root,"notes.txt");Files.write(unrelated.toPath(),new byte[]{7});
        File forged=revision(root,'f',"ready",2000);Files.write(new File(forged,"ready").toPath(),id('b').getBytes(StandardCharsets.UTF_8));
        ModelRevisions.prune(root,id('a'));assertTrue(unknown.exists());assertTrue(unrelated.exists());assertTrue(forged.exists());
    }
    @Test public void interruptionDoesNotStartCleanup()throws Exception{
        File root=temporary.newFolder();revision(root,'a',"ready",1000);File old=revision(root,'b',"installing",2000);
        Thread.currentThread().interrupt();try{ModelRevisions.prune(root,id('a'));fail("expected cancellation");}catch(InterruptedIOException expected){}finally{Thread.interrupted();}
        assertTrue(old.exists());
    }
    @Test public void approvedCompatibleRevisionIsPreservedEvenWhenOlderThanOtherCopies()throws Exception{
        File root=temporary.newFolder();File approved=revision(root,'a',"ready",1000),newer=revision(root,'b',"ready",2000);revision(root,'c',"ready",3000);
        ModelRevisions.prune(root,id('c'),java.util.Collections.singleton(id('a')));
        assertTrue(approved.exists());assertTrue(newer.exists());
    }
}
