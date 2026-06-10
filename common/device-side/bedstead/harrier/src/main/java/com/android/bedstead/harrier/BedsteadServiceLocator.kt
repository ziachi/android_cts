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

import android.util.Log
import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.android.bedstead.nene.utils.FailureDumper
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * Registrar of dependencies for use by Bedstead modules.
 *
 * Use of this service locator allows for the single [DeviceState] entry point to
 * bedstead while allowing modularisation and loose coupling.
 */
class BedsteadServiceLocator : DeviceStateComponent {

    private val dependenciesMap = mutableMapOf<KClass<*>, Any>()

    /**
     * Obtains the instance of the given [clazz]
     * if you have circular dependencies use [getValue]
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(clazz: KClass<T>): T {
        val existingInstance = dependenciesMap[clazz]
        return if (existingInstance != null) {
            existingInstance as T
        } else {
            createDependencyByReflection(clazz.java).also {
                dependenciesMap[clazz] = it
                if (it is DeviceStateComponent) {
                    Log.v(LOG_TAG, "prepareTestState (after creation): " + it.javaClass)
                    it.prepareTestState()
                }
            }
        }
    }

    /**
     * See [BedsteadServiceLocator.get]
     */
    inline fun <reified T : Any> get(): T = get(T::class)

    /**
     * Obtains the instance of the given type when needed by delegated properties
     * example: val instance: Type by locator
     */
    inline operator fun <reified T : Any> getValue(thisRef: Any, property: KProperty<*>): T {
        return get<T>()
    }

    /**
     * See [BedsteadServiceLocator.get]
     */
    fun <T : Any> get(clazz: Class<T>): T = get(clazz.kotlin)

    /**
     * Obtains the instance of the given [className]
     * @param className – the fully qualified name of the desired class.
     */
    @Suppress("UNCHECKED_CAST")
    @CanIgnoreReturnValue
    fun <T : Any> get(className: String): T {
        try {
            return (get(Class.forName(className))) as T
        } catch (e: ClassNotFoundException) {
            throw IllegalStateException(
                "Could not find dependency: $className. " +
                        "Make sure it is on the classpath and the appropriate module is loaded"
            )
        }
    }

    /**
     * Obtains the instance of the given [className] or null if it's not available
     * @param className – the fully qualified name of the desired class.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(className: String): T? {
        return try {
            (get(Class.forName(className))) as T
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    private fun <T : Any> createDependencyByReflection(clazz: Class<T>): T {
        return try {
            clazz.getDeclaredConstructor().newInstance()
        } catch (ignored: NoSuchMethodException) {
            try {
                clazz
                    .getDeclaredConstructor(BedsteadServiceLocator::class.java)
                    .newInstance(this)
            } catch (ignored: NoSuchMethodException) {
                throw IllegalStateException(
                    "$clazz doesn't have a constructor taking BedsteadServiceLocator as the only " +
                            "parameter or an empty constructor. " +
                            "Kotlin classes with init blocks can't be created by reflection. " +
                            "Provide the right constructor."
                )
            }
        }
    }

    /**
     * Get all loaded dependencies
     */
    fun getAllDependencies(): Collection<Any> {
        return dependenciesMap.values
    }

    /**
     * Get all loaded dependencies of type T
     */
    private inline fun <reified T : Any> getAllDependenciesOfType(): List<T> {
        return getAllDependencies().filterIsInstance<T>()
    }

    /**
     * Get all loaded FailureDumpers
     */
    fun getAllFailureDumpers(): List<FailureDumper> {
        return getAllDependenciesOfType<FailureDumper>()
    }

    /**
     * Get all loaded TestRuleExecutors
     */
    fun getAllTestRuleExecutors(): List<TestRuleExecutor> {
        return getAllDependenciesOfType<TestRuleExecutor>()
    }

    override fun teardownShareableState() {
        getAllDependenciesOfType<DeviceStateComponent>().forEach {
            Log.v(LOG_TAG, "teardownShareableState: " + it.javaClass)
            try {
                it.teardownShareableState()
            } catch (exception: Exception) {
                Log.e(
                    LOG_TAG,
                    "an exception occurred while executing " +
                            "teardownShareableState for ${it.javaClass}",
                    exception
                )
            }
        }
    }

    override fun teardownNonShareableState() {
        getAllDependenciesOfType<DeviceStateComponent>().forEach {
            Log.v(LOG_TAG, "teardownNonShareableState: " + it.javaClass)
            try {
                it.teardownNonShareableState()
            } catch (exception: Exception) {
                Log.e(
                    LOG_TAG,
                    "an exception occurred while executing " +
                            "teardownNonShareableState for ${it.javaClass}",
                    exception
                )
            }
        }
    }

    override fun prepareTestState() {
        getAllDependenciesOfType<DeviceStateComponent>().forEach {
            Log.v(LOG_TAG, "prepareTestState: " + it.javaClass)
            it.prepareTestState()
        }
    }

    /**
     * remove all dependencies in order to free some memory
     */
    fun clearDependencies() {
        dependenciesMap.clear()
    }

    companion object {
        private const val LOG_TAG = "BedsteadServiceLocator"
    }
}
