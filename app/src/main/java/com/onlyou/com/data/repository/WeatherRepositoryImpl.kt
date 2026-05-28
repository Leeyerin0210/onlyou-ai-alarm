package com.onlyou.com.data.repository

import com.onlyou.com.domain.repository.WeatherInfo
import com.onlyou.com.domain.repository.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

class WeatherRepositoryImpl @Inject constructor() : WeatherRepository {
    override suspend fun getCurrentWeather(lat: Double, lon: Double): Result<WeatherInfo> = withContext(Dispatchers.IO) {
        try {
            // Open-Meteo API
            val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true&hourly=relative_humidity_2m,precipitation_probability&timezone=auto"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                val current = json.getJSONObject("current_weather")
                val temperature = current.getDouble("temperature")
                val weatherCode = current.getInt("weathercode")

                val hourly = json.getJSONObject("hourly")
                val humidityList = hourly.getJSONArray("relative_humidity_2m")
                val precipitationList = hourly.getJSONArray("precipitation_probability")
                
                // 간단히 현재 시간에 해당하는 인덱스(0)를 사용 (정확한 시간 매핑은 복잡하므로 단순화)
                val humidity = if (humidityList.length() > 0) humidityList.getInt(0) else 50
                val precipitation = if (precipitationList.length() > 0) precipitationList.getInt(0) else 0

                Result.success(
                    WeatherInfo(
                        temperature = temperature,
                        weatherCode = weatherCode,
                        humidity = humidity,
                        precipitationProbability = precipitation
                    )
                )
            } else {
                Result.failure(Exception("Failed to fetch weather: ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCoordinatesFromName(name: String): Result<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        try {
            val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
            val urlString = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedName&count=1&language=ko&format=json"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                if (json.has("results")) {
                    val results = json.getJSONArray("results")
                    if (results.length() > 0) {
                        val first = results.getJSONObject(0)
                        val lat = first.getDouble("latitude")
                        val lon = first.getDouble("longitude")
                        return@withContext Result.success(Pair(lat, lon))
                    }
                }
                Result.failure(Exception("No results found for $name"))
            } else {
                Result.failure(Exception("Failed to fetch coordinates: ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
