/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.server.cts.device.statsdatom;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.HardwareBufferRenderer;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.DataSpace;
import android.hardware.HardwareBuffer;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.media.Image;
import android.media.ImageWriter;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;


public class TextureViewActivity extends Activity implements SurfaceTextureListener  {
    private static final String TAG = "TextureViewActivity";
    private static final int MAX_SRGB_FRAMES = 3;
    private static final int MAX_P3_FRAMES = 2;
    private static final int BUFFER_DIMENSION = 25;

    private FrameLayout mLayout;
    private TextureView mTextureView;
    private ImageWriter mWriter;
    private int mFrameCount;
    private int mDataSpace;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        mTextureView = new TextureView(this);
        mTextureView.setSurfaceTextureListener(this);
        mLayout = new FrameLayout(this);
        mLayout.addView(mTextureView,
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        setContentView(mLayout);
    }

    private void pushFrame() {
        if (mFrameCount >= MAX_SRGB_FRAMES + MAX_P3_FRAMES) {
            Log.d(TAG, "Done pushing frames");
            getMainExecutor().execute(() -> mLayout.removeView(mTextureView));
            return;
        }
        if (mWriter == null) {
            Log.d(TAG, "No image writer set up!");
            return;
        }
        if (mFrameCount >= MAX_SRGB_FRAMES) {
            mDataSpace = DataSpace.DATASPACE_DISPLAY_P3;
        }
        Log.d(TAG, "Pushing frame " + mFrameCount + " with dataspace " + mDataSpace);
        Image image = mWriter.dequeueInputImage();
        image.setDataSpace(mDataSpace);
        Image.Plane plane = image.getPlanes()[0];
        Bitmap bitmap = Bitmap.createBitmap(plane.getRowStride() / 4, image.getHeight(),
                Bitmap.Config.ARGB_8888, true, ColorSpace.getFromDataSpace(mDataSpace));
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.RED);
        bitmap.copyPixelsToBuffer(plane.getBuffer());
        mWriter.queueInputImage(image);
        mFrameCount += 1;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "onSurfaceTextureAvailable");
        mDataSpace = DataSpace.DATASPACE_SRGB;

        mWriter = new ImageWriter
                        .Builder(new Surface(surface))
                        .setHardwareBufferFormat(PixelFormat.RGBA_8888)
                        .setDataSpace(mDataSpace)
                        .setWidthAndHeight(BUFFER_DIMENSION, BUFFER_DIMENSION)
                        .build();

        pushFrame();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mWriter = null;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        pushFrame();
    }

}
