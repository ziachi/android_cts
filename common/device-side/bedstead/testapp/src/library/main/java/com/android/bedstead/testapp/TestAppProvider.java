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

package com.android.bedstead.testapp;

import static com.android.bedstead.nene.devicepolicy.CommonDeviceAdminInfo.USES_ENCRYPTED_STORAGE;
import static com.android.bedstead.nene.devicepolicy.CommonDeviceAdminInfo.USES_POLICY_DISABLE_CAMERA;
import static com.android.bedstead.nene.devicepolicy.CommonDeviceAdminInfo.USES_POLICY_DISABLE_KEYGUARD_FEATURES;
import static com.android.bedstead.nene.devicepolicy.CommonDeviceAdminInfo.USES_POLICY_EXPIRE_PASSWORD;
import static com.android.bedstead.nene.devicepolicy.CommonDeviceAdminInfo.USES_POLICY_FORCE_LOCK;
import static com.android.bedstead.nene.devicepolicy.CommonDeviceAdminInfo.USES_POLICY_LIMIT_PASSWORD;
import static com.android.bedstead.nene.devicepolicy.CommonDeviceAdminInfo.USES_POLICY_RESET_PASSWORD;
import static com.android.bedstead.nene.devicepolicy.CommonDeviceAdminInfo.USES_POLICY_SETS_GLOBAL_PROXY;
import static com.android.bedstead.nene.devicepolicy.CommonDeviceAdminInfo.USES_POLICY_WATCH_LOGIN;
import static com.android.bedstead.nene.devicepolicy.CommonDeviceAdminInfo.USES_POLICY_WIPE_DATA;

import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

import com.android.bedstead.nene.TestApis;
import com.android.queryable.annotations.Query;
import com.android.queryable.info.ActivityInfo;
import com.android.queryable.info.MetadataInfo;
import com.android.queryable.info.MetadataValue;
import com.android.queryable.info.ReceiverInfo;
import com.android.queryable.info.ResourceInfo;
import com.android.queryable.info.ServiceInfo;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/** Entry point to Test App. Used for querying for {@link TestApp} instances. */
public final class TestAppProvider {

    private static final String TAG = TestAppProvider.class.getSimpleName();

    // Must be instrumentation context to access resources
    private static final Context sContext = TestApis.context().instrumentationContext();
    private boolean mTestAppsInitialised = false;
    private final List<TestAppDetails> mTestApps = new ArrayList<>();
    private Set<TestAppDetails> mTestAppsSnapshot = null;

    private static final Map<String, Integer> sPoliciesIntToXmlTagMap =
            ImmutableMap.of("limit-password", USES_POLICY_LIMIT_PASSWORD,
                    "watch-login", USES_POLICY_WATCH_LOGIN,
                    "reset-password", USES_POLICY_RESET_PASSWORD,
                    "force-lock", USES_POLICY_FORCE_LOCK,
                    "wipe-data", USES_POLICY_WIPE_DATA,
                    "set-global-proxy", USES_POLICY_SETS_GLOBAL_PROXY,
                    "expire-password", USES_POLICY_EXPIRE_PASSWORD,
                    "encrypted-storage", USES_ENCRYPTED_STORAGE,
                    "disable-camera", USES_POLICY_DISABLE_CAMERA,
                    "disable-keyguard-features", USES_POLICY_DISABLE_KEYGUARD_FEATURES);

    public TestAppProvider() {
        initTestApps();
    }

    /** Begin a query for a {@link TestApp}. */
    public TestAppQueryBuilder query() {
        return new TestAppQueryBuilder(this);
    }

    /** Create a query for a {@link TestApp} starting with a {@link Query}. */
    public TestAppQueryBuilder query(Query query) {
        return query().applyAnnotation(query);
    }

    /** Get any {@link TestApp}. */
    public TestApp any() {
        TestApp testApp = query().get();
        Log.d(TAG, "any(): returning " + testApp);
        return testApp;
    }

    List<TestAppDetails> testApps() {
        return mTestApps;
    }

    /** Save the state of the provider, to be reset by {@link #restore()}. */
    public void snapshot() {
        mTestAppsSnapshot = new HashSet<>(mTestApps);
    }

    /**
     * Restore the state of the provider to that recorded by {@link #snapshot()}.
     */
    public void restore() {
        if (mTestAppsSnapshot == null) {
            throw new IllegalStateException("You must call snapshot() before restore()");
        }
        mTestApps.clear();
        mTestApps.addAll(mTestAppsSnapshot);
    }

    /**
     * Release resources.
     * <br><br>
     * Note: This method is intended for internal use and should <b>not</b> be called outside core
     * Bedstead infrastructure.
     */
    public void releaseResources() {
        mTestApps.clear();
        mTestAppsSnapshot.clear();
    }

    private void initTestApps() {
        if (mTestAppsInitialised) {
            return;
        }
        mTestAppsInitialised = true;

        try (InputStream inputStream = sContext.getAssets().open("testapps/index.txt")) {
            TestappProtos.TestAppIndex index = TestappProtos.TestAppIndex.parseFrom(inputStream);
            for (int i = 0; i < index.getAppsCount(); i++) {
                loadApk(index.getApps(i));
            }
            Collections.sort(mTestApps,
                    Comparator.comparing((testAppDetails) -> testAppDetails.mApp.getPackageName()));
        } catch (IOException e) {
            throw new RuntimeException("Error loading testapp index", e);
        }
    }

    private void loadApk(TestappProtos.AndroidApp app) throws IOException {
        TestAppDetails details = new TestAppDetails();
        details.mApp = app;

        for (int i = 0; i < app.getMetadataCount(); i++) {
            TestappProtos.Metadata metadataEntry = app.getMetadata(i);
            MetadataInfo metadataInfo = MetadataInfo.builder().key(metadataEntry.getName())
                    .value(MetadataValue.builder().value(metadataEntry.getValue()).build())
                    .build();

            if (!metadataEntry.getResource().isEmpty()) {
                String resourceName = metadataEntry.getValue();
                if (!resourceName.isEmpty()) {
                    // TODO(b/273291850): enable parsing of non-xml resources as well.
                    try (InputStream inputStream =
                                 sContext.getAssets().open("resources/" + resourceName + ".xml")) {
                        String content =
                                new String(ByteStreams.toByteArray(inputStream), StandardCharsets.UTF_8);
                        metadataInfo.setResource(ResourceInfo.builder().content(content).build());
                        Set<Integer> policies = fetchPoliciesFromResource(content);
                        if (policies != null) {
                            details.mPolicies.addAll(policies);
                        }
                    }
                }
            }

            details.mMetadata.add(metadataInfo);
        }

        for (int i = 0; i < app.getPermissionsCount(); i++) {
            details.mPermissions.add(app.getPermissions(i).getName());
        }

        for (int i = 0; i < app.getActivitiesCount(); i++) {
            TestappProtos.Activity activityEntry = app.getActivities(i);
            details.mActivities.add(ActivityInfo.builder()
                    .activityClass(activityEntry.getName())
                    .exported(activityEntry.getExported())
                    .intentFilters(intentFilterSetFromProtoList(
                            activityEntry.getIntentFiltersList()))
                    .permission(activityEntry.getPermission().equals("") ? null
                            : activityEntry.getPermission())
                    .build());
        }

        for (int i = 0; i < app.getActivityAliasesCount(); i++) {
            TestappProtos.ActivityAlias activityAliasEntry = app.getActivityAliases(i);
            ActivityInfo activityInfo = ActivityInfo.builder()
                    .activityClass(activityAliasEntry.getName())
                    .exported(activityAliasEntry.getExported())
                    .intentFilters(intentFilterSetFromProtoList(
                            activityAliasEntry.getIntentFiltersList()))
                    .permission(activityAliasEntry.getPermission().equals("") ? null
                            : activityAliasEntry.getPermission())
                    .build();

            details.mActivityAliases.add(activityInfo);

        }

        for (int i = 0; i < app.getServicesCount(); i++) {
            TestappProtos.Service serviceEntry = app.getServices(i);
            details.mServices.add(ServiceInfo.builder()
                    .serviceClass(serviceEntry.getName())
                    .intentFilters(intentFilterSetFromProtoList(
                            serviceEntry.getIntentFiltersList()))
                    .metadata(metadataSetFromProtoList(
                            serviceEntry.getMetadataList()))
                    .build());
        }

        for (int i = 0; i < app.getReceiversCount(); i++) {
            TestappProtos.Receiver receiverEntry = app.getReceivers(i);
            details.mReceivers.add(ReceiverInfo.builder()
                    .name(receiverEntry.getName())
                    .metadata(metadataSetFromProtoList(receiverEntry.getMetadataList()))
                    .build());
        }

        mTestApps.add(details);
    }

    private Set<IntentFilter> intentFilterSetFromProtoList(
            List<TestappProtos.IntentFilter> list) {
        Set<IntentFilter> filterInfoSet = new HashSet<>();

        for (TestappProtos.IntentFilter filter : list) {
            IntentFilter filterInfo = intentFilterFromProto(filter);
            filterInfoSet.add(filterInfo);
        }

        return filterInfoSet;
    }

    private IntentFilter intentFilterFromProto(TestappProtos.IntentFilter filterProto) {
        IntentFilter filter = new IntentFilter();

        for (String action : filterProto.getActionsList()) {
            filter.addAction(action);
        }
        for (String category : filterProto.getCategoriesList()) {
            filter.addCategory(category);
        }

        return filter;
    }

    private Set<Bundle> metadataSetFromProtoList(
            List<TestappProtos.Metadata> list) {
        Set<Bundle> metadataSet = new HashSet<>();

        for (TestappProtos.Metadata metadata : list) {
            Bundle metadataBundle = new Bundle();
            metadataBundle.putString(metadata.getName(), metadata.getValue());
            metadataSet.add(metadataBundle);
        }

        return metadataSet;
    }

    void markTestAppUsed(TestAppDetails testApp) {
        mTestApps.remove(testApp);
    }

    private Set<Integer> fetchPoliciesFromResource(String resourceContent) {
        Document document = convertStringToXml(resourceContent);
        NodeList nodeList = document.getElementsByTagName("uses-policies");
        if (nodeList == null || nodeList.item(0) == null) {
            return null;
        }
        nodeList = document.getElementsByTagName("uses-policies").item(0)
                .getChildNodes();

        Set<Integer> policies = new HashSet<>();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node node = nodeList.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Integer policyIntValue = sPoliciesIntToXmlTagMap.get(node.getNodeName());
                if (policyIntValue == null) {
                    continue;
                }
                policies.add(policyIntValue);
            }
        }
        return policies;
    }

    private static Document convertStringToXml(String xmlString) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = dbf.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xmlString)));
        } catch (ParserConfigurationException | IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

}
