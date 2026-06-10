/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.media.audio.cts.audiopermissiontests.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.AttributionSource
import android.content.AttributionSource.myAttributionSource
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.Bundle
import android.os.IBinder
import android.permission.PermissionManager
import android.util.Log

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Service which can records and sends response intents when recording moves between silenced and
 * unsilenced state.
 */
open class RecordService : Service() {
    val TAG = getAppName() + "RecordService"
    val PREFIX = "android.media.audio.cts." + getAppName()

    private val mJob =
        SupervisorJob().apply {
            // Completer on the parent job for all coroutines, so test app is informed that teardown
            // completes
            invokeOnCompletion {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                respond(ACTION_TEARDOWN_FINISHED)
            }
        }

    private val handler = object : AbstractCoroutineContextElement(CoroutineExceptionHandler),
            CoroutineExceptionHandler {
        override fun handleException(context: CoroutineContext, exception: Throwable) =
                Log.wtf(TAG, "Uncaught exception", exception).let{}
    }

    // Parent scope executes on the main thread
    private val mScope = CoroutineScope(mJob + Dispatchers.Main.immediate + handler)

    // Keyed by record ID provided by the client. Channel is used to communicate with the launched
    // record coroutine. true/false to start/stop recording, close to end recording.
    // Main thread (mScope) only for thread safety!
    private val mRecordings = HashMap<Int, Channel<Boolean>>()

    lateinit var mPermissionManager: PermissionManager
    val mAttributionSource: AtomicReference<AttributionSource> = AtomicReference();

    override fun onCreate() {
        mPermissionManager = getSystemService(PermissionManager::class.java)
        mAttributionSource.set(mPermissionManager.registerAttributionSource(myAttributionSource()))
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        mScope.launch {
            val recordId = intent.getIntExtra(EXTRA_RECORD_ID, 0)
            Log.i(TAG, "Receive onStartCommand action: ${intent.getAction()}, id: $recordId")
            when (intent.getAction()) {
                PREFIX + ACTION_START_RECORD -> {
                    intent.getExtras()
                        ?.getBinder(EXTRA_ATTRIBUTION)
                        ?.let(IAttrProvider.Stub::asInterface)
                        ?.let { getAttribution(it) }
                        ?.let { next ->
                            mAttributionSource.get().let { old ->
                                    mPermissionManager.registerAttributionSource(
                                        AttributionSource.Builder(old.getUid())
                                                .setPackageName(old.getPackageName())
                                                .setNext(next)
                                                .build())
                            }
                        }
                        ?.let { mAttributionSource.set(it) }
                    mRecordings
                        .getOrPut(recordId) {
                            // Create the channel, kick off the record  and insert into map
                            Channel<Boolean>(Channel.UNLIMITED).also {
                                // IO for unbounded thread-pool, thread per record
                                launch(CoroutineName("Record $recordId") + Dispatchers.IO) {
                                    record(recordId, it)
                                }
                            }
                        }
                        .send(true)
                }
                PREFIX + ACTION_STOP_RECORD ->
                    mRecordings.get(intent.getIntExtra(EXTRA_RECORD_ID, 0))?.send(false)
                PREFIX + ACTION_FINISH_RECORD ->
                    mRecordings.get(intent.getIntExtra(EXTRA_RECORD_ID, 0))?.close()
                PREFIX + ACTION_START_FOREGROUND ->
                    intent.getIntExtra(EXTRA_CAP_OVERRIDE, getCapabilities()).let {
                        Log.i(TAG, "Going foreground with capabilities $it")
                        startForeground(1, buildNotification(), it)
                    }
                PREFIX + ACTION_STOP_FOREGROUND -> stopForeground(STOP_FOREGROUND_REMOVE)
                PREFIX + ACTION_TEARDOWN -> {
                    // Finish ongoing records
                    mRecordings.values.forEach { it.close() }
                    mRecordings.clear()
                    // Mark supervisor complete, completer will fire when all children complete.
                    mJob.complete()
                }
                PREFIX + ACTION_REQUEST_ATTRIBUTION ->
                    sendBroadcast(
                        Intent(PREFIX + ACTION_SEND_ATTRIBUTION).apply {
                            setPackage(TARGET_PACKAGE)
                            putExtras(Bundle().apply {
                                putBinder(EXTRA_ATTRIBUTION, object: IAttrProvider.Stub() {
                                    override fun inject(x: IAttrConsumer) = x.provideAttribution(
                                            mAttributionSource.get())
                                })
                            })
                        })
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        mJob.cancel()
    }

    // Binding cannot be used since that affects the proc state
    override fun onBind(intent: Intent): IBinder? = null

    override fun getAttributionSource(): AttributionSource = mAttributionSource.get()

    /** For subclasses to return the package name for receiving intents. */
    open fun getAppName(): String = "Base"

    /** For subclasses to return the capabilities to start the service with. */
    open fun getCapabilities(): Int = 0

    private fun respond(action: String, recordId: Int? = null) {
        Log.i(TAG, "Sending $action for id: $recordId")
        sendBroadcast(
            Intent(PREFIX + action).apply {
                setPackage(TARGET_PACKAGE)
                recordId?.let { putExtra(EXTRA_RECORD_ID, it) }
            })
    }

    /**
     * Continuously record while {@link mIsRecording} is true. Returns when false. Send intents as
     * stream moves in and out of being silenced.
     */
    suspend fun record(recordId: Int, channel: ReceiveChannel<Boolean>) {
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val sampleRate = 32000
        val RECORD_WARMUP = 800 // 25ms
        val format = AudioFormat.ENCODING_PCM_16BIT
        val bufferSizeInBytes = 2 * AudioRecord.getMinBufferSize(sampleRate, channelConfig, format)
        val audioRecord =
            AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(format)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build())
                .setBufferSizeInBytes(bufferSizeInBytes)
                .setContext(this)
                .build()

        var isSilenced: Boolean? = null
        var isRecording = false
        val data = ShortArray(bufferSizeInBytes / 2)
        try {
            while (coroutineContext.isActive) {
                val newIsRecording = computeNextRecording(channel, isRecording) ?: break
                if (!isRecording && newIsRecording) {
                    audioRecord.startRecording()
                    var warmupFrames = 0
                    while (warmupFrames < RECORD_WARMUP) {
                        warmupFrames += audioRecord.read(data, 0, data.size).also {
                            if (it < 0) throw IllegalStateException("AudioRecord read invalid $it")
                        }
                    }
                    mScope.launch { respond(ACTION_RECORD_STARTED, recordId) }
                } else if (isRecording && !newIsRecording) {
                    audioRecord.stop()
                    mScope.launch { respond(ACTION_RECORD_STOPPED, recordId) }
                    isSilenced = null
                }
                isRecording = newIsRecording
                if (isRecording) {
                    isAudioRecordSilenced(audioRecord, data)?.let { newIsSilenced ->
                        if (isSilenced != newIsSilenced) {
                            mScope.launch {
                                respond(
                                    if (newIsSilenced) ACTION_BEGAN_RECEIVE_SILENCE
                                    else ACTION_BEGAN_RECEIVE_AUDIO,
                                    recordId)
                            }
                        }
                        isSilenced = newIsSilenced
                    }
                }
            }
        } finally {
            if (isRecording) {
                audioRecord.stop()
                mScope.launch { respond(ACTION_RECORD_STOPPED, recordId) }
            }
            audioRecord.release()
            mScope.launch { respond(ACTION_RECORD_FINISHED, recordId) }
        }
    }

    private fun getAttribution(prov: IAttrProvider) : AttributionSource {
        val res = CompletableFuture<AttributionSource>()
        prov.inject(object : IAttrConsumer.Stub() {
            override fun provideAttribution(attr: AttributionSource) = res.complete(attr).let {}
        })
        return res.get().also {
            Log.i(TAG, "Received attr source ${it}")
        }
    }

    /**
     * Consume the data in the channel, and based on the current recording state return the next
     * state. Returns null to represent ending the task. If not isRecording, block until new data is
     * available in the channel
     */
    private suspend fun computeNextRecording(
        channel: ReceiveChannel<Boolean>,
        isRecording: Boolean
    ): Boolean? =
        channel.tryReceive().run {
            when {
                isClosed -> null
                // no update: only wait for state update if we are NOT recording
                isFailure ->
                    isRecording ||
                        try {
                            channel.receive()
                        } catch (e: ClosedReceiveChannelException) {
                            return null
                        }
                // This shouldn't throw now. Non-blocking read of the record state
                else -> getOrThrow()
            }
        }

    /**
     * Determine if the audiorecord is silenced.
     *
     * @param audioRecord the recording to evaluate
     * @param data temp data buffer to use
     * @return true if silenced, false if not silenced, null if no data
     */
    private fun isAudioRecordSilenced(audioRecord: AudioRecord, data: ShortArray): Boolean? =
        audioRecord.read(data, 0, data.size).let {
            when {
                it == 0 -> null
                it < 0 -> throw IllegalStateException("AudioRecord read invalid result: $it")
                else -> (data.take(it).map { abs(it.toInt()) }.sum() == 0)
            }
        }

    /** Create a notification which is required to start a foreground service */
    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel("all", "All Notifications", NotificationManager.IMPORTANCE_NONE))

        return Notification.Builder(this, "all")
            .setContentTitle("Recording audio")
            .setContentText("recording...")
            .setSmallIcon(R.drawable.ic_fg)
            .build()
    }
}
