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

import java.util.ArrayList;
import java.util.Locale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap.Config;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.miz.base.MizActivity;
import com.miz.functions.AsyncTask;
import com.miz.functions.DecryptedMovie;
import com.miz.functions.MizLib;
import com.miz.functions.TMDb;
import com.miz.functions.TMDbMovie;
import com.miz.service.TheMovieDB;
import com.squareup.picasso.Picasso;

public class IdentifyMovie extends MizActivity {

	public String filename;
	private ArrayList<Result> results = new ArrayList<Result>();
	private ListView lv;
	private EditText searchText;
	private ProgressBar pbar;
	private StartSearch startSearch;
	private long rowId;
	private boolean localizedInfo;
	private ListAdapter mAdapter;
	private SharedPreferences settings;
	private CheckBox useSystemLanguage;
	private Locale locale;
	private DecryptedMovie mMovie;
	private Picasso mPicasso;
	private Config mConfig;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		setContentView(R.layout.identify_movie);

		// Initialize the PreferenceManager variable and preference variable(s)
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		localizedInfo = settings.getBoolean("prefsUseLocalData", false);

		mPicasso = MizuuApplication.getPicasso(this);
		mConfig = MizuuApplication.getBitmapConfig();

		rowId = Long.valueOf(getIntent().getExtras().getString("rowId"));
		filename = getIntent().getExtras().getString("fileName");
		mMovie = MizLib.decryptMovie(filename, settings.getString("ignoredTags", ""));

		locale = Locale.getDefault();

		pbar = (ProgressBar) findViewById(R.id.pbar);

		useSystemLanguage = (CheckBox) findViewById(R.id.searchLanguage);
		useSystemLanguage.setText(getString(R.string.searchIn) + " " + locale.getDisplayLanguage(Locale.ENGLISH));
		if (localizedInfo)
			useSystemLanguage.setChecked(true);

		lv = (ListView) findViewById(android.R.id.list);
		lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				updateMovie(arg2);
			}
		});
		lv.setEmptyView(findViewById(R.id.no_results));
		findViewById(R.id.no_results).setVisibility(View.GONE);

		searchText = (EditText) findViewById(R.id.search);
		searchText.setText(mMovie.getDecryptedFileName());
		searchText.setSelection(searchText.length());
		searchText.setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_DOWN || actionId == EditorInfo.IME_ACTION_SEARCH)
					searchForMovies();
				return true;
			}
		});

		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter("mizuu-movies-identification"));

		startSearch = new StartSearch();

		if (MizLib.isOnline(this)) {
			startSearch.execute(searchText.getText().toString());
		} else {
			Toast.makeText(getApplicationContext(), getString(R.string.noInternet), Toast.LENGTH_SHORT).show();
		}
	}

	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			IdentifyMovie.this.setResult(2);
			finish();
			return;
		}
	};

	public void searchForMovies(View v) {
		searchForMovies();
	}

	private void searchForMovies() {
		results.clear();
		if (MizLib.isOnline(this)) {
			if (!searchText.getText().toString().isEmpty()) {
				startSearch.cancel(true);
				startSearch = new StartSearch();
				startSearch.execute(searchText.getText().toString());
			} else mAdapter.notifyDataSetChanged();
		} else Toast.makeText(getApplicationContext(), getString(R.string.noInternet), Toast.LENGTH_SHORT).show();
	}

	protected class StartSearch extends AsyncTask<String, String, String> {

		@Override
		protected void onPreExecute() {
			showProgressBar();
		}

		@Override
		protected String doInBackground(String... params) {
			try {
				TMDb tmdb = new TMDb(getApplicationContext());
				ArrayList<TMDbMovie> movieResults;
				if (useSystemLanguage.isChecked())
					movieResults = tmdb.searchForMovies(params[0], "", getLocaleShortcode());
				else
					movieResults = tmdb.searchForMovies(params[0], "", "en");

				int count = movieResults.size();
				for (int i = 0; i < count; i++) {
					results.add(new Result(
							movieResults.get(i).getTitle(),
							movieResults.get(i).getId(),
							movieResults.get(i).getCover(),
							movieResults.get(i).getOriginalTitle(),
							movieResults.get(i).getReleasedate())
							);
				}
				return "";
			} catch (Exception e) {}
			return null;
		}

		@Override
		protected void onPostExecute(String result) {
			hideProgressBar();
			if (searchText.getText().toString().length() > 0) {
				if (lv.getAdapter() == null) {
					mAdapter = new ListAdapter(getApplicationContext());
					lv.setAdapter(mAdapter);
				}
				
				mAdapter.notifyDataSetChanged();
			}
		}
	}

	private String getLocaleShortcode() {
		String language = locale.toString();
		if (language.contains("_"))
			language = language.substring(0, language.indexOf("_"));
		return language;
	}

	private void updateMovie(int id) {
		if (MizLib.isOnline(this)) {
			Toast.makeText(this, getString(R.string.updatingMovieInfo), Toast.LENGTH_LONG).show();

			Intent tmdbIntent = new Intent(this, TheMovieDB.class);
			Bundle b = new Bundle();
			b.putString("filepath", filename);
			b.putBoolean("isFromManualIdentify", true);
			b.putLong("rowId", rowId);
			b.putString("tmdbId", results.get(id).getId());
			if (useSystemLanguage.isChecked())
				b.putString("language", getLocaleShortcode());
			tmdbIntent.putExtras(b);

			startService(tmdbIntent);
		} else
			Toast.makeText(this, getString(R.string.noInternet), Toast.LENGTH_SHORT).show();
	}

	static class ViewHolder {
		TextView title, orig_title, release;
		ImageView cover;
		LinearLayout layout;
	}

	public class ListAdapter extends BaseAdapter {

		private LayoutInflater inflater;
		private final Context mContext;
		private int mItemHeight = 0;
		private GridView.LayoutParams mImageViewLayoutParams;

		public ListAdapter(Context context) {
			super();
			mContext = context;
			inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			mImageViewLayoutParams = new ListView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
		}

		public int getCount() {
			return results.size();
		}

		// create a new ImageView for each item referenced by the Adapter
		public View getView(int position, View convertView, ViewGroup parent) {

			ViewHolder holder;

			if (convertView == null) {
				convertView = inflater.inflate(R.layout.list_item_movie, parent, false);

				holder = new ViewHolder();
				holder.title = (TextView) convertView.findViewById(R.id.text);
				holder.orig_title = (TextView) convertView.findViewById(R.id.origTitle);
				holder.release = (TextView) convertView.findViewById(R.id.releasedate);
				holder.cover = (ImageView) convertView.findViewById(R.id.cover);
				holder.layout = (LinearLayout) convertView.findViewById(R.id.cover_layout);

				// Check the height matches our calculated column width
				if (holder.layout.getLayoutParams().height != mItemHeight) {
					holder.layout.setLayoutParams(mImageViewLayoutParams);
				}
				
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			holder.title.setText(results.get(position).getName());
			holder.orig_title.setText(results.get(position).getOriginalTitle());
			holder.release.setText(results.get(position).getRelease());
			
			mPicasso.load(results.get(position).getPic()).placeholder(R.drawable.gray).error(R.drawable.loading_image).config(mConfig).into(holder.cover);

			return convertView;
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			onBackPressed();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		getActionBar().setDisplayHomeAsUpEnabled(true);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (mAdapter != null) mAdapter.notifyDataSetChanged();
	}

	public class Result {
		String name, id, pic, originaltitle, release;

		public Result(String name, String id, String pic, String originalTitle, String release) {
			this.name = name;
			this.id = id;
			this.pic = pic;
			this.originaltitle = originalTitle;
			this.release = release;
		}

		public String getName() {
			return name;
		}

		public String getId() {
			return id;
		}

		public String getPic() {
			return pic;
		}

		public String getOriginalTitle() {
			return originaltitle;
		}

		public String getRelease() {
			if (release.equals("null"))
				return getString(R.string.unknownYear);
			return release;
		}
	}

	private void showProgressBar() {
		lv.setVisibility(View.GONE);
		pbar.setVisibility(View.VISIBLE);
	}

	private void hideProgressBar() {
		lv.setVisibility(View.VISIBLE);
		pbar.setVisibility(View.GONE);
	}
}