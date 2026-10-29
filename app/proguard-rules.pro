-keepattributes Signature
-keep class app.quietreader.ai.SentencePieceTokenizer { *; }

# ONNX Runtime JNI resolves Java tensor/value classes and constructors by name.
-keep class ai.onnxruntime.** { *; }
