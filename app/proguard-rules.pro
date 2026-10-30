# R8 rules for the release build of PS4 Monitor.
# The app uses no reflection of its own (no Class.forName / getDeclared*, no JS interfaces), so only third-party notes are needed.

# Tink (used by androidx.security:security-crypto for the encrypted preferences): protobuf-lite reads its fields by reflection.
-keepclassmembers class * extends com.google.crypto.tink.shaded.protobuf.GeneratedMessageLite { <fields>; }
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**

# OkHttp optional TLS providers (they are looked up by name, and are not present on Android)
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Readable crash reports
-keepattributes SourceFile,LineNumberTable
