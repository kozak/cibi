package com.cibi.overlay;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;

import java.util.ArrayList;
import java.util.List;

/**
 * @author morswin
 */
public class CustomItemizedOverlay extends ItemizedOverlay {

    private List<OverlayItem> overlays = new ArrayList<OverlayItem>();
    private Context context;

    public CustomItemizedOverlay(Drawable drawable) {
        super(boundCenter(drawable));
    }

    public CustomItemizedOverlay(Drawable drawable, Context context) {
        super(boundCenter(drawable));
        this.context = context;
    }


    public void addOverlay(OverlayItem overlay) {
        overlays.add(overlay);
        populate();
    }


    @Override
    protected OverlayItem createItem(int i) {
        return overlays.get(i);
    }

    @Override
    public int size() {
        return overlays.size();
    }

    public void clear(){
        overlays.clear();
        populate();
    }
}
