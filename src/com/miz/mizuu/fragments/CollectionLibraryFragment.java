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

package com.miz.mizuu.fragments;

import java.io.File;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Pattern;

import jcifs.smb.SmbFile;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.ActionBar.OnNavigationListener;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.graphics.Bitmap.Config;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.SectionIndexer;
import android.widget.TextView;

import com.miz.db.DbAdapter;
import com.miz.db.DbHelper;
import com.miz.functions.AsyncTask;
import com.miz.functions.CoverItem;
import com.miz.functions.FileSource;
import com.miz.functions.MediumMovie;
import com.miz.functions.MizLib;
import com.miz.functions.SQLiteCursorLoader;
import com.miz.functions.SpinnerItem;
import com.miz.mizuu.MizuuApplication;
import com.miz.mizuu.MovieDetails;
import com.miz.mizuu.Preferences;
import com.miz.mizuu.R;
import com.squareup.picasso.Picasso;

public class CollectionLibraryFragment extends Fragment implements OnNavigationListener, OnSharedPreferenceChangeListener {

	public static final String ALL = "ALL", AVAILABLE = "AVAILABLE", FAVORITES = "FAVORITES", WATCHLIST = "WATCHLIST";

	private SharedPreferences settings;
	private int mImageThumbSize, mImageThumbSpacing, mResizedWidth, mResizedHeight;
	private LoaderAdapter mAdapter;
	private ArrayList<MediumMovie> movies = new ArrayList<MediumMovie>(), shownMovies = new ArrayList<MediumMovie>();
	private GridView mGridView = null;
	private ProgressBar pbar;
	private boolean mShowTitles, ignorePrefixes, prefsDisableEthernetWiFiCheck, ignoreNfo;
	private ActionBar actionBar;
	private String collectionId;
	private ArrayList<SpinnerItem> spinnerItems = new ArrayList<SpinnerItem>();
	private ActionBarSpinner spinnerAdapter;
	private SearchTask mSearch;
	private Picasso mPicasso;
	private Config mConfig;

	/**
	 * Empty constructor as per the Fragment documentation
	 */
	public CollectionLibraryFragment() {}

	public static CollectionLibraryFragment newInstance(String collectionId, String collectionTitle) {
		CollectionLibraryFragment frag = new CollectionLibraryFragment();
		Bundle b = new Bundle();
		b.putString("collectionId", collectionId);
		b.putString("collectionTitle", collectionTitle);
		frag.setArguments(b);		
		return frag;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {		
		super.onCreate(savedInstanceState);

		collectionId = getArguments().getString("collectionId", "");
		if (MizLib.isEmpty(collectionId)) {
			getActivity().finish();
			return;
		}

		setRetainInstance(true);
		setHasOptionsMenu(true);

		setupSpinnerItems();

		// Set OnSharedPreferenceChange listener
		PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);

		// Initialize the PreferenceManager variable and preference variable(s)
		settings = PreferenceManager.getDefaultSharedPreferences(getActivity());

		mShowTitles = settings.getBoolean("prefsShowGridTitles", false);
		ignorePrefixes = settings.getBoolean("prefsIgnorePrefixesInTitles", false);
		ignoreNfo = settings.getBoolean("prefsIgnoreNfoFiles", true);
		prefsDisableEthernetWiFiCheck = settings.getBoolean("prefsDisableEthernetWiFiCheck", false);

		String thumbnailSize = settings.getString("prefsGridItemSize", getString(R.string.normal));
		if (thumbnailSize.equals(getString(R.string.normal))) 
			mImageThumbSize = (int) (getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size) * 1);
		else
			mImageThumbSize = (int) (getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size) * 0.75);
		mImageThumbSpacing = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_spacing);

		mPicasso = MizuuApplication.getPicasso(getActivity());
		mConfig = MizuuApplication.getBitmapConfig();

		mAdapter = new LoaderAdapter(getActivity());
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		// Setup ActionBar with the action list
		actionBar = getActivity().getActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
		spinnerAdapter = new ActionBarSpinner();
		actionBar.setListNavigationCallbacks(spinnerAdapter, this);

		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mMessageReceiver, new IntentFilter("mizuu-movies-update"));
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mMessageReceiver, new IntentFilter("mizuu-library-change"));
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mMessageReceiver, new IntentFilter("mizuu-movie-cover-change"));
	}

	private void setupSpinnerItems() {
		spinnerItems.clear();
		spinnerItems.add(new SpinnerItem(getArguments().getString("collectionTitle", getString(R.string.chooserMovies)), getString(R.string.choiceAllMovies)));
		spinnerItems.add(new SpinnerItem(getString(R.string.choiceFavorites), getString(R.string.choiceFavorites)));
		spinnerItems.add(new SpinnerItem(getString(R.string.choiceAvailableFiles), getString(R.string.choiceAvailableFiles)));
		spinnerItems.add(new SpinnerItem(getString(R.string.choiceWatchedMovies), getString(R.string.choiceWatchedMovies)));
		spinnerItems.add(new SpinnerItem(getString(R.string.choiceUnwatchedMovies), getString(R.string.choiceUnwatchedMovies)));
	}

	private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (intent.filterEquals(new Intent("mizuu-library-change")) || intent.filterEquals(new Intent("mizuu-movie-cover-change"))) {
				clearCaches();
			}	
			if (getLoaderManager().getLoader(0) == null)
				getLoaderManager().initLoader(0, null, loaderCallbacks);
			else
				forceLoaderLoad();
		}
	};

	LoaderCallbacks<Cursor> loaderCallbacks = new LoaderCallbacks<Cursor>() {
		@Override
		public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
			return new SQLiteCursorLoader(getActivity(), DbHelper.getHelper(getActivity()), DbAdapter.DATABASE_TABLE, DbAdapter.SELECT_ALL, DbAdapter.KEY_EXTRA_2 + " = '" + collectionId + "'", null, DbAdapter.KEY_TITLE, null, DbAdapter.KEY_TITLE + " ASC");
		}

		@Override
		public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
			movies.clear();
			shownMovies.clear();

			// Do while the cursor can move to the next item in cursor
			while (cursor.moveToNext()) {
				try {
					movies.add(new MediumMovie(getActivity(),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_ROWID)),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_FILEPATH)),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_TITLE)),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_TMDBID)),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_RATING)),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_RELEASEDATE)),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_GENRES)),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_FAVOURITE)),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_CAST)),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_COLLECTION)),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_EXTRA_2)),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_TO_WATCH)),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_HAS_WATCHED)),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_EXTRA_1)),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_CERTIFICATION)),
							cursor.getString(cursor.getColumnIndex(DbAdapter.KEY_RUNTIME)),
							ignorePrefixes,
							ignoreNfo
							));
				} catch (NullPointerException e) {}
			}

			shownMovies.addAll(movies);

			showCollectionBasedOnNavigationIndex(actionBar.getSelectedNavigationIndex());
		}

		@Override
		public void onLoaderReset(Loader<Cursor> arg0) {
			movies.clear();
			shownMovies.clear();
			notifyDataSetChanged();
		}
	};

	private void clearCaches() {
		if (isAdded())
			MizuuApplication.getLruCache(getActivity()).clear();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {		
		final View v = inflater.inflate(R.layout.image_grid_fragment, container, false);

		pbar = (ProgressBar) v.findViewById(R.id.progress);

		mAdapter = new LoaderAdapter(getActivity());

		mGridView = (GridView) v.findViewById(R.id.gridView);
		mGridView.setFastScrollEnabled(true);
		mGridView.setAdapter(mAdapter);
		mGridView.setColumnWidth(mImageThumbSize);

		// Calculate the total column width to set item heights by factor 1.5
		mGridView.getViewTreeObserver().addOnGlobalLayoutListener(
				new ViewTreeObserver.OnGlobalLayoutListener() {
					@SuppressLint("NewApi")
					@SuppressWarnings("deprecation")
					@Override
					public void onGlobalLayout() {
						if (mAdapter.getNumColumns() == 0) {
							final int numColumns = (int) Math.floor(mGridView.getWidth() / (mImageThumbSize + mImageThumbSpacing));
							if (numColumns > 0) {
								mAdapter.setNumColumns(numColumns);
								mResizedWidth = (int) (((mGridView.getWidth() - (numColumns * mImageThumbSpacing))
										/ numColumns) * 1.1); // * 1.1 is a hack to make images look slightly less blurry
								mResizedHeight = (int) (mResizedWidth * 1.5);
							}

							if (MizLib.hasJellyBean()) {
								mGridView.getViewTreeObserver()
								.removeOnGlobalLayoutListener(this);
							} else {
								mGridView.getViewTreeObserver()
								.removeGlobalOnLayoutListener(this);
							}
						}
					}
				});
		mGridView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				Intent intent = new Intent();
				intent.putExtra("rowId", Integer.parseInt(shownMovies.get(arg2).getRowId()));
				intent.setClass(getActivity(), MovieDetails.class);
				startActivityForResult(intent, 0);
			}
		});

		return v;
	}

	@Override
	public void onResume() {
		super.onResume();

		if (movies.size() == 0)
			forceLoaderLoad();

		if (mAdapter != null)
			mAdapter.notifyDataSetChanged();
	}
	@Override
	public void onDestroy() {
		super.onDestroy();		
		// Unregister since the activity is about to be closed.
		LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mMessageReceiver);
		PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(this);
	}

	private class LoaderAdapter extends BaseAdapter implements SectionIndexer {

		private LayoutInflater inflater;
		private final Context mContext;
		private int mNumColumns = 0, mSidePadding, mBottomPadding, mCard, mCardBackground, mCardTitleColor;
		private Object[] sections;

		public LoaderAdapter(Context context) {
			super();
			mContext = context;
			inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			mSidePadding = MizLib.convertDpToPixels(mContext, 1);
			mBottomPadding = MizLib.convertDpToPixels(mContext, 2);
			mCard = MizuuApplication.getCardDrawable(mContext);
			mCardBackground = MizuuApplication.getCardColor(mContext);
			mCardTitleColor = MizuuApplication.getCardTitleColor(mContext);
		}

		@Override
		public int getCount() {
			return shownMovies.size();
		}

		@Override
		public Object getItem(int position) {
			return position;
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public int getItemViewType(int position) {
			return 0;
		}

		@Override
		public int getViewTypeCount() {
			return 1;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup container) {

			CoverItem holder;
			if (convertView == null) {
				convertView = inflater.inflate(R.layout.grid_item, container, false);
				holder = new CoverItem();

				holder.mLinearLayout = (LinearLayout) convertView.findViewById(R.id.card_layout);
				holder.cover = (ImageView) convertView.findViewById(R.id.cover);
				holder.text = (TextView) convertView.findViewById(R.id.text);
				holder.subtext = (TextView) convertView.findViewById(R.id.gridCoverSubtitle);

				holder.mLinearLayout.setBackgroundResource(mCard);
				holder.text.setBackgroundResource(mCardBackground);
				holder.text.setTextColor(mCardTitleColor);
				holder.subtext.setBackgroundResource(mCardBackground);

				convertView.setTag(holder);
			} else {
				holder = (CoverItem) convertView.getTag();
			}

			if (!mShowTitles) {
				holder.text.setVisibility(View.GONE);
				holder.subtext.setVisibility(View.GONE);
				holder.cover.setPadding(mSidePadding, 0, mSidePadding, mBottomPadding);
			} else {
				holder.text.setVisibility(View.VISIBLE);
				holder.subtext.setVisibility(View.VISIBLE);
			}

			holder.text.setText(shownMovies.get(position).getTitle());

			holder.cover.setImageResource(mCardBackground);

			// Finally load the image asynchronously into the ImageView, this also takes care of
			// setting a placeholder image while the background thread runs
			if (mResizedWidth > 0)
				mPicasso.load(shownMovies.get(position).getThumbnail()).resize(mResizedWidth, mResizedHeight).config(mConfig).into(holder);
			else
				mPicasso.load(shownMovies.get(position).getThumbnail()).config(mConfig).into(holder);

			return convertView;
		}

		public void setNumColumns(int numColumns) {
			mNumColumns = numColumns;
		}

		public int getNumColumns() {
			return mNumColumns;
		}

		@Override
		public int getPositionForSection(int section) {
			return section;
		}

		@Override
		public int getSectionForPosition(int position) {
			return position;
		}

		@Override
		public Object[] getSections() {
			return sections;
		}

		@Override
		public void notifyDataSetChanged() {

			ArrayList<MediumMovie> tempMovies = new ArrayList<MediumMovie>(shownMovies);
			sections = new Object[tempMovies.size()];

			String SORT_TYPE = settings.getString("prefsSortingCollections", "sortTitle");
			if (SORT_TYPE.equals("sortRating")) {
				DecimalFormat df = new DecimalFormat("#.#");
				for (int i = 0; i < sections.length; i++)
					sections[i] = df.format(tempMovies.get(i).getRawRating());
			} else if (SORT_TYPE.equals("sortWeightedRating")) {
				DecimalFormat df = new DecimalFormat("#.#");
				for (int i = 0; i < sections.length; i++)
					sections[i] = df.format(tempMovies.get(i).getWeightedRating());
			} else if (SORT_TYPE.equals("sortDuration")) {
				String hour = getResources().getQuantityString(R.plurals.hour, 1, 1).substring(0,1);
				String minute = getResources().getQuantityString(R.plurals.minute, 1, 1).substring(0,1);

				for (int i = 0; i < sections.length; i++)
					sections[i] = MizLib.getRuntimeInMinutesOrHours(tempMovies.get(i).getRuntime(), hour, minute);
			} else {
				String temp = "";
				for (int i = 0; i < sections.length; i++)
					if (!MizLib.isEmpty(tempMovies.get(i).getTitle())) {
						temp = tempMovies.get(i).getTitle().substring(0,1);
						if (Character.isLetter(temp.charAt(0)))
							sections[i] = tempMovies.get(i).getTitle().substring(0,1);
						else
							sections[i] = "#";
					} else
						sections[i] = "";
			}

			tempMovies.clear();
			tempMovies = null;

			super.notifyDataSetChanged();
		}
	}

	private void notifyDataSetChanged() {
		if (mAdapter != null)
			mAdapter.notifyDataSetChanged();

		if (spinnerAdapter != null)
			spinnerAdapter.notifyDataSetChanged();
	}

	@Override
	public boolean onNavigationItemSelected(int itemPosition, long itemId) {
		showCollectionBasedOnNavigationIndex(itemPosition);
		return true;
	}

	private void showCollectionBasedOnNavigationIndex(int itemPosition) {
		switch (itemPosition) {
		case 0:
			showAllMovies();
			break;
		case 1:
			showFavorites();
			break;
		case 2:
			showAvailableFiles();
			break;
		case 3:
			showWatchedMovies(true);
			break;
		case 4:
			showWatchedMovies(false);
			break;
		}
	}

	private void showAllMovies() {
		showProgressBar();

		shownMovies.clear();

		shownMovies.addAll(movies);

		sortMovies();

		notifyDataSetChanged();

		hideProgressBar();
	}

	private void showFavorites() {
		showProgressBar();

		shownMovies.clear();

		for (int i = 0; i < movies.size(); i++) {
			if (movies.get(i).isFavourite())
				shownMovies.add(movies.get(i));
		}

		sortMovies();

		notifyDataSetChanged();

		hideProgressBar();
	}

	private void showAvailableFiles() {
		showProgressBar();

		shownMovies.clear();

		new Thread() {
			@Override
			public void run() {
				ArrayList<FileSource> filesources = MizLib.getFileSources(MizLib.TYPE_MOVIE, true);

				for (int i = 0; i < movies.size(); i++) {
					if (movies.get(i).isNetworkFile()) {
						if (MizLib.isWifiConnected(getActivity(), prefsDisableEthernetWiFiCheck)) {
							FileSource source = null;

							for (int j = 0; j < filesources.size(); j++)
								if (movies.get(i).getFilepath().contains(filesources.get(j).getFilepath())) {
									source = filesources.get(j);
									continue;
								}

							if (source == null)
								continue;

							try {
								final SmbFile file = new SmbFile(
										MizLib.createSmbLoginString(
												URLEncoder.encode(source.getDomain(), "utf-8"),
												URLEncoder.encode(source.getUser(), "utf-8"),
												URLEncoder.encode(source.getPassword(), "utf-8"),
												movies.get(i).getFilepath(),
												false
												));
								if (file.exists())
									shownMovies.add(movies.get(i));
							} catch (Exception e) {}  // Do nothing - the file isn't available (either MalformedURLException or SmbException)
						}
					} else {
						if (new File(movies.get(i).getFilepath()).exists())
							shownMovies.add(movies.get(i));
					}
				}

				sortMovies();

				getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						notifyDataSetChanged();

						hideProgressBar();
					}	
				});
			}
		}.start();
	}

	private void showWatchedMovies(boolean watched) {
		showProgressBar();

		shownMovies.clear();

		for (int i = 0; i < movies.size(); i++) {
			if (watched) {
				if (movies.get(i).hasWatched())
					shownMovies.add(movies.get(i));
			} else {
				if (!movies.get(i).hasWatched())
					shownMovies.add(movies.get(i));
			}
		}

		sortMovies();

		notifyDataSetChanged();

		hideProgressBar();
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu, menu);
		menu.removeItem(R.id.update);
		SearchView searchView = (SearchView) menu.findItem(R.id.search_textbox).getActionView();
		searchView.setOnQueryTextListener(new OnQueryTextListener() {
			@Override
			public boolean onQueryTextChange(String newText) {
				if (newText.length() > 0) {
					search(newText);
				} else {
					showAllMovies();
				}
				return true;
			}
			@Override
			public boolean onQueryTextSubmit(String query) { return false; }
		});
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {		
		super.onOptionsItemSelected(item);

		switch (item.getItemId()) {
		case R.id.menuSortAdded:
			sortByDateAdded();
			break;
		case R.id.menuSortRating:
			sortByRating();
			break;
		case R.id.menuSortWeightedRating:
			sortByWeightedRating();
			break;
		case R.id.menuSortRelease:
			sortByRelease();
			break;
		case R.id.menuSortTitle:
			sortByTitle();
			break;
		case R.id.menuSortDuration:
			sortByDuration();
			break;
		case R.id.menuSettings:
			startActivity(new Intent(getActivity(), Preferences.class));
			break;
		case R.id.genres:
			showGenres();
			break;
		case R.id.certifications:
			showCertifications();
			break;
		}

		return true;
	}

	private void sortMovies() {
		String SORT_TYPE = settings.getString("prefsSortingCollections", "sortTitle");
		if (SORT_TYPE.equals("sortTitle")) {
			sortByTitle();
		} else if (SORT_TYPE.equals("sortRelease")) {
			sortByRelease();
		} else if (SORT_TYPE.equals("sortRating")) {
			sortByRating();
		} else if (SORT_TYPE.equals("sortWeightedRating")) {
			sortByWeightedRating();
		} else if (SORT_TYPE.equals("sortAdded")) {
			sortByDateAdded();
		} else if (SORT_TYPE.equals("sortDuration")) {
			sortByDuration();
		} else {
			sortByTitle();
		}
	}

	public void sortByTitle() {
		Editor editor = settings.edit();
		editor.putString("prefsSortingCollections", "sortTitle");
		editor.apply();

		sortBy(TITLE);
	}

	public void sortByRelease() {
		Editor editor = settings.edit();
		editor.putString("prefsSortingCollections", "sortRelease");
		editor.apply();

		sortBy(RELEASE);
	}

	public void sortByRating() {
		Editor editor = settings.edit();
		editor.putString("prefsSortingCollections", "sortRating");
		editor.apply();

		sortBy(RATING);
	}

	public void sortByWeightedRating() {
		Editor editor = settings.edit();
		editor.putString("prefsSortingCollections", "sortWeightedRating");
		editor.apply();

		sortBy(WEIGHTED_RATING);
	}

	public void sortByDateAdded() {
		Editor editor = settings.edit();
		editor.putString("prefsSortingCollections", "sortAdded");
		editor.apply();

		sortBy(DATE);
	}

	public void sortByDuration() {
		Editor editor = settings.edit();
		editor.putString("prefsSortingCollections", "sortDuration");
		editor.apply();

		sortBy(DURATION);
	}

	private final int TITLE = 10, RELEASE = 11, RATING = 12, DATE = 13, WEIGHTED_RATING = 14, DURATION = 15;

	public void sortBy(int sort) {

		ArrayList<MediumMovie> tempMovies = new ArrayList<MediumMovie>(shownMovies);

		switch (sort) {	
		case TITLE:

			Collections.sort(tempMovies, new Comparator<MediumMovie>() {
				@Override
				public int compare(MediumMovie o1, MediumMovie o2) {		
					return o1.getTitle().compareToIgnoreCase(o2.getTitle());
				}
			});

			break;
		case RELEASE:

			Collections.sort(tempMovies, new Comparator<MediumMovie>() {
				@Override
				public int compare(MediumMovie o1, MediumMovie o2) {
					// Dates are always presented as YYYY-MM-DD, so removing
					// the hyphens will easily provide a great way of sorting.

					int firstDate = 0, secondDate = 0;
					String first = "", second = "";

					if (o1.getReleasedate() != null)
						first = o1.getReleasedate().replace("-", "");

					if (!(first.equals("null") | first.isEmpty()))
						firstDate = Integer.valueOf(first);

					if (o2.getReleasedate() != null)
						second = o2.getReleasedate().replace("-", "");

					if (!(second.equals("null") | second.isEmpty()))
						secondDate = Integer.valueOf(second);

					// This part is reversed to get the highest numbers first
					if (firstDate < secondDate)
						return 1; // First date is lower than second date - put it second
					else if (firstDate > secondDate)
						return -1; // First date is greater than second date - put it first

					return 0; // They're equal
				}
			});

			break;
		case RATING:

			Collections.sort(tempMovies, new Comparator<MediumMovie>() {
				@Override
				public int compare(MediumMovie o1, MediumMovie o2) {	
					if (o1.getRawRating() < o2.getRawRating()) {
						return 1;
					} else if (o1.getRawRating() > o2.getRawRating())
						return -1;

					return 0;
				}
			});

			break;
		case WEIGHTED_RATING:
			Collections.sort(tempMovies, new Comparator<MediumMovie>() {
				@Override
				public int compare(MediumMovie o1, MediumMovie o2) {	
					if (o1.getWeightedRating() < o2.getWeightedRating())
						return 1;
					else if (o1.getWeightedRating() > o2.getWeightedRating())
						return -1;

					return 0;
				}
			});
			break;
		case DATE:

			Collections.sort(tempMovies, new Comparator<MediumMovie>() {
				@Override
				public int compare(MediumMovie o1, MediumMovie o2) {		
					return o1.getDateAdded().compareTo(o2.getDateAdded()) * -1;
				}
			});

			break;
		case DURATION:

			Collections.sort(tempMovies, new Comparator<MediumMovie>() {
				@Override
				public int compare(MediumMovie o1, MediumMovie o2) {

					int first = Integer.valueOf(o1.getRuntime());
					int second = Integer.valueOf(o2.getRuntime());

					if (first < second)
						return 1;
					else if (first > second)
						return -1;

					return 0;
				}
			});
		}

		shownMovies = new ArrayList<MediumMovie>(tempMovies);

		// Clean up on aisle three...
		tempMovies.clear();
	}

	private void showGenres() {
		showCollectionBasedOnNavigationIndex(actionBar.getSelectedNavigationIndex());

		final TreeMap<String, Integer> map = new TreeMap<String, Integer>();
		String[] splitGenres;
		for (int i = 0; i < shownMovies.size(); i++) {
			splitGenres = shownMovies.get(i).getGenres().split(",");
			for (int j = 0; j < splitGenres.length; j++) {
				if (map.containsKey(splitGenres[j].trim())) {
					map.put(splitGenres[j].trim(), map.get(splitGenres[j].trim()) + 1);
				} else {
					map.put(splitGenres[j].trim(), 1);
				}
			}
		}

		final CharSequence[] tempArray = map.keySet().toArray(new CharSequence[map.keySet().size()]);	
		for (int i = 0; i < tempArray.length; i++)
			tempArray[i] = tempArray[i] + " (" + map.get(tempArray[i]) +  ")";

		final CharSequence[] temp = new CharSequence[tempArray.length + 1];
		temp[0] = getString(R.string.allGenres);

		for (int i = 1; i < temp.length; i++)
			temp[i] = tempArray[i-1];


		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.selectGenre)
		.setItems(temp, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (which > 0) {
					ArrayList<MediumMovie> currentlyShown = new ArrayList<MediumMovie>();
					currentlyShown.addAll(shownMovies);

					shownMovies.clear();

					String selectedGenre = temp[which].toString();
					selectedGenre = selectedGenre.substring(0, selectedGenre.lastIndexOf("(")).trim();

					for (int i = 0; i < currentlyShown.size(); i++)
						if (currentlyShown.get(i).getGenres().contains(selectedGenre))
							shownMovies.add(currentlyShown.get(i));

					sortMovies();
					notifyDataSetChanged();
				}

				dialog.dismiss();
			}
		});
		builder.show();
	}

	private void showCertifications() {
		showCollectionBasedOnNavigationIndex(actionBar.getSelectedNavigationIndex());

		final TreeMap<String, Integer> map = new TreeMap<String, Integer>();
		for (int i = 0; i < shownMovies.size(); i++) {
			String certification = shownMovies.get(i).getCertification();
			if (!MizLib.isEmpty(certification)) {
				if (map.containsKey(certification.trim())) {
					map.put(certification.trim(), map.get(certification.trim()) + 1);
				} else {
					map.put(certification.trim(), 1);
				}
			}
		}

		final CharSequence[] tempArray = map.keySet().toArray(new CharSequence[map.keySet().size()]);	
		for (int i = 0; i < tempArray.length; i++)
			tempArray[i] = tempArray[i] + " (" + map.get(tempArray[i]) +  ")";

		final CharSequence[] temp = new CharSequence[tempArray.length + 1];
		temp[0] = getString(R.string.allCertifications);

		for (int i = 1; i < temp.length; i++)
			temp[i] = tempArray[i-1];


		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.selectCertification)
		.setItems(temp, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				if (which > 0) {
					ArrayList<MediumMovie> currentlyShown = new ArrayList<MediumMovie>();
					currentlyShown.addAll(shownMovies);

					shownMovies.clear();

					String selectedGenre = temp[which].toString();
					selectedGenre = selectedGenre.substring(0, selectedGenre.lastIndexOf("(")).trim();

					for (int i = 0; i < currentlyShown.size(); i++)
						if (currentlyShown.get(i).getCertification().trim().contains(selectedGenre))
							shownMovies.add(currentlyShown.get(i));

					sortMovies();
					notifyDataSetChanged();
				}

				dialog.dismiss();
			}
		});
		builder.show();
	}

	private void search(String query) {
		showProgressBar();

		if (mSearch != null)
			mSearch.cancel(true);

		mSearch = new SearchTask(query);
		mSearch.execute();
	}

	private class SearchTask extends AsyncTask<String, String, String> {

		private String searchQuery = "";

		public SearchTask(String query) {
			searchQuery = query.toLowerCase(Locale.ENGLISH);
		}

		@Override
		protected String doInBackground(String... params) {
			shownMovies.clear();

			if (searchQuery.startsWith("actor:")) {
				for (int i = 0; i < movies.size(); i++) {
					if (isCancelled())
						return null;

					if (movies.get(i).getCast().toLowerCase(Locale.ENGLISH).contains(searchQuery.replace("actor:", "").trim()))
						shownMovies.add(movies.get(i));
				}
			} else if (searchQuery.equalsIgnoreCase("missing_genres")) {
				for (int i = 0; i < movies.size(); i++) {
					if (isCancelled())
						return null;

					if (MizLib.isEmpty(movies.get(i).getGenres()))
						shownMovies.add(movies.get(i));
				}
			} else {
				String lowerCase = "", filepath; // Reuse String variables
				Pattern p = Pattern.compile(MizLib.CHARACTER_REGEX); // Use a pre-compiled pattern as it's a lot faster (approx. 3x for ~700 movies)

				for (int i = 0; i < movies.size(); i++) {
					if (isCancelled())
						return null;

					lowerCase = movies.get(i).getTitle().toLowerCase(Locale.ENGLISH);
					filepath = movies.get(i).getFilepath().toLowerCase(Locale.ENGLISH);

					if (lowerCase.indexOf(searchQuery) != -1 || filepath.indexOf(searchQuery) != -1 || p.matcher(lowerCase).replaceAll("").indexOf(searchQuery) != -1)
						shownMovies.add(movies.get(i));
				}
			}

			sortMovies();

			return null;
		}

		@Override
		protected void onPostExecute(String result) {
			notifyDataSetChanged();
			hideProgressBar();
		}
	}

	private void showProgressBar() {
		pbar.setVisibility(View.VISIBLE);
		mGridView.setVisibility(View.GONE);
	}

	private void hideProgressBar() {
		pbar.setVisibility(View.GONE);
		mGridView.setVisibility(View.VISIBLE);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == 1) { // Update
			forceLoaderLoad();
		} else if (resultCode == 2) { // Favourite removed
			if (actionBar.getSelectedNavigationIndex() == 1) {
				showFavorites();
			}
		} else if (resultCode == 3) {
			notifyDataSetChanged();
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals("prefsIgnorePrefixesInTitles")) {
			ignorePrefixes = settings.getBoolean("prefsIgnorePrefixesInTitles", false);
			forceLoaderLoad();
		} else if (key.equals("prefsGridItemSize")) {
			String thumbnailSize = settings.getString("prefsGridItemSize", getString(R.string.normal));
			if (thumbnailSize.equals(getString(R.string.normal))) 
				mImageThumbSize = (int) (getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size) * 1);
			else
				mImageThumbSize = (int) (getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size) * 0.75);

			mGridView.setColumnWidth(mImageThumbSize);

			final int numColumns = (int) Math.floor(mGridView.getWidth() / (mImageThumbSize + mImageThumbSpacing));
			if (numColumns > 0) {
				mAdapter.setNumColumns(numColumns);
			}
		}


		mShowTitles = settings.getBoolean("prefsShowGridTitles", false);

		sortMovies();
		notifyDataSetChanged();
	}

	private void forceLoaderLoad() {
		if (isAdded())
			if (getLoaderManager().getLoader(0) == null)
				getLoaderManager().initLoader(0, null, loaderCallbacks);
			else
				getLoaderManager().restartLoader(0, null, loaderCallbacks);
	}

	private class ActionBarSpinner extends BaseAdapter {

		private LayoutInflater inflater;

		public ActionBarSpinner() {
			inflater = LayoutInflater.from(getActivity());
		}

		@Override
		public int getCount() {
			return spinnerItems.size();
		}

		@Override
		public Object getItem(int position) {
			return null;
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public int getItemViewType(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			convertView = inflater.inflate(R.layout.spinner_header, parent, false);
			((TextView) convertView.findViewById(R.id.title)).setText(spinnerItems.get(position).getTitle());

			int size = shownMovies.size();
			if (actionBar.getSelectedNavigationIndex() == 3) // Collections
				((TextView) convertView.findViewById(R.id.subtitle)).setText(size + " " + getResources().getQuantityString(R.plurals.collectionsInLibrary, size, size));
			else
				((TextView) convertView.findViewById(R.id.subtitle)).setText(size + " " + getResources().getQuantityString(R.plurals.moviesInLibrary, size, size));

			return convertView;
		}

		@Override
		public int getViewTypeCount() {
			return 0;
		}

		@Override
		public boolean hasStableIds() {
			return true;
		}

		@Override
		public boolean isEmpty() {
			return spinnerItems.size() == 0;
		}

		@Override
		public View getDropDownView(int position, View convertView, ViewGroup parent) {			
			convertView = inflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
			((TextView) convertView.findViewById(android.R.id.text1)).setText(spinnerItems.get(position).getSubtitle());

			return convertView;
		}
	}
}