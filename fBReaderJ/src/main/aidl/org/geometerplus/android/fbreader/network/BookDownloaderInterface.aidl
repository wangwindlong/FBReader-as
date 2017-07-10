// BookDownloaderInterface.aidl
package org.geometerplus.android.fbreader.network;

// Declare any non-default types here with import statements

interface BookDownloaderInterface {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
	boolean isBeingDownloaded(in String url);
}
