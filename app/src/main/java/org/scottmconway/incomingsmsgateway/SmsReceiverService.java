package org.scottmconway.incomingsmsgateway;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Telephony;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;

public class SmsReceiverService extends Service {

    BroadcastReceiver receiver;
    ContentObserver mmsObserver;

    private static final String CHANNEL_ID = "SmsDefault";

    public SmsReceiverService() {
        receiver = new SmsBroadcastReceiver();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            filter.addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
            filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);

        } else {
            filter.addAction("android.provider.Telephony.SMS_RECEIVED");
            filter.addAction("android.provider.TelephonyManager.ACTION_PHONE_STATE_CHANGED");
        }

        registerReceiver(receiver, filter);

        // Register MMS ContentObserver on both URIs since different Android
        // implementations notify on different content URIs for incoming MMS
        Handler handler = new Handler(getMainLooper());
        mmsObserver = new MmsBroadcastReceiver(handler, getApplicationContext());
        getContentResolver().registerContentObserver(
                Uri.parse("content://mms"), true, mmsObserver);
        getContentResolver().registerContentObserver(
                Uri.parse("content://mms-sms"), true, mmsObserver);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getText(R.string.notification_channel),
                    NotificationManager.IMPORTANCE_NONE);

            notificationManager.createNotificationChannel(channel);

            Notification notification =
                    new Notification.Builder(this, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_f)
                            .setColor(getColor(R.color.colorPrimary))
                            .setOngoing(true)
                            .build();

            startForeground(1, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(receiver);
        getContentResolver().unregisterContentObserver(mmsObserver);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(true);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}