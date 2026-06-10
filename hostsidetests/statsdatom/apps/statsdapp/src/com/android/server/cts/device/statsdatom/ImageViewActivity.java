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
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;

public class ImageViewActivity extends Activity {
    private static final String TAG = "ImageViewActivity";
    private static final String IMAGE_NAME = "red-hlg-profile.png";

    static {
        System.loadLibrary("imagedecoderhelper");
    }

    private Bitmap mBitmap;
    private ImageView mImageView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        mImageView = new ImageView(this);
        mImageView.setImageDrawable(null);
        setContentView(mImageView);
    }

    @Override
    public void onEnterAnimationComplete() {
        decodeImage(IMAGE_NAME);
    }

    private void decodeImage(String name) {
        AssetManager assets = getResources().getAssets();
        ImageDecoder.Source src = ImageDecoder.createSource(assets, IMAGE_NAME);
        try {
            mBitmap = ImageDecoder.decodeBitmap(src);
            Log.d(TAG, "Assigning bitmap from view");
            mImageView.setImageBitmap(mBitmap);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        nDecode(getAssets(), IMAGE_NAME);

        try {
            BitmapFactory.decodeStream(assets.open(IMAGE_NAME));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        mImageView.getViewTreeObserver().registerFrameCommitCallback(() -> {
            Log.d(TAG, "Removing bitmap from view");
            mImageView.setImageBitmap(null);
            mImageView.getViewTreeObserver().registerFrameCommitCallback(() -> {
                Log.d(TAG, "Assigning bitmap to view");
                mImageView.setImageBitmap(mBitmap);
                mImageView.getViewTreeObserver().registerFrameCommitCallback(this::finish);
            });
        });
    }

    private native void nDecode(AssetManager assetManager, String name);
}
