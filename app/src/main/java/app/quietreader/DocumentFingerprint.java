package app.quietreader;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static app.quietreader.Models.*;

/** Compares normalized reading content, not volatile source markup or just block counts. */
final class DocumentFingerprint {
    private final MessageDigest digest;
    private DocumentFingerprint() {
        try { digest=MessageDigest.getInstance("SHA-256"); }
        catch(java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    static String of(Document document) {
        DocumentFingerprint f=new DocumentFingerprint();
        f.text(document.title);f.text(document.byline);f.text(document.url);f.text(document.notice);
        f.number(document.unsupportedVideo?1:0);
        f.number(document.filteredVideo?1:0);f.number(document.filteredVideos);
        for(String id:new java.util.TreeSet<>(document.filteredSectionIds))f.text(id);
        f.text(document.nextUrl);f.text(document.moreStatus);f.blocks(document.blocks);
        f.number(document.sections.size());
        for(Section section:document.sections) {
            f.text(section.id);f.text(section.label);f.text(section.continuationUrl);f.number(section.answer?1:0);f.blocks(section.blocks);
        }
        f.number(document.related.size());
        for(Item item:document.related) {
            f.text(item.source.name());f.text(item.title);f.text(item.url);f.text(item.detail);
        }
        StringBuilder hex=new StringBuilder(64);
        for(byte b:f.digest.digest()) { hex.append(Character.forDigit((b>>>4)&15,16));hex.append(Character.forDigit(b&15,16)); }
        return hex.toString();
    }
    private void blocks(java.util.List<Block> blocks) {
        number(blocks.size());
        for(Block block:blocks) {
            text(block.type);text(block.value);number(block.inlineImages.size());
            for(InlineImage image:block.inlineImages) {
                number(image.start);number(image.end);text(image.url);text(image.alt);
            }
            number(block.inlineLinks.size());
            for(InlineLink link:block.inlineLinks){number(link.start);number(link.end);text(link.url);}
        }
    }
    private void number(int value) {
        digest.update((byte)(value>>>24));digest.update((byte)(value>>>16));digest.update((byte)(value>>>8));digest.update((byte)value);
    }
    private void text(String value) {
        if(value==null) { number(-1);return; }
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);number(bytes.length);digest.update(bytes);
    }
}
