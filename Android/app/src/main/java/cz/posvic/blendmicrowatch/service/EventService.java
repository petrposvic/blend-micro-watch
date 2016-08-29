package cz.posvic.blendmicrowatch.service;

import android.app.Service;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.PhoneStateListener;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EventService extends Service implements Runnable {

	private static final String TAG = EventService.class.getName();

	public static final String BROADCAST_MSG_SEND = "event-msg-send";
	public static final String BROADCAST_MSG_RECEIVE = "event-msg-receive";
	public static final String BROADCAST_MSG_DATA = "event-msg-data";
	public static final String BROADCAST_MSG_RECONNECT = "event-msg-reconnect";
	public static final String BROADCAST_MSG_WATCHDOG = "event-msg-watchdog";

	private boolean mRunning, mConnected;
	private RBLService mBluetoothLeService;
	private Map<UUID, BluetoothGattCharacteristic> map = new HashMap<>();

	@Override
	public IBinder onBind(Intent intent) {
		Log.d(TAG, "-- onBind(...) --");
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "-- onStartCommand(...," + flags + "," + startId + ") --");

		if (mRunning) {
			Log.i(TAG, "service already run");
			return START_STICKY;
		}

		mRunning = true;
		new Thread(this).start();

		// Intent gattServiceIntent = new Intent(this, RBLService.class);
		// bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		registerReceiver(mSmsReceiver, makeSmsIntentFilter());
		registerReceiver(mCallReceiver, makeCallIntentFilter());

		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter(BROADCAST_MSG_SEND));
		LocalBroadcastManager.getInstance(this).registerReceiver(mWatchdogReceiver, new IntentFilter(BROADCAST_MSG_WATCHDOG));

		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.d(TAG, "-- onDestroy() --");

		gattDisconnected(false);
		unbindService(mServiceConnection);

		unregisterReceiver(mGattUpdateReceiver);
		unregisterReceiver(mSmsReceiver);
		unregisterReceiver(mCallReceiver);

		LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
		LocalBroadcastManager.getInstance(this).unregisterReceiver(mWatchdogReceiver);

		mRunning = false;
	}

	@Override
	public void run() {
		while (mRunning) {
			try {
				Thread.sleep(5000);
			} catch (InterruptedException ignored) {}

			Log.d(TAG, "connected = " + mConnected);

			// Try rebind connection service
			if (!mConnected) {
				Intent intent = new Intent(BROADCAST_MSG_WATCHDOG);
				LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
			}
		}

		Log.d(TAG, "watchdog stops");
	}

	private void gattDiscovered() {
		Intent intent = new Intent(BROADCAST_MSG_RECEIVE);
		intent.putExtra(EventService.BROADCAST_MSG_DATA, "discovered");

		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void gattDisconnected(boolean reconnect) {
		Intent intent = new Intent(BROADCAST_MSG_RECEIVE);
		intent.putExtra(EventService.BROADCAST_MSG_DATA, "disconnected");
		intent.putExtra(EventService.BROADCAST_MSG_RECONNECT, reconnect);

		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private String getContactNameByNumber(String number) {
		Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
		String name = number;

		ContentResolver contentResolver = getContentResolver();
		Cursor contactLookup = contentResolver.query(uri, new String[] {
				BaseColumns._ID, ContactsContract.PhoneLookup.DISPLAY_NAME
		}, null, null, null);

		try {
			if (contactLookup != null && contactLookup.getCount() > 0) {
				contactLookup.moveToNext();
				name = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
			}
		} finally {
			if (contactLookup != null) {
				contactLookup.close();
			}
		}

		return name;
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
		String toSend = sanitize(msg);

		// Prevent long messages
		int count = 0;
		for (int i = 0; i < toSend.length(); i++) {
			char ch = toSend.charAt(i);
			if (ch == '~' || ch == '&' || ch == '^') {
				continue;
			}

			count++;
		}
		if (count > 148) {
			toSend = toSend.substring(0, 148);
			Log.d(TAG, "to send = " + toSend);
		}

		StringBuilder sb = new StringBuilder(toSend);
		while (sb.length() > 0) {

			// Send 16 bytes chunk or rest of the message
			int length = Math.min(16, sb.length());
			String str = sb.substring(0, length);
			sb.delete(0, length);

			BluetoothGattCharacteristic characteristic = map.get(RBLService.UUID_BLE_SHIELD_TX);
			characteristic.setValue(str.getBytes());

			mBluetoothLeService.writeCharacteristic(characteristic);

			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private String sanitize(String msg) {
		final Map<Character, Character> map = new HashMap<>();

		// Commands
		map.put('~', '~');
		map.put('&', '&');
		map.put('^', '^');

		// Sentences
		map.put('.', '.');
		map.put(',', ',');
		map.put(' ', ' ');
		map.put('!', '!');
		map.put('?', '?');
		map.put(':', ':');
		map.put('-', '-');
		map.put('"', '"');
		map.put('@', '@');

		// Czech
		map.put('á', 'a');
		map.put('č', 'c');
		map.put('ď', 'd');
		map.put('ě', 'e');
		map.put('é', 'e');
		map.put('í', 'i');
		map.put('ň', 'n');
		map.put('ó', 'o');
		map.put('ř', 'r');
		map.put('š', 's');
		map.put('ť', 't');
		map.put('ů', 'u');
		map.put('ú', 'u');
		map.put('ý', 'y');
		map.put('ž', 'z');

		// Escaped
		map.put('\'', '"');
		map.put('\n', ' ');

		// Command char is possible to use just once
		boolean command = false;

		StringBuilder sb = new StringBuilder(msg.length());
		for (int i = 0; i < msg.length(); i++) {
			char ch = msg.charAt(i);

			// Some groups of chars are OK
			if (
					(ch >= 48 && ch <= 57) ||   // Numbers
					(ch >= 65 && ch <= 90) ||   // Big letters
					(ch >= 97 && ch <= 122)     // Small letters
			) {
				sb.append(ch);
				continue;
			}

			Character character = map.get(ch);
			if (character != null) {
				if (
						character == '~' ||
						character == '&' ||
						character == '^'
				) {
					if (command) {
						continue;
					}
					command = true;
				}

				sb.append(character);
			} else {
				sb.append('?');
			}
		}

		return sb.toString();
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
			Log.d(TAG, "-- onServiceConnected(" + componentName + "," + service + ") --");

			mBluetoothLeService = ((RBLService.LocalBinder) service).getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e(TAG, "Unable to initialize Bluetooth");
				// finish();
			}

			// Automatically connects to the device upon successful start-up
			// initialization.
			mBluetoothLeService.connect(
					// "F1:9C:4B:4A:63:21" // Prototype board
					"FA:35:8C:ED:4D:12" // Testing board
			);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			Log.d(TAG, "-- onServiceDisconnected(" + componentName + ") --");
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
					gattDisconnected(true);
					mConnected = false;
					break;

				case RBLService.ACTION_GATT_SERVICES_DISCOVERED:
					Log.i(TAG, "gatt services discovered");
					getGattService(mBluetoothLeService.getSupportedGattService());
					gattDiscovered();
					mConnected = true;
					break;

				case RBLService.ACTION_DATA_AVAILABLE:
					Log.i(TAG, "data available");

					byte[] byteArray = intent.getByteArrayExtra(RBLService.EXTRA_DATA);
					if (byteArray != null) {
						sendDataToApp(new String(byteArray));
					}

					break;
			}
		}
	};

	private void sendDataToApp(String msg) {
		Intent intent = new Intent(BROADCAST_MSG_RECEIVE);
		intent.putExtra(BROADCAST_MSG_DATA, msg);

		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

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
						String number = sms.getDisplayOriginatingAddress();
						String message = sms.getDisplayMessageBody();

						Log.i(TAG, "senderNum: " + number + "; message: " + message);
						sendGattMessage("~SMS '" + getContactNameByNumber(number) + ":" + message + "'");
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
								sendGattMessage("~CALL '" + getContactNameByNumber(incomingNumber) + "'");
								break;
						}
					}
				}, PhoneStateListener.LISTEN_CALL_STATE);
			}
		}
	};

	private final BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String msg = intent.getStringExtra(BROADCAST_MSG_DATA);
			Log.d(TAG, "got message: " + msg);

			if (msg != null) {
				sendGattMessage(msg);
			}
		}
	};

	private final BroadcastReceiver mWatchdogReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			try {
				unbindService(mServiceConnection);
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}

			Intent gattServiceIntent = new Intent(EventService.this, RBLService.class);
			bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
		}
	};
}
