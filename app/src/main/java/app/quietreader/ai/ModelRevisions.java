package app.quietreader.ai;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Bounded cleanup of explicitly marked private installations. Keeps one rollback revision. */
public final class ModelRevisions {
    private ModelRevisions(){}
    public static void prune(File parent,String current)throws IOException{
        prune(parent,current,Collections.emptySet());
    }
    public static void prune(File parent,String current,Set<String> approved)throws IOException{
        if(!current.matches("[0-9a-f]{64}")||Files.isSymbolicLink(parent.toPath()))return;
        File root=parent.getCanonicalFile(),active=new File(root,current);
        if(!marked(active,"ready",current))return;
        File[] children=root.listFiles();if(children==null)return;
        List<File> ready=new ArrayList<>(),incomplete=new ArrayList<>();
        for(File dir:children){
            interrupted();String name=dir.getName();
            if(name.equals(current)||approved.contains(name)||!name.matches("[0-9a-f]{64}")||!dir.isDirectory()||Files.isSymbolicLink(dir.toPath())||!dir.getCanonicalFile().getParentFile().equals(root))continue;
            if(marked(dir,"ready",name))ready.add(dir);
            else if(marked(dir,"installing",name))incomplete.add(dir);
        }
        ready.sort(Comparator.comparingLong((File dir)->new File(dir,"ready").lastModified()).reversed().thenComparing(File::getName));
        // The newest complete predecessor remains available for a compatible rollback.
        for(int n=1;n<ready.size();n++)remove(ready.get(n),root,0);
        for(File dir:incomplete)remove(dir,root,0);
    }
    private static boolean marked(File dir,String marker,String revision)throws IOException{
        File file=new File(dir,marker);
        if(Files.isSymbolicLink(dir.toPath())||Files.isSymbolicLink(file.toPath())||!file.isFile()||file.length()!=64)return false;
        return revision.equals(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
    }
    private static boolean remove(File file,File root,int depth)throws IOException{
        interrupted();
        if(depth>8||Files.isSymbolicLink(file.toPath())||!file.getCanonicalPath().startsWith(root.getPath()+File.separator))return false;
        File[] children=file.listFiles();boolean complete=true;
        if(children!=null)for(File child:children)complete=remove(child,root,depth+1)&&complete;
        return complete&&(!file.exists()||file.delete());
    }
    private static void interrupted()throws InterruptedIOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("模型清理已取消");}
}
