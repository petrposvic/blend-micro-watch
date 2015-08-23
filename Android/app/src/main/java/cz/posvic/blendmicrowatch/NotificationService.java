package cz.posvic.blendmicrowatch;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class NotificationService extends NotificationListenerService {
	private static final String TAG = NotificationService.class.getName();

	@Override
	public void onNotificationPosted(StatusBarNotification sbn) {
		Log.d(TAG, "-- onNotificationPosted(" + sbn + ") --");

		// TODO Send author, subject and head of the message
		if ("com.google.android.gm".equals(sbn.getPackageName())) {
			Intent intent = new Intent(EventService.BROADCAST_MSG_SEND);
			intent.putExtra(EventService.BROADCAST_MSG_DATA, "~EMAIL");

			LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
		}

		super.onNotificationPosted(sbn);
	}

	@Override
	public void onNotificationRemoved(StatusBarNotification sbn) {
		Log.d(TAG, "-- onNotificationRemoved(" + sbn + ") --");
		super.onNotificationRemoved(sbn);
	}
}
