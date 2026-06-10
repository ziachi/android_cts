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

import com.android.xts.apimapper.asm.CLASS_INITIALIZER_DESC
import com.android.xts.apimapper.asm.CLASS_INITIALIZER_NAME
import com.android.xts.apimapper.asm.CTOR_DESC
import com.android.xts.apimapper.asm.CTOR_NAME
import com.android.xts.apimapper.asm.ClassNodes
import org.junit.ClassRule
import org.junit.Rule
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.ACC_ABSTRACT
import org.objectweb.asm.Opcodes.ACC_ANNOTATION
import org.objectweb.asm.Opcodes.ACC_ENUM
import org.objectweb.asm.Opcodes.ACC_FINAL
import org.objectweb.asm.Opcodes.ACC_PUBLIC
import org.objectweb.asm.Opcodes.ACC_STATIC
import org.objectweb.asm.Opcodes.ALOAD
import org.objectweb.asm.Opcodes.ASTORE
import org.objectweb.asm.Opcodes.ATHROW
import org.objectweb.asm.Opcodes.GETFIELD
import org.objectweb.asm.Opcodes.GOTO
import org.objectweb.asm.Opcodes.INVOKESPECIAL
import org.objectweb.asm.Opcodes.INVOKEVIRTUAL
import org.objectweb.asm.Opcodes.RETURN
import org.objectweb.asm.Type
import org.objectweb.asm.commons.AdviceAdapter

private const val HELPER_RULE_CLASS =
    "com/android/xts/apimapper/helper/DeviceApiMapperHelperRule"
private const val INSTANCE_RULE_FIELD_NAME = "_apiMapperHelperInstanceRule"
private const val CLASS_RULE_FIELD_NAME = "_apiMapperHelperClassRule"
private const val RUN_BARE_METHOD = "runBare"
private const val RUN_BARE_DESC = "()V"
private const val TEST_RULE_FIELD_DESC = "L$HELPER_RULE_CLASS;"

/**
 * Inject `DeviceApiMapperHelperRule` junit rule for device-side test classes.
 *
 * If the test class extends from Junit3 `TestCase`, then it doesn't support JUnit rules. In
 * that case, add a `runBare()` override and explicitly call methods defined in
 * `DeviceApiMapperHelperRule`.
 */
open class RuleInjectingAdapter(
    nextVisitor: ClassVisitor,
    protected val classNodes: ClassNodes,
) : ClassVisitor(OPCODE_VERSION, nextVisitor) {

    private val instanceRuleFieldDesc = Type.getType(Rule::class.java).descriptor
    private val classRuleFieldDesc = Type.getType(ClassRule::class.java).descriptor
    private var isTestClass: Boolean = false
    private var isJunit3Test: Boolean = false

    private lateinit var className: String

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?,
    ) {
        className = name
        super.visit(version, access, name, signature, superName, interfaces)

        classNodes.findClass(name)?.let {cn ->
            val isAbstract = (access and ACC_ABSTRACT) != 0
            val isAnnotation = (access and ACC_ANNOTATION) != 0
            val isEnum = (access and ACC_ENUM) != 0
            if (!isAbstract && !isAnnotation && !isEnum && cn.isTestClass(classNodes)) {
                isTestClass = true
                isJunit3Test = cn.isJunit3Test(classNodes)
            }
        }
        if (isTestClass) {
            injectRuleFields()
            if (!classNodes.hasClassInitializer(className)) {
                // If the class doesn't have a <clinit>, create it.
                injectClassInitializer()
            }
            if (isJunit3Test) {
                injectRunBareForTestCase()
            }
        }
    }

    /** Create a <clinit> method. */
    private fun injectClassInitializer() {
        visitMethod(
            ACC_STATIC,
            CLASS_INITIALIZER_NAME,
            CLASS_INITIALIZER_DESC,
            null,
            null
        ).let { mv ->
            mv.visitCode()
            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(0, 0) // Let ASM figure them out.
        }
    }

    private fun injectRuleFields() {
        /*
        Generated code should be like:
        public static final org.junit.rules.TestRule _apiMapperHelperClassRule;
          descriptor: Lorg/junit/rules/TestRule;
          flags: (0x0019) ACC_PUBLIC, ACC_STATIC, ACC_FINAL
          RuntimeVisibleAnnotations:
            0: #33()
              org.junit.ClassRule

        public final org.junit.rules.TestRule _apiMapperHelperInstanceRule;
          descriptor: Lorg/junit/rules/TestRule;
          flags: (0x0011) ACC_PUBLIC, ACC_FINAL
          RuntimeVisibleAnnotations:
            0: #34()
              org.junit.Rule
         */
        visitField(
            ACC_PUBLIC or ACC_STATIC or ACC_FINAL,
            CLASS_RULE_FIELD_NAME,
            TEST_RULE_FIELD_DESC,
            null,
            null,
        ).let { fv ->
            fv.visitAnnotation(classRuleFieldDesc, true)
            fv.visitEnd()
        }
        visitField(
            ACC_PUBLIC or ACC_FINAL,
            INSTANCE_RULE_FIELD_NAME,
            TEST_RULE_FIELD_DESC,
            null,
            null,
        ).let { fv ->
            fv.visitAnnotation(instanceRuleFieldDesc, true)
            fv.visitEnd()
        }
    }

    /** Inject a "runBare() override to a JUnit3 test class. */
    private fun injectRunBareForTestCase() {
        var calleeRunBareMethodName = RUN_BARE_METHOD
        var calleeRunBareClassName = "junit/framework/TestCase"

        // If runBare has already been overridden, rename it to _runBare and call it in the injected
        // runBare method.
        if (classNodes.findMethod(className, RUN_BARE_METHOD, RUN_BARE_DESC) != null) {
            calleeRunBareMethodName = "_$RUN_BARE_METHOD"
            calleeRunBareClassName = className
        } else {
            val cn = classNodes.findClass(className)
            if (cn?.superName != null) {
                calleeRunBareClassName = cn.superName
            }
        }

        /*
        Generated code should look like this

        public void runBare() throws Throwable {
            this._apiMapperHelperInstanceRule.onBeforeTest(this.getClass().getName(), this.getName());
            try {
                super.runBare();
            } finally {
                this._apiMapperHelperInstanceRule.onAfterTest(this.getClass().getName(), this.getName());
            }
        }

        public void runBare() throws java.lang.Throwable;
          descriptor: ()V
          flags: (0x0001) ACC_PUBLIC
          Code:
            stack=3, locals=2, args_size=1
               0: aload_0
               1: getfield      #13                 // Field _apiMapperHelperInstanceRule:Lcom/android/mytest/DeviceApiMapperHelperRule;
               4: aload_0
               5: invokevirtual #16                 // Method java/lang/Object.getClass:()Ljava/lang/Class;
               8: invokevirtual #22                 // Method java/lang/Class.getName:()Ljava/lang/String;
              11: aload_0
              12: invokevirtual #28                 // Method getName:()Ljava/lang/String;
              15: invokevirtual #29                 // Method com/android/mytest/DeviceApiMapperHelperRule.onBeforeTest:(Ljava/lang/String;Ljava/lang/String;)V

              18: aload_0
              19: invokespecial #35                 // Method junit/framework/TestCase.runBare:()V

              22: aload_0
              23: getfield      #13                 // Field _apiMapperHelperInstanceRule:Lcom/android/mytest/DeviceApiMapperHelperRule;
              26: aload_0
              27: invokevirtual #16                 // Method java/lang/Object.getClass:()Ljava/lang/Class;
              30: invokevirtual #22                 // Method java/lang/Class.getName:()Ljava/lang/String;
              33: aload_0
              34: invokevirtual #28                 // Method getName:()Ljava/lang/String;
              37: invokevirtual #38                 // Method com/android/mytest/DeviceApiMapperHelperRule.onAfterTest:(Ljava/lang/String;Ljava/lang/String;)V

              40: goto          64
              43: astore_1

              44: aload_0
              45: getfield      #13                 // Field _apiMapperHelperInstanceRule:Lcom/android/mytest/DeviceApiMapperHelperRule;
              48: aload_0
              49: invokevirtual #16                 // Method java/lang/Object.getClass:()Ljava/lang/Class;
              52: invokevirtual #22                 // Method java/lang/Class.getName:()Ljava/lang/String;
              55: aload_0
              56: invokevirtual #28                 // Method getName:()Ljava/lang/String;
              59: invokevirtual #38                 // Method com/android/mytest/DeviceApiMapperHelperRule.onAfterTest:(Ljava/lang/String;Ljava/lang/String;)V

              62: aload_1
              63: athrow
              64: return
            Exception table:
               from    to  target type
                  18    22    43   any
          Exceptions:
            throws java.lang.Throwable
         */

        visitMethod(
            ACC_PUBLIC,
            "_apimapper_$RUN_BARE_METHOD",
            RUN_BARE_DESC,
            null,
            null
        ).let { mv ->
            mv.visitCode()

            mv.visitVarInsn(ALOAD, 0)
            mv.visitFieldInsn(GETFIELD, className, INSTANCE_RULE_FIELD_NAME, TEST_RULE_FIELD_DESC)
            mv.visitVarInsn(ALOAD, 0)
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/lang/Object",
                "getClass",
                "()Ljava/lang/Class;",
                false
            )
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                "java/lang/Class",
                "getName",
                "()Ljava/lang/String;",
                false
            )
            mv.visitVarInsn(ALOAD, 0)
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                "junit/framework/TestCase",
                "getName",
                "()Ljava/lang/String;",
                false
            )
            mv.visitMethodInsn(
                INVOKEVIRTUAL,
                HELPER_RULE_CLASS,
                "onBeforeTest",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                false
            )

            val tryStartLabel = Label()
            mv.visitLabel(tryStartLabel)
            mv.visitVarInsn(ALOAD, 0)
            mv.visitMethodInsn(
                INVOKESPECIAL,
                calleeRunBareClassName,
                calleeRunBareMethodName,
                RUN_BARE_DESC,
                false
            )
            val tryEndLabel = Label()
            mv.visitLabel(tryEndLabel)

            val finallyCode = {
                mv.visitVarInsn(ALOAD, 0)
                mv.visitFieldInsn(
                    GETFIELD,
                    className,
                    INSTANCE_RULE_FIELD_NAME,
                    TEST_RULE_FIELD_DESC
                )
                mv.visitVarInsn(ALOAD, 0)
                mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/lang/Object",
                    "getClass",
                    "()Ljava/lang/Class;",
                    false
                )
                mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "java/lang/Class",
                    "getName",
                    "()Ljava/lang/String;",
                    false
                )
                mv.visitVarInsn(ALOAD, 0)
                mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    "junit/framework/TestCase",
                    "getName",
                    "()Ljava/lang/String;",
                    false
                )
                mv.visitMethodInsn(
                    INVOKEVIRTUAL,
                    HELPER_RULE_CLASS,
                    "onAfterTest",
                    "(Ljava/lang/String;Ljava/lang/String;)V",
                    false
                )
            }
            finallyCode()

            val returnLabel = Label()
            mv.visitJumpInsn(GOTO, returnLabel)

            val finallyLabel = Label()
            mv.visitLabel(finallyLabel)
            mv.visitVarInsn(ASTORE, 1)
            finallyCode()

            mv.visitVarInsn(ALOAD, 1)
            mv.visitInsn(ATHROW)
            mv.visitLabel(returnLabel)
            mv.visitInsn(RETURN)

            mv.visitTryCatchBlock(tryStartLabel, tryEndLabel, finallyLabel, null)

            mv.visitMaxs(0, 0) // Let ASM figure them out.
        }
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        var realName = name
        // Make the injected runBare method to the real runBare override.
        if (name == "_apimapper_$RUN_BARE_METHOD") {
            realName = RUN_BARE_METHOD
        }
        // If runBare has already been overridden, rename it to _runBare.
        if (isJunit3Test && name == RUN_BARE_METHOD && descriptor == RUN_BARE_DESC) {
            realName = "_$RUN_BARE_METHOD"
        }

        var mv = super.visitMethod(access, realName, descriptor, signature, exceptions)

        if (isTestClass) {
            if (name == CLASS_INITIALIZER_NAME && descriptor == CLASS_INITIALIZER_DESC) {
                mv = ClassRuleInjectingAdapter(mv)
            } else if (name == CTOR_NAME) {
                mv = InstanceRuleInjectingAdapter(access, name, descriptor, mv)
            }
        }
        return mv
    }

    /** Initialize the `_apiMapperHelperClassRule` field. */
    private inner class ClassRuleInjectingAdapter(
        next: MethodVisitor?
    ) : MethodVisitor(OPCODE_VERSION, next) {
        override fun visitCode() {
            super.visitCode()

            /*
            Inject a line  _apiMapperHelperClassRule = new DeviceApiMapperHelperRule();

            Generated code should be like:
            static {};
              descriptor: ()V
              flags: (0x0008) ACC_STATIC
              Code:
                stack=2, locals=0, args_size=0
                   0: new           #30                 // class com/android/mytest/DeviceApiMapperHelperRule
                   3: dup
                   4: invokespecial #41                 // Method com/android/mytest/DeviceApiMapperHelperRule."<init>":()V
                   7: putstatic     #7                  // Field _apiMapperHelperClassRule:Lcom/android/mytest/DeviceApiMapperHelperRule;
                  10: return
             */
            visitTypeInsn(Opcodes.NEW, HELPER_RULE_CLASS)
            visitInsn(Opcodes.DUP)
            visitMethodInsn(
                INVOKESPECIAL,
                HELPER_RULE_CLASS,
                CTOR_NAME,
                CTOR_DESC,
                false
            )
            visitFieldInsn(
                Opcodes.PUTSTATIC,
                className,
                CLASS_RULE_FIELD_NAME,
                TEST_RULE_FIELD_DESC
            )
        }
    }

    /** Initialize the `_apiMapperHelperInstanceRule` field. */
    private inner class InstanceRuleInjectingAdapter(
        access: Int,
        name: String,
        descriptor: String,
        next: MethodVisitor?
    ) : AdviceAdapter(OPCODE_VERSION, next, access, name, descriptor) {
        override fun onMethodEnter() {
            /*
            Inject a line _apiMapperHelperInstanceRule = _apiMapperHelperClassRule;

            Generated code should be like:
            public com.android.mytest.ATest();
              descriptor: ()V
              flags: (0x0001) ACC_PUBLIC
              Code:
                stack=2, locals=1, args_size=1
                   0: aload_0
                   1: invokespecial #1                  // Method junit/framework/TestCase."<init>":()V
                   4: aload_0
                   5: getstatic     #7                  // Field _apiMapperHelperClassRule:Lcom/android/mytest/DeviceApiMapperHelperRule;
                   8: putfield      #13                 // Field _apiMapperHelperInstanceRule:Lcom/android/mytest/DeviceApiMapperHelperRule;
                  11: return
             */
            visitVarInsn(ALOAD, 0)
            visitFieldInsn(
                Opcodes.GETSTATIC,
                className,
                CLASS_RULE_FIELD_NAME,
                TEST_RULE_FIELD_DESC
            )
            visitFieldInsn(
                Opcodes.PUTFIELD,
                className,
                INSTANCE_RULE_FIELD_NAME,
                TEST_RULE_FIELD_DESC
            )
        }
    }
}
