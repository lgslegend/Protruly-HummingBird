/*
* Copyright (C) 2015 The Android Open Source Project
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

package com.android.packageinstaller.permission.ui;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import com.android.packageinstaller.R;
import com.android.packageinstaller.permission.utils.Utils;
import com.hb.themeicon.theme.IconManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import hb.app.HbActivity;
import hb.preference.Preference;
import hb.preference.PreferenceCategory;
import hb.preference.PreferenceGroup;
import hb.widget.toolbar.Toolbar;

public final class HMBAllAppPermissionsFragment extends HMBSettingsWithHeader {

    private static final String LOG_TAG = "HMBAllAppPermissionsFragment";

    private static final String KEY_OTHER = "other_perms";

    public static HMBAllAppPermissionsFragment newInstance(String packageName) {
        HMBAllAppPermissionsFragment instance = new HMBAllAppPermissionsFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        instance.setArguments(arguments);
        return instance;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setTitle(R.string.all_permissions);
            ab.setDisplayHomeAsUpEnabled(true);
        }

        Toolbar toolbar = ((HbActivity) getActivity()).getToolbar();
        if (toolbar != null) {
            toolbar.setTitle(getString(R.string.app_permissions));
            toolbar.setNavigationIcon(com.hb.R.drawable.ic_toolbar_back);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    getActivity().finish();
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUi();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                getFragmentManager().popBackStack();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateUi() {
        if (getPreferenceScreen() != null) {
            getPreferenceScreen().removeAll();
        }
        addPreferencesFromResource(R.xml.all_permissions);
        PreferenceGroup otherGroup = (PreferenceGroup) findPreference(KEY_OTHER);
        ArrayList<Preference> prefs = new ArrayList<>(); // Used for sorting.
        prefs.add(otherGroup);
        String pkg = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        otherGroup.removeAll();
        PackageManager pm = getContext().getPackageManager();

        try {
            PackageInfo info = pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS);

            ApplicationInfo appInfo = info.applicationInfo;
            IconManager iconManager = IconManager.getInstance(getActivity(), true, false);
            final Drawable icon = iconManager.getIconDrawable(appInfo.packageName, null);
            final CharSequence label = appInfo.loadLabel(pm);
            Intent infoIntent = null;
            if (!getActivity().getIntent().getBooleanExtra(
                    AppPermissionsFragment.EXTRA_HIDE_INFO_BUTTON, false)) {
                infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", pkg, null));
            }
            setHeader(icon, label, infoIntent);

            if (info.requestedPermissions != null) {
                for (int i = 0; i < info.requestedPermissions.length; i++) {
                    PermissionInfo perm;
                    try {
                        perm = pm.getPermissionInfo(info.requestedPermissions[i], 0);
                    } catch (NameNotFoundException e) {
                        Log.e(LOG_TAG,
                                "Can't get permission info for " + info.requestedPermissions[i], e);
                        continue;
                    }

                    if ((perm.flags & PermissionInfo.FLAG_INSTALLED) == 0
                            || (perm.flags & PermissionInfo.FLAG_HIDDEN) != 0) {
                        continue;
                    }

                    if (perm.protectionLevel == PermissionInfo.PROTECTION_DANGEROUS) {
                        PermissionGroupInfo group = getGroup(perm.group, pm);
                        PreferenceGroup pref =
                                findOrCreate(group != null ? group : perm, pm, prefs);
                        pref.addPreference(getPreference(perm, group, pm));
                    } else if (perm.protectionLevel == PermissionInfo.PROTECTION_NORMAL) {
                        PermissionGroupInfo group = getGroup(perm.group, pm);
                        otherGroup.addPreference(getPreference(perm, group, pm));
                    }
                }
            }
        } catch (NameNotFoundException e) {
            Log.e(LOG_TAG, "Problem getting package info for " + pkg, e);
        }
        // Sort an ArrayList of the groups and then set the order from the sorting.
        Collections.sort(prefs, new Comparator<Preference>() {
            @Override
            public int compare(Preference lhs, Preference rhs) {
                String lKey = lhs.getKey();
                String rKey = rhs.getKey();
                if (lKey.equals(KEY_OTHER)) {
                    return 1;
                } else if (rKey.equals(KEY_OTHER)) {
                    return -1;
                } else if (Utils.isModernPermissionGroup(lKey)
                        != Utils.isModernPermissionGroup(rKey)) {
                    return Utils.isModernPermissionGroup(lKey) ? -1 : 1;
                }
                return lhs.getTitle().toString().compareTo(rhs.getTitle().toString());
            }
        });
        for (int i = 0; i < prefs.size(); i++) {
            prefs.get(i).setOrder(i);
        }

        if (getPreferenceScreen().getPreferenceCount() > 0) {
            showEmptyView(false);
        }
    }

    private PermissionGroupInfo getGroup(String group, PackageManager pm) {
        try {
            return pm.getPermissionGroupInfo(group, 0);
        } catch (NameNotFoundException e) {
            return null;
        }
    }

    private PreferenceGroup findOrCreate(PackageItemInfo group, PackageManager pm,
            ArrayList<Preference> prefs) {
        PreferenceGroup pref = (PreferenceGroup) findPreference(group.name);
        if (pref == null) {
            pref = new PreferenceCategory(getContext());
            pref.setKey(group.name);
//            pref.setLayoutResource(R.layout.preference_category_material);
            pref.setTitle(group.loadLabel(pm));
            prefs.add(pref);
            getPreferenceScreen().addPreference(pref);
        }
        return pref;
    }

    private Preference getPreference(PermissionInfo perm, PermissionGroupInfo group,
            PackageManager pm) {
        Preference pref = new Preference(getActivity());
//        pref.setLayoutResource(R.layout.preference_permissions);
        pref.setLayoutResource(com.hb.R.layout.preference_material_hb);
        Drawable icon = null;

        if (perm.protectionLevel == PermissionInfo.PROTECTION_NORMAL) {
            icon = getContext().getDrawable(R.drawable.perm_group_other);
        } else {
            if (perm.icon != 0) {
                icon = perm.loadIcon(pm);
            } else if (group != null && group.icon != 0) {
                icon = group.loadIcon(pm);
            } else {
                icon = getContext().getDrawable(R.drawable.ic_perm_device_info);
            }
        }

        pref.setIcon(Utils.getPermissionGroupsIcon(getContext(), perm.group, icon, false));
        pref.setTitle(perm.loadLabel(pm));
        final CharSequence desc = perm.loadDescription(pm);
        pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                new AlertDialog.Builder(getActivity())
                        .setMessage(desc)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return true;
            }
        });

        return pref;
    }
}