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

package com.android.xts.apimapper.asm

import java.io.BufferedInputStream
import java.util.HashMap
import java.util.zip.ZipFile
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

/** Load all classes from a .jar file. */
fun loadClassStructure(inJar: String): ClassNodes {
    val allClasses = ClassNodes(inJar)

    ZipFile(inJar).use { inZip ->
        val inEntries = inZip.entries()
        while (inEntries.hasMoreElements()) {
            val entry = inEntries.nextElement()
            if (!entry.name.endsWith(".class")) {
                continue
            }
            val bis = BufferedInputStream(inZip.getInputStream(entry))
            val cr = ClassReader(bis)
            val cn = ClassNode()
            cr.accept(
                cn,
                ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES
            )
            allClasses.addClass(cn)
        }
    }
    return allClasses
}

/** Store all classes loaded from a jar file. */
class ClassNodes(val sourceJarFile: String) {

    private val mClasses: MutableMap<String, ClassNode> = HashMap()

    /** Add a class node. */
    fun addClass(node: ClassNode) {
        mClasses.putIfAbsent(node.name.toJvmClassName(), node)
    }

    /** Find the ClassNode with the given class name. */
    fun findClass(className: String): ClassNode? {
        return mClasses[className.toJvmClassName()]
    }

    /** Check whether the given class exists or not. */
    fun hasClass(className: String): Boolean {
        return mClasses.containsKey(className.toJvmClassName())
    }

    /** Find the method member of a class. Returns null if it doesn't exist. */
    fun findMethod(className: String, methodName: String, desc: String): MethodNode? {
        return findClass(className)?.methods?.firstOrNull {
            it.name == methodName && it.desc == desc }?.let { node -> return node }
    }

    /** @return true if a class has a class initializer. */
    fun hasClassInitializer(className: String): Boolean {
        return findMethod(className, CLASS_INITIALIZER_NAME, CLASS_INITIALIZER_DESC) != null
    }
}
