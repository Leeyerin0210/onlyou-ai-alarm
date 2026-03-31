package com.nemuria.miya.util

import android.content.Context
import com.scottyab.rootbeer.RootBeer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 기기의 루팅 여부를 탐지하는 유틸리티.
 *
 * 보이스 에셋은 상업적으로 보호되어야 하는 IP이므로,
 * 루팅된 기기에서는 복호화 자체를 차단합니다.
 *
 * [isDeviceRooted] 가 true이면 [AlarmService]는 시스템 기본 알람음으로 폴백합니다.
 */
@Singleton
class RootCheckUtil @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * 기기가 루팅되었는지 여부를 반환합니다.
     * 에뮬레이터에서는 일반적으로 false를 반환합니다.
     */
    fun isDeviceRooted(): Boolean {
        return RootBeer(context).isRooted
    }
}
