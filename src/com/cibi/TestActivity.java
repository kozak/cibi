package com.cibi;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * @author morswin
 */
public class TestActivity extends Activity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView textview = new TextView(this);
        textview.setText("This is a test tab");
        setContentView(textview);
    }
}