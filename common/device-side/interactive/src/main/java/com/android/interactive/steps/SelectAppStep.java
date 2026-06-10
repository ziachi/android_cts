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

package com.android.interactive.steps;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.Package;
import com.android.interactive.R;
import com.android.interactive.Step;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** A {@link Step} to show installed APPs to let the user select one. */
public class SelectAppStep extends Step<Package> {

    /** The {@link ArrayAdapter} to render a list of APPs and to handle the click event. */
    private static final class AppAdapter extends ArrayAdapter<ResolveInfo> {

        /** View caches to help render an APP. */
        private static final class ViewHolder {

            final ImageView mIcon;
            final TextView mAppName;
            final TextView mAppPackage;

            ViewHolder(View v) {
                mIcon = (ImageView) v.findViewById(R.id.icon);
                mAppName = (TextView) v.findViewById(R.id.name);
                mAppPackage = (TextView) v.findViewById(R.id.app_package);
            }
        }

        private final Context mContext;
        // The data to render the list.
        private final List<ResolveInfo> mResolveInfos;
        // The step the list is rendering for.
        private final SelectAppStep mSelectAppStep;

        AppAdapter(
                Context context,
                int textViewResourceId,
                List<ResolveInfo> resolveInfos,
                SelectAppStep selectAppStep) {
            super(context, textViewResourceId, resolveInfos);
            mContext = context;
            mResolveInfos = resolveInfos;
            mSelectAppStep = selectAppStep;
        }

        @Override
        public int getCount() {
            return mResolveInfos.size();
        }

        @Override
        public ResolveInfo getItem(int position) {
            return mResolveInfos.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mResolveInfos.indexOf(getItem(position));
        }

        @Override
        public View getView(int position, View appItemView, ViewGroup parent) {
            ViewHolder viewHolder;
            if (appItemView == null) {
                LayoutInflater inflater =
                        (LayoutInflater)
                                mContext.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
                appItemView = inflater.inflate(R.layout.app_item, parent, false);
                viewHolder = new ViewHolder(appItemView);
                appItemView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) appItemView.getTag();
            }

            final ResolveInfo resolveInfo = getItem(position);
            PackageManager packageManager = mContext.getPackageManager();
            viewHolder.mIcon.setImageDrawable(resolveInfo.loadIcon(packageManager));
            viewHolder.mAppName.setText(resolveInfo.loadLabel(packageManager));
            viewHolder.mAppPackage.setText(resolveInfo.activityInfo.packageName);

            appItemView.setOnClickListener(
                    new View.OnClickListener() {

                        @Override
                        public void onClick(View v) {
                            mSelectAppStep.appSelected(resolveInfo);
                        }
                    });

            return appItemView;
        }
    }

    protected final String mInstruction;

    // Determine if a queried APP is matched to to be shown in the step.
    protected final Predicate<Package> mPackageMatcher;

    protected SelectAppStep(String instruction) {
        this(instruction, /* packageMatcher= */ p -> true);
    }

    protected SelectAppStep(String instruction, List<Package> packages) {
        this(instruction, p -> packages.contains(p));
    }

    protected SelectAppStep(String instruction, Predicate<Package> packageMatcher) {
        mInstruction = instruction;
        mPackageMatcher = packageMatcher;
    }

    /**
     * Callback when the APP is selected. Override it if there's any customized logic in a sub-step.
     */
    protected void appSelected(ResolveInfo resolveInfo) {
        pass(Package.of(resolveInfo.activityInfo.packageName));
        close();
    }

    @Override
    public void interact() {
        showWithArrayAdapter(mInstruction, getAppAdapter());
    }

    /** Gets the {@link AppAdapter} to help render a list of APPs. */
    private AppAdapter getAppAdapter() {
        // Filter out all system APPs (without the main activity).
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        Context instrumentationContext = TestApis.context().instrumentationContext();
        List<ResolveInfo> allResolveInfos =
                instrumentationContext.getPackageManager().queryIntentActivities(mainIntent, 0);

        List<ResolveInfo> filteredResolveInfos = new ArrayList<>();
        for (ResolveInfo resolveInfo : allResolveInfos) {
            if (mPackageMatcher.test(Package.of(resolveInfo.activityInfo.packageName))) {
                filteredResolveInfos.add(resolveInfo);
            }
        }

        return new AppAdapter(
                instrumentationContext,
                android.R.layout.simple_list_item_1,
                filteredResolveInfos,
                this);
    }
}
