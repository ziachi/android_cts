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
package android.app.appsearch.cts.appindexer;

import static android.app.appsearch.testutil.AppFunctionConstants.APP_A_DYNAMIC_SCHEMA_FEWER_TYPES_PRINT_APP_FUNCTION;
import static android.app.appsearch.testutil.AppFunctionConstants.APP_A_DYNAMIC_SCHEMA_MULTIPLE_ROOT_SCHEMAS_COMMON_SCHEMA_METADATA;
import static android.app.appsearch.testutil.AppFunctionConstants.APP_A_DYNAMIC_SCHEMA_MULTIPLE_ROOT_SCHEMAS_PRINT_APP_FUNCTION;
import static android.app.appsearch.testutil.AppFunctionConstants.APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION;
import static android.app.appsearch.testutil.AppFunctionConstants.APP_A_V2_PRINT_APP_FUNCTION;
import static android.app.appsearch.testutil.AppFunctionConstants.APP_B_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION;
import static android.app.appsearch.testutil.AppFunctionConstants.APP_B_PRINT_APP_FUNCTION;

import static com.google.common.truth.Truth.assertThat;

import android.app.appsearch.GenericDocument;
import android.app.appsearch.GlobalSearchSessionShim;
import android.app.appsearch.SearchResult;
import android.app.appsearch.SearchResultsShim;
import android.app.appsearch.SearchSpec;
import android.app.appsearch.testutil.AppSearchTestUtils;
import android.app.appsearch.testutil.GlobalSearchSessionShimImpl;
import android.content.Context;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SdkSuppress;

import com.android.appsearch.flags.Flags;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RequiresFlagsEnabled(Flags.FLAG_APPS_INDEXER_ENABLED)
public class AppIndexerCtsTest {
    public static final String INDEXER_PACKAGE_NAME = "android";

    @Rule public final RuleChain mRuleChain = AppSearchTestUtils.createCommonTestRules();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private static final String TEST_APP_ROOT_FOLDER = "/data/local/tmp/cts/appsearch/";
    private static final String TEST_APP_A_V1_PATH =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV1.apk";
    private static final String TEST_APP_A_V2_PATH =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV2.apk";
    private static final String TEST_APP_A_V3_PATH =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppAV3.apk";
    private static final String TEST_APP_B_V1_PATH =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppBV1.apk";
    private static final String TEST_APP_A_DYNAMIC_SCHEMA_PATH =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppADynamicSchema.apk";
    private static final String TEST_APP_A_DYNAMIC_SCHEMA_FEWER_TYPES_PATH =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppADynamicSchemaFewerTypes.apk";
    private static final String TEST_APP_A_DYNAMIC_SCHEMA_MULTIPLE_ROOT_SCHEMAS_PATH =
            TEST_APP_ROOT_FOLDER
                    + "CtsAppSearchIndexerTestAppADynamicSchemaMultipleRootSchemas.apk";
    private static final String TEST_APP_B_DYNAMIC_SCHEMA_PATH =
            TEST_APP_ROOT_FOLDER + "CtsAppSearchIndexerTestAppBDynamicSchema.apk";
    private static final String TEST_APP_A_PKG = "com.android.cts.appsearch.indexertestapp.a";
    private static final String TEST_APP_B_PKG = "com.android.cts.appsearch.indexertestapp.b";
    private static final String NAMESPACE_MOBILE_APPLICATION = "apps";
    private static final String NAMESPACE_APP_FUNCTIONS = "app_functions";
    private static final String APP_PROPERTY_DISPLAY_NAME = "displayName";
    private static final String PROPERTY_FUNCTION_ID = "functionId";
    private static final String PROPERTY_PACKAGE_NAME = "packageName";
    private static final String PROPERTY_SCHEMA_NAME = "schemaName";
    private static final String PROPERTY_SCHEMA_VERSION = "schemaVersion";
    private static final String PROPERTY_SCHEMA_CATEGORY = "schemaCategory";
    private static final String PROPERTY_DISPLAY_NAME_STRING_RES = "displayNameStringRes";
    private static final String PROPERTY_ENABLED_BY_DEFAULT = "enabledByDefault";
    private static final String PROPERTY_RESTRICT_CALLERS_WITH_EXECUTE_APP_FUNCTIONS =
            "restrictCallersWithExecuteAppFunctions";

    private static final long RETRY_CHECK_INTERVAL_MILLIS = 500;
    private static final long RETRY_MAX_INTERVALS = 10;

    @Before
    @After
    public void uninstallTestApks() throws Throwable {
        uninstallPackage(TEST_APP_A_PKG);
        uninstallPackage(TEST_APP_B_PKG);

        retryAssert(
                () -> {
                    assertThat(searchMobileApplicationWithId(TEST_APP_A_PKG)).isNull();
                    assertThat(searchMobileApplicationWithId(TEST_APP_B_PKG)).isNull();
                });
    }

    @Test
    public void indexMobileApplications_packageChanges() throws Throwable {
        {
            // Install a new app
            installPackage(TEST_APP_A_V1_PATH);

            retryAssert(
                    () -> {
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_A_PKG);
                        assertThat(mobileApplication).isNotNull();
                        assertThat(mobileApplication.getPropertyString(APP_PROPERTY_DISPLAY_NAME))
                                .isEqualTo("App A [v1]");
                    });
        }

        {
            // Update it
            installPackage(TEST_APP_A_V2_PATH);

            retryAssert(
                    () -> {
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_A_PKG);
                        assertThat(mobileApplication).isNotNull();
                        assertThat(mobileApplication.getPropertyString(APP_PROPERTY_DISPLAY_NAME))
                                .isEqualTo("App A [v2]");
                    });
        }

        {
            // Uninstall it
            uninstallPackage(TEST_APP_A_PKG);

            retryAssert(
                    () -> {
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_A_PKG);
                        assertThat(mobileApplication).isNull();
                    });
        }
    }

    @Test
    public void indexAppFunctions_packageChanges() throws Throwable {
        {
            // Install A V1 which does not have app functions.
            installPackage(TEST_APP_A_V1_PATH);

            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                        assertThat(appFunctions).isEmpty();
                    });
        }

        {
            // Update to v2 which has one app function
            installPackage(TEST_APP_A_V2_PATH);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                        List<String> functionIds = new ArrayList<>();
                        for (int i = 0; i < appFunctions.size(); i++) {
                            functionIds.add(
                                    appFunctions.get(i).getPropertyString(PROPERTY_FUNCTION_ID));
                        }
                        assertThat(functionIds).containsExactly("com.example.utils#print1");
                    });
        }

        {
            // Update to v3 which no longer has print1 but has print2 and print3.
            installPackage(TEST_APP_A_V3_PATH);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                        List<String> functionIds = new ArrayList<>();
                        for (int i = 0; i < appFunctions.size(); i++) {
                            functionIds.add(
                                    appFunctions.get(i).getPropertyString(PROPERTY_FUNCTION_ID));
                        }
                        assertThat(functionIds)
                                .containsExactly(
                                        "com.example.utils#print2", "com.example.utils#print3");
                    });
        }

        {
            // Uninstall package A
            uninstallPackage(TEST_APP_A_PKG);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                        List<String> functionIds = new ArrayList<>();
                        for (int i = 0; i < appFunctions.size(); i++) {
                            functionIds.add(
                                    appFunctions.get(i).getPropertyString(PROPERTY_FUNCTION_ID));
                        }
                        assertThat(functionIds).isEmpty();
                    });
        }
    }

    @Test
    public void indexAppFunctions_fullXml() throws Throwable {
        // The XML in A v2 has the full XML which specifies all the properties. Here we verify
        // all the properties are being indexed properly.
        installPackage(TEST_APP_A_V2_PATH);
        retryAssert(
                () -> {
                    List<GenericDocument> appFunctions =
                            searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                    assertThat(appFunctions).hasSize(1);
                    GenericDocument appFunction = appFunctions.get(0);
                    assertThat(appFunction.getPropertyString(PROPERTY_FUNCTION_ID))
                            .isEqualTo("com.example.utils#print1");
                    assertThat(appFunction.getPropertyString(PROPERTY_PACKAGE_NAME))
                            .isEqualTo(TEST_APP_A_PKG);
                    assertThat(appFunction.getPropertyBoolean(PROPERTY_ENABLED_BY_DEFAULT))
                            .isEqualTo(false);
                    assertThat(appFunction.getPropertyString(PROPERTY_SCHEMA_NAME))
                            .isEqualTo("print");
                    assertThat(appFunction.getPropertyString(PROPERTY_SCHEMA_CATEGORY))
                            .isEqualTo("utils");
                    assertThat(appFunction.getPropertyLong(PROPERTY_SCHEMA_VERSION)).isEqualTo(1);
                    assertThat(
                                    appFunction.getPropertyBoolean(
                                            PROPERTY_RESTRICT_CALLERS_WITH_EXECUTE_APP_FUNCTIONS))
                            .isEqualTo(true);
                    assertThat(appFunction.getPropertyLong(PROPERTY_DISPLAY_NAME_STRING_RES))
                            .isEqualTo(10);
                });
    }

    @Test
    public void indexAppFunctions_defaultValue() throws Throwable {
        // The XML in B V1 only have functionId, schema_name, schema_version and schema_category.
        // Here, we check the default value of the optional properties are set properly.
        installPackage(TEST_APP_B_V1_PATH);
        retryAssert(
                () -> {
                    List<GenericDocument> appFunctions =
                            searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                    assertThat(appFunctions).hasSize(1);
                    GenericDocument appFunction = appFunctions.get(0);
                    assertThat(appFunction.getPropertyString(PROPERTY_FUNCTION_ID))
                            .isEqualTo("com.example.utils#print5");
                    assertThat(appFunction.getPropertyBoolean(PROPERTY_ENABLED_BY_DEFAULT))
                            .isEqualTo(true);
                    assertThat(
                                    appFunction.getPropertyBoolean(
                                            PROPERTY_RESTRICT_CALLERS_WITH_EXECUTE_APP_FUNCTIONS))
                            .isEqualTo(false);
                });
    }

    @Test
    public void indexAppFunctions_installAppWithNoAppFunction_retainIndexedFunctions()
            throws Throwable {
        // Install the test app B V1 which has one app function. That function should be indexed.
        {
            installPackage(TEST_APP_B_V1_PATH);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                        assertThat(appFunctions).hasSize(1);
                        GenericDocument appFunction = appFunctions.get(0);
                        assertThat(appFunction.getPropertyString(PROPERTY_FUNCTION_ID))
                                .isEqualTo("com.example.utils#print5");
                    });
        }

        // Install test app A v1 which does not have any app function. The functions from B
        // should be retained.
        {
            installPackage(TEST_APP_A_V1_PATH);
            retryAssert(
                    () -> {
                        // Ensure the app A is indexed before checking if the function is retained.
                        // This prevents a false positive result if the indexer hasn't finished
                        // running yet.
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_A_PKG);
                        assertThat(mobileApplication).isNotNull();

                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                        assertThat(appFunctions).hasSize(1);
                        GenericDocument appFunction = appFunctions.get(0);
                        assertThat(appFunction.getPropertyString(PROPERTY_FUNCTION_ID))
                                .isEqualTo("com.example.utils#print5");
                    });
        }
    }

    @Test
    public void indexAppFunctionsFromTwoApps() throws Throwable {
        // Install the test app B V1 which has one app function. That function should be indexed.
        {
            installPackage(TEST_APP_B_V1_PATH);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                        assertThat(appFunctions).hasSize(1);
                        GenericDocument appFunction = appFunctions.get(0);
                        assertThat(appFunction.getPropertyString(PROPERTY_FUNCTION_ID))
                                .isEqualTo("com.example.utils#print5");
                    });
        }

        // Install test app A v2 which also has one app function. The function from B should be
        // retained and the new function from A should be indexed.
        {
            installPackage(TEST_APP_A_V2_PATH);
            retryAssert(
                    () -> {
                        List<GenericDocument> appFunctionsFromB =
                                searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                        assertThat(appFunctionsFromB).hasSize(1);
                        GenericDocument appFunctionFromB = appFunctionsFromB.get(0);
                        assertThat(appFunctionFromB.getPropertyString(PROPERTY_FUNCTION_ID))
                                .isEqualTo("com.example.utils#print5");

                        List<GenericDocument> appFunctionsFromA =
                                searchAppFunctionsWithPackageName(TEST_APP_A_PKG);
                        assertThat(appFunctionsFromA).hasSize(1);
                        GenericDocument appFunctionFromA = appFunctionsFromA.get(0);
                        assertThat(appFunctionFromA.getPropertyString(PROPERTY_FUNCTION_ID))
                                .isEqualTo("com.example.utils#print1");
                    });
        }
    }

    @Test
    public void indexMobileApplicationAndAppFunction_withoutLauncherIcon() throws Throwable {
        {
            // Install B V1 which does not have a launcher icon but have app functions.
            installPackage(TEST_APP_B_V1_PATH);

            retryAssert(
                    () -> {
                        // A MobileApplication for it should be inserted.
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_B_PKG);
                        assertThat(mobileApplication).isNotNull();
                        // Its app functions should be indexed.
                        List<GenericDocument> appFunctions =
                                searchAppFunctionsWithPackageName(TEST_APP_B_PKG);
                        List<String> functionIds = new ArrayList<>();
                        for (int i = 0; i < appFunctions.size(); i++) {
                            functionIds.add(
                                    appFunctions.get(i).getPropertyString(PROPERTY_FUNCTION_ID));
                        }
                        assertThat(functionIds).containsExactly("com.example.utils#print5");
                    });
        }
    }

    @RequiresFlagsDisabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @Test
    public void indexAppWithDynamicSchema_dynamicSchemasDisabled_indexesPredefinedSchemaFieldsOnly()
            throws Throwable {
        installPackage(TEST_APP_A_DYNAMIC_SCHEMA_PATH);

        // Retry till the indexer has completed a run.
        retryAssert(
                () -> {
                    // A MobileApplication for it should be inserted.
                    GenericDocument mobileApplication =
                            searchMobileApplicationWithId(TEST_APP_A_PKG);
                    assertThat(mobileApplication).isNotNull();
                });
        // Its app functions should be indexed.
        Map<String, GenericDocument> appFnMap = searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
        assertThat(appFnMap).hasSize(1);
        assertThat(
                        clearTimestampsAndParentTypesInDocument(
                                appFnMap.get(TEST_APP_A_PKG + "/com.example.utils#print1")))
                .isEqualTo(APP_A_V2_PRINT_APP_FUNCTION);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexAppWithDynamicSchema() throws Throwable {
        installPackage(TEST_APP_A_DYNAMIC_SCHEMA_PATH);

        // Retry till the indexer has completed a run.
        retryAssert(
                () -> {
                    // A MobileApplication for it should be inserted.
                    GenericDocument mobileApplication =
                            searchMobileApplicationWithId(TEST_APP_A_PKG);
                    assertThat(mobileApplication).isNotNull();
                });
        // Its app functions should be indexed.
        Map<String, GenericDocument> appFnMap = searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
        assertThat(appFnMap).hasSize(1);
        assertThat(
                        clearTimestampsAndParentTypesInDocument(
                                appFnMap.get(TEST_APP_A_PKG + "/com.example.utils#print1")))
                .isEqualTo(APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexAppWithDynamicSchema_multipleRootSchemas() throws Throwable {
        installPackage(TEST_APP_A_DYNAMIC_SCHEMA_MULTIPLE_ROOT_SCHEMAS_PATH);

        // Retry till the indexer has completed a run.
        retryAssert(
                () -> {
                    // A MobileApplication for it should be inserted.
                    GenericDocument mobileApplication =
                            searchMobileApplicationWithId(TEST_APP_A_PKG);
                    assertThat(mobileApplication).isNotNull();
                });
        // Its app functions should be indexed.
        Map<String, GenericDocument> appFnMap = searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
        assertThat(appFnMap).hasSize(2);
        assertThat(
                        clearTimestampsAndParentTypesInDocument(
                                appFnMap.get(TEST_APP_A_PKG + "/com.example.utils#print1")))
                .isEqualTo(APP_A_DYNAMIC_SCHEMA_MULTIPLE_ROOT_SCHEMAS_PRINT_APP_FUNCTION);
        assertThat(
                        clearTimestampsAndParentTypesInDocument(
                                appFnMap.get(
                                        TEST_APP_A_PKG + "/topLevelSchemaMetadata#commonSchema")))
                .isEqualTo(APP_A_DYNAMIC_SCHEMA_MULTIPLE_ROOT_SCHEMAS_COMMON_SCHEMA_METADATA);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexMultipleAppsWithDynamicSchema() throws Throwable {

        installPackage(TEST_APP_B_DYNAMIC_SCHEMA_PATH);
        installPackage(TEST_APP_A_DYNAMIC_SCHEMA_PATH);

        retryAssert(
                () -> {
                    Map<String, GenericDocument> appFnMapAppB =
                            searchAppFunctionDocumentsIntoMap(TEST_APP_B_PKG);
                    assertThat(appFnMapAppB).hasSize(1);
                    assertThat(
                                    clearTimestampsAndParentTypesInDocument(
                                            appFnMapAppB.get(
                                                    TEST_APP_B_PKG + "/com.example.utils#print1")))
                            .isEqualTo(APP_B_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
                });

        retryAssert(
                () -> {
                    Map<String, GenericDocument> appFnMapAppA =
                            searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                    assertThat(appFnMapAppA).hasSize(1);
                    assertThat(
                                    clearTimestampsAndParentTypesInDocument(
                                            appFnMapAppA.get(
                                                    TEST_APP_A_PKG + "/com.example.utils#print1")))
                            .isEqualTo(APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
                });
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexAppsWithAndWithoutDynamicSchema() throws Throwable {
        installPackage(TEST_APP_A_DYNAMIC_SCHEMA_PATH);
        installPackage(TEST_APP_B_V1_PATH);

        // Retry till the indexer has completed a run.
        retryAssert(
                () -> {
                    // A MobileApplication for it should be inserted.
                    assertThat(searchMobileApplicationWithId(TEST_APP_A_PKG)).isNotNull();
                    assertThat(searchMobileApplicationWithId(TEST_APP_B_PKG)).isNotNull();
                });
        // Verify dynamic schema app function.
        Map<String, GenericDocument> appFnMap = searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
        assertThat(
                        clearTimestampsAndParentTypesInDocument(
                                appFnMap.get(TEST_APP_A_PKG + "/com.example.utils#print1")))
                .isEqualTo(APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
        // Verify app B app function.
        appFnMap = searchAppFunctionDocumentsIntoMap(TEST_APP_B_PKG);
        assertThat(appFnMap).hasSize(1);
        assertThat(
                        clearTimestampsAndParentTypesInDocument(
                                appFnMap.get(TEST_APP_B_PKG + "/com.example.utils#print5")))
                .isEqualTo(APP_B_PRINT_APP_FUNCTION);
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexApp_updateToDynamicSchema() throws Throwable {
        {
            installPackage(TEST_APP_A_V2_PATH);

            // Retry till the indexer has completed a run.
            retryAssert(
                    () -> {
                        // Its app functions should be indexed.
                        Map<String, GenericDocument> appFnMap =
                                searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                        assertThat(appFnMap).hasSize(1);
                        assertThat(
                                        clearTimestampsAndParentTypesInDocument(
                                                appFnMap.get(
                                                        TEST_APP_A_PKG
                                                                + "/com.example.utils#print1")))
                                .isEqualTo(APP_A_V2_PRINT_APP_FUNCTION);
                    });
        }

        {
            installPackage(TEST_APP_A_DYNAMIC_SCHEMA_PATH);

            // Retry till the indexer has completed a run.
            retryAssert(
                    () -> {
                        // Its app functions should be indexed.
                        Map<String, GenericDocument> appFnMap =
                                searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                        assertThat(appFnMap).hasSize(1);
                        assertThat(
                                        clearTimestampsAndParentTypesInDocument(
                                                appFnMap.get(
                                                        TEST_APP_A_PKG
                                                                + "/com.example.utils#print1")))
                                .isEqualTo(APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
                    });
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexApp_updateToWithoutDynamicSchema() throws Throwable {
        {
            installPackage(TEST_APP_A_DYNAMIC_SCHEMA_PATH);

            // Retry till the indexer has completed a run.
            retryAssert(
                    () -> {
                        // A MobileApplication for it should be inserted.
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_A_PKG);
                        assertThat(mobileApplication).isNotNull();
                    });
            // Its app functions should be indexed.
            Map<String, GenericDocument> appFnMap =
                    searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
            assertThat(appFnMap).hasSize(1);
            assertThat(
                            clearTimestampsAndParentTypesInDocument(
                                    appFnMap.get(TEST_APP_A_PKG + "/com.example.utils#print1")))
                    .isEqualTo(APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
        }

        {
            installPackage(TEST_APP_A_V2_PATH);

            // Retry till the indexer has completed another run.
            retryAssert(
                    () -> {
                        Map<String, GenericDocument> appFnMap =
                                searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                        assertThat(appFnMap).hasSize(1);
                        assertThat(
                                        clearTimestampsAndParentTypesInDocument(
                                                appFnMap.get(
                                                        TEST_APP_A_PKG
                                                                + "/com.example.utils#print1")))
                                .isEqualTo(APP_A_V2_PRINT_APP_FUNCTION);
                    });
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexApp_updateToDynamicSchemaWithFewerTypes() throws Throwable {
        {
            installPackage(TEST_APP_A_DYNAMIC_SCHEMA_PATH);

            // Retry till the indexer has completed a run.
            retryAssert(
                    () -> {
                        // A MobileApplication for it should be inserted.
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_A_PKG);
                        assertThat(mobileApplication).isNotNull();
                    });
            // Its app functions should be indexed.
            Map<String, GenericDocument> appFnMap =
                    searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
            assertThat(appFnMap).hasSize(1);
            assertThat(
                            clearTimestampsAndParentTypesInDocument(
                                    appFnMap.get(TEST_APP_A_PKG + "/com.example.utils#print1")))
                    .isEqualTo(APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
        }

        {
            installPackage(TEST_APP_A_DYNAMIC_SCHEMA_FEWER_TYPES_PATH);

            // Retry till the indexer has completed another run.
            retryAssert(
                    () -> {
                        Map<String, GenericDocument> appFnMap =
                                searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                        assertThat(appFnMap).hasSize(1);
                        assertThat(
                                        clearTimestampsAndParentTypesInDocument(
                                                appFnMap.get(
                                                        TEST_APP_A_PKG
                                                                + "/com.example.utils#print1")))
                                .isEqualTo(APP_A_DYNAMIC_SCHEMA_FEWER_TYPES_PRINT_APP_FUNCTION);
                    });
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_APP_FUNCTIONS_SCHEMA_PARSER)
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Test
    public void indexApp_updateToDynamicSchemaWithMoreTypesThanBefore() throws Throwable {
        {
            installPackage(TEST_APP_A_DYNAMIC_SCHEMA_FEWER_TYPES_PATH);

            // Retry till the indexer has completed a run.
            retryAssert(
                    () -> {
                        // A MobileApplication for it should be inserted.
                        GenericDocument mobileApplication =
                                searchMobileApplicationWithId(TEST_APP_A_PKG);
                        assertThat(mobileApplication).isNotNull();
                    });
            // Its app functions should be indexed.
            Map<String, GenericDocument> appFnMap =
                    searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
            assertThat(appFnMap).hasSize(1);
            assertThat(
                            clearTimestampsAndParentTypesInDocument(
                                    appFnMap.get(TEST_APP_A_PKG + "/com.example.utils#print1")))
                    .isEqualTo(APP_A_DYNAMIC_SCHEMA_FEWER_TYPES_PRINT_APP_FUNCTION);
        }

        {
            installPackage(TEST_APP_A_DYNAMIC_SCHEMA_PATH);

            // Retry till the indexer has completed another run.
            retryAssert(
                    () -> {
                        Map<String, GenericDocument> appFnMap =
                                searchAppFunctionDocumentsIntoMap(TEST_APP_A_PKG);
                        assertThat(appFnMap).hasSize(1);
                        assertThat(
                                        clearTimestampsAndParentTypesInDocument(
                                                appFnMap.get(
                                                        TEST_APP_A_PKG
                                                                + "/com.example.utils#print1")))
                                .isEqualTo(APP_A_DYNAMIC_SCHEMA_PRINT_APP_FUNCTION);
                    });
        }
    }

    private GenericDocument clearTimestampsAndParentTypesInDocument(
            @NonNull GenericDocument document) {
        GenericDocument.Builder<?> builder =
                new GenericDocument.Builder<>(document)
                        .setCreationTimestampMillis(0)
                        // GenericDocument#PARENT_TYPES_SYNTHETIC_PROPERTY is hidden
                        .clearProperty("$$__AppSearch__parentTypes");

        for (String propertyName : document.getPropertyNames()) {
            Object property = document.getProperty(propertyName);
            if (property instanceof GenericDocument[] nestedDocuments) {
                GenericDocument[] clearedNestedDocuments =
                        new GenericDocument[nestedDocuments.length];

                for (int i = 0; i < nestedDocuments.length; i++) {
                    clearedNestedDocuments[i] =
                            clearTimestampsAndParentTypesInDocument(nestedDocuments[i]);
                }

                builder.setPropertyDocument(propertyName, clearedNestedDocuments);
            }
        }

        return builder.build();
    }

    private GenericDocument searchMobileApplicationWithId(String id)
            throws ExecutionException, InterruptedException {
        GlobalSearchSessionShim globalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync().get();

        SearchResultsShim searchResults =
                globalSearchSession.search(
                        "",
                        new SearchSpec.Builder()
                                .addFilterNamespaces(NAMESPACE_MOBILE_APPLICATION)
                                .addFilterPackageNames(INDEXER_PACKAGE_NAME)
                                .build());
        List<GenericDocument> genericDocuments = collectAllResults(searchResults);
        for (int i = 0; i < genericDocuments.size(); i++) {
            GenericDocument genericDocument = genericDocuments.get(i);
            if (genericDocument.getId().equals(id)) {
                return genericDocument;
            }
        }
        return null;
    }

    private List<GenericDocument> searchAppFunctionsWithPackageName(String packageName)
            throws ExecutionException, InterruptedException {
        GlobalSearchSessionShim globalSearchSession =
                GlobalSearchSessionShimImpl.createGlobalSearchSessionAsync().get();

        SearchResultsShim searchResults =
                globalSearchSession.search(
                        String.format("packageName:\"%s\"", packageName),
                        new SearchSpec.Builder()
                                .addFilterNamespaces(NAMESPACE_APP_FUNCTIONS)
                                .addFilterPackageNames(INDEXER_PACKAGE_NAME)
                                .setVerbatimSearchEnabled(true)
                                .build());
        return collectAllResults(searchResults);
    }

    private Map<String, GenericDocument> searchAppFunctionDocumentsIntoMap(String packageName)
            throws ExecutionException, InterruptedException {
        Map<String, GenericDocument> appFns = new ArrayMap<>();
        for (GenericDocument document : searchAppFunctionsWithPackageName(packageName)) {
            appFns.put(document.getId(), document);
        }

        return appFns;
    }

    private List<GenericDocument> collectAllResults(SearchResultsShim searchResults)
            throws ExecutionException, InterruptedException {
        List<GenericDocument> documents = new ArrayList<>();
        List<SearchResult> results;
        do {
            results = searchResults.getNextPageAsync().get();
            for (SearchResult result : results) {
                documents.add(result.getGenericDocument());
            }
        } while (results.size() > 0);
        return documents;
    }

    private void installPackage(@NonNull String path) {
        assertThat(
                        SystemUtil.runShellCommand(
                                String.format(
                                        "pm install -r -i %s -t -g %s",
                                        mContext.getPackageName(), path)))
                .isEqualTo("Success\n");
    }

    private void uninstallPackage(@NonNull String packageName) {
        SystemUtil.runShellCommand("pm uninstall " + packageName);
    }

    /** Retries an assertion with a delay between attempts. */
    private static void retryAssert(ThrowRunnable runnable) throws Throwable {
        Throwable lastError = null;

        for (int attempt = 0; attempt < RETRY_MAX_INTERVALS; attempt++) {
            try {
                runnable.run();
                return;
            } catch (Throwable e) {
                lastError = e;
                if (attempt < RETRY_MAX_INTERVALS) {
                    Thread.sleep(RETRY_CHECK_INTERVAL_MILLIS);
                }
            }
        }
        throw lastError;
    }

    /** Runnable that throws. */
    public interface ThrowRunnable {
        void run() throws Throwable;
    }
}
