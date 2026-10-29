package app.quietreader;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;
import static app.quietreader.discovery.ContentLanguage.Body.*;
public class BodyLanguageEvidenceTest {
    private Document prose(String text){Document d=new Document();d.title="这是中文标题";d.blocks.add(new Block("text",text));return d;}
    @Test public void bodyOverridesHeadingWithoutUsingNotice(){
        Document d=prose("This report describes the latest scientific findings and their possible implications. ".repeat(8));
        assertEquals(NON_CHINESE,BodyLanguageEvidence.classify(d));
        d.loginRequired=true;assertEquals(UNKNOWN,BodyLanguageEvidence.classify(d));
        d.loginRequired=false;d.sourceUnavailable=true;assertEquals(UNKNOWN,BodyLanguageEvidence.classify(d));
    }
    @Test public void chineseProseCanContainEnglishTechnicalTerms(){assertEquals(CHINESE,BodyLanguageEvidence.classify(prose("这项研究介绍了手机系统的具体变化，以及本地模型运行时的资源管理方法 Android ONNX。".repeat(10))));}
    @Test public void shortMixedAndNonProseEvidenceRemainUnknown(){
        assertEquals(UNKNOWN,BodyLanguageEvidence.classify(prose("简短内容")));
        Document d=new Document();d.blocks.add(new Block("image","https://example.org/".repeat(100)));d.blocks.add(new Block("code","public static void main".repeat(100)));assertEquals(UNKNOWN,BodyLanguageEvidence.classify(d));
    }
    @Test public void duplicateBlockReferencesDoNotManufactureEnoughEvidence(){
        Document d=prose("This is only a short preview.");for(int n=0;n<100;n++)d.blocks.add(d.blocks.get(0));assertEquals(UNKNOWN,BodyLanguageEvidence.classify(d));
    }
}
