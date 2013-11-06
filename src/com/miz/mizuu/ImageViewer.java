package com.miz.mizuu;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;

import com.miz.mizuu.fragments.ActorPhotoFragment;

public class ImageViewer extends FragmentActivity {

	private ViewPager awesomePager;
	private String[] photos;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
		
		setContentView(R.layout.viewpager);
		
		photos = getIntent().getStringArrayExtra("photos");
		
		setTitle(getIntent().getStringExtra("actorName"));
		getActionBar().setSubtitle((getIntent().getIntExtra("selectedIndex", 0) + 1) + " " + getString(R.string.of) + " " + photos.length);
		
		awesomePager = (ViewPager) findViewById(R.id.awesomepager);
		awesomePager.setAdapter(new ActorPhotosAdapter(getSupportFragmentManager()));
		awesomePager.setOnPageChangeListener(new OnPageChangeListener() {

			@Override
			public void onPageScrollStateChanged(int arg0) {}

			@Override
			public void onPageScrolled(int arg0, float arg1, int arg2) {}

			@Override
			public void onPageSelected(int arg0) {
				getActionBar().setSubtitle((arg0 + 1) + " " + getString(R.string.of) + " " + photos.length);
			}
			
		});
		awesomePager.setCurrentItem(getIntent().getIntExtra("selectedIndex", 0));
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.open_in_browser, menu);
		return true;
	}
	
	private class ActorPhotosAdapter extends FragmentPagerAdapter {

		public ActorPhotosAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override  
		public Fragment getItem(int index) {
			return ActorPhotoFragment.newInstance(photos[index]);
		}  

		@Override  
		public int getCount() { 
			return photos.length;
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			onBackPressed();
			return true;
		case R.id.openInBrowser:
			openInBrowser();
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private void openInBrowser() {
		Intent i = new Intent(Intent.ACTION_VIEW);
		i.setData(Uri.parse(photos[awesomePager.getCurrentItem()]));
		startActivity(i);
	}

	@Override
	public void onStart() {
		super.onStart();
		getActionBar().setDisplayHomeAsUpEnabled(true);
	}
}