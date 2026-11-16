package com.example.repository

import com.example.api.ApiErrorHandler
import com.example.api.ApiResult
import com.example.api.P2PApiService
import com.example.models.ServerHealth

class ServerRepository(
    private val api: P2PApiService,
    private val errorHandler: ApiErrorHandler
) {
    suspend fun checkHealth(): ApiResult<ServerHealth> {
        val start = System.currentTimeMillis()
        return when (val res = errorHandler.safeCall(emitUnauthorizedOn401 = false) { api.getHealth() }) {
            is ApiResult.Success -> {
                val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                val d = res.data
                ApiResult.Success(
                    ServerHealth(
                        appName = d.app,
                        status = d.status,
                        version = d.version,
                        mediaStorage = d.mediaStorage,
                        profilePhotoStorage = d.profilePhotoStorage,
                        textMessageStorage = d.textMessageStorage,
                        latencyMs = latency,
                        checkedAtMillis = System.currentTimeMillis()
                    ),
                    res.statusCode
                )
            }
            is ApiResult.Failure -> res
        }
    }
}
