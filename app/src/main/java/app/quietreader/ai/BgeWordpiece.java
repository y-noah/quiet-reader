package app.quietreader.ai;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** BGE-small-zh's cased BERT tokenizer. Verified against the pinned HF tokenizer.
 * In particular a partially matched unknown word is ONE [UNK], not partial pieces.
 */
public final class BgeWordpiece {
    private final Map<String,Integer> vocabulary=new HashMap<>();
    private static final Pattern SPECIAL=Pattern.compile("\\[UNK\\]|\\[SEP\\]|\\[PAD\\]|\\[CLS\\]|\\[MASK\\]");
    public BgeWordpiece(File vocab)throws IOException{
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(vocab),StandardCharsets.UTF_8))){
            String line;int id=0;while((line=reader.readLine())!=null)vocabulary.put(line,id++);
        }
        for(String special:new String[]{"[CLS]","[SEP]","[PAD]","[UNK]","[MASK]"})if(!vocabulary.containsKey(special))throw new IOException("Invalid vocabulary");
    }
    public long[] encode(String text){
        List<Long> ids=new ArrayList<>();ids.add((long)vocabulary.get("[CLS]"));
        Matcher matcher=SPECIAL.matcher(text);int offset=0;
        while(matcher.find()){plain(text.substring(offset,matcher.start()),ids);ids.add((long)vocabulary.get(matcher.group()));offset=matcher.end();}
        plain(text.substring(offset),ids);ids.add((long)vocabulary.get("[SEP]"));
        long[] out=new long[ids.size()];for(int i=0;i<out.length;i++)out[i]=ids.get(i);return out;
    }
    private void plain(String text,List<Long> ids){
        StringBuilder cleaned=new StringBuilder();
        text.codePoints().forEach(cp->{
            int type=Character.getType(cp);
            boolean whitespace=cp==' '||cp=='\t'||cp=='\n'||cp=='\r'||type==Character.SPACE_SEPARATOR;
            if(cp==0||cp==0xfffd||!whitespace&&(type==Character.CONTROL||type==Character.FORMAT||type==Character.PRIVATE_USE||type==Character.UNASSIGNED||type==Character.SURROGATE))return;
            if(whitespace)cleaned.append(' ');
            else if(chinese(cp))cleaned.append(' ').appendCodePoint(cp).append(' ');
            else cleaned.appendCodePoint(cp);
        });
        String normalized=Normalizer.normalize(cleaned,Normalizer.Form.NFC);
        StringBuilder word=new StringBuilder();
        normalized.codePoints().forEach(cp->{
            if(Character.isWhitespace(cp)||Character.isSpaceChar(cp)){flush(word,ids);}
            else if(punctuation(cp)){flush(word,ids);pieces(new String(Character.toChars(cp)),ids);}
            else word.appendCodePoint(cp);
        });flush(word,ids);
    }
    private void flush(StringBuilder word,List<Long> ids){if(word.length()>0){pieces(word.toString(),ids);word.setLength(0);}}
    private void pieces(String word,List<Long> ids){
        int[] cps=word.codePoints().toArray();long unknown=vocabulary.get("[UNK]");
        if(cps.length>100){ids.add(unknown);return;}
        List<Long> matched=new ArrayList<>();int start=0;
        while(start<cps.length){int end=cps.length;Integer found=null;
            while(end>start){String piece=(start==0?"":"##")+new String(cps,start,end-start);found=vocabulary.get(piece);if(found!=null)break;end--;}
            if(found==null){ids.add(unknown);return;}matched.add(found.longValue());start=end;
        }
        ids.addAll(matched);
    }
    private static boolean punctuation(int cp){
        if(cp>=33&&cp<=47||cp>=58&&cp<=64||cp>=91&&cp<=96||cp>=123&&cp<=126)return true;
        int t=Character.getType(cp);return t==Character.CONNECTOR_PUNCTUATION||t==Character.DASH_PUNCTUATION||t==Character.START_PUNCTUATION||t==Character.END_PUNCTUATION||t==Character.INITIAL_QUOTE_PUNCTUATION||t==Character.FINAL_QUOTE_PUNCTUATION||t==Character.OTHER_PUNCTUATION;
    }
    private static boolean chinese(int c){return c>=0x4e00&&c<=0x9fff||c>=0x3400&&c<=0x4dbf||c>=0x20000&&c<=0x2a6df||c>=0x2a700&&c<=0x2b73f||c>=0x2b740&&c<=0x2b81f||c>=0x2b820&&c<=0x2ceaf||c>=0xf900&&c<=0xfaff||c>=0x2f800&&c<=0x2fa1f;}
}
