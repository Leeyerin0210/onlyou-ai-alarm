package com.onlyou.com.domain.repository

data class WeatherInfo(
    val temperature: Double,
    val weatherCode: Int,
    val humidity: Int,
    val precipitationProbability: Int,
    val locationName: String = "서울시 강남구" // TODO: 역지오코딩 적용
)

interface WeatherRepository {
    suspend fun getCurrentWeather(lat: Double, lon: Double): Result<WeatherInfo>
    suspend fun getCoordinatesFromName(name: String): Result<Pair<Double, Double>>
}
