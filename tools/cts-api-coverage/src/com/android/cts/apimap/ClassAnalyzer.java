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

package com.android.cts.apimap;

import com.android.cts.apicommon.ApiCoverage;
import com.android.cts.ctsprofiles.ClassProfile;
import com.android.cts.ctsprofiles.ClassProfile.ClassType;
import com.android.cts.ctsprofiles.MethodProfile;
import com.android.cts.ctsprofiles.MethodProfile.MethodType;
import com.android.cts.ctsprofiles.ModuleProfile;
import com.android.cts.ctsprofiles.Utils;

import org.apache.commons.math3.util.Pair;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** A class for collecting class methods and annotations. */
public class ClassAnalyzer extends ClassVisitor {

    private final ClassProfile mClass;

    private final ModuleProfile mModule;

    private final ApiCoverage mApiCoverage;

    static final Set<String> XTS_ANNOTATIONS = new HashSet<>(
            List.of("com.android.compatibility.common.util.CddTest",
                    "com.android.compatibility.common.util.ApiTest",
                    "com.android.compatibility.common.util.GmsTest",
                    "com.android.compatibility.common.util.VsrTest",
                    "android.platform.test.annotations.RequiresFlagsEnabled",
                    "android.platform.test.annotations.RequiresFlagsDisabled")
    );

    public ClassAnalyzer(
            ClassProfile classProfile, ModuleProfile moduleProfile, ApiCoverage apiCoverage) {
        super(Opcodes.ASM9);
        mClass = classProfile;
        mModule = moduleProfile;
        mApiCoverage = apiCoverage;
    }

    @Override
    public void visit(
            int version,
            int access,
            String name,
            String signature,
            String superName,
            String[] interfaces) {
        addClassType(access, Opcodes.ACC_ABSTRACT, ClassType.ABSTRACT);
        addClassType(access, Opcodes.ACC_INTERFACE, ClassType.INTERFACE);
        addClassType(access, Opcodes.ACC_ANNOTATION, ClassType.ANNOTATION);
        if (superName != null) {
            mClass.setSuperClass(getClassProfile(superName));
        }
        if (interfaces != null) {
            for (String interfaceName : interfaces) {
                mClass.addInterface(getClassProfile(interfaceName));
            }
        }
    }

    @Override
    public MethodVisitor visitMethod(
            int access,
            String name,
            String desc,
            String signature,
            String[] exceptions) {
        // Add a method in the class.
        List<String> params = new ArrayList<>();
        for (Type type: Type.getArgumentTypes(desc)) {
            params.add(type.getClassName().replace("\\$", "."));
        }
        MethodProfile method = mClass.getOrCreateMethod(name, params);
        method.addMethodType(MethodType.DIRECT_MEMBER);
        Type returnType = Type.getReturnType(desc);
        // Set the method type to common if it is not a test methods candidate.
        if ((access & Opcodes.ACC_PUBLIC) == 0
                || !returnType.getClassName().equals("void")
                || !params.isEmpty()) {
            method.addMethodType(MethodType.COMMON);
        }
        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            method.addMethodType(MethodType.ABSTRACT);
        }
        return new MethodAnalyzer(method, mModule, mApiCoverage);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        // Add an annotation for the class.
        // TODO(slotus): Filter out some annotations.
        String type = Type.getType(desc).getClassName();
        Pair<String, String> packageClass = Utils.getPackageClass(type);
        ClassProfile annotationClass = mModule.getOrCreateClass(
                packageClass.getFirst(), packageClass.getSecond(), mApiCoverage);
        mClass.annotationManagement.addAnnotation(annotationClass);
        if (XTS_ANNOTATIONS.contains(type)) {
            return new AnnotationAnalyzer(mClass.annotationManagement, packageClass.getSecond());
        }
        return super.visitAnnotation(desc, visible);
    }

    private ClassProfile getClassProfile(String asmClassName) {
        // Add a class in the module.
        Pair<String, String> packageClass = Utils.getPackageClassFromASM(asmClassName);
        return mModule.getOrCreateClass(
                packageClass.getFirst(), packageClass.getSecond(), mApiCoverage);
    }

    private void addClassType(int access, int asmType, ClassType classType) {
        if ((access & asmType) != 0) {
            mClass.addClassType(classType);
        }
    }
}
