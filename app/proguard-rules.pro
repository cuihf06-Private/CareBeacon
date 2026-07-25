# Default ProGuard rules for CareBeacon.
# Project-specific keep rules can be added below.

# Keep Compose runtime metadata.
-keep class androidx.compose.runtime.** { *; }

# Keep Room entities and DAOs (KSP emits code that uses them).
-keep class com.carebeacon.app.data.** { *; }

# Keep receivers (referenced from manifest).
-keep class com.carebeacon.app.alarm.** { *; }
-keep class com.carebeacon.app.alert.** { *; }
-keep class com.carebeacon.app.service.** { *; }