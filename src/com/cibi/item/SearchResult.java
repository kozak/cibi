package com.cibi.item;

import java.util.ArrayList;
import java.util.List;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * @author morswin
 */

public final class SearchResult implements Parcelable {

	public static final Creator<SearchResult> CREATOR = new Creator<SearchResult>() {
		public SearchResult[] newArray(int size) {
			return new SearchResult[size];
		}
		
		public SearchResult createFromParcel(Parcel source) {
			return new SearchResult(source);
		}
	};
	
	private List<GeoItem> items;
	
	public SearchResult() {
		items = new ArrayList<GeoItem>();
	}
	
	@SuppressWarnings("unchecked")
	private SearchResult(Parcel source) {
		items = source.readArrayList(GeoItem.class.getClassLoader());
	}
	
	public void addGeoItem(GeoItem item) {
		items.add(item);
	}
	
	public List<GeoItem> getItems() {
		return items;
	}
	
	public int describeContents() {
		return 0;
	}

	public void writeToParcel(Parcel dest, int flags) {
		dest.writeList(items);
	}

}
