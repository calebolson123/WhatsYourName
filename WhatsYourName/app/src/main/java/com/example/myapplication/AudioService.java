package com.example.myapplication;

import static com.example.myapplication.MainActivity.CONVERSATION;

import static java.lang.Math.abs;

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

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;


import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Array;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

public class AudioService extends Service implements RecognitionListener {

    private static final String NOTIFICATION_CHANNEL_DFU = "123";
    private NotificationChannel chan;
    private TokenNameFinderModel model;
    private TokenNameFinder nameFinder;
    private Tokenizer tokenizer;
    private String theName = "";
    private Map<String, Integer> weightedEntities;

    Instant convoStartTime = null;
    protected String conversation = "";
    protected ArrayList<String> conversationArr = new ArrayList<String>();

    private final IBinder mBinder = new LocalBinder();

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
    public IBinder onBind(Intent arg0) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        AudioService getService() {
            return AudioService.this;
        }
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
        convoStartTime = Instant.now();
    }

    @Override
    public void onRmsChanged(float v) {}

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
                    conversation = "Listening...";
                    conversationArr.set(0, conversation);
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

        if(data != null && data.size() > 0 && data.get(0) != null && data.get(0).length() > 0) {
            try {
                conversation += StringUtils.capitalize(data.get(0).trim() + ". ");
                conversationArr.add(StringUtils.capitalize(data.get(0).trim() + "."));
                ArrayList<String> names = findNames(conversationArr);
                System.out.println("namesARrrrrr: " + conversationArr.toString());
//                System.out.println("convo: " + conversation);

//                String name = theName;
                if(names.size() > 0) {
                    weightedEntities = weightNamedEntitiesBasedOnDistanceBetween(names);
                    weightedEntities = weightNamedEntitiesBasedOnDistanceFromStartOfConversation(weightedEntities);
                }

                String name = "tmp";

                Iterator<Map.Entry<String, Integer>> itr = weightedEntities.entrySet().iterator();

                while(itr.hasNext()) {
                    Map.Entry<String, Integer> entry = itr.next();
                    System.out.println("Key = " + entry.getKey() + ", Value = " + entry.getValue());

pick the victor, descending list? or just top? top pair?
                }

                if(!theName.equals(name)) {
                    System.out.println("Name updating to: " + name);
                    theName = name;
                    updateNotification(name);
                }

                Intent intent = new Intent(CONVERSATION);
                intent.putExtra(CONVERSATION, conversation);
                sendBroadcast(intent);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // add weight (bad/punishment) based on distance from start of conversation
    private Map<String, Integer> weightNamedEntitiesBasedOnDistanceFromStartOfConversation(Map<String, Integer> weightedEntities) {
        String strOnlyWords = conversation.replace(",", "").replace(".", "");

        System.out.println("strOnlyWords: " + strOnlyWords.toString());
        final List<String> words = Arrays.asList(strOnlyWords.split(" "));
        System.out.println("words: " + words.toString());

        for (String name : weightedEntities.keySet()) {
            int distanceFromStart = words.indexOf(name);
            System.out.println("indexOf " + name + " is: " + distanceFromStart);
            System.out.println("weight before: " + weightedEntities.get(name));
            weightedEntities.put(name, weightedEntities.get(name) + distanceFromStart);
            System.out.println("weight after: " + weightedEntities.get(name));
        }

        return weightedEntities;
    }

    // apply rules & heuristics to decide most likely name
    private Map<String, Integer> weightNamedEntitiesBasedOnDistanceBetween(ArrayList<String> names) {
        Map<String, Integer> nameScoreMap = new HashMap<String, Integer>();

        // TODO: make names unique probably? or should frequency within conversation be accounted for?
        // TODO: fix below

        System.out.println("APPLY WEIGHTS: " + names.toString());

        // H1: distance between entities? Typically both names when meeting are nearby
        for (int i = 0; i < names.size() - 1; i++) {
            for (int j = i + 1; j < names.size(); j++) {
                System.out.println("("+i+" , "+j+")");
                System.out.println("comparing: " + names.get(i) + " : " + names.get(j));
                int distance = distanceBetweenWords(names.get(i), names.get(j));

                int vi = 0;
                System.out.println("GET: " + nameScoreMap.get(names.get(i)));
                if(nameScoreMap.get(names.get(i)) != null) {
                    System.out.println("value: " + nameScoreMap.get(names.get(i)));
                    vi = nameScoreMap.get(names.get(i));
                }
                int vj = 0;
                if(nameScoreMap.get(names.get(j)) != null) {
                    System.out.println("value: " + nameScoreMap.get(names.get(j)));
                    vj = nameScoreMap.get(names.get(j));
                }
                if(distance < vi || vi == 0) {
                    nameScoreMap.put(names.get(i), distance);
                }
                if(distance < vj || vj == 0) {
                    nameScoreMap.put(names.get(j), distance);
                }
            }
        }

        for (Map.Entry<String,Integer> entry : nameScoreMap.entrySet())
            System.out.println("Key = " + entry.getKey() +
                    ", Value = " + entry.getValue());

        return nameScoreMap;
    }

    private int distanceBetweenWords(String w1, String w2) {
        // Remove any special chars from string
        String strOnlyWords = conversation.replace(",", "").replace(".", "");

        System.out.println("strOnlyWords: " + strOnlyWords.toString());
        final List<String> words = Arrays.asList(strOnlyWords.split(" "));
        System.out.println("words: " + words.toString());
        final int index1 = words.indexOf(w1);
        final int index2 = words.indexOf(w2);
        int distance = -1;

        // Check index of two words
        if (index1 != -1 && index2 != - 1) {
            distance = abs(index2 - index1);
        }

        System.out.println(distance);
        return distance;
    }

    @Override
    public void onEvent(int i, Bundle bundle) {
        System.out.println("onEvent: " + i);
    }

    public ArrayList<String> findNames(ArrayList<String> paragraph) throws IOException {
//        paragraph = "Pierre Vinken, 61 years old, will join the board as a nonexecutive director Nov. 29 . Mr . Vinken is chairman of Elsevier N.V. , the Dutch publishing group . Rudolph Agnew , 55 years old and former chairman of Consolidated Gold Fields PLC , was named a director of this British industrial conglomerate.";

//        String[] tokens = tokenizer.tokenize(paragraph);
//        Span[] names = nameFinder.find(tokens);

        ArrayList<String> namesList = new ArrayList<String>();
//        for(Span s: names) {
//            System.out.print("Names found in convo: " + tokens[s.getStart()]);
//            namesList.add(tokens[s.getStart()]);
//        }
//        return tokens[names[0].getStart()];

            for (int i = 0; i < paragraph.size(); i++) {
                String[] tokens = tokenizer.tokenize(paragraph.get(i));
                Span[] names = nameFinder.find(tokens);
                // do something with the names

                for(Span s: names) {
                    System.out.println("Names found in convo: " + tokens[s.getStart()]);
                    namesList.add(tokens[s.getStart()]);
//                    System.out.println("TEST: " + s.getStart());
                }

                nameFinder.clearAdaptiveData();
            }

        return namesList;
    }

}