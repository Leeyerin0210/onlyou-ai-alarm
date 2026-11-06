package com.onlyou.com.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** 마지막으로 확인한 기기 위치 (대략 위치, 동네 이름 포함). */
data class SavedLocation(val lat: Double, val lon: Double, val placeName: String, val savedAtMs: Long)

/**
 * '오늘의 날씨'용 기기 위치.
 *
 * 브리핑 대본은 알람 전 백그라운드에서 만든다. 안드로이드 10+는 백그라운드 위치 권한 없이
 * 백그라운드에서 위치를 못 읽으므로, 앱이 켜져 있을 때(포그라운드) 대략 위치를 저장해 두고
 * 알람 때는 저장된 값을 쓴다. 대략 위치(ACCESS_COARSE_LOCATION)만 쓴다 — 날씨엔 동네 수준이면 충분하다.
 *
 * 위치정보법: 출시(외부 배포) 전 위치기반서비스사업 신고 필요 여부를 확인할 것.
 */
@Singleton
class DeviceLocationStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val prefs = context.getSharedPreferences("device_location", Context.MODE_PRIVATE)

        fun hasPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        /** 저장된 위치. [MAX_AGE_MS]보다 오래됐으면 이사·여행 등으로 틀릴 수 있어 쓰지 않는다. */
        fun saved(nowMs: Long = System.currentTimeMillis()): SavedLocation? {
            if (!prefs.contains("lat")) return null
            val loc = SavedLocation(
                lat = prefs.getFloat("lat", 0f).toDouble(),
                lon = prefs.getFloat("lon", 0f).toDouble(),
                placeName = prefs.getString("name", null) ?: "현재 위치",
                savedAtMs = prefs.getLong("at", 0L),
            )
            return loc.takeIf { nowMs - it.savedAtMs in 0..MAX_AGE_MS }
        }

        /** 포그라운드에서 호출. 권한이 없거나 위치를 못 얻으면 아무것도 안 한다. */
        suspend fun refresh() {
            if (!hasPermission()) return
            val location = currentLocation() ?: return
            val name = placeName(location) ?: "현재 위치"
            prefs.edit()
                .putFloat("lat", location.latitude.toFloat())
                .putFloat("lon", location.longitude.toFloat())
                .putString("name", name)
                .putLong("at", System.currentTimeMillis())
                .apply()
        }

        @SuppressLint("MissingPermission") // hasPermission()으로 확인한 뒤에만 호출된다
        private suspend fun currentLocation(): Location? = withContext(Dispatchers.IO) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
            if (recent != null && System.currentTimeMillis() - recent.time < FRESH_FIX_MS) return@withContext recent
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || providers.isEmpty()) return@withContext recent
            withTimeoutOrNull(10_000) {
                suspendCancellableCoroutine { cont ->
                    lm.getCurrentLocation(providers.first(), null, context.mainExecutor) { cont.resume(it) }
                }
            } ?: recent
        }

        /** 좌표 → "강남구" 같은 동네 이름 (기기 내 지오코더). */
        @Suppress("DEPRECATION")
        private suspend fun placeName(location: Location): String? = withContext(Dispatchers.IO) {
            runCatching {
                val address = Geocoder(context, Locale.KOREAN)
                    .getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull() ?: return@runCatching null
                address.subLocality ?: address.locality ?: address.subAdminArea ?: address.adminArea
            }.getOrNull()
        }

        companion object {
            /** 저장 위치 유효 기간 — 하루 이틀 앱을 안 열어도 집 근처 날씨는 맞다 */
            const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000

            /** 이보다 최근 위치면 새로 잡지 않는다 */
            private const val FRESH_FIX_MS = 30L * 60 * 1000
        }
    }
