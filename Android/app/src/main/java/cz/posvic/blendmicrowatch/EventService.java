package cz.posvic.blendmicrowatch;

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

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EventService extends Service {

	private static final String TAG = EventService.class.getName();

	public static final String BROADCAST_MSG_SEND = "event-msg-send";
	public static final String BROADCAST_MSG_RECEIVE = "event-msg-receive";
	public static final String BROADCAST_MSG_DATA = "event-msg-data";

	private boolean mRunning;
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
			return START_NOT_STICKY;
		}

		mRunning = true;

		Intent gattServiceIntent = new Intent(this, RBLService.class);
		bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

		registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
		registerReceiver(mSmsReceiver, makeSmsIntentFilter());
		registerReceiver(mCallReceiver, makeCallIntentFilter());

		LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, new IntentFilter(BROADCAST_MSG_SEND));

		return super.onStartCommand(intent, flags, startId);
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

		mRunning = false;
	}

	private void gattDiscovered() {
		Intent intent = new Intent(BROADCAST_MSG_RECEIVE);
		intent.putExtra(EventService.BROADCAST_MSG_DATA, "discovered");

		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}

	private void gattDisconnected(boolean reconnect) {
		Intent intent = new Intent(BROADCAST_MSG_RECEIVE);
		intent.putExtra(EventService.BROADCAST_MSG_DATA, "disconnected");
		intent.putExtra("reconnect", reconnect);

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
		StringBuilder sb = new StringBuilder(sanitize2(msg));
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

	/**
	 * Replace special characters to standard ASCII.
	 * @param msg Message
	 * @return String in ASCII
	 */
	private String sanitize(String msg) {
		try {
			byte[] bytes = msg.getBytes("US-ASCII");
			String ret = new String(bytes);
			Log.d(TAG, "sanitized text: " + ret);
			return ret;
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		return "?";
	}

	private String sanitize2(String msg) {
		final Map<Character, Character> map = new HashMap<>();

		// Special
		map.put('~', '~');
		map.put('@', '@');

		// Sentences
		map.put('.', '.');
		map.put(',', ',');
		map.put(' ', ' ');
		map.put('!', '!');
		map.put('?', '?');
		map.put(':', ':');
		map.put('-', '-');
		map.put('"', '"');

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

		StringBuilder sb = new StringBuilder(msg.length());
		for (int i = 0; i < msg.length(); i++) {
			char ch = msg.charAt(i);

			// Big and small letters are OK
			if (
					(ch >= 65 && ch <= 90) ||
					(ch >= 97 && ch <= 122)
			) {
				sb.append(ch);
				continue;
			}

			Character character = map.get(ch);
			if (character != null) {
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
			mBluetoothLeService.connect("F1:9C:4B:4A:63:21");
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
					stopSelf();
					break;

				case RBLService.ACTION_GATT_SERVICES_DISCOVERED:
					Log.i(TAG, "gatt services discovered");
					getGattService(mBluetoothLeService.getSupportedGattService());
					gattDiscovered();
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
}
