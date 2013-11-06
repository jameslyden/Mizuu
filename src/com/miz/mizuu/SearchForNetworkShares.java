package com.miz.mizuu;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class SearchForNetworkShares extends Activity {

	private ListView list;
	private NetworkAdapter adapter;
	private TextView status;
	private ProgressBar pbar;
	private boolean running = true;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.simple_list);

		pbar = (ProgressBar) findViewById(R.id.progressBar);

		status = (TextView) findViewById(R.id.overviewMessage);

		list = (ListView) findViewById(R.id.listView1);
		adapter = new NetworkAdapter();
		list.setAdapter(adapter);
		list.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
					long arg3) {
				sendBroadcast(arg2);
			}
		});

		new Thread() {
			@Override
			public void run() {
				ExecutorService executor = Executors.newFixedThreadPool(10);
				for(int dest=0; dest<255; dest++) {
					String host = "192.168.1." + dest;
					executor.execute(pingRunnable(host));
				}

				executor.shutdown();
				try {
					executor.awaitTermination(60*1000, TimeUnit.MILLISECONDS);
				} catch (InterruptedException ignored) {}

				if (running)
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							pbar.setVisibility(View.GONE);
							status.setVisibility(View.GONE);
						}
					});
			}
		}.start();
	}

	private void sendBroadcast(int number) {
		Intent i = new Intent("mizuu-network-search");
		i.putExtra("ip", ((NetworkAdapter) list.getAdapter()).getHost(number));
		LocalBroadcastManager.getInstance(this).sendBroadcast(i);
		running = false;
		finish();
	}

	private Runnable pingRunnable(final String host) {
		return new Runnable() {
			public void run() {
				if (isReachableByPing(host)) {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							((NetworkAdapter) list.getAdapter()).addHost(host);
						}
					});
				}
			}
		};
	}

	public static boolean isReachableByPing(String host) {
		try {
			Process myProcess = Runtime.getRuntime().exec("ping -c 1 " + host);
			myProcess.waitFor();

			if(myProcess.exitValue() == 0)
				return true;
		} catch(Exception e) {}

		return false;
	}

	static class ViewHolder {
		TextView name, desc;
		ImageView icon;
	}

	private class NetworkAdapter extends BaseAdapter {

		private LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		private ArrayList<String> hosts = new ArrayList<String>();

		@Override
		public int getCount() {
			return hosts.size();
		}

		public void addHost(String host) {
			hosts.add(host);
			notifyDataSetChanged();
		}

		public String getHost(int count) {
			if (count > hosts.size())
				return "";
			return hosts.get(count);
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
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;

			if (convertView == null) {
				convertView = inflater.inflate(R.layout.file_list_item, null);
				holder = new ViewHolder();
				holder.name = (TextView) convertView.findViewById(R.id.text1);
				holder.desc = (TextView) convertView.findViewById(R.id.size);
				holder.icon = (ImageView) convertView.findViewById(R.id.icon);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			holder.name.setText(hosts.get(position));
			holder.desc.setVisibility(View.GONE);
			holder.icon.setImageResource(R.drawable.folder);

			return convertView;
		}
	}
}