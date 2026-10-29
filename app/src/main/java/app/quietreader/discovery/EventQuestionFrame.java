package app.quietreader.discovery;

import java.util.regex.*;

/** Model input only. Original headlines remain the authority for every guard. */
final class EventQuestionFrame {
    static final String VERSION="question-frame-v2-generic-tail";
    private static final Pattern LEADING=Pattern.compile("^(?:如何看待|怎么看待|如何评价|如何理解)\\s*");
    private static final String GENERIC="(?:有哪些亮点值得关注|你看好其市场前景吗)[？?]";
    private static final Pattern TAIL=Pattern.compile("[，,]\\s*"+GENERIC+"(?:\\s*"+GENERIC+")*\\s*$");
    private EventQuestionFrame(){}
    /** Whole generic questions only. Never cut at an arbitrary question mark,
     * comma or keyword; unknown and fact-bearing suffixes stay intact. */
    static String withoutGenericTail(String original){
        Matcher tail=TAIL.matcher(original);
        if(!tail.find())return original;
        String result=original.substring(0,tail.start()).stripTrailing();
        return result.isBlank()?original:result;
    }
    static String title(String original){
        String result=withoutGenericTail(original);
        Matcher leading=LEADING.matcher(result);
        if(!leading.find())return result;
        result=result.substring(leading.end());
        if(result.endsWith("？")||result.endsWith("?"))result=result.substring(0,result.length()-1).stripTrailing();
        // Never turn a title consisting only of framing into an empty input.
        return result.isBlank()?original:result;
    }
}
