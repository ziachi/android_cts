/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.bedstead.harrier;

import java.lang.annotation.Annotation;
import org.junit.runner.Description;

/**
 * Interface used to register a new class which can execute an external test rule.
 *
 * <p>This can be used to add additional harrier-compatible rules without modifying harrier.
 */
public interface TestRuleExecutor {

    /**
     * Applies external test rule, for e.g. this can be used to execute the OnboardingTestsRule,
     * that is part of the onboarding module.
     *
     * <p>This should take care of recording any state necessary to correctly restore state after
     * the test.
     */
    void applyTestRule(HarrierRule deviceState, Annotation annotation, Description description);

    /**
     * Invokes teardown of this test rule.
     */
    void teardown(HarrierRule deviceState);
}
