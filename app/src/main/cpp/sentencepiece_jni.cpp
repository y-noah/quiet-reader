#include <jni.h>
#include <memory>
#include <string>
#include <vector>
#include "sentencepiece_processor.h"

namespace {
void fail(JNIEnv* env,const char* type,const char* message) {
    if (!env->ExceptionCheck()) env->ThrowNew(env->FindClass(type),message);
}
// Java's modified UTF-8 does not preserve NUL and supplementary characters as
// standard UTF-8. Encode UTF-16 explicitly and pass the full std::string length.
std::string utf8(JNIEnv* env,jstring text) {
    const jchar* chars=env->GetStringChars(text,nullptr);
    if (!chars) return {};
    jsize count=env->GetStringLength(text);std::string out;
    for(jsize i=0;i<count;i++) {
        uint32_t c=chars[i];
        if(c>=0xd800 && c<=0xdbff && i+1<count && chars[i+1]>=0xdc00 && chars[i+1]<=0xdfff) c=0x10000+((c-0xd800)<<10)+(chars[++i]-0xdc00);
        else if(c>=0xd800 && c<=0xdfff) c=0xfffd;
        if(c<0x80)out.push_back(static_cast<char>(c));
        else if(c<0x800){out.push_back(static_cast<char>(0xc0|(c>>6)));out.push_back(static_cast<char>(0x80|(c&63)));}
        else if(c<0x10000){out.push_back(static_cast<char>(0xe0|(c>>12)));out.push_back(static_cast<char>(0x80|((c>>6)&63)));out.push_back(static_cast<char>(0x80|(c&63)));}
        else{out.push_back(static_cast<char>(0xf0|(c>>18)));out.push_back(static_cast<char>(0x80|((c>>12)&63)));out.push_back(static_cast<char>(0x80|((c>>6)&63)));out.push_back(static_cast<char>(0x80|(c&63)));}
    }
    env->ReleaseStringChars(text,chars);return out;
}
}

extern "C" JNIEXPORT jlong JNICALL Java_app_quietreader_ai_SentencePieceTokenizer_nativeOpen(JNIEnv* env,jclass,jstring path) {
    try {
        auto processor=std::make_unique<sentencepiece::SentencePieceProcessor>();
        auto value=utf8(env,path);if(env->ExceptionCheck())return 0;
        auto status=processor->Load(value);
        if(!status.ok()){fail(env,"java/io/IOException","SentencePiece vocabulary cannot load");return 0;}
        return reinterpret_cast<jlong>(processor.release());
    }catch(const std::exception&){fail(env,"java/io/IOException","SentencePiece initialization failed");return 0;}
}

extern "C" JNIEXPORT jintArray JNICALL Java_app_quietreader_ai_SentencePieceTokenizer_nativeEncode(JNIEnv* env,jclass,jlong handle,jstring text) {
    if(!handle){fail(env,"java/lang/IllegalStateException","Tokenizer is closed");return nullptr;}
    try {
        auto value=utf8(env,text);if(env->ExceptionCheck())return nullptr;
        std::vector<int> ids;auto status=reinterpret_cast<sentencepiece::SentencePieceProcessor*>(handle)->Encode(value,&ids);
        if(!status.ok()){fail(env,"java/io/IOException","SentencePiece encoding failed");return nullptr;}
        jintArray result=env->NewIntArray(static_cast<jsize>(ids.size()));
        if(result)env->SetIntArrayRegion(result,0,static_cast<jsize>(ids.size()),ids.data());return result;
    }catch(const std::exception&){fail(env,"java/io/IOException","SentencePiece encoding failed");return nullptr;}
}

extern "C" JNIEXPORT void JNICALL Java_app_quietreader_ai_SentencePieceTokenizer_nativeClose(JNIEnv*,jclass,jlong handle) {
    delete reinterpret_cast<sentencepiece::SentencePieceProcessor*>(handle);
}
