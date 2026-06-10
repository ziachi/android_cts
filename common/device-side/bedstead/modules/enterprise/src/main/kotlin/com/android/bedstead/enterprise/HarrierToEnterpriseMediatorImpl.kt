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
package com.android.bedstead.enterprise

import com.android.bedstead.harrier.HarrierToEnterpriseMediator
import java.util.stream.Stream
import org.junit.runners.model.FrameworkMethod

/**
 * Allows to execute Enterprise methods from Harrier when the module is loaded
 */
@Suppress("unused")
class HarrierToEnterpriseMediatorImpl : HarrierToEnterpriseMediator {

    override fun generatePolicyArgumentTests(
        frameworkMethod: FrameworkMethod,
        expandedMethods: Stream<FrameworkMethod>
    ): Stream<FrameworkMethod> {
        return PolicyArgumentTestsGenerator.generate(frameworkMethod, expandedMethods)
    }

    override fun parseEnterpriseAnnotations(annotations: List<Annotation>) {
        EnterpriseAnnotationsParser.parse(annotations)
    }
}
