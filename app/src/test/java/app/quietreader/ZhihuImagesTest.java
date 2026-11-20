package app.quietreader;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import static app.quietreader.Models.*;
import static org.junit.Assert.*;

public class ZhihuImagesTest {
    private static final String URL="https://www.zhihu.com/question/2104954238514230174";
    private static final String SMALL="https://picx.zhimg.com/50/v2-e261408261a795a0c8225402761dbe85_720w.jpg?source=1def8aca";
    private static final String LARGE="https://pic1.zhimg.com/v2-e261408261a795a0c8225402761dbe85_r.jpg?source=1def8aca";
    private Document parse(String body){return SourceParser.article(Source.ZHIHU,"<div class=QuestionRichText>"+body+"</div>",URL);}
    private static long images(Document doc){return doc.blocks.stream().filter(b->b.type.equals("image")).count();}
    @Test public void capturedPhoneArticleHasOneImagePerActualFigure()throws Exception{
        String html;try(var in=getClass().getResourceAsStream("/zhihu-20261116/image-fallback.html")){html=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        for(Document doc:List.of(SourceParser.article(Source.ZHIHU,html,URL),SourceParser.readingArticle(Source.ZHIHU,html,URL))){
            assertEquals(8,images(doc));Section answer=doc.sections.stream().filter(s->s.id.equals("2105401197649838538")).findFirst().orElseThrow();
            assertEquals(3,answer.blocks.stream().filter(b->b.type.equals("image")).count());
        }
        org.jsoup.nodes.Document oldDom=org.jsoup.Jsoup.parse(html);for(var fallback:new ArrayList<>(oldDom.select("noscript")))fallback.unwrap();
        Document old=SourceParser.article(Source.ZHIHU,oldDom.outerHtml(),URL);assertEquals(15,images(old));
        Document restored=ReadingHistory.decode(ReadingHistory.encode(old));
        assertEquals(8,images(restored));
        Set<String> expanded=new HashSet<>();
        for(Section section:restored.sections)expanded.add(section.id);
        String reader=ReaderHtml.render(restored,Source.ZHIHU,19,true,expanded,false);
        var rendered=org.jsoup.Jsoup.parse(reader);
        assertEquals(8,rendered.select("section.part img").size());
    }
    @Test public void fallbackVariantIsRemovedOnlyInsideItsOwnFigure(){
        Document doc=parse("<figure><noscript><img src='"+SMALL+"'></noscript><div><img src='"+LARGE+"'></div></figure>");
        assertEquals(1,images(doc));assertEquals(LARGE,doc.blocks.get(0).value);
        assertEquals(1,images(parse("<figure><noscript><img src='"+SMALL+"'></noscript></figure>")));
        assertEquals(2,images(parse("<figure><noscript><img src='"+SMALL+"'></noscript></figure><figure><img src='"+LARGE+"'></figure>")));
    }
    @Test public void intentionalLiveRepeatsAndUnrelatedFallbackAreKept(){
        assertEquals(2,images(parse("<figure><img src='"+LARGE+"'><img src='"+LARGE+"'></figure>")));
        assertEquals(2,images(parse("<figure><noscript><img src='"+SMALL+"'></noscript><img src='https://pic1.zhimg.com/v2-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa_r.jpg'></figure>")));
    }
    @Test public void rotatingLetterCdnAndNumberedCdnShareAnImageFamily()throws Exception{
        String fallback=SMALL.replace("picx.zhimg.com","pica.zhimg.com");
        assertEquals(1,images(parse("<figure><noscript><img src='"+fallback+"'></noscript><img src='"+LARGE+"'></figure>")));
        assertEquals(1,images(stored(new Block("image",fallback),new Block("image",LARGE))));
    }
    private Document stored(Block... blocks)throws Exception{
        Document doc=new Document();doc.url=URL;Section section=new Section("answer","回答",true);section.blocks.addAll(Arrays.asList(blocks));doc.sections.add(section);doc.blocks.addAll(section.blocks);
        return ReadingHistory.decode(ReadingHistory.encode(doc));
    }
    @Test public void oldCachedAdjacentSizeCopiesAreRepairedOnRestore()throws Exception{
        Document doc=stored(new Block("image",SMALL),new Block("image",LARGE));assertEquals(1,images(doc));assertEquals(LARGE,doc.sections.get(0).blocks.get(0).value);
        assertEquals(2,images(stored(new Block("image",LARGE),new Block("image",LARGE))));
        assertEquals(2,images(stored(new Block("image",SMALL),new Block("text","另一段内容"),new Block("image",LARGE))));
    }
    @Test public void separateAnswersAndUntrustedImageHostsAreNotMerged()throws Exception{
        Document doc=new Document();doc.url=URL;for(String image:List.of(SMALL,LARGE)){Section s=new Section("answer-"+doc.sections.size(),"回答",true);s.blocks.add(new Block("image",image));doc.sections.add(s);doc.blocks.addAll(s.blocks);}
        assertEquals(2,images(ReadingHistory.decode(ReadingHistory.encode(doc))));
        assertEquals(2,images(stored(new Block("image",SMALL.replace("picx.zhimg.com","picx.zhimg.com.evil.test")),new Block("image",LARGE))));
    }
}
