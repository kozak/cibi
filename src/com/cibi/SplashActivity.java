package com.cibi;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

/**
 * @author morswin
 */
public class SplashActivity extends Activity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash);

        final SplashActivity self = this;
        TimerTask runMain = new TimerTask() {
            @Override
            public void run() {
                finish();
                Intent intent = new Intent(self, OverviewActivity.class);
                startActivity(intent);
            }
        };
        Timer timer = new Timer("Splash timer");
        timer.schedule(runMain, 2 * 1000L);

    }
}