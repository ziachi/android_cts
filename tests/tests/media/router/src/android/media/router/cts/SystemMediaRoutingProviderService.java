/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.media.router.cts;

import static android.media.MediaRoute2Info.FLAG_ROUTING_TYPE_REMOTE;
import static android.media.MediaRoute2Info.FLAG_ROUTING_TYPE_SYSTEM_AUDIO;
import static android.media.MediaRoute2Info.PLAYBACK_VOLUME_VARIABLE;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRoute2Info;
import android.media.MediaRoute2ProviderService;
import android.media.RouteDiscoveryPreference;
import android.media.RoutingSessionInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.media.flags.Flags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.GuardedBy;

/**
 * {@link MediaRoute2ProviderService} implementation that serves routes that support system media.
 *
 * @see MediaRoute2Info#getSupportedRoutingTypes()
 * @see MediaRoute2ProviderService#onCreateSystemRoutingSession
 */
public class SystemMediaRoutingProviderService extends MediaRoute2ProviderService {

    /** The format to pass to {@link #notifySystemRoutingSessionCreated}. */
    private static final AudioFormat AUDIO_FORMAT_RECORDING =
            new AudioFormat.Builder()
                    .setSampleRate(44100 /*Hz*/)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build();

    public static final String FEATURE_SAMPLE = "android.media.router.cts.FEATURE_SAMPLE";
    public static final String ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_1 =
            "ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_1";
    public static final String ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_2 =
            "ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_2";
    public static final String ROUTE_ID_ONLY_REMOTE = "ROUTE_ID_ONLY_REMOTE";
    public static final String ROUTE_ID_BOTH_SYSTEM_AND_REMOTE = "ROUTE_ID_BOTH_SYSTEM_AND_REMOTE";
    public static final String ROUTE_ID_ONLY_SYSTEM_AUDIO_SELECTABLE =
            "ROUTE_ID_ONLY_SYSTEM_AUDIO_GROUPABLE";
    public static final int VOLUME_MAX = 100;
    public static final int INITIAL_VOLUME = 50;
    private static final String ROUTING_SESSION_ID = "ROUTING_SESSION_ID";

    /**
     * Holds the ids of the routes from this provider that support transferring to each other.
     *
     * @see RoutingSessionInfo#getTransferableRoutes()
     */
    private static final Set<String> TRANSFERABLE_ROUTES =
            Set.of(
                    ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_1,
                    ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_2);

    private static final Object sLock = new Object();

    /** Maps route ids to routes. */
    @GuardedBy("sLock")
    private final Map<String, MediaRoute2Info> mRoutes = new HashMap<>();

    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private final byte[] mBuffer = new byte[1024 * 100];

    @GuardedBy("sLock")
    private RoutingSessionInfo mCurrentRoutingSession = null;

    private final AtomicInteger mNoisyByteCount = new AtomicInteger(0);

    @GuardedBy("sLock")
    private static SystemMediaRoutingProviderService sInstance;

    /** Returns the currently running service instance, or null if the service is not running. */
    public static SystemMediaRoutingProviderService getInstance() {
        synchronized (sLock) {
            return sInstance;
        }
    }

    /**
     * Returns the {@link MediaRoute2Info#getOriginalId() original id} of the currently selected
     * route, or null if there's no selected route at the moment.
     */
    @Nullable
    public String getSelectedRouteOriginalId() {
        synchronized (sLock) {
            return mCurrentRoutingSession != null
                    ? mCurrentRoutingSession.getSelectedRoutes().getFirst()
                    : null;
        }
    }

    /**
     * Returns the number of non-zero bytes read from audio record since the {@link
     * #getSelectedRouteOriginalId currently selected route} became selected.
     */
    public int getNoisyBytesCount() {
        return mNoisyByteCount.get();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mHandlerThread = new HandlerThread(getClass().getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mNoisyByteCount.set(0);
        synchronized (sLock) {
            sInstance = this;
        }
    }

    @Override
    public void onDestroy() {
        synchronized (sLock) {
            sInstance = null;
        }
        mHandlerThread.quitSafely();
        super.onDestroy();
    }

    @Override
    public void onSetRouteVolume(long requestId, @NonNull String routeId, int volume) {
        synchronized (sLock) {
            var route = mRoutes.get(routeId);
            if (route == null
                    || mCurrentRoutingSession == null
                    || !mCurrentRoutingSession.getSelectedRoutes().contains(routeId)) {
                notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE);
                return;
            }
            MediaRoute2Info newRoute = new MediaRoute2Info.Builder(route).setVolume(volume).build();
            mRoutes.put(routeId, newRoute);
            notifyRoutes(mRoutes.values());
        }
    }

    @Override
    public void onSetSessionVolume(long requestId, @NonNull String sessionId, int volume) {
        synchronized (sLock) {
            if (mCurrentRoutingSession == null
                    || !mCurrentRoutingSession.getOriginalId().equals(sessionId)) {
                notifyRequestFailed(requestId, REASON_INVALID_COMMAND);
                return;
            }
            mCurrentRoutingSession =
                    new RoutingSessionInfo.Builder(mCurrentRoutingSession)
                            .setVolume(volume)
                            .build();
            notifySessionUpdated(mCurrentRoutingSession);
        }
    }

    @Override
    public void onCreateSession(
            long requestId,
            @NonNull String packageName,
            @NonNull String routeId,
            @Nullable Bundle sessionHints) {
        throw new IllegalStateException(
                "Unexpected: This provider only expects system-media routing.");
    }

    @Override
    public void onReleaseSession(long requestId, @NonNull String sessionId) {
        mHandler.post(() -> releaseSessionOnHandler(sessionId));
    }

    @Override
    public void onSelectRoute(long requestId, @NonNull String sessionId, @NonNull String routeId) {
        synchronized (sLock) {
            if (mCurrentRoutingSession == null
                    || !mCurrentRoutingSession.getSelectableRoutes().contains(routeId)) {
                notifyRequestFailed(requestId, REASON_INVALID_COMMAND);
                return;
            }
            if (!mRoutes.containsKey(routeId)) {
                notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE);
                return;
            }
            mCurrentRoutingSession =
                    new RoutingSessionInfo.Builder(mCurrentRoutingSession)
                            .removeSelectableRoute(routeId)
                            .addSelectedRoute(routeId)
                            .addDeselectableRoute(routeId)
                            .build();
            notifySessionUpdated(mCurrentRoutingSession);
        }
    }

    @Override
    public void onDeselectRoute(
            long requestId, @NonNull String sessionId, @NonNull String routeId) {
        synchronized (sLock) {
            if (mCurrentRoutingSession == null
                    || !mCurrentRoutingSession.getDeselectableRoutes().contains(routeId)) {
                notifyRequestFailed(requestId, REASON_INVALID_COMMAND);
                return;
            }
            if (!mRoutes.containsKey(routeId)) {
                notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE);
                return;
            }
            var selectedRoutes = mCurrentRoutingSession.getSelectedRoutes();
            if (!selectedRoutes.contains(routeId) || selectedRoutes.size() == 1) {
                // We don't expect this condition to be ever true, as that would mean that there's
                // an implementation error in this provider. But we add this check for robustness.
                notifyRequestFailed(requestId, REASON_UNKNOWN_ERROR);
                return;
            }
            mCurrentRoutingSession =
                    new RoutingSessionInfo.Builder(mCurrentRoutingSession)
                            .removeSelectedRoute(routeId)
                            .removeDeselectableRoute(routeId)
                            .addSelectableRoute(routeId)
                            .build();
            notifySessionUpdated(mCurrentRoutingSession);
        }
    }

    @Override
    public void onTransferToRoute(
            long requestId, @NonNull String sessionId, @NonNull String routeId) {
        synchronized (sLock) {
            if (mCurrentRoutingSession == null
                    || !TextUtils.equals(mCurrentRoutingSession.getOriginalId(), sessionId)) {
                // Unexpected call to transfer or invalid id received.
                notifyRequestFailed(requestId, REASON_INVALID_COMMAND);
                return;
            }
            if (!mRoutes.containsKey(routeId)) {
                notifyRequestFailed(requestId, REASON_ROUTE_NOT_AVAILABLE);
                return;
            }
            String clientPackageName = mCurrentRoutingSession.getClientPackageName();
            mHandler.post(() -> transferToRouteOnHandler(clientPackageName, routeId));
        }
    }

    @Override
    public void onCreateSystemRoutingSession(
            long requestId,
            @NonNull String routeId,
            @NonNull SystemRoutingSessionParams parameters) {
        var routingSession =
                createRoutingSession(parameters.getPackageName(), /* selectedRouteId= */ routeId);
        var streams =
                notifySystemRoutingSessionCreated(
                        requestId,
                        routingSession,
                        new MediaStreamsFormats.Builder()
                                .setAudioFormat(AUDIO_FORMAT_RECORDING)
                                .build());
        var audioRecord = streams.getAudioRecord();
        if (audioRecord != null) {
            audioRecord.startRecording();
            mNoisyByteCount.set(0);
            synchronized (sLock) {
                mCurrentRoutingSession = routingSession;
            }
            mHandler.post(() -> readFromAudioRecordOnHandler(audioRecord));
        }
    }

    /** Creates a routing session with a single given selected route. */
    private static RoutingSessionInfo createRoutingSession(
            String clientPackageName, String selectedRouteId) {
        boolean isInTransferableRoutes = TRANSFERABLE_ROUTES.contains(selectedRouteId);
        var transferableRoutes =
                new ArrayList<>(isInTransferableRoutes ? TRANSFERABLE_ROUTES : Set.of());
        // A route should not be transferable and also selected.
        transferableRoutes.remove(selectedRouteId);
        var sessionBuilder =
                new RoutingSessionInfo.Builder(ROUTING_SESSION_ID, clientPackageName)
                        .setVolumeHandling(PLAYBACK_VOLUME_VARIABLE)
                        .setVolumeMax(VOLUME_MAX)
                        .setVolume(INITIAL_VOLUME)
                        .addSelectedRoute(selectedRouteId);
        if (!selectedRouteId.equals(ROUTE_ID_ONLY_SYSTEM_AUDIO_SELECTABLE)) {
            sessionBuilder.addSelectableRoute(ROUTE_ID_ONLY_SYSTEM_AUDIO_SELECTABLE);
        }
        transferableRoutes.forEach(sessionBuilder::addTransferableRoute);
        return sessionBuilder.build();
    }

    @Override
    public void onDiscoveryPreferenceChanged(@NonNull RouteDiscoveryPreference preference) {
        synchronized (sLock) {
            if (preference.shouldPerformActiveScan()) {
                populateRoutes();
            } else if (mCurrentRoutingSession == null) {
                mRoutes.clear();
            } else {
                // We must keep routes that are in a routing session, even while not scanning.
                mRoutes.keySet()
                        .removeIf(it -> !mCurrentRoutingSession.getSelectedRoutes().contains(it));
            }
            notifyRoutes(mRoutes.values());
        }
    }

    @GuardedBy("sLock")
    private void populateRoutes() {
        if (!Flags.enableMirroringInMediaRouter2()) {
            return;
        }
        synchronized (sLock) {
            mRoutes.clear();
            registerRoute(
                    ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_1, FLAG_ROUTING_TYPE_SYSTEM_AUDIO);
            registerRoute(
                    ROUTE_ID_ONLY_SYSTEM_AUDIO_TRANSFERABLE_2, FLAG_ROUTING_TYPE_SYSTEM_AUDIO);
            registerRoute(
                    ROUTE_ID_BOTH_SYSTEM_AND_REMOTE,
                    FLAG_ROUTING_TYPE_SYSTEM_AUDIO | FLAG_ROUTING_TYPE_REMOTE);
            registerRoute(ROUTE_ID_ONLY_REMOTE, FLAG_ROUTING_TYPE_REMOTE);
            registerRoute(ROUTE_ID_ONLY_SYSTEM_AUDIO_SELECTABLE, FLAG_ROUTING_TYPE_SYSTEM_AUDIO);
        }
    }

    /**
     * Creates and registers a route with the given properties.
     *
     * <p>For convenience we use the id as the name of the route as well. We don't need a human
     * readable string for the test route names.
     */
    @GuardedBy("sLock")
    private void registerRoute(
            String idAndName, @MediaRoute2Info.RoutingType int supportedRoutingTypes) {
        var route =
                new MediaRoute2Info.Builder(/* id= */ idAndName, /* name= */ idAndName)
                        .addFeature(FEATURE_SAMPLE)
                        .setVolumeHandling(PLAYBACK_VOLUME_VARIABLE)
                        .setVolumeMax(VOLUME_MAX)
                        .setVolume(INITIAL_VOLUME)
                        .setSupportedRoutingTypes(supportedRoutingTypes)
                        .setDeduplicationIds(Set.of(idAndName))
                        .build();
        // We only put it if not already there, to avoid overriding a "mutable" property, such as
        // volume.
        mRoutes.putIfAbsent(route.getOriginalId(), route);
    }

    private void transferToRouteOnHandler(String packageName, String routeId) {
        var updatedSession = createRoutingSession(packageName, routeId);
        synchronized (sLock) {
            mCurrentRoutingSession = updatedSession;
        }
        mNoisyByteCount.set(0);
        notifySessionUpdated(updatedSession);
    }

    private void readFromAudioRecordOnHandler(AudioRecord audioRecord) {
        int bytesRead =
                audioRecord.read(
                        mBuffer,
                        /* offsetInBytes= */ 0,
                        mBuffer.length,
                        AudioRecord.READ_NON_BLOCKING);
        for (int i = 0; i < bytesRead; i++) {
            if (mBuffer[i] != 0) {
                mNoisyByteCount.incrementAndGet();
            }
        }
        mHandler.post(() -> readFromAudioRecordOnHandler(audioRecord));
    }

    private void releaseSessionOnHandler(String sessionId) {
        mHandler.removeCallbacksAndMessages(/* token= */ null);
        synchronized (sLock) {
            mCurrentRoutingSession = null;
        }
        mNoisyByteCount.set(0);
        notifySessionReleased(sessionId);
    }
}
