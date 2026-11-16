package com.example.api

object ApiConfig {
    // Centralized production backend URL — never localhost or mock
    const val BASE_URL = "https://omitok07090.pythonanywhere.com/"

    const val CONNECT_TIMEOUT_SECONDS = 20L
    const val READ_TIMEOUT_SECONDS = 25L
    const val WRITE_TIMEOUT_SECONDS = 25L

    const val CHAT_POLL_INTERVAL_MS = 2500L
    const val SIGNAL_POLL_INTERVAL_MS = 2000L
    const val MAX_P2P_MEDIA_BYTES = 20L * 1024L * 1024L

    val STUN_SERVERS = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302"
    )
}
