/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.content.pm.cts.shortcutmanager;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertDynamicShortcutCountExceeded;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.assertWith;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.list;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.retryUntil;
import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.setDefaultLauncher;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.CddTest;

@CddTest(requirement="3.8.1/C-4-1")
@SmallTest
public class ShortcutManagerMaxCountTest extends ShortcutManagerCtsTestsBase {
    /**
     * Basic tests: single app, single activity, no manifest shortcuts.
     */
    public void testNumDynamicShortcuts() {
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            assertTrue(getManager().setDynamicShortcuts(list(makeShortcut("s1"))));

            assertTrue(getManager().setDynamicShortcuts(makeShortcuts(makeIds("s", 1, mMaxShortcuts))));
            assertWith(getManager().getDynamicShortcuts())
                    .haveIds(makeIds("s", 1, mMaxShortcuts))
                    .areAllDynamic()
                    .areAllEnabled();

            assertTrue(getManager().setDynamicShortcuts(makeShortcuts(makeIds("sx", 1, mMaxShortcuts))));

            assertDynamicShortcutCountExceeded(() -> {
                getManager().setDynamicShortcuts(makeShortcuts(makeIds("sy", 1, mMaxShortcuts + 1)));
            });

            assertWith(getManager().getDynamicShortcuts())
                    .haveIds(makeIds("sx", 1, mMaxShortcuts))
                    .areAllDynamic()
                    .areAllEnabled();

            assertDynamicShortcutCountExceeded(() -> {
                getManager().addDynamicShortcuts(list(
                        makeShortcut("sy1")));
            });
            String[] dynamicIds = makeIds("sx", 1, mMaxShortcuts);
            assertWith(getManager().getDynamicShortcuts())
                    .haveIds(dynamicIds)
                    .areAllDynamic()
                    .areAllEnabled();
            getManager().removeDynamicShortcuts(list("sx" + mMaxShortcuts));
            assertTrue(getManager().addDynamicShortcuts(list(
                    makeShortcut("sy1"))));
            dynamicIds[dynamicIds.length - 1] = "sy1";

            assertWith(getManager().getDynamicShortcuts())
                    .haveIds(dynamicIds)
                    .areAllDynamic()
                    .areAllEnabled();

            getManager().removeAllDynamicShortcuts();

            assertTrue(getManager().setDynamicShortcuts(makeShortcuts(makeIds("s", 1, mMaxShortcuts))));
            assertWith(getManager().getDynamicShortcuts())
                    .haveIds(makeIds("s", 1, mMaxShortcuts))
                    .areAllDynamic()
                    .areAllEnabled();
        });
    }

    /**
     * Manifest shortcuts are included in the count too.
     */
    public void testWithManifest() throws Exception {
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            enableManifestActivity("Launcher_manifest_1", true);
            enableManifestActivity("Launcher_manifest_2", true);

            retryUntil(() -> getManager().getManifestShortcuts().size() == 3,
                    "Manifest shortcuts didn't show up");

        });

        runWithCallerWithStrictMode(mPackageContext1, () -> {
            assertWith(getManager().getManifestShortcuts())
                    .haveIds("ms1", "ms21", "ms22")
                    .areAllManifest()
                    .areAllEnabled()
                    .areAllNotPinned()

                    .selectByIds("ms1")
                    .forAllShortcuts(sa -> {
                        assertEquals(getActivity("Launcher_manifest_1"), sa.getActivity());
                    })

                    .revertToOriginalList()
                    .selectByIds("ms21", "ms22")
                    .forAllShortcuts(sa -> {
                        assertEquals(getActivity("Launcher_manifest_2"), sa.getActivity());
                    });

        });

        // Note since max counts is per activity, testNumDynamicShortcuts_single should just pass.
        testNumDynamicShortcuts();

        // Launcher_manifest_1 has one manifest, so can only add {@link #getMaxShortcutCountPerActivity()} - 1 dynamic shortcuts.
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            setTargetActivityOverride("Launcher_manifest_1");

            assertTrue(getManager().setDynamicShortcuts(makeShortcuts(makeIds("s", 1, mMaxShortcuts - 1))));
            assertWith(getManager().getDynamicShortcuts())
                    .selectByActivity(getActivity("Launcher_manifest_1"))
                    .haveIds(makeIds("s", 1, mMaxShortcuts - 1))
                    .areAllEnabled();

            assertDynamicShortcutCountExceeded(() -> getManager().setDynamicShortcuts(
                    makeShortcuts(makeIds("sx", 1, mMaxShortcuts))));
            // Not changed.
            assertWith(getManager().getDynamicShortcuts())
                    .selectByActivity(getActivity("Launcher_manifest_1"))
                    .haveIds(makeIds("s", 1, mMaxShortcuts - 1))
                    .areAllEnabled();
        });

        // Launcher_manifest_2 has two manifests, so can only add {@link #getMaxShortcutCountPerActivity()} - 2.
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            setTargetActivityOverride("Launcher_manifest_2");

            assertTrue(getManager().addDynamicShortcuts(makeShortcuts(makeIds("s", 1, mMaxShortcuts - 2))));
            assertWith(getManager().getDynamicShortcuts())
                    .selectByActivity(getActivity("Launcher_manifest_2"))
                    .haveIds(makeIds("s", 1, mMaxShortcuts - 2))
                    .areAllEnabled();

            assertDynamicShortcutCountExceeded(() -> getManager().addDynamicShortcuts(list(
                    makeShortcut("sx1")
            )));
            // Not added.
            assertWith(getManager().getDynamicShortcuts())
                    .selectByActivity(getActivity("Launcher_manifest_2"))
                    .haveIds(makeIds("s", 1, mMaxShortcuts - 2))
                    .areAllEnabled();
        });
    }

    public void testChangeActivity() {
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            setTargetActivityOverride("Launcher");
            assertTrue(getManager().setDynamicShortcuts(makeShortcuts(makeIds("s", 1, mMaxShortcuts))));
            assertWith(getManager().getDynamicShortcuts())
                    .selectByActivity(getActivity("Launcher"))
                    .haveIds(makeIds("s", 1, mMaxShortcuts))
                    .areAllDynamic()
                    .areAllEnabled();

            setTargetActivityOverride("Launcher2");
            assertTrue(getManager().addDynamicShortcuts(makeShortcuts(makeIds("sb", 1, mMaxShortcuts))));

            assertWith(getManager().getDynamicShortcuts())
                    .selectByActivity(getActivity("Launcher"))
                    .haveIds(makeIds("s", 1, mMaxShortcuts))
                    .areAllDynamic()
                    .areAllEnabled()

                    .revertToOriginalList()
                    .selectByActivity(getActivity("Launcher2"))
                    .haveIds(makeIds("sb", 1, mMaxShortcuts))
                    .areAllDynamic()
                    .areAllEnabled();

            // Moving one from L1 to L2 is not allowed.
            assertDynamicShortcutCountExceeded(() -> getManager().updateShortcuts(list(
                    makeShortcut("s1", getActivity("Launcher2"))
            )));

            String[] launcherIds = makeIds("s", 1, mMaxShortcuts);
            String[] launcher2Ids = makeIds("sb", 1, mMaxShortcuts);

            assertWith(getManager().getDynamicShortcuts())
                    .selectByActivity(getActivity("Launcher"))
                    .haveIds(launcherIds)
                    .areAllDynamic()
                    .areAllEnabled()

                    .revertToOriginalList()
                    .selectByActivity(getActivity("Launcher2"))
                    .haveIds(launcher2Ids)
                    .areAllDynamic()
                    .areAllEnabled();

            // But swapping shortcuts will work.
            assertTrue(getManager().updateShortcuts(list(
                    makeShortcut("s1", getActivity("Launcher2")),
                    makeShortcut("sb1", getActivity("Launcher"))
            )));
            launcherIds[0] = "sb1";
            launcher2Ids[0] = "s1";

            assertWith(getManager().getDynamicShortcuts())
                    .selectByActivity(getActivity("Launcher"))
                    .haveIds(launcherIds)
                    .areAllDynamic()
                    .areAllEnabled()

                    .revertToOriginalList()
                    .selectByActivity(getActivity("Launcher2"))
                    .haveIds(launcher2Ids)
                    .areAllDynamic()
                    .areAllEnabled();
        });
    }

    public void testWithPinned() {
        runWithCallerWithStrictMode(mPackageContext1, () -> {
            assertTrue(getManager().setDynamicShortcuts(makeShortcuts(makeIds("s", 1, mMaxShortcuts))));
        });

        setDefaultLauncher(getInstrumentation(), mLauncherContext1);

        runWithCallerWithStrictMode(mLauncherContext1, () -> {
            getLauncherApps().pinShortcuts(mPackageContext1.getPackageName(),
                    list(makeIds("s", 1, mMaxShortcuts)),
                    getUserHandle());
        });

        runWithCallerWithStrictMode(mPackageContext1, () -> {
            assertTrue(getManager().setDynamicShortcuts(makeShortcuts(makeIds("sb", 1, mMaxShortcuts))));

            assertWith(getManager().getDynamicShortcuts())
                    .haveIds(makeIds("sb", 1, mMaxShortcuts))
                    .areAllEnabled()
                    .areAllNotPinned();

            assertWith(getManager().getPinnedShortcuts())
                    .haveIds(makeIds("s", 1, mMaxShortcuts))
                    .areAllEnabled()
                    .areAllNotDynamic();
        });
    }
}
