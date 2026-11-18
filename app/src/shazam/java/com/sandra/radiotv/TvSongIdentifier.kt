package com.sandra.radiotv

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import com.shazam.shazamkit.AudioSampleRateInHz
import com.shazam.shazamkit.DeveloperToken
import com.shazam.shazamkit.DeveloperTokenProvider
import com.shazam.shazamkit.MatchResult
import com.shazam.shazamkit.ShazamKit
import com.shazam.shazamkit.ShazamKitResult.Failure
import com.shazam.shazamkit.ShazamKitResult.Success
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-demand TV recognition. The renderer only copies into a bounded, non-blocking queue;
 * downmixing, signature generation and network work never run on the playback thread.
 * No microphone, files, second player, continuous signatures, or stored audio.
 * Lifecycle methods are called on the service's main looper.
 */
class TvSongIdentifier(private val listener: Listener) : TeeAudioProcessor.AudioBufferSink {
    fun interface Listener {
        fun onState(mediaId: String, state: String, title: String, artist: String)
    }

    private data class PcmFormat(val rate: Int, val channels: Int, val encoding: Int)
    private data class Chunk(val format: PcmFormat, val bytes: ByteArray)
    private class Capture {
        val chunks = Channel<Chunk>(32, onUndeliveredElement = { it.bytes.fill(0) })
        val failure = AtomicReference<Exception?>(null)
    }

    private class Progress {
        @Volatile var stage = "configuration"
        @Volatile var frames = 0L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val capture = AtomicReference<Capture?>(null)
    private val tokenClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    @Volatile private var format: PcmFormat? = null
    private var generation = 0L
    private var requestJob: Job? = null
    private var stationId = ""

    fun start(mediaId: String) {
        if (requestJob?.isActive == true) {
            TvIdentificationLog.info("request: ignored because identification is already active")
            return
        }
        val requestGeneration = ++generation
        val progress = Progress()
        TvIdentificationLog.info("request $requestGeneration: SDK enabled; starting on-demand identification")
        stationId = mediaId
        listener.onState(mediaId, TvSongIdentification.LISTENING, "", "")
        requestJob = scope.launch {
            try {
                val result = withTimeout(40_000) {
                    withContext(Dispatchers.Default) { recognize(progress) }
                }
                if (generation == requestGeneration) {
                    when (result) {
                        is MatchResult.Match -> {
                            TvIdentificationLog.info("match: received ${result.matchedMediaItems.size} result(s)")
                            val item = result.matchedMediaItems.firstOrNull()
                            val title = item?.title
                            val artist = item?.artist
                            if (title.isNullOrBlank() || artist.isNullOrBlank()) {
                                listener.onState(mediaId, TvSongIdentification.NO_MATCH, "", "")
                            } else {
                                listener.onState(mediaId, TvSongIdentification.MATCH,
                                    title, artist)
                            }
                        }
                        is MatchResult.NoMatch -> {
                            TvIdentificationLog.info("match: catalog returned no match")
                            listener.onState(mediaId, TvSongIdentification.NO_MATCH, "", "")
                        }
                        is MatchResult.Error -> {
                            TvIdentificationLog.error("catalog match: ${result.exception.matchError}", result.exception)
                            reportError(requestGeneration, mediaId)
                        }
                    }
                }
            } catch (error: TimeoutCancellationException) {
                TvIdentificationLog.error("${progress.stage}; capturedFrames=${progress.frames}; timeout", error)
                reportError(requestGeneration, mediaId)
            } catch (cancelled: CancellationException) {
                TvIdentificationLog.info("request $requestGeneration: cancelled during ${progress.stage}")
                throw cancelled
            } catch (error: Exception) {
                TvIdentificationLog.error("${progress.stage}; capturedFrames=${progress.frames}", error)
                reportError(requestGeneration, mediaId)
            } catch (error: LinkageError) {
                TvIdentificationLog.error("${progress.stage}; SDK native library unavailable", error)
                // Missing/unsupported native SDK libraries must not tear down radio playback.
                reportError(requestGeneration, mediaId)
            }
        }
    }

    fun reset() {
        generation++
        capture.getAndSet(null)?.chunks?.cancel()
        requestJob?.cancel()
        requestJob = null
        stationId = ""
        listener.onState("", TvSongIdentification.IDLE, "", "")
    }

    fun playbackUnavailable() {
        // Keep an already completed match visible when the user pauses.
        if (requestJob?.isActive != true) return
        TvIdentificationLog.info("capture: recognition cancelled because playback stopped or was interrupted")
        val mediaId = stationId
        reset()
        listener.onState(mediaId, TvSongIdentification.ERROR, "", "")
    }

    fun release() {
        reset()
        scope.cancel()
        tokenClient.dispatcher.cancelAll()
        tokenClient.connectionPool.evictAll()
    }

    private fun reportError(requestGeneration: Long, mediaId: String) {
        if (generation == requestGeneration) {
            listener.onState(mediaId, TvSongIdentification.ERROR, "", "")
        }
    }

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        format = PcmFormat(sampleRateHz, channelCount, encoding)
        capture.getAndSet(null)?.let { failCapture(it, IOException("Audio discontinuity")) }
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val active = capture.get() ?: return // Idle: no copies or audio analysis.
        try {
            val current = format ?: throw IOException("PCM format unavailable")
            if (current.encoding != C.ENCODING_PCM_16BIT || current.channels !in 1..8) {
                throw IOException("Unsupported PCM encoding=${current.encoding}, channels=${current.channels}, rate=${current.rate}")
            }
            val frameBytes = current.channels * 2
            if (buffer.remaining() % frameBytes != 0) throw IOException("Incomplete PCM frame")
            val input = buffer.duplicate() // Never consume/change the playback buffer.
            val chunkSize = (32_768 / frameBytes) * frameBytes
            while (input.hasRemaining() && capture.get() === active) {
                val bytes = ByteArray(minOf(chunkSize, input.remaining()))
                input.get(bytes)
                if (active.chunks.trySend(Chunk(current, bytes)).isFailure) {
                    bytes.fill(0)
                    // Drop the recognition request, never block or back-pressure radio playback.
                    throw IOException("Recognition queue full")
                }
            }
        } catch (error: Exception) {
            capture.compareAndSet(active, null)
            failCapture(active, error)
        }
    }

    private fun failCapture(active: Capture, error: Exception) {
        active.failure.compareAndSet(null, error)
        active.chunks.close(error)
    }

    private suspend fun recognize(progress: Progress): MatchResult {
        // Fetch a fresh, short-lived token for each request. It is kept only in this request.
        progress.stage = "token request"
        val token = fetchDeveloperToken()
        TvIdentificationLog.info("token: received; authentication will be checked by the Shazam catalog")
        progress.stage = "catalog initialization"
        val catalog = ShazamKit.createShazamCatalog(DeveloperTokenProvider { DeveloperToken(token) })
        TvIdentificationLog.info("catalog: initialized")
        val active = Capture()
        var inFlightAudio: ByteArray? = null
        try {
            // Serialize installation with reset/release on the service looper.
            withContext(Dispatchers.Main.immediate) { capture.set(active) }
            progress.stage = "waiting for PCM from existing player"
            TvIdentificationLog.info("capture: tap armed; waiting for first PCM buffer")
            val signature = withTimeout(18_000) {
                var chunk = active.chunks.receive().also { inFlightAudio = it.bytes }
                val pcm = chunk.format
                TvIdentificationLog.info("capture: first PCM buffer; rate=${pcm.rate}Hz, channels=${pcm.channels}, " +
                    "encoding=${pcm.encoding}, bytes=${chunk.bytes.size}")
                progress.stage = "PCM validation"
                val sampleRate = when (pcm.rate) {
                    48_000 -> AudioSampleRateInHz.SAMPLE_RATE_48000
                    44_100 -> AudioSampleRateInHz.SAMPLE_RATE_44100
                    32_000 -> AudioSampleRateInHz.SAMPLE_RATE_32000
                    16_000 -> AudioSampleRateInHz.SAMPLE_RATE_16000
                    else -> throw IOException("Unsupported PCM sample rate ${pcm.rate} Hz; expected 16000/32000/44100/48000")
                }
                progress.stage = "signature generator initialization"
                val generator = when (val created = ShazamKit.createSignatureGenerator(sampleRate)) {
                    is Success -> created.data
                    is Failure -> throw IOException("Signature generator failed: ${created.reason.internalError}", created.reason)
                }
                val durationMs = minOf(10_000L, catalog.maximumQuerySignatureDurationInMs.toLong())
                if (durationMs < catalog.minimumQuerySignatureDurationInMs) {
                    throw IOException("Capture duration is below catalog minimum")
                }
                val targetFrames = pcm.rate.toLong() * durationMs / 1000
                var frames = 0L
                var peak = 0
                val startMs = System.currentTimeMillis()
                progress.stage = "PCM capture and signature append"
                TvIdentificationLog.info("signature: generator ready; feeding 16-bit little-endian mono at ${pcm.rate}Hz")
                while (frames < targetFrames) {
                    currentCoroutineContext().ensureActive()
                    active.failure.get()?.let { throw it }
                    if (chunk.format != pcm) throw IOException("PCM format changed")
                    val count = minOf(chunk.bytes.size / (pcm.channels * 2),
                        (targetFrames - frames).toInt())
                    val mono = ByteArray(count * 2)
                    try {
                        val input = ByteBuffer.wrap(chunk.bytes).order(ByteOrder.LITTLE_ENDIAN)
                        val output = ByteBuffer.wrap(mono).order(ByteOrder.LITTLE_ENDIAN)
                        repeat(count) {
                            var sum = 0
                            repeat(pcm.channels) { sum += input.short.toInt() }
                            val sample = sum / pcm.channels
                            peak = maxOf(peak, kotlin.math.abs(sample))
                            output.putShort(sample.toShort())
                        }
                        generator.append(mono, mono.size, startMs + frames * 1000 / pcm.rate)
                        frames += count
                        progress.frames = frames
                    } finally {
                        mono.fill(0)
                        chunk.bytes.fill(0)
                    }
                    if (frames < targetFrames) {
                        chunk = active.chunks.receive().also { inFlightAudio = it.bytes }
                    }
                }
                active.failure.get()?.let { throw it }
                // Stop the tap before matching over the network, and discard all queued PCM.
                capture.compareAndSet(active, null)
                active.chunks.cancel()
                currentCoroutineContext().ensureActive()
                TvIdentificationLog.info("capture: completed; frames=$frames, durationMs=${frames * 1000 / pcm.rate}, peak=$peak")
                if (peak == 0) throw IOException("Captured PCM contains only silence")
                progress.stage = "signature generation"
                generator.generateSignature().also {
                    val size = it.dataRepresentation.size
                    TvIdentificationLog.info("signature: generated; durationMs=${it.durationInMs}, bytes=$size")
                    if (size == 0 || it.durationInMs < catalog.minimumQuerySignatureDurationInMs
                        || it.durationInMs > catalog.maximumQuerySignatureDurationInMs) {
                        throw IOException("Generated signature is empty or outside catalog duration limits")
                    }
                }
            }
            progress.stage = "match session initialization"
            val session = when (val created = ShazamKit.createSession(catalog)) {
                is Success -> created.data
                is Failure -> throw IOException("Match session failed: ${created.reason.internalError}", created.reason)
            }
            currentCoroutineContext().ensureActive()
            progress.stage = "catalog match request"
            TvIdentificationLog.info("match: invoking ShazamKit Session.match with validated signature")
            return session.match(signature)
        } finally {
            capture.compareAndSet(active, null)
            active.chunks.cancel()
            inFlightAudio?.fill(0)
        }
    }

    private suspend fun fetchDeveloperToken(): String = suspendCancellableCoroutine { continuation ->
        val request = try {
            if (BuildConfig.SHAZAM_TOKEN_ENDPOINT.isBlank()) {
                throw IOException("SHAZAM_TOKEN_ENDPOINT is empty; configure the Apple developer-token backend")
            }
            Request.Builder().url(BuildConfig.SHAZAM_TOKEN_ENDPOINT).get().build().also {
                require(it.url.isHttps && it.url.username.isEmpty() && it.url.password.isEmpty())
            }
        } catch (error: Exception) {
            continuation.resumeWithException(IOException("Configure SHAZAM_TOKEN_ENDPOINT", error))
            return@suspendCancellableCoroutine
        }
        val call = tokenClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val token = response.use {
                        TvIdentificationLog.info("token: endpoint HTTP ${it.code}")
                        if (!it.isSuccessful) throw IOException("Token endpoint HTTP ${it.code}")
                        val body = it.body ?: throw IOException("Empty token response")
                        val source = body.source()
                        source.request(16_385)
                        if (source.buffer.size > 16_384) throw IOException("Token response too large")
                        val payload = try {
                            JSONObject(source.readUtf8())
                        } catch (_: org.json.JSONException) {
                            // JSON parser errors can contain the response body, including secrets.
                            throw IOException("Token endpoint returned invalid JSON")
                        }
                        (payload.opt("token") as? String
                            ?: throw IOException("Token endpoint JSON must contain a string token")).also { value ->
                            if (value.isBlank()) throw IOException("Empty developer token")
                        }
                    }
                    if (continuation.isActive) continuation.resume(token)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        })
    }
}
