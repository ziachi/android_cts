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

package android.companion.cts.multidevice

import android.app.Instrumentation
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.companion.cts.common.CompanionActivity
import android.companion.cts.common.PrimaryCompanionService
import android.companion.cts.multidevice.CallbackUtils.SystemDataTransferCallback
import android.companion.cts.uicommon.CompanionDeviceManagerUi
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerExecutor
import android.os.HandlerThread
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.android.mobly.snippet.Snippet
import com.google.android.mobly.snippet.rpc.Rpc
import java.util.concurrent.Executor

/**
 * Snippet class that exposes Android APIs in CompanionDeviceManager.
 */
class CompanionDeviceManagerSnippet : Snippet {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()!!
    private val context: Context = instrumentation.targetContext
    private val companionDeviceManager = context.getSystemService(Context.COMPANION_DEVICE_SERVICE)
            as CompanionDeviceManager

    private val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val btConnector = BluetoothConnector(btManager.adapter, companionDeviceManager)

    private val uiDevice by lazy { UiDevice.getInstance(instrumentation) }
    private val confirmationUi by lazy { CompanionDeviceManagerUi(uiDevice) }

    private val handlerThread = HandlerThread("Snippet-Aware")
    private val handler: Handler
    private val executor: Executor

    init {
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        executor = HandlerExecutor(handler)
    }

    /**
     * Associate with a nearby device with given name and return newly-created association ID.
     */
    @Rpc(description = "Start device association flow.")
    @Throws(Exception::class)
    fun associate(deviceAddress: String): Int {
        val filter = BluetoothDeviceFilter.Builder()
                .setAddress(deviceAddress)
                .build()
        val request = AssociationRequest.Builder()
                .setSingleDevice(true)
                .addDeviceFilter(filter)
                .build()
        val callback = CallbackUtils.AssociationCallback()
        companionDeviceManager.associate(request, callback, handler)
        val pendingConfirmation = callback.waitForPendingIntent()
                ?: throw RuntimeException("Association is pending but intent sender is null.")
        CompanionActivity.launchAndWait(context)
        CompanionActivity.startIntentSender(pendingConfirmation)
        confirmationUi.waitUntilVisible()
        confirmationUi.waitUntilPositiveButtonIsEnabledAndClick()
        confirmationUi.waitUntilGone()

        val (_, result) = CompanionActivity.waitForActivityResult()
        CompanionActivity.safeFinish()
        CompanionActivity.waitUntilGone()

        if (result == null) {
            throw RuntimeException("Association result can't be null.")
        }

        val association = checkNotNull(result.getParcelableExtra(
                CompanionDeviceManager.EXTRA_ASSOCIATION,
                AssociationInfo::class.java
        ))

        return association.id
    }

    /**
     * Request user consent to system data transfer and accept.
     */
    @Rpc(description = "Start permissions sync.")
    fun requestPermissionTransferUserConsent(associationId: Int) {
        val pendingIntent = checkNotNull(
                companionDeviceManager.buildPermissionTransferUserConsentIntent(associationId)
        )
        CompanionActivity.launchAndWait(context)
        CompanionActivity.startIntentSender(pendingIntent)
        confirmationUi.waitUntilSystemDataTransferConfirmationVisible()
        confirmationUi.clickPositiveButton()
        confirmationUi.waitUntilGone()

        CompanionActivity.waitForActivityResult()
        CompanionActivity.safeFinish()
        CompanionActivity.waitUntilGone()
    }

    /**
     * Returns the list of association IDs owned by the test app.
     */
    @Rpc(description = "Get my association IDs.")
    @Throws(Exception::class)
    fun getMyAssociations(): List<Int> {
        return companionDeviceManager.myAssociations.stream().map { it.id }.toList()
    }

    /**
     * Disassociate an association with given ID.
     */
    @Rpc(description = "Disassociate device.")
    @Throws(Exception::class)
    fun disassociate(associationId: Int) {
        companionDeviceManager.disassociate(associationId)
    }

    /**
     * Clean up all associations.
     */
    @Rpc(description = "Disassociate all associations.")
    fun disassociateAll() {
        companionDeviceManager.myAssociations.forEach {
            Log.d(TAG, "Disassociating id=${it.id}.")
            companionDeviceManager.disassociate(it.id)
        }
    }

    /**
     * Initiate system data transfer using Bluetooth socket.
     */
    @Rpc(description = "Start permissions sync.")
    fun startPermissionsSync(associationId: Int) {
        val callback = SystemDataTransferCallback()
        companionDeviceManager.startSystemDataTransfer(associationId, executor, callback)
        callback.waitForCompletion()
    }

    @Rpc(description = "Remove bluetooth bond.")
    fun removeBond(associationId: Int): Boolean {
        return companionDeviceManager.removeBond(associationId)
    }

    @Rpc(description = "Start listening for device presence event.")
    fun startObservingDevicePresence(associationId: Int) {
        val request = ObservingDevicePresenceRequest.Builder()
            .setAssociationId(associationId)
            .build()
        companionDeviceManager.startObservingDevicePresence(request)
    }

    @Rpc(description = "Stop listening for device presence event.")
    fun stopObservingDevicePresence(associationId: Int) {
        val request = ObservingDevicePresenceRequest.Builder()
            .setAssociationId(associationId)
            .build()
        companionDeviceManager.stopObservingDevicePresence(request)
    }

    @Rpc(description = "Wait for a BT classic device to connect to a test service.")
    fun isAssociationBtConnected(associationId: Int): Boolean {
        return PrimaryCompanionService.connectedBtBondDevices.stream().anyMatch {
            it.id == associationId
        }
    }

    @Rpc(description = "Attach client socket.")
    fun attachClientSocket(associationId: Int) {
        btConnector.attachClientSocket(associationId)
    }

    @Rpc(description = "Attach server socket.")
    fun attachServerSocket(associationId: Int) {
        btConnector.attachServerSocket(associationId)
    }

    @Rpc(description = "Remove all sockets.")
    fun detachAllSockets() {
        btConnector.closeAllSockets()
    }

    @Rpc(description = "Check if device is a watch.")
    fun isWatch(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
    }

    companion object {
        private const val TAG = "CDM_CompanionDeviceManagerSnippet"
    }
}
