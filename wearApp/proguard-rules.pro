# 스택 트레이스 라인번호 보존
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Google Play Services Wearable (폰 앱에서 오는 메시지 수신)
-keep class com.google.android.gms.wearable.** { *; }
-dontwarn com.google.android.gms.wearable.**

# Manifest에 등록된 컴포넌트
-keep class com.awds1236.smarthealth.wear.WearAlarmActivity
-keep class com.awds1236.smarthealth.wear.WearAlarmService
