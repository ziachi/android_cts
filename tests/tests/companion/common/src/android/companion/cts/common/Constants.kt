package android.companion.cts.common

import android.Manifest
import android.companion.AssociationRequest.DEVICE_PROFILE_APP_STREAMING
import android.companion.AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION
import android.companion.AssociationRequest.DEVICE_PROFILE_COMPUTER
import android.companion.AssociationRequest.DEVICE_PROFILE_GLASSES
import android.companion.AssociationRequest.DEVICE_PROFILE_NEARBY_DEVICE_STREAMING
import android.companion.AssociationRequest.DEVICE_PROFILE_VIRTUAL_DEVICE
import android.companion.AssociationRequest.DEVICE_PROFILE_WATCH
import android.net.MacAddress
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import java.util.concurrent.Executor

/** Set of all supported CDM Device Profiles. */
val DEVICE_PROFILES = buildSet {
    add(DEVICE_PROFILE_WATCH)
    add(DEVICE_PROFILE_GLASSES)
    add(DEVICE_PROFILE_NEARBY_DEVICE_STREAMING)
    add(DEVICE_PROFILE_COMPUTER)
    add(DEVICE_PROFILE_APP_STREAMING)
    add(DEVICE_PROFILE_AUTOMOTIVE_PROJECTION)
    if (android.companion.virtualdevice.flags.Flags.enableLimitedVdmRole()) {
        add(DEVICE_PROFILE_VIRTUAL_DEVICE)
    }
}

val DEVICE_PROFILE_TO_NAME = buildMap {
    put(DEVICE_PROFILE_WATCH, "WATCH")
    put(DEVICE_PROFILE_GLASSES, "GLASSES")
    put(DEVICE_PROFILE_NEARBY_DEVICE_STREAMING, "NEARBY_DEVICE_STREAMING")
    put(DEVICE_PROFILE_COMPUTER, "COMPUTER")
    put(DEVICE_PROFILE_APP_STREAMING, "APP_STREAMING")
    put(DEVICE_PROFILE_AUTOMOTIVE_PROJECTION, "AUTOMOTIVE_PROJECTION")
    if (android.companion.virtualdevice.flags.Flags.enableLimitedVdmRole()) {
        put(DEVICE_PROFILE_VIRTUAL_DEVICE, "VIRTUAL_DEVICE")
    }
}

val DEVICE_PROFILE_TO_PERMISSION = buildMap {
    put(DEVICE_PROFILE_WATCH, Manifest.permission.REQUEST_COMPANION_PROFILE_WATCH)
    put(DEVICE_PROFILE_APP_STREAMING, Manifest.permission.REQUEST_COMPANION_PROFILE_APP_STREAMING)
    put(
        DEVICE_PROFILE_AUTOMOTIVE_PROJECTION,
        Manifest.permission.REQUEST_COMPANION_PROFILE_AUTOMOTIVE_PROJECTION
    )
    put(DEVICE_PROFILE_GLASSES, Manifest.permission.REQUEST_COMPANION_PROFILE_GLASSES)
    put(
        DEVICE_PROFILE_NEARBY_DEVICE_STREAMING,
        Manifest.permission.REQUEST_COMPANION_PROFILE_NEARBY_DEVICE_STREAMING
    )
    put(DEVICE_PROFILE_COMPUTER, Manifest.permission.REQUEST_COMPANION_PROFILE_COMPUTER)
    if (android.companion.virtualdevice.flags.Flags.enableLimitedVdmRole()) {
        put(
            DEVICE_PROFILE_VIRTUAL_DEVICE,
            Manifest.permission.REQUEST_COMPANION_PROFILE_VIRTUAL_DEVICE
        )
    }
}

val MAC_ADDRESS_A = MacAddress.fromString("00:00:00:00:00:AA")
val MAC_ADDRESS_B = MacAddress.fromString("00:00:00:00:00:BB")
val MAC_ADDRESS_C = MacAddress.fromString("00:00:00:00:00:CC")

val UUID_A: ParcelUuid = ParcelUuid.fromString("bc4990b9-698c-473d-8498-2a5c4119f73d")
val UUID_B: ParcelUuid = ParcelUuid.fromString("ba6d2f1e-9adc-11ee-b9d1-0242ac120002")

const val CUSTOM_ID_A = "00:00:00:00:00:AA"
const val CUSTOM_ID_B = "00:00:00:00:00:BB"

var CUSTOM_ID_INVALID = "A".repeat(1025)

const val DEVICE_DISPLAY_NAME_A = "Device A"
const val DEVICE_DISPLAY_NAME_B = "Device B"

const val ASSOCIATION_ID = 1

val SIMPLE_EXECUTOR: Executor by lazy { Executor { it.run() } }

val MAIN_THREAD_EXECUTOR: Executor by lazy {
    Executor {
        with(Handler.getMain()) { post(it) }
    }
}

val BACKGROUND_THREAD_EXECUTOR: Executor by lazy {
    with(HandlerThread("CdmTestBackgroundThread")) {
        start()
        Executor { threadHandler.post(it) }
    }
}

val PRIMARY_PROCESS_NAME = ":primary"
val SECONDARY_PROCESS_NAME = ":secondary"
