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

package com.android.bedstead.testapisreflection.processor.generators.common;

import static com.android.bedstead.testapisreflection.processor.Processor.ALLOWLISTED_TEST_FIELDS;
import static com.android.bedstead.testapisreflection.processor.Processor.PACKAGE_NAME;
import static com.android.bedstead.testapisreflection.processor.generators.ProxyClassesGenerator.SERVICES_ALIAS;
import static com.android.bedstead.testapis.parser.utils.TypeUtils.convertToKotlinCompatibleType;
import static com.android.bedstead.testapis.parser.utils.TypeUtils.getDeclaredType;
import static com.android.bedstead.testapis.parser.utils.TypeUtils.getNestedClass;
import static com.android.bedstead.testapis.parser.utils.TypeUtils.isNestedClass;
import static com.android.bedstead.testapis.parser.utils.TypeUtils.isParameterizedType;
import static com.android.bedstead.testapis.parser.utils.TypeUtils.parameterizedTypeWrapperName;
import static com.android.bedstead.testapis.parser.utils.TypeUtils.typeForString;
import static com.android.bedstead.testapis.parser.utils.TypeUtils.typePackageName;
import static com.android.bedstead.testapis.parser.utils.TypeUtils.typeSimpleName;
import static com.android.bedstead.testapis.parser.utils.TypeUtils.splitParameterList;

import com.android.bedstead.testapis.parser.signatures.ClassSignature;
import com.android.bedstead.testapis.parser.signatures.FieldSignature;
import com.android.bedstead.testapis.parser.signatures.MethodSignature;

import com.squareup.kotlinpoet.AnnotationSpec;
import com.squareup.kotlinpoet.ClassName;
import com.squareup.kotlinpoet.CodeBlock;
import com.squareup.kotlinpoet.FileSpec;
import com.squareup.kotlinpoet.FunSpec;
import com.squareup.kotlinpoet.KModifier;
import com.squareup.kotlinpoet.ParameterSpec;
import com.squareup.kotlinpoet.ParameterizedTypeName;
import com.squareup.kotlinpoet.PropertySpec;
import com.squareup.kotlinpoet.TypeName;
import com.squareup.kotlinpoet.TypeSpec;

import kotlin.Suppress;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;

/**
 * Helper class to generate code for {@code TestApisReflection}.
 */
public final class CodeGenerator {

    private static final String LOG_TAG = "TestApisReflection";
    private static final Pattern sGetterPattern = Pattern.compile("^(is|get)[A-Z]");

    private final ProcessingEnvironment mProcessingEnvironment;

    public CodeGenerator(ProcessingEnvironment processingEnvironment) {
        mProcessingEnvironment = processingEnvironment;
    }

    /**
     * Generate code for reflection method.
     */
    public void generateReflectionMethod(FileSpec.Builder fileBuilder,
            MethodSignature methodSignature, List<ClassSignature> testClasses) {
        TypeName returnTypeName = returnTypeOrProxy(methodSignature, testClasses);

        // explicitly add import for nested return type
        if (returnTypeName != null) {
            if (isNestedClass(returnTypeName.toString()) &&
                    testClasses.stream().noneMatch(
                            c -> c.getName().equals(returnTypeName.toString()))) {
                List<String> nestedClassParts = getNestedClass(returnTypeName.toString());
                fileBuilder.addAliasedImport(
                        new ClassName(typePackageName(returnTypeName.toString()),
                                nestedClassParts.toArray(new String[0])),
                        String.join("", nestedClassParts));
            }
        }

        ClassName receiverClass = new ClassName(
                typePackageName(methodSignature.getFrameworkClass()),
                typeSimpleName(methodSignature.getFrameworkClass()));

        // explicitly add import for nested receiver class
        if (isNestedClass(receiverClass.toString())) {
            List<String> nestedClassParts = getNestedClass(receiverClass.toString());
            fileBuilder.addAliasedImport(
                    new ClassName(typePackageName(receiverClass.toString()),
                            nestedClassParts.toArray(new String[0])),
                    String.join("", nestedClassParts));
        }

        if (methodSignature.isGetter()) {
            generateGetterPropertyCode(fileBuilder, methodSignature, receiverClass,
                    returnTypeName);
        } else {
            generateReflectionMethodCode(fileBuilder, receiverClass, methodSignature,
                    returnTypeName, testClasses);
        }
    }

    /**
     * Generate code for proxy class.
     */
    public void generateProxyClass(
            ClassSignature classSignature, List<ClassSignature> testClasses) {
        String testApiClassSimpleName = typeSimpleName(classSignature.getName());
        String proxyClassName = testApiClassSimpleName + "Proxy";

        if (isNestedClass(proxyClassName)) {
            List<String> nestedClassParts = getNestedClass(proxyClassName);
            proxyClassName = nestedClassParts.get(0) + nestedClassParts.get(1);
        }

        TypeSpec.Builder proxyClassBuilder = TypeSpec.classBuilder(proxyClassName)
                .addSuperinterface(new ClassName("android.os", "Parcelable"),
                        CodeBlock.builder().build())
                .primaryConstructor(FunSpec.constructorBuilder().build())
                .addModifiers(KModifier.OPEN);

        // Get fields for test classes
        List<FieldSignature> allowlistedTestFields = getTestFieldsForClass(testApiClassSimpleName);

        implementParcelable(proxyClassBuilder, proxyClassName, allowlistedTestFields);

        FileSpec.Builder classBuilder = FileSpec.builder(PACKAGE_NAME, proxyClassName);

        for (MethodSignature methodSignature : classSignature.getMethodSignatures()) {
            try {
                if (methodSignature.getName().equals("describeContents") ||
                        methodSignature.getName().equals("writeToParcel")) {
                    // we generate Parcelable overrides ourselves
                    continue;
                }

                FunSpec.Builder methodBuilder = FunSpec.builder(methodSignature.getName())
                        .addModifiers(KModifier.PUBLIC);

                TypeName returnTypeName = returnTypeOrProxy(methodSignature, testClasses);

                if (returnTypeName != null) {
                    if (isNestedClass(returnTypeName.toString())) {
                        List<String> nestedClassParts = getNestedClass(returnTypeName.toString());
                        classBuilder.addAliasedImport(
                                new ClassName(typePackageName(returnTypeName.toString()),
                                        nestedClassParts.get(0)), nestedClassParts.get(0));
                    }

                    methodBuilder.returns(returnTypeName);
                }

                String[] parametersInfo = generateParametersCode(methodSignature,
                        methodBuilder,
                        testClasses, classBuilder);
                String paramNames = parametersInfo[0];
                String paramTypes = parametersInfo[1];

                CodeBlock.Builder codeBuilder = CodeBlock.builder();

                beginTryBlock(codeBuilder);

                if (SERVICES_ALIAS.containsKey(classSignature.getName())) {
                    // if TestApi is a service class
                    codeBuilder
                            .addStatement("val clazz = Class.forName(%S)", classSignature.getName())
                            .addStatement(
                                    "val obj = androidx.test.platform.app."
                                            + "InstrumentationRegistry.getInstrumentation()"
                                            + ".getTargetContext().getSystemService(%S)",
                                    SERVICES_ALIAS.get(classSignature.getName()));
                } else {
                    codeBuilder
                            .addStatement("val clazz = Class.forName(%S)", classSignature.getName())
                            .addStatement("val pc: java.lang.reflect.Constructor<*> = "
                                    + "clazz.getDeclaredConstructor()")
                            .addStatement("pc.setAccessible(true)")
                            .addStatement("val obj = pc.newInstance()");
                }

                for (FieldSignature field : allowlistedTestFields) {
                    codeBuilder.addStatement(
                            "clazz.getField(%S).set(obj, this.%L)",
                            field.getName(), field.getName());
                }

                if (returnTypeName != null) {
                    if (paramNames != null) {
                        codeBuilder.addStatement(
                                "return clazz.getMethod(%S, %L).invoke("
                                        + "obj, %L) as %L", methodSignature.getName(), paramTypes,
                                paramNames, returnTypeName);
                    } else {
                        codeBuilder.addStatement(
                                "return clazz.getMethod(%S).invoke(obj) as %L",
                                methodSignature.getName(), returnTypeName);
                    }
                } else {
                    if (paramNames != null) {
                        codeBuilder.addStatement(
                                "clazz.getMethod(%S, %L).invoke(obj, %L)",
                                methodSignature.getName(), paramTypes, paramNames);
                    } else {
                        codeBuilder.addStatement(
                                "clazz.getMethod(%S).invoke(obj)",
                                methodSignature.getName());
                    }
                }

                closeTryAndBuildCatchBlock(codeBuilder);

                methodBuilder.addCode(codeBuilder.build());

                proxyClassBuilder.addFunction(methodBuilder.build());
            } catch (IllegalStateException e) {
                System.out.println(LOG_TAG + ": " +
                        String.format("Unable to generate proxy for %s#%s",
                                methodSignature.getFrameworkClass(), methodSignature.getName()));
            }
        }

        classBuilder.addType(proxyClassBuilder.build());

        FileSpec proxyClassFile = classBuilder.build();

        try {
            proxyClassFile.writeTo(mProcessingEnvironment.getFiler());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "TestApisReflection: Error writing proxy file for " + classSignature.getName(),
                    e);
        }
    }

    /**
     * Generate code to reflect on a getter.
     */
    private void generateGetterPropertyCode(FileSpec.Builder classBuilder, MethodSignature method,
            ClassName receiverClass, TypeName returnTypeName) {
        CodeBlock.Builder getterCodeBuilder = CodeBlock.builder();
        boolean isReturnTypeProxy = method.getReturnType().getProxyType() != null;
        String classReference = method.isStatic() ? "null" : "this";

        if (isReturnTypeProxy) {
            getterCodeBuilder.addStatement("val obj = %L::class.java.getMethod(%S).invoke(%L)",
                    receiverClass, method.getName(), classReference);

            generateReturnTypeProxyCode(getterCodeBuilder, returnTypeName);
        } else {
            getterCodeBuilder.addStatement(
                    "return %L::class.java.getMethod(%S).invoke(%L) as %L",
                    receiverClass, method.getName(), classReference, returnTypeName);
        }

        classBuilder.addProperty(
                PropertySpec.builder(removeGetPrefix(method.getName()), returnTypeName)
                        .receiver(receiverClass)
                        .getter(FunSpec.getterBuilder()
                                .addCode(getterCodeBuilder.build()).build())
                        .build());
    }

    /**
     * Generate code to reflect on a regular method.
     */
    private void generateReflectionMethodCode(FileSpec.Builder fileBuilder,
            ClassName receiverClass, MethodSignature methodSignature, TypeName returnTypeName,
            List<ClassSignature> testClasses) {
        FunSpec.Builder methodBuilder = FunSpec.builder(methodSignature.getName())
                .addModifiers(KModifier.PUBLIC)
                .receiver(receiverClass)
                .addAnnotation(AnnotationSpec.builder(Suppress.class)
                        .addMember("%S", "UNCHECKED_CAST")
                        .build());
        if (returnTypeName != null) {
            methodBuilder.returns(returnTypeName);
        }

        String paramNames = null;
        String paramTypes = null;
        if (!methodSignature.getParameterTypes().isEmpty()) {
            String[] parametersInfo = generateParametersCode(methodSignature,
                    methodBuilder, testClasses, fileBuilder);
            paramNames = parametersInfo[0];
            paramTypes = parametersInfo[1];
        }


        CodeBlock.Builder codeBuilder = CodeBlock.builder();

        beginTryBlock(codeBuilder);

        String classReference = methodSignature.isStatic() ? "null" : "this";
        boolean isReturnTypeProxy = methodSignature.getReturnType().getProxyType() != null;

        if (methodBuilder.getParameters().isEmpty()) {
            if (returnTypeName == null) {
                codeBuilder.addStatement(
                        "%L::class.java.getMethod(%S).invoke(%L)", receiverClass,
                        methodSignature.getName(), classReference);
            } else {
                if (isReturnTypeProxy) {
                    codeBuilder.addStatement("val obj = %L::class.java.getMethod(%S).invoke(%L)",
                            receiverClass, methodSignature.getName(), classReference);

                    generateReturnTypeProxyCode(codeBuilder, returnTypeName);
                } else {
                    codeBuilder.addStatement(
                            "return %L::class.java.getMethod(%S).invoke(%L) as %L",
                            receiverClass, methodSignature.getName(), classReference,
                            returnTypeName);
                }
            }
        } else {
            if (returnTypeName == null) {
                codeBuilder.addStatement(
                        "%L::class.java.getMethod(%S, %L).invoke(%L, %L)", receiverClass,
                        methodSignature.getName(), paramTypes, classReference, paramNames);
            } else {
                if (isReturnTypeProxy) {
                    codeBuilder.addStatement(
                            "val obj = %L::class.java.getMethod(%S, %L).invoke(%L, %L)",
                            receiverClass, methodSignature.getName(), paramTypes, classReference,
                            paramNames);

                    generateReturnTypeProxyCode(codeBuilder, returnTypeName);
                } else {
                    codeBuilder.addStatement(
                            "return %L::class.java.getMethod(%S, %L).invoke(%L, %L) as %L",
                            receiverClass, methodSignature.getName(), paramTypes, classReference,
                            paramNames, returnTypeName);
                }
            }
        }

        closeTryAndBuildCatchBlock(codeBuilder);

        methodBuilder.addCode(codeBuilder.build());

        fileBuilder.addFunction(methodBuilder.build());
    }

    /**
     * Common method to generate the parameters part of the method.
     */
    private String[] generateParametersCode(
            MethodSignature method, FunSpec.Builder methodBuilder,
            List<ClassSignature> testClasses, FileSpec.Builder fileBuilder) {
        StringBuilder parameterNamesBuilder = new StringBuilder();
        StringBuilder parameterTypesBuilder = new StringBuilder();
        Set<String> testClassNames = testClasses.stream()
                .map(ClassSignature::getName)
                .collect(Collectors.toUnmodifiableSet());
        int count = 0;

        for (String paramType : method.getParameterTypes()) {
            String paramName = "arg" + count++;

            TypeMirror parameterTypeMirror;
            TypeName parameterTypeName;

            if (testClassNames.contains(paramType)) {
                // if parameter type is a @TestApi class

                String proxyType = PACKAGE_NAME + "." + typeSimpleName(paramType) + "Proxy";
                parameterTypeName = getDeclaredType(proxyType,
                        /* wrappedTypeQualifiedName= */ null,
                        /* returnNonParameterizedType= */ true);

                if (isNestedClass(parameterTypeName.toString())) {
                    List<String> nestedClassParts = getNestedClass(parameterTypeName.toString());
                    parameterTypesBuilder.append(nestedClassParts.get(0)).append(
                            nestedClassParts.get(1)).append("::class.java, ");

                    parameterTypeName = new ClassName(
                            PACKAGE_NAME, nestedClassParts.get(0) + nestedClassParts.get(1));
                } else {
                    parameterTypesBuilder.append(parameterTypeName).append("::class.java, ");
                }
                parameterNamesBuilder.append(paramName).append(", ");
            } else {
                parameterTypeMirror = typeForString(paramType,
                        mProcessingEnvironment.getTypeUtils(),
                        mProcessingEnvironment.getElementUtils());

                parameterTypeName = convertToKotlinCompatibleType(parameterTypeMirror,
                        /* returnNonParameterizedType= */ false);

                TypeName reflectMethodParameterType = convertToKotlinCompatibleType(
                        parameterTypeMirror, /* returnNonParameterizedType= */ true);
                parameterTypesBuilder.append(reflectMethodParameterType).append("::class.java, ");
                parameterNamesBuilder.append(paramName).append(", ");
            }

            if (isParameterizedType(parameterTypeName.toString())) {
                String wrappedParams =
                        parameterTypeName.toString().substring(
                                parameterTypeName.toString().indexOf("<") + 1,
                                parameterTypeName.toString().lastIndexOf(">"));
                // TODO(b/381395375): Support nested generics like `List<List<Boolean>>`.
                List<String> params = splitParameterList(wrappedParams);
                for (String param : params) {
                    if (isNestedClass(param)) {
                        List<String> nestedClassParts = getNestedClass(param);
                        fileBuilder.addAliasedImport(new ClassName(
                                typePackageName(param),
                                nestedClassParts.get(0)), nestedClassParts.get(0));
                    }
                }
            } else if (isNestedClass(parameterTypeName.toString())) {
                List<String> nestedClassParts = getNestedClass(parameterTypeName.toString());
                fileBuilder.addAliasedImport(new ClassName(
                        typePackageName(parameterTypeName.toString()),
                        nestedClassParts.get(0)), nestedClassParts.get(0));
            }

            ParameterSpec parameterSpec = new ParameterSpec(paramName, parameterTypeName);
            methodBuilder.addParameter(parameterSpec);
        }

        String parameterNamesConcatenated = null, parameterTypesConcatenated = null;
        if (!method.getParameterTypes().isEmpty()) {
            parameterNamesConcatenated = parameterNamesBuilder.substring(
                    0, parameterNamesBuilder.toString().length() - 2);
            parameterTypesConcatenated = parameterTypesBuilder.substring(
                    0, parameterTypesBuilder.toString().length() - 2);
        }

        return new String[]{parameterNamesConcatenated, parameterTypesConcatenated};
    }

    /**
     * Common method to return the appropriate proxy type if return type is a proxy class,
     * otherwise the original type.
     */
    private TypeName returnTypeOrProxy(MethodSignature method, List<ClassSignature> testClasses) {
        TypeName returnTypeName = null;

        Set<String> testClassNames = testClasses.stream()
                .map(ClassSignature::getName)
                .collect(Collectors.toUnmodifiableSet());

        if (testClassNames.contains(method.getReturnType().getType())) {
            // if return type is a 'TestClass'

            if (isNestedClass(method.getReturnType().getType())) {
                List<String> nestedParts = getNestedClass(method.getReturnType().getType());
                method.getReturnType().setProxyType(PACKAGE_NAME + "." +
                        nestedParts.get(0) + nestedParts.get(1) + "Proxy");
            } else {
                method.getReturnType().setProxyType(PACKAGE_NAME + "." +
                        typeSimpleName(method.getReturnType().getType()) + "Proxy");
            }

            returnTypeName = getDeclaredType(method.getReturnType().getProxyType(),
                    /* wrappedTypeQualifiedName= */ null,
                    /* returnNonParameterizedType= */ true);
        }

        if (isParameterizedType(method.getReturnType().getType())) {
            // if return type is parameterized
            // and if wrapped type here is an "allowlisted test class",
            // then convert wrapped type to proxy type
            String wrappedType = method.getReturnType().getType().substring(
                    method.getReturnType().getType().indexOf("<") + 1,
                    method.getReturnType().getType().indexOf(">"));

            if (testClassNames.contains(wrappedType)) {
                String rawType = method.getReturnType().getType().substring(
                        0, method.getReturnType().getType().indexOf("<"));

                String wrappedProxyType;
                if (isNestedClass(wrappedType)) {
                    List<String> nestedParts = getNestedClass(wrappedType);
                    wrappedProxyType =
                            PACKAGE_NAME + "." + nestedParts.get(0) + nestedParts.get(1) + "Proxy";
                } else {
                    wrappedProxyType = PACKAGE_NAME + "." + typeSimpleName(wrappedType) + "Proxy";
                }

                method.getReturnType().setProxyType(rawType + "<" + wrappedProxyType + ">");

                returnTypeName = getDeclaredType(rawType,
                        Collections.singletonList(wrappedProxyType),
                        /* returnNonParameterizedType= */ false);
            }
        }

        if (method.getReturnType().getProxyType() == null) {
            TypeMirror returnTypeMirror = typeForString(
                    method.getReturnType().getType(),
                    mProcessingEnvironment.getTypeUtils(),
                    mProcessingEnvironment.getElementUtils());
            returnTypeName = convertToKotlinCompatibleType(returnTypeMirror,
                    /* returnNonParameterizedType= */ false);
        }

        return returnTypeName;
    }

    /**
     * Common method to generate code for when return type is a proxy class.
     */
    private void generateReturnTypeProxyCode(CodeBlock.Builder codeBuilder,
            TypeName returnTypeName) {
        String returnType = returnTypeName.toString();
        String returnTypeSimpleName;
        if (isParameterizedType(returnType)) {
            returnTypeSimpleName = parameterizedTypeWrapperName(returnTypeName.toString());
        } else {
            returnTypeSimpleName = typeSimpleName(returnTypeName.toString());
        }

        List<FieldSignature> allowlistedTestFields = getTestFieldsForClass(
                returnTypeSimpleName.replaceAll("Proxy", ""));

        if (isParameterizedType(returnType)) {
            // If return type is parameterized
            String rawType = returnType.substring(0, returnType.indexOf("<"));
            String wrappedType = returnType.substring(returnType.indexOf("<") + 1,
                    returnType.indexOf(">"));

            if (rawType.contains("List") || rawType.contains("Collection")) {
                codeBuilder.addStatement("val objAsList = obj as List<*>");
                codeBuilder.addStatement("val list = arrayListOf<%L>()", wrappedType);

                codeBuilder.addStatement("for (o in objAsList) {", wrappedType);
                codeBuilder.addStatement("\tval i = %L()", wrappedType);

                for (FieldSignature field : allowlistedTestFields) {
                    TypeName type = convertToKotlinCompatibleType(field.getType(),
                            /* returnNonParameterizedType= */ false);
                    codeBuilder.addStatement(
                            "\ti.%L = o?.javaClass?.getField(%S)?.get(o) as %L?", field.getName(),
                            field.getName(), type.toString());
                }
                codeBuilder.addStatement("\tlist.add(i)");
                codeBuilder.addStatement("}");

                codeBuilder.addStatement("return list");
            } else if (rawType.contains("Set")) {
                codeBuilder.addStatement("val objAsSet = obj as Set<*>");
                codeBuilder.addStatement("val set = mutableSetOf<%L>()", wrappedType);

                codeBuilder.addStatement("for (o in objAsSet) {", wrappedType);
                codeBuilder.addStatement("\tval i = %L()", wrappedType);

                for (FieldSignature field : allowlistedTestFields) {
                    TypeName type = convertToKotlinCompatibleType(field.getType(),
                            /* returnNonParameterizedType= */ false);
                    codeBuilder.addStatement(
                            "\ti.%L = o?.javaClass?.getField(%S)?.get(o) as %L?", field.getName(),
                            field.getName(), type.toString());
                }

                codeBuilder.addStatement("\tset.add(i)");
                codeBuilder.addStatement("}");

                codeBuilder.addStatement("return set");
            }
        } else {
            codeBuilder.addStatement("val i = %L()", returnType);

            for (FieldSignature field : allowlistedTestFields) {
                TypeName type = convertToKotlinCompatibleType(field.getType(),
                        /* returnNonParameterizedType= */ false);
                codeBuilder.addStatement(
                        "i.%L = obj.javaClass.getDeclaredField(%S).get(obj) as %L", field.getName(),
                        field.getName(), type.toString());
            }

            codeBuilder.addStatement("return i");
        }
    }

    private void implementParcelable(TypeSpec.Builder proxyClassBuilder,
            String proxyClassName, List<FieldSignature> allowlistedTestFields) {
        FunSpec.Builder parcelConstructorBuilder = FunSpec.constructorBuilder()
                .addModifiers(KModifier.PRIVATE)
                .addParameter(new ParameterSpec("source",
                        new ClassName("android.os", "Parcel")))
                .callThisConstructor(CodeBlock.builder().build());

        TypeName intTypeName = convertToKotlinCompatibleType(typeForString("int",
                        mProcessingEnvironment.getTypeUtils(),
                        mProcessingEnvironment.getElementUtils()),
                /* returnNonParameterizedType= */ true);

        // Create describeContents method
        proxyClassBuilder.addFunction(FunSpec.builder("describeContents")
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .addCode(CodeBlock.builder().addStatement("return 0").build())
                .returns(intTypeName)
                .build());

        // Create writeToParcel method
        List<ParameterSpec> writeToParcelParameters = new ArrayList<>();
        writeToParcelParameters.add(new ParameterSpec("dest",
                new ClassName("android.os", "Parcel")));
        writeToParcelParameters.add(new ParameterSpec("flags", intTypeName));

        FunSpec.Builder writeToParcelMethod = FunSpec.builder("writeToParcel")
                .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                .addParameters(writeToParcelParameters);

        for (FieldSignature field : allowlistedTestFields) {
            TypeName fieldTypeName = convertToKotlinCompatibleType(field.getType(),
                    /* returnNonParameterizedType= */ false);

            proxyClassBuilder.addProperty(
                    PropertySpec.builder(field.getName(),
                                    fieldTypeName.copy(true, Collections.emptyList()))
                            .addAnnotation(AnnotationSpec.builder(
                                            new ClassName("kotlin.jvm", "JvmField"))
                                    .build())
                            .mutable(true)
                            .initializer("null")
                            .build());

            switch (fieldTypeName.toString()) {
                case "kotlin.Int":
                    writeToParcelMethod.addCode(
                            CodeBlock.builder().addStatement("dest.writeInt(%L!!)",
                                    field.getName()).build());
                    parcelConstructorBuilder.addCode(
                            CodeBlock.builder().addStatement("%L = source.readInt()",
                                    field.getName()).build());
                    break;
                case "kotlin.Long":
                    writeToParcelMethod.addCode(
                            CodeBlock.builder().addStatement("dest.writeLong(%L!!)",
                                    field.getName()).build());
                    parcelConstructorBuilder.addCode(
                            CodeBlock.builder().addStatement("%L = source.readLong()",
                                    field.getName()).build());
                    break;
                case "kotlin.String":
                    writeToParcelMethod.addCode(
                            CodeBlock.builder().addStatement("dest.writeString(%L!!)",
                                    field.getName()).build());
                    parcelConstructorBuilder.addCode(
                            CodeBlock.builder()
                                    .addStatement("%L = source.readString()", field.getName())
                                    .build());
                    break;
                case "kotlin.Boolean":
                    writeToParcelMethod.addCode(
                            CodeBlock.builder().addStatement("dest.writeBoolean(%L!!)",
                                    field.getName()).build());
                    parcelConstructorBuilder.addCode(
                            CodeBlock.builder()
                                    .addStatement("%L = source.readBoolean()", field.getName())
                                    .build());
                    break;
                default:
                    writeToParcelMethod.addCode(
                            CodeBlock.builder().addStatement("dest.writeParcelable(%L!!, flags)",
                                    field.getName()).build());
                    parcelConstructorBuilder.addCode(
                            CodeBlock.builder()
                                    .addStatement(
                                            "%L = source.readParcelable(%L::class.java"
                                                    + ".getClassLoader())",
                                            field.getName(), field.getFrameworkClass())
                                    .build());
            }
        }
        proxyClassBuilder.addFunction(writeToParcelMethod.build());
        proxyClassBuilder.addFunction(parcelConstructorBuilder.build());

        // Create static field CREATOR
        TypeName nullableProxyClass = new ClassName(PACKAGE_NAME, proxyClassName)
                .copy(true, Collections.emptyList());
        TypeSpec parcelableCreator = TypeSpec.classBuilder("ParcelableCreator")
                .addModifiers(KModifier.OPEN)
                .addSuperinterface(
                        ParameterizedTypeName.get(
                                new ClassName("android.os.Parcelable", "Creator"),
                                Collections.singletonList(nullableProxyClass)),
                        CodeBlock.builder().build())
                .addFunction(FunSpec.builder("createFromParcel")
                        .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                        .addParameter(new ParameterSpec("arg0",
                                new ClassName("android.os", "Parcel")))
                        .returns(nullableProxyClass)
                        .addCode(CodeBlock.builder()
                                .addStatement("return %L(arg0)", proxyClassName).build())
                        .build())
                .addFunction(FunSpec.builder("newArray")
                        .addModifiers(KModifier.PUBLIC, KModifier.OVERRIDE)
                        .addParameter(new ParameterSpec("size",
                                new ClassName("kotlin", "Int")))
                        .returns(ParameterizedTypeName.get(
                                new ClassName("kotlin", "Array"),
                                Collections.singletonList(nullableProxyClass)))
                        .addCode(CodeBlock.builder()
                                .addStatement("return arrayOfNulls(size)", proxyClassName)
                                .build())
                        .build())
                .build();
        proxyClassBuilder.addType(parcelableCreator);
        proxyClassBuilder.addType(TypeSpec.companionObjectBuilder("CREATOR")
                .superclass(
                        new ClassName(PACKAGE_NAME + "." + proxyClassName, "ParcelableCreator"))
                .build());
    }

    private List<FieldSignature> getTestFieldsForClass(String frameworkClass) {
        return ALLOWLISTED_TEST_FIELDS.stream()
                .map(f -> FieldSignature.forFieldString(f, mProcessingEnvironment.getTypeUtils(),
                        mProcessingEnvironment.getElementUtils()))
                .filter(t -> typeSimpleName(t.getFrameworkClass()).equals(frameworkClass))
                .collect(Collectors.toUnmodifiableList());
    }

    private static String removeGetPrefix(String name) {
        Matcher matcher = sGetterPattern.matcher(name);
        if (!matcher.find()) {
            return name;
        }

        char[] c = name.replaceFirst(matcher.group(1), "").toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return new String(c);
    }

    private static void beginTryBlock(CodeBlock.Builder codeBuilder) {
        codeBuilder.beginControlFlow("try {");
    }

    private static void closeTryAndBuildCatchBlock(CodeBlock.Builder codeBuilder) {
        // close try block
        codeBuilder.endControlFlow();
        // catch InvocationTargetException and throw actual cause
        codeBuilder.beginControlFlow("catch (e: java.lang.reflect.InvocationTargetException) {");
        codeBuilder.addStatement("throw e.cause!!");
        codeBuilder.endControlFlow();
    }
}
