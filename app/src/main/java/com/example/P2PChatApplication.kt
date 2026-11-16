package com.example

import android.app.Application
import com.example.api.NetworkModule
import com.example.authentication.AuthRepository
import com.example.authentication.SecureTokenStorage
import com.example.database.AppDatabase
import com.example.repository.ChatRepository
import com.example.repository.ProfileRepository
import com.example.repository.ServerRepository
import com.example.repository.SettingsRepository
import com.example.repository.UserRepository
import com.example.webrtc.WebRtcManager

class AppContainer(application: Application) {
    val tokenStorage = SecureTokenStorage(application)
    val networkModule = NetworkModule(tokenStorage)
    val database = AppDatabase.create(application)
    val chatDao = database.chatDao()

    val settingsRepository = SettingsRepository(application, chatDao)
    val serverRepository = ServerRepository(networkModule.apiService, networkModule.errorHandler)
    val authRepository = AuthRepository(
        api = networkModule.apiService,
        errorHandler = networkModule.errorHandler,
        tokenStorage = tokenStorage,
        chatDao = chatDao
    )
    val userRepository = UserRepository(
        api = networkModule.apiService,
        errorHandler = networkModule.errorHandler,
        chatDao = chatDao
    )
    val profileRepository = ProfileRepository(
        context = application,
        api = networkModule.apiService,
        errorHandler = networkModule.errorHandler,
        authRepository = authRepository
    )
    val chatRepository = ChatRepository(
        api = networkModule.apiService,
        errorHandler = networkModule.errorHandler,
        chatDao = chatDao,
        tokenStorage = tokenStorage
    )
    val webRtcManager = WebRtcManager(
        context = application,
        api = networkModule.apiService,
        errorHandler = networkModule.errorHandler,
        chatRepository = chatRepository
    )
}

class P2PChatApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
