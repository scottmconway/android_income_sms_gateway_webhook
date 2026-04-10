package org.scottmconway.incomingsmsgateway;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract;
import android.util.Base64;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;

public class MmsBroadcastReceiver extends ContentObserver {

    private static final String TAG = "MmsBroadcastReceiver";
    private static final Uri MMS_CONTENT_URI = Uri.parse("content://mms");
    private static final int MMS_ADDR_TYPE_FROM = 137;
    private static final int MMS_ADDR_TYPE_TO = 151;
    private static final int MAX_READ_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {2000, 4000, 8000};

    private final Context context;
    private long lastMmsId;
    private boolean initialized;

    public MmsBroadcastReceiver(Handler handler, Context context) {
        super(handler);
        this.context = context;
        if (hasReadSmsPermission()) {
            this.lastMmsId = getLatestMmsId();
            this.initialized = true;
        } else {
            this.lastMmsId = 0;
            this.initialized = false;
        }
    }

    @Override
    public void onChange(boolean selfChange) {
        super.onChange(selfChange);

        if (!hasReadSmsPermission()) {
            return;
        }
        if (!initialized) {
            lastMmsId = getLatestMmsId();
            initialized = true;
            return;
        }
        processNewMmsMessages();
    }

    private boolean hasReadSmsPermission() {
        return ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void processNewMmsMessages() {
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(
                MMS_CONTENT_URI,
                new String[]{"_id", "sub", "date", "msg_box"},
                "_id > ?",
                new String[]{String.valueOf(lastMmsId)},
                "_id ASC"
        );

        if (cursor == null) {
            Log.w(TAG, "MMS query returned null cursor");
            return;
        }

        try {
            while (cursor.moveToNext()) {
                long mmsId = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
                int msgBox = cursor.getInt(cursor.getColumnIndexOrThrow("msg_box"));
                String subject = cursor.getString(cursor.getColumnIndexOrThrow("sub"));
                long date = cursor.getLong(cursor.getColumnIndexOrThrow("date")) * 1000;

                lastMmsId = mmsId;

                // msg_box=1 is inbox (received messages)
                if (msgBox != 1) {
                    continue;
                }

                if (subject == null) subject = "";

                processMmsWithRetry(mmsId, subject, date, 0);
            }
        } finally {
            cursor.close();
        }
    }

    private void processMmsWithRetry(long mmsId, String subject, long date, int attempt) {
        long delay = attempt == 0 ? 2000 : RETRY_DELAYS_MS[Math.min(attempt - 1, RETRY_DELAYS_MS.length - 1)];

        new Handler(context.getMainLooper()).postDelayed(() -> {
            String sender = getMmsSender(mmsId);
            if (sender == null && attempt < MAX_READ_RETRIES) {
                processMmsWithRetry(mmsId, subject, date, attempt + 1);
                return;
            }
            if (sender == null) {
                Log.w(TAG, "Could not read sender for MMS " + mmsId + " after retries");
                return;
            }

            ArrayList<MmsPart> parts = getMmsParts(mmsId);
            if (parts.isEmpty() && attempt < MAX_READ_RETRIES) {
                processMmsWithRetry(mmsId, subject, date, attempt + 1);
                return;
            }

            StringBuilder textContent = new StringBuilder();
            ArrayList<MmsPart> attachments = new ArrayList<>();
            for (MmsPart part : parts) {
                if (part.contentType != null && part.contentType.equals("text/plain")) {
                    if (part.text != null) {
                        textContent.append(part.text);
                    }
                } else if (part.data != null && part.data.length > 0) {
                    attachments.add(part);
                }
            }

            String senderName = getContactNameByPhoneNumber(sender, context);
            ArrayList<String> recipients = getMmsRecipients(mmsId);
            String displayName = formatGroupName(sender, senderName, recipients);
            String text = textContent.toString();

            if (attachments.isEmpty()) {
                WebhookMessage message = new WebhookMessage(
                        "Incoming MMS", sender, displayName, 0, "undetected",
                        text, date, subject, "", "", null);
                dispatchToWebhooks(message);
            } else {
                for (MmsPart attachment : attachments) {
                    String b64Data = Base64.encodeToString(attachment.data, Base64.NO_WRAP);
                    WebhookMessage message = new WebhookMessage(
                            "Incoming MMS", sender, displayName, 0, "undetected",
                            text, date, subject, b64Data, attachment.contentType, attachment.data);
                    dispatchToWebhooks(message);
                }
            }
        }, delay);
    }

    private void dispatchToWebhooks(WebhookMessage message) {
        ArrayList<ForwardingConfig> configs = ForwardingConfig.getAll(context);
        String asterisk = context.getString(R.string.asterisk);

        for (ForwardingConfig config : configs) {
            if ((message.senderPhoneNumber == null ||
                    !message.senderPhoneNumber.equals(config.getSender())) &&
                    !config.getSender().equals(asterisk)) {
                continue;
            }

            if (!config.getIsSmsEnabled()) {
                continue;
            }

            if (config.getSimSlot() > 0 && config.getSimSlot() != message.simSlotId) {
                continue;
            }

            // 1. Send the normal templated text webhook
            callWebHook(config, message);

            // 2. Send raw binary attachment if enabled and data is present
            if (config.getAttachmentUploadEnabled()
                    && message.mmsAttachmentData != null
                    && message.mmsAttachmentData.length > 0) {
                callBinaryWebHook(config, message);
            }
        }
    }

    private String getMmsSender(long mmsId) {
        Uri addrUri = Uri.parse("content://mms/" + mmsId + "/addr");
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(addrUri,
                new String[]{"address", "type"},
                "type = " + MMS_ADDR_TYPE_FROM,
                null, null);

        if (cursor == null) return null;

        try {
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow("address"));
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    private ArrayList<String> getMmsRecipients(long mmsId) {
        ArrayList<String> recipients = new ArrayList<>();
        Uri addrUri = Uri.parse("content://mms/" + mmsId + "/addr");
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(addrUri,
                new String[]{"address", "type"},
                "type = " + MMS_ADDR_TYPE_TO,
                null, null);

        if (cursor == null) return recipients;

        try {
            while (cursor.moveToNext()) {
                String addr = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                if (addr != null && !addr.isEmpty()) {
                    recipients.add(addr);
                }
            }
        } finally {
            cursor.close();
        }
        return recipients;
    }

    private boolean isOwnNumber(String number) {
        try {
            android.telephony.TelephonyManager tm =
                    (android.telephony.TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm != null) {
                String ownNumber = tm.getLine1Number();
                if (ownNumber != null && !ownNumber.isEmpty()) {
                    String norm1 = number.replaceAll("[^\\d+]", "");
                    String norm2 = ownNumber.replaceAll("[^\\d+]", "");
                    return norm1.endsWith(norm2) || norm2.endsWith(norm1);
                }
            }
        } catch (SecurityException ignored) {}
        // Also filter the generic "insert-address-token" placeholder Android uses
        return "insert-address-token".equals(number);
    }

    private String formatGroupName(String sender, String senderName, ArrayList<String> recipients) {
        if (recipients.isEmpty()) {
            return senderName;
        }

        StringBuilder group = new StringBuilder();
        group.append(senderName).append(" - ").append(senderName);

        for (String recip : recipients) {
            if (isOwnNumber(recip)) continue;
            String recipName = getContactNameByPhoneNumber(recip, context);
            group.append(", ").append(recipName);
        }

        return group.toString();
    }

    private ArrayList<MmsPart> getMmsParts(long mmsId) {
        ArrayList<MmsPart> parts = new ArrayList<>();
        Uri partUri = Uri.parse("content://mms/" + mmsId + "/part");
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(partUri,
                new String[]{"_id", "ct", "text", "_data"},
                null, null, null);

        if (cursor == null) return parts;

        try {
            while (cursor.moveToNext()) {
                MmsPart part = new MmsPart();
                part.id = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
                part.contentType = cursor.getString(cursor.getColumnIndexOrThrow("ct"));
                int textCol = cursor.getColumnIndex("text");
                part.text = textCol >= 0 ? cursor.getString(textCol) : null;

                if (part.contentType != null && !part.contentType.equals("text/plain")) {
                    part.data = readPartData(part.id);
                }

                parts.add(part);
            }
        } finally {
            cursor.close();
        }
        return parts;
    }

    private byte[] readPartData(long partId) {
        Uri partUri = Uri.parse("content://mms/part/" + partId);
        try (InputStream is = context.getContentResolver().openInputStream(partUri)) {
            if (is == null) return new byte[0];
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            Log.e(TAG, "Failed to read MMS part data: " + e.getMessage());
            return new byte[0];
        }
    }

    private long getLatestMmsId() {
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(MMS_CONTENT_URI,
                new String[]{"_id"},
                null, null, "_id DESC");

        if (cursor == null) return 0;

        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
            }
        } finally {
            cursor.close();
        }
        return 0;
    }

    /**
     * Sends the webhook via a background thread with manual retry and exponential backoff.
     * We avoid WorkManager here because its Data object has a ~10KB size limit,
     * which base64-encoded MMS attachments will easily exceed.
     */
    private void callWebHook(ForwardingConfig config, WebhookMessage message) {
        String strMessage = config.prepareMessage(message);
        int maxRetries = config.getRetriesNumber();

        new Thread(() -> {
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                Request request = new Request(config.getUrl(), strMessage);
                request.setJsonHeaders(config.prepareHeaders(message));
                request.setIgnoreSsl(config.getIgnoreSsl());
                request.setUseChunkedMode(config.getChunkedMode());

                String result = request.execute();
                if (Objects.equals(result, Request.RESULT_SUCCESS)) {
                    return;
                }
                if (Objects.equals(result, Request.RESULT_ERROR)) {
                    return;
                }
                // RESULT_RETRY: wait with exponential backoff
                try {
                    Thread.sleep((long) Math.pow(2, attempt) * 1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
            Log.w(TAG, "Webhook delivery failed after " + maxRetries + " retries");
        }).start();
    }

    private void callBinaryWebHook(ForwardingConfig config, WebhookMessage message) {
        String preparedHeaders = config.prepareAttachmentHeaders(message);
        int maxRetries = config.getRetriesNumber();

        new Thread(() -> {
            for (int attempt = 0; attempt <= maxRetries; attempt++) {
                BinaryRequest request = new BinaryRequest(
                        config.getAttachmentUrl(),
                        config.getAttachmentMethod(),
                        message.mmsAttachmentData,
                        message.mmsAttachmentType);
                request.setJsonHeaders(preparedHeaders);
                request.setIgnoreSsl(config.getIgnoreSsl());

                String result = request.execute();
                if (Objects.equals(result, BinaryRequest.RESULT_SUCCESS)) {
                    return;
                }
                if (Objects.equals(result, BinaryRequest.RESULT_ERROR)) {
                    return;
                }
                try {
                    Thread.sleep((long) Math.pow(2, attempt) * 1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
            Log.w(TAG, "Binary attachment delivery failed after " + maxRetries + " retries");
        }).start();
    }

    private static String getContactNameByPhoneNumber(String phoneNumber, Context context) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED) {
            try {
                ContentResolver contentResolver = context.getContentResolver();
                Uri uri = Uri.withAppendedPath(
                        ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
                String[] projection = {ContactsContract.PhoneLookup.DISPLAY_NAME};
                Cursor cursor = contentResolver.query(uri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                    String contactName = cursor.getString(nameIndex);
                    cursor.close();
                    if (contactName != null) return contactName;
                }
            } catch (java.lang.SecurityException se) {
                Log.w(TAG, "READ_CONTACTS permission not granted");
            }
        }
        return phoneNumber;
    }

    private static class MmsPart {
        long id;
        String contentType;
        String text;
        byte[] data;
    }
}
