package cz.posvic.blendmicrowatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends ActionBarActivity {
	private static final String TAG = MainActivity.class.getName();

	private SimpleDateFormat sdf = new SimpleDateFormat("HHmmss");

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Button butDebugCall = (Button) findViewById(R.id.butDebugCall);
		butDebugCall.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				sendMessage("CALL 'Mamka'~");
			}
		});

		Button butDebugSms = (Button) findViewById(R.id.butDebugSms);
		butDebugSms.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				sendMessage("SMS 'Ahoj,promin,ale tenhle vikend napracovavam,co jsem zameskal o nemoci.Ale jestli chces,tak pojedu vecer do mesta,tak se muzes pridat.'~");
			}
		});

		Button butDebugServiceStart = (Button) findViewById(R.id.butDebugServiceStart);
		butDebugServiceStart.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startService(new Intent(MainActivity.this, EventService.class));
			}
		});

		Button butDebugServiceStop = (Button) findViewById(R.id.butDebugServiceStop);
		butDebugServiceStop.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				stopService(new Intent(MainActivity.this, EventService.class));
			}
		});

		Button butSetClock = (Button) findViewById(R.id.butSetClock);
		butSetClock.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				sendMessage("&" + sdf.format(new Date()));
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter(EventService.BROADCAST_MSG_RECEIVE));
	}

	@Override
	protected void onPause() {
		super.onPause();
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();

		if (id == R.id.action_settings) {
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	/**
	 * Send message to service.
	 * @param msg
	 */
	private void sendMessage(String msg) {
		Intent intent = new Intent(EventService.BROADCAST_MSG_SEND);
		intent.putExtra(EventService.BROADCAST_MSG_DATA, msg);

		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	// ------------------------------------------------------------------------
	// Receivers
	// ------------------------------------------------------------------------

	// Receive message from service
	private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String msg = intent.getStringExtra(EventService.BROADCAST_MSG_DATA);
			Log.d(TAG, "got message: " + msg);
			Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
		}
	};
}
