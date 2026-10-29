package app.quietreader;

import static app.quietreader.Models.*;

/** Eligibility only: Activity owns scroll intent, request concurrency and append position. */
final class ReaderContinuation {
    private ReaderContinuation(){}
    static boolean question(Source source,Document doc){return source==Source.ZHIHU&&UrlPolicy.belongs(source,doc.url)&&doc.url.matches("https://[^/]*zhihu.com/question/\\d+.*");}
    static boolean automatic(Source source,Document doc){
        if(doc.sourceUnavailable||doc.loginRequired||doc.filteredVideo||!doc.moreStatus.isEmpty()&&!doc.moreStatus.equals("loaded"))return false;
        return question(source,doc)||TiebaReplies.supports(source,doc)||DiscussionPages.usesStream(source,doc)&&DiscussionPages.next(source,doc);
    }
}
