# llama.cpp and whisper.cpp are reached through JNI by class and method name.
-keep class com.aisendiri.llama.** { *; }

# Readable stack traces in Play Console crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
