# Add project specific ProGuard rules here.

# =================================================================
# 보이스 보호 관련 - Android Keystore / Crypto 클래스 유지
# R8이 잘못 최적화하면 KeyStore/Cipher 동작이 깨질 수 있음
# =================================================================
-keep class java.security.** { *; }
-keep class javax.crypto.** { *; }
-keep class android.security.keystore.** { *; }

# 루팅 탐지 라이브러리 (RootBeer) 유지
-keep class com.scottyab.rootbeer.** { *; }

# Hilt / DI - 보안 유틸 클래스가 Hilt 주입을 사용하므로 유지
-keep class com.onlyou.com.util.VoiceEncryptionUtil { *; }
-keep class com.onlyou.com.util.RootCheckUtil { *; }

# Room 엔티티 유지 (테이블 이름/컬럼 이름이 문자열로 참조됨)
-keep class com.onlyou.com.data.local.**Entity { *; }

# --- Gson DTO 보호 ---
# Dto.kt의 데이터 클래스들은 @SerializedName 없이 필드명으로 JSON을 매핑한다.
# R8가 필드명을 리네임하면 직렬화/역직렬화가 조용히 깨지므로 원본 이름을 유지한다.
-keep class com.onlyou.com.data.remote.** { *; }
-keepclassmembers class com.onlyou.com.data.remote.** { *; }
# Gson 리플렉션이 제네릭 시그니처/애노테이션에 의존
-keepattributes Signature
-keepattributes *Annotation*
# @SerializedName가 붙은 필드는 난독화되더라도 이름 유지 (향후 대비)
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Firebase 관련 (기본 규칙으로 충분하나 명시적으로 유지)
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# 스택 트레이스를 위해 줄 번호 유지 (선택)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile