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

import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_CLOSE_WEARABLE_CONNECTION;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_READ_FILE_AND_VERIFY;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_REMOVE_ALL_STORED_CONNECTIONS;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_RESET;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_SEND_DATA_TO_WEARABLE;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_SET_BOOLEAN_STATE;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_VERIFY_BOOLEAN_STATE;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_VERIFY_DATA_RECEIVED_FROM_PFD;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_VERIFY_DATA_RECEIVED_FROM_WEARABLE;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_VERIFY_METADATA;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_VERIFY_NO_CONNECTION_PROVIDED;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_VERIFY_NO_READ_ONLY_PFD_PROVIDED;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_WRITE_FILE_AND_VERIFY_EXCEPTION;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_WRITE_TO_CONNECTION_AND_VERIFY_EXCEPTION;
import static android.wearable.cts.CtsIsolatedWearableSensingService.BUNDLE_ACTION_KEY;
import static android.wearable.cts.CtsIsolatedWearableSensingService.BUNDLE_CONNECTION_ID_KEY;
import static android.wearable.cts.CtsIsolatedWearableSensingService.EXPECTED_EXCEPTION_KEY;
import static android.wearable.cts.CtsIsolatedWearableSensingService.EXPECTED_FILE_CONTENT_KEY;
import static android.wearable.cts.CtsIsolatedWearableSensingService.EXPECTED_STRING_KEY;
import static android.wearable.cts.CtsIsolatedWearableSensingService.FILE_PATH_KEY;
import static android.wearable.cts.CtsIsolatedWearableSensingService.INVALID_CONNECTION_ID;
import static android.wearable.cts.CtsIsolatedWearableSensingService.METADATA_KEY;
import static android.wearable.cts.CtsIsolatedWearableSensingService.STRING_TO_SEND_KEY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.Manifest;
import android.app.wearable.Flags;
import android.app.wearable.WearableConnection;
import android.app.wearable.WearableSensingManager;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.wearable.WearableSensingService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.DeviceConfigStateChangerRule;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Test the {@link WearableSensingManager} API using {@link CtsIsolatedWearableSensingService} as
 * the implementation for {@link WearableSensingService}.
 *
 * <p>Run with "atest CtsWearableSensingServiceTestCases".
 */
@RunWith(JUnitParamsRunner.class)
@AppModeFull(reason = "PM will not recognize CtsIsolatedWearableSensingService in instantMode.")
public class WearableSensingManagerIsolatedServiceTest {
    private static final String TAG = "WSMIsolatedTest";
    private static final String CTS_PACKAGE_NAME =
            CtsIsolatedWearableSensingService.class.getPackage().getName();
    private static final String CTS_ISOLATED_SERVICE_NAME =
            CTS_PACKAGE_NAME + "/." + CtsIsolatedWearableSensingService.class.getSimpleName();
    private static final int USER_ID = UserHandle.myUserId();
    private static final int TEMPORARY_SERVICE_DURATION = 5000;
    private static final String NAMESPACE_WEARABLE_SENSING = "wearable_sensing";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    // The CDM message types come from CompanionDeviceManager.MESSAGE_ONEWAY_*
    private static final int CDM_MESSAGE_ONEWAY_FROM_WEARABLE = 0x43708287;
    private static final int CDM_MESSAGE_ONEWAY_TO_WEARABLE = 0x43847987;
    private static final String CDM_ASSOCIATION_DISPLAY_NAME = "CDM_ASSOCIATION_DISPLAY_NAME";
    private static final String DATA_TO_WRITE_0 = "DATA_TO_WRITE";
    private static final String DATA_TO_WRITE_1 = "SOME_OTHER_DATA_TO_WRITE";
    private static final String DATA_TO_WRITE_2 = "YET_ANOTHER_DATA_TO_WRITE_2";
    private static final String DATA_TO_WRITE_3 = "THIRD_DATA_TO_WRITE_3";
    private static final String DATA_TO_WRITE_4 = "FOURTH_DATA_TO_WRITE_4";
    private static final String FILE_DIRECTORY_NAME = "IsolatedServiceTest";
    private static final String FILE_CONTENT_1 = "My first file content";
    private static final String FILE_CONTENT_2 = "My second file content";

    private static final Executor EXECUTOR = InstrumentationRegistry.getContext().getMainExecutor();

    private Context mContext;
    private WearableSensingManager mWearableSensingManager;
    private CompanionDeviceManager mCompanionDeviceManager;
    private ParcelFileDescriptor[] mSocketPair0;
    private ParcelFileDescriptor[] mSocketPair1;
    private ParcelFileDescriptor[] mSocketPair2;
    @Mock private Consumer<Integer> mMockStatusConsumer0;
    @Mock private Consumer<Integer> mMockStatusConsumer1;
    @Mock private Consumer<Integer> mMockStatusConsumer2;
    private WearableConnection mWearableConnection0;
    private WearableConnection mWearableConnection1;
    private WearableConnection mWearableConnection2;
    private Integer mCdmAssociationId0;
    private Integer mCdmAssociationId1;
    private Integer mCdmAssociationId2;
    private File mTestDirectory;
    private final List<ParcelFileDescriptor> mParcelFileDescriptorsToCleanUp = new ArrayList<>();

    @Rule
    public final DeviceConfigStateChangerRule mWearableSensingConfigRule =
            new DeviceConfigStateChangerRule(
                    getInstrumentation().getTargetContext(),
                    NAMESPACE_WEARABLE_SENSING,
                    KEY_SERVICE_ENABLED,
                    "true");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = getInstrumentation().getContext();
        assumeFalse(isWatch(mContext)); // WearableSensingManagerService is not supported on WearOS
        // For an unknown reason, the CDM onTransportsChanged listener is not called on TV builds
        assumeFalse(isTelevision(mContext));
        // Sleep for 2 seconds to avoid flakiness until b/326256152 is fixed. The bug can cause
        // CDM to reuse the same associationId previously assigned to WearableSensingSecureChannel
        // after the previous association is disassociation. If async clean up of the CDM
        // secure channel for that previous association has not completed, it can affect the new
        // association with the reused associationId and causes tests to flake.
        SystemClock.sleep(2000);
        mWearableSensingManager =
                (WearableSensingManager)
                        mContext.getSystemService(Context.WEARABLE_SENSING_SERVICE);
        mCompanionDeviceManager = mContext.getSystemService(CompanionDeviceManager.class);
        mSocketPair0 = ParcelFileDescriptor.createSocketPair();
        mSocketPair1 = ParcelFileDescriptor.createSocketPair();
        mSocketPair2 = ParcelFileDescriptor.createSocketPair();
        mParcelFileDescriptorsToCleanUp.add(mSocketPair0[0]);
        mParcelFileDescriptorsToCleanUp.add(mSocketPair0[1]);
        mParcelFileDescriptorsToCleanUp.add(mSocketPair1[0]);
        mParcelFileDescriptorsToCleanUp.add(mSocketPair1[1]);
        mParcelFileDescriptorsToCleanUp.add(mSocketPair2[0]);
        mParcelFileDescriptorsToCleanUp.add(mSocketPair2[1]);
        mWearableConnection0 =
                createWearableConnection(
                        mSocketPair0[0],
                        createMetadataBundle(/* connectionId= */ 0),
                        mMockStatusConsumer0);
        mWearableConnection1 =
                createWearableConnection(
                        mSocketPair1[0],
                        createMetadataBundle(/* connectionId= */ 1),
                        mMockStatusConsumer1);
        mWearableConnection2 =
                createWearableConnection(
                        mSocketPair2[0],
                        createMetadataBundle(/* connectionId= */ 2),
                        mMockStatusConsumer2);

        clearTestableWearableSensingService();
        bindToIsolatedWearableSensingService();
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);
        resetIsolatedWearableSensingServiceStates();
        if (Flags.enableConcurrentWearableConnections()) {
            mWearableSensingManager.removeAllConnections();
            Log.i(
                    TAG,
                    "available concurrent connection count: "
                            + mWearableSensingManager.getAvailableConnectionCount());
        }
        mTestDirectory = new File(mContext.getFilesDir(), FILE_DIRECTORY_NAME);
        mTestDirectory.mkdirs();
    }

    private PersistableBundle createMetadataBundle(int connectionId) {
        PersistableBundle metadata = new PersistableBundle();
        metadata.putInt(BUNDLE_CONNECTION_ID_KEY, connectionId);
        return metadata;
    }

    @After
    public void tearDown() throws Exception {
        Log.i(TAG, "#tearDown");
        if (mWearableSensingManager == null) {
            return; // Skip tear down if the test has not been set up.
        }
        if (Flags.enableConcurrentWearableConnections()) {
            mWearableSensingManager.removeAllConnections();
        }
        clearTestableWearableSensingService();
        // Do not call mCompanionDeviceManager.disassociate(mCdmAssociationId) until b/326256152 is
        // fixed. Calling it can cause the CDM associationId to be reused in
        // WearableSensingSecureChannel, which causes the async CDM SecureChannel clean up in the
        // current test to close the WearableSensingSecureChannel used in the next test.
        // This issue is way more likely to reproduce on a freshly wiped device since CDM persists
        // association IDs on disk.
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        // mTestDirectory is null when the setUp method returns early from assumeFalse calls.
        if (mTestDirectory != null) {
            for (File file : mTestDirectory.listFiles()) {
                file.delete();
            }
            mTestDirectory.delete();
        }
        for (ParcelFileDescriptor parcelFileDescriptor : mParcelFileDescriptorsToCleanUp) {
            try {
                parcelFileDescriptor.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.service.wearable.WearableSensingService#onSecureConnectionProvided"
            })
    @Parameters({"true", "false"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void provideConnection_canReceiveStatusFromWss(boolean isForConcurrentApi)
            throws Exception {
        AtomicInteger statusCodeRef = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusCodeLatch = new CountDownLatch(1);

        provideConnection(
                isForConcurrentApi,
                mSocketPair0[1],
                (statusCode) -> {
                    statusCodeRef.set(statusCode);
                    statusCodeLatch.countDown();
                });

        assertThat(statusCodeLatch.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.service.wearable.WearableSensingService#onSecureConnectionProvided"
            })
    @Parameters({"true", "false"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void provideConnection_otherEndAttachedToCdm_canReceiveDataInWss(
            boolean isForConcurrentApi) throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE,
                        Manifest.permission.REQUEST_COMPANION_SELF_MANAGED,
                        Manifest.permission.USE_COMPANION_TRANSPORTS);
        mCdmAssociationId0 = attachTransportToCdm(mSocketPair0[0]).mAssociationId;
        byte[] dataToWrite = DATA_TO_WRITE_0.getBytes(StandardCharsets.UTF_8);
        CountDownLatch statusCodeLatch = new CountDownLatch(1);

        provideConnection(
                isForConcurrentApi, mSocketPair0[1], (statusCode) -> statusCodeLatch.countDown());
        assertThat(statusCodeLatch.await(3, SECONDS)).isTrue();
        mCompanionDeviceManager.sendMessage(
                CDM_MESSAGE_ONEWAY_FROM_WEARABLE, dataToWrite, new int[] {mCdmAssociationId0});

        assertThat(statusCodeLatch.await(3, SECONDS)).isTrue();

        verifyDataReceivedFromWearable(DATA_TO_WRITE_0);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.service.wearable.WearableSensingService#onSecureConnectionProvided"
            })
    @Parameters({"true", "false"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void provideConnection_otherEndAttachedToCdm_canReceiveDataFromWss(
            boolean isForConcurrentApi) throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE,
                        Manifest.permission.REQUEST_COMPANION_SELF_MANAGED,
                        Manifest.permission.USE_COMPANION_TRANSPORTS);
        CdmSecureChannelContext cdmSecureChannelContext = attachTransportToCdm(mSocketPair0[0]);
        CountDownLatch statusCodeLatch = new CountDownLatch(1);

        provideConnection(
                isForConcurrentApi, mSocketPair0[1], (statusCode) -> statusCodeLatch.countDown());
        assertThat(statusCodeLatch.await(3, SECONDS)).isTrue();
        sendDataToWearableFromWss(DATA_TO_WRITE_0);

        byte[] dataReceived = receiveMessageFromCdm(cdmSecureChannelContext);
        assertThat(dataReceived).isEqualTo(DATA_TO_WRITE_0.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.service.wearable.WearableSensingService#onSecureConnectionProvided"
            })
    @Parameters({"true", "false"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void provideConnection_wearableStreamClosedThenSendDataFromWss_channelErrorStatus(
            boolean isForConcurrentApi) throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE,
                        Manifest.permission.REQUEST_COMPANION_SELF_MANAGED,
                        Manifest.permission.USE_COMPANION_TRANSPORTS);
        AtomicInteger statusCodeRef = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch successStatusLatch = new CountDownLatch(1);
        // Count down twice because we expect a success status followed by a channel error
        CountDownLatch channelErrorStatusLatch = new CountDownLatch(2);
        mCdmAssociationId0 = attachTransportToCdm(mSocketPair0[0]).mAssociationId;

        provideConnection(
                isForConcurrentApi,
                mSocketPair0[1],
                (statusCode) -> {
                    statusCodeRef.set(statusCode);
                    successStatusLatch.countDown();
                    channelErrorStatusLatch.countDown();
                });
        assertThat(successStatusLatch.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);

        mSocketPair0[0].close();
        sendDataToWearableFromWss(DATA_TO_WRITE_0);

        assertThat(channelErrorStatusLatch.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef.get()).isEqualTo(WearableSensingManager.STATUS_CHANNEL_ERROR);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.service.wearable.WearableSensingService#onSecureConnectionProvided"
            })
    public void provideConnection_wearableStreamClosedThenSendDataFromWss_restartWssProcess()
            throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE,
                        Manifest.permission.REQUEST_COMPANION_SELF_MANAGED,
                        Manifest.permission.USE_COMPANION_TRANSPORTS);
        CountDownLatch statusLatch = new CountDownLatch(1);
        mCdmAssociationId0 = attachTransportToCdm(mSocketPair0[0]).mAssociationId;

        mWearableSensingManager.provideConnection(
                mSocketPair0[1], EXECUTOR, (statusCode) -> statusLatch.countDown());
        assertThat(statusLatch.await(3, SECONDS)).isTrue();
        setBooleanStateInWss();
        verifyBooleanStateInWss(true);

        // Trigger a channel error
        mSocketPair0[0].close();
        sendDataToWearableFromWss(DATA_TO_WRITE_0);

        // wait until the RemoteWearableSensingService is notified of the Binder/process death
        SystemClock.sleep(2000);
        // This indirectly verifies that the process is restarted
        verifyBooleanStateInWss(false);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.service.wearable.WearableSensingService#onSecureConnectionProvided"
            })
    @Parameters({"true", "false"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void provideConnection_wssStreamClosed_channelErrorStatus(boolean isForConcurrentApi)
            throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE,
                        Manifest.permission.REQUEST_COMPANION_SELF_MANAGED,
                        Manifest.permission.USE_COMPANION_TRANSPORTS);
        AtomicInteger statusCodeRef = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch successStatusLatch = new CountDownLatch(1);
        // Count down twice because we expect a success status followed by a channel error
        CountDownLatch channelErrorStatusLatch = new CountDownLatch(2);
        mCdmAssociationId0 = attachTransportToCdm(mSocketPair0[0]).mAssociationId;

        provideConnection(
                isForConcurrentApi,
                mSocketPair0[1],
                (statusCode) -> {
                    statusCodeRef.set(statusCode);
                    successStatusLatch.countDown();
                    channelErrorStatusLatch.countDown();
                });
        assertThat(successStatusLatch.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);

        closeWearableConnectionFromWss();

        assertThat(channelErrorStatusLatch.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef.get()).isEqualTo(WearableSensingManager.STATUS_CHANNEL_ERROR);
    }

    @Test
    @ApiTest(apis = {"android.app.wearable.WearableSensingManager#provideConnection"})
    public void provideConnection_restartsWssProcess() throws Exception {
        // The first call of provideConnection may not restart the process,
        // so we call once first, then set up and call it again
        AtomicInteger statusCodeRef1 = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusCodeLatch1 = new CountDownLatch(1);
        mWearableSensingManager.provideConnection(
                mSocketPair0[1],
                EXECUTOR,
                (statusCode) -> {
                    statusCodeRef1.set(statusCode);
                    statusCodeLatch1.countDown();
                });
        assertThat(statusCodeLatch1.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef1.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);
        setBooleanStateInWss();
        // Verify that the state is true after setting it
        verifyBooleanStateInWss(true);
        AtomicInteger statusCodeRef2 = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusCodeLatch2 = new CountDownLatch(1);

        mWearableSensingManager.provideConnection(
                mSocketPair0[1],
                EXECUTOR,
                (statusCode) -> {
                    statusCodeRef2.set(statusCode);
                    statusCodeLatch2.countDown();
                });
        assertThat(statusCodeLatch2.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef2.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);

        // This indirectly verifies that the process is restarted
        verifyBooleanStateInWss(false);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.service.wearable.WearableSensingService#onSecureConnectionProvided"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void provideConnection_allowConcurrent_allConnectionsReceiveSuccessStatus()
            throws Exception {
        provideThreeConcurrentConnectionsAndVerifySuccess();
    }

    private void provideThreeConcurrentConnectionsAndVerifySuccess() {
        mWearableSensingManager.provideConnection(mWearableConnection0, EXECUTOR);
        mWearableSensingManager.provideConnection(mWearableConnection1, EXECUTOR);
        mWearableSensingManager.provideConnection(mWearableConnection2, EXECUTOR);
        // make sure WearableSensingService has received the connections
        verify(mMockStatusConsumer0, timeout(2000)).accept(WearableSensingManager.STATUS_SUCCESS);
        verify(mMockStatusConsumer1, timeout(2000)).accept(WearableSensingManager.STATUS_SUCCESS);
        verify(mMockStatusConsumer2, timeout(2000)).accept(WearableSensingManager.STATUS_SUCCESS);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.service.wearable.WearableSensingService#onSecureConnectionProvided"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void provideConnection_allowConcurrent_canReceiveDataInWss() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE,
                        Manifest.permission.REQUEST_COMPANION_SELF_MANAGED,
                        Manifest.permission.USE_COMPANION_TRANSPORTS);
        mCdmAssociationId0 = attachTransportToCdm(mSocketPair0[1]).mAssociationId;
        mCdmAssociationId1 = attachTransportToCdm(mSocketPair1[1]).mAssociationId;
        mCdmAssociationId2 = attachTransportToCdm(mSocketPair2[1]).mAssociationId;

        provideThreeConcurrentConnectionsAndVerifySuccess();

        mCompanionDeviceManager.sendMessage(
                CDM_MESSAGE_ONEWAY_FROM_WEARABLE,
                DATA_TO_WRITE_1.getBytes(StandardCharsets.UTF_8),
                new int[] {mCdmAssociationId1});
        mCompanionDeviceManager.sendMessage(
                CDM_MESSAGE_ONEWAY_FROM_WEARABLE,
                DATA_TO_WRITE_0.getBytes(StandardCharsets.UTF_8),
                new int[] {mCdmAssociationId0});
        mCompanionDeviceManager.sendMessage(
                CDM_MESSAGE_ONEWAY_FROM_WEARABLE,
                DATA_TO_WRITE_2.getBytes(StandardCharsets.UTF_8),
                new int[] {mCdmAssociationId2});
        verifyDataReceivedFromWearable(DATA_TO_WRITE_0, 0);
        verifyDataReceivedFromWearable(DATA_TO_WRITE_1, 1);
        verifyDataReceivedFromWearable(DATA_TO_WRITE_2, 2);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.service.wearable.WearableSensingService#onSecureConnectionProvided"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void provideConnection_allowConcurrent_canReceiveDataFromWss() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE,
                        Manifest.permission.REQUEST_COMPANION_SELF_MANAGED,
                        Manifest.permission.USE_COMPANION_TRANSPORTS);
        CdmSecureChannelContext cdmSecureChannelContext0 = attachTransportToCdm(mSocketPair0[1]);
        CdmSecureChannelContext cdmSecureChannelContext1 = attachTransportToCdm(mSocketPair1[1]);
        CdmSecureChannelContext cdmSecureChannelContext2 = attachTransportToCdm(mSocketPair2[1]);

        provideThreeConcurrentConnectionsAndVerifySuccess();

        sendDataToWearableFromWss(DATA_TO_WRITE_1, 1);
        sendDataToWearableFromWss(DATA_TO_WRITE_0, 0);
        sendDataToWearableFromWss(DATA_TO_WRITE_2, 2);
        assertThat(receiveMessageFromCdm(cdmSecureChannelContext0))
                .isEqualTo(DATA_TO_WRITE_0.getBytes(StandardCharsets.UTF_8));
        assertThat(receiveMessageFromCdm(cdmSecureChannelContext1))
                .isEqualTo(DATA_TO_WRITE_1.getBytes(StandardCharsets.UTF_8));
        assertThat(receiveMessageFromCdm(cdmSecureChannelContext2))
                .isEqualTo(DATA_TO_WRITE_2.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.service.wearable.WearableSensingService#onSecureConnectionProvided"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void provideMultipleConcurrentConnections_closeOne_othersCanStillBeUsed()
            throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE,
                        Manifest.permission.REQUEST_COMPANION_SELF_MANAGED,
                        Manifest.permission.USE_COMPANION_TRANSPORTS);
        provideThreeConcurrentConnectionsAndVerifySuccess();
        reset(mMockStatusConsumer0);
        reset(mMockStatusConsumer1);
        reset(mMockStatusConsumer2);
        CdmSecureChannelContext cdmSecureChannelContext0 = attachTransportToCdm(mSocketPair0[1]);
        CdmSecureChannelContext cdmSecureChannelContext2 = attachTransportToCdm(mSocketPair2[1]);

        mSocketPair1[0].close();
        mSocketPair1[1].close();
        // Send some data to trigger the error path
        sendDataToWearableFromWss(DATA_TO_WRITE_1, 1);

        verify(mMockStatusConsumer1, timeout(2000))
                .accept(WearableSensingManager.STATUS_CHANNEL_ERROR);
        verify(mMockStatusConsumer0, after(500).never()).accept(anyInt());
        verify(mMockStatusConsumer2, after(500).never()).accept(anyInt());
        // verify WSS can read data from connection0
        sendDataToWearableFromWss(DATA_TO_WRITE_0, 0);
        assertThat(receiveMessageFromCdm(cdmSecureChannelContext0))
                .isEqualTo(DATA_TO_WRITE_0.getBytes(StandardCharsets.UTF_8));
        // verify data from WSS can be read from connection0
        mCompanionDeviceManager.sendMessage(
                CDM_MESSAGE_ONEWAY_FROM_WEARABLE,
                DATA_TO_WRITE_3.getBytes(StandardCharsets.UTF_8),
                new int[] {cdmSecureChannelContext0.mAssociationId});
        verifyDataReceivedFromWearable(DATA_TO_WRITE_3, 0);
        // verify WSS can read data from connection2
        sendDataToWearableFromWss(DATA_TO_WRITE_2, 2);
        assertThat(receiveMessageFromCdm(cdmSecureChannelContext2))
                .isEqualTo(DATA_TO_WRITE_2.getBytes(StandardCharsets.UTF_8));
        // verify data from WSS can be read from connection2
        mCompanionDeviceManager.sendMessage(
                CDM_MESSAGE_ONEWAY_FROM_WEARABLE,
                DATA_TO_WRITE_4.getBytes(StandardCharsets.UTF_8),
                new int[] {cdmSecureChannelContext2.mAssociationId});
        verifyDataReceivedFromWearable(DATA_TO_WRITE_4, 2);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.service.wearable.WearableSensingService#onSecureConnectionProvided"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void provideConnection_canProvideAvailableNumberOfConnections() throws Exception {
        int availableConnectionCount = mWearableSensingManager.getAvailableConnectionCount();
        assumeTrue(availableConnectionCount > 0);

        for (int i = 0; i < availableConnectionCount; i++) {
            provideNewConnectionAndVerifySuccess();
        }
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.service.wearable.WearableSensingService#onSecureConnectionProvided"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void provideConnection_cannotProvideConnectionAfterReachingLimit() throws Exception {
        int availableConnectionCount = mWearableSensingManager.getAvailableConnectionCount();
        assumeTrue(availableConnectionCount > 0);
        for (int i = 0; i < availableConnectionCount; i++) {
            provideNewConnectionAndVerifySuccess();
        }
        assertThat(mWearableSensingManager.getAvailableConnectionCount()).isEqualTo(0);
        removeAllStoredConnectionsFromWss();
        Consumer<Integer> mockStatusConsumer = (Consumer<Integer>) mock(Consumer.class);
        WearableConnection newConnection = createNewConnection(mockStatusConsumer);

        mWearableSensingManager.provideConnection(newConnection, EXECUTOR);

        verify(mockStatusConsumer, timeout(2000))
                .accept(WearableSensingManager.STATUS_MAX_CONCURRENT_CONNECTIONS_EXCEEDED);
        SystemClock.sleep(2000); // make sure all async operations have completed
        verifyNoConnectionProvidedToWss();
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.app.wearable.WearableSensingManager#removeConnection",
                "android.service.wearable.WearableSensingService#onSecureConnectionProvided"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void provideConnection_reachedConnectionLimit_canProvideAfterRemovingConnection()
            throws Exception {
        int availableConnectionCount = mWearableSensingManager.getAvailableConnectionCount();
        assumeTrue(availableConnectionCount > 0);
        WearableConnection firstConnection = provideNewConnectionAndVerifySuccess();
        for (int i = 0; i < availableConnectionCount - 1; i++) {
            provideNewConnectionAndVerifySuccess();
        }
        assertThat(mWearableSensingManager.getAvailableConnectionCount()).isEqualTo(0);

        // remove the first, provide again, verify success
        mWearableSensingManager.removeConnection(firstConnection);
        WearableConnection lastConnection = provideNewConnectionAndVerifySuccess();

        // remove the last, provide again, verify success
        mWearableSensingManager.removeConnection(lastConnection);
        provideNewConnectionAndVerifySuccess();
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.app.wearable.WearableSensingManager#removeAllConnections",
                "android.service.wearable.WearableSensingService#onSecureConnectionProvided"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void provideConnection_reachedConnectionLimit_canProvideAfterRemovingAllConnections()
            throws Exception {
        int maxAvailableConnectionCount = mWearableSensingManager.getAvailableConnectionCount();
        assumeTrue(maxAvailableConnectionCount > 0);
        for (int i = 0; i < maxAvailableConnectionCount; i++) {
            provideNewConnectionAndVerifySuccess();
        }
        assertThat(mWearableSensingManager.getAvailableConnectionCount()).isEqualTo(0);

        mWearableSensingManager.removeAllConnections();

        for (int i = 0; i < maxAvailableConnectionCount; i++) {
            provideNewConnectionAndVerifySuccess();
        }
    }

    private WearableConnection provideNewConnectionAndVerifySuccess() throws Exception {
        Consumer<Integer> mockStatusConsumer = (Consumer<Integer>) mock(Consumer.class);
        WearableConnection connection = createNewConnection(mockStatusConsumer);
        mWearableSensingManager.provideConnection(connection, EXECUTOR);
        verify(mockStatusConsumer, timeout(2000)).accept(WearableSensingManager.STATUS_SUCCESS);
        return connection;
    }

    private WearableConnection createNewConnection(Consumer<Integer> statusConsumer)
            throws Exception {
        ParcelFileDescriptor[] socketPair = ParcelFileDescriptor.createSocketPair();
        mParcelFileDescriptorsToCleanUp.add(socketPair[0]);
        mParcelFileDescriptorsToCleanUp.add(socketPair[1]);
        return createWearableConnection(socketPair[0], PersistableBundle.EMPTY, statusConsumer);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.app.wearable.WearableSensingManager#removeConnection"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void removeConnection_connectionPreviouslyProvidedAndNotYetRemoved_noException() {
        mWearableSensingManager.provideConnection(mWearableConnection0, EXECUTOR);
        verify(mMockStatusConsumer0, timeout(2000)).accept(WearableSensingManager.STATUS_SUCCESS);

        mWearableSensingManager.removeConnection(mWearableConnection0);
        // no exception
    }

    @Test
    @ApiTest(apis = {"android.app.wearable.WearableSensingManager#removeConnection"})
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void removeConnection_connectionNotPreviouslyProvided_throwsNoSuchElementException() {
        assertThrows(
                NoSuchElementException.class,
                () -> mWearableSensingManager.removeConnection(mWearableConnection0));
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.app.wearable.WearableSensingManager#removeConnection"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void removeConnection_connectionAlreadyRemoved_throwsNoSuchElementException() {
        mWearableSensingManager.provideConnection(mWearableConnection0, EXECUTOR);
        verify(mMockStatusConsumer0, timeout(2000)).accept(WearableSensingManager.STATUS_SUCCESS);
        mWearableSensingManager.removeConnection(mWearableConnection0);

        assertThrows(
                NoSuchElementException.class,
                () -> mWearableSensingManager.removeConnection(mWearableConnection0));
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.app.wearable.WearableSensingManager#removeConnection",
                "android.app.wearable.WearableSensingManager#removeAllConnections"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void
            removeConnection_connectionAlreadyRemovedFromRemoveAll_throwsNoSuchElementException() {
        mWearableSensingManager.provideConnection(mWearableConnection0, EXECUTOR);
        verify(mMockStatusConsumer0, timeout(2000)).accept(WearableSensingManager.STATUS_SUCCESS);
        mWearableSensingManager.removeAllConnections();

        assertThrows(
                NoSuchElementException.class,
                () -> mWearableSensingManager.removeConnection(mWearableConnection0));
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.app.wearable.WearableSensingManager#removeConnection"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void removeConnection_cannotSendMessageFromWss() throws Exception {
        mWearableSensingManager.provideConnection(mWearableConnection0, EXECUTOR);
        verify(mMockStatusConsumer0, timeout(2000)).accept(WearableSensingManager.STATUS_SUCCESS);

        mWearableSensingManager.removeConnection(mWearableConnection0);

        sendDataFromWssAndVerifyException(/* connectionId= */ 0);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.app.wearable.WearableSensingManager#removeConnection"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void removeConnection_otherConnectionsCanStillBeUsed() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE,
                        Manifest.permission.REQUEST_COMPANION_SELF_MANAGED,
                        Manifest.permission.USE_COMPANION_TRANSPORTS);
        provideThreeConcurrentConnectionsAndVerifySuccess();
        CdmSecureChannelContext cdmSecureChannelContext0 = attachTransportToCdm(mSocketPair0[1]);
        CdmSecureChannelContext cdmSecureChannelContext2 = attachTransportToCdm(mSocketPair2[1]);

        mWearableSensingManager.removeConnection(mWearableConnection1);

        // verify WSS can read data from connection0
        sendDataToWearableFromWss(DATA_TO_WRITE_0, 0);
        assertThat(receiveMessageFromCdm(cdmSecureChannelContext0))
                .isEqualTo(DATA_TO_WRITE_0.getBytes(StandardCharsets.UTF_8));
        // verify data from WSS can be read from connection0
        mCompanionDeviceManager.sendMessage(
                CDM_MESSAGE_ONEWAY_FROM_WEARABLE,
                DATA_TO_WRITE_3.getBytes(StandardCharsets.UTF_8),
                new int[] {cdmSecureChannelContext0.mAssociationId});
        verifyDataReceivedFromWearable(DATA_TO_WRITE_3, 0);
        // verify WSS can read data from connection2
        sendDataToWearableFromWss(DATA_TO_WRITE_2, 2);
        assertThat(receiveMessageFromCdm(cdmSecureChannelContext2))
                .isEqualTo(DATA_TO_WRITE_2.getBytes(StandardCharsets.UTF_8));
        // verify data from WSS can be read from connection2
        mCompanionDeviceManager.sendMessage(
                CDM_MESSAGE_ONEWAY_FROM_WEARABLE,
                DATA_TO_WRITE_4.getBytes(StandardCharsets.UTF_8),
                new int[] {cdmSecureChannelContext2.mAssociationId});
        verifyDataReceivedFromWearable(DATA_TO_WRITE_4, 2);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.app.wearable.WearableSensingManager#removeAllConnections"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void removeAllConnections_cannotSendMessageFromWss() throws Exception {
        provideThreeConcurrentConnectionsAndVerifySuccess();

        mWearableSensingManager.removeAllConnections();

        sendDataFromWssAndVerifyException(/* connectionId= */ 0);
        sendDataFromWssAndVerifyException(/* connectionId= */ 1);
        sendDataFromWssAndVerifyException(/* connectionId= */ 2);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.app.wearable.WearableSensingManager#getAvailableConnectionCount"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void getAvailableConnectionCount_returnsReducedQuotaAfterProvidingConnections() {
        int initialCount = mWearableSensingManager.getAvailableConnectionCount();
        assumeTrue(initialCount >= 3);

        mWearableSensingManager.provideConnection(mWearableConnection0, EXECUTOR);
        assertThat(mWearableSensingManager.getAvailableConnectionCount())
                .isEqualTo(initialCount - 1);

        mWearableSensingManager.provideConnection(mWearableConnection1, EXECUTOR);
        assertThat(mWearableSensingManager.getAvailableConnectionCount())
                .isEqualTo(initialCount - 2);

        mWearableSensingManager.provideConnection(mWearableConnection2, EXECUTOR);
        assertThat(mWearableSensingManager.getAvailableConnectionCount())
                .isEqualTo(initialCount - 3);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideConnection",
                "android.app.wearable.WearableSensingManager#removeConnection",
                "android.app.wearable.WearableSensingManager#getAvailableConnectionCount"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void getAvailableConnectionCount_returnsUpdatedQuotaAfterRemovingConnections() {
        assumeTrue(mWearableSensingManager.getAvailableConnectionCount() >= 3);
        provideThreeConcurrentConnectionsAndVerifySuccess();
        int quotaAfterProvidingThreeConnections =
                mWearableSensingManager.getAvailableConnectionCount();

        mWearableSensingManager.removeConnection(mWearableConnection1);
        assertThat(mWearableSensingManager.getAvailableConnectionCount())
                .isEqualTo(quotaAfterProvidingThreeConnections + 1);

        // Removing an already removed connection does not further increase quota
        assertThrows(
                NoSuchElementException.class,
                () -> mWearableSensingManager.removeConnection(mWearableConnection1));
        assertThat(mWearableSensingManager.getAvailableConnectionCount())
                .isEqualTo(quotaAfterProvidingThreeConnections + 1);

        mWearableSensingManager.removeConnection(mWearableConnection0);
        assertThat(mWearableSensingManager.getAvailableConnectionCount())
                .isEqualTo(quotaAfterProvidingThreeConnections + 2);

        mWearableSensingManager.removeConnection(mWearableConnection2);
        assertThat(mWearableSensingManager.getAvailableConnectionCount())
                .isEqualTo(quotaAfterProvidingThreeConnections + 3);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#removeAllConnections",
                "android.app.wearable.WearableSensingManager#getAvailableConnectionCount"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS)
    public void getAvailableConnectionCount_returnsInitialQuotaAfterRemovingAllConnections() {
        // We know that initialCount is the max count because we called #removeAllConnection in
        // #setup
        int initialCount = mWearableSensingManager.getAvailableConnectionCount();
        assumeTrue(initialCount >= 3);
        // Intentionally do not wait for the status callback. #removeAllConnections should remove
        // connections even if their status callback has not been triggered as long as
        // #provideConnection has returned.
        mWearableSensingManager.provideConnection(mWearableConnection0, EXECUTOR);
        mWearableSensingManager.provideConnection(mWearableConnection1, EXECUTOR);
        mWearableSensingManager.provideConnection(mWearableConnection2, EXECUTOR);

        mWearableSensingManager.removeAllConnections();

        assertThat(mWearableSensingManager.getAvailableConnectionCount()).isEqualTo(initialCount);
    }

    @Test
    @Parameters({"true", "false"})
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS})
    public void openFileInputFromWss_afterProvideConnection_canReadFile(boolean isForConcurrentApi)
            throws Exception {
        String filename = "fileFromProvideConnection";
        File file = new File(mTestDirectory, filename);
        writeFile(file.getAbsolutePath(), FILE_CONTENT_1);
        AtomicInteger statusCodeRef1 = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusCodeLatch1 = new CountDownLatch(1);
        provideConnection(
                isForConcurrentApi,
                mSocketPair0[1],
                (statusCode) -> {
                    statusCodeRef1.set(statusCode);
                    statusCodeLatch1.countDown();
                });
        assertThat(statusCodeLatch1.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef1.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);

        readDataAndVerify(FILE_DIRECTORY_NAME + "/" + filename, FILE_CONTENT_1);
    }

    @Test
    public void openFileInputFromWss_afterProvideDataStream_canReadFile() throws Exception {
        String filename = "fileFromProvideDataStream";
        File file = new File(mTestDirectory, filename);
        writeFile(file.getAbsolutePath(), FILE_CONTENT_1);
        AtomicInteger statusCodeRef1 = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusCodeLatch1 = new CountDownLatch(1);
        mWearableSensingManager.provideDataStream(
                mSocketPair0[1],
                EXECUTOR,
                (statusCode) -> {
                    statusCodeRef1.set(statusCode);
                    statusCodeLatch1.countDown();
                });
        assertThat(statusCodeLatch1.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef1.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);

        readDataAndVerify(FILE_DIRECTORY_NAME + "/" + filename, FILE_CONTENT_1);
    }

    @Test
    @Parameters({"true", "false"})
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS})
    public void openFileInputFromWss_afterProvideConnection_canReadMultipleFiles(
            boolean isForConcurrentApi) throws Exception {
        String filename1 = "multipleFilesFromProvideConnection1";
        File file1 = new File(mTestDirectory, filename1);
        writeFile(file1.getAbsolutePath(), FILE_CONTENT_1);
        String filename2 = "multipleFilesFromProvideConnection2";
        File file2 = new File(mTestDirectory, filename2);
        writeFile(file2.getAbsolutePath(), FILE_CONTENT_2);
        AtomicInteger statusCodeRef1 = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusCodeLatch1 = new CountDownLatch(1);
        provideConnection(
                isForConcurrentApi,
                mSocketPair0[1],
                (statusCode) -> {
                    statusCodeRef1.set(statusCode);
                    statusCodeLatch1.countDown();
                });
        assertThat(statusCodeLatch1.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef1.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);

        readDataAndVerify(FILE_DIRECTORY_NAME + "/" + filename1, FILE_CONTENT_1);
        readDataAndVerify(FILE_DIRECTORY_NAME + "/" + filename2, FILE_CONTENT_2);
    }

    @Test
    public void openFileInputFromWss_afterProvideDataStream_canReadMultipleFiles()
            throws Exception {
        String filename1 = "multipleFilesFromProvideDataStream1";
        File file1 = new File(mTestDirectory, filename1);
        writeFile(file1.getAbsolutePath(), FILE_CONTENT_1);
        String filename2 = "multipleFilesFromProvideDataStream2";
        File file2 = new File(mTestDirectory, filename2);
        writeFile(file2.getAbsolutePath(), FILE_CONTENT_2);
        AtomicInteger statusCodeRef1 = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusCodeLatch1 = new CountDownLatch(1);
        mWearableSensingManager.provideDataStream(
                mSocketPair0[1],
                EXECUTOR,
                (statusCode) -> {
                    statusCodeRef1.set(statusCode);
                    statusCodeLatch1.countDown();
                });
        assertThat(statusCodeLatch1.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef1.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);

        readDataAndVerify(FILE_DIRECTORY_NAME + "/" + filename1, FILE_CONTENT_1);
        readDataAndVerify(FILE_DIRECTORY_NAME + "/" + filename2, FILE_CONTENT_2);
    }

    @Test
    @Parameters({"true", "false"})
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS})
    public void
            openFileInputFromWss_afterProvideConnection_readNonExistentFile_FileNotFoundException(
                    boolean isForConcurrentApi) throws Exception {
        // This also implicitly tests that the process calling WearableSensingManager (i.e. the test
        // runner) does not crash.
        AtomicInteger statusCodeRef1 = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusCodeLatch1 = new CountDownLatch(1);
        provideConnection(
                isForConcurrentApi,
                mSocketPair0[1],
                (statusCode) -> {
                    statusCodeRef1.set(statusCode);
                    statusCodeLatch1.countDown();
                });
        assertThat(statusCodeLatch1.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef1.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);

        readDataAndVerify(FILE_DIRECTORY_NAME + "/nonExistentFile", FileNotFoundException.class);
    }

    @Test
    public void
            openFileInputFromWss_afterProvideDataStream_readNonExistentFile_FileNotFoundException()
                    throws Exception {
        // This also implicitly tests that the process calling WearableSensingManager (i.e. the test
        // runner) does not crash.
        AtomicInteger statusCodeRef1 = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusCodeLatch1 = new CountDownLatch(1);
        mWearableSensingManager.provideDataStream(
                mSocketPair0[1],
                EXECUTOR,
                (statusCode) -> {
                    statusCodeRef1.set(statusCode);
                    statusCodeLatch1.countDown();
                });
        assertThat(statusCodeLatch1.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef1.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);

        readDataAndVerify(FILE_DIRECTORY_NAME + "/nonExistentFile", FileNotFoundException.class);
    }

    @Test
    @Parameters({"true", "false"})
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CONCURRENT_WEARABLE_CONNECTIONS})
    public void openFileInputFromWss_afterProvideConnection_cannotWriteToFile(
            boolean isForConcurrentApi) throws Exception {
        String filename = "fileToTryWritingToFromProvideConnection";
        File file = new File(mTestDirectory, filename);
        writeFile(file.getAbsolutePath(), FILE_CONTENT_1);
        AtomicInteger statusCodeRef1 = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusCodeLatch1 = new CountDownLatch(1);
        provideConnection(
                isForConcurrentApi,
                mSocketPair0[1],
                (statusCode) -> {
                    statusCodeRef1.set(statusCode);
                    statusCodeLatch1.countDown();
                });
        assertThat(statusCodeLatch1.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef1.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);

        writeDataAndVerifyException(FILE_DIRECTORY_NAME + "/" + filename);
    }

    @Test
    public void openFileInputFromWss_afterProvideDataStream_cannotWriteToFile() throws Exception {
        String filename = "fileToTryWritingToFromProvideDataStream";
        File file = new File(mTestDirectory, filename);
        writeFile(file.getAbsolutePath(), FILE_CONTENT_1);
        AtomicInteger statusCodeRef1 = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusCodeLatch1 = new CountDownLatch(1);
        mWearableSensingManager.provideDataStream(
                mSocketPair0[1],
                EXECUTOR,
                (statusCode) -> {
                    statusCodeRef1.set(statusCode);
                    statusCodeLatch1.countDown();
                });
        assertThat(statusCodeLatch1.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef1.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);

        writeDataAndVerifyException(FILE_DIRECTORY_NAME + "/" + filename);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideReadOnlyParcelFileDescriptor"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PROVIDE_READ_ONLY_PFD)
    public void provideReadOnlyParcelFileDescriptor_fromSocketPair_throwsException()
            throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mWearableSensingManager.provideReadOnlyParcelFileDescriptor(
                                mSocketPair0[0],
                                PersistableBundle.EMPTY,
                                EXECUTOR,
                                mMockStatusConsumer0));
        // Sleep to make sure async calls are complete
        SystemClock.sleep(2000);
        verifyNoReadOnlyPfdProvidedToWss();
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideReadOnlyParcelFileDescriptor"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PROVIDE_READ_ONLY_PFD)
    public void provideReadOnlyParcelFileDescriptor_writeEndOfPipe_throwsException()
            throws Exception {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        mParcelFileDescriptorsToCleanUp.add(pipe[0]);
        mParcelFileDescriptorsToCleanUp.add(pipe[1]);

        // pipe index 1 is write end
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mWearableSensingManager.provideReadOnlyParcelFileDescriptor(
                                pipe[1], PersistableBundle.EMPTY, EXECUTOR, mMockStatusConsumer0));
        // Sleep to make sure async calls are complete
        SystemClock.sleep(2000);
        verifyNoReadOnlyPfdProvidedToWss();
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideReadOnlyParcelFileDescriptor",
                "android.service.wearable.WearableSensingService"
                        + "#onReadOnlyParcelFileDescriptorProvided"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PROVIDE_READ_ONLY_PFD)
    public void provideReadOnlyParcelFileDescriptor_readEndOfPipe_canReadFromWss()
            throws Exception {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        mParcelFileDescriptorsToCleanUp.add(pipe[0]);
        mParcelFileDescriptorsToCleanUp.add(pipe[1]);
        OutputStream outputStream = new AutoCloseOutputStream(pipe[1]);
        outputStream.write(DATA_TO_WRITE_0.getBytes(StandardCharsets.UTF_8));

        // pipe index 0 is read end
        mWearableSensingManager.provideReadOnlyParcelFileDescriptor(
                pipe[0], PersistableBundle.EMPTY, EXECUTOR, mMockStatusConsumer0);

        verify(mMockStatusConsumer0, timeout(2000)).accept(WearableSensingManager.STATUS_SUCCESS);
        verifyDataReceivedFromReadOnlyPfd(DATA_TO_WRITE_0);
        // write again after WSS receives the stream
        outputStream.write(DATA_TO_WRITE_1.getBytes(StandardCharsets.UTF_8));
        verifyDataReceivedFromReadOnlyPfd(DATA_TO_WRITE_1);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideReadOnlyParcelFileDescriptor"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PROVIDE_READ_ONLY_PFD)
    public void provideReadOnlyParcelFileDescriptor_writableFileHandle_throwsException()
            throws Exception {
        String filename = "provideReadOnlyParcelFileDescriptor1";
        File file = new File(mTestDirectory, filename);
        ParcelFileDescriptor writablePfd =
                ParcelFileDescriptor.dup(new FileOutputStream(file).getFD());
        mParcelFileDescriptorsToCleanUp.add(writablePfd);

        // pipe index 1 is write end
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mWearableSensingManager.provideReadOnlyParcelFileDescriptor(
                                writablePfd,
                                PersistableBundle.EMPTY,
                                EXECUTOR,
                                mMockStatusConsumer0));
        // Sleep to make sure async calls are complete
        SystemClock.sleep(2000);
        verifyNoReadOnlyPfdProvidedToWss();
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideReadOnlyParcelFileDescriptor",
                "android.service.wearable.WearableSensingService"
                        + "#onReadOnlyParcelFileDescriptorProvided"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PROVIDE_READ_ONLY_PFD)
    public void provideReadOnlyParcelFileDescriptor_readOnlyFile_canReadFromWss() throws Exception {
        String filename = "provideReadOnlyParcelFileDescriptor2";
        File file = new File(mTestDirectory, filename);
        writeFile(file.getAbsolutePath(), DATA_TO_WRITE_0);

        mWearableSensingManager.provideReadOnlyParcelFileDescriptor(
                ParcelFileDescriptor.dup(new FileInputStream(file).getFD()),
                PersistableBundle.EMPTY,
                EXECUTOR,
                mMockStatusConsumer0);

        verify(mMockStatusConsumer0, timeout(2000)).accept(WearableSensingManager.STATUS_SUCCESS);
        verifyDataReceivedFromReadOnlyPfd(DATA_TO_WRITE_0);
    }

    @Test
    @ApiTest(
            apis = {
                "android.app.wearable.WearableSensingManager#provideReadOnlyParcelFileDescriptor",
                "android.service.wearable.WearableSensingService"
                        + "#onReadOnlyParcelFileDescriptorProvided"
            })
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PROVIDE_READ_ONLY_PFD)
    public void provideReadOnlyParcelFileDescriptor_providesCorrectMetadata() throws Exception {
        ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
        mParcelFileDescriptorsToCleanUp.add(pipe[0]);
        mParcelFileDescriptorsToCleanUp.add(pipe[1]);
        PersistableBundle metadata = new PersistableBundle();
        metadata.putString(METADATA_KEY, DATA_TO_WRITE_0);

        // pipe index 0 is read end
        mWearableSensingManager.provideReadOnlyParcelFileDescriptor(
                pipe[0], metadata, EXECUTOR, mMockStatusConsumer0);

        verify(mMockStatusConsumer0, timeout(2000)).accept(WearableSensingManager.STATUS_SUCCESS);
        verifyExpectedMetadataReceived(DATA_TO_WRITE_0);
    }

    private void writeFile(String absolutePath, String content) throws Exception {
        try (PrintWriter printWriter = new PrintWriter(new File(absolutePath))) {
            printWriter.print(content);
        }
    }

    private void readDataAndVerify(String relativeFilePath, String expectedFileContent)
            throws Exception {
        readDataAndVerify(
                relativeFilePath, expectedFileContent, /* expectedExceptionClass= */ null);
    }

    private void readDataAndVerify(
            String relativeFilePath, Class<? extends Exception> expectedExceptionClass)
            throws Exception {
        readDataAndVerify(
                relativeFilePath, /* expectedFileContent= */ null, expectedExceptionClass);
    }

    private void readDataAndVerify(
            String relativeFilePath,
            String expectedFileContent,
            Class<? extends Exception> expectedExceptionClass)
            throws Exception {
        PersistableBundle instruction = new PersistableBundle();
        instruction.putString(BUNDLE_ACTION_KEY, ACTION_READ_FILE_AND_VERIFY);
        instruction.putString(FILE_PATH_KEY, relativeFilePath);
        if (expectedFileContent != null) {
            instruction.putString(EXPECTED_FILE_CONTENT_KEY, expectedFileContent);
        }
        if (expectedExceptionClass != null) {
            instruction.putString(EXPECTED_EXCEPTION_KEY, expectedExceptionClass.getSimpleName());
        }
        assertThat(sendInstructionToIsolatedWearableSensingServiceAndWait(instruction))
                .isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    private void writeDataAndVerifyException(String relativeFilePath) throws Exception {
        PersistableBundle instruction = new PersistableBundle();
        instruction.putString(BUNDLE_ACTION_KEY, ACTION_WRITE_FILE_AND_VERIFY_EXCEPTION);
        instruction.putString(FILE_PATH_KEY, relativeFilePath);
        assertThat(sendInstructionToIsolatedWearableSensingServiceAndWait(instruction))
                .isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    private void sendDataFromWssAndVerifyException(int connectionId) throws Exception {
        PersistableBundle instruction = new PersistableBundle();
        instruction.putString(BUNDLE_ACTION_KEY, ACTION_WRITE_TO_CONNECTION_AND_VERIFY_EXCEPTION);
        instruction.putInt(BUNDLE_CONNECTION_ID_KEY, connectionId);
        assertThat(sendInstructionToIsolatedWearableSensingServiceAndWait(instruction))
                .isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    private CdmSecureChannelContext attachTransportToCdm(ParcelFileDescriptor pfd)
            throws Exception {
        // Tests that call this method will trigger a CDM attestation.
        // On user builds, the attestation requires a certificate that is only provided to the
        // device after it passes CTS tests, which means it can't pass the attestation. Attestation
        // failure will cause the system to kill the WearableSensingService process, so tests that
        // call this method will fail or become flaky, so we ignore them on user builds.
        assumeTrue(Build.isDebuggable());
        CountDownLatch transportAvailableLatch = new CountDownLatch(1);
        AtomicReference<CdmSecureChannelContext> cdmSecureChannelContext = new AtomicReference<>();
        mCompanionDeviceManager.associate(
                new AssociationRequest.Builder()
                        .setDisplayName(CDM_ASSOCIATION_DISPLAY_NAME)
                        .setSelfManaged(true)
                        .build(),
                EXECUTOR,
                new CompanionDeviceManager.Callback() {
                    @Override
                    public void onAssociationCreated(AssociationInfo associationInfo) {
                        Log.d(
                                TAG,
                                "#onAssociationCreated, associationId: " + associationInfo.getId());
                        cdmSecureChannelContext.set(
                                new CdmSecureChannelContext(associationInfo.getId()));
                        mCompanionDeviceManager.addOnTransportsChangedListener(
                                EXECUTOR,
                                associationInfos -> {
                                    if (associationInfos.stream()
                                            .anyMatch(
                                                    info ->
                                                            info.getId()
                                                                    == associationInfo.getId())) {
                                        transportAvailableLatch.countDown();
                                    }
                                });
                        // OnMessageReceivedListener must be registered before attaching the
                        // transport. This is because CDM will drop messages that arrive before the
                        // listener is registered.
                        mCompanionDeviceManager.addOnMessageReceivedListener(
                                EXECUTOR,
                                CDM_MESSAGE_ONEWAY_TO_WEARABLE,
                                (associationIdForMessage, messageBytes) -> {
                                    if (associationIdForMessage == associationInfo.getId()) {
                                        cdmSecureChannelContext
                                                .get()
                                                .mMessageReceived
                                                .set(messageBytes);
                                        cdmSecureChannelContext
                                                .get()
                                                .mOnMessageReceivedLatch
                                                .countDown();
                                    }
                                });
                        mCompanionDeviceManager.attachSystemDataTransport(
                                associationInfo.getId(),
                                new AutoCloseInputStream(pfd),
                                new AutoCloseOutputStream(pfd));
                    }

                    @Override
                    public void onFailure(CharSequence error) {
                        Log.e(TAG, "Failed to create CDM association: " + error);
                    }
                });
        assertThat(transportAvailableLatch.await(3, SECONDS)).isTrue();
        return cdmSecureChannelContext.get();
    }

    private byte[] receiveMessageFromCdm(CdmSecureChannelContext cdmSecureChannelContext)
            throws InterruptedException {
        assertThat(cdmSecureChannelContext.mOnMessageReceivedLatch.await(3, SECONDS)).isTrue();
        return cdmSecureChannelContext.mMessageReceived.get();
    }

    private void clearTestableWearableSensingService() {
        runShellCommand("cmd wearable_sensing set-temporary-service %d", USER_ID);
    }

    private void bindToIsolatedWearableSensingService() {
        assertThat(getWearableSensingServiceComponent()).isNotEqualTo(CTS_ISOLATED_SERVICE_NAME);
        setTestableWearableSensingService(CTS_ISOLATED_SERVICE_NAME);
        assertThat(CTS_ISOLATED_SERVICE_NAME).contains(getWearableSensingServiceComponent());
    }

    private String getWearableSensingServiceComponent() {
        return runShellCommand("cmd wearable_sensing get-bound-package %d", USER_ID);
    }

    private void setTestableWearableSensingService(String service) {
        runShellCommand(
                "cmd wearable_sensing set-temporary-service %d %s %d",
                USER_ID, service, TEMPORARY_SERVICE_DURATION);
    }

    private void resetIsolatedWearableSensingServiceStates() throws Exception {
        sendActionToIsolatedWearableSensingServiceAndWait(ACTION_RESET);
    }

    private void verifyDataReceivedFromWearable(String expectedString) throws Exception {
        // This tests the non-concurrent connection API, so we use INVALID_CONNECTION_ID
        verifyDataReceivedFromWearable(expectedString, /* connectionId= */ INVALID_CONNECTION_ID);
    }

    private void verifyDataReceivedFromWearable(String expectedString, int connectionId)
            throws Exception {
        PersistableBundle instruction = new PersistableBundle();
        instruction.putString(BUNDLE_ACTION_KEY, ACTION_VERIFY_DATA_RECEIVED_FROM_WEARABLE);
        instruction.putString(EXPECTED_STRING_KEY, expectedString);
        if (connectionId != INVALID_CONNECTION_ID) {
            instruction.putInt(BUNDLE_CONNECTION_ID_KEY, connectionId);
        }
        assertThat(sendInstructionToIsolatedWearableSensingServiceAndWait(instruction))
                .isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    private void sendDataToWearableFromWss(String dataToSend) throws Exception {
        // This tests the non-concurrent connection API, so we use INVALID_CONNECTION_ID
        sendDataToWearableFromWss(dataToSend, /* connectionId= */ INVALID_CONNECTION_ID);
    }

    private void sendDataToWearableFromWss(String dataToSend, int connectionId) throws Exception {
        PersistableBundle instruction = new PersistableBundle();
        instruction.putString(BUNDLE_ACTION_KEY, ACTION_SEND_DATA_TO_WEARABLE);
        instruction.putString(STRING_TO_SEND_KEY, dataToSend);
        if (connectionId != INVALID_CONNECTION_ID) {
            instruction.putInt(BUNDLE_CONNECTION_ID_KEY, connectionId);
        }
        assertThat(sendInstructionToIsolatedWearableSensingServiceAndWait(instruction))
                .isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    private void closeWearableConnectionFromWss() throws Exception {
        sendActionToIsolatedWearableSensingServiceAndWait(ACTION_CLOSE_WEARABLE_CONNECTION);
    }

    private void setBooleanStateInWss() throws Exception {
        sendActionToIsolatedWearableSensingServiceAndWait(ACTION_SET_BOOLEAN_STATE);
    }

    private void verifyBooleanStateInWss(boolean expectedState) throws Exception {
        int statusCode =
                sendActionToIsolatedWearableSensingServiceAndWait(ACTION_VERIFY_BOOLEAN_STATE);
        if (expectedState) {
            assertThat(statusCode).isEqualTo(WearableSensingManager.STATUS_SUCCESS);
        } else {
            assertThat(statusCode).isEqualTo(WearableSensingManager.STATUS_UNKNOWN);
        }
    }

    private void removeAllStoredConnectionsFromWss() throws Exception {
        assertThat(
                        sendActionToIsolatedWearableSensingServiceAndWait(
                                ACTION_REMOVE_ALL_STORED_CONNECTIONS))
                .isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    private void verifyNoConnectionProvidedToWss() throws Exception {
        assertThat(
                        sendActionToIsolatedWearableSensingServiceAndWait(
                                ACTION_VERIFY_NO_CONNECTION_PROVIDED))
                .isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    private void verifyNoReadOnlyPfdProvidedToWss() throws Exception {
        assertThat(
                        sendActionToIsolatedWearableSensingServiceAndWait(
                                ACTION_VERIFY_NO_READ_ONLY_PFD_PROVIDED))
                .isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    private void verifyDataReceivedFromReadOnlyPfd(String expectedString) throws Exception {
        PersistableBundle instruction = new PersistableBundle();
        instruction.putString(BUNDLE_ACTION_KEY, ACTION_VERIFY_DATA_RECEIVED_FROM_PFD);
        instruction.putString(EXPECTED_STRING_KEY, expectedString);
        assertThat(sendInstructionToIsolatedWearableSensingServiceAndWait(instruction))
                .isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    private void verifyExpectedMetadataReceived(String expectedString) throws Exception {
        PersistableBundle instruction = new PersistableBundle();
        instruction.putString(BUNDLE_ACTION_KEY, ACTION_VERIFY_METADATA);
        instruction.putString(EXPECTED_STRING_KEY, expectedString);
        assertThat(sendInstructionToIsolatedWearableSensingServiceAndWait(instruction))
                .isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    private int sendActionToIsolatedWearableSensingServiceAndWait(String action) throws Exception {
        PersistableBundle instruction = new PersistableBundle();
        instruction.putString(BUNDLE_ACTION_KEY, action);
        return sendInstructionToIsolatedWearableSensingServiceAndWait(instruction);
    }

    private int sendInstructionToIsolatedWearableSensingServiceAndWait(
            PersistableBundle instruction) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger statusRef = new AtomicInteger();
        mWearableSensingManager.provideData(
                instruction,
                null,
                EXECUTOR,
                (status) -> {
                    statusRef.set(status);
                    latch.countDown();
                });
        assertThat(latch.await(3, SECONDS)).isTrue();
        return statusRef.get();
    }

    /**
     * The two overloads of #provideConnection have many shared tests. We use this method to
     * facilitate parameterizing the tests.
     */
    private void provideConnection(
            boolean isConcurrentApi,
            ParcelFileDescriptor parcelFileDescriptor,
            Consumer<Integer> statusCallback) {
        if (isConcurrentApi) {
            mWearableSensingManager.provideConnection(
                    createWearableConnection(
                            parcelFileDescriptor, PersistableBundle.EMPTY, statusCallback),
                    EXECUTOR);
        } else {
            mWearableSensingManager.provideConnection(
                    parcelFileDescriptor, EXECUTOR, statusCallback);
        }
    }

    private WearableConnection createWearableConnection(
            ParcelFileDescriptor parcelFileDescriptor,
            PersistableBundle metadata,
            Consumer<Integer> statusConsumer) {
        return new WearableConnection() {
            @NonNull
            @Override
            public ParcelFileDescriptor getConnection() {
                return parcelFileDescriptor;
            }

            @NonNull
            @Override
            public PersistableBundle getMetadata() {
                return metadata;
            }

            @Override
            public void onConnectionAccepted() {
                statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
            }

            @Override
            public void onError(int errorCode) {
                if (errorCode == WearableSensingManager.STATUS_SUCCESS) {
                    throw new IllegalArgumentException(
                            "Received success status code from onError.");
                }
                statusConsumer.accept(errorCode);
            }
        };
    }

    private static boolean isTelevision(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    private static boolean isWatch(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    private static class CdmSecureChannelContext {
        final int mAssociationId;
        final CountDownLatch mOnMessageReceivedLatch = new CountDownLatch(1);
        final AtomicReference<byte[]> mMessageReceived = new AtomicReference<>();

        CdmSecureChannelContext(int associationId) {
            this.mAssociationId = associationId;
        }
    }
}
