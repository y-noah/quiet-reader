package app.quietreader;

import app.quietreader.Models.*;
import org.json.*;
import org.jsoup.Jsoup;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArticleUpdateEvidenceTest {
    private static final String URL="https://www.ithome.com/0/990/101.htm",TIME="2024-01-02T08:00:00+08:00";
    private static final long NOW=1800000000000L,AT=1704153600000L;
    private String canonical(){return "<link rel=canonical href='"+URL+"'>";}
    private long parse(String html){return ArticleUpdateEvidence.modified(Source.ITHOME,Jsoup.parse(html,URL),URL,NOW);}
    @Test public void canonicalPublisherModifiedTimeIsRevisionEvidence(){assertEquals(AT,parse(canonical()+"<meta property='article:modified_time' content='"+TIME+"'>"));}
    @Test public void fetchDatePublishedAndHeadlinesAreNotModifiedEvidence(){assertEquals(0,parse(canonical()+"<meta property='article:published_time' content='"+TIME+"'><h1>最新更新新闻</h1><time>"+TIME+"</time>"));}
    @Test public void metadataWithoutExactCanonicalFailsClosed(){assertEquals(0,parse("<meta property='article:modified_time' content='"+TIME+"'>"));assertEquals(0,parse(canonical()+"<link rel=canonical href='https://www.ithome.com/0/990/102.htm'><meta property='article:modified_time' content='"+TIME+"'>"));}
    @Test public void exactNewsArticleJsonLdWorksWithoutCanonical(){assertEquals(AT,parse("<script type='application/ld+json'>{\"@type\":\"NewsArticle\",\"url\":\""+URL+"\",\"dateModified\":\""+TIME+"\"}</script>"));}
    @Test public void unrelatedJsonLdCannotTimestampCurrentArticle(){assertEquals(0,parse(canonical()+"<script type='application/ld+json'>{\"@type\":\"NewsArticle\",\"url\":\"https://www.ithome.com/0/990/102.htm\",\"dateModified\":\""+TIME+"\"}</script>"));}
    @Test public void mainEntityAndSchemaGraphAreBoundedSupportedContainers(){assertEquals(AT,parse("<script type='application/ld+json'>{\"@graph\":[{\"@type\":\"Article\",\"mainEntityOfPage\":{\"@id\":\""+URL+"\"},\"dateModified\":\""+TIME+"\"}]}</script>"));assertEquals(0,parse("<script type='application/ld+json'>{\"@graph\":["+"{},".repeat(65)+"{}]}</script>"));}
    @Test public void conflictingAndMalformedValuesRejectEvidence(){assertEquals(0,parse(canonical()+"<meta property='article:modified_time' content='"+TIME+"'><meta itemprop=dateModified content='2024-01-03T00:00:00Z'>"));assertEquals(0,parse(canonical()+"<meta itemprop=dateModified content='nonsense'>"));assertEquals(0,parse(canonical()+"<meta itemprop=dateModified content='"+TIME+"'><script type='application/ld+json'>{not-json</script>"));}
    @Test public void futureAndUnzonedDatesAreNotInferred(){assertEquals(0,ArticleUpdateEvidence.parse("2038-01-01T00:00:00Z",NOW));assertEquals(0,ArticleUpdateEvidence.parse("2024-01-02T08:00:00",NOW));assertEquals(0,ArticleUpdateEvidence.parse("2024-01-02",NOW));assertEquals(0,ArticleUpdateEvidence.parse("0",NOW));}
    @Test public void deeplyNestedJsonIsRejectedBeforeRecursiveParser(){assertEquals(0,parse("<script type='application/ld+json'>"+"[".repeat(10000)+"0"+"]".repeat(10000)+"</script>"));}
    @Test public void multipleRootObjectsCannotHideConflictingMetadata(){assertEquals(0,parse("<script type='application/ld+json'>{\"@type\":\"NewsArticle\",\"url\":\""+URL+"\",\"dateModified\":\""+TIME+"\"} {}</script>"));}
    @Test public void discussionPlatformsAreExcludedEvenWithArticleMetadata(){String url="https://www.v2ex.com/t/990101";assertEquals(0,ArticleUpdateEvidence.modified(Source.V2EX,Jsoup.parse("<link rel=canonical href='"+url+"'><meta property='article:modified_time' content='"+TIME+"'>",url),url,NOW));}
    @Test public void actualUnifiedArticleParserAndCodecPreserveVersionedEvidence()throws Exception{Document doc=SourceParser.article(Source.ITHOME,canonical()+"<meta property='article:modified_time' content='"+TIME+"'><h1>受控中文文章</h1><div id=paragraph class=post_content><p>这是实际统一正文解析器中的合成新闻内容。</p></div>",URL);assertTrue(doc.hasContent());assertEquals(AT,doc.sourceModifiedAt);Document restored=ReadingHistory.decode(ReadingHistory.encode(doc));assertEquals(AT,restored.sourceModifiedAt);assertEquals(ArticleUpdateEvidence.VERSION,restored.sourceUpdateEvidence);}
    @Test public void oldOrMalformedHistoryDoesNotInventSourceTime()throws Exception{JSONObject history=new JSONObject().put("url",URL).put("sections",new JSONArray());assertEquals(0,ReadingHistory.decode(history).sourceModifiedAt);for(Object at:new Object[]{"1704153600000",1704153600000.5,Long.MAX_VALUE}){history.put("sourceUpdateEvidence",new JSONObject().put("version",ArticleUpdateEvidence.VERSION).put("modifiedAt",at));assertEquals(0,ReadingHistory.decode(history).sourceModifiedAt);}history.put("sourceUpdateEvidence",new JSONObject().put("version","future").put("modifiedAt",AT));assertEquals(0,ReadingHistory.decode(history).sourceModifiedAt);}
}
