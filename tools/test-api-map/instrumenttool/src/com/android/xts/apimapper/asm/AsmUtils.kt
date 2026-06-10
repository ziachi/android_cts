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

import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

/** Name of the class initializer method. */
const val CLASS_INITIALIZER_NAME = "<clinit>"

/** Descriptor of the class initializer methods. */
const val CLASS_INITIALIZER_DESC = "()V"

/** Descriptor of the constructor. */
const val CTOR_DESC = "()V"

/** Name of the constructor. */
const val CTOR_NAME = "<init>"

/** Convert the class name to jvm format. */
fun String.toJvmClassName(): String = replace('.', '/')

/** Convert the class name from jvm format. */
fun String.toHumanReadableClassName() = replace(Regex("[/$]"), ".")

/** Convert method params from jvm format. */
fun String.toHumanReadableDesc(): String {
    val params = ArrayList<String>()
    Type.getArgumentTypes(this).forEach {
        params.add(it.className.toHumanReadableClassName())
    }
    return params.joinToString(", ")
}

/** Extract the class name from a .class file. */
fun zipEntryNameToClassName(entryFilename: String): String? {
    val suffix = ".class"
    return if (entryFilename.endsWith(suffix)) {
        entryFilename.removeSuffix(suffix)
    } else {
        null
    }
}

/**
 * Write bytecode to "RETURN" that matches the method's return type, according to
 * [methodDescriptor].
 */
fun writeByteCodeToReturn(methodDescriptor: String, writer: MethodVisitor) {
    Type.getReturnType(methodDescriptor).let { type ->
        // See https://en.wikipedia.org/wiki/List_of_Java_bytecode_instructions
        when (type) {
            Type.VOID_TYPE -> writer.visitInsn(Opcodes.RETURN)
            Type.BOOLEAN_TYPE, Type.CHAR_TYPE, Type.BYTE_TYPE, Type.SHORT_TYPE, Type.INT_TYPE
            -> writer.visitInsn(Opcodes.IRETURN)
            Type.FLOAT_TYPE -> writer.visitInsn(Opcodes.FRETURN)
            Type.LONG_TYPE -> writer.visitInsn(Opcodes.LRETURN)
            Type.DOUBLE_TYPE -> writer.visitInsn(Opcodes.DRETURN)
            else -> writer.visitInsn(Opcodes.ARETURN)
        }
    }
}

/** Given a method descriptor, insert an [argType] as the first argument to it. */
fun prependArgTypeToMethodDescriptor(methodDescriptor: String, argType: Type): String {
    val returnType = Type.getReturnType(methodDescriptor)
    val argTypes = Type.getArgumentTypes(methodDescriptor).toMutableList()
    argTypes.add(0, argType)
    return Type.getMethodDescriptor(returnType, *argTypes.toTypedArray())
}

/**
 * Write bytecode to push all the method arguments to the stack. The number of arguments and their
 * type are taken from [methodDesc].
 */
fun writeByteCodeToPushArguments(
    methodDesc: String,
    mv: MethodVisitor,
    offset: Int,
) {
    var i = offset
    Type.getArgumentTypes(methodDesc).forEach { type ->
        // See https://en.wikipedia.org/wiki/List_of_Java_bytecode_instructions
        // Long and Double will consume two local variable spaces.
        when (type) {
            Type.VOID_TYPE -> throw AsmGenException("VOID_TYPE not expected")
            Type.BOOLEAN_TYPE, Type.CHAR_TYPE, Type.BYTE_TYPE, Type.SHORT_TYPE, Type.INT_TYPE
            -> mv.visitVarInsn(Opcodes.ILOAD, i)
            Type.FLOAT_TYPE -> mv.visitVarInsn(Opcodes.FLOAD, i)
            Type.LONG_TYPE -> mv.visitVarInsn(Opcodes.LLOAD, i++)
            Type.DOUBLE_TYPE -> mv.visitVarInsn(Opcodes.DLOAD, i++)
            else -> mv.visitVarInsn(Opcodes.ALOAD, i)
        }
        i++
    }
}
