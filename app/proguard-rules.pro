# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep, allowobfuscation, allowoptimization class org.kodein.type.TypeReference
-keep, allowobfuscation, allowoptimization class org.kodein.type.JVMAbstractTypeToken$Companion$WrappingTest

-keep, allowobfuscation, allowoptimization class * extends org.kodein.type.TypeReference
-keep, allowobfuscation, allowoptimization class * extends org.kodein.type.JVMAbstractTypeToken$Companion$WrappingTest

-keep class io.netty.** {*;}
-keep class org.apache.** {*;}
-keep class org.slf4j.** {*;}
-keep class com.barchart.** {*;}
-keep class com.fasterxml.** {*;}
-keep class com.google.protobuf.** {*;}
-keep class com.jcraft.** {*;}
-keep class com.ning.** {*;}
-keep class com.oracle.svm.** {*;}
-keep class com.sun.nio.** {*;}
-keep class gnu.io.** {*;}
-keep class javax.** {*;}
-keep class lzma.** {*;}
-keep class net.jpountz.** {*;}
-keep class sun.security.** {*;}

-dontwarn org.codehaus.mojo.animal_sniffer.*

#Netty
-keepattributes Signature,InnerClasses
-keepclasseswithmembers class io.netty.** {*;}
-keepnames class io.netty.** {*;}
-keepclassmembernames class io.netty.buffer.AbstractByteBufAllocator {*;}
-keepclassmembernames class io.netty.buffer.AdvancedLeakAwareByteBuf {*;}
-keep public class io.netty.util.ReferenceCountUtil {*;}