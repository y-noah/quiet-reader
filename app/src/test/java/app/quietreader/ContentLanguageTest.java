package app.quietreader;
import app.quietreader.discovery.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class ContentLanguageTest {
    @Test public void preservesSimplifiedTraditionalAndTechnicalNames(){
        for(String title:new String[]{"中国空间站实验","臺灣半導體研究","React 19 和 Android 16 有哪些变化","如何使用 git checkout <branch>","人工智能AI新進展"})assertTrue(title,ContentLanguage.eligible(title,"",ContentLanguage.Body.UNKNOWN));
    }
    @Test public void shortBrandNeedsEvidenceRatherThanCharacterRatio(){
        assertFalse(ContentLanguage.eligible("GPT-5","",ContentLanguage.Body.UNKNOWN));
        assertTrue(ContentLanguage.eligible("GPT-5","这个模型的中文表现如何？",ContentLanguage.Body.UNKNOWN));
        assertEquals(ContentLanguage.Text.UNCERTAIN,ContentLanguage.text("2026 🎉"));
    }
    @Test public void knownNonChineseBodyOverridesChineseHeading(){
        assertFalse(ContentLanguage.eligible("美国航天新发现","这是中文摘要",ContentLanguage.Body.NON_CHINESE));
        assertFalse(ContentLanguage.eligible("New study reveals surprising discoveries","",ContentLanguage.Body.UNKNOWN));
        assertFalse(ContentLanguage.eligible("東京の新しい研究","",ContentLanguage.Body.UNKNOWN));
        assertFalse(ContentLanguage.eligible("한국의 새로운 연구","",ContentLanguage.Body.UNKNOWN));
        assertFalse(ContentLanguage.eligible("This new study discusses the future of artificial intelligence in 中国","",ContentLanguage.Body.UNKNOWN));
    }
    @Test public void evidenceSurvivesSerializationAndLegacyRemainsUnknown()throws Exception{
        HotCandidate c=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/123","中文标题","","",false,1,null,1,ContentLanguage.Body.NON_CHINESE);
        assertEquals(ContentLanguage.Body.NON_CHINESE,HotCandidate.fromJson(c.json()).bodyLanguage);
        org.json.JSONObject legacy=c.json();legacy.remove("bodyLanguage");assertEquals(ContentLanguage.Body.UNKNOWN,HotCandidate.fromJson(legacy).bodyLanguage);
    }
    @Test public void bothSortModesExcludeNonChineseCandidatesBeforeSemanticRanking(){
        HotCandidate chinese=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/1","中文技术讨论","","",false,3,null,1);
        HotCandidate english=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/2","A new scientific discovery in space","","",false,1,100L,1);
        HotCandidate translatedHeading=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/3","翻译标题","","",false,2,200L,1,ContentLanguage.Body.NON_CHINESE);
        for(boolean newest:new boolean[]{true,false}){
            java.util.List<FeedStore.Row> rows=CandidateOrder.arrange(java.util.Arrays.asList(chinese,english,translatedHeading),new org.json.JSONArray(),newest);
            assertEquals(1,rows.size());assertEquals(chinese.id,rows.get(0).candidate.id);
        }
    }
}
