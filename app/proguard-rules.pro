# 빌드 스택 트레이스에 라인번호 보존 (Play Console에서 디오브퍼스케이션 가능)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ===========================
# Health Connect
# ===========================
# Health Connect SDK는 권한 및 데이터 타입을 리플렉션으로 처리하므로 보존 필요
-keep class androidx.health.connect.client.** { *; }
-keep class androidx.health.platform.client.** { *; }
-dontwarn androidx.health.connect.client.**
-dontwarn androidx.health.platform.client.**

# ===========================
# WorkManager (SleepMonitorWorker)
# ===========================
# WorkManager는 Worker 클래스를 리플렉션으로 인스턴스화함
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keep class * extends androidx.work.CoroutineWorker
-keep class androidx.work.impl.** { *; }
-dontwarn androidx.work.**

# ===========================
# Google Play Services Wearable (MessageClient 통신)
# ===========================
-keep class com.google.android.gms.wearable.** { *; }
-dontwarn com.google.android.gms.wearable.**

# ===========================
# Kotlin Coroutines
# ===========================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ===========================
# 앱 내부에서 Manifest로 등록된 컴포넌트들 (이미 자동 보존되지만 명시)
# ===========================
-keep class com.awds1236.smarthealth.MainActivity
-keep class com.awds1236.smarthealth.AlarmActivity
-keep class com.awds1236.smarthealth.HealthRationaleActivity
-keep class com.awds1236.smarthealth.PermissionSetupActivity
-keep class com.awds1236.smarthealth.SettingsActivity
-keep class com.awds1236.smarthealth.SleepSoundsActivity
-keep class com.awds1236.smarthealth.AlarmReceiver
-keep class com.awds1236.smarthealth.BootReceiver
-keep class com.awds1236.smarthealth.AlarmService
-keep class com.awds1236.smarthealth.SleepSoundService
-keep class com.awds1236.smarthealth.SleepMonitorService
