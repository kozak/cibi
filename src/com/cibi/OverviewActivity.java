package com.cibi;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.TextView;
import com.cibi.overlay.PoliceItem;
import com.cibi.overlay.PoliceItemizedOverlay;
import com.google.android.maps.*;

import java.util.List;

public class OverviewActivity extends MapActivity
{
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.overview);
        MapView view = (MapView) findViewById(R.id.mapview);
        view.setBuiltInZoomControls(true);
        List<Overlay> overlays = view.getOverlays();
        Drawable drawable = this.getResources().getDrawable(R.drawable.androidmarker);
        PoliceItemizedOverlay itemizedOverlay = new PoliceItemizedOverlay(drawable, getApplicationContext());
        GeoPoint p = new GeoPoint(19240000,-99120000);
        PoliceItem item = new PoliceItem(p, "Hola, Mundo!", "I'm in Mexico City!");
        itemizedOverlay.addOverlay(item);
        overlays.add(itemizedOverlay);
        view.getController().setZoom(10);
        view.getController().animateTo(p);
    }

    @Override
    protected boolean isRouteDisplayed() {
        return false;
    }
}
