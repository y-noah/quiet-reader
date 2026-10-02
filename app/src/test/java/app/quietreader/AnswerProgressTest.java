package app.quietreader;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class AnswerProgressTest {
    private Document answers(String... ids){Document d=new Document();for(String id:ids){Section s=new Section(id,"回答",true);s.blocks.add(new Block("text",id));d.sections.add(s);d.blocks.addAll(s.blocks);}return d;}
    @Test public void replacementIsProgressEvenWhenTotalIsUnchanged(){
        Document before=answers("a","video"),after=AnswerStream.copy(before);
        Document batch=answers("a","new");batch.filteredSectionIds.add("video");AnswerStream.merge(after,batch);
        assertEquals(AnswerStream.answers(before),AnswerStream.answers(after));
        assertTrue(AnswerStream.hasNewAnswers(after,AnswerStream.answerIds(before)));
    }
    @Test public void replacementIsProgressEvenWhenTotalDecreases(){
        Document before=answers("a","v1","v2"),after=AnswerStream.copy(before),batch=answers("new");
        batch.filteredSectionIds.addAll(Arrays.asList("v1","v2"));AnswerStream.merge(after,batch);
        assertEquals(2,AnswerStream.answers(after));assertTrue(AnswerStream.hasNewAnswers(after,AnswerStream.answerIds(before)));
    }
    @Test public void removalWithoutAdditionDoesNotRestartAutomaticLoading(){
        Document before=answers("video"),after=AnswerStream.copy(before),batch=new Document();
        batch.filteredSectionIds.add("video");AnswerStream.merge(after,batch);
        assertFalse(AnswerStream.hasNewAnswers(after,AnswerStream.answerIds(before)));
    }
    @Test public void sameIdentityWithLongerTextIsNotANewAnswer(){
        Document before=answers("a"),batch=answers("a"),after=AnswerStream.copy(before);
        batch.sections.get(0).blocks.add(new Block("text","longer body"));AnswerStream.merge(after,batch);
        assertFalse(AnswerStream.hasNewAnswers(after,AnswerStream.answerIds(before)));
    }
    @Test public void QuestionSupplementDoesNotCountAsNewAnswer(){
        Document before=answers("a"),after=answers("a");after.sections.add(new Section("supplement","问题补充",false));
        assertFalse(AnswerStream.hasNewAnswers(after,AnswerStream.answerIds(before)));
    }
    @Test public void requestSnapshotIsIndependentAndExcludedIdentityNeverCounts(){
        Document d=answers("a");Set<String> before=AnswerStream.answerIds(d);d.sections.add(new Section("v","回答",true));d.filteredSectionIds.add("v");
        assertEquals(Collections.singleton("a"),before);assertFalse(AnswerStream.hasNewAnswers(d,before));
        d.sections.add(new Section("new","回答",true));assertTrue(AnswerStream.hasNewAnswers(d,before));assertFalse(before.contains("new"));
    }
}
