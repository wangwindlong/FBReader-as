// ApiInterface.aidl
package org.geometerplus.android.fbreader.api;

// Declare any non-default types here with import statements
import org.geometerplus.android.fbreader.api.ApiObject;

interface ApiInterface {
	ApiObject request(int method, in ApiObject[] parameters);
	List<ApiObject> requestList(int method, in ApiObject[] parameters);
	Map requestMap(int method, in ApiObject[] parameters);
}
