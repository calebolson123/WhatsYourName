package com.example.myapplication;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends AppCompatActivity {
    public static final Integer RecordAudioRequestCode = 1;
    public static final String CONVERSATION = "WhatIsYourName";
    public static final String SCORED_ENTITIES = "WhatIsYourName_ScoredEntities";
    private DataUpdateReceiver dataUpdateReceiver;
    protected TextView conversationView = null;
    protected TextView scoredEntitiesView = null;
    private Matcher m = null;
    List<String> highlightColors = Arrays.asList("green", "red", "yellow", "blue", "purple", "brown", "orange", "pink");
    AudioService services;

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED){
            checkPermission();
        }

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                AudioService.LocalBinder binderr = (AudioService.LocalBinder) service;
                services = binderr.getService();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };

        Intent intent = new Intent(this, AudioService.class);
        this.startForegroundService(intent);

        bindService(intent, connection, Context.BIND_IMPORTANT);
        conversationView = findViewById(R.id.conversation);
        scoredEntitiesView = findViewById(R.id.scoredEntities);
        conversationView.setMovementMethod(new ScrollingMovementMethod());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (dataUpdateReceiver == null) dataUpdateReceiver = new DataUpdateReceiver();
        IntentFilter intentFilter = new IntentFilter(CONVERSATION);
        registerReceiver(dataUpdateReceiver, intentFilter);
        IntentFilter intentFilter2 = new IntentFilter(SCORED_ENTITIES);
        registerReceiver(dataUpdateReceiver, intentFilter2);

        if(services != null) {
            String prettyScores = services.prettyStringSortedScoresAndNames();
            m = Pattern.compile("\\w+").matcher(prettyScores);

            int i = 0;
            while (m.find()) {
                String highlight = highlightColors.get(i);
                String coloredMatch = String.format("<font color=%s>%s</font>", highlight, m.group());
                prettyScores = prettyScores.replaceAll(m.group(), coloredMatch);
                i++;
            }
            scoredEntitiesView.setText(Html.fromHtml(prettyScores));

            String conversation = services.conversation;
            int j = 0;
            if (m != null) {
                m.reset();
                while (m.find()) {
                    String highlight = highlightColors.get(j);
                    String coloredMatch = String.format("<font color=%s>%s</font>", highlight, m.group());
                    conversation = conversation.replaceFirst(m.group(), coloredMatch);
                    j++;
                }
                conversationView.setText(Html.fromHtml(conversation));
            } else {
                conversationView.setText(conversation);
            }
        }
    }

    protected void onPause() {
        super.onPause();
        if (dataUpdateReceiver != null) unregisterReceiver(dataUpdateReceiver);
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECORD_AUDIO},RecordAudioRequestCode);
        }
    }

    private class DataUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(SCORED_ENTITIES)) {
                String prettyScores = intent.getStringExtra(SCORED_ENTITIES);
//                System.out.println("prettyScores rawwww: " + prettyScores);
                m = Pattern.compile("\\w+").matcher(prettyScores);

                int i = 0;
                while (m.find()) {
                    String highlight = highlightColors.get(i);
                    String coloredMatch = String.format("<font color=%s>%s</font>", highlight, m.group());
                    prettyScores = prettyScores.replaceAll(m.group(), coloredMatch);
                    i++;
                }

                scoredEntitiesView.setText(Html.fromHtml(prettyScores));
            }

            String conversation = services.conversation;

            int i = 0;
            if (m != null) {
                m.reset();
                while (m.find()) {
                    String highlight = highlightColors.get(i);
                    String coloredMatch = String.format("<font color=%s>%s</font>", highlight, m.group());
                    conversation = conversation.replaceFirst(m.group(), coloredMatch);
                    i++;
                }
                conversationView.setText(Html.fromHtml(conversation));
            } else {
                conversationView.setText(conversation);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RecordAudioRequestCode && grantResults.length > 0 ){
            if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
                Toast.makeText(this,"Permission Granted",Toast.LENGTH_SHORT).show();
        }
    }
}