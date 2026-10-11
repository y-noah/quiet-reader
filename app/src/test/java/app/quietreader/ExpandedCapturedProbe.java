package app.quietreader;
import java.nio.file.*;
import java.util.*;
import static app.quietreader.Models.*;
public final class ExpandedCapturedProbe {
 public static void main(String[] args)throws Exception{
  Path dir=Path.of(args[0]);
  for(Source s:new Source[]{Source.BAIDU,Source.V2EX,Source.SSPAI,Source.BILIBILI,Source.XHS}){
   String raw=new String(Files.readAllBytes(dir.resolve(s==Source.V2EX?"v2ex-hot.html":s.name().toLowerCase(Locale.ROOT)+".txt")),java.nio.charset.StandardCharsets.UTF_8);List<Item> rows=SourceParser.list(s,raw);
   System.out.println(s+" count="+rows.size()+" title="+rows.get(0).title);
   if(rows.size()<5)throw new AssertionError(s+" unexpectedly short board");
  }
  for(Source s:new Source[]{Source.SSPAI,Source.V2EX}){Document doc=SourceParser.article(s,new String(Files.readAllBytes(dir.resolve(s.name().toLowerCase(Locale.ROOT)+"-article.html")),java.nio.charset.StandardCharsets.UTF_8),s.login);System.out.println(s+" article blocks="+doc.blocks.size()+" sections="+doc.sections.size());if(doc.blocks.size()<3)throw new AssertionError("Missing body: "+s);}
 }
}
