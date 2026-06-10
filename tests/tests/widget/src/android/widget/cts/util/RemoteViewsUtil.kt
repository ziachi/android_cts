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

package android.widget.cts.util

import android.appwidget.flags.Flags
import android.content.Context
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.util.SizeF
import android.util.proto.ProtoInputStream
import android.util.proto.ProtoOutputStream
import android.view.View
import android.widget.RemoteViews
import androidx.test.rule.ActivityTestRule
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assume

object RemoteViewsUtil {

    @JvmStatic
    fun recreateFromProto(context: Context, views: RemoteViews): RemoteViews {
        val output = ProtoOutputStream()
        views.writePreviewToProto(context, output)
        val input = ProtoInputStream(output.bytes)
        return RemoteViews.createPreviewFromProto(context, input)
    }

    @JvmOverloads
    @JvmStatic
    @Suppress("deprecation")
    fun applyRemoteViews(
        activityRule: ActivityTestRule<*>,
        context: Context,
        remoteViews: RemoteViews,
        isProtoTest: Boolean,
        initialSize: SizeF? = null,
        colorResources: RemoteViews.ColorResources? = null,
    ): View {
        val viewsToApply = if (isProtoTest) {
            recreateFromProto(context, remoteViews)
        } else {
            remoteViews
        }
        val result = AtomicReference<View>()
        activityRule.runOnUiThread {
            result.set(
                viewsToApply.apply(context, null, null, initialSize, colorResources)
            )
        }
        return result.get()
    }

    @JvmOverloads
    @JvmStatic
    @Suppress("deprecation")
    fun reapplyRemoteViews(
        activityRule: ActivityTestRule<*>,
        context: Context,
        remoteViews: RemoteViews,
        root: View,
        isProtoTest: Boolean,
        initialSize: SizeF? = null,
        async: Boolean = false,
    ) {
        val viewsToApply = if (isProtoTest) {
            recreateFromProto(context, remoteViews)
        } else {
            remoteViews
        }
        activityRule.runOnUiThread {
            if (async) {
                viewsToApply.reapplyAsync(context, root, null, null)
            } else {
                viewsToApply.reapply( context, root, null, initialSize, null)
            }
        }
    }

    /**
     * Skips the proto test if the RemoteViews proto flag is not enabled.
     */
    @JvmStatic
    fun checkRemoteViewsProtoFlag(
        isProtoTest: Boolean
    ) {
        if (isProtoTest) {
            Assume.assumeTrue(
                String.format(
                    "Flag %s must be enabled to run the proto test",
                    Flags.FLAG_REMOTE_VIEWS_PROTO,
                ),
                DeviceFlagsValueProvider().getBoolean(Flags.FLAG_REMOTE_VIEWS_PROTO)
            )
        }
    }
}
