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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.storage.VolumeInfo;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.DocumentsContract;
import android.util.ArrayMap;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_43093 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 341680936)
    public void testPocCVE_2024_43093() {
        try {
            // Get external storage provider package name
            final Context context = getApplicationContext();
            final String externalStorageProviderPackageName =
                    context.getPackageManager()
                            .queryBroadcastReceivers(
                                    new Intent(VolumeInfo.ACTION_VOLUME_STATE_CHANGED),
                                    PackageManager.MATCH_ALL /* flags */)
                            .get(0 /* index */)
                            .getComponentInfo()
                            .packageName;

            // Fetch the class 'ExternalStorageProvider'
            final Class externalStorageProviderClass =
                    context.createPackageContext(
                                    externalStorageProviderPackageName,
                                    Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE)
                            .getClassLoader()
                            .loadClass("com.android.externalstorage.ExternalStorageProvider");

            // Fetch the constructor of 'ExternalStorageProvider'
            final Constructor externalStorageProviderClassConstructor =
                    externalStorageProviderClass.getDeclaredConstructor();
            externalStorageProviderClassConstructor.setAccessible(true);

            // Create an instance of 'ExternalStorageProvider' which is required to invoke the
            // vulnerable method
            final Object externalStorageProviderInstance =
                    externalStorageProviderClassConstructor.newInstance();

            // Fetch the 'mRoots' field from the external storage provider
            Field rootsField = null;
            for (Field field : externalStorageProviderClass.getDeclaredFields()) {
                if (field.getName().equals("mRoots")) {
                    rootsField = field;
                    rootsField.setAccessible(true);
                    break;
                }
            }
            assume().withMessage(
                            "Unable to fetch the 'mRoots' field from the external storage provider")
                    .that(rootsField)
                    .isNotNull();

            // Fetch the value of the 'mRoots' field, cast it to the appropriate type and
            // add a null entry for 'DocumentsContract.EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID'
            // to reach the vulnerable code
            final ArrayMap<String, Object> rootsMap =
                    (ArrayMap<String, Object>) rootsField.get(externalStorageProviderInstance);
            rootsMap.put(
                    DocumentsContract.EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID,
                    null /* RootInfo */);

            // Fetch the vulnerable method 'shouldHideDocument()' from the external storage provider
            Method shouldHideDocumentMethod = null;
            for (Method method : externalStorageProviderClass.getDeclaredMethods()) {
                if (method.getName().equals("shouldHideDocument")) {
                    shouldHideDocumentMethod = method;
                    shouldHideDocumentMethod.setAccessible(true);
                    break;
                }
            }
            assume().withMessage(
                            "Unable to fetch the vulnerable method 'shouldHideDocument()' from"
                                    + " external storage provider")
                    .that(shouldHideDocumentMethod)
                    .isNotNull();

            // Without Fix, 'shouldHideDocument()' returns false, as restricted android subtree
            // pattern does not match the document path
            // With fix, 'shouldHideDocument()' returns true, as a 'FileNotFoundException' is raised
            // when trying to access the document path and is handled internally
            final String invalidDocumentId =
                    String.format(
                            "%s:%s",
                            DocumentsContract.EXTERNAL_STORAGE_PRIMARY_EMULATED_ROOT_ID,
                            context.getPackageName());
            final boolean shouldHideDocument =
                    (boolean)
                            shouldHideDocumentMethod.invoke(
                                    externalStorageProviderInstance, invalidDocumentId);
            assertWithMessage(
                            "Device is vulnerable to b/341680936 !! Apps can request access to"
                                    + " directories that should be hidden due to improper URI"
                                    + " validation")
                    .that(shouldHideDocument)
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
