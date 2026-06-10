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

import static com.android.cts.apimap.ClassAnalyzer.XTS_ANNOTATIONS;

import com.android.cts.apicommon.ApiCoverage;
import com.android.cts.ctsprofiles.ClassProfile;
import com.android.cts.ctsprofiles.MethodProfile;
import com.android.cts.ctsprofiles.ModuleProfile;
import com.android.cts.ctsprofiles.Utils;

import org.apache.commons.math3.util.Pair;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/** A class for collecting method calls and method annotations. */
public class MethodAnalyzer extends MethodVisitor {

    private final MethodProfile mMethod;

    private final ModuleProfile mModule;

    private final ApiCoverage mApiCoverage;

    // TODO: Add known common method packages that don't need to be recorded.
    private static final Set<String> COMMON_PACKAGES = new HashSet<>(List.of());

    public MethodAnalyzer(
            MethodProfile methodProfile,
            ModuleProfile moduleProfile,
            ApiCoverage apiCoverage) {
        super(Opcodes.ASM9);
        mMethod = methodProfile;
        mModule = moduleProfile;
        mApiCoverage = apiCoverage;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        // Add an annotation for the method.
        // TODO(slotus): Filter out some annotations.
        String type = Type.getType(desc).getClassName();
        Pair<String, String> packageClass = Utils.getPackageClass(type);
        ClassProfile annotationClass = mModule.getOrCreateClass(
                packageClass.getFirst(), packageClass.getSecond(), mApiCoverage);
        mMethod.annotationManagement.addAnnotation(annotationClass);
        if (XTS_ANNOTATIONS.contains(type)) {
            return new AnnotationAnalyzer(mMethod.annotationManagement, packageClass.getSecond());
        }
        return super.visitAnnotation(desc, visible);
    }

    @Override
    public void visitInvokeDynamicInsn(
            String name,
            String desc,
            Handle bootstrapMethodHandle, Object... bootstrapMethodArgs) {
        for (Object obj : bootstrapMethodArgs) {
            if (!(obj instanceof Handle handle)) {
                continue;
            }
            try {
                handleMethodCall(handle.getOwner(), handle.getName(), handle.getDesc());
            } catch (RuntimeException e) {
                // TODO(slotus): handle the exception
            }
        }
    }

    @Override
    public void visitMethodInsn(int opcode, String owners, String name, String desc, boolean itf) {
        handleMethodCall(owners, name, desc);
    }

    /** Records a method call. */
    private void handleMethodCall(String owners, String name, String desc) {
        Pair<String, String>  packageClass = Utils.getPackageClassFromASM(owners);
        String packageName = packageClass.getFirst();
        String className = packageClass.getSecond();
        if (!shouldRecordMethodCall(packageName, className)) {
            return;
        }
        List<String> params = new ArrayList<>();
        for (Type type : Type.getArgumentTypes(desc)) {
            params.add(type.getClassName().replaceAll("\\$", "."));
        }
        ClassProfile classProfile = mModule.getOrCreateClass(
                packageName, className, mApiCoverage);
        MethodProfile callMethod = classProfile.getOrCreateMethod(name, params);
        mMethod.addMethodCall(callMethod);
    }

    private static boolean shouldRecordMethodCall(String packageName, String className) {
        if (className.startsWith("[")) {
            return false;
        }
        for (String commonPackage: COMMON_PACKAGES) {
            if (packageName.startsWith(commonPackage)) {
                return false;
            }
        }
        return true;
    }
}
