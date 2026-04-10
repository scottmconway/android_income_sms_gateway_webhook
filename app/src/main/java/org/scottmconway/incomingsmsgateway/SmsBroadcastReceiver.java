package org.scottmconway.incomingsmsgateway;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;

import androidx.core.content.ContextCompat;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import android.util.Log;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class SmsBroadcastReceiver extends BroadcastReceiver {

    private Context context;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getExtras() == null || intent.getAction() == null) {
            return;
        }

        this.context = context;
        WebhookMessage message = null;

        ArrayList<ForwardingConfig> configs = ForwardingConfig.getAll(context);
        String asterisk = context.getString(R.string.asterisk);

        switch (intent.getAction()) {
            case "android.provider.Telephony.SMS_RECEIVED":
                message = onReceiveSmsReceived(context, intent);
                break;
            case "android.intent.action.PHONE_STATE":
                message = onReceivePhoneStateChange(context, intent);
                break;
        }

        // determine if we should call any webhooks
        if (message == null) return;
        for (ForwardingConfig config : configs) {
            // check sender phone number
            // TODO should this be allowed to be null? eg. Unknown number calling
            // if the sender is null or not the config's chosen sender AND the config isn't set to "*"
            // kinda silly to check config.getSender() twice
            // TODO senderPhoneNumber MUST BE A STRING!!!
            // CONTINUE FROM HERE!

            if ((message.senderPhoneNumber == null || !message.senderPhoneNumber.equals(config.getSender())) && !config.getSender().equals(asterisk)) {
                continue;
            }

            // check SMS enabled
            // TODO add extra modes for calls/SMS
            if (!config.getIsSmsEnabled()) {
                continue;
            }

            // check SIM slot
            if (config.getSimSlot() > 0 && config.getSimSlot() != message.simSlotId) {
                continue;
            }

            // call webhook if all above criteria met
            this.callWebHook(config, message);
        }
    }

    public WebhookMessage onReceivePhoneStateChange(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();

        // TODO reference locale instead
        String messageSource = "Incoming Call";


        // get caller number
        // When the phone is ringing, read the incoming number
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (state == null || !state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
            return null;
        }

        String callerPhoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

        // exit if we can't see the phone number,
        // since we assume multiple pings with a single state change and only want a single notification
        // TODO investigate further for things like Unknown callers!
        if (callerPhoneNumber == null || callerPhoneNumber.isEmpty()) {
            return null;
        }

        // get caller's contact name if applicable
        String senderName = getContactNameByPhoneNumber(callerPhoneNumber, context);

        // get SIM slot info
        int slotId = this.detectSim(bundle) + 1;
        String slotName = "undetected";
        if (slotId < 0) {
            slotId = 0;
        }

        if (slotId > 0) {
            slotName = "sim" + slotId;
        }

        return new WebhookMessage(messageSource, callerPhoneNumber, senderName, slotId, slotName, "",  System.currentTimeMillis());
    }

    public WebhookMessage onReceiveSmsReceived(Context context, Intent intent) {

        // construct SMS message
        Bundle bundle = intent.getExtras();

        // TODO reference locale instead
        String messageSource = "Incoming SMS";

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null || pdus.length == 0) {
            return null;
        }

        StringBuilder content = new StringBuilder();
        final SmsMessage[] messages = new SmsMessage[pdus.length];
        for (int i = 0; i < pdus.length; i++) {
            messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
            content.append(messages[i].getDisplayMessageBody());
        }

        // get sending phone number
        String senderPhoneNumber = messages[0].getOriginatingAddress();
        if (senderPhoneNumber == null) {
            return null;
        }

        // get sender's contact name if applicable
        String senderName = getContactNameByPhoneNumber(senderPhoneNumber, context);

        // get SIM slot info
        int slotId = this.detectSim(bundle) + 1;
        String slotName = "undetected";
        if (slotId < 0) {
            slotId = 0;
        }

        if (slotId > 0) {
                slotName = "sim" + slotId;
            }

        return new WebhookMessage(messageSource, senderPhoneNumber, senderName, slotId, slotName, content.toString(), messages[0].getTimestampMillis());
    }

    public void callWebHook(ForwardingConfig config,WebhookMessage message) {

        String strMessage = config.prepareMessage(message);

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        Data data = new Data.Builder()
                .putString(RequestWorker.DATA_URL, config.getUrl())
                .putString(RequestWorker.DATA_TEXT, strMessage)
                .putString(RequestWorker.DATA_HEADERS, config.prepareHeaders(message))
                .putBoolean(RequestWorker.DATA_IGNORE_SSL, config.getIgnoreSsl())
                .putBoolean(RequestWorker.DATA_CHUNKED_MODE, config.getChunkedMode())
                .putInt(RequestWorker.DATA_MAX_RETRIES, config.getRetriesNumber())
                .build();

        WorkRequest workRequest =
                new OneTimeWorkRequest.Builder(RequestWorker.class)
                        .setConstraints(constraints)
                        .setBackoffCriteria(
                                BackoffPolicy.EXPONENTIAL,
                                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                                TimeUnit.MILLISECONDS
                        )
                        .setInputData(data)
                        .build();

        WorkManager
                .getInstance(this.context)
                .enqueue(workRequest);

    }

    private int detectSim(Bundle bundle) {
        int slotId = -1;
        Set<String> keySet = bundle.keySet();
        for (String key : keySet) {
            switch (key) {
                case "phone":
                    slotId = bundle.getInt("phone", -1);
                    break;
                case "slot":
                    slotId = bundle.getInt("slot", -1);
                    break;
                case "simId":
                    slotId = bundle.getInt("simId", -1);
                    break;
                case "simSlot":
                    slotId = bundle.getInt("simSlot", -1);
                    break;
                case "slot_id":
                    slotId = bundle.getInt("slot_id", -1);
                    break;
                case "simnum":
                    slotId = bundle.getInt("simnum", -1);
                    break;
                case "slotId":
                    slotId = bundle.getInt("slotId", -1);
                    break;
                case "slotIdx":
                    slotId = bundle.getInt("slotIdx", -1);
                    break;
                case "android.telephony.extra.SLOT_INDEX":
                    slotId = bundle.getInt("android.telephony.extra.SLOT_INDEX", -1);
                    break;
                default:
                    if (key.toLowerCase().contains("slot") | key.toLowerCase().contains("sim")) {
                        String value = bundle.getString(key, "-1");
                        if (value.equals("0") | value.equals("1") | value.equals("2")) {
                            slotId = bundle.getInt(key, -1);
                        }
                    }
            }

            if (slotId != -1) {
                break;
            }
        }

        return slotId;
    }

    private static String getContactNameByPhoneNumber(String phoneNumber, Context context) {

        // Attempt to resolve sender name if `READ_CONTACTS` permission has been granted
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try {
                ContentResolver contentResolver = context.getContentResolver();
                Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
                String[] projection = {ContactsContract.PhoneLookup.DISPLAY_NAME};
                String contactName = null;
                Cursor cursor = contentResolver.query(uri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                    contactName = cursor.getString(nameIndex);
                    cursor.close();
                }

                if (contactName != null) return contactName;
            }
            catch (java.lang.SecurityException se) {
                // READ_CONTACTS hasn't been granted, but we shouldn't've gotten here...
                assert true;
            }
        }

        return phoneNumber;
    }
}
