package org.scottmconway.incomingsmsgateway;

public class WebhookMessage {
    public String messageType;
    public String senderPhoneNumber;
    public String senderName;
    public int simSlotId;
    public String simSlotName;
    public String messageContent;
    public long timestamp;
    public String mmsSubject;
    public String mmsAttachmentB64;
    public String mmsAttachmentType;
    public byte[] mmsAttachmentData;

    public WebhookMessage(String messageType, String senderPhoneNumber, String senderName, int simSlotId, String simSlotName, String messageContent, long timestamp) {
        this(messageType, senderPhoneNumber, senderName, simSlotId, simSlotName, messageContent, timestamp, "", "", "", null);
    }

    public WebhookMessage(String messageType, String senderPhoneNumber, String senderName, int simSlotId, String simSlotName, String messageContent, long timestamp, String mmsSubject, String mmsAttachmentB64, String mmsAttachmentType, byte[] mmsAttachmentData) {
        this.messageType = messageType;
        this.senderName = senderName;
        this.simSlotId = simSlotId;
        this.simSlotName = simSlotName;
        this.messageContent = messageContent;
        this.timestamp = timestamp;
        this.mmsSubject = mmsSubject != null ? mmsSubject : "";
        this.mmsAttachmentB64 = mmsAttachmentB64 != null ? mmsAttachmentB64 : "";
        this.mmsAttachmentType = mmsAttachmentType != null ? mmsAttachmentType : "";
        this.mmsAttachmentData = mmsAttachmentData;

        if (senderPhoneNumber == null) {
            this.senderPhoneNumber = "";
        }
        else this.senderPhoneNumber = senderPhoneNumber;
    }

    public String getMmsFilename() {
        return "attachment" + MimeTypeHelper.getExtension(mmsAttachmentType);
    }
}
