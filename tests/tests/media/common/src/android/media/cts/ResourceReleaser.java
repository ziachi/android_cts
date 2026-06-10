/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.media.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.rules.ExternalResource;
import org.junit.rules.TestRule;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * {@link TestRule} for releasing resources once a test ends.
 *
 * <p><b>Minimal Example:</b>
 *
 * <pre>{@code
 * public class FooTest {
 *     @Rule public final ResourceReleaser mResourceReleaser = new ResourceReleaser();
 *
 *     @Test
 *     public void registerFoo_doesSomething() {
 *         Foo foo = new Foo();
 *         foo.register();
 *         mResourceReleaser.add(foo::unregister);
 *
 *         // Do assertions here.
 *     }
 * }
 *
 * }</pre>
 */
public final class ResourceReleaser extends ExternalResource {

    /** Equivalent to {@link #ResourceReleaser(boolean) new ResourceReleaser(false)}. */
    public ResourceReleaser() {
        this(false);
    }

    /**
     * Creates a new instance.
     *
     * @param useStack Whether to invoke {@link #add added runnables} in the inverse order of
     *     addition. If false, runnables will be invoked in the same order in which they were added.
     */
    public ResourceReleaser(boolean useStack) {
        mUseStack = useStack;
    }

    private final ArrayDeque<Runnable> mPendingRunnables = new ArrayDeque<>();
    private final boolean mUseStack;

    /**
     * Adds a {@link Runnable} for execution after the end of the test run, regardless of the test
     * result.
     */
    public void add(Runnable runnable) {
        mPendingRunnables.add(runnable);
    }

    @Override
    public void after() {
        ArrayList<Throwable> throwables = new ArrayList<>();
        while (!mPendingRunnables.isEmpty()) {
            Runnable runnable =
                    mUseStack ? mPendingRunnables.removeLast() : mPendingRunnables.removeFirst();
            try {
                runnable.run();
            } catch (Throwable e) {
                throwables.add(e);
            }
        }
        assertWithMessage("Ran into exceptions while releasing resources.")
                .that(throwables)
                .isEmpty();
    }
}
