package app.quietreader.discovery;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Remove presentation/question wrappers only, never infer interchangeable facts. */
final class HeadlineIdentity {
    private static final Pattern QUESTION=Pattern.compile("^(?:如何看待|怎么看待|如何评价|如何理解)\\s*");
    private static final Pattern LABEL=Pattern.compile("^(?:热议|热搜|话题)\\s*[:：]\\s*");
    private static final Pattern OPINION=Pattern.compile("[？?，,；;]\\s*(?:对此)?(?:你怎么看(?:待)?|你如何评价|有何看法|有哪些值得关注的信息|有哪些信息值得关注)[？?！!。\\s]*$");
    private static final Pattern PUNCTUATION=Pattern.compile("[\\p{P}\\p{Z}\\s]+");
    private HeadlineIdentity(){}
    static String literal(String title){return PUNCTUATION.matcher(Normalizer.normalize(title,Normalizer.Form.NFKC)).replaceAll("").toLowerCase(Locale.ROOT);}
    private static String facts(String title){
        int[] points=Normalizer.normalize(title,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).codePoints().toArray();StringBuilder out=new StringBuilder();
        for(int n=0;n<points.length;n++){
            int cp=points[n],type=Character.getType(cp);if(Character.isWhitespace(cp)||Character.isSpaceChar(cp))continue;
            boolean punctuation=type==Character.CONNECTOR_PUNCTUATION||type==Character.DASH_PUNCTUATION||type==Character.START_PUNCTUATION||type==Character.END_PUNCTUATION||type==Character.INITIAL_QUOTE_PUNCTUATION||type==Character.FINAL_QUOTE_PUNCTUATION||type==Character.OTHER_PUNCTUATION;
            boolean before=n>0&&Character.isDigit(points[n-1]),after=n+1<points.length&&Character.isDigit(points[n+1]);
            boolean numeric=before&&after||after&&(cp=='.'||cp=='-')||before&&(cp=='%'||cp==0x2030||cp==0x2031);
            if(!punctuation||numeric)out.appendCodePoint(cp);
        }
        return out.toString();
    }
    static boolean legacySafe(String title){return facts(title).equals(literal(title));}
    static boolean hasNumbers(String title){return title.codePoints().anyMatch(Character::isDigit);}
    static String core(String title){
        String text=Normalizer.normalize(title,Normalizer.Form.NFKC).trim();
        text=LABEL.matcher(text).replaceFirst("");
        boolean question=QUESTION.matcher(text).find();text=QUESTION.matcher(text).replaceFirst("");
        text=OPINION.matcher(text).replaceFirst("");
        String normalized=facts(text);
        if(question&&normalized.endsWith("一事"))normalized=normalized.substring(0,normalized.length()-2);
        // Never turn a short/generic question into a shared event identity.
        return normalized.codePointCount(0,normalized.length())>=10?normalized:facts(title);
    }
}
