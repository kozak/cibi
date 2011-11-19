package com.cibi.service;

import com.cibi.service.ItemApi;
import com.cibi.service.ItemListener;
import com.cibi.item.SearchResult;

interface ItemApi {
    SearchResult getLatestSearchResult();
    void addListener(ItemListener listener);
    void removeListener(ItemListener listener);
    void setParams(int lat, int lng, int latSpan, int lngSpan, in String[] types);

}
