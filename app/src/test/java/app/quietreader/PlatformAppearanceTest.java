package app.quietreader;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class PlatformAppearanceTest {
    @Test public void allVisibleSourcesUseSameDarkReaderWithoutPhotoInversion(){
        for(Source source:Source.values())if(source.visible()){
            Document d=new Document();d.title=source.label;d.url=source.login;
            d.blocks.add(new Block("text","合成正文"));
            d.blocks.add(new Block("image","https://example.com/photo.jpg"));
            String html=ReaderHtml.render(d,source,19,true,Collections.emptySet(),false);
            assertTrue(source.name(),html.contains("color-scheme:dark;--bg:#1f2025;--card:#282a31;--ink:#cdcfd5"));
            assertFalse(source.name(),html.contains("color-scheme:light"));
            assertFalse(source.name(),html.contains("filter:invert"));
            assertFalse(source.name(),html.contains("filter: invert"));
        }
    }
    @Test public void lightModeRemainsAvailableForEveryVisibleSource(){
        for(Source source:Source.values())if(source.visible()){
            Document d=new Document();d.title=source.label;d.url=source.login;
            d.blocks.add(new Block("text","合成正文"));
            String html=ReaderHtml.render(d,source,19,false,Collections.emptySet(),false);
            assertTrue(source.name(),html.contains("color-scheme:light;--bg:#fafafa;--card:#f1f2f4;--ink:#30323a"));
            assertFalse(source.name(),html.contains("color-scheme:dark"));
        }
    }
}
