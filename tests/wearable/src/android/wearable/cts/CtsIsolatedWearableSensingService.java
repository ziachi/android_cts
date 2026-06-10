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
package android.wearable.cts;

import android.app.ambientcontext.AmbientContextEventRequest;
import android.app.wearable.WearableSensingManager;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.SharedMemory;
import android.service.ambientcontext.AmbientContextDetectionResult;
import android.service.ambientcontext.AmbientContextDetectionServiceStatus;
import android.service.wearable.WearableSensingService;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * An implementation of {@link WearableSensingService} for CTS testing in an isolated process.
 *
 * <p>This service allows us to test APIs that will kill the {@link WearableSensingService} process,
 * which is not possible for {@link CtsWearableSensingService} because it will kill the test runner
 * too. The downside of this service is that the test runner cannot use static methods for setup and
 * verification. Instead, We use {@link #onDataProvided(PersistableBundle, SharedMemory, Consumer)}
 * with special keys in the PersistableBundle to perform setup and verification.
 */
public class CtsIsolatedWearableSensingService extends WearableSensingService {
    private static final String TAG = "CtsIsolatedWSS";

    /** PersistableBundle key that represents an action, such as setup and verify. */
    public static final String BUNDLE_ACTION_KEY = "ACTION";

    /** PersistableBundle value that represents a request to reset the service. */
    public static final String ACTION_RESET = "RESET";

    /** PersistableBundle key that represents the ID of the connection to action on. */
    public static final String BUNDLE_CONNECTION_ID_KEY = "CONNECTION_ID";

    /** A connection ID that is not valid. */
    public static final int INVALID_CONNECTION_ID = -1;

    /**
     * PersistableBundle value that represents a request to verify the data received from the
     * wearable.
     */
    public static final String ACTION_VERIFY_DATA_RECEIVED_FROM_WEARABLE =
            "VERIFY_DATA_RECEIVED_FROM_WEARABLE";

    /**
     * PersistableBundle key that represents the expected string to be received from the requested
     * ACTION.
     */
    public static final String EXPECTED_STRING_KEY = "EXPECTED_STRING_KEY";

    /** PersistableBundle key that represents the metadata to verify. */
    public static final String METADATA_KEY = "METADATA_KEY";

    /**
     * PersistableBundle value that represents a request to send data to the secure wearable
     * connection.
     */
    public static final String ACTION_SEND_DATA_TO_WEARABLE = "SEND_DATA_TO_WEARABLE";

    /** PersistableBundle key that represents the string to send to the wearable. */
    public static final String STRING_TO_SEND_KEY = "STRING_TO_SEND_KEY";

    /**
     * PersistableBundle value that represents a request to close the secure wearable connection.
     */
    public static final String ACTION_CLOSE_WEARABLE_CONNECTION = "CLOSE_WEARABLE_CONNECTION";

    /** PersistableBundle value that represents a request to set a boolean state to true. */
    public static final String ACTION_SET_BOOLEAN_STATE = "SET_BOOLEAN_STATE";

    /**
     * PersistableBundle value that represents a request to verify the boolean state has been set to
     * true. This is used to check whether the process has been restarted since the state is set.
     */
    public static final String ACTION_VERIFY_BOOLEAN_STATE = "VERIFY_BOOLEAN_STATE";

    /** PersistableBundle value that represents a request to read a file and verify the output. */
    public static final String ACTION_READ_FILE_AND_VERIFY = "READ_FILE_AND_VERIFY";

    /**
     * PersistableBundle key that represents the file path to read. The value should be relative to
     * the package's root file directory.
     */
    public static final String FILE_PATH_KEY = "FILE_PATH_KEY";

    /**
     * PersistableBundle key that represents the expected content of the file read. For simplicity,
     * the content should contain only one line of text.
     */
    public static final String EXPECTED_FILE_CONTENT_KEY = "EXPECTED_FILE_CONTENT_KEY";

    /**
     * PersistableBundle key that represents the expected exception encountered for an action. The
     * value is the exception's simple class name.
     */
    public static final String EXPECTED_EXCEPTION_KEY = "EXPECTED_EXCEPTION_KEY";

    /**
     * PersistableBundle value that represents a request to write to a file and verify the exception
     * thrown.
     */
    public static final String ACTION_WRITE_FILE_AND_VERIFY_EXCEPTION =
            "WRITE_FILE_AND_VERIFY_EXCEPTION";

    /**
     * PersistableBundle value that represents a request to write to a connection and verify an
     * exception is thrown.
     */
    public static final String ACTION_WRITE_TO_CONNECTION_AND_VERIFY_EXCEPTION =
            "WRITE_TO_CONNECTION_AND_VERIFY_EXCEPTION";

    /** PersistableBundle value that represents a request to remove all stored connections. */
    public static final String ACTION_REMOVE_ALL_STORED_CONNECTIONS =
            "REMOVE_ALL_STORED_CONNECTIONS";

    /**
     * PersistableBundle value that represents a request to verify that no connection is provided.
     */
    public static final String ACTION_VERIFY_NO_CONNECTION_PROVIDED =
            "VERIFY_NO_CONNECTION_PROVIDED";

    /**
     * PersistableBundle value that represents a request to verify that
     * onReadOnlyParcelFileDescriptorProvided is not invoked.
     */
    public static final String ACTION_VERIFY_NO_READ_ONLY_PFD_PROVIDED =
            "VERIFY_NO_READ_ONLY_PFD_PROVIDED";

    /**
     * PersistableBundle value that represents a request to verify data received from the
     * ParcelFileDescriptor provided via onReadOnlyParcelFileDescriptorProvided.
     */
    public static final String ACTION_VERIFY_DATA_RECEIVED_FROM_PFD =
            "VERIFY_DATA_RECEIVED_FROM_PFD";

    /**
     * PersistableBundle value that represents a request to verify a metadata value received from
     * onReadOnlyParcelFileDescriptorProvided.
     */
    public static final String ACTION_VERIFY_METADATA = "VERIFY_METADATA";

    private volatile ParcelFileDescriptor mSecureWearableConnection;

    @GuardedBy("mSecureWearableConnectionMap")
    private final Map<Integer, ParcelFileDescriptor> mSecureWearableConnectionMap = new HashMap<>();

    private Set<ParcelFileDescriptor> mParcelFileDescriptorsToCleanUp = new HashSet<>();

    private volatile ParcelFileDescriptor mReadOnlyParcelFileDescriptorReceived = null;
    private volatile PersistableBundle mMetadataReceived = null;

    private volatile boolean mBooleanState = false;

    @Override
    public void onSecureConnectionProvided(
            ParcelFileDescriptor secureWearableConnection, Consumer<Integer> statusConsumer) {
        Log.w(TAG, "onSecureConnectionProvided");
        mSecureWearableConnection = secureWearableConnection;
        statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
    }

    @Override
    public void onSecureConnectionProvided(
            ParcelFileDescriptor secureWearableConnection,
            PersistableBundle metadata,
            Consumer<Integer> statusConsumer) {
        Log.w(TAG, "onSecureConnectionProvided with metadata");
        mSecureWearableConnection = secureWearableConnection;
        int connectionId = metadata.getInt(BUNDLE_CONNECTION_ID_KEY, INVALID_CONNECTION_ID);
        if (connectionId != INVALID_CONNECTION_ID) {
            synchronized (mSecureWearableConnectionMap) {
                Log.i(
                        TAG,
                        "connectionId: "
                                + connectionId
                                + " connection: "
                                + secureWearableConnection);
                mSecureWearableConnectionMap.put(connectionId, secureWearableConnection);
            }
        } else {
            Log.w(
                    TAG,
                    "Connection Id not found, not adding the connection to"
                        + " mSecureWearableConnectionMap. This may be expected if the test does not"
                        + " involve multiple concurrent connections.");
        }
        statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
    }

    @Override
    public void onDataStreamProvided(
            ParcelFileDescriptor parcelFileDescriptor, Consumer<Integer> statusConsumer) {
        Log.w(TAG, "onDataStreamProvided");
        statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
    }

    @Override
    public void onReadOnlyParcelFileDescriptorProvided(
            ParcelFileDescriptor parcelFileDescriptor,
            PersistableBundle metadata,
            Consumer<Integer> statusConsumer) {
        mReadOnlyParcelFileDescriptorReceived = parcelFileDescriptor;
        mMetadataReceived = metadata;
        statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
    }

    @Override
    public void onDataProvided(
            PersistableBundle data, SharedMemory sharedMemory, Consumer<Integer> statusConsumer) {
        String action = data.getString(BUNDLE_ACTION_KEY);
        Log.i(TAG, "#onDataProvided, action: " + action);
        try {
            String relativeFilePath;
            String expectedString;
            int connectionId;
            switch (action) {
                case ACTION_RESET:
                    reset();
                    statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
                    return;
                case ACTION_VERIFY_DATA_RECEIVED_FROM_WEARABLE:
                    expectedString = data.getString(EXPECTED_STRING_KEY);
                    connectionId =
                            data.getInt(
                                    BUNDLE_CONNECTION_ID_KEY,
                                    /* defaultValue= */ INVALID_CONNECTION_ID);
                    if (connectionId == INVALID_CONNECTION_ID) {
                        verifyDataReceivedFromWearableViaNonConcurrentConnection(
                                expectedString, statusConsumer);
                    } else {
                        verifyDataReceivedFromWearableViaConcurrentConnection(
                                expectedString, statusConsumer, connectionId);
                    }
                    return;
                case ACTION_SEND_DATA_TO_WEARABLE:
                    String stringToSend = data.getString(STRING_TO_SEND_KEY);
                    connectionId =
                            data.getInt(
                                    BUNDLE_CONNECTION_ID_KEY,
                                    /* defaultValue= */ INVALID_CONNECTION_ID);
                    if (connectionId == INVALID_CONNECTION_ID) {
                        sendDataToWearableViaNonConcurrentConnection(stringToSend, statusConsumer);
                    } else {
                        sendDataToWearableViaConcurrentConnection(
                                stringToSend, statusConsumer, connectionId);
                    }
                    return;
                case ACTION_CLOSE_WEARABLE_CONNECTION:
                    closeWearableConnection(statusConsumer);
                    return;
                case ACTION_SET_BOOLEAN_STATE:
                    mBooleanState = true;
                    statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
                    return;
                case ACTION_VERIFY_BOOLEAN_STATE:
                    statusConsumer.accept(
                            mBooleanState
                                    ? WearableSensingManager.STATUS_SUCCESS
                                    : WearableSensingManager.STATUS_UNKNOWN);
                    return;
                case ACTION_READ_FILE_AND_VERIFY:
                    relativeFilePath = data.getString(FILE_PATH_KEY);
                    String expectedContent = data.getString(EXPECTED_FILE_CONTENT_KEY);
                    String expectedException = data.getString(EXPECTED_EXCEPTION_KEY);
                    readFileAndVerify(
                            relativeFilePath, expectedContent, expectedException, statusConsumer);
                    return;
                case ACTION_WRITE_FILE_AND_VERIFY_EXCEPTION:
                    relativeFilePath = data.getString(FILE_PATH_KEY);
                    writeToFileAndVerifyException(relativeFilePath, statusConsumer);
                    return;
                case ACTION_WRITE_TO_CONNECTION_AND_VERIFY_EXCEPTION:
                    connectionId =
                            data.getInt(
                                    BUNDLE_CONNECTION_ID_KEY,
                                    /* defaultValue= */ INVALID_CONNECTION_ID);
                    if (connectionId == INVALID_CONNECTION_ID) {
                        Log.e(
                                TAG,
                                "ACTION_WRITE_TO_CONNECTION_AND_VERIFY_EXCEPTION but no"
                                        + " connectionId provided.");
                        statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
                    } else {
                        writeToConnectionAndVerifyException(connectionId, statusConsumer);
                    }
                    return;
                case ACTION_REMOVE_ALL_STORED_CONNECTIONS:
                    removeAllStoredConnections(statusConsumer);
                    return;
                case ACTION_VERIFY_NO_CONNECTION_PROVIDED:
                    verifyNoConnectionProvided(statusConsumer);
                    return;
                case ACTION_VERIFY_NO_READ_ONLY_PFD_PROVIDED:
                    verifyNoReadOnlyPfdProvided(statusConsumer);
                    return;
                case ACTION_VERIFY_DATA_RECEIVED_FROM_PFD:
                    expectedString = data.getString(EXPECTED_STRING_KEY);
                    verifyDataReceivedFromPfd(expectedString, statusConsumer);
                    return;
                case ACTION_VERIFY_METADATA:
                    expectedString = data.getString(EXPECTED_STRING_KEY);
                    verifyMetadata(expectedString, statusConsumer);
                    return;
                default:
                    Log.w(TAG, "Unknown action: " + action);
                    statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
                    return;
            }
        } catch (Exception ex) {
            // Exception in this process will not show up in the test runner, so just Log it and
            // return an unknown status code.
            Log.e(TAG, "Unexpected exception in onDataProvided.", ex);
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
        }
    }

    private void reset() {
        Log.i(TAG, "#reset");
        Optional.ofNullable(mSecureWearableConnection).ifPresent(this::tryCloseConnection);
        mSecureWearableConnection = null;
        synchronized (mSecureWearableConnectionMap) {
            for (ParcelFileDescriptor connection : mSecureWearableConnectionMap.values()) {
                tryCloseConnection(connection);
            }
            mSecureWearableConnectionMap.clear();
        }
        for (ParcelFileDescriptor connection : mParcelFileDescriptorsToCleanUp) {
            tryCloseConnection(connection);
        }
        mBooleanState = false;
        mReadOnlyParcelFileDescriptorReceived = null;
        mMetadataReceived = null;
    }

    private void tryCloseConnection(ParcelFileDescriptor connection) {
        try {
            connection.close();
        } catch (IOException ex) {
            Log.w(TAG, "Unable to close connection.", ex);
        }
    }

    private void verifyDataReceivedFromWearableViaNonConcurrentConnection(
            String expectedString, Consumer<Integer> statusConsumer) throws IOException {
        if (mSecureWearableConnection == null) {
            Log.e(
                    TAG,
                    "#verifyDataReceivedFromWearable called but mSecureWearableConnection is null");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
            return;
        }
        verifyDataReceivedFromParcelFileDescriptor(
                expectedString, statusConsumer, mSecureWearableConnection);
    }

    private void verifyDataReceivedFromWearableViaConcurrentConnection(
            String expectedString, Consumer<Integer> statusConsumer, int connectionId)
            throws IOException {
        ParcelFileDescriptor connection;
        synchronized (mSecureWearableConnectionMap) {
            if (!mSecureWearableConnectionMap.containsKey(connectionId)) {
                Log.e(
                        TAG,
                        "#verifyDataReceivedFromWearableViaConcurrentConnection connection ID not"
                                + " found: "
                                + connectionId);
                statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
                return;
            }
            connection = mSecureWearableConnectionMap.get(connectionId);
        }
        Log.i(
                TAG,
                "Verifying connectionId "
                        + connectionId
                        + ", connection "
                        + connection
                        + ", expected "
                        + expectedString);
        verifyDataReceivedFromParcelFileDescriptor(expectedString, statusConsumer, connection);
    }

    private void verifyDataReceivedFromParcelFileDescriptor(
            String expectedString,
            Consumer<Integer> statusConsumer,
            ParcelFileDescriptor parcelFileDescriptor)
            throws IOException {
        if (expectedString == null) {
            Log.e(
                    TAG,
                    "#verifyDataReceivedFromParcelFileDescriptor called but no expected string is"
                            + " provided");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
            return;
        }
        byte[] expectedBytes = expectedString.getBytes(StandardCharsets.UTF_8);
        Log.i(TAG, "About to read from " + parcelFileDescriptor + " expected: " + expectedString);
        byte[] dataFromWearable = readData(parcelFileDescriptor, expectedBytes.length);
        mParcelFileDescriptorsToCleanUp.add(parcelFileDescriptor);
        Log.i(
                TAG,
                "Finished reading from "
                        + parcelFileDescriptor
                        + " data read: "
                        + new String(dataFromWearable, StandardCharsets.UTF_8));
        if (Arrays.equals(expectedBytes, dataFromWearable)) {
            statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
        } else {
            Log.e(
                    TAG,
                    String.format(
                            "Data bytes received from parcelFileDescriptor are different from"
                                    + " expected. Received length: %s, expected length: %s",
                            dataFromWearable.length, expectedBytes.length));
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
        }
    }

    private byte[] readData(ParcelFileDescriptor pfd, int length) throws IOException {
        InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
        byte[] dataRead = new byte[length];
        is.read(dataRead, 0, length);
        // Do not close the InputStream because it will close the underlying PFD, which may be
        // needed in a later part of the test.
        return dataRead;
    }

    private void sendDataToWearableViaConcurrentConnection(
            String stringToSend, Consumer<Integer> statusConsumer, int connectionId)
            throws Exception {
        ParcelFileDescriptor connection;
        synchronized (mSecureWearableConnectionMap) {
            if (!mSecureWearableConnectionMap.containsKey(connectionId)) {
                Log.e(
                        TAG,
                        "#sendDataToWearableViaConcurrentConnection connection ID not found: "
                                + connectionId);
                statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
                return;
            }
            connection = mSecureWearableConnectionMap.get(connectionId);
        }
        sendDataToWearable(stringToSend, statusConsumer, connection);
    }

    private void sendDataToWearableViaNonConcurrentConnection(
            String stringToSend, Consumer<Integer> statusConsumer) throws Exception {
        if (mSecureWearableConnection == null) {
            Log.e(TAG, "#sendDataToWearable called but mSecureWearableConnection is null");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
            return;
        }
        sendDataToWearable(stringToSend, statusConsumer, mSecureWearableConnection);
    }

    private void sendDataToWearable(
            String stringToSend,
            Consumer<Integer> statusConsumer,
            ParcelFileDescriptor secureWearableConnection)
            throws Exception {
        if (stringToSend == null) {
            Log.e(TAG, "#sendDataToWearable called but no stringToSend is provided");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
            return;
        }
        byte[] bytesToSend = stringToSend.getBytes(StandardCharsets.UTF_8);
        writeData(secureWearableConnection, bytesToSend);
        statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
    }

    private void writeData(ParcelFileDescriptor pfd, byte[] data) throws IOException {
        OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(pfd);
        os.write(data);
    }

    private void closeWearableConnection(Consumer<Integer> statusConsumer) throws Exception {
        if (mSecureWearableConnection == null) {
            Log.e(TAG, "#sendDataToWearable called but mSecureWearableConnection is null");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
            return;
        }
        mSecureWearableConnection.close();
        statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
    }

    private void readFileAndVerify(
            String relativeFilePath,
            String expectedContent,
            String expectedException,
            Consumer<Integer> statusConsumer)
            throws Exception {
        try (FileInputStream fileInputStream = openFileInput(relativeFilePath)) {
            BufferedReader bufferedReader =
                    new BufferedReader(new InputStreamReader(fileInputStream));
            String content = bufferedReader.readLine();
            if (expectedContent != null) {
                if (expectedContent.equals(content)) {
                    statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
                } else {
                    Log.e(
                            TAG,
                            "Unexpected file content. Expected "
                                    + expectedContent
                                    + " but found "
                                    + content);
                    statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
                }
                return;
            }
        } catch (Exception ex) {
            if (expectedException != null
                    && expectedException.equals(ex.getClass().getSimpleName())) {
                statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
                return;
            }
            throw ex; // onDataProvided will catch the exception and return the error status code
        }
        Log.e(
                TAG,
                "#readFileAndVerify with no specified expectedContent, but no exception is"
                        + " thrown.");
        statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
    }

    private void writeToFileAndVerifyException(
            String relativeFilePath, Consumer<Integer> statusConsumer) throws Exception {
        try (FileInputStream fileInputStream = openFileInput(relativeFilePath)) {
            try {
                FileOutputStream fileOutputStream = new FileOutputStream(fileInputStream.getFD());
                fileOutputStream.write(3); // write anything
            } catch (SecurityException | IOException ex) {
                Log.i(TAG, "Caught expected exception from writing to read-only stream", ex);
                statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
                return;
            }
        }
        Log.e(TAG, "#writeToFileAndVerifyException does not throw any exception.");
        statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
    }

    private void writeToConnectionAndVerifyException(
            int connectionId, Consumer<Integer> statusConsumer) throws Exception {
        ParcelFileDescriptor connection;
        synchronized (mSecureWearableConnectionMap) {
            if (!mSecureWearableConnectionMap.containsKey(connectionId)) {
                Log.e(TAG, "Connection ID not found: " + connectionId);
                statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
                return;
            }
            connection = mSecureWearableConnectionMap.get(connectionId);
        }
        try {
            new ParcelFileDescriptor.AutoCloseOutputStream(connection).write(1);
        } catch (IOException ex) {
            statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
            return;
        }
        Log.e(TAG, "No exception encountered when writing to the connection");
        statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
    }

    private void removeAllStoredConnections(Consumer<Integer> statusConsumer) {
        // We want to remove stored connections without closing them because closing them will
        // trigger clean up code in the system and affect test results, so we add them to a clean up
        // list to be closed in the next reset.
        Optional.ofNullable(mSecureWearableConnection)
                .ifPresent(mParcelFileDescriptorsToCleanUp::add);
        mSecureWearableConnection = null;
        synchronized (mSecureWearableConnectionMap) {
            mParcelFileDescriptorsToCleanUp.addAll(mSecureWearableConnectionMap.values());
            mSecureWearableConnectionMap.clear();
        }
        statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
    }

    private void verifyNoConnectionProvided(Consumer<Integer> statusConsumer) {
        boolean isSecureWearableConnectionMapEmpty;
        synchronized (mSecureWearableConnectionMap) {
            isSecureWearableConnectionMapEmpty = mSecureWearableConnectionMap.isEmpty();
        }
        if (mSecureWearableConnection == null && isSecureWearableConnectionMapEmpty) {
            statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
        } else {
            Log.e(TAG, "At least one connection has been provided.");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
        }
    }

    private void verifyNoReadOnlyPfdProvided(Consumer<Integer> statusConsumer) {
        if (mReadOnlyParcelFileDescriptorReceived == null) {
            statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
        } else {
            Log.e(
                    TAG,
                    "verifyNoReadOnlyPfdProvided called, but a read-only ParcelFileDescriptor was"
                            + " provided.");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
        }
    }

    private void verifyDataReceivedFromPfd(String expectedData, Consumer<Integer> statusConsumer)
            throws Exception {
        if (mReadOnlyParcelFileDescriptorReceived == null) {
            Log.e(
                    TAG,
                    "verifyDataReceivedFromPfd called, but mReadOnlyParcelFileDescriptorReceived is"
                            + " null");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
            return;
        }
        verifyDataReceivedFromParcelFileDescriptor(
                expectedData, statusConsumer, mReadOnlyParcelFileDescriptorReceived);
    }

    private void verifyMetadata(String expectedString, Consumer<Integer> statusConsumer) {
        if (expectedString == null) {
            Log.e(TAG, "verifyMetadata called, but no expected string is provided.");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
            return;
        }
        if (mMetadataReceived == null) {
            Log.e(TAG, "No metadata was received.");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
            return;
        }
        if (Objects.equals(mMetadataReceived.getString(METADATA_KEY), expectedString)) {
            statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
        } else {
            Log.e(
                    TAG,
                    String.format(
                            "Expected metadata %s, but received %s",
                            expectedString, mMetadataReceived.getString(METADATA_KEY)));
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
        }
    }

    // The methods below are not used. They are tested in CtsWearableSensingService and only
    // implemented here because they are abstract.

    @Override
    public void onStartDetection(
            AmbientContextEventRequest request,
            String packageName,
            Consumer<AmbientContextDetectionServiceStatus> statusConsumer,
            Consumer<AmbientContextDetectionResult> detectionResultConsumer) {
        Log.w(TAG, "onStartDetection");
    }

    @Override
    public void onStopDetection(String packageName) {
        Log.w(TAG, "onStopDetection");
    }

    @Override
    public void onQueryServiceStatus(
            Set<Integer> eventTypes,
            String packageName,
            Consumer<AmbientContextDetectionServiceStatus> consumer) {
        Log.w(TAG, "onQueryServiceStatus");
    }
}
