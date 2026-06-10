/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.remoteframeworkclasses.processor;

import com.android.bedstead.remoteframeworkclasses.processor.annotations.RemoteFrameworkClasses;
import com.android.bedstead.testapis.parser.TestApisParser;
import com.android.bedstead.testapis.parser.signatures.ClassSignature;

import com.google.android.enterprise.connectedapps.annotations.CrossUser;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Generated;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;

/**
 * Processor for generating {@code RemoteSystemService} classes.
 *
 * <p>This is started by including the {@link RemoteFrameworkClasses} annotation.
 *
 * <p>For each entry in {@code FRAMEWORK_CLASSES} this will generate an interface including all
 * public
 * and test APIs with the {@code CrossUser} annotation. This interface will be named the same as
 * the
 * framework class except with a prefix of "Remote", and will be in the same package.
 *
 * <p>This will also generate an implementation of the interface which takes an instance of the
 * framework class in the constructor, and each method proxying calls to the framework class.
 */
@SupportedAnnotationTypes({
        "com.android.bedstead.remoteframeworkclasses.processor.annotations.RemoteFrameworkClasses",
})
@AutoService(javax.annotation.processing.Processor.class)
public final class Processor extends AbstractProcessor {

    private static final ImmutableSet<String> FRAMEWORK_CLASSES =
            loadList("/apis/framework-classes.txt");

    private static final String PARENT_PROFILE_INSTANCE =
            "public android.app.admin.DevicePolicyManager getParentProfileInstance(android"
                    + ".content.ComponentName)";
    private static final String GET_CONTENT_RESOLVER =
            "public android.content.ContentResolver getContentResolver()";
    private static final String GET_ADAPTER =
            "public android.bluetooth.BluetoothAdapter getAdapter()";
    private static final String GET_DEFAULT_ADAPTER =
            "public static android.bluetooth.BluetoothAdapter getDefaultAdapter()";

    private static final ImmutableSet<String> BLOCKLISTED_TYPES =
            loadList("/apis/type-blocklist.txt");
    private static final ImmutableSet<String> ALLOWLISTED_METHODS =
            loadList("/apis/allowlisted-methods.txt");

    /** A set of all classes listed in test-current.txt. */
    static final ImmutableSet<ClassSignature> CLASSES_LISTED_IN_TEST_CURRENT_FILE =
            loadClassesListedInTestCurrentFile();

    /**
     * The TestApisReflection module generates proxy classes used to access TestApi classes and
     * methods through reflection. These proxy classes are then processed like other framework
     * classes in this processor.
     */
    static final String TEST_APIS_REFLECTION_PACKAGE = "android.cts.testapisreflection";
    private static final String TEST_APIS_REFLECTION_FILE =
            TEST_APIS_REFLECTION_PACKAGE + ".TestApisReflectionKt";

    private static final ClassName NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableRemoteDevicePolicyManager");
    private static final ClassName NULL_PARCELABLE_REMOTE_CONTENT_RESOLVER_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableRemoteContentResolver");
    private static final ClassName NULL_PARCELABLE_REMOTE_BLUETOOTH_ADAPTER_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableRemoteBluetoothAdapter");

    // TODO(b/205562849): These only support passing null, which is fine for existing tests but
    //  will be misleading
    private static final ClassName NULL_PARCELABLE_ACTIVITY_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableActivity");
    private static final ClassName NULL_PARCELABLE_ACCOUNT_MANAGER_CALLBACK_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableAccountManagerCallback");
    private static final ClassName NULL_HANDLER_CALLBACK_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableHandler");

    private static final ClassName COMPONENT_NAME_CLASSNAME =
            ClassName.get("android.content", "ComponentName");

    private static final ClassName ACCOUNT_MANAGE_FUTURE_WRAPPER_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.remoteframeworkclasses", "AccountManagerFutureWrapper");

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {
        if (!roundEnv.getElementsAnnotatedWith(RemoteFrameworkClasses.class).isEmpty()) {
            Set<MethodSignature> allowListedMethods = ALLOWLISTED_METHODS.stream()
                    .map(i -> MethodSignature.forApiString(i, processingEnv.getTypeUtils(),
                            processingEnv.getElementUtils()))
                    .collect(Collectors.toUnmodifiableSet());

            for (String systemService : FRAMEWORK_CLASSES) {
                TypeElement typeElement =
                        processingEnv.getElementUtils().getTypeElement(systemService);
                generateRemoteSystemService(
                        typeElement, allowListedMethods, processingEnv.getElementUtils());
            }

            generateWrappers();
        }

        return true;
    }

    private void generateWrappers() {
        generateWrapper(NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME);
        generateWrapper(NULL_PARCELABLE_REMOTE_CONTENT_RESOLVER_CLASSNAME);
        generateWrapper(NULL_PARCELABLE_REMOTE_BLUETOOTH_ADAPTER_CLASSNAME);
        generateWrapper(NULL_PARCELABLE_ACTIVITY_CLASSNAME);
        generateWrapper(NULL_PARCELABLE_ACCOUNT_MANAGER_CALLBACK_CLASSNAME);
        generateWrapper(NULL_HANDLER_CALLBACK_CLASSNAME);
    }

    private void generateWrapper(ClassName className) {
        String contents = null;
        try {
            URL url = Processor.class.getResource(
                    "/parcelablewrappers/" + className.simpleName() + ".java.txt");
            contents = Resources.toString(url, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not parse wrapper " + className, e);
        }

        JavaFileObject builderFile;
        try {
            builderFile = processingEnv.getFiler()
                    .createSourceFile(className.packageName() + "." + className.simpleName());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not write parcelablewrapper for " + className, e);
        }

        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            out.write(contents);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not write parcelablewrapper for " + className, e);
        }
    }

    private void generateRemoteSystemService(
            TypeElement frameworkClass,
            Set<MethodSignature> allowListedMethods,
            Elements elements) {
        Set<Api> apis =
                filterMethods(
                                frameworkClass,
                                getMethods(frameworkClass, processingEnv.getElementUtils()),
                                Apis.forClass(
                                        frameworkClass.getQualifiedName().toString(),
                                        processingEnv.getTypeUtils(),
                                        processingEnv.getElementUtils()),
                                elements)
                        .stream()
                        .filter(api -> !usesBlocklistedType(api, allowListedMethods, elements))
                        .filter(api -> !parametersHaveWildcards(api.method))
                        .collect(Collectors.toSet());

        generateFrameworkInterface(frameworkClass, apis);
        generateFrameworkImpl(frameworkClass, apis);

        if (frameworkClass.getSimpleName().contentEquals("DevicePolicyManager")) {
            // Special case, we need to support the .getParentProfileInstance method
            generateDpmParent(frameworkClass, apis);
        }
    }

    private static String removeTypeArguments(TypeMirror type) {
        if (type instanceof DeclaredType) {
            return ((DeclaredType) type).asElement().asType().toString().split("<", 2)[0];
        }
        return type.toString();
    }

    public static List<TypeMirror> extractTypeArguments(TypeMirror type) {
        if (!(type instanceof DeclaredType)) {
            return new ArrayList<>();
        }

        return new ArrayList<>(((DeclaredType) type).getTypeArguments());
    }

    private boolean hasWildcard(TypeMirror type) {
        if (type.getKind() == TypeKind.WILDCARD) {
            return true;
        }
        if (type.getKind() == TypeKind.DECLARED) {
            DeclaredType declaredtype = (DeclaredType) type;

            List<? extends TypeMirror> typeArguments = declaredtype.getTypeArguments();

            for (TypeMirror arg : typeArguments) {
                return hasWildcard(arg);
            }
        }

        return false;
    }

    private boolean parametersHaveWildcards(ExecutableElement method) {
        // getSystemServiceName returns a Class<?> which still works
        // with @CrossUser and is used.
        if (method.getSimpleName().toString().equals("getSystemServiceName")) {
            return false;
        }

        List<? extends VariableElement> parameters = method.getParameters();

        for (VariableElement parameter : parameters) {
            if (hasWildcard(parameter.asType())) {
                return true;
            }
        }

        return false;
    }

    private boolean isBlocklistedType(TypeMirror typeMirror) {
        if (BLOCKLISTED_TYPES.contains(removeTypeArguments(typeMirror))) {
            return true;
        }

        for (TypeMirror t : extractTypeArguments(typeMirror)) {
            if (isBlocklistedType(t)) {
                return true;
            }
        }

        return false;
    }

    private boolean usesBlocklistedType(Api api, Set<MethodSignature> allowListedMethods,
            Elements elements) {
        ExecutableElement method = api.method;
        if (allowListedMethods.contains(MethodSignature.forMethod(method, elements))) {
            return false; // Special case hacked in methods
        }

        if (isBlocklistedType(method.getReturnType())) {
            return true;
        }

        for (int i = 0; i < method.getParameters().size(); i++) {
            if (i == 0 && api.isTestApi) {
                // if it is a TestApi, ignore the first parameter as that is the kotlin
                // extension receiver parameter.
                continue;
            }
            if (isBlocklistedType(method.getParameters().get(i).asType())) {
                return true;
            }
        }

        for (TypeMirror exception : method.getThrownTypes()) {
            if (isBlocklistedType(exception)) {
                return true;
            }
        }

        return false;
    }

    private void generateFrameworkInterface(TypeElement frameworkClass, Set<Api> apis) {
        MethodSignature parentProfileInstanceSignature =
                MethodSignature.forApiString(PARENT_PROFILE_INSTANCE, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getContentResolverSignature =
                MethodSignature.forApiString(GET_CONTENT_RESOLVER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getAdapterSignature =
                MethodSignature.forApiString(GET_ADAPTER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getDefaultAdapterSignature =
                MethodSignature.forApiString(GET_DEFAULT_ADAPTER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());

        Map<MethodSignature, ClassName> signatureReturnOverrides = new HashMap<>();
        signatureReturnOverrides.put(parentProfileInstanceSignature,
                ClassName.get("android.app.admin", "RemoteDevicePolicyManager"));
        signatureReturnOverrides.put(getContentResolverSignature,
                ClassName.get("android.content", "RemoteContentResolver"));
        signatureReturnOverrides.put(getAdapterSignature,
                ClassName.get("android.bluetooth", "RemoteBluetoothAdapter"));
        signatureReturnOverrides.put(getDefaultAdapterSignature,
                ClassName.get("android.bluetooth", "RemoteBluetoothAdapter"));

        String packageName = frameworkClass.getEnclosingElement().toString();
        ClassName className = ClassName.get(packageName,
                "Remote" + frameworkClass.getSimpleName().toString());
        ClassName implClassName = ClassName.get(packageName,
                "Remote" + frameworkClass.getSimpleName().toString() + "Impl");
        TypeSpec.Builder classBuilder =
                TypeSpec.interfaceBuilder(className)
                        .addModifiers(Modifier.PUBLIC);

        classBuilder.addJavadoc("Public, test, and system interface for {@link $T}.\n\n",
                frameworkClass);
        classBuilder.addJavadoc("<p>All methods are annotated {@link $T} for compatibility with the"
                + " Connected Apps SDK.\n\n", CrossUser.class);
        classBuilder.addJavadoc("<p>For implementation see {@link $T}.\n", implClassName);


        classBuilder
                .addAnnotation(
                        AnnotationSpec.builder(Generated.class)
                                .addMember("value", "$S", Processor.class.getName())
                                .build())
                .addAnnotation(AnnotationSpec.builder(CrossUser.class)
                        .addMember("parcelableWrappers",
                                "{$T.class, $T.class, $T.class, $T.class, $T.class, $T.class}",
                                NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME,
                                NULL_PARCELABLE_REMOTE_CONTENT_RESOLVER_CLASSNAME,
                                NULL_PARCELABLE_REMOTE_BLUETOOTH_ADAPTER_CLASSNAME,
                                NULL_PARCELABLE_ACTIVITY_CLASSNAME,
                                NULL_PARCELABLE_ACCOUNT_MANAGER_CALLBACK_CLASSNAME,
                                NULL_HANDLER_CALLBACK_CLASSNAME)
                        .addMember("futureWrappers", "$T.class",
                                ACCOUNT_MANAGE_FUTURE_WRAPPER_CLASSNAME)
                        .build());

        for (Api api : apis) {
            ExecutableElement method = api.method;

            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .addAnnotation(CrossUser.class);

            MethodSignature signature = MethodSignature.forMethod(method,
                    processingEnv.getElementUtils());

            if (signatureReturnOverrides.containsKey(signature)) {
                methodBuilder.returns(signatureReturnOverrides.get(signature));
            }

            methodBuilder.addJavadoc("See {@link $T#$L}.",
                    ClassName.get(frameworkClass.asType()), method.getSimpleName());

            for (TypeMirror thrownType : method.getThrownTypes()) {
                methodBuilder.addException(ClassName.get(thrownType));
            }

            List<? extends VariableElement> parameters;
            if (api.isTestApi) {
                // This is a kotlin extension method. Kotlin extension methods when converted to
                // java code have the receiver as the first argument. We need to drop this argument.
                parameters = method.getParameters().subList(1, method.getParameters().size());
            } else {
                parameters = method.getParameters();
            }

            for (VariableElement param : parameters) {
                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()),
                                param.getSimpleName().toString()).build();

                methodBuilder.addParameter(parameterSpec);
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(packageName, classBuilder.build());
    }

    private void generateDpmParent(TypeElement frameworkClass, Set<Api> apis) {
        MethodSignature parentProfileInstanceSignature = MethodSignature.forApiString(
                PARENT_PROFILE_INSTANCE, processingEnv.getTypeUtils(),
                processingEnv.getElementUtils());
        String packageName = frameworkClass.getEnclosingElement().toString();
        ClassName className =
                ClassName.get(packageName, "Remote" + frameworkClass.getSimpleName() + "Parent");
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(className).addModifiers(Modifier.FINAL, Modifier.PUBLIC);

        classBuilder.addAnnotation(AnnotationSpec.builder(CrossUser.class)
                .addMember("parcelableWrappers",
                        "{$T.class, $T.class, $T.class, $T.class, $T.class, $T.class}",
                        NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME,
                        NULL_PARCELABLE_REMOTE_CONTENT_RESOLVER_CLASSNAME,
                        NULL_PARCELABLE_REMOTE_BLUETOOTH_ADAPTER_CLASSNAME,
                        NULL_PARCELABLE_ACTIVITY_CLASSNAME,
                        NULL_PARCELABLE_ACCOUNT_MANAGER_CALLBACK_CLASSNAME,
                        NULL_HANDLER_CALLBACK_CLASSNAME)
                .build());

        classBuilder.addField(ClassName.get(frameworkClass),
                "mFrameworkClass", Modifier.PRIVATE, Modifier.FINAL);

        classBuilder.addMethod(
                MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ClassName.get(frameworkClass), "frameworkClass")
                        .addCode("mFrameworkClass = frameworkClass;")
                        .build()
        );

        for (Api api : apis) {
            ExecutableElement method = api.method;

            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(CrossUser.class);

            MethodSignature signature = MethodSignature.forMethod(method,
                    processingEnv.getElementUtils());

            for (TypeMirror thrownType : method.getThrownTypes()) {
                methodBuilder.addException(ClassName.get(thrownType));
            }

            methodBuilder.addParameter(COMPONENT_NAME_CLASSNAME, "profileOwnerComponentName");

            List<? extends VariableElement> parameters;
            if (api.isTestApi) {
                // This is a kotlin extension method. Kotlin extension methods when converted to
                // java code have the receiver as the first argument. We need to drop this argument.
                parameters = method.getParameters().subList(1, method.getParameters().size());
            } else {
                parameters = method.getParameters();
            }

            List<String> paramNames = new ArrayList<>(parameters.size());
            for (VariableElement param : parameters) {
                String paramName = param.getSimpleName().toString();
                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()), paramName).build();

                paramNames.add(paramName);

                methodBuilder.addParameter(parameterSpec);
            }

            if (signature.equals(parentProfileInstanceSignature)) {
                // Special case, we want to return a RemoteDevicePolicyManager instead
                methodBuilder.returns(ClassName.get(
                        "android.app.admin", "RemoteDevicePolicyManager"));
                methodBuilder.addStatement(
                        "mFrameworkClass.getParentProfileInstance(profileOwnerComponentName).$L"
                                + "($L)",
                        method.getSimpleName(), String.join(", ", paramNames));
                methodBuilder.addStatement("throw new $T($S)", UnsupportedOperationException.class,
                        "TestApp does not support calling .getParentProfileInstance() on a parent"
                                + ".");
            } else if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                if (api.isTestApi) {
                    if (paramNames.isEmpty()) {
                        methodBuilder.addStatement(
                                "$L.$L(mFrameworkClass.getParentProfileInstance(profileOwnerComponentName))",
                                TEST_APIS_REFLECTION_FILE,
                                method.getSimpleName());
                    } else {
                        methodBuilder.addStatement(
                                "$L.$L(mFrameworkClass.getParentProfileInstance(profileOwnerComponentName),"
                                    + " $L)",
                                TEST_APIS_REFLECTION_FILE,
                                method.getSimpleName(),
                                String.join(", ", paramNames));
                    }
                } else {
                    methodBuilder.addStatement(
                            "mFrameworkClass.getParentProfileInstance(profileOwnerComponentName).$L($L)",
                            method.getSimpleName(),
                            String.join(", ", paramNames));
                }
            } else {
                if (api.isTestApi) {
                    if (paramNames.isEmpty()) {
                        methodBuilder.addStatement(
                                "return"
                                    + " $L.$L(mFrameworkClass.getParentProfileInstance(profileOwnerComponentName))",
                                TEST_APIS_REFLECTION_FILE,
                                method.getSimpleName());
                    } else {
                        methodBuilder.addStatement(
                                "return"
                                    + " $L.$L(mFrameworkClass.getParentProfileInstance(profileOwnerComponentName),"
                                    + " $L)",
                                TEST_APIS_REFLECTION_FILE,
                                method.getSimpleName(),
                                String.join(", ", paramNames));
                    }
                } else {
                    methodBuilder.addStatement(
                            "return mFrameworkClass.getParentProfileInstance"
                                    + "(profileOwnerComponentName).$L($L)",
                            method.getSimpleName(), String.join(", ", paramNames));
                }
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(packageName, classBuilder.build());
    }

    private void generateFrameworkImpl(TypeElement frameworkClass, Set<Api> apis) {
        MethodSignature parentProfileInstanceSignature =
                MethodSignature.forApiString(PARENT_PROFILE_INSTANCE, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getContentResolverSignature =
                MethodSignature.forApiString(GET_CONTENT_RESOLVER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getAdapterSignature =
                MethodSignature.forApiString(GET_ADAPTER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getDefaultAdapterSignature =
                MethodSignature.forApiString(GET_DEFAULT_ADAPTER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());

        Map<MethodSignature, ClassName> signatureReturnOverrides = new HashMap<>();
        signatureReturnOverrides.put(parentProfileInstanceSignature,
                ClassName.get("android.app.admin", "RemoteDevicePolicyManager"));
        signatureReturnOverrides.put(getContentResolverSignature,
                ClassName.get("android.content", "RemoteContentResolver"));
        signatureReturnOverrides.put(getAdapterSignature,
                ClassName.get("android.bluetooth", "RemoteBluetoothAdapter"));
        signatureReturnOverrides.put(getDefaultAdapterSignature,
                ClassName.get("android.bluetooth", "RemoteBluetoothAdapter"));

        String packageName = frameworkClass.getEnclosingElement().toString();
        ClassName interfaceClassName = ClassName.get(packageName,
                "Remote" + frameworkClass.getSimpleName().toString());
        ClassName className = ClassName.get(packageName,
                "Remote" + frameworkClass.getSimpleName().toString() + "Impl");
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                                className)
                        .addSuperinterface(interfaceClassName)
                        .addModifiers(Modifier.PUBLIC);

        classBuilder.addAnnotation(
                        AnnotationSpec.builder(Generated.class)
                                .addMember("value", "$S", Processor.class.getName())
                                .build())
                .addAnnotation(
                        AnnotationSpec.builder(SuppressWarnings.class)
                                .addMember("value", "$S", "CheckSignatures")
                                .build());

        classBuilder.addField(ClassName.get(frameworkClass),
                "mFrameworkClass", Modifier.PRIVATE, Modifier.FINAL);

        classBuilder.addMethod(
                MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ClassName.get(frameworkClass), "frameworkClass")
                        .addCode("mFrameworkClass = frameworkClass;")
                        .build()
        );

        for (Api api : apis) {
            ExecutableElement method = api.method;

            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class);

            MethodSignature signature = MethodSignature.forMethod(method,
                    processingEnv.getElementUtils());

            for (TypeMirror thrownType : method.getThrownTypes()) {
                methodBuilder.addException(ClassName.get(thrownType));
            }

            List<? extends VariableElement> parameters;
            if (api.isTestApi) {
                // This is a kotlin extension method. Kotlin extension methods when converted to
                // java code have the receiver as the first argument. We need to drop this argument.
                parameters = method.getParameters().subList(1, method.getParameters().size());
            } else {
                parameters = method.getParameters();
            }

            List<String> paramNames = new ArrayList<>(parameters.size());
            for (VariableElement param : parameters) {
                String paramName = param.getSimpleName().toString();

                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()), paramName).build();

                paramNames.add(paramName);

                methodBuilder.addParameter(parameterSpec);
            }

            String frameworkClassName = "mFrameworkClass";

            if (method.getModifiers().contains(Modifier.STATIC)) {
                frameworkClassName = frameworkClass.getQualifiedName().toString();
            }

            if (signatureReturnOverrides.containsKey(signature)) {
                methodBuilder.returns(signatureReturnOverrides.get(signature));

                ClassName iClassName = signatureReturnOverrides.get(signature);
                ClassName implClassName =
                        ClassName.get(iClassName.packageName(), iClassName.simpleName() + "Impl");

                methodBuilder.addStatement(
                        "$1T ret = new $1T($2L.$3L($4L))",
                        implClassName, frameworkClassName,
                        method.getSimpleName(), String.join(", ", paramNames));
                // We assume all replacements are null-only
                methodBuilder.addStatement("return null");
            } else if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                if (api.isTestApi) {
                    if (paramNames.isEmpty()) {
                        methodBuilder.addStatement(
                                "$L.$L($L)",
                                TEST_APIS_REFLECTION_FILE, method.getSimpleName(),
                                "mFrameworkClass");
                    } else {
                        methodBuilder.addStatement(
                                "$L.$L($L, $L)",
                                TEST_APIS_REFLECTION_FILE, method.getSimpleName(),
                                "mFrameworkClass",
                                String.join(", ", paramNames));
                    }
                } else {
                    methodBuilder.addStatement(
                            "$L.$L($L)",
                            frameworkClassName, method.getSimpleName(),
                            String.join(", ", paramNames));
                }
            } else {
                if (api.isTestApi) {
                    if (paramNames.isEmpty()) {
                        methodBuilder.addStatement(
                                "return $L.$L($L)",
                                TEST_APIS_REFLECTION_FILE, method.getSimpleName(),
                                "mFrameworkClass");
                    } else {
                        methodBuilder.addStatement(
                                "return $L.$L($L, $L)",
                                TEST_APIS_REFLECTION_FILE, method.getSimpleName(),
                                "mFrameworkClass",
                                String.join(", ", paramNames));
                    }
                } else {
                    methodBuilder.addStatement(
                            "return $L.$L($L)",
                            frameworkClassName, method.getSimpleName(),
                            String.join(", ", paramNames));
                }
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(packageName, classBuilder.build());
    }

    private Set<Api> filterMethods(TypeElement frameworkClass,
            Set<ExecutableElement> allMethods, Apis validApis, Elements elements) {
        Set<Api> filteredMethods = new HashSet<>();

        for (ExecutableElement method : allMethods) {
            MethodSignature methodSignature = MethodSignature.forMethod(method, elements);
            if (validApis.methods().contains(methodSignature)) {
                if (method.getModifiers().contains(Modifier.PROTECTED)) {
                    System.out.println(methodSignature + " is protected. Dropping");
                } else {
                    filteredMethods.add(new Api(method, /* isTestApi= */ false));
                }
            } else {
                System.out.println("No matching public API for " + methodSignature);
            }
        }

        filterValidTestApis(filteredMethods, frameworkClass, elements);

        return filteredMethods;
    }

    private void filterValidTestApis(Set<Api> filteredMethods, TypeElement frameworkClass,
            Elements elements) {
        Set<ExecutableElement> testMethods = new HashSet<>();
        TypeElement testApisReflectionTypeElement =
                processingEnv.getElementUtils().getTypeElement(TEST_APIS_REFLECTION_FILE);

        testApisReflectionTypeElement.getEnclosedElements().stream()
                .filter(e -> e instanceof ExecutableElement)
                .map(e -> (ExecutableElement) e)
                .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
                .forEach(e -> testMethods.add(e));

        for (ExecutableElement method : testMethods) {
            MethodSignature methodSignature = MethodSignature.forMethod(method, elements);

            if (!methodSignature.mParameterTypes.get(0).equals(
                    frameworkClass.getQualifiedName().toString())) {
                continue;
            }

            Api testApi = new Api(method, /* isTestApi= */ true);
            if (filteredMethods.contains(testApi)) {
                System.out.println("Api " + methodSignature.getName() + " is already added, "
                        + "probably because it is marked as another type of Api as well.");
                continue;
            }

            filteredMethods.add(testApi);
        }
    }

    private void writeClassToFile(String packageName, TypeSpec clazz) {
        String qualifiedClassName =
                packageName.isEmpty() ? clazz.name : packageName + "." + clazz.name;

        JavaFile javaFile = JavaFile.builder(packageName, clazz).build();
        try {
            JavaFileObject builderFile =
                    processingEnv.getFiler().createSourceFile(qualifiedClassName);
            try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
                javaFile.writeTo(out);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error writing " + qualifiedClassName + " to file", e);
        }
    }

    private Set<ExecutableElement> getMethods(TypeElement interfaceClass, Elements elements) {
        Map<String, ExecutableElement> methods = new HashMap<>();
        getMethods(methods, interfaceClass, elements);
        return new HashSet<>(methods.values());
    }

    private void getMethods(Map<String, ExecutableElement> methods, TypeElement interfaceClass,
            Elements elements) {

        interfaceClass.getEnclosedElements().stream()
                .filter(e -> e instanceof ExecutableElement)
                .map(e -> (ExecutableElement) e)
                .filter(e -> !methods.containsKey(e.getSimpleName().toString()))
                .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
                .forEach(e -> methods.put(methodHash(e), e));

        interfaceClass.getInterfaces().stream()
                .map(m -> elements.getTypeElement(m.toString()))
                .forEach(m -> getMethods(methods, m, elements));


        TypeElement superclassElement = (TypeElement) processingEnv.getTypeUtils()
                .asElement(interfaceClass.getSuperclass());

        if (superclassElement != null) {
            getMethods(methods, superclassElement, elements);
        }
    }

    private String methodHash(ExecutableElement method) {
        return method.getSimpleName() + "(" + method.getParameters().stream()
                .map(p -> p.asType().toString()).collect(
                        Collectors.joining(",")) + ")";
    }

    private static ImmutableSet<String> loadList(String filename) {
        try {
            return ImmutableSet.copyOf(Resources.toString(
                    Processor.class.getResource(filename),
                    StandardCharsets.UTF_8).split("\n"));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read file", e);
        }
    }

    private static ImmutableSet<ClassSignature> loadClassesListedInTestCurrentFile() {
        return ImmutableSet.copyOf(TestApisParser.parse().stream()
                .flatMap(p -> p.getClassSignatures().stream())
                .collect(Collectors.toSet()));
    }

    private static class Api {
        private final ExecutableElement method;
        private final boolean isTestApi;

        private Api(ExecutableElement method, boolean isTestApi) {
            this.method = method;
            this.isTestApi = isTestApi;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Api)) return false;
            Api that = (Api) o;
            if (Objects.equals(method.getSimpleName(), that.method.getSimpleName())) {
                if (isTestApi) {
                    // when comparing a TestApi with a non-TestApi we need to ignore the first
                    // parameter in the TestApi as that parameter is the kotlin extension receiver
                    // parameter
                    if (method.getParameters().size() == that.method.getParameters().size() + 1) {
                        for (int i = 1; i < method.getParameters().size(); i++) {
                            String thisIthParameter = method.getParameters().get(i).asType()
                                    .toString();
                            String thatIthParameter = that.method.getParameters().get(i - 1)
                                    .asType().toString();
                            if (!thisIthParameter.equals(thatIthParameter)) {
                                return false;
                            }
                        }
                        return true;
                    }
                } else {
                    return method.getParameters().equals(that.method.getParameters());
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            StringBuilder params = new StringBuilder();
            int index = 0;
            if (isTestApi) {
                // if it is a TestApi we need to ignore the first
                // parameter in the TestApi as that is the kotlin extension receiver
                // parameter
                index = 1;
            }
            for (int i = index; i < method.getParameters().size(); i++) {
                params.append(method.getParameters().get(i).asType().toString());
            }

            return Objects.hash(method.getSimpleName().toString(), params.toString());
        }
    }
}