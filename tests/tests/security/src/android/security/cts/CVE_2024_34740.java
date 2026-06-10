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

import static android.os.Build.VERSION.SDK_INT;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_34740 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 307288067)
    public void testPocCVE_2024_34740() {
        try {
            // Load the 'BinaryXmlSerializer' class through reflection.
            final ClassLoader classLoader = getApplicationContext().getClassLoader();
            final String binaryXmlSerializerClassPkg =
                    (SDK_INT > 33 /* TIRAMISU */)
                            ? "com.android.modules.utils"
                            : "com.android.internal.util";
            final Class binaryXmlSerializer =
                    classLoader.loadClass(
                            String.format("%s.BinaryXmlSerializer", binaryXmlSerializerClassPkg));
            assume().withMessage("'BinaryXmlSerializer' class failed to load")
                    .that(binaryXmlSerializer)
                    .isNotNull();

            // Create an instance of 'BinaryXmlSerializer'
            final Object binaryXmlSerializerObject =
                    binaryXmlSerializer.getDeclaredConstructor().newInstance();

            // Create and set the parameters to invoke the vulnerable methods reliably.
            // Set the byte array length to '2 * MAX_UNSIGNED_SHORT' to detect the
            // vulnerability reliably.
            final String name = "cve_2024_34740_name";
            final Field maxUnsignedShortField =
                    classLoader
                            .loadClass(
                                    String.format("%s.FastDataInput", binaryXmlSerializerClassPkg))
                            .getDeclaredField("MAX_UNSIGNED_SHORT");
            maxUnsignedShortField.setAccessible(true);
            final int maxUnsignedShort = (int) maxUnsignedShortField.get(null);
            final byte[] value =
                    new byte[maxUnsignedShort * 2 /* Two times of 'MAX_UNSIGNED_SHORT' */];

            // Invoke the 'setOutput()' of 'BinaryXmlSerializer' to initialize 'mOut'.
            final Method setOutput =
                    binaryXmlSerializer.getDeclaredMethod(
                            "setOutput", OutputStream.class, String.class);
            setOutput.setAccessible(true);

            // Lambda method to invoke the vulnerable method.
            // Without fix, the data is added to 'mOut' of 'BinaryXmlSerializer' and the size of
            // the 'ByteArrayOutputStream' increases.
            // With fix, a check on data length to avoids data overflow, and an 'IOException'
            // is thrown.
            final Function<Method, Boolean> isMethodVulnerable =
                    (vulnerableMethodName) -> {
                        try (ByteArrayOutputStream outputForBinaryXmlSerializer =
                                new ByteArrayOutputStream()) {
                            // Invoke 'setOutput()' of 'BinaryXmlSerializer' to initialize 'mOut'.
                            setOutput.invoke(
                                    binaryXmlSerializerObject,
                                    outputForBinaryXmlSerializer,
                                    "utf-8" /* encoding */);

                            // Invoke the vulnerable method
                            int initialLengthOfStream = outputForBinaryXmlSerializer.size();
                            vulnerableMethodName.invoke(
                                    binaryXmlSerializerObject, null /* namespace */, name, value);

                            // Without fix, due to a missing check to avoid data overflow, the data
                            // is added to 'mOut' of 'BinaryXmlSerializer' and the size increases.
                            if ((outputForBinaryXmlSerializer.size() - initialLengthOfStream)
                                    > maxUnsignedShort) {
                                return true;
                            }
                        } catch (Exception exception) {
                            if (exception
                                    .getCause()
                                    .getMessage()
                                    .contains("exceeds maximum allowed size")) {
                                // Ignore as with fix, an 'IOException' is thrown with unique
                                // message '<..>exceeds maximum allowed size<...>'.
                                return false;
                            } else {
                                throw new IllegalStateException(exception);
                            }
                        }
                        return false;
                    };

            // Invoke the vulnerable method 'attributeBytesHex()'.
            final List<String> vulnerableMethods = new ArrayList<String>();
            final Method attributeBytesHexMethod =
                    binaryXmlSerializer.getDeclaredMethod(
                            "attributeBytesHex", String.class, String.class, byte[].class);
            attributeBytesHexMethod.setAccessible(true);
            if (isMethodVulnerable.apply(attributeBytesHexMethod)) {
                vulnerableMethods.add("BinaryXmlSerializer::attributeBytesHex");
            }

            // Invoke the vulnerable method 'attributeBytesBase64()'.
            final Method attributeBytesBase64Method =
                    binaryXmlSerializer.getDeclaredMethod(
                            "attributeBytesBase64", String.class, String.class, byte[].class);
            attributeBytesBase64Method.setAccessible(true);
            if (isMethodVulnerable.apply(attributeBytesBase64Method)) {
                vulnerableMethods.add("BinaryXmlSerializer::attributeBytesBase64");
            }

            // In-case of without fix, do assert fail and add the vulnerable class names
            // in the message.
            assertWithMessage(
                            "Device is vulnerable to b/307288067. Fix is not present in "
                                    + String.join(
                                            " and ", vulnerableMethods.toArray(new String[0])))
                    .that(vulnerableMethods)
                    .isEmpty();
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
