package app.quietreader;

import org.junit.Test;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public final class BbcChineseSourceTest {
    private String capturedText(JSONObject block)throws Exception{JSONObject model=block.optJSONObject("model");if(model==null)return "";if("fragment".equals(block.optString("type")))return model.optString("text");StringBuilder out=new StringBuilder();JSONArray children=model.optJSONArray("blocks");if(children!=null)for(int n=0;n<children.length();n++)out.append(capturedText(children.getJSONObject(n)));return out.toString();}
    private JSONObject flourish(JSONObject sample)throws Exception{JSONArray blocks=props(sample).getJSONObject("pageData").getJSONObject("content").getJSONObject("model").getJSONArray("blocks");for(int n=0;n<blocks.length();n++)if("oEmbed".equals(blocks.getJSONObject(n).optString("type")))return blocks.getJSONObject(n).getJSONObject("model").getJSONObject("oembed");throw new IllegalStateException("Captured chart missing");}
    @Test public void capturedFlourishChartKeepsEverySurroundingParagraphAndAnExplicitChartLink()throws Exception{
        JSONObject sample=state("bbc-flourish-20261028");String url="https://www.bbc.com"+props(sample).getString("id");Document doc=SourceParser.article(Source.BBC,html(sample),url);
        assertTrue(doc.hasContent());assertFalse(doc.sourceUnavailable);String prose=doc.blocks.stream().map(b->b.value).collect(java.util.stream.Collectors.joining("\n"));JSONArray blocks=props(sample).getJSONObject("pageData").getJSONObject("content").getJSONObject("model").getJSONArray("blocks");int paragraphs=0;
        for(int n=0;n<blocks.length();n++){JSONObject block=blocks.getJSONObject(n);if(!List.of("text","subheadline").contains(block.optString("type")))continue;JSONArray children=block.getJSONObject("model").optJSONArray("blocks");if(children!=null)for(int p=0;p<children.length();p++){String expected=capturedText(children.getJSONObject(p));if(!expected.trim().isEmpty()){assertTrue("source paragraph "+paragraphs,prose.contains(expected));paragraphs++;}}}
        assertTrue(paragraphs>20);assertTrue(doc.blocks.stream().flatMap(b->b.inlineLinks.stream()).anyMatch(link->link.url.equals("https://public.flourish.studio/visualisation/30709667")));
        String rendered=ReaderHtml.render(doc,Source.BBC,19,false,Set.of(doc.sections.get(0).id),false);assertTrue(rendered.contains("查看互动图表"));assertFalse(rendered.contains("<iframe"));assertFalse(rendered.contains("sizeAdjustScript"));assertFalse(rendered.contains("window.addEventListener"));
    }
    @Test public void chartSourceOrProviderMismatchCannotSilentlyDropTheEmbed()throws Exception{
        for(String invalid:List.of("javascript:alert(1)","https://public.flourish.studio.evil.test/visualisation/30709667","https://public.flourish.studio/visualisation/30709667?other=1")){JSONObject sample=state("bbc-flourish-20261028");flourish(sample).put("source",invalid);assertTrue(SourceParser.article(Source.BBC,html(sample),"https://www.bbc.com"+props(sample).getString("id")).sourceUnavailable);}
        JSONObject sample=state("bbc-flourish-20261028");flourish(sample).put("oEmbedType","newUnknownProvider");assertTrue(SourceParser.article(Source.BBC,html(sample),"https://www.bbc.com"+props(sample).getString("id")).sourceUnavailable);
    }
    @Test public void opaqueChartMarkupNeverRunsOrSuppliesAnAlternativeLink()throws Exception{
        JSONObject sample=state("bbc-flourish-20261028");flourish(sample).put("html","<script>bad()</script><iframe src='https://evil.test/frame'></iframe>").put("iFrameSrc","https://evil.test/frame");String url="https://www.bbc.com"+props(sample).getString("id");Document doc=SourceParser.article(Source.BBC,html(sample),url);assertTrue(doc.hasContent());String rendered=ReaderHtml.render(doc,Source.BBC,19,false,Set.of(doc.sections.get(0).id),false);assertFalse(rendered.contains("evil.test"));assertFalse(rendered.contains("bad()"));assertFalse(rendered.contains("<iframe"));
    }
    @Test public void fiveFurtherTraditionalCapturesRetainEveryParagraph()throws Exception{
        JSONArray records=new JSONObject(resource("release-trad-manifest.json")).getJSONArray("records");
        for(int n=0;n<records.length();n++){
            JSONObject sample=records.getJSONObject(n);Document doc=SourceParser.readingArticle(Source.BBC,html(state("release-trad-"+n)),sample.getString("url"));
            assertTrue("traditional capture "+n,doc.hasContent());assertFalse(doc.sourceUnavailable);
            String all=doc.blocks.stream().map(b->b.value).collect(java.util.stream.Collectors.joining("\n"));
            JSONArray paragraphs=sample.getJSONArray("expectedParagraphs");for(int p=0;p<paragraphs.length();p++)assertTrue("capture "+n+" paragraph "+p,all.contains(paragraphs.getString(p)));
            assertTrue(doc.sections.get(0).id.startsWith("bbc-article-"));
        }
    }
    private String resource(String name)throws Exception{try(java.io.InputStream in=getClass().getResourceAsStream("/bbc-chinese-20261026/"+name)){assertNotNull(in);return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    private String html(JSONObject state){return "<script id=__NEXT_DATA__ type=application/json>"+state+"</script><article>侧栏摘要不应成为正文</article>";}
    private JSONObject state(String name)throws Exception{return new JSONObject(resource(name+".json"));}
    private JSONObject props(JSONObject state)throws Exception{return state.getJSONObject("props").getJSONObject("pageProps");}
    @Test public void capturedArticleRevisionTimeIsBoundToCanonicalAndTwoMatchingFields()throws Exception{
        JSONObject sample=state("bbc-article-0"),metadata=props(sample).getJSONObject("pageData").getJSONObject("metadata");String url="https://www.bbc.com"+props(sample).getString("id");
        Document doc=SourceParser.article(Source.BBC,html(sample),url);assertEquals(metadata.getLong("lastPublished"),doc.sourceModifiedAt);assertEquals(ArticleUpdateEvidence.VERSION,doc.sourceUpdateEvidence);
        metadata.getJSONObject("atiAnalytics").put("timeUpdated","2024-01-01T00:00:00Z");doc=SourceParser.article(Source.BBC,html(sample),url);assertTrue(doc.hasContent());assertEquals(0,doc.sourceModifiedAt);
    }
    @Test public void revisionMetadataMismatchNeverDropsOtherwiseReadableBbcBody()throws Exception{
        for(String scenario:List.of("canonical","type","future")){JSONObject sample=state("bbc-article-0"),metadata=props(sample).getJSONObject("pageData").getJSONObject("metadata");String url="https://www.bbc.com"+props(sample).getString("id");
            if(scenario.equals("canonical"))metadata.getJSONObject("locators").put("canonicalUrl","https://www.bbc.com/zhongwen/articles/c11111111111/simp");
            else if(scenario.equals("type"))metadata.put("lastPublished",Long.toString(metadata.getLong("lastPublished")));else metadata.put("lastPublished",Long.MAX_VALUE);
            Document doc=SourceParser.article(Source.BBC,html(sample),url);assertTrue(scenario,doc.hasContent());assertFalse(doc.sourceUnavailable);assertEquals(scenario,0,doc.sourceModifiedAt);
        }
    }
    @Test public void actualMostReadRetainsOfficialOrderChineseAndPublication()throws Exception{
        List<Item> rows=SourceParser.list(Source.BBC,html(state("bbc-simp-popular")));
        assertEquals(10,rows.size());assertTrue(app.quietreader.discovery.HotSourceCatalog.defaultEnabled(Source.BBC));
        JSONArray items=props(state("bbc-simp-popular")).getJSONObject("pageData").getJSONArray("curations").getJSONObject(0).getJSONObject("mostRead").getJSONArray("items");
        for(int n=0;n<rows.size();n++){Item row=rows.get(n);assertEquals(items.getJSONObject(n).getString("href"),row.url);assertEquals(items.getJSONObject(n).getString("title"),row.title);assertNotNull(row.publishedAt);assertEquals(Source.BBC,ReaderLinks.readerSource(row.url));assertFalse(row.video);}
    }
    @Test public void fiveActualPublicArticlesRetainEveryTextParagraphIncludingTail()throws Exception{
        JSONArray captures=new JSONObject(resource("capture-manifest.json")).getJSONArray("records");
        for(int n=1;n<captures.length();n++){
            JSONObject capture=captures.getJSONObject(n);Document doc=SourceParser.article(Source.BBC,html(state(capture.getString("name"))),capture.getString("articleUrl"));
            assertTrue(capture.getString("name"),doc.hasContent());assertFalse(doc.sourceUnavailable);assertFalse(doc.loginRequired);assertFalse(doc.title.isEmpty());if(!capture.getString("name").equals("bbc-article-4"))assertFalse(capture.getString("name")+" author",doc.byline.isEmpty());
            String all=doc.blocks.stream().map(b->b.value).collect(java.util.stream.Collectors.joining("\n"));JSONArray expected=capture.getJSONArray("expectedParagraphs");assertTrue(expected.length()>5);
            for(int i=0;i<expected.length();i++)assertTrue(capture.getString("name")+" paragraph "+i,all.contains(expected.getString(i)));
            assertFalse(all.contains("侧栏摘要不应成为正文"));assertEquals(1,doc.sections.size());assertTrue(doc.blocks.stream().anyMatch(b->b.type.equals("image")&&b.value.startsWith("https://ichef.bbci.co.uk/")));
            String rendered=ReaderHtml.render(doc,Source.BBC,19);assertFalse(rendered.contains("<script"));assertTrue(rendered.contains(ReaderHtml.escape(doc.title)));
        }
    }
    @Test public void wrongServiceVariantPageTypeOrMissingHotCannotBecomeABoard()throws Exception{
        for(String field:List.of("service","variant","pageType")){
            JSONObject state=state("bbc-simp-popular");props(state).put(field,"other");try{SourceParser.list(Source.BBC,html(state));fail(field);}catch(IllegalStateException expected){}
        }
        JSONObject state=state("bbc-simp-popular");props(state).getJSONObject("pageData").put("curations",new JSONArray());try{SourceParser.list(Source.BBC,html(state));fail();}catch(IllegalStateException expected){}
    }
    @Test public void boardRejectsForeignLinksEnglishAndMismatchedIdentities()throws Exception{
        JSONObject state=state("bbc-simp-popular");JSONArray items=props(state).getJSONObject("pageData").getJSONArray("curations").getJSONObject(0).getJSONObject("mostRead").getJSONArray("items");
        items.getJSONObject(0).put("href","https://www.bbc.com.evil.test/zhongwen/articles/ckx2q587wvrqo/simp");items.getJSONObject(1).put("id","wrong");items.getJSONObject(2).put("href","https://www.bbc.com/news/articles/ckx2q587wvrqo");
        assertEquals(7,SourceParser.list(Source.BBC,html(state)).size());
    }
    @Test public void identityMismatchAndEmptyBodyNeverUseSummaryOrPageFallback()throws Exception{
        JSONObject original=state("bbc-article-0");String url="https://www.bbc.com"+props(original).getString("id");
        for(String field:List.of("id","service","variant","pageType")){
            JSONObject state=state("bbc-article-0");props(state).put(field,"other");Document doc=SourceParser.article(Source.BBC,html(state),url);assertFalse(field,doc.hasContent());assertTrue(doc.sourceUnavailable);
        }
        JSONObject state=state("bbc-article-0");props(state).getJSONObject("pageData").getJSONObject("content").getJSONObject("model").put("blocks",new JSONArray());assertFalse(SourceParser.article(Source.BBC,html(state),url).hasContent());
        assertFalse(SourceParser.article(Source.BBC,"<article>仅有摘要</article>",url).hasContent());
    }
    @Test public void ChineseArticleBoundaryDoesNotClaimOtherBbcPages(){
        for(String url:List.of("https://www.bbc.com/","https://www.bbc.com/zhongwen/simp","https://www.bbc.com/news/articles/cm5ynx99yxzwo","https://www.bbc.com/zhongwen/articles/cm5ynx99yxzwo/simp/other","https://www.bbc.com.evil.test/zhongwen/articles/cm5ynx99yxzwo/simp"))assertNull(ReaderLinks.readerSource(url));
    }
    @Test public void textArticleKeepsOpaqueVideoAttachmentWithoutFetchingMedia()throws Exception{
        JSONObject state=state("bbc-article-2");Document doc=SourceParser.article(Source.BBC,html(state),"https://www.bbc.com"+props(state).getString("id"));
        assertTrue(doc.hasContent());assertFalse(doc.videoTopic);assertFalse(doc.filteredVideo);assertTrue(doc.unsupportedVideo);assertEquals(1,doc.videos.size());
        assertEquals("",doc.videos.get(0).url);assertTrue(doc.videos.get(0).poster.startsWith("https://ichef.bbci.co.uk/"));assertTrue(doc.blocks.stream().anyMatch(b->b.type.equals("video")));
        Set<String> expanded=new HashSet<>();ReaderHtml.expandPrimary(doc,expanded);
        String rendered=ReaderHtml.render(doc,Source.BBC,19,false,expanded,false);assertTrue(rendered.contains("来源页视频"));assertTrue(rendered.contains(ReaderHtml.escape(doc.videos.get(0).poster)));assertFalse(rendered.contains("<video"));
    }
    @Test public void sameOrderingAndExplicitPlatformControlsApplyToBbc()throws Exception{
        app.quietreader.discovery.HotCandidate bbc=new app.quietreader.discovery.HotCandidate(Source.BBC,"https://www.bbc.com/zhongwen/articles/cm5ynx99yxzwo/simp","观鸟与大脑健康研究","","",false,8,null,1);
        app.quietreader.discovery.HotCandidate domestic=new app.quietreader.discovery.HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/123456","本地模型的设备耗电测试","","",false,1,null,1);
        List<app.quietreader.discovery.FeedStore.Row> rows=app.quietreader.discovery.CandidateOrder.arrange(List.of(bbc,domestic),new JSONArray(),false);
        assertEquals(2,rows.size());assertEquals(Source.ZHIHU,rows.get(0).candidate.source);
        JSONArray block=new JSONArray().put(new JSONObject().put("action","BLOCK").put("scope","source").put("target","BBC"));
        rows=app.quietreader.discovery.CandidateOrder.arrange(List.of(bbc,domestic),block,false);assertEquals(1,rows.size());assertEquals(Source.ZHIHU,rows.get(0).candidate.source);
        JSONArray more=new JSONArray().put(new JSONObject().put("action","MORE").put("scope","source").put("target","BBC"));
        assertEquals(1,new app.quietreader.discovery.PlatformPreference(more).score(Source.BBC),0);
    }

    @Test public void realTraditionalChineseLinkIsReadableButDoesNotMixIntoSimplifiedBoard()throws Exception{
        JSONObject state=state("bbc-article-trad");String url="https://www.bbc.com"+props(state).getString("id");Document doc=SourceParser.article(Source.BBC,html(state),url);
        assertEquals(Source.BBC,ReaderLinks.readerSource(url));assertTrue(doc.hasContent());assertFalse(doc.sourceUnavailable);assertTrue(doc.title.contains("觀鳥"));
        assertFalse(SourceParser.article(Source.BBC,html(state),url.replace("/trad","/simp")).hasContent());
        JSONObject board=state("bbc-simp-popular");props(board).put("variant","trad");try{SourceParser.list(Source.BBC,html(board));fail();}catch(IllegalStateException expected){}
    }

    @Test public void unknownContentBlockCannotSilentlyTruncateAnOtherwiseReadableArticle()throws Exception{
        JSONObject state=state("bbc-article-0");JSONObject p=props(state);
        p.getJSONObject("pageData").getJSONObject("content").getJSONObject("model").getJSONArray("blocks").put(new JSONObject().put("type","newArticleBodyType").put("model",new JSONObject().put("text","不能被静默丢掉的正文")));
        Document doc=SourceParser.article(Source.BBC,html(state),"https://www.bbc.com"+p.getString("id"));assertFalse(doc.hasContent());assertTrue(doc.sourceUnavailable);assertTrue(doc.sections.isEmpty());
    }

    @Test public void currentPublicArticleWithSocialEmbedAndRelatedCardsRetainsEveryProseParagraph()throws Exception{
        JSONObject sample=state("bbc-social-links-20261027");String url="https://www.bbc.com"+props(sample).getString("id");
        Document doc=SourceParser.article(Source.BBC,html(sample),url);
        assertTrue(doc.hasContent());assertFalse(doc.sourceUnavailable);
        String all=doc.blocks.stream().map(b->b.value).collect(java.util.stream.Collectors.joining("\n"));
        JSONArray paragraphs=new JSONArray(resource("bbc-social-links-paragraphs-20261027.json"));
        for(int n=0;n<paragraphs.length();n++)assertTrue("source paragraph "+n,all.contains(paragraphs.getString(n)));
        assertTrue(doc.blocks.stream().flatMap(b->b.inlineLinks.stream()).anyMatch(l->l.url.equals("https://twitter.com/KMbappe/status/2115045633832931532")));
        assertFalse(all.contains("中国公司就涉及种族歧视洗衣液广告道歉"));
        assertFalse(ReaderHtml.render(doc,Source.BBC,19).contains("<script"));
    }

    @Test public void socialQuotesUsePlainTextAndNeverCopyExecutableEmbedMarkup()throws Exception{
        JSONObject sample=state("bbc-social-links-20261027");JSONArray blocks=props(sample).getJSONObject("pageData").getJSONObject("content").getJSONObject("model").getJSONArray("blocks");
        JSONObject social=null;for(int n=0;n<blocks.length();n++)if(blocks.getJSONObject(n).getString("type").equals("social"))social=blocks.getJSONObject(n);
        assertNotNull(social);
        JSONObject embed=social.getJSONObject("model").getJSONArray("blocks").getJSONObject(0).getJSONObject("model").getJSONArray("blocks").getJSONObject(0).getJSONObject("model").getJSONObject("oembed");
        embed.put("html","<blockquote><p>公开引用文字<script>不能执行</script><iframe src='https://example.org/tracker'></iframe></p></blockquote><script src='https://example.org/widget.js'></script>");
        String url="https://www.bbc.com"+props(sample).getString("id");Document doc=SourceParser.article(Source.BBC,html(sample),url);assertTrue(doc.hasContent());
        String text=doc.blocks.stream().map(b->b.value).collect(java.util.stream.Collectors.joining("\n"));assertTrue("quote retained in parsed document",text.contains("公开引用文字"));
        String rendered=ReaderHtml.render(doc,Source.BBC,19,false,Set.of(doc.sections.get(0).id),false);assertTrue("expanded quote visible",rendered.contains("公开引用文字"));assertFalse(rendered.contains("不能执行"));assertFalse(rendered.contains("example.org"));
        social.getJSONObject("model").put("source","javascript:alert(1)");
        assertTrue(SourceParser.article(Source.BBC,html(sample),url).sourceUnavailable);
    }

}
