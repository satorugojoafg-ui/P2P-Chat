package com.example.repository

import com.example.api.ApiErrorHandler
import com.example.api.ApiResult
import com.example.api.P2PApiService
import com.example.database.ChatDao
import com.example.database.toEntity
import com.example.models.UserProfile
import com.example.models.toDomain

class UserRepository(
    private val api: P2PApiService,
    private val errorHandler: ApiErrorHandler,
    private val chatDao: ChatDao
) {
    suspend fun searchUsers(query: String): ApiResult<List<UserProfile>> {
        return when (val res = errorHandler.safeCall { api.searchUsers(query.trim()) }) {
            is ApiResult.Success -> {
                val users = res.data.map { it.toDomain() }
                chatDao.upsertUsers(users.map { it.toEntity() })
                ApiResult.Success(users, res.statusCode)
            }
            is ApiResult.Failure -> res
        }
    }

    suspend fun getUser(username: String): ApiResult<UserProfile> {
        return when (val res = errorHandler.safeCall { api.getUser(username) }) {
            is ApiResult.Success -> {
                val user = res.data.toDomain()
                chatDao.upsertUsers(listOf(user.toEntity()))
                ApiResult.Success(user, res.statusCode)
            }
            is ApiResult.Failure -> res
        }
    }
}
