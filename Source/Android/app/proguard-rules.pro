# Being able to get sensible stack traces from users is more important
# than the space savings obfuscation could give us
-dontobfuscate

# Required for R8 full mode
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# This is referenced in JNI_OnLoad.
-keep class androidx.core.util.Pair {
    <init>(java.lang.Object, java.lang.Object);
}

# Vom C++-Kern ueber ihren Namen gesucht und dort erzeugt, aus Java nie:
# SkylanderConfig.cpp ruft FindClass und GetMethodID darauf. Ohne diese Regel
# entfernt R8 den Konstruktor als ungenutzt, GetMethodID liefert null, und die
# App bricht beim ersten Auflegen mit
# "JNI DETECTED ERROR IN APPLICATION: mid == null" ab.
-keep class org.dolphinemu.dolphinemu.features.skylanders.model.SkylanderPair {
    <init>(int, int);
}
