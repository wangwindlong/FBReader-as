// CoverReader.aidl
package org.geometerplus.android.fbreader.formatPlugin;

import android.graphics.Bitmap;

interface CoverReader {
	Bitmap readBitmap(in String path, in int maxWidth, in int maxHeight);
}
