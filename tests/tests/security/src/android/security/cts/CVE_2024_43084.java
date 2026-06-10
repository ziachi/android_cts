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

import static android.Manifest.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG;
import static android.Manifest.permission.WRITE_DEVICE_CONFIG;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Notification;
import android.app.Notification.CallStyle;
import android.app.Notification.MessagingStyle;
import android.app.Notification.MessagingStyle.Message;
import android.app.Person;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.DeviceConfig;
import android.widget.RemoteViews;
import android.widget.RemoteViews.RemoteCollectionItems;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_43084 extends StsExtraBusinessLogicTestCase {
    final String mValiduriString = "content://valid_uri_";
    final String mMaliciousUriString = "content://malicious_uri_";

    @Test
    @AsbSecurityTest(cveBugId = 281044385)
    public void testCVE_2024_43084() {
        try {
            // Create an instance of Bundle and configure it to reproduce the vulnerability.
            final Bundle testBundle = new Bundle();

            // Create a list of 'Person' with malicious URI set to 'mUri'. Further append
            // the list to the 'testBundle' with key 'Notification.EXTRA_PEOPLE_LIST'.
            // With fix, the malicious URI(content://malicious_uri_extraPeopleList) is detected.
            final ArrayList<Person> listOfPerson = new ArrayList<Person>();
            listOfPerson.add(getPersonWithMaliciousConfig("extraPeopleList"));
            testBundle.putParcelableArrayList(Notification.EXTRA_PEOPLE_LIST, listOfPerson);

            // Create instances of 'Person' with malicious URIs to set to 'mUri', and append it to
            // the 'testBundle' with key 'Notification.EXTRA_MESSAGING_PERSON' and
            // 'Notification.EXTRA_CALL_PERSON'.
            // With fix, the malicious URI(content://malicious_uri_messagingUser) and
            // URI(content://malicious_uri_callPerson) is detected.
            testBundle.putParcelable(
                    Notification.EXTRA_MESSAGING_PERSON,
                    getPersonWithMaliciousConfig("messagingUser"));
            testBundle.putParcelable(
                    Notification.EXTRA_CALL_PERSON, getPersonWithMaliciousConfig("callPerson"));

            // Create an instance of 'Message', using an instance of 'Person' having a malicious URI
            // set to 'mUri'. Further, add it into an array of 'Bundle' and append it to
            // 'testBundle' with key 'Notification.EXTRA_MESSAGES' and
            // 'Notification.EXTRA_HISTORIC_MESSAGES'.
            // With fix, the malicious URI(content://malicious_uri_messages) and
            // URI(content://malicious_uri_historic) are detected.
            testBundle.putParcelableArray(
                    Notification.EXTRA_MESSAGES,
                    new Bundle[] {
                        new Message(
                                        "b_281044385_text" /* text */,
                                        0 /* timestamp */,
                                        getPersonWithMaliciousConfig("messages"))
                                .setData("image/png" /* dataMimeType */, Uri.EMPTY)
                                .toBundle()
                    });
            testBundle.putParcelableArray(
                    Notification.EXTRA_HISTORIC_MESSAGES,
                    new Bundle[] {
                        new Message(
                                        "b_281044385_text" /* text */,
                                        0 /* timestamp */,
                                        getPersonWithMaliciousConfig("historic"))
                                .setData("image/png" /* dataMimeType */, Uri.EMPTY)
                                .toBundle()
                    });

            // Create an instance of 'RemoteView' and set it's 'mAction' field.
            // Override the 'visitUris' to verify, whether the 'tickerView.visitUris()'
            // was invoked.
            final CompletableFuture<Boolean> visitUrisWasTriggeredOnTickerView =
                    new CompletableFuture<Boolean>();
            final String pkgName = getApplicationContext().getPackageName();
            final RemoteViews tickerView =
                    new RemoteViews(pkgName, 0 /* layout */) {
                        @Override
                        public void visitUris(Consumer<Uri> visitor) {
                            visitUrisWasTriggeredOnTickerView.complete(true);
                            super.visitUris(visitor);
                        }
                    };
            getDeclaredField(RemoteViews.class, "mAction").set(tickerView, new ArrayList<>());

            // Create a malicious 'RemoteView' and overload the 'visitUris' to verify, whether
            // the malicious 'RemoteView' was visited.
            final CompletableFuture<Boolean> visitUrisWasTriggeredOnMaliciousRemoteView =
                    new CompletableFuture<Boolean>();
            final RemoteViews maliciousRemoteView =
                    new RemoteViews(pkgName, 0 /* layout */) {
                        @Override
                        public void visitUris(Consumer<Uri> visitor) {
                            visitUrisWasTriggeredOnMaliciousRemoteView.complete(true);
                        }
                    };

            // Create an instance of Notification
            final Notification notification = new Notification();

            // Create an instance of 'RemoteCollectionItems'.
            final RemoteCollectionItems remoteCollectionItems =
                    new RemoteCollectionItems.Builder().addItem(0, maliciousRemoteView).build();
            tickerView.setRemoteAdapter(0, remoteCollectionItems);
            getDeclaredField(Notification.class, "tickerView").set(notification, tickerView);

            // Create a 'Consumer' which verifies the URI.
            // With fix, both 'content://valid_uri_<data>' and 'content://malicious_uri_<data>'
            // is expected to be fetched.
            // Without fix, only 'content://valid_uri_<data>' is read and
            // 'content://malicious_uri_<data>' does not gets fetched.
            final HashSet<String> validUriSet = new HashSet<String>();
            final HashSet<String> maliciousUriSet = new HashSet<String>();
            final Consumer<Uri> testConsumer =
                    (uri) -> {
                        if (uri != null) {
                            final String uriString = uri.toString();
                            if (uriString.startsWith(mValiduriString)) {
                                validUriSet.add(uriString.replace(mValiduriString, ""));
                            }
                            if (uriString.startsWith(mMaliciousUriString)) {
                                maliciousUriSet.add(uriString.replace(mMaliciousUriString, ""));
                            }
                        }
                    };

            // Fetch the vulnerable method 'Notification.visitUris()' using reflection.
            final Method vulnerableMethod =
                    Notification.class.getDeclaredMethod("visitUris", Consumer.class);
            vulnerableMethod.setAccessible(true);

            // For Android VIC and above.
            // If 'systemui_is_cached' is 'false', set 'DeviceConfig' with key 'NAMESPACE_SYSTEMUI'
            // and value 'true'.
            // Else, explicitly set 'visitPersonUri' as 'true'.
            if (Build.VERSION.SDK_INT >= 35 /* VIC */) {
                // Set 'DeviceConfig' with key 'NAMESPACE_SYSTEMUI' and value 'true'.
                final Class flagClass = Class.forName("android.app.Flags");
                final Class featureFlagsImplClass = Class.forName("android.app.FeatureFlagsImpl");
                if (!getDeclaredField(featureFlagsImplClass, "systemui_is_cached")
                        .getBoolean(null)) {
                    SystemUtil.runWithShellPermissionIdentity(
                            () ->
                                    DeviceConfig.setProperty(
                                            DeviceConfig.NAMESPACE_SYSTEMUI,
                                            (String)
                                                    getDeclaredField(
                                                                    flagClass,
                                                                    "FLAG_VISIT_PERSON_URI")
                                                            .get(null),
                                            "true" /* value */,
                                            true /* makeDefault */),
                            WRITE_DEVICE_CONFIG, WRITE_ALLOWLISTED_DEVICE_CONFIG);
                } else {
                    // Set 'visitPersonUri' as 'true'.
                    getDeclaredField(featureFlagsImplClass, "visitPersonUri").set(null, true);
                }

                // Invoke 'Flags.visitPersonUri()' to verify, if it returns 'true'.
                final Method visitPersonUri = flagClass.getDeclaredMethod("visitPersonUri");
                assume().withMessage("The 'Flags.visitPersonUri()' was expected to return 'true'")
                        .that((boolean) visitPersonUri.invoke(null))
                        .isTrue();
            }

            // Append a key 'Notification.EXTRA_TEMPLATE' with 'MessagingStyle' to bypass
            // 'isStyle()' check for 'Notification.EXTRA_MESSAGES' and
            // 'Notification.EXTRA_HISTORIC_MESSAGES'.
            // Assign the configured 'testBundle' to 'Notification.extras' and invoke the
            // vulnerable method 'Notification.visitUris()' to reproduce the vulnerability.
            testBundle.putString(Notification.EXTRA_TEMPLATE, MessagingStyle.class.getName());
            notification.extras = testBundle;
            vulnerableMethod.invoke(notification, testConsumer);

            // Append a key 'Notification.EXTRA_TEMPLATE' with 'CallStyle' to bypass
            // 'isStyle()' check for 'Notification.EXTRA_CALL_PERSON'.
            // Assign the configured 'testBundle' to 'Notification.extras'.
            testBundle.putString(Notification.EXTRA_TEMPLATE, CallStyle.class.getName());
            notification.extras = testBundle;
            vulnerableMethod.invoke(notification, testConsumer);

            // Verify whether the malicious 'RemoteView' was visited.
            final boolean maliciousRemoteViewVisited =
                    visitUrisWasTriggeredOnMaliciousRemoteView
                            .getNow(false /* valueIfAbsent */)
                            .equals(
                                    visitUrisWasTriggeredOnTickerView.getNow(
                                            false /* valueIfAbsent */));

            // Verify whether the elements present in 'validUriSet' are also present
            // in 'maliciousUriSet'.
            final String maliciousUriNotVisited =
                    validUriSet.stream()
                            .filter(element -> !maliciousUriSet.contains(element))
                            .collect(Collectors.joining(", "));
            assertWithMessage(
                            String.format(
                                    "Device is vulnerable to b/281044385!! Malicious URI was not"
                                            + " visited : [%s]",
                                    maliciousUriNotVisited))
                    .that(maliciousUriNotVisited.isEmpty() && maliciousRemoteViewVisited)
                    .isTrue();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private final Person getPersonWithMaliciousConfig(String uriString) {
        return new Person.Builder()
                .setUri(mMaliciousUriString.concat(uriString))
                .setIcon(Icon.createWithContentUri(Uri.parse(mValiduriString.concat(uriString))))
                .build();
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
}
