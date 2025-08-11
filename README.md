# Incoming SMS to URL forwarder
This application provides a simple interface to send HTTP requests with information from incoming SMS messages and calls.

## Available Placeholders
* %from%
* %fromName%
* %text%
* %messageType%
* %sentStamp%
* %receivedStamp%
* %sim%


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
