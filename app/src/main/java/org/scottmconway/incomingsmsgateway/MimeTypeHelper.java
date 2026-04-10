package org.scottmconway.incomingsmsgateway;

import android.webkit.MimeTypeMap;

public class MimeTypeHelper {

    public static String getExtension(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            return "";
        }
        String ext = MimeTypeMap.getSingleton()
                .getExtensionFromMimeType(mimeType.toLowerCase(java.util.Locale.ROOT));
        if (ext != null) {
            return "." + ext;
        }
        return "";
    }
}
