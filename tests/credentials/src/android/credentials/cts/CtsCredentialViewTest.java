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

package android.credentials.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.credentials.Credential;
import android.credentials.CredentialOption;
import android.credentials.GetCredentialException;
import android.credentials.GetCredentialRequest;
import android.credentials.GetCredentialResponse;
import android.credentials.cts.testcore.CtsCredentialManagerUtils;
import android.credentials.cts.testcore.DeviceConfigStateRequiredRule;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.RemoteException;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.os.BuildCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

@AppModeFull
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class CtsCredentialViewTest {
    private static final String LOG_TAG = "CtsCredentialViewTest";
    public static final String DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER =
            "enable_credential_manager";
    private final Context mContext = getInstrumentation().getContext();
    private ViewTestCtsActivity mActivity;
    private static final Binder CALLBACK =
            new Binder() {
                @Override
                protected boolean onTransact(
                        final int code,
                        @NonNull final Parcel data,
                        final Parcel reply,
                        final int flags)
                        throws RemoteException {
                    if (code != 1 && code != 2) {
                        return super.onTransact(code, data, reply, flags);
                    }

                    data.enforceInterface("android.credentials.IGetCandidateCredentialsCallback");

                    if (code == 1) {
                        final Object response;
                        try {
                            @SuppressLint("PrivateApi")
                            final Class<?> klass =
                                    Class.forName(
                                            "android.credentials.GetCandidateCredentialsResponse");
                            final Parcelable.Creator<?> creator =
                                    (Parcelable.Creator<?>)
                                            klass.getDeclaredField("CREATOR").get(null);
                            assert creator != null;
                            response = data.readTypedObject(creator);
                        } catch (final ClassNotFoundException
                                | IllegalAccessException
                                | NoSuchFieldException e) {
                            throw new RuntimeException(e);
                        }
                        Log.i(LOG_TAG, response.toString());
                    } else {
                        final String errorType = data.readString();
                        final String message = data.readString();

                        Log.e(LOG_TAG, "Error (" + errorType + "): " + message);
                    }

                    data.enforceNoDataAvail();

                    return true;
                }
            };

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<ViewTestCtsActivity> mActivityRule =
            new ActivityTestRule<>(ViewTestCtsActivity.class);

    @Rule
    public final RequiredFeatureRule mRequiredFeatureRule =
            new RequiredFeatureRule(PackageManager.FEATURE_CREDENTIALS);

    @Rule
    public final DeviceConfigStateRequiredRule mDeviceConfigStateRequiredRule =
            new DeviceConfigStateRequiredRule(
                    DEVICE_CONFIG_ENABLE_CREDENTIAL_MANAGER,
                    DeviceConfig.NAMESPACE_CREDENTIAL,
                    mContext,
                    "true");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        Log.i(LOG_TAG, "Setting up CtsCredentialViewTest");
        assumeTrue("VERSION.SDK_INT=" + Build.VERSION.SDK_INT, BuildCompat.isAtLeastV());
        assumeFalse("Skipping test: Auto does not support CredentialManager yet",
                CtsCredentialManagerUtils.isAuto(mContext));
        mActivity = mActivityRule.getActivity();
    }
    @Test
    public void testClearCredentialManagerRequest() {
        View view = new View(mActivity);
        GetCredentialRequest request = new GetCredentialRequest.Builder(Bundle.EMPTY)
                .addCredentialOption(
                        new CredentialOption.Builder(
                                "TYPE_XYZ",
                                new Bundle(),
                                new Bundle())
                                .build()
                )
                .build();

        OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        // Do nothing
                    }
                };

        assertNull(view.getPendingCredentialRequest());
        assertNull(view.getPendingCredentialCallback());

        view.setPendingCredentialRequest(request, callback);

        assertEquals(view.getPendingCredentialRequest(), request);
        assertEquals(view.getPendingCredentialCallback(), callback);

        view.clearPendingCredentialRequest();

        assertNull(view.getPendingCredentialRequest());
        assertNull(view.getPendingCredentialCallback());
    }

    @Test
    public void testSetCredentialManagerRequest() {
        View view = new View(mActivity);
        GetCredentialRequest request = new GetCredentialRequest.Builder(Bundle.EMPTY)
                .addCredentialOption(
                        new CredentialOption.Builder(
                                "TYPE_XYZ",
                                new Bundle(),
                                new Bundle())
                                .build()
                )
                .build();

        OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(@NonNull GetCredentialResponse response) {
                        // Do nothing
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        // Do nothing
                    }
                };

        assertNull(view.getPendingCredentialRequest());
        assertNull(view.getPendingCredentialCallback());

        view.setPendingCredentialRequest(request, callback);

        assertEquals(view.getPendingCredentialRequest(), request);
        assertEquals(view.getPendingCredentialCallback(), callback);
    }

    @Test
    @AsbSecurityTest(cveBugId = 370477460)
    public void testGetCandidateCredentials_cannotBeInvokedOutsideCredentialAutofillService() {
        final IBinder binder;
        try {
            @SuppressLint("PrivateApi")
            final Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            final Method getService = serviceManager.getMethod("getService", String.class);
            binder = (IBinder) getService.invoke(null, "credential");
        } catch (final ClassNotFoundException
                | IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        final Bundle passkeyCandidateQueryData = new Bundle();
        passkeyCandidateQueryData.putString(
                "androidx.credentials.BUNDLE_KEY_REQUEST_JSON",
                "{'challenge': '', 'rpId': 'passkeys-codelab.glitch.me'}");

        final List<CredentialOption> options =
                List.of(
                        new CredentialOption.Builder(
                                        Credential.TYPE_PASSWORD_CREDENTIAL,
                                        Bundle.EMPTY,
                                        Bundle.EMPTY)
                                .build(),
                        new CredentialOption.Builder(
                                        "androidx.credentials.TYPE_PUBLIC_KEY_CREDENTIAL",
                                        Bundle.EMPTY,
                                        passkeyCandidateQueryData)
                                .build());
        final GetCredentialRequest credentialRequest =
                new GetCredentialRequest.Builder(Bundle.EMPTY)
                        .setCredentialOptions(options)
                        .build();

        try {
            getCandidateCredentials(
                    binder, credentialRequest, CALLBACK, null, "com.app.password.provider");
            fail("A SecurityException should have been thrown.");
        } catch (SecurityException e) {
            Log.i(LOG_TAG, "SecurityException is thrown. The feature is secure.");
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    private static void getCandidateCredentials(
            final IBinder /* android.credentials.ICredentialManager */ binder,
            final GetCredentialRequest request,
            final IBinder /* android.credentials.IGetCandidateCredentialsCallback */ callback,
            final IBinder clientCallback,
            final String callingPackage)
            throws RemoteException {
        final Parcel data = Parcel.obtain(binder);
        final Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken("android.credentials.ICredentialManager");
            data.writeTypedObject(request, 0);
            data.writeStrongBinder(callback);
            data.writeStrongBinder(clientCallback);
            data.writeString(callingPackage);
            binder.transact(1 + 3, data, reply, 0);
            reply.readException();
            reply.readStrongBinder();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
