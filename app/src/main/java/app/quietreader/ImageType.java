package app.quietreader;

/** Accept raster signatures only, not HTML error responses or active SVG documents. */
public final class ImageType {
    private ImageType() {}
    public static String detect(byte[] b) {
        if(b.length<12)return null;
        if((b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255)return "image/jpeg";
        if((b[0]&255)==137&&b[1]==80&&b[2]==78&&b[3]==71&&b[4]==13&&b[5]==10&&b[6]==26&&b[7]==10)return "image/png";
        String head=new String(b,0,12,java.nio.charset.StandardCharsets.ISO_8859_1);
        if(head.startsWith("GIF87a")||head.startsWith("GIF89a"))return "image/gif";
        if(head.startsWith("RIFF")&&head.substring(8).equals("WEBP"))return "image/webp";
        if(head.substring(4).equals("ftypavif")||head.substring(4).equals("ftypavis"))return "image/avif";
        return null;
    }
}
