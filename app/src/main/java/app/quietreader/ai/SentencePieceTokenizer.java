package app.quietreader.ai;

import java.io.*;

/** Official SentencePiece 0.2.1 compiled with NDK r28; one worker owns the instance. */
public final class SentencePieceTokenizer implements AutoCloseable {
    static {System.loadLibrary("quietreader_sentencepiece");}
    private long handle;
    public SentencePieceTokenizer(File vocabulary)throws IOException{handle=nativeOpen(vocabulary.getPath());if(handle==0)throw new IOException("SentencePiece failed to initialize");}
    public int[] encode(String text)throws IOException{if(handle==0)throw new IllegalStateException("Tokenizer is closed");return nativeEncode(handle,text);}
    @Override public void close(){if(handle!=0){nativeClose(handle);handle=0;}}
    private static native long nativeOpen(String path)throws IOException;
    private static native int[] nativeEncode(long handle,String text)throws IOException;
    private static native void nativeClose(long handle);
}
