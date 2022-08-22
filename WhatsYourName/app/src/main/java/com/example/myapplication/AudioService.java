package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;


import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.Span;

public class AudioService extends Service implements RecognitionListener {

    private static final String NOTIFICATION_CHANNEL_DFU = "123";
    private NotificationChannel chan;
    private TokenNameFinderModel model;
    private TokenNameFinder nameFinder;
    private Tokenizer tokenizer;
    private String theName = "";

    Instant convoStartTime = null;
    private String conversation = "";

    // Binder given to clients
    private final IBinder iBinder = (IBinder) new LocalBinder();

    private SpeechRecognizer speechRecognizer;

    public void onCreate() {
        super.onCreate();

        try {
            InputStream inputStream = (InputStream)this.getResources().openRawResource(R.raw.name_model);
            model = new TokenNameFinderModel(inputStream);
            nameFinder = new NameFinderME(model);
            tokenizer = SimpleTokenizer.INSTANCE;
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            chan = new NotificationChannel(NOTIFICATION_CHANNEL_DFU, "Update service", NotificationManager.IMPORTANCE_NONE); chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            service.createNotificationChannel(chan);

            Notification notification = getMyActivityNotification("Listening...");
            startForeground(1005, notification);

            System.out.println("onCreate");
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        startListeningAgain();

        return START_NOT_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void startListeningAgain() {
        AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        boolean isMuted = audioManager.isStreamMute(AudioManager.STREAM_NOTIFICATION);
        if(!isMuted) {
            audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
        }

        final Intent speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 999999999);

        speechRecognizer.setRecognitionListener(this);
        speechRecognizer.startListening(speechRecognizerIntent);

        if(!isMuted) {
            new java.util.Timer().schedule(
                new java.util.TimerTask() {
                    @Override
                    public void run() {
                        audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, false);

                    }
                },3000
            );
        }
    }

    private Notification getMyActivityNotification(String text){

        System.out.println("getMyActivityNotification");
        System.out.println(text);

        NotificationCompat.Builder builder = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = new NotificationCompat.Builder(this, chan.getId())
                    .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.raw.icon))
                    .setSmallIcon(R.raw.icon)
                    .setContentTitle("What's your name?")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_MAX);
//            builder.setSilent(true);
//            builder.setSound(null);
        }

        return builder.build();
    }

    private void updateNotification(String text) {
        Notification notification = getMyActivityNotification(text);
        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(1005, notification);

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        @SuppressLint("InvalidWakeLockTag") PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "Tag");
        wakeLock.acquire();
        wakeLock.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
//        throw new UnsupportedOperationException("Not yet implemented");
        return iBinder;
    }

    @Override
    public void onDestroy() {
        System.out.println("DESTROY");
        super.onDestroy();
        speechRecognizer.destroy();
    }

    @Override
    public void onReadyForSpeech(Bundle bundle) {
        System.out.println("onReadyForSpeech");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onBeginningOfSpeech() {
        System.out.println("onBeginningOfSpeech");
//        if(convoStartTime == null) {
        convoStartTime = Instant.now();
//        }
//        System.out.println("convoStartTime: " + convoStartTime);
    }

    @Override
    public void onRmsChanged(float v) {

    }

    @Override
    public void onBufferReceived(byte[] bytes) {
        System.out.println("onBufferReceived");
    }

    @Override
    public void onEndOfSpeech() {
        System.out.println("onEndOfSpeech");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onError(int i) {
        System.out.println("onError: " + i);
        if (i == 7) {
            if(convoStartTime != null && !theName.equals("Listening...")) {
                Duration res = Duration.between(convoStartTime, Instant.now());
                System.out.println("res.getSeconds(): " + res.getSeconds());

                if (res.getSeconds() >= 30) {
                    System.out.println("Reset stored conversation.");
                    conversation = "";
                    convoStartTime = null;
                    theName = "Listening...";
                    updateNotification(theName);
                }
            }

            startListeningAgain();
        }

        // else handle error

    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onResults(Bundle bundle) {
        System.out.println("onResults");

        // TODO: restart activity here? consider convo done?
        startListeningAgain();
    }

    @Override
    public void onPartialResults(Bundle bundle) {
        ArrayList<String> data = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
//        System.out.println("data: " + data);

        if(data != null && data.size() > 0 && data.get(0) != null && data.get(0).length() > 0) {
            try {
                conversation += StringUtils.capitalize(data.get(0) + ". ");
                String name = findName(conversation);
                if(name.length() > 0 && !theName.equals(name)) {
                    System.out.println("Name updating to: " + name);
                    theName = name;
                    updateNotification(name);
                }
                System.out.println("convo: " + conversation);

//this cant happen here, has to happen in activity..need to bind/pass back, look it up
//                TextView textView = (TextView) findViewById(R.id.conversation);
//                textView.setText("Enter whatever you Like!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onEvent(int i, Bundle bundle) {
        System.out.println("onEvent: " + i);
    }

    public class LocalBinder extends Binder {
        public AudioService getService() {
            return AudioService.this;
        }
    }


    public String findName(String paragraph) throws IOException {
        // paragraph = "Pierre Vinken, 61 years old, will join the board as a nonexecutive director Nov. 29 . Mr . Vinken is chairman of Elsevier N.V. , the Dutch publishing group . Rudolph Agnew , 55 years old and former chairman of Consolidated Gold Fields PLC , was named a director of this British industrial conglomerate.";

        String[] tokens = tokenizer.tokenize(paragraph);
        Span[] names = nameFinder.find(tokens);

        String nameToDisplay = "";
        System.out.print("Names found in convo: ");
        for(Span s: names) {
            nameToDisplay = tokens[s.getStart()];
            System.out.print(tokens[s.getStart()]);
        }
//        return tokens[names[0].getStart()];

        return nameToDisplay;
    }

}