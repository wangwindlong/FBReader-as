// ConfigInterface.aidl
package org.geometerplus.android.fbreader.config;

// Declare any non-default types here with import statements

import java.util.List;

interface ConfigInterface {
	List<String> listGroups();
	List<String> listNames(in String group);

	String getValue(in String group, in String name);
	void setValue(in String group, in String name, in String value);
	void unsetValue(in String group, in String name);
	void removeGroup(in String name);

	List<String> requestAllValuesForGroup(in String group);
}
