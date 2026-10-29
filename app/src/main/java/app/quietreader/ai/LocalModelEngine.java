package app.quietreader.ai;

import ai.onnxruntime.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** CPU-only, synchronous worker engine. No network, cookies, UI, or account access.
 * One model at a time. The caller serializes work and owns the lifecycle.
 */
public final class LocalModelEngine implements AutoCloseable {
    private final OrtEnvironment environment=OrtEnvironment.getEnvironment();
    private final File root;
    private final Object runLock=new Object();
    private OrtSession.RunOptions activeRun;
    private volatile boolean cancelled;
    private final ModelVerification verification;
    private OrtSession model;
    private SentencePieceTokenizer spTokenizer;
    private BgeWordpiece bert;
    private String loaded="";
    private boolean closed;
    private static final Pattern SP_SPECIAL=Pattern.compile("<s>|</s>|<unk>|<pad>|<mask>");
    public LocalModelEngine(File root,String trustedManifest){this.root=root;verification=new ModelVerification(root,trustedManifest);}
    private static OrtSession.SessionOptions options()throws OrtException{
        OrtSession.SessionOptions options=new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1);
        options.setCPUArenaAllocator(false);options.setMemoryPatternOptimization(false);
        options.addConfigEntry("session.disable_prepacking","1");
        return options;
    }
    private void checkOpen()throws IOException{if(closed||cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Model work cancelled");}
    private ModelVerification.Result verify(String kind)throws Exception{return verification.verify(kind,this::checkOpen);}
    public void load(String kind)throws Exception{
        if(!kind.equals("embedding")&&!kind.equals("reranker"))throw new IllegalArgumentException("Unknown model");
        checkOpen();if(kind.equals(loaded))return;unload();verify(kind);
        File folder=new File(root,kind);
        try{
            if(kind.equals("embedding"))bert=new BgeWordpiece(new File(folder,"upstream/vocab.txt"));
            else spTokenizer=new SentencePieceTokenizer(new File(folder,"upstream/sentencepiece.bpe.model"));
            try(OrtSession.SessionOptions opts=options()){model=environment.createSession(new File(folder,kind.equals("embedding")?"model_quantized.onnx":"model_int8.onnx").getPath(),opts);}
            loaded=kind;
        }catch(Exception e){unload();throw e;}
    }
    public long[] tokenize(String kind,String text)throws Exception{
        load(kind);checkOpen();if(text.length()>16000)throw new IllegalArgumentException("Input too long");
        if(kind.equals("embedding"))return bert.encode(text);
        List<Long> ids=new ArrayList<>();ids.add(0L);Matcher matcher=SP_SPECIAL.matcher(text);int offset=0;
        while(matcher.find()){
            String part=text.substring(offset,matcher.start());
            if(matcher.group().equals("<mask>"))part=part.replaceAll("[\\s\\p{Z}]+$","");
            spSegment(part,ids);
            long special=matcher.group().equals("<s>")?0:matcher.group().equals("</s>")?2:matcher.group().equals("<pad>")?1:matcher.group().equals("<unk>")?3:250001;
            ids.add(special);offset=matcher.end();
        }
        spSegment(text.substring(offset),ids);ids.add(2L);
        long[] output=new long[ids.size()];for(int i=0;i<output.length;i++)output[i]=ids.get(i);return output;
    }
    private void spSegment(String text,List<Long> ids)throws Exception{
        if(text.isEmpty())return;
        // XLM-R fairseq offset: SentencePiece unknown=0 maps to 3, other IDs +1.
        for(int token:spTokenizer.encode(text))ids.add(token==0?3L:(long)token+1);
    }
    public float[] embed(String text)throws Exception{
        long[] ids=single(tokenize("embedding",text),128,102);
        try(OrtSession.Result result=run(ids,true)){
            Object value=result.get(0).getValue();float[] vector=value instanceof float[][][]?((float[][][])value)[0][0].clone():((float[][])value)[0].clone();
            if(vector.length!=512)throw new IOException("Embedding dimension mismatch");double sum=0;for(float f:vector){if(!Float.isFinite(f))throw new IOException("Invalid embedding");sum+=(double)f*f;}
            if(sum==0)throw new IOException("Empty embedding");double norm=Math.sqrt(sum);for(int i=0;i<vector.length;i++)vector[i]/=norm;return vector;
        }
    }
    private static final class PairInput {
        final long[] ids;final int originalTokens;
        PairInput(long[] ids,int originalTokens){this.ids=ids;this.originalTokens=originalTokens;}
    }
    public static final class RerankResult {
        public final float logit;public final int originalTokens,usedTokens;public final boolean complete;
        private RerankResult(float logit,PairInput input){this.logit=logit;originalTokens=input.originalTokens;usedTokens=input.ids.length;complete=originalTokens==usedTokens;}
    }
    private PairInput pairInput(String query,String text)throws Exception{
        long[] a=tokenize("reranker",query),b=tokenize("reranker",text);int na=a.length-2,nb=b.length-2;
        int original=na+nb+4;
        while(na+nb+4>256){if(na>nb)na--;else nb--;}
        long[] ids=new long[na+nb+4];int at=0;ids[at++]=0;
        for(int i=0;i<na;i++)ids[at++]=a[i+1];ids[at++]=2;ids[at++]=2;
        for(int i=0;i<nb;i++)ids[at++]=b[i+1];ids[at]=2;return new PairInput(ids,original);
    }
    public long[] pair(String query,String text)throws Exception{return pairInput(query,text).ids;}
    public float rerank(String query,String text)throws Exception{
        return rerankResult(query,text).logit;
    }
    public RerankResult rerankResult(String query,String text)throws Exception{
        PairInput input=pairInput(query,text);
        try(OrtSession.Result result=run(input.ids,false)){
            float value=((float[][])result.get(0).getValue())[0][0];if(!Float.isFinite(value))throw new IOException("Invalid reranking score");return new RerankResult(value,input);
        }
    }
    private OrtSession.Result run(long[] ids,boolean tokenTypes)throws Exception{
        checkOpen();Map<String,OnnxTensor> inputs=new HashMap<>();long[] mask=new long[ids.length];Arrays.fill(mask,1);
        try{
            inputs.put("input_ids",OnnxTensor.createTensor(environment,new long[][]{ids}));
            inputs.put("attention_mask",OnnxTensor.createTensor(environment,new long[][]{mask}));
            if(tokenTypes)inputs.put("token_type_ids",OnnxTensor.createTensor(environment,new long[][]{new long[ids.length]}));
            try(OrtSession.RunOptions options=new OrtSession.RunOptions()){
                synchronized(runLock){checkOpen();activeRun=options;}
                try{return model.run(inputs,options);}
                finally{synchronized(runLock){activeRun=null;}}
            }
        }finally{for(OnnxTensor input:inputs.values())input.close();}
    }
    private static long[] single(long[] ids,int max,long end){if(ids.length<=max)return ids;long[] out=Arrays.copyOf(ids,max);out[max-1]=end;return out;}
    /** Safe from another thread. Native model loading is bounded by the service process. */
    public void cancel(){
        cancelled=true;
        synchronized(runLock){if(activeRun!=null)try{activeRun.setTerminate(true);}catch(OrtException ignored){}}
    }
    public void unload()throws OrtException{
        OrtException failure=null;
        try{if(model!=null)model.close();}catch(OrtException e){failure=e;}finally{model=null;}
        try{if(spTokenizer!=null)spTokenizer.close();}finally{spTokenizer=null;bert=null;loaded="";}
        if(failure!=null)throw failure;
    }
    @Override public void close()throws OrtException{closed=true;unload();}
}
