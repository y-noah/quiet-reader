package app.quietreader;

/** Anchor offsets may outlive removed/folded content. Never scroll outside the new page. */
final class ReadingPosition {
    private ReadingPosition() {}
    static int clamp(int requested,int contentCss,float scale,int viewportPx){
        int maximum=Math.max(0,Math.round(contentCss*scale)-viewportPx);
        return Math.max(0,Math.min(requested,maximum));
    }
}
