package app.quietreader.discovery;

/** Metadata evidence only: a Chinese heading never certifies the article body.
 * No network/body fetch or translation is performed for ranking. */
public final class ContentLanguage {
    public static final String VERSION="zh-metadata-v1";
    public enum Body { UNKNOWN, CHINESE, NON_CHINESE }
    public enum Text { CHINESE, NON_CHINESE, UNCERTAIN }
    private ContentLanguage(){}
    public static Text text(String value){
        int han=0,otherLetters=0,kanaHangul=0;
        for(int cp:(value==null?"":value).codePoints().toArray()){
            Character.UnicodeScript script=Character.UnicodeScript.of(cp);
            if(script==Character.UnicodeScript.HAN)han++;
            else if(script==Character.UnicodeScript.HIRAGANA||script==Character.UnicodeScript.KATAKANA||script==Character.UnicodeScript.HANGUL)kanaHangul++;
            else if(Character.isLetter(cp))otherLetters++;
        }
        // Mixed Japanese/Korean evidence is uncertain, not confidently Chinese.
        if(kanaHangul>0)return han>0?Text.UNCERTAIN:Text.NON_CHINESE;
        if(han>0){
            int latinWords=0;java.util.regex.Matcher words=java.util.regex.Pattern.compile("[A-Za-z]+(?:['’-][A-Za-z]+)*").matcher(value==null?"":value);while(words.find())latinWords++;
            if(latinWords>6&&latinWords>han)return Text.UNCERTAIN;
            return Text.CHINESE;
        }
        return otherLetters>=12?Text.NON_CHINESE:Text.UNCERTAIN;
    }
    public static boolean eligible(String title,String snippet,Body body){
        if(body==Body.NON_CHINESE)return false;
        Text heading=text(title),summary=text(snippet);
        // A short brand/code heading needs supporting Chinese summary evidence.
        return heading==Text.CHINESE||heading==Text.UNCERTAIN&&summary==Text.CHINESE;
    }
}
