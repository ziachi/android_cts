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

package android.widget.cts;

import static com.android.compatibility.common.util.WidgetTestUtils.runOnMainAndDrawSync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.appwidget.AppWidgetHostView;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Parcel;
import android.util.SizeF;
import android.util.TypedValue;
import android.util.proto.ProtoInputStream;
import android.util.proto.ProtoOutputStream;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Adapter;
import android.widget.AdapterViewFlipper;
import android.widget.CompoundButton;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.RemoteViews;
import android.widget.RemoteViews.RemoteCollectionItems;
import android.widget.StackView;
import android.widget.TextView;
import android.widget.cts.util.RemoteViewsUtil;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.ApiTest;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@MediumTest
@ApiTest(apis = {"android.widget.RemoteViews#setRemoteAdapter(int,RemoteCollectionItems)"})
@RunWith(Parameterized.class)
public class RemoteViewsFixedCollectionAdapterTest {
    private static final String PACKAGE_NAME = "android.widget.cts";

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<RemoteViewsCtsActivity> mActivityRule =
            new ActivityTestRule<>(RemoteViewsCtsActivity.class);

    @Parameterized.Parameters(name = "isProtoTest={0}")
    public static Object[] parameters() {
        return new Object[] {false, true};
    }

    /**
     * When this parameter is true, the test serializes and deserializes the RemoteViews to/from
     * proto before applying. This ensures that proto serialization does not cause a change in the
     * structure or function of RemoteViews, apart from PendingIntent based APIs.
     */
    @Parameterized.Parameter(0)
    public boolean isProtoTest;

    private Instrumentation mInstrumentation;

    private Activity mActivity;

    private RemoteViews mRemoteViews;

    private View mView;
    private ListView mListView;
    private GridView mGridView;
    private StackView mStackView;
    private AdapterViewFlipper mAdapterViewFlipper;

    @Before
    public void setUp() throws Throwable {
        RemoteViewsUtil.checkRemoteViewsProtoFlag(isProtoTest);
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mRemoteViews = new RemoteViews(PACKAGE_NAME, R.layout.remoteviews_adapters);
        applyRemoteViews();

        ViewGroup parent = mActivity.findViewById(R.id.remoteView_host);
        AppWidgetHostView hostView = new AppWidgetHostView(mActivity);
        mActivityRule.runOnUiThread(
                () -> {
                    parent.addView(hostView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                    hostView.addView(mView);
                });

        mListView = mView.findViewById(R.id.remoteView_list);
        mGridView = mView.findViewById(R.id.remoteView_grid);
        mStackView = mView.findViewById(R.id.remoteView_stack);
        mAdapterViewFlipper = mView.findViewById(R.id.remoteView_flipper);
    }

    @Test
    public void testParcelingAndUnparceling() {
        RemoteCollectionItems items = new RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .setViewTypeCount(10)
                .addItem(3 /* id */, new RemoteViews(PACKAGE_NAME, R.layout.textview_singleline))
                .addItem(5 /* id */, new RemoteViews(PACKAGE_NAME, R.layout.textview_gravity))
                .build();

        RemoteCollectionItems unparceled = parcelAndUnparcel(items);
        assertEquals(2, unparceled.getItemCount());
        assertEquals(3, unparceled.getItemId(0));
        assertEquals(5, unparceled.getItemId(1));
        assertEquals(R.layout.textview_singleline, unparceled.getItemView(0).getLayoutId());
        assertEquals(R.layout.textview_gravity, unparceled.getItemView(1).getLayoutId());
        assertTrue(unparceled.hasStableIds());
        assertEquals(10, unparceled.getViewTypeCount());
        assertNotNull(unparceled.getItemView(0).mApplication);
        assertSame(
                unparceled.getItemView(0).mApplication, unparceled.getItemView(1).mApplication);

        // Parcel and unparcel the RemoteViews and test again to ensure that the parent child
        // relationship is correctly established from the Parcel constructor.
        unparceled = parcelAndUnparcel(unparceled);
        assertEquals(2, unparceled.getItemCount());
        assertEquals(3, unparceled.getItemId(0));
        assertEquals(5, unparceled.getItemId(1));
        assertEquals(R.layout.textview_singleline, unparceled.getItemView(0).getLayoutId());
        assertEquals(R.layout.textview_gravity, unparceled.getItemView(1).getLayoutId());
        assertTrue(unparceled.hasStableIds());
        assertEquals(10, unparceled.getViewTypeCount());
        assertNotNull(unparceled.getItemView(0).mApplication);
        assertSame(
                unparceled.getItemView(0).mApplication, unparceled.getItemView(1).mApplication);
    }

    @Test
    public void testParcelingAndUnparceling_afterAttaching() {
        RemoteCollectionItems items = new RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .setViewTypeCount(10)
                .addItem(3 /* id */, new RemoteViews(PACKAGE_NAME, R.layout.textview_singleline))
                .addItem(5 /* id */, new RemoteViews(PACKAGE_NAME, R.layout.textview_gravity))
                .build();

        RemoteViews parent = new RemoteViews(PACKAGE_NAME, R.layout.listview_layout);
        parent.setRemoteAdapter(R.id.listview_default, items);

        RemoteCollectionItems unparceled = parcelAndUnparcel(items);
        assertEquals(2, unparceled.getItemCount());
        assertEquals(3, unparceled.getItemId(0));
        assertEquals(5, unparceled.getItemId(1));
        assertEquals(R.layout.textview_singleline, unparceled.getItemView(0).getLayoutId());
        assertEquals(R.layout.textview_gravity, unparceled.getItemView(1).getLayoutId());
        assertTrue(unparceled.hasStableIds());
        assertEquals(10, unparceled.getViewTypeCount());
        assertNotNull(unparceled.getItemView(0).mApplication);
        assertSame(
                unparceled.getItemView(0).mApplication, unparceled.getItemView(1).mApplication);
    }

    @Test
    public void testParcelingAndUnparceling_multiplePackages() {
        String otherPackageName = getAnotherPackageName();
        RemoteCollectionItems items =
                new RemoteCollectionItems.Builder()
                        .setHasStableIds(true)
                        .setViewTypeCount(10)
                        .addItem(
                                3 /* id */,
                                new RemoteViews(PACKAGE_NAME, R.layout.textview_singleline))
                        .addItem(
                                5 /* id */,
                                new RemoteViews(otherPackageName, android.R.layout.list_content))
                        .build();

        RemoteViews parent = new RemoteViews(PACKAGE_NAME, R.layout.listview_layout);
        parent.setRemoteAdapter(R.id.listview_default, items);

        RemoteCollectionItems unparceled = parcelAndUnparcel(items);
        assertEquals(2, unparceled.getItemCount());
        assertEquals(3, unparceled.getItemId(0));
        assertEquals(5, unparceled.getItemId(1));
        assertEquals(R.layout.textview_singleline, unparceled.getItemView(0).getLayoutId());
        assertEquals(android.R.layout.list_content, unparceled.getItemView(1).getLayoutId());
        assertTrue(unparceled.hasStableIds());
        assertEquals(10, unparceled.getViewTypeCount());
        assertNotNull(unparceled.getItemView(0).mApplication);
        assertNotNull(unparceled.getItemView(1).mApplication);
        assertEquals(PACKAGE_NAME, unparceled.getItemView(0).mApplication.packageName);
        assertEquals(otherPackageName, unparceled.getItemView(1).mApplication.packageName);
    }

    @Test
    public void testBuilder_empty() {
        RemoteCollectionItems items = new RemoteCollectionItems.Builder().build();

        assertEquals(0, items.getItemCount());
        assertEquals(1, items.getViewTypeCount());
        assertFalse(items.hasStableIds());
    }

    @Test
    public void testBuilder_viewTypeCountUnspecified() {
        RemoteViews firstItem = new RemoteViews(PACKAGE_NAME, R.layout.textview_singleline);
        RemoteViews secondItem = new RemoteViews(PACKAGE_NAME, R.layout.textview_gravity);
        RemoteCollectionItems items = new RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .addItem(3 /* id */, firstItem)
                .addItem(5 /* id */, secondItem)
                .build();

        assertEquals(2, items.getItemCount());
        assertEquals(3, items.getItemId(0));
        assertEquals(5, items.getItemId(1));
        assertSame(firstItem, items.getItemView(0));
        assertSame(secondItem, items.getItemView(1));
        assertTrue(items.hasStableIds());
        // The view type count should be derived from the number of different layout ids if
        // unspecified.
        assertEquals(2, items.getViewTypeCount());
    }

    @Test
    public void testBuilder_viewTypeCountSpecified() {
        RemoteViews firstItem = new RemoteViews(PACKAGE_NAME, R.layout.textview_singleline);
        RemoteViews secondItem = new RemoteViews(PACKAGE_NAME, R.layout.textview_gravity);
        RemoteCollectionItems items = new RemoteCollectionItems.Builder()
                .addItem(3 /* id */, firstItem)
                .addItem(5 /* id */, secondItem)
                .setViewTypeCount(15)
                .build();

        assertEquals(15, items.getViewTypeCount());
    }

    @Test
    public void testBuilder_repeatedIdsAndLayouts() {
        RemoteViews firstItem = new RemoteViews(PACKAGE_NAME, R.layout.textview_singleline);
        RemoteViews secondItem = new RemoteViews(PACKAGE_NAME, R.layout.textview_singleline);
        RemoteViews thirdItem = new RemoteViews(PACKAGE_NAME, R.layout.textview_singleline);
        RemoteCollectionItems items = new RemoteCollectionItems.Builder()
                .setHasStableIds(false)
                .addItem(42 /* id */, firstItem)
                .addItem(42 /* id */, secondItem)
                .addItem(42 /* id */, thirdItem)
                .build();

        assertEquals(3, items.getItemCount());
        assertEquals(42, items.getItemId(0));
        assertEquals(42, items.getItemId(1));
        assertEquals(42, items.getItemId(2));
        assertSame(firstItem, items.getItemView(0));
        assertSame(secondItem, items.getItemView(1));
        assertSame(thirdItem, items.getItemView(2));
        assertEquals(1, items.getViewTypeCount());
        assertFalse(items.hasStableIds());
    }

    @Test
    public void testBuilder_nullItem() {
        assertThrows(
                NullPointerException.class,
                () -> new RemoteCollectionItems.Builder()
                        .addItem(0, null /* view */)
                        .build());
    }

    @Test
    public void testBuilder_multipleLayouts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RemoteCollectionItems.Builder()
                        .addItem(0, new RemoteViews(
                                new RemoteViews(PACKAGE_NAME, R.layout.listview_layout),
                                new RemoteViews(PACKAGE_NAME, R.layout.listview_layout)
                        ))
                        .build());
    }

    @Test
    public void testBuilder_viewTypeCountLowerThanLayoutCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RemoteCollectionItems.Builder()
                        .setHasStableIds(true)
                        .setViewTypeCount(1)
                        .addItem(3 /* id */,
                                new RemoteViews(PACKAGE_NAME, R.layout.textview_singleline))
                        .addItem(5 /* id */,
                                new RemoteViews(PACKAGE_NAME, R.layout.textview_gravity))
                        .build());
    }

    @Test
    public void testSerializationSize_largeCollection() {
        RemoteCollectionItems items = createSampleCollectionItems(/* size= */ 100);

        int dataSize = parcelAndGetDataSize(items);

        // 7,408 when test was written.
        assertLessThan(10_000, dataSize);
    }

    @Test
    public void testSerializationSize_largeCollection_multiPackage() {
        String otherPackageName = getAnotherPackageName();
        RemoteCollectionItems items =
                createSampleMultiPackageCollectionItems(/* size= */ 100, otherPackageName);

        int dataSize = parcelAndGetDataSize(items);

        // 9,140 when test was written.
        assertLessThan(12_000, dataSize);
    }

    @Test
    public void testSerializationSize_extraLargeCollection() {
        RemoteCollectionItems items = createSampleCollectionItems(/* size= */ 1000);

        int dataSize = parcelAndGetDataSize(items);

        if (isProtoTest) {
            // 71,876 when test was written
            assertLessThan(80_000, dataSize);
        } else {
            // 50,608 when test was written.
            assertLessThan(70_000, dataSize);
        }
    }

    @Test
    public void testSerializationSize_largeCollectionInLandPortRemoteViews() {
        RemoteViews landscape = new RemoteViews(PACKAGE_NAME, R.layout.listview_layout);
        landscape.setRemoteAdapter(
                R.id.listview_default,
                createSampleCollectionItems(/* size= */ 100));
        RemoteViews portrait = new RemoteViews(PACKAGE_NAME, R.layout.listview_layout);
        landscape.setRemoteAdapter(
                R.id.listview_default,
                createSampleCollectionItems(/* size= */ 100));

        RemoteViews joinedRemoteViews = new RemoteViews(landscape, portrait);

        int dataSize = parcelAndGetDataSize(joinedRemoteViews);

        // 12,336 when test was written.
        assertLessThan(16_000, dataSize);
    }

    @Test
    public void testSerializationSize_largeCollectionInLandPortRemoteViews_multiPackage() {
        String otherPackage = getAnotherPackageName();
        RemoteViews landscape = new RemoteViews(PACKAGE_NAME, R.layout.listview_layout);
        landscape.setRemoteAdapter(
                R.id.listview_default,
                createSampleMultiPackageCollectionItems(/* size= */ 100, otherPackage));
        RemoteViews portrait = new RemoteViews(PACKAGE_NAME, R.layout.listview_layout);
        landscape.setRemoteAdapter(
                R.id.listview_default,
                createSampleMultiPackageCollectionItems(/* size= */ 100, otherPackage));

        RemoteViews joinedRemoteViews = new RemoteViews(landscape, portrait);

        int dataSize = parcelAndGetDataSize(joinedRemoteViews);

        // 14,068 when test was written.
        assertLessThan(20_000, dataSize);
    }

    @Test
    public void testSerializationSize_largeCollectionInSizedRemoteViews() {
        List<SizeF> sizes =
                Lists.newArrayList(
                        new SizeF(50, 50),
                        new SizeF(50, 100),
                        new SizeF(100, 100),
                        new SizeF(200, 100));
        Map<SizeF, RemoteViews> sizeToRemoteViews =
                sizes.stream().collect(Collectors.toMap(Function.identity(), ignored -> {
                    RemoteCollectionItems items = createSampleCollectionItems(/* size= */ 100);
                    RemoteViews views = new RemoteViews(PACKAGE_NAME, R.layout.listview_layout);
                    views.setRemoteAdapter(R.id.listview_default, items);
                    return views;
                }));
        RemoteViews joinedRemoteViews = new RemoteViews(sizeToRemoteViews);

        int dataSize = parcelAndGetDataSize(joinedRemoteViews);

        // 22,100 when test was written.
        assertLessThan(32_000, dataSize);
    }

    @Test
    public void testSerializationSize_largeCollectionInSizedRemoteViews_multiPackage() {
        String otherPackage = getAnotherPackageName();
        List<SizeF> sizes =
                Lists.newArrayList(
                        new SizeF(50, 50),
                        new SizeF(50, 100),
                        new SizeF(100, 100),
                        new SizeF(200, 100));
        Map<SizeF, RemoteViews> sizeToRemoteViews =
                sizes.stream().collect(Collectors.toMap(Function.identity(), ignored -> {
                    RemoteCollectionItems items =
                            createSampleMultiPackageCollectionItems(
                                    /* size= */ 100, otherPackage);
                    RemoteViews views = new RemoteViews(PACKAGE_NAME, R.layout.listview_layout);
                    views.setRemoteAdapter(R.id.listview_default, items);
                    return views;
                }));
        RemoteViews joinedRemoteViews = new RemoteViews(sizeToRemoteViews);

        int dataSize = parcelAndGetDataSize(joinedRemoteViews);

        // 23,832 when test was written.
        assertLessThan(30_000, dataSize);
    }

    @Test
    @FlakyTest(bugId = 237442832)
    public void testSetRemoteAdapter_emptyCollection() {
        RemoteCollectionItems items = new RemoteCollectionItems.Builder().build();
        mRemoteViews.setRemoteAdapter(R.id.remoteView_list, items);
        reapplyRemoteViews(mListView);

        assertEquals(0, mListView.getChildCount());
        assertEquals(0, mListView.getAdapter().getCount());
        assertEquals(1, mListView.getAdapter().getViewTypeCount());
        assertFalse(mListView.getAdapter().hasStableIds());
    }

    @Test
    public void testSetRemoteAdapter_withItems() {
        RemoteViews item0 = new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout);
        item0.setTextViewText(android.R.id.text1, "Hello");

        RemoteViews item1 = new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout);
        item1.setTextViewText(android.R.id.text1, "World");

        RemoteCollectionItems items = new RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .addItem(10 /* id= */, item0)
                .addItem(11 /* id= */, item1)
                .build();

        mRemoteViews.setRemoteAdapter(R.id.remoteView_list, items);
        reapplyRemoteViews(mListView);

        Adapter adapter = mListView.getAdapter();
        assertEquals(2, adapter.getCount());
        assertEquals(1, adapter.getViewTypeCount());
        assertEquals(adapter.getItemViewType(0), adapter.getItemViewType(1));
        assertEquals(10, adapter.getItemId(0));
        assertEquals(11, adapter.getItemId(1));
        assertTrue(adapter.hasStableIds());

        assertEquals(2, mListView.getChildCount());
        AppWidgetHostView child0 = (AppWidgetHostView) mListView.getChildAt(0);
        AppWidgetHostView child1 = (AppWidgetHostView) mListView.getChildAt(1);
        TextView textView0 = (TextView) child0.getChildAt(0);
        TextView textView1 = (TextView) child1.getChildAt(0);
        assertEquals("Hello", textView0.getText());
        assertEquals("World", textView1.getText());
    }

    @Test
    public void testSetRemoteAdapter_checkedChangeListener() throws Throwable {
        String action = "my-action";
        MockBroadcastReceiver receiver = new MockBroadcastReceiver();
        mActivity.registerReceiver(receiver, new IntentFilter(action), Context.RECEIVER_EXPORTED);

        Intent intent = new Intent(action).setPackage(mActivity.getPackageName());
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        mActivity,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        mRemoteViews.setPendingIntentTemplate(R.id.remoteView_list, pendingIntent);

        ListView listView = mView.findViewById(R.id.remoteView_list);

        RemoteViews item0 = new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout);
        item0.setTextViewText(android.R.id.text1, "Hello");

        RemoteViews item1 = new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout);
        item1.setTextViewText(android.R.id.text1, "World");

        RemoteViews item2 = new RemoteViews(PACKAGE_NAME, R.layout.checkbox_layout);
        item2.setTextViewText(R.id.check_box, "Checkbox");
        item2.setCompoundButtonChecked(R.id.check_box, true);
        item2.setOnCheckedChangeResponse(
                R.id.check_box,
                RemoteViews.RemoteResponse.fromFillInIntent(new Intent().putExtra("my-extra", 42)));

        RemoteCollectionItems items = new RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .addItem(10 /* id= */, item0)
                .addItem(11 /* id= */, item1)
                .addItem(12 /* id= */, item2)
                .build();

        mRemoteViews.setRemoteAdapter(R.id.remoteView_list, parcelAndUnparcel(items));
        reapplyRemoteViews();

        Adapter adapter = listView.getAdapter();
        assertEquals(3, adapter.getCount());
        assertEquals(2, adapter.getViewTypeCount());
        assertEquals(adapter.getItemViewType(0), adapter.getItemViewType(1));
        assertNotEquals(adapter.getItemViewType(0), adapter.getItemViewType(2));
        assertEquals(10, adapter.getItemId(0));
        assertEquals(11, adapter.getItemId(1));
        assertEquals(12, adapter.getItemId(2));
        assertTrue(adapter.hasStableIds());

        assertEquals(3, listView.getChildCount());
        AppWidgetHostView child0 = (AppWidgetHostView) listView.getChildAt(0);
        AppWidgetHostView child1 = (AppWidgetHostView) listView.getChildAt(1);
        AppWidgetHostView child2 = (AppWidgetHostView) listView.getChildAt(2);
        TextView textView0 = (TextView) child0.getChildAt(0);
        TextView textView1 = (TextView) child1.getChildAt(0);
        CompoundButton checkBox2 =
                (CompoundButton) ((ViewGroup) child2.getChildAt(0)).getChildAt(0);
        assertEquals("Hello", textView0.getText());
        assertEquals("World", textView1.getText());
        assertEquals("Checkbox", checkBox2.getText());
        assertTrue(checkBox2.isChecked());

        // Proto serialization removes PendingIntents from the RemoteViews.
        if (isProtoTest) return;

        // View being checked to false should launch the intent.
        reapplyRemoteViews();
        mActivityRule.runOnUiThread(() -> checkBox2.setChecked(false));
        Intent checkChangeIntent = receiver.awaitIntent();
        assertFalse(checkChangeIntent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, true));
        assertEquals(42, checkChangeIntent.getIntExtra("my-extra", 0));
    }

    @Test
    public void testSetRemoteAdapter_clickFillListener() throws Throwable {
        String action = "my-action";
        MockBroadcastReceiver receiver = new MockBroadcastReceiver();
        mActivity.registerReceiver(receiver, new IntentFilter(action), Context.RECEIVER_EXPORTED);

        Intent intent = new Intent(action).setPackage(mActivity.getPackageName());
        PendingIntent pendingIntent =
                PendingIntent.getBroadcast(
                        mActivity,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        mRemoteViews.setPendingIntentTemplate(R.id.remoteView_list, pendingIntent);

        ListView listView = mView.findViewById(R.id.remoteView_list);

        RemoteViews item0 = new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout);
        item0.setTextViewText(android.R.id.text1, "Hello");
        item0.setOnClickFillInIntent(android.R.id.text1, new Intent().putExtra("my-extra", 42));

        RemoteViews item1 = new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout);
        item1.setTextViewText(android.R.id.text1, "World");

        RemoteCollectionItems items = new RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .addItem(10 /* id= */, item0)
                .addItem(11 /* id= */, item1)
                .build();

        mRemoteViews.setRemoteAdapter(R.id.remoteView_list, items);
        reapplyRemoteViews();

        // Proto serialization removes PendingIntents from the RemoteViews.
        if (isProtoTest) return;

        mActivityRule.runOnUiThread(() -> listView.performItemClick(listView.getChildAt(0), 0, 10));
        Intent itemClickIntent = receiver.awaitIntent();
        assertEquals(42, itemClickIntent.getIntExtra("my-extra", 0));
    }

    @Test
    public void testSetRemoteAdapter_newViewTypeAddedCoveredByViewTypeCount() {
        ListView listView = mView.findViewById(R.id.remoteView_list);

        RemoteCollectionItems items = new RemoteCollectionItems.Builder()
                .addItem(10 /* id= */, new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout))
                .setViewTypeCount(2)
                .build();

        mRemoteViews.setRemoteAdapter(R.id.remoteView_list, items);
        reapplyRemoteViews();

        Adapter initialAdapter = listView.getAdapter();
        AppWidgetHostView child0 = (AppWidgetHostView) listView.getChildAt(0);
        TextView initialFirstItemView = (TextView) child0.getChildAt(0);
        int initialFirstItemViewType = initialAdapter.getItemViewType(0);

        items = new RemoteCollectionItems.Builder()
                .addItem(8 /* id= */, new RemoteViews(PACKAGE_NAME, R.layout.checkbox_layout))
                .addItem(10 /* id= */, new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout))
                .setViewTypeCount(2)
                .build();
        mRemoteViews.setRemoteAdapter(R.id.remoteView_list, items);
        reapplyRemoteViews();

        // The adapter should have been reused and simply updated. The view type for the first
        // layoutId should have been maintained (as 0) and the next view type assigned to the
        // checkbox layout. The view for the row should have been recycled without inflating a new
        // view.
        AppWidgetHostView child1 = (AppWidgetHostView) listView.getChildAt(1);
        assertSame(initialAdapter, listView.getAdapter());
        assertSame(initialFirstItemView, child1.getChildAt(0));
        assertEquals(initialFirstItemViewType, listView.getAdapter().getItemViewType(1));
        assertNotEquals(initialFirstItemViewType, listView.getAdapter().getItemViewType(0));
    }

    @Test
    public void testSetRemoteAdapter_newViewTypeAddedToIncreaseViewTypeCount() {
        ListView listView = mView.findViewById(R.id.remoteView_list);

        RemoteCollectionItems items = new RemoteCollectionItems.Builder()
                .addItem(10 /* id= */, new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout))
                .build();

        mRemoteViews.setRemoteAdapter(R.id.remoteView_list, items);
        reapplyRemoteViews();
        Adapter initialAdapter = listView.getAdapter();
        AppWidgetHostView child0 = (AppWidgetHostView) listView.getChildAt(0);
        TextView initialFirstItemView = (TextView) child0.getChildAt(0);

        items = new RemoteCollectionItems.Builder()
                .addItem(8 /* id= */, new RemoteViews(PACKAGE_NAME, R.layout.checkbox_layout))
                .addItem(10 /* id= */, new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout))
                .build();
        mRemoteViews.setRemoteAdapter(R.id.remoteView_list, items);
        reapplyRemoteViews();
        // The adapter should have been replaced, which is required when the view type increases.
        AppWidgetHostView child1 = (AppWidgetHostView) listView.getChildAt(1);
        assertEquals(2, listView.getAdapter().getViewTypeCount());
        assertNotSame(initialAdapter, listView.getAdapter());
        assertNotSame(initialFirstItemView, child1.getChildAt(0));
    }

    @Test
    public void testSetRemoteAdapter_viewTypeRemoved_viewTypeCountSame() {
        ListView listView = mView.findViewById(R.id.remoteView_list);

        RemoteCollectionItems items = new RemoteCollectionItems.Builder()
                .addItem(8 /* id= */, new RemoteViews(PACKAGE_NAME, R.layout.checkbox_layout))
                .addItem(10 /* id= */, new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout))
                .setViewTypeCount(2)
                .build();

        mRemoteViews.setRemoteAdapter(R.id.remoteView_list, parcelAndUnparcel(items));
        reapplyRemoteViews();
        Adapter initialAdapter = listView.getAdapter();
        AppWidgetHostView child1 = (AppWidgetHostView) listView.getChildAt(1);
        TextView initialSecondItemView = (TextView) child1.getChildAt(0);
        assertEquals(1, initialAdapter.getItemViewType(1));

        items = new RemoteCollectionItems.Builder()
                .addItem(10 /* id= */, new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout))
                .setViewTypeCount(2)
                .build();
        mRemoteViews.setRemoteAdapter(R.id.remoteView_list, parcelAndUnparcel(items));
        reapplyRemoteViews();
        // The adapter should have been kept, and the second item should have maintained its view
        // type of 1 even though its now the only view type.
        AppWidgetHostView child0 = (AppWidgetHostView) listView.getChildAt(0);
        assertEquals(2, listView.getAdapter().getViewTypeCount());
        assertSame(initialAdapter, listView.getAdapter());
        assertSame(initialSecondItemView, child0.getChildAt(0));
        assertEquals(1, listView.getAdapter().getItemViewType(0));
    }

    @Test
    public void testSetRemoteAdapter_viewTypeRemoved_viewTypeCountLowered() {
        ListView listView = mView.findViewById(R.id.remoteView_list);

        RemoteCollectionItems items = new RemoteCollectionItems.Builder()
                .addItem(8 /* id= */, new RemoteViews(PACKAGE_NAME, R.layout.checkbox_layout))
                .addItem(10 /* id= */, new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout))
                .build();

        mRemoteViews.setRemoteAdapter(R.id.remoteView_list, parcelAndUnparcel(items));
        reapplyRemoteViews();
        Adapter initialAdapter = listView.getAdapter();
        AppWidgetHostView child1 = (AppWidgetHostView) listView.getChildAt(1);
        TextView initialSecondItemView = (TextView) child1.getChildAt(0);
        assertEquals(1, initialAdapter.getItemViewType(1));

        items = new RemoteCollectionItems.Builder()
                .addItem(10 /* id= */, new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout))
                .build();
        mRemoteViews.setRemoteAdapter(R.id.remoteView_list, items);
        reapplyRemoteViews();
        // The adapter should have been kept, and kept its higher view count to allow for views to
        // be recycled.
        AppWidgetHostView child0 = (AppWidgetHostView) listView.getChildAt(0);
        assertEquals(2, listView.getAdapter().getViewTypeCount());
        assertSame(initialAdapter, listView.getAdapter());
        assertSame(initialSecondItemView, child0.getChildAt(0));
        assertEquals(1, listView.getAdapter().getItemViewType(0));
    }

    @Test
    public void testSetRemoteAdapter_gridView() {
        RemoteViews item0 = new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout);
        item0.setViewLayoutWidth(android.R.id.text1, 100, TypedValue.COMPLEX_UNIT_DIP);
        item0.setTextViewText(android.R.id.text1, "Hello");

        RemoteViews item1 = new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout);
        item0.setViewLayoutWidth(android.R.id.text1, 100, TypedValue.COMPLEX_UNIT_DIP);
        item1.setTextViewText(android.R.id.text1, "World");

        RemoteViews item2 = new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout);
        item2.setViewLayoutWidth(android.R.id.text1, 100, TypedValue.COMPLEX_UNIT_DIP);
        item2.setTextViewText(android.R.id.text1, "Hola");

        RemoteViews item3 = new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout);
        item3.setViewLayoutWidth(android.R.id.text1, 100, TypedValue.COMPLEX_UNIT_DIP);
        item3.setTextViewText(android.R.id.text1, "Mundo");

        RemoteCollectionItems items = new RemoteCollectionItems.Builder()
                .addItem(10 /* id= */, item0)
                .addItem(11 /* id= */, item1)
                .addItem(12 /* id= */, item2)
                .addItem(13 /* id= */, item3)
                .build();

        try {
            mActivityRule.runOnUiThread(
                    () -> {
                        mListView.setVisibility(View.GONE);
                        mGridView.setVisibility(View.VISIBLE);
                    });
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        mRemoteViews.setRemoteAdapter(R.id.remoteView_grid, parcelAndUnparcel(items));
        reapplyRemoteViews(mGridView);

        Adapter adapter = mGridView.getAdapter();
        assertEquals(4, adapter.getCount());
        assertEquals(1, adapter.getViewTypeCount());
        assertEquals(adapter.getItemViewType(0), adapter.getItemViewType(1));
        assertEquals(10, adapter.getItemId(0));
        assertEquals(11, adapter.getItemId(1));
        assertEquals(12, adapter.getItemId(2));
        assertEquals(13, adapter.getItemId(3));

        assertEquals(4, mGridView.getChildCount());
        AppWidgetHostView child0 = (AppWidgetHostView) mGridView.getChildAt(0);
        AppWidgetHostView child1 = (AppWidgetHostView) mGridView.getChildAt(1);
        AppWidgetHostView child2 = (AppWidgetHostView) mGridView.getChildAt(2);
        AppWidgetHostView child3 = (AppWidgetHostView) mGridView.getChildAt(3);
        TextView textView0 = (TextView) child0.getChildAt(0);
        TextView textView1 = (TextView) child1.getChildAt(0);
        TextView textView2 = (TextView) child2.getChildAt(0);
        TextView textView3 = (TextView) child3.getChildAt(0);
        assertEquals("Hello", textView0.getText());
        assertEquals("World", textView1.getText());
        assertEquals("Hola", textView2.getText());
        assertEquals("Mundo", textView3.getText());
    }

    @Test
    public void testSetRemoteAdapter_stackView() {
        RemoteViews item0 = new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout);
        item0.setTextViewText(android.R.id.text1, "Hello");

        RemoteViews item1 = new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout);
        item1.setTextViewText(android.R.id.text1, "World");

        RemoteCollectionItems items = new RemoteCollectionItems.Builder()
                .addItem(10 /* id= */, item0)
                .addItem(11 /* id= */, item1)
                .build();

        try {
            mActivityRule.runOnUiThread(
                    () -> {
                        mListView.setVisibility(View.GONE);
                        mStackView.setVisibility(View.VISIBLE);
                    });
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        mRemoteViews.setRemoteAdapter(R.id.remoteView_stack, items);
        reapplyRemoteViews(mStackView);

        Adapter adapter = mStackView.getAdapter();
        assertEquals(2, adapter.getCount());
        assertEquals(1, adapter.getViewTypeCount());
        assertEquals(adapter.getItemViewType(0), adapter.getItemViewType(1));
        assertEquals(10, adapter.getItemId(0));
        assertEquals(11, adapter.getItemId(1));
    }

    @Test
    public void testSetRemoteAdapter_viewFlipper() {
        RemoteViews item0 = new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout);
        item0.setTextViewText(android.R.id.text1, "Hello");

        RemoteViews item1 = new RemoteViews(PACKAGE_NAME, R.layout.listitemfixed_layout);
        item1.setTextViewText(android.R.id.text1, "World");

        RemoteCollectionItems items = new RemoteCollectionItems.Builder()
                .addItem(10 /* id= */, item0)
                .addItem(11 /* id= */, item1)
                .build();

        runOnMainAndDrawSync(
                mActivityRule,
                mAdapterViewFlipper, () -> {
                    mListView.setVisibility(View.GONE);
                    mAdapterViewFlipper.setVisibility(View.VISIBLE);
                    mRemoteViews.setRemoteAdapter(R.id.remoteView_flipper, items);
                    mRemoteViews.reapply(mActivity, mView);
                });
        try {
            mActivityRule.runOnUiThread(
                    () -> {
                        mListView.setVisibility(View.GONE);
                        mAdapterViewFlipper.setVisibility(View.VISIBLE);
                    });
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        mRemoteViews.setRemoteAdapter(R.id.remoteView_flipper, items);
        reapplyRemoteViews(mAdapterViewFlipper);

        Adapter adapter = mAdapterViewFlipper.getAdapter();
        assertEquals(2, adapter.getCount());
        assertEquals(1, adapter.getViewTypeCount());
        assertEquals(adapter.getItemViewType(0), adapter.getItemViewType(1));
        assertEquals(10, adapter.getItemId(0));
        assertEquals(11, adapter.getItemId(1));
    }

    /**
     * Serialize and deserialize the RemoteCollectionItems to test serialization code. When the
     * isProtoTest parameter is true, this function uses proto serialization instead of parcels.
     */
    private RemoteCollectionItems parcelAndUnparcel(RemoteCollectionItems items) {
        if (isProtoTest) {
            ProtoOutputStream out = new ProtoOutputStream();
            items.writeToProto(mActivity, out);
            ProtoInputStream in = new ProtoInputStream(out.getBytes());
            try {
                return RemoteCollectionItems.createFromProto(mActivity, in);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            Parcel parcel = Parcel.obtain();
            items.writeToParcel(parcel, /* flags= */ 0);
            parcel.setDataPosition(0);
            return RemoteCollectionItems.CREATOR.createFromParcel(parcel);
        }
    }

    /**
     * Serialize the RemoteCollectionItems and return its size in bytes. When the isProtoTest
     * parameter is true, this function uses proto serialization instead of parcels.
     */
    private int parcelAndGetDataSize(RemoteCollectionItems items) {
        if (isProtoTest) {
            ProtoOutputStream out = new ProtoOutputStream();
            items.writeToProto(mActivity, out);
            return out.getBytes().length;
        } else {
            Parcel parcel = Parcel.obtain();
            items.writeToParcel(parcel, /* flags= */ 0);
            parcel.setDataPosition(0);
            try {
                return parcel.dataSize();
            } finally {
                parcel.recycle();
            }
        }
    }

    /**
     * Serialize the RemoteViews and return its size in bytes. When the isProtoTest parameter is
     * true, this function uses proto serialization instead of parcels.
     */
    private int parcelAndGetDataSize(RemoteViews views) {
        if (isProtoTest) {
            ProtoOutputStream out = new ProtoOutputStream();
            views.writePreviewToProto(mActivity, out);
            return out.getBytes().length;
        } else {
            Parcel parcel = Parcel.obtain();
            views.writeToParcel(parcel, /* flags= */ 0);
            parcel.setDataPosition(0);
            try {
                return parcel.dataSize();
            } finally {
                parcel.recycle();
            }
        }
    }

    private void applyRemoteViews(View syncView) {
        runOnMainAndDrawSync(
                mActivityRule,
                syncView,
                () -> {
                    try {
                        mView =
                                RemoteViewsUtil.applyRemoteViews(
                                        mActivityRule, mActivity, mRemoteViews, isProtoTest);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void applyRemoteViews() {
        applyRemoteViews(mActivity.findViewById(R.id.remoteView_host));
    }

    private void reapplyRemoteViews(View syncView) {
        runOnMainAndDrawSync(
                mActivityRule,
                syncView,
                () -> {
                    try {
                        RemoteViewsUtil.reapplyRemoteViews(
                                mActivityRule, mActivity, mRemoteViews, mView, isProtoTest);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private void reapplyRemoteViews() {
        reapplyRemoteViews(mView);
    }

    private static void assertLessThan(int expected, int actual) {
        if (actual >= expected) {
            fail("Expected to be less than " + expected + ", but was " + actual);
        }
    }

    private static final class MockBroadcastReceiver extends BroadcastReceiver {

        Intent mIntent;
        private CountDownLatch mCountDownLatch;

        @Override
        public synchronized void onReceive(Context context, Intent intent) {
            mIntent = intent;
            if (mCountDownLatch != null) {
                mCountDownLatch.countDown();
                mCountDownLatch = null;
            }
        }

        /** Waits for an intent to be received and returns it. */
        public Intent awaitIntent() {
            CountDownLatch countDownLatch;
            synchronized (this) {
                // If we already have an intent, don't wait and just return it now.
                if (mIntent != null) return mIntent;

                countDownLatch = new CountDownLatch(1);
                mCountDownLatch = countDownLatch;
            }

            try {
                // Note: if the latch already counted down, this will return true immediately.
                countDownLatch.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            synchronized (this) {
                if (mIntent == null) {
                    fail("Expected to receive a broadcast within 20 seconds");
                }

                return mIntent;
            }
        }
    }

    private static RemoteCollectionItems createSampleCollectionItems(int size) {
        RemoteCollectionItems.Builder builder = new RemoteCollectionItems.Builder();
        for (int i = 0; i < size; i++) {
            builder.addItem(i,
                    new RemoteViews(PACKAGE_NAME, R.layout.textview_singleline));
        }
        return builder.build();
    }

    private static RemoteCollectionItems createSampleMultiPackageCollectionItems(
            int size, String otherPackage) {
        RemoteCollectionItems.Builder builder = new RemoteCollectionItems.Builder();
        for (int i = 0; i < size; i++) {
            String packageName = i % 2 == 0 ? PACKAGE_NAME : otherPackage;
            builder.addItem(i, new RemoteViews(packageName, android.R.layout.list_content));
        }
        return builder.build();
    }

    /**
     * Returns a different package on the device that can be used for testing multi-package
     * collections. Tries to find a package name under 40 characters so that the resulting
     * collection serialization size is more predictable.
     */
    private String getAnotherPackageName() {
        Optional<String> name = mActivity.getPackageManager()
                .getInstalledApplications(/* flags= */ 0)
                .stream()
                .filter(info -> !PACKAGE_NAME.equals(info.packageName)
                        && info.packageName.length() < 40)
                .findFirst()
                .map(info -> info.packageName);
        assumeTrue("Skipping serialization size test; no packageName under 40 chars found",
                name.isPresent());
        return name.get();
    }
}
