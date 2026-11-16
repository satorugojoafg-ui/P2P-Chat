package com.example.webrtc

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.api.ApiConfig
import com.example.api.ApiErrorHandler
import com.example.api.ApiResult
import com.example.api.P2PApiService
import com.example.models.MediaKind
import com.example.models.MediaTransferItem
import com.example.models.SendSignalRequest
import com.example.models.SignalDto
import com.example.models.TransferDirection
import com.example.models.TransferStatus
import com.example.models.WebRtcConnectionState
import com.example.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WebRtcManager(
    private val context: Context,
    private val api: P2PApiService,
    private val errorHandler: ApiErrorHandler,
    private val chatRepository: ChatRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val peerStates = ConcurrentHashMap<String, PeerSession>()
    private val cancelledTransfers = ConcurrentHashMap.newKeySet<String>()
    private val outgoingMediaCache = ConcurrentHashMap<String, QueuedOutgoingMedia>()

    private val _connectionStates = MutableStateFlow<Map<String, WebRtcConnectionState>>(emptyMap())
    val connectionStates: StateFlow<Map<String, WebRtcConnectionState>> = _connectionStates.asStateFlow()

    private val _transfers = MutableStateFlow<Map<String, List<MediaTransferItem>>>(emptyMap())
    val transfers: StateFlow<Map<String, List<MediaTransferItem>>> = _transfers.asStateFlow()

    private val _typingPeers = MutableStateFlow<Set<String>>(emptySet())
    val typingPeers: StateFlow<Set<String>> = _typingPeers.asStateFlow()

    private val _completedTransfers = MutableSharedFlow<MediaTransferItem>(extraBufferCapacity = 8)
    val completedTransfers: SharedFlow<MediaTransferItem> = _completedTransfers.asSharedFlow()

    private var factory: PeerConnectionFactory? = null
    private var pollingJob: Job? = null

    private data class IncomingFileBuffer(
        val transferId: String,
        val fileName: String,
        val mimeType: String,
        val mediaKind: MediaKind,
        val totalBytes: Long,
        val outputFile: File,
        val outputStream: FileOutputStream,
        var receivedBytes: Long = 0L
    )

    private data class QueuedOutgoingMedia(
        val transferId: String,
        val file: File,
        val fileName: String,
        val mimeType: String,
        val mediaKind: MediaKind,
        val totalBytes: Long
    )

    private class PeerSession(
        val peerUsername: String,
        var peerConnection: PeerConnection? = null,
        var dataChannel: DataChannel? = null,
        val pendingIceCandidates: MutableList<IceCandidate> = mutableListOf(),
        var remoteDescriptionSet: Boolean = false,
        var pendingOutgoing: QueuedOutgoingMedia? = null,
        var incomingFile: IncomingFileBuffer? = null,
        var timeoutJob: Job? = null,
        var typingTimeoutJob: Job? = null
    )

    private fun ensureFactory(): PeerConnectionFactory {
        factory?.let { return it }
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )
        return PeerConnectionFactory.builder().createPeerConnectionFactory().also {
            factory = it
        }
    }

    fun startSignalingPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                pollSignalsOnce()
                delay(ApiConfig.SIGNAL_POLL_INTERVAL_MS)
            }
        }
    }

    fun stopSignalingPolling() {
        pollingJob?.cancel()
        pollingJob = null
        closeAllPeers()
    }

    fun closeAllPeers() {
        peerStates.values.forEach { session ->
            session.timeoutJob?.cancel()
            session.typingTimeoutJob?.cancel()
            runCatching { session.incomingFile?.outputStream?.close() }
            runCatching { session.dataChannel?.close() }
            runCatching { session.peerConnection?.close() }
        }
        peerStates.clear()
        _connectionStates.value = emptyMap()
        _typingPeers.value = emptySet()
    }

    fun sendTypingIndicator(peerUsername: String, isTyping: Boolean) {
        val dc = peerStates[peerUsername]?.dataChannel ?: return
        if (dc.state() != DataChannel.State.OPEN) return
        val json = JSONObject().apply {
            put("kind", "typing")
            put("active", isTyping)
        }.toString()
        runCatching {
            dc.send(DataChannel.Buffer(ByteBuffer.wrap(json.toByteArray(Charsets.UTF_8)), false))
        }
    }

    fun connectToPeer(peerUsername: String) {
        scope.launch {
            resetPeer(peerUsername)
            val session = getOrCreateSession(peerUsername)
            val pc = createPeerConnection(peerUsername, session) ?: run {
                updateConnectionState(peerUsername, WebRtcConnectionState.FAILED)
                return@launch
            }
            session.peerConnection = pc

            val init = DataChannel.Init().apply { ordered = true }
            val dc = pc.createDataChannel("p2p_media", init)
            session.dataChannel = dc
            attachDataChannelObserver(peerUsername, session, dc)

            updateConnectionState(peerUsername, WebRtcConnectionState.SIGNALING)
            startConnectionTimeout(peerUsername, session)

            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp == null) {
                        updateConnectionState(peerUsername, WebRtcConnectionState.FAILED)
                        return
                    }
                    pc.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            sendSignal(
                                to = peerUsername,
                                type = "offer",
                                payload = mapOf(
                                    "type" to sdp.type.canonicalForm(),
                                    "sdp" to sdp.description
                                )
                            )
                        }

                        override fun onSetFailure(error: String?) {
                            updateConnectionState(peerUsername, WebRtcConnectionState.FAILED)
                        }
                    }, sdp)
                }

                override fun onCreateFailure(error: String?) {
                    updateConnectionState(peerUsername, WebRtcConnectionState.FAILED)
                }
            }, MediaConstraints())
        }
    }

    fun sendMedia(
        peerUsername: String,
        uri: Uri,
        onFileTooLarge: () -> Unit,
        onReadFailed: () -> Unit
    ) {
        scope.launch {
            val prepared = prepareMediaFile(uri)
            if (prepared == null) {
                withContext(Dispatchers.Main) { onReadFailed() }
                return@launch
            }
            if (prepared.totalBytes > ApiConfig.MAX_P2P_MEDIA_BYTES) {
                prepared.file.delete()
                withContext(Dispatchers.Main) { onFileTooLarge() }
                return@launch
            }
            outgoingMediaCache[prepared.transferId] = prepared
            startOutgoingTransfer(peerUsername, prepared)
        }
    }

    fun cancelTransfer(peerUsername: String, transferId: String) {
        cancelledTransfers.add(transferId)
        val session = peerStates[peerUsername]
        if (session?.pendingOutgoing?.transferId == transferId) {
            session.pendingOutgoing = null
        }
        val dc = session?.dataChannel
        if (dc != null && dc.state() == DataChannel.State.OPEN) {
            val cancelJson = JSONObject().apply {
                put("kind", "cancel")
                put("transferId", transferId)
            }.toString()
            runCatching {
                dc.send(DataChannel.Buffer(ByteBuffer.wrap(cancelJson.toByteArray(Charsets.UTF_8)), false))
            }
        }
        updateTransferStatus(peerUsername, transferId, TransferStatus.CANCELLED)
        if (_connectionStates.value[peerUsername] == WebRtcConnectionState.TRANSFERRING) {
            val state = if (dc?.state() == DataChannel.State.OPEN) {
                WebRtcConnectionState.CONNECTED
            } else {
                WebRtcConnectionState.IDLE
            }
            updateConnectionState(peerUsername, state)
        }
    }

    fun retryTransfer(peerUsername: String, transferId: String) {
        val cached = outgoingMediaCache[transferId] ?: return
        if (!cached.file.exists()) return
        cancelledTransfers.remove(transferId)
        scope.launch {
            startOutgoingTransfer(peerUsername, cached)
        }
    }

    private suspend fun startOutgoingTransfer(peerUsername: String, prepared: QueuedOutgoingMedia) {
        addOrUpdateTransfer(
            peerUsername,
            MediaTransferItem(
                transferId = prepared.transferId,
                peerUsername = peerUsername,
                fileName = prepared.fileName,
                mimeType = prepared.mimeType,
                mediaKind = prepared.mediaKind,
                totalBytes = prepared.totalBytes,
                transferredBytes = 0L,
                progress = 0f,
                direction = TransferDirection.OUTGOING,
                localFilePath = prepared.file.absolutePath,
                statusTextKey = TransferStatus.NEGOTIATING
            )
        )

        val existing = peerStates[peerUsername]
        val dc = existing?.dataChannel
        if (existing != null && dc != null && dc.state() == DataChannel.State.OPEN) {
            streamFileOverDataChannel(peerUsername, dc, prepared)
        } else {
            resetPeer(peerUsername)
            val session = getOrCreateSession(peerUsername)
            session.pendingOutgoing = prepared
            val pc = createPeerConnection(peerUsername, session) ?: run {
                updateTransferStatus(peerUsername, prepared.transferId, TransferStatus.FAILED)
                updateConnectionState(peerUsername, WebRtcConnectionState.FAILED)
                return
            }
            session.peerConnection = pc
            val channel = pc.createDataChannel("p2p_media", DataChannel.Init().apply { ordered = true })
            session.dataChannel = channel
            attachDataChannelObserver(peerUsername, session, channel)
            updateConnectionState(peerUsername, WebRtcConnectionState.SIGNALING)
            startConnectionTimeout(peerUsername, session)

            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp == null) {
                        updateTransferStatus(peerUsername, prepared.transferId, TransferStatus.FAILED)
                        updateConnectionState(peerUsername, WebRtcConnectionState.FAILED)
                        return
                    }
                    pc.setLocalDescription(object : SimpleSdpObserver() {
                        override fun onSetSuccess() {
                            sendSignal(
                                to = peerUsername,
                                type = "offer",
                                payload = mapOf(
                                    "type" to sdp.type.canonicalForm(),
                                    "sdp" to sdp.description
                                )
                            )
                        }

                        override fun onSetFailure(error: String?) {
                            updateTransferStatus(peerUsername, prepared.transferId, TransferStatus.FAILED)
                            updateConnectionState(peerUsername, WebRtcConnectionState.FAILED)
                        }
                    }, sdp)
                }

                override fun onCreateFailure(error: String?) {
                    updateTransferStatus(peerUsername, prepared.transferId, TransferStatus.FAILED)
                    updateConnectionState(peerUsername, WebRtcConnectionState.FAILED)
                }
            }, MediaConstraints())
        }
    }

    private suspend fun pollSignalsOnce() {
        when (val res = errorHandler.safeCall { api.getSignals() }) {
            is ApiResult.Success -> {
                res.data.forEach { signal ->
                    handleIncomingSignal(signal)
                }
            }
            is ApiResult.Failure -> Unit
        }
    }

    private fun handleIncomingSignal(signal: SignalDto) {
        val peer = signal.from
        val payload = signal.payload
        when (signal.type.lowercase()) {
            "offer" -> {
                val sdpText = payload["sdp"] as? String ?: return
                resetPeer(peer)
                val session = getOrCreateSession(peer)
                val pc = createPeerConnection(peer, session) ?: return
                session.peerConnection = pc
                updateConnectionState(peer, WebRtcConnectionState.CONNECTING)
                startConnectionTimeout(peer, session)

                val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdpText)
                pc.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        session.remoteDescriptionSet = true
                        flushPendingIce(session)
                        pc.createAnswer(object : SimpleSdpObserver() {
                            override fun onCreateSuccess(answerSdp: SessionDescription?) {
                                if (answerSdp == null) return
                                pc.setLocalDescription(object : SimpleSdpObserver() {
                                    override fun onSetSuccess() {
                                        sendSignal(
                                            to = peer,
                                            type = "answer",
                                            payload = mapOf(
                                                "type" to answerSdp.type.canonicalForm(),
                                                "sdp" to answerSdp.description
                                            )
                                        )
                                    }
                                }, answerSdp)
                            }
                        }, MediaConstraints())
                    }

                    override fun onSetFailure(error: String?) {
                        updateConnectionState(peer, WebRtcConnectionState.FAILED)
                    }
                }, remoteSdp)
            }

            "answer" -> {
                val sdpText = payload["sdp"] as? String ?: return
                val session = peerStates[peer] ?: return
                val pc = session.peerConnection ?: return
                val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, sdpText)
                pc.setRemoteDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        session.remoteDescriptionSet = true
                        flushPendingIce(session)
                        updateConnectionState(peer, WebRtcConnectionState.CONNECTING)
                    }

                    override fun onSetFailure(error: String?) {
                        updateConnectionState(peer, WebRtcConnectionState.FAILED)
                    }
                }, remoteSdp)
            }

            "ice" -> {
                val candidateStr = payload["candidate"] as? String ?: return
                val sdpMid = payload["sdpMid"] as? String ?: "0"
                val lineIndexNum = payload["sdpMLineIndex"] as? Number ?: 0
                val ice = IceCandidate(sdpMid, lineIndexNum.toInt(), candidateStr)
                val session = getOrCreateSession(peer)
                if (session.remoteDescriptionSet && session.peerConnection != null) {
                    session.peerConnection?.addIceCandidate(ice)
                } else {
                    session.pendingIceCandidates.add(ice)
                }
            }
        }
    }

    private fun flushPendingIce(session: PeerSession) {
        val pc = session.peerConnection ?: return
        val copy = session.pendingIceCandidates.toList()
        session.pendingIceCandidates.clear()
        copy.forEach { pc.addIceCandidate(it) }
    }

    private fun createPeerConnection(peerUsername: String, session: PeerSession): PeerConnection? {
        val peerFactory = ensureFactory()
        val iceServers = ApiConfig.STUN_SERVERS.map {
            PeerConnection.IceServer.builder(it).createIceServer()
        }
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        return peerFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                when (newState) {
                    PeerConnection.IceConnectionState.CHECKING -> {
                        updateConnectionState(peerUsername, WebRtcConnectionState.CONNECTING)
                    }
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        session.timeoutJob?.cancel()
                        if (session.dataChannel?.state() == DataChannel.State.OPEN) {
                            updateConnectionState(peerUsername, WebRtcConnectionState.CONNECTED)
                        }
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        session.timeoutJob?.cancel()
                        failPendingTransfers(peerUsername)
                        updateConnectionState(peerUsername, WebRtcConnectionState.FAILED)
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED,
                    PeerConnection.IceConnectionState.CLOSED -> {
                        if (_connectionStates.value[peerUsername] != WebRtcConnectionState.FAILED) {
                            updateConnectionState(peerUsername, WebRtcConnectionState.IDLE)
                        }
                    }
                    else -> Unit
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                sendSignal(
                    to = peerUsername,
                    type = "ice",
                    payload = mapOf(
                        "candidate" to candidate.sdp,
                        "sdpMid" to candidate.sdpMid,
                        "sdpMLineIndex" to candidate.sdpMLineIndex
                    )
                )
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(dc: DataChannel?) {
                if (dc == null) return
                session.dataChannel = dc
                attachDataChannelObserver(peerUsername, session, dc)
            }

            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
        })
    }

    private fun attachDataChannelObserver(
        peerUsername: String,
        session: PeerSession,
        dc: DataChannel
    ) {
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                when (dc.state()) {
                    DataChannel.State.OPEN -> {
                        session.timeoutJob?.cancel()
                        updateConnectionState(peerUsername, WebRtcConnectionState.CONNECTED)
                        val pending = session.pendingOutgoing
                        if (pending != null) {
                            session.pendingOutgoing = null
                            scope.launch {
                                streamFileOverDataChannel(peerUsername, dc, pending)
                            }
                        }
                    }
                    DataChannel.State.CLOSED -> {
                        if (_connectionStates.value[peerUsername] == WebRtcConnectionState.CONNECTED) {
                            updateConnectionState(peerUsername, WebRtcConnectionState.IDLE)
                        }
                    }
                    else -> Unit
                }
            }

            override fun onMessage(buffer: DataChannel.Buffer?) {
                if (buffer == null) return
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                if (!buffer.binary) {
                    handleControlMessage(peerUsername, session, String(data, Charsets.UTF_8))
                } else {
                    handleBinaryChunk(peerUsername, session, data)
                }
            }
        })
    }

    private fun handleControlMessage(peerUsername: String, session: PeerSession, jsonStr: String) {
        runCatching {
            val json = JSONObject(jsonStr)
            when (json.optString("kind")) {
                "typing" -> {
                    val active = json.optBoolean("active", false)
                    session.typingTimeoutJob?.cancel()
                    if (active) {
                        _typingPeers.update { it + peerUsername }
                        session.typingTimeoutJob = scope.launch {
                            delay(4000L)
                            _typingPeers.update { it - peerUsername }
                        }
                    } else {
                        _typingPeers.update { it - peerUsername }
                    }
                }
                "header" -> {
                    val transferId = json.getString("transferId")
                    val fileName = json.optString("fileName", "media_${System.currentTimeMillis()}")
                    val mimeType = json.optString("mimeType", "image/jpeg")
                    val totalBytes = json.optLong("totalBytes", 0L)
                    val mediaKind = if (mimeType.startsWith("video/")) MediaKind.VIDEO else MediaKind.IMAGE
                    val mediaDir = File(context.filesDir, "p2p_media").apply { mkdirs() }
                    val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val outFile = File(mediaDir, "${transferId}_$safeName")
                    val fos = FileOutputStream(outFile)

                    session.incomingFile = IncomingFileBuffer(
                        transferId = transferId,
                        fileName = fileName,
                        mimeType = mimeType,
                        mediaKind = mediaKind,
                        totalBytes = totalBytes,
                        outputFile = outFile,
                        outputStream = fos
                    )
                    updateConnectionState(peerUsername, WebRtcConnectionState.TRANSFERRING)
                    addOrUpdateTransfer(
                        peerUsername,
                        MediaTransferItem(
                            transferId = transferId,
                            peerUsername = peerUsername,
                            fileName = fileName,
                            mimeType = mimeType,
                            mediaKind = mediaKind,
                            totalBytes = totalBytes,
                            transferredBytes = 0L,
                            progress = 0f,
                            direction = TransferDirection.INCOMING,
                            localFilePath = outFile.absolutePath,
                            statusTextKey = TransferStatus.TRANSFERRING
                        )
                    )
                }
                "cancel" -> {
                    val transferId = json.optString("transferId")
                    val incoming = session.incomingFile
                    if (incoming != null && incoming.transferId == transferId) {
                        runCatching { incoming.outputStream.close() }
                        runCatching { incoming.outputFile.delete() }
                        session.incomingFile = null
                    }
                    updateTransferStatus(peerUsername, transferId, TransferStatus.CANCELLED)
                    updateConnectionState(peerUsername, WebRtcConnectionState.CONNECTED)
                }
                "complete" -> {
                    val incoming = session.incomingFile ?: return
                    runCatching {
                        incoming.outputStream.flush()
                        incoming.outputStream.close()
                    }
                    session.incomingFile = null
                    updateConnectionState(peerUsername, WebRtcConnectionState.CONNECTED)
                    updateTransferProgress(
                        peerUsername = peerUsername,
                        transferId = incoming.transferId,
                        transferred = incoming.totalBytes,
                        total = incoming.totalBytes,
                        status = TransferStatus.COMPLETED
                    )
                    val completedItem = _transfers.value[peerUsername]
                        ?.firstOrNull { it.transferId == incoming.transferId }
                    if (completedItem != null) {
                        _completedTransfers.tryEmit(completedItem)
                    }
                    scope.launch {
                        chatRepository.recordP2PMediaBubble(
                            peerUsername = peerUsername,
                            transferId = incoming.transferId,
                            fileName = incoming.fileName,
                            mimeType = incoming.mimeType,
                            mediaKind = incoming.mediaKind,
                            localPath = incoming.outputFile.absolutePath,
                            sizeBytes = incoming.outputFile.length(),
                            isOutgoing = false,
                            summaryLabel = incoming.fileName
                        )
                    }
                }
            }
        }
    }

    private fun handleBinaryChunk(peerUsername: String, session: PeerSession, chunk: ByteArray) {
        val incoming = session.incomingFile ?: return
        runCatching {
            incoming.outputStream.write(chunk)
            incoming.receivedBytes += chunk.size
            updateTransferProgress(
                peerUsername = peerUsername,
                transferId = incoming.transferId,
                transferred = incoming.receivedBytes,
                total = incoming.totalBytes,
                status = TransferStatus.TRANSFERRING
            )
        }.onFailure {
            runCatching { incoming.outputStream.close() }
            session.incomingFile = null
            updateTransferStatus(peerUsername, incoming.transferId, TransferStatus.FAILED)
        }
    }

    private suspend fun streamFileOverDataChannel(
        peerUsername: String,
        dc: DataChannel,
        media: QueuedOutgoingMedia
    ) {
        updateConnectionState(peerUsername, WebRtcConnectionState.TRANSFERRING)
        updateTransferProgress(
            peerUsername = peerUsername,
            transferId = media.transferId,
            transferred = 0L,
            total = media.totalBytes,
            status = TransferStatus.TRANSFERRING
        )

        val headerJson = JSONObject().apply {
            put("kind", "header")
            put("transferId", media.transferId)
            put("fileName", media.fileName)
            put("mimeType", media.mimeType)
            put("totalBytes", media.totalBytes)
        }.toString()

        val sentHeader = dc.send(DataChannel.Buffer(ByteBuffer.wrap(headerJson.toByteArray(Charsets.UTF_8)), false))
        if (!sentHeader) {
            updateTransferStatus(peerUsername, media.transferId, TransferStatus.FAILED)
            updateConnectionState(peerUsername, WebRtcConnectionState.FAILED)
            return
        }

        val buffer = ByteArray(16 * 1024)
        var sentTotal = 0L
        var failed = false
        var cancelled = false

        runCatching {
            media.file.inputStream().use { input ->
                while (true) {
                    if (cancelledTransfers.contains(media.transferId)) {
                        cancelled = true
                        break
                    }
                    val read = input.read(buffer)
                    if (read <= 0) break
                    if (dc.state() != DataChannel.State.OPEN) {
                        failed = true
                        break
                    }
                    while (dc.bufferedAmount() > 1024L * 1024L && dc.state() == DataChannel.State.OPEN) {
                        if (cancelledTransfers.contains(media.transferId)) {
                            cancelled = true
                            break
                        }
                        delay(20L)
                    }
                    if (cancelled) break
                    val slice = if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
                    val ok = dc.send(DataChannel.Buffer(ByteBuffer.wrap(slice), true))
                    if (!ok) {
                        failed = true
                        break
                    }
                    sentTotal += read
                    updateTransferProgress(
                        peerUsername = peerUsername,
                        transferId = media.transferId,
                        transferred = sentTotal,
                        total = media.totalBytes,
                        status = TransferStatus.TRANSFERRING
                    )
                }
            }
        }.onFailure { failed = true }

        if (cancelled) {
            updateTransferStatus(peerUsername, media.transferId, TransferStatus.CANCELLED)
            updateConnectionState(peerUsername, WebRtcConnectionState.CONNECTED)
            return
        }

        if (failed || dc.state() != DataChannel.State.OPEN) {
            updateTransferStatus(peerUsername, media.transferId, TransferStatus.FAILED)
            updateConnectionState(peerUsername, WebRtcConnectionState.FAILED)
            return
        }

        val completeJson = JSONObject().apply {
            put("kind", "complete")
            put("transferId", media.transferId)
        }.toString()
        dc.send(DataChannel.Buffer(ByteBuffer.wrap(completeJson.toByteArray(Charsets.UTF_8)), false))

        updateConnectionState(peerUsername, WebRtcConnectionState.CONNECTED)
        updateTransferProgress(
            peerUsername = peerUsername,
            transferId = media.transferId,
            transferred = media.totalBytes,
            total = media.totalBytes,
            status = TransferStatus.COMPLETED
        )
        _transfers.value[peerUsername]
            ?.firstOrNull { it.transferId == media.transferId }
            ?.let { _completedTransfers.tryEmit(it) }

        chatRepository.recordP2PMediaBubble(
            peerUsername = peerUsername,
            transferId = media.transferId,
            fileName = media.fileName,
            mimeType = media.mimeType,
            mediaKind = media.mediaKind,
            localPath = media.file.absolutePath,
            sizeBytes = media.totalBytes,
            isOutgoing = true,
            summaryLabel = media.fileName
        )
    }

    private suspend fun prepareMediaFile(uri: Uri): QueuedOutgoingMedia? = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "image/jpeg"
            val mediaKind = if (mimeType.startsWith("video/")) MediaKind.VIDEO else MediaKind.IMAGE
            var fileName = "p2p_${System.currentTimeMillis()}"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        fileName = cursor.getString(idx) ?: fileName
                    }
                }
            }
            val transferId = UUID.randomUUID().toString().take(12)
            val dir = File(context.filesDir, "p2p_media").apply { mkdirs() }
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val destFile = File(dir, "${transferId}_$safeName")
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext null

            QueuedOutgoingMedia(
                transferId = transferId,
                file = destFile,
                fileName = fileName,
                mimeType = mimeType,
                mediaKind = mediaKind,
                totalBytes = destFile.length()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun startConnectionTimeout(peerUsername: String, session: PeerSession) {
        session.timeoutJob?.cancel()
        session.timeoutJob = scope.launch {
            delay(25_000L)
            val state = _connectionStates.value[peerUsername]
            if (state == WebRtcConnectionState.SIGNALING || state == WebRtcConnectionState.CONNECTING) {
                failPendingTransfers(peerUsername)
                updateConnectionState(peerUsername, WebRtcConnectionState.FAILED)
            }
        }
    }

    private fun sendSignal(to: String, type: String, payload: Map<String, Any?>) {
        scope.launch {
            errorHandler.safeCall {
                api.sendSignal(SendSignalRequest(to = to, type = type, payload = payload))
            }
        }
    }

    private fun getOrCreateSession(peerUsername: String): PeerSession =
        peerStates.getOrPut(peerUsername) { PeerSession(peerUsername) }

    private fun resetPeer(peerUsername: String) {
        peerStates.remove(peerUsername)?.let { old ->
            old.timeoutJob?.cancel()
            old.typingTimeoutJob?.cancel()
            runCatching { old.incomingFile?.outputStream?.close() }
            runCatching { old.dataChannel?.close() }
            runCatching { old.peerConnection?.close() }
        }
    }

    private fun updateConnectionState(peerUsername: String, state: WebRtcConnectionState) {
        _connectionStates.update { it + (peerUsername to state) }
    }

    private fun addOrUpdateTransfer(peerUsername: String, item: MediaTransferItem) {
        _transfers.update { map ->
            val list = map[peerUsername].orEmpty().filterNot { it.transferId == item.transferId } + item
            map + (peerUsername to list)
        }
    }

    private fun updateTransferProgress(
        peerUsername: String,
        transferId: String,
        transferred: Long,
        total: Long,
        status: TransferStatus
    ) {
        val ratio = if (total > 0L) (transferred.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
        _transfers.update { map ->
            val updated = map[peerUsername].orEmpty().map { item ->
                if (item.transferId == transferId) {
                    item.copy(
                        transferredBytes = transferred,
                        progress = ratio,
                        statusTextKey = status
                    )
                } else {
                    item
                }
            }
            map + (peerUsername to updated)
        }
    }

    private fun updateTransferStatus(peerUsername: String, transferId: String, status: TransferStatus) {
        _transfers.update { map ->
            val updated = map[peerUsername].orEmpty().map { item ->
                if (item.transferId == transferId) {
                    item.copy(statusTextKey = status)
                } else {
                    item
                }
            }
            map + (peerUsername to updated)
        }
    }

    private fun failPendingTransfers(peerUsername: String) {
        _transfers.update { map ->
            val updated = map[peerUsername].orEmpty().map { item ->
                if (item.statusTextKey == TransferStatus.NEGOTIATING || item.statusTextKey == TransferStatus.TRANSFERRING) {
                    item.copy(statusTextKey = TransferStatus.FAILED)
                } else {
                    item
                }
            }
            map + (peerUsername to updated)
        }
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
