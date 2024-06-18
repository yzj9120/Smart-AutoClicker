package com.buzbuz.smartautoclicker.utils

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException

sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

class NetworkRequestHelper(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val client = OkHttpClient()

    suspend fun get(url: String): Result<String> {
        return withContext(dispatcher) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = client.newCall(request).execute()
                handleResponse(response)
            } catch (e: IOException) {
                Result.Error(e)
            }
        }
    }

    suspend fun post(url: String, jsonBody: String): Result<String> {
        return withContext(dispatcher) {
            try {
                val body = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), jsonBody)
                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                handleResponse(response)
            } catch (e: IOException) {
                Result.Error(e)
            }
        }
    }

    private fun handleResponse(response: Response): Result<String> {
        return if (response.isSuccessful) {
            response.body?.string()?.let {
                Result.Success(it)
            } ?: Result.Error(IOException("Response body is null"))
        } else {
            Result.Error(IOException("Network call failed with code: ${response.code}"))
        }
    }
}
