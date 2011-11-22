package com.cibi.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import com.cibi.R;

/**
 *
 */
public class Communication {
    public static final int VOICE_RECOGNITION_REQUEST_CODE = 1234;
    public static final int T2S_CHECK_CODE = 1235;


    public static void inputVoiceCommand(Activity activity) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        // Specify the calling package to identify your application
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, activity.getClass().getPackage().getName());
        // Display an hint to the user about what he should say.
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, R.string.speak_prompt);
        // Given an hint to the recognizer about what the user is going to say
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // Specify how many results you want to receive. The results will be sorted
        // where the first result is the one with higher confidence.
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        activity.startActivityForResult(intent, VOICE_RECOGNITION_REQUEST_CODE);


    }
}
