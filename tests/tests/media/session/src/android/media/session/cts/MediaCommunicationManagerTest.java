/*
 * Copyright 2020 The Android Open Source Project
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
package android.media.session.cts;

import static android.Manifest.permission.MEDIA_CONTENT_CONTROL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.content.Context;
import android.media.MediaCommunicationManager;
import android.media.MediaSession2;
import android.media.Session2CommandGroup;
import android.media.Session2Token;
import android.os.Bundle;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.UserTest;

import com.google.common.base.Objects;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Tests {@link android.media.MediaCommunicationManager}. */
@RunWith(BedsteadJUnit4.class)
@SmallTest
@SdkSuppress(minSdkVersion = 31, codeName = "S")
public class MediaCommunicationManagerTest {
    private static final int TIMEOUT_MS = 5000;
    private static final int WAIT_MS = 500;

    private Context mContext;
    private MediaCommunicationManager mManager;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mManager = mContext.getSystemService(MediaCommunicationManager.class);
    }

    @After
    public void tearDown() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    @UserTest({UserType.INITIAL_USER, UserType.WORK_PROFILE, UserType.SECONDARY_USER})
    public void testGetVersion() {
        assertNotNull("Missing MediaCommunicationManager", mManager);
        assertTrue(mManager.getVersion() > 0);
    }

    @Test
    @UserTest({UserType.INITIAL_USER, UserType.WORK_PROFILE, UserType.SECONDARY_USER})
    public void testGetSession2Tokens() throws Exception {
        // registerSessionCallback requires permission MEDIA_CONTENT_CONTROL
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(MEDIA_CONTENT_CONTROL);

        Executor executor = Executors.newSingleThreadExecutor();

        assertNotNull("Missing MediaCommunicationManager", mManager);
        ManagerSessionCallback managerCallback = new ManagerSessionCallback();
        Session2Callback sessionCallback = new Session2Callback();

        mManager.registerSessionCallback(executor, managerCallback);

        try (MediaSession2 session = new MediaSession2.Builder(mContext)
                .setSessionCallback(executor, sessionCallback)
                .build()) {
            assertTrue(managerCallback.mCreatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            Session2Token currentToken = session.getToken();
            assertTrue(managerCallback.mCreatedTokens.contains(currentToken));
            assertTrue(mManager.getSession2Tokens().contains(currentToken));
        }

        mManager.unregisterSessionCallback(managerCallback);
    }

    @Test
    @UserTest({UserType.INITIAL_USER, UserType.WORK_PROFILE, UserType.SECONDARY_USER})
    public void registerSessionCallback_noMediaContentControlPermission_throwsSecurityException() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_EXTERNAL_STORAGE);
        Executor executor = Executors.newSingleThreadExecutor();

        assertNotNull("Missing MediaCommunicationManager", mManager);
        ManagerSessionCallback managerCallback = new ManagerSessionCallback();

        // Test permission enforced
        assertThrows(SecurityException.class,
                () -> mManager.registerSessionCallback(executor, managerCallback));
    }

    @Test
    @UserTest({UserType.INITIAL_USER, UserType.SECONDARY_USER}) // Requires full user.
    public void testManagerSessionCallback() throws Exception {
        // registerSessionCallback requires permission MEDIA_CONTENT_CONTROL
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(MEDIA_CONTENT_CONTROL);

        Executor executor = Executors.newSingleThreadExecutor();

        assertNotNull("Missing MediaCommunicationManager", mManager);
        ManagerSessionCallback managerCallback = new ManagerSessionCallback();
        Session2Callback sessionCallback = new Session2Callback();

        mManager.registerSessionCallback(executor, managerCallback);

        String uuid1 = UUID.randomUUID().toString();
        Bundle sessionExtras1 = new Bundle();
        sessionExtras1.putString("uuid", uuid1);
        try (MediaSession2 session =
                new MediaSession2.Builder(mContext)
                        .setId("session1")
                        .setExtras(sessionExtras1)
                        .setSessionCallback(executor, sessionCallback)
                        .build()) {
            assertTrue(managerCallback.mCreatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertTrue(managerCallback.mChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            Session2Token currentToken = session.getToken();
            assertUuidInExtrasOfTokens(
                    managerCallback.mCreatedTokens, uuid1, /* expectedNumberOfMatches= */ 1);
            assertUuidInExtrasOfListOfTokens(
                    managerCallback.mReportedTokens, uuid1, /* expectedNumberOfMatches= */ 1);

            // Create another session
            managerCallback.resetLatches();
            String uuid2 = UUID.randomUUID().toString();
            Bundle sessionExtras2 = new Bundle();
            sessionExtras2.putString("uuid", uuid2);
            MediaSession2 session2 =
                    new MediaSession2.Builder(mContext)
                            .setId("session2")
                            .setExtras(sessionExtras2)
                            .setSessionCallback(executor, sessionCallback)
                            .build();
            assertTrue(managerCallback.mCreatedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            assertTrue(managerCallback.mChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            Session2Token token2 = session2.getToken();
            assertUuidInExtrasOfTokens(
                    managerCallback.mCreatedTokens, uuid1, /* expectedNumberOfMatches= */ 1);
            assertUuidInExtrasOfTokens(
                    managerCallback.mCreatedTokens, uuid2, /* expectedNumberOfMatches= */ 1);
            assertUuidInExtrasOfListOfTokens(
                    managerCallback.mReportedTokens, uuid1, /* expectedNumberOfMatches= */ 2);
            assertUuidInExtrasOfListOfTokens(
                    managerCallback.mReportedTokens, uuid2, /* expectedNumberOfMatches= */ 1);

            // Test if onSession2TokensChanged are called if a session is closed
            managerCallback.resetLatches();
            session2.close();
            assertTrue(managerCallback.mChangedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));

            assertUuidInExtrasOfListOfTokens(
                    managerCallback.mReportedTokens, uuid1, /* expectedNumberOfMatches= */ 3);
            assertUuidInExtrasOfListOfTokens(
                    managerCallback.mReportedTokens, uuid2, /* expectedNumberOfMatches= */ 1);
        }

        mManager.unregisterSessionCallback(managerCallback);
    }

    private void assertUuidInExtrasOfTokens(
            List<Session2Token> tokens, String uuid, int expectedNumberOfMatches) {
        long matchCount =
                tokens.stream()
                        .filter(token -> Objects.equal(uuid, token.getExtras().getString("uuid")))
                        .count();
        assertEquals(matchCount, expectedNumberOfMatches);
    }

    private void assertUuidInExtrasOfListOfTokens(
            List<List<Session2Token>> listOfTokens, String uuid, int expectedNumberOfMatches) {
        long matchCount =
                listOfTokens.stream()
                        .flatMap(List::stream)
                        .filter(token -> Objects.equal(uuid, token.getExtras().getString("uuid")))
                        .count();
        assertEquals(expectedNumberOfMatches, matchCount);
    }

    private static class Session2Callback extends MediaSession2.SessionCallback {
        @Override
        public Session2CommandGroup onConnect(MediaSession2 session,
                MediaSession2.ControllerInfo controller) {
            return new Session2CommandGroup.Builder().build();
        }
    }

    private static class ManagerSessionCallback
            implements MediaCommunicationManager.SessionCallback {
        CountDownLatch mCreatedLatch;
        CountDownLatch mChangedLatch;
        final List<Session2Token> mCreatedTokens = new CopyOnWriteArrayList<>();
        List<List<Session2Token>> mReportedTokens = new CopyOnWriteArrayList<>();

        private ManagerSessionCallback() {
            mCreatedLatch = new CountDownLatch(1);
            mChangedLatch = new CountDownLatch(1);
        }

        @Override
        public void onSession2TokenCreated(Session2Token token) {
            mCreatedTokens.add(token);
            mCreatedLatch.countDown();
        }

        @Override
        public void onSession2TokensChanged(List<Session2Token> tokens) {
            mReportedTokens.add(tokens);
            mChangedLatch.countDown();
        }

        public void resetLatches() {
            mCreatedLatch = new CountDownLatch(1);
            mChangedLatch = new CountDownLatch(1);
        }
    }
}
