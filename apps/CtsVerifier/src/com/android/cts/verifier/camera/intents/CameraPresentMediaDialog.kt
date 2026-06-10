/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.cts.verifier.camera.intents

import android.app.Dialog
import android.app.DialogFragment
import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.extractor.metadata.mp4.MotionPhotoMetadata
import androidx.media3.ui.PlayerView
import com.android.cts.verifier.R
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import java.util.concurrent.Executors

/** This dialog shows/plays an image, motion photo, or video uri. */
public class CameraPresentMediaDialog : DialogFragment() {
  private lateinit var playerView: PlayerView
  private lateinit var exoPlayer: ExoPlayer
  private val executor = Executors.newSingleThreadExecutor()

  override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
    val dialog = Dialog(activity)
    dialog.setContentView(R.layout.ci_present_media)

    dialog.setTitle(getString(R.string.ci_verify_capture_title))
    val uri = arguments.getParcelable<Uri>(KEY_URI)!!

    playerView = dialog.findViewById<PlayerView>(R.id.player_view)
    exoPlayer = ExoPlayer.Builder(activity).build()
    playerView.player = exoPlayer
    val mediaItem = MediaItem.fromUri(uri)
    exoPlayer.setMediaItem(mediaItem)
    exoPlayer.repeatMode = ExoPlayer.REPEAT_MODE_ONE
    exoPlayer.playWhenReady = true
    exoPlayer.prepare()

    val metadataFuture = MetadataRetriever.retrieveMetadata(activity, mediaItem)
    Futures.addCallback(
        metadataFuture,
        object : FutureCallback<TrackGroupArray?> {
          override fun onSuccess(trackGroups: TrackGroupArray?) {
            var containsMotionPhotoMetadata = false
            trackGroups?.let {
              containsMotionPhotoMetadata =
                  0.until(trackGroups.length)
                      .asSequence()
                      .mapNotNull { trackGroups[it].getFormat(0).metadata }
                      .filter { metadata -> metadata.length() == 1 }
                      .map { metadata -> metadata[0] }
                      .filterIsInstance<MotionPhotoMetadata>()
                      .any()
            }

            val dismissButton = dialog.findViewById<Button>(R.id.dismiss_button)
            dismissButton.setOnClickListener({ v: View ->
              dismiss()
              (activity as DialogCallback).onDialogClose(containsMotionPhotoMetadata)
            })
          }

          override fun onFailure(t: Throwable) {
            Log.e(TAG, "Failed to retrieve metadata", t)
          }
        },
        executor,
    )

    return dialog
  }

  override fun onCancel(dialog: DialogInterface) {
    (activity as DialogCallback).onDialogClose(containsMotionPhotoMetadata = false)
  }

  interface DialogCallback {
    fun onDialogClose(containsMotionPhotoMetadata: Boolean)
  }

  companion object {
    val TAG = CameraPresentMediaDialog::class.java.simpleName

    const val KEY_URI = "uri"

    /** Get a dialogFragment showing media. */
    @JvmStatic
    fun newInstance(uri: Uri): CameraPresentMediaDialog {
      val dialog = CameraPresentMediaDialog()
      val args = Bundle()
      args.putParcelable(KEY_URI, uri)
      dialog.setArguments(args)
      return dialog
    }
  }
}
