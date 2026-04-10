# Incoming SMS/MMS to URL forwarder
This application provides a simple interface to send HTTP requests with information from incoming SMS/MMS messages and calls.

## Available Placeholders
* %from% - sender phone number
* %fromName% - sender contact name (or phone number if not in contacts)
* %text% - message body (SMS or MMS text content)
* %messageType% - message type ("Incoming SMS", "Incoming MMS", "Incoming Call")
* %sentStamp% - sent timestamp
* %receivedStamp% - received timestamp
* %sim% - SIM slot name
* %mmsSubject% - MMS subject line (if present)
* %mmsAttachmentB64% - MMS attachment as base64-encoded string
* %mmsAttachmentType% - MMS attachment MIME type (eg. "image/jpeg")
* %mmsFilename% - derived filename from MIME type (eg. "attachment.jpg")

Placeholders may be used in the request's JSON body or headers.

### MMS Attachment Upload
For MMS messages with binary attachments (images, videos, contact cards, etc.), the app can optionally send a second HTTP request with the raw attachment data as the request body.

The attachment upload has its own configuration:
* URL - defaults to the webhook URL if not set
* Method - PUT or POST
* Headers

### Group MMS
For group MMS messages, `%fromName%` is formatted as:
```
Sender - Sender, Recipient1, Recipient2, ...
```
Your own number is excluded from the recipient list.

## Request Info
HTTP method: POST  
Content-type: application/json; charset=utf-8  

If HTTP Basic Auth credentials are present in the URL, an `Authorization` header will be composed automatically. If an explicit `Authorization` header value is supplied as well, the header value will override the credentials in the URL.

Sample configuration for ntfy:  

<table>
<tr>
<td> Parameter </td> <td> Value </td>
</tr>
<td> Sender </td> <td> * </td>
</tr>
<td> Webhook URL </td> <td> https://ntfy.sh </td>
</tr>
<td> Payload </td>
<td>

```json
{
    "topic": "incoming-sms",
    "message": "%fromName%: %text% %sentStamp% %receivedStamp% %sim%"
    "title": "%messageType"
}
```

</td>
</tr>
<tr>
<td> Headers </td>
<td>

```json
{
    "Authorization": "bearer tk_..."}
```

</td>
</tr>
</table>


## Screenshots
<img alt="Incoming SMS Webhook Gateway screenshot 1" src="https://raw.githubusercontent.com/bogkonstantin/android_income_sms_gateway_webhook/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="30%"/> <img alt="Incoming SMS Webhook Gateway screenshot 2" src="https://raw.githubusercontent.com/bogkonstantin/android_income_sms_gateway_webhook/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="30%"/> <img alt="Incoming SMS Webhook Gateway screenshot 3" src="https://raw.githubusercontent.com/bogkonstantin/android_income_sms_gateway_webhook/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="30%"/>

## Builds

You can download apk releases from the [release page](https://github.com/scottmconway/android_income_sms_gateway_webhook/releases)
