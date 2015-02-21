package cz.posvic.blendmicrowatch;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends ActionBarActivity {
	private static final String TAG = MainActivity.class.getName();

	private String mDeviceAddress;
	private RBLService mBluetoothLeService;
	private Map<UUID, BluetoothGattCharacteristic> map = new HashMap<>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Button butDebugCall = (Button) findViewById(R.id.butDebugCall);
		butDebugCall.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				sendGattMessage("CALL 'Mamka'~");
			}
		});

		Button butDebugSms = (Button) findViewById(R.id.butDebugSms);
		butDebugSms.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				sendGattMessage("SMS 'Ahoj,promin,ale tenhle vikend napracovavam,co jsem zameskal o nemoci.Ale jestli chces,tak pojedu vecer do mesta,tak se muzes pridat.'~");
			}
		});

		// My device address
		mDeviceAddress = "F1:9C:4B:4A:63:21";

		Intent gattServiceIntent = new Intent(this, RBLService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		registerReceiver(mSmsReceiver, makeSmsIntentFilter());
		registerReceiver(mCallReceiver, makeCallIntentFilter());
	}

	@Override
	protected void onStop() {
		super.onStop();
		unregisterReceiver(mGattUpdateReceiver);
		unregisterReceiver(mSmsReceiver);
		unregisterReceiver(mCallReceiver);
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

	// ------------------------------------------------------------------------

	private void getGattService(BluetoothGattService gattService) {
		if (gattService == null) {
			return;
		}

		BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(RBLService.UUID_BLE_SHIELD_TX);
		map.put(characteristic.getUuid(), characteristic);

		BluetoothGattCharacteristic characteristicRx = gattService.getCharacteristic(RBLService.UUID_BLE_SHIELD_RX);
		mBluetoothLeService.setCharacteristicNotification(characteristicRx, true);
		mBluetoothLeService.readCharacteristic(characteristicRx);
	}

	private void sendGattMessage(String msg) {
		StringBuilder sb = new StringBuilder(msg);
		while (sb.length() > 0) {

			// Send 16 bytes chunk or rest of the message
			int length = Math.min(16, sb.length());
			String str = sb.substring(0, length);
			sb.delete(0, length);

			BluetoothGattCharacteristic characteristic = map.get(RBLService.UUID_BLE_SHIELD_TX);
			characteristic.setValue(str.getBytes());

			mBluetoothLeService.writeCharacteristic(characteristic);
		}
	}

	// ------------------------------------------------------------------------
	// Filters
	// ------------------------------------------------------------------------

	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();

		intentFilter.addAction(RBLService.ACTION_GATT_CONNECTED);
		intentFilter.addAction(RBLService.ACTION_GATT_DISCONNECTED);
		intentFilter.addAction(RBLService.ACTION_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(RBLService.ACTION_DATA_AVAILABLE);

		return intentFilter;
	}

	private static IntentFilter makeSmsIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();

		intentFilter.addAction("android.provider.Telephony.SMS_RECEIVED");

		return intentFilter;
	}

	private static IntentFilter makeCallIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();

		intentFilter.addAction("android.intent.action.PHONE_STATE");

		return intentFilter;
	}

	// ------------------------------------------------------------------------

	private final ServiceConnection mServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			mBluetoothLeService = ((RBLService.LocalBinder) service).getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				finish();
			}

			// Automatically connects to the device upon successful start-up
			// initialization.
			mBluetoothLeService.connect(mDeviceAddress);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	// ------------------------------------------------------------------------
	// Receivers
	// ------------------------------------------------------------------------

	private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();

			switch (action) {
				case RBLService.ACTION_GATT_DISCONNECTED:
					Log.i(TAG, "gatt disconnected");

					Intent gattServiceIntent = new Intent(MainActivity.this, RBLService.class);
					bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
					break;

				case RBLService.ACTION_GATT_SERVICES_DISCOVERED:
					Log.i(TAG, "gatt services discovered");
					getGattService(mBluetoothLeService.getSupportedGattService());
					break;

				case RBLService.ACTION_DATA_AVAILABLE:
					Log.i(TAG, "data available");

					byte[] byteArray = intent.getByteArrayExtra(RBLService.EXTRA_DATA);
					if (byteArray != null) {
						Log.i(TAG, "incoming data: " + new String(byteArray));
					}

					break;
			}
		}
	};

	private final BroadcastReceiver mSmsReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			Log.d(TAG, "action = " + action);

			if ("android.provider.Telephony.SMS_RECEIVED".equals(action)) {

				final Bundle bundle = intent.getExtras();
				if (bundle == null) {
					Log.e(TAG, "bundle is null");
					return;
				}

				try {
					final Object[] pDusObjArray = (Object[]) bundle.get("pdus");
					for (Object pDusObj : pDusObjArray) {
						SmsMessage sms = SmsMessage.createFromPdu((byte[]) pDusObj);
						String senderNum = sms.getDisplayOriginatingAddress();
						String message = sms.getDisplayMessageBody();

						Log.i(TAG, "senderNum: " + senderNum + "; message: " + message);
						sendGattMessage("SMS '" + senderNum + ":" + message + "'~");
					}
				} catch (Exception e) {
					Log.e(TAG, e.toString());
				}
			}
		}
	};

	private final BroadcastReceiver mCallReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			Log.d(TAG, "action = " + action);

			if ("android.intent.action.PHONE_STATE".equals(action)) {
				TelephonyManager telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
				telephony.listen(new PhoneStateListener() {
					@Override
					public void onCallStateChanged(int state, String incomingNumber) {
						switch(state){
							case TelephonyManager.CALL_STATE_IDLE:
								Log.d(TAG, "IDLE");
								break;

							case TelephonyManager.CALL_STATE_OFFHOOK:
								Log.d(TAG, "OFFHOOK");
								break;

							case TelephonyManager.CALL_STATE_RINGING:
								Log.d(TAG, "RINGING");
								sendGattMessage("CALL '" + incomingNumber + "'~");
								break;
						}
					}
				}, PhoneStateListener.LISTEN_CALL_STATE);
			}
		}
	};
}
