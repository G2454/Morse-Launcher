# Morse uses no reflection or serialization frameworks; the default Android
# optimize rules plus the consumer rules shipped with AndroidX are enough.
# Keep line numbers so crash reports from release builds stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
