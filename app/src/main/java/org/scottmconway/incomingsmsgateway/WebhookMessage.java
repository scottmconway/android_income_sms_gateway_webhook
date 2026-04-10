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
    public String mmsAttachment;
    public String mmsAttachmentType;

    public WebhookMessage(String messageType, String senderPhoneNumber, String senderName, int simSlotId, String simSlotName, String messageContent, long timestamp) {
        this(messageType, senderPhoneNumber, senderName, simSlotId, simSlotName, messageContent, timestamp, "", "", "");
    }

    public WebhookMessage(String messageType, String senderPhoneNumber, String senderName, int simSlotId, String simSlotName, String messageContent, long timestamp, String mmsSubject, String mmsAttachment, String mmsAttachmentType) {
        this.messageType = messageType;
        this.senderName = senderName;
        this.simSlotId = simSlotId;
        this.simSlotName = simSlotName;
        this.messageContent = messageContent;
        this.timestamp = timestamp;
        this.mmsSubject = mmsSubject != null ? mmsSubject : "";
        this.mmsAttachment = mmsAttachment != null ? mmsAttachment : "";
        this.mmsAttachmentType = mmsAttachmentType != null ? mmsAttachmentType : "";

        if (senderPhoneNumber == null) {
            this.senderPhoneNumber = "";
        }
        else this.senderPhoneNumber = senderPhoneNumber;
    }
}
