package com.example.myapplication;

import static com.example.myapplication.MainActivity.CONVERSATION;
import static com.example.myapplication.MainActivity.SCORED_ENTITIES;

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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinder;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.SimpleTokenizer;
import opennlp.tools.tokenize.Tokenizer;
import opennlp.tools.util.Span;

public class AudioService extends Service {

    private static final String NOTIFICATION_CHANNEL_DFU = "123";
    private NotificationChannel chan;
    private TokenNameFinderModel model;
    private TokenNameFinder nameFinder;
    private Tokenizer tokenizer;
    private String theName = "";
    private Map<String, Integer> weightedEntities;
//    private Map<String, Double> scoredEntities;
    protected TreeMap<Double, String> sortedScoresAndNames = new TreeMap<Double, String>();

    protected String conversation = "";
    protected ArrayList<String> conversationArr = new ArrayList<String>();
    private SpeechListener speechListener = new SpeechListener();

    private final IBinder mBinder = new LocalBinder();

    private SpeechRecognizer speechRecognizer;

    private Instant convoStartTime = null;

    public void onCreate() {
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

            startListeningAgain();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void startListeningAgain() {
        AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        int volume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
        if(volume > 0) {
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0);
        }

        final Intent speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 999999999);

        if(speechRecognizer != null) speechRecognizer.destroy();
        speechRecognizer = null;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(speechListener);
        speechRecognizer.startListening(speechRecognizerIntent);

        // mega jank
        if(volume > 0) {
            new Timer().schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, volume, 0);
                    }
                },3000
            );
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        speechRecognizer.destroy();
    }

    private Notification getMyActivityNotification(String text){
        NotificationCompat.Builder builder = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = new NotificationCompat.Builder(this, chan.getId())
                    .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.raw.icon))
                    .setSmallIcon(R.raw.icon)
                    .setContentTitle("What's your name?")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_MAX);
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

    protected String prettyStringSortedScoresAndNames() {
        String prettyString = "";
        if(sortedScoresAndNames != null && sortedScoresAndNames.size() > 0) {
            int i = 0;
            for (Map.Entry<Double, String> entry : sortedScoresAndNames.entrySet()) {
                if(i == 1 && entry.getKey() <= 0.5) {
                    prettyString = prettyString.replace("\n", " - ") + entry.getValue() + "\n";
                } else {
                    prettyString += entry.getValue() + "\n";
                }
                i++;
            }

        } else {
            if(!theName.equals("Listening...")) {
                prettyString = theName;
            }
        }

        return prettyString;
    }

    // add weight (bad/punishment) based on distance from start of conversation
    private Map<String, Integer> weightNamedEntitiesBasedOnDistanceFromStartOfConversation(Map<String, Integer> weightedEntities) {
        String strOnlyWords = conversation.replace(",", "").replace(".", "");

        final List<String> words = Arrays.asList(strOnlyWords.split(" "));
        for (String name : weightedEntities.keySet()) {
            int distanceFromStart = words.indexOf(name);
            weightedEntities.put(name, weightedEntities.get(name) + distanceFromStart);
        }

        return weightedEntities;
    }

    // apply rules & heuristics to decide most likely name
    private Map<String, Integer> weightNamedEntitiesBasedOnDistanceBetween(ArrayList<String> names) {
        Map<String, Integer> nameScoreMap = new HashMap<String, Integer>();

        // distance between entities. Typically both names when meeting are nearby
        for (int i = 0; i < names.size() - 1; i++) {
            for (int j = i + 1; j < names.size(); j++) {
                int distance = distanceBetweenWords(names.get(i), names.get(j)) * 4; // arbitrary multiplier to make this matter more

                int vi = 0;
                if(nameScoreMap.get(names.get(i)) != null) {
                    vi = nameScoreMap.get(names.get(i));
                }
                int vj = 0;
                if(nameScoreMap.get(names.get(j)) != null) {
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

        return nameScoreMap;
    }

    private int distanceBetweenWords(String w1, String w2) {
        // Remove any special chars from string
        String strOnlyWords = conversation.replace(",", "").replace(".", "");

        List<String> words = Arrays.asList(strOnlyWords.split(" "));
        int index1 = words.indexOf(w1);
        int index2 = words.indexOf(w2);
        int distance = -1;

        // Check index of two words
        if (index1 != -1 && index2 != - 1) {
            distance = abs(index2 - index1);
        }

        return distance;
    }

    public ArrayList<String> findNames(ArrayList<String> paragraph) throws IOException {
        ArrayList<String> namesList = new ArrayList<String>();
        for (int i = 0; i < paragraph.size(); i++) {
            String[] tokens = tokenizer.tokenize(paragraph.get(i));
            Span[] names = nameFinder.find(tokens);

            for(Span s: names) {
                String name = tokens[s.getStart()];
                if(!namesList.contains(name)){
                    namesList.add(name);
                }
            }
            nameFinder.clearAdaptiveData();
        }
        return namesList;
    }

    class SpeechListener implements RecognitionListener {
        @Override
        public void onEvent(int i, Bundle bundle) {}

        @Override
        public void onReadyForSpeech(Bundle bundle) {
//            System.out.println("onReadyForSpeech");
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onBeginningOfSpeech() {
//            System.out.println("onBeginningOfSpeech");
            convoStartTime = Instant.now();
        }

        @Override
        public void onRmsChanged(float v) {}

        @Override
        public void onBufferReceived(byte[] bytes) {}

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onEndOfSpeech() {
//            System.out.println("onEndOfSpeech");
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onError(int i) {
            if (i == 7) {
                if(convoStartTime != null && !theName.equals("Listening...")) {
                    Duration res = Duration.between(convoStartTime, Instant.now());

                    if (res.getSeconds() >= 30) {
                        conversation = "Listening...";
                        conversationArr.clear();
//                        scoredEntities.clear();
                        sortedScoresAndNames.clear();
                        convoStartTime = null;
                        theName = "Listening...";
                        updateNotification(theName);
                    }
                }
                startListeningAgain();
                return;
            }

            // else handle error
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        private void handleResults(ArrayList<String> data) {
//            for(int i=0; i< data.size(); i++) {
//                System.out.println("data[i]: " + data.get(i));
//            }
            startListeningAgain();

            if(data != null && data.size() > 0 && data.get(0) != null && data.get(0).length() > 0) {
                try {
                    conversation += StringUtils.capitalize(data.get(0).trim() + ". ");
                    conversationArr.add(StringUtils.capitalize(data.get(0)));

                    ArrayList<String> names = findNames(conversationArr);
//                    System.out.println("convo: " + conversation);
                    if(names.size() > 0) {
                        weightedEntities = weightNamedEntitiesBasedOnDistanceBetween(names);
                        weightedEntities = weightNamedEntitiesBasedOnDistanceFromStartOfConversation(weightedEntities);
                    }

                    String name = "Listening...";
                    int maxWeight = 0;
                    int minWeight = 999;
                    if (weightedEntities != null) {
                        if(names.size() == 1) {
                            name = names.get(0);
                        } else if(names.size() == 2) {
                            name = names.get(0) + " - " + names.get(1);
                        } else {
//                            System.out.println("names:::: " + names.size() + " : " + names.toString());
                            name = "";
                            for (int weight : weightedEntities.values()) {
                                minWeight = Math.min(weight, minWeight);
                                maxWeight = Math.max(weight, maxWeight);
                            }

                            Iterator<Map.Entry<String, Integer>> itr = weightedEntities.entrySet().iterator();
                            ArrayList<Double> scores = new ArrayList();

                            sortedScoresAndNames.clear();
                            while(itr.hasNext()) {
                                Map.Entry<String, Integer> entry = itr.next();
                                String entityName = entry.getKey();
                                Integer score = entry.getValue();

//                                System.out.println("name and score: " + entityName + " : " + score);

                                double percentageOfTotal = (((double)score - (double)minWeight) / (double)(maxWeight - minWeight));

                                scores.add(percentageOfTotal);
                                sortedScoresAndNames.put(percentageOfTotal, entityName);
                            }

                            Collections.sort(scores);
                            double mostLikely = scores.get(0);
                            double secondMostLikely = scores.get(1);
                            if(secondMostLikely <= 0.51) { // display both names
                                name = sortedScoresAndNames.get(mostLikely) + " - " + sortedScoresAndNames.get(secondMostLikely);
                            } else { // just top probably
                                name = sortedScoresAndNames.get(mostLikely);
                            }
                        }
                    }

                    if(!theName.equals(name)) {
                        theName = name;
                        updateNotification(name);
                    }

                    // send convo & ranked names over to activity
                    Intent intent = new Intent(CONVERSATION);
                    intent.putExtra(CONVERSATION, conversation);
                    sendBroadcast(intent);

                    String prettyScoresAndNames = prettyStringSortedScoresAndNames();
                    Intent intentPrettyScores = new Intent(SCORED_ENTITIES);
                    intentPrettyScores.putExtra(SCORED_ENTITIES, prettyScoresAndNames);
                    sendBroadcast(intentPrettyScores);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onResults(Bundle bundle) {
//            System.out.println("onResults: " + bundle);
            ArrayList<String> data = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            handleResults(data);
        }

        @RequiresApi(api = Build.VERSION_CODES.N)
        @Override
        public void onPartialResults(Bundle bundle) {
//            System.out.println("onPartialResults");
            ArrayList<String> data = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

            if(data != null && data.size() > 0 && data.get(0) != null && data.get(0).length() > 0) {
                handleResults(data);
            }
        }
    }
}