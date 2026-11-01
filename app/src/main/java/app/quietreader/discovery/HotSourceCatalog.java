package app.quietreader.discovery;

import app.quietreader.Models.Source;
import java.util.*;

/** Eligibility is a source contract, not a platform preference or popularity bonus. */
public final class HotSourceCatalog {
    private HotSourceCatalog(){}
    public static final List<Source> HOT=Collections.unmodifiableList(Arrays.asList(Source.ZHIHU,Source.WEIBO,Source.HUPU,Source.CLS,Source.V2EX,Source.ITHOME,Source.THEPAPER,Source.BBC,Source.NYT,Source.BAIDU,Source.TIEBA,Source.BILIBILI,Source.DOUYIN));
    /** Text-oriented popular feeds are the initial supply. Optional video-centric
     * boards remain available, but must not cost browser work before opt-in. */
    public static final List<Source> TEXT_HOT=Collections.unmodifiableList(Arrays.asList(Source.ZHIHU,Source.WEIBO,Source.HUPU,Source.CLS,Source.V2EX,Source.ITHOME,Source.THEPAPER,Source.BBC,Source.NYT,Source.BAIDU,Source.TIEBA));
    /** Candidates are loaded only from enabled sources. Video boards require explicit opt-in. */
    public static boolean contentEligible(HotCandidate candidate){return !candidate.video||optionalVideo(candidate);}
    public static boolean optionalVideo(HotCandidate candidate){return candidate.video&&(candidate.source==Source.BILIBILI||candidate.source==Source.DOUYIN);}
    public static boolean defaultEnabled(Source source){return TEXT_HOT.contains(source);}
    public static boolean eligible(Source source){return HOT.contains(source);}
    public static boolean hasChineseTitle(String title){return title!=null&&title.codePoints().anyMatch(cp->Character.UnicodeScript.of(cp)==Character.UnicodeScript.HAN);}
}
