/*
 * Copyright (C) 2014 Michell Bak
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

package com.miz.mizuu;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

public class Prefs extends PreferenceFragment implements OnSharedPreferenceChangeListener {

	Preference pref;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		int res=getActivity().getResources().getIdentifier(getArguments().getString("resource"), "xml", getActivity().getPackageName());
		addPreferencesFromResource(res);

		PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);

		pref = getPreferenceScreen().findPreference("prefsIgnoredFiles");
		if (pref != null)
			pref.setEnabled(PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("prefsIgnoredFilesEnabled", false));
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("prefsIgnoredFilesEnabled")) {
			if (pref != null)
				pref.setEnabled(sharedPreferences.getBoolean("prefsIgnoredFilesEnabled", false));
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(this);
	}
}