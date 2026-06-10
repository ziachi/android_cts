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

package android.media.tv.ad.cts;


import android.content.Context;
import android.graphics.Rect;
import android.media.tv.TvTrackInfo;
import android.media.tv.ad.TvAdService;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Stub implementation of {@link android.media.tv.ad.TvAdService}.
 */
public class StubTvAdService extends TvAdService {

    public static Bundle sAppLinkCommand = null;

    public static StubSessionImpl sSession;
    @Nullable
    @Override
    public Session onCreateSession(@NonNull String serviceId, @NonNull String type) {
        sSession = new StubSessionImpl(this);
        return sSession;
    }

    @Override
    public void onAppLinkCommand(Bundle command) {
        super.onAppLinkCommand(command);
        sAppLinkCommand = command;
    }

    public static class StubSessionImpl extends TvAdService.Session {
        public int mStartAdServiceCount;
        public int mStopAdServiceCount;
        public int mCurrentVideoBoundsCount;
        public int mCurrentTvInputIdCount;
        public int mSigningResultCount;
        public int mCurrentChannelUriCount;
        public int mTrackInfoListCount;
        public int mTvMessageCount;
        public int mErrorCount;
        public int mResetAdServiceCount;
        public byte[] mSigningResultByte;
        public Boolean mMediaViewEnabled;
        public int mMediaViewEnabledCount;
        public int mMediaViewSizeChangedCount;
        public Integer mMediaViewWidth;
        public Integer mMediaViewHeight;
        public int mKeyDownCount;
        public int mKeyLongPressCount;
        public int mKeyMultipleCount;
        public int mKeyUpCount;
        public Integer mKeyDownCode;
        public Integer mKeyLongPressCode;
        public Integer mKeyMultipleCode;
        public Integer mKeyUpCode;
        public int mOnTvInputSessionDataCount;
        public String mCurrentTvInputId;
        public Rect mCurrentVideoBounds;
        public Uri mCurrentChannelUri;
        public Integer mTvMessageType;
        public Bundle mTvMessageData;
        public String mSigningResultId;
        public List<TvTrackInfo> mTvTrackInfo;
        public String mErrMessage;
        public Bundle mErrBundle;
        public KeyEvent mKeyDownEvent;
        public KeyEvent mKeyLongPressEvent;
        public KeyEvent mKeyMultipleEvent;
        public KeyEvent mKeyUpEvent;
        public String mOnTvInputSessionDataType;
        public Bundle mOnTvInputSessionDataBundle;

        /**
         * Creates a new Session.
         *
         * @param context The context of the application
         */
        public StubSessionImpl(@NonNull Context context) {
            super(context);
        }

        /**
         * Resets values.
         */
        public void resetValues() {
            mStartAdServiceCount = 0;
            mStopAdServiceCount = 0;
            mCurrentVideoBoundsCount = 0;
            mCurrentTvInputIdCount = 0;
            mSigningResultCount = 0;
            mCurrentChannelUriCount = 0;
            mTrackInfoListCount = 0;
            mTvMessageCount = 0;
            mErrorCount = 0;
            mResetAdServiceCount = 0;
            mMediaViewEnabled = null;
            mMediaViewEnabledCount = 0;
            mMediaViewSizeChangedCount = 0;
            mMediaViewWidth = null;
            mMediaViewHeight = null;
            mKeyDownCount = 0;
            mKeyLongPressCount = 0;
            mKeyMultipleCount = 0;
            mKeyUpCount = 0;
            mKeyDownCode = null;
            mKeyLongPressCode = null;
            mKeyMultipleCode = null;
            mKeyUpCode = null;
            mOnTvInputSessionDataCount = 0;
            mCurrentTvInputId = null;
            mCurrentVideoBounds = null;
            mCurrentChannelUri = null;
            mTvMessageType = null;
            mTvMessageData = null;
            mSigningResultByte = null;
            mSigningResultId = null;
            mTvTrackInfo = null;
            mErrMessage = null;
            mErrBundle = null;
            mOnTvInputSessionDataType = null;
            mOnTvInputSessionDataBundle = null;
            mKeyDownEvent = null;
            mKeyLongPressEvent = null;
            mKeyMultipleEvent = null;
            mKeyUpEvent = null;
        }

        @Override
        public void onRelease() {
        }

        @Override
        public boolean onSetSurface(@Nullable Surface surface) {
            return false;
        }

        @Override
        public void onStartAdService() {
            mStartAdServiceCount++;
        }

        @Override
        public void onStopAdService() {
            mStopAdServiceCount++;
        }

        @Override
        public void onCurrentVideoBounds(Rect rect) {
            super.onCurrentVideoBounds(rect);
            mCurrentVideoBoundsCount++;
            mCurrentVideoBounds = rect;
        }

        @Override
        public void onCurrentTvInputId(String inputId) {
            super.onCurrentTvInputId(inputId);
            mCurrentTvInputIdCount++;
            mCurrentTvInputId = inputId;
        }

        @Override
        public void onCurrentChannelUri(Uri channelUri) {
            super.onCurrentChannelUri(channelUri);
            mCurrentChannelUriCount++;
            mCurrentChannelUri = channelUri;
        }

        @Override
        public void onSigningResult(String signingId, byte[] result) {
            super.onSigningResult(signingId, result);
            mSigningResultCount++;
            mSigningResultId = signingId;
            mSigningResultByte = result;
        }

        @Override
        public void onTrackInfoList(List<TvTrackInfo> info) {
            super.onTrackInfoList(info);
            mTrackInfoListCount++;
            mTvTrackInfo = info;
        }

        @Override
        public void onTvMessage(int type, Bundle data) {
            super.onTvMessage(type, data);
            mTvMessageCount++;
            mTvMessageType = type;
            mTvMessageData = data;
        }

        @Override
        public void onResetAdService() {
            super.onResetAdService();
            mResetAdServiceCount++;
        }

        @Override
        public void onError(String errMsg, Bundle params) {
            super.onError(errMsg, params);
            mErrorCount++;
            mErrMessage = errMsg;
            mErrBundle = params;
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            super.onKeyDown(keyCode, event);
            mKeyDownCount++;
            mKeyDownCode = keyCode;
            mKeyDownEvent = event;
            return false;
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            super.onKeyUp(keyCode, event);
            mKeyUpCount++;
            mKeyUpCode = keyCode;
            mKeyUpEvent = event;
            return false;
        }

        @Override
        public boolean onKeyLongPress(int keyCode, KeyEvent event) {
            super.onKeyLongPress(keyCode, event);
            mKeyLongPressCount++;
            mKeyLongPressCode = keyCode;
            mKeyLongPressEvent = event;
            return false;
        }

        @Override
        public boolean onKeyMultiple(int keyCode, int repeatCnt, KeyEvent event) {
            super.onKeyDown(keyCode, event);
            mKeyMultipleCount++;
            mKeyMultipleCode = keyCode;
            mKeyMultipleEvent = event;
            return false;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            return false;
        }

        @Override
        public boolean onTrackballEvent(MotionEvent event) {
            super.onTrackballEvent(event);
            return false;
        }

        @Override
        public boolean onGenericMotionEvent(MotionEvent event) {
            super.onGenericMotionEvent(event);
            return false;
        }

        @Override
        public boolean isMediaViewEnabled() {
            super.isMediaViewEnabled();
            mMediaViewEnabledCount++;
            return true;
        }

        @Override
        public View onCreateMediaView() {
            super.onCreateMediaView();
            mMediaViewEnabledCount++;
            return null;
        }

        @Override
        public void setMediaViewEnabled(boolean enable) {
            super.setMediaViewEnabled(enable);
            mMediaViewEnabledCount++;
        }

        @Override
        public void onMediaViewSizeChanged(int w, int h) {
            super.onMediaViewSizeChanged(w, h);
            mMediaViewSizeChangedCount++;
            mMediaViewWidth = w;
            mMediaViewHeight = h;
        }

        @Override
        public void onTvInputSessionData(String type, Bundle bundle) {
            super.onTvInputSessionData(type, bundle);
            mOnTvInputSessionDataCount++;
            mOnTvInputSessionDataType = type;
            mOnTvInputSessionDataBundle = bundle;
        }
    }
}
