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

package com.android.xts.apimapper.adapter

import com.android.xts.apimapper.asm.ClassNodes
import com.android.xts.apimapper.asm.prependArgTypeToMethodDescriptor
import com.android.xts.apimapper.asm.toHumanReadableClassName
import com.android.xts.apimapper.asm.toHumanReadableDesc
import com.android.xts.apimapper.asm.writeByteCodeToPushArguments
import com.android.xts.apimapper.asm.writeByteCodeToReturn
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ACC_PRIVATE
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ACONST_NULL
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Type

const val OPCODE_VERSION = Opcodes.ASM9
const val METHOD_CLASS_NAME = "com/android/xts/apimapper/helper/DeviceMethodCallHook"
const val HOOK_METHOD_DESC = "(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;" +
        "Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V"
const val HOOK_METHOD_NAME = "onBeforeCall"

private const val APIMAPPER_BRIDGE_PREFIX = "_apimapper_bridge"

/**
 * Inject a hook to log each potential API call.
 */
class MethodCallHookingAdapter(
    nextVisitor: ClassVisitor,
    val settings: HookSettings,
    val classes: ClassNodes,
) : ClassVisitor(OPCODE_VERSION, nextVisitor) {

    lateinit var className: String
    private var nextBridgeMethod = 0

    // Map for reusing bridge methods.
    private val bridgeMethods = mutableMapOf<BridgeKey, String>()

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        super.visit(version, access, name, signature, superName, interfaces)
        className = name
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?,
    ): MethodVisitor {
        return MethodCallHookingMethodVisitor(
            name,
            descriptor,
            super.visitMethod(access, name, descriptor, signature, exceptions)
        )
    }

    private inner class MethodCallHookingMethodVisitor(
        val methodName: String,
        val methodDescription: String,
        val nextVisitor: MethodVisitor,
    ) : MethodVisitor(OPCODE_VERSION, nextVisitor) {

        override fun visitCode() {
            super.visitCode()
            logCall(
                nextVisitor,
                null,
                null,
                className,
                methodName,
                methodDescription,
                null
            )
        }

        override fun visitMethodInsn(
            opcode: Int,
            owner: String,
            name: String,
            desc: String,
            isInterface: Boolean,
        ) {
            if (!methodName.startsWith(APIMAPPER_BRIDGE_PREFIX)) {
                if (settings.shouldInjectHook(
                        classes,
                        className,
                        opcode,
                        owner,
                        name,
                        desc
                    )) {
                    hookMethod(
                        nextVisitor,
                        methodName,
                        opcode,
                        owner,
                        name,
                        desc,
                        isInterface
                    )
                    return
                }
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface)
        }
    }

    /** Log the call. */
    private fun logCall(
        visitor: MethodVisitor,
        callerMethodName: String?,
        calleeMethodOpcode: Int?,
        calleeMethodOwner: String,
        calleeMethodName: String,
        calleeMethodDesc: String,
        receiverIndex: Int?
    ) {
        if (callerMethodName == null) {
            visitor.visitInsn(ACONST_NULL)
            visitor.visitInsn(ACONST_NULL)
            visitor.visitInsn(ACONST_NULL)
        } else {
            visitor.visitLdcInsn(className.toHumanReadableClassName())
            visitor.visitLdcInsn(callerMethodName)
            visitor.visitLdcInsn(calleeMethodOpcode)
        }
        visitor.visitLdcInsn(calleeMethodOwner.toHumanReadableClassName())
        visitor.visitLdcInsn(calleeMethodName)
        visitor.visitLdcInsn(calleeMethodDesc.toHumanReadableDesc())
        if (receiverIndex == null) {
            visitor.visitInsn(ACONST_NULL)
        } else {
            visitor.visitVarInsn(ALOAD, 0)
        }

        visitor.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            METHOD_CLASS_NAME,
            HOOK_METHOD_NAME,
            HOOK_METHOD_DESC,
            false
        )
    }

    private fun hookMethod(
        visitor: MethodVisitor,
        callerMethodName: String,
        calleeMethodOpcode: Int,
        calleeMethodOwner: String,
        calleeMethodName: String,
        calleeMethodDesc: String,
        calleeMethodIsInterface: Boolean,
    ) {
        if (!(calleeMethodOpcode == Opcodes.INVOKEVIRTUAL ||
                    calleeMethodOpcode == Opcodes.INVOKEINTERFACE)) {
            // In this case, simply inject a method call log.
            logCall(
                visitor,
                callerMethodName,
                calleeMethodOpcode,
                calleeMethodOwner,
                calleeMethodName,
                calleeMethodDesc,
                null
            )
            // Call the real method.
            visitor.visitMethodInsn(
                calleeMethodOpcode,
                calleeMethodOwner,
                calleeMethodName,
                calleeMethodDesc,
                calleeMethodIsInterface
            )
            return
        }

        // If it's a virtual or interface call, call the bridge method to log the real object.
        val bridgeKey = BridgeKey(calleeMethodOwner, calleeMethodName, calleeMethodDesc)
        val existing = bridgeMethods[bridgeKey]
        val bridgeName = existing ?: (APIMAPPER_BRIDGE_PREFIX + nextBridgeMethod++)
        val bridgeDesc =
            prependArgTypeToMethodDescriptor(
                calleeMethodDesc,
                Type.getType("L$calleeMethodOwner;"),
            )

        // Replace the call with a call to the bridge method.
        visitor.visitMethodInsn(
            Opcodes.INVOKESTATIC,
            className,
            bridgeName,
            bridgeDesc,
            false
        )

        if (existing != null) {
            // Bridge method has already created.
            return
        }
        bridgeMethods[bridgeKey] = bridgeName

        // Create a bridge method.
        super.visitMethod(
            ACC_PRIVATE or ACC_STATIC,
            bridgeName,
            bridgeDesc,
            null,
            null,
        ).let { br ->
            br.visitCode()

            logCall(
                br,
                callerMethodName,
                calleeMethodOpcode,
                calleeMethodOwner,
                calleeMethodName,
                calleeMethodDesc,
                0,
            )

            // Re-push the arguments to the stack.
            br.visitVarInsn(ALOAD, 0)
            writeByteCodeToPushArguments(calleeMethodDesc, br, 1)

            // Call the real method.
            br.visitMethodInsn(
                calleeMethodOpcode,
                calleeMethodOwner,
                calleeMethodName,
                calleeMethodDesc,
                calleeMethodIsInterface
            )

            writeByteCodeToReturn(bridgeDesc, br)

            br.visitMaxs(0, 0)
        }
    }

    private data class BridgeKey(
        val className: String,
        val methodName: String,
        val methodDesc: String,
    )
}
