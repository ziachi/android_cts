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
package com.android.bedstead.harrier

import com.android.bedstead.harrier.components.UserTypeResolver
import com.android.bedstead.nene.utils.Assert.assertThrows
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("UNUSED_PARAMETER")
@RunWith(JUnit4::class)
class BedsteadServiceLocatorTest {

    @Test
    fun emptyLocator_getDependencyThrowsException() {
        class ClassNotSupportedByBedsteadServiceLocatorReflection(parameter: String)

        val locator = BedsteadServiceLocator()

        assertThrows(IllegalStateException::class.java) {
            locator.get(ClassNotSupportedByBedsteadServiceLocatorReflection::class)
        }
        assertThrows(IllegalStateException::class.java) {
            locator.get<ClassNotSupportedByBedsteadServiceLocatorReflection>()
        }
        assertThrows(IllegalStateException::class.java) {
            locator.get(ClassNotSupportedByBedsteadServiceLocatorReflection::class.java)
        }
    }

    @Test
    fun callGetFewTimes_dependencyInstanceIsReused() {
        class ExampleClass

        val locator = BedsteadServiceLocator()

        val result1 = locator.get(ExampleClass::class)
        val result2 = locator.get(ExampleClass::class)
        val result3 = locator.get(ExampleClass::class.java)
        val result4 = locator.get<ExampleClass>()

        assertThat(result1).isSameInstanceAs(result2)
        assertThat(result1).isSameInstanceAs(result3)
        assertThat(result1).isSameInstanceAs(result4)
        assertThat(locator.getAllDependencies().size).isEqualTo(1)
    }

    @Test
    fun getClassWithDependencies_allClassesAreCreatedByReflection() {
        class ClassWithoutParameters
        class ClassWithLocatorParameter(locator: BedsteadServiceLocator)
        class ExampleClass(locator: BedsteadServiceLocator) {
            val classWithoutParameters: ClassWithoutParameters = locator.get()
            val classWithLocatorParameter: ClassWithLocatorParameter = locator.get()
        }

        val locator = BedsteadServiceLocator()
        val exampleClass: ExampleClass = locator.get()

        assertThat(exampleClass.classWithoutParameters).isNotNull()
        assertThat(exampleClass.classWithLocatorParameter).isNotNull()
        assertThat(locator.getAllDependencies().size).isEqualTo(3)
    }

    private class FirstClass(locator: BedsteadServiceLocator) {
        val secondClass: SecondClass by locator
    }

    private class SecondClass(locator: BedsteadServiceLocator) {
        val firstClass: FirstClass by locator
    }

    @Test
    fun circularDependencies_dependencyIsCreatedByDelegate() {
        val locator = BedsteadServiceLocator()

        val firstClass: FirstClass = locator.get()
        val secondClass: SecondClass = locator.get()

        assertThat(firstClass.secondClass).isNotNull()
        assertThat(secondClass.firstClass).isNotNull()
    }

    @Test
    fun getClassByName_dependencyIsCreated() {
        val locator = BedsteadServiceLocator()

        val instance: UserTypeResolver = locator.get(UserTypeResolver::class.qualifiedName!!)

        assertThat(instance).isNotNull()
    }

    @Test
    fun getNonExistingClass_throwsException() {
        val locator = BedsteadServiceLocator()

        assertThrows {
            locator.get("non.existing.class")
        }
    }

    @Test
    fun useGetOrNullForNonExistingClass_valueIsNull() {
        val locator = BedsteadServiceLocator()

        val instance: Any? = locator.getOrNull("non.existing.class")

        assertThat(instance).isNull()
    }
}
