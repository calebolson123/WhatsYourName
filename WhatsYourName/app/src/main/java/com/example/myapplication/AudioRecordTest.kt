    package com.example.myapplication

    //import android.support.v4.app.ActivityCompat
    //import android.support.v7.app.AppCompatActivity
    import android.Manifest
    import android.content.Context
    import android.content.Intent
    import android.content.pm.PackageManager
    import android.media.MediaPlayer
    import android.media.MediaRecorder
    import android.os.Build
    import android.os.Bundle
    import android.os.PowerManager
    import android.os.PowerManager.WakeLock
    import android.util.Log
    import android.view.View.OnClickListener
    import android.view.ViewGroup
    import android.widget.Button
    import android.widget.LinearLayout
    import androidx.annotation.RequiresApi
    import androidx.appcompat.app.AppCompatActivity
    import androidx.core.app.ActivityCompat
    import java.io.IOException
//    import android.support.v4.app;
//    import android.support.v4.content.ContextCompat

    private const val LOG_TAG = "AudioRecordTest"
    private const val REQUEST_RECORD_AUDIO_PERMISSION = 200

    class AudioRecordTest: AppCompatActivity() {

        private var fileName: String = ""

        private var recordButton: RecordButton? = null
        private var recorder: MediaRecorder? = null

        private var playButton: PlayButton? = null
        private var player: MediaPlayer? = null

        // Requesting permission to RECORD_AUDIO
        private var permissionToRecordAccepted = false
        private var permissions: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)

        private var wakeLock: PowerManager.WakeLock? = null

        override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
        ) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            permissionToRecordAccepted = if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
            if (!permissionToRecordAccepted) finish()
        }

        private fun onRecord(start: Boolean) = if (start) {
            startRecording()
        } else {
            stopRecording()
        }

        private fun onPlay(start: Boolean) = if (start) {
            startPlaying()
        } else {
            stopPlaying()
        }

        private fun startPlaying() {
            player = MediaPlayer().apply {
                try {
                    setDataSource(fileName)
                    prepare()
                    start()
                } catch (e: IOException) {
                    Log.e(LOG_TAG, "prepare() failed")
                }
            }
        }

        private fun stopPlaying() {
            player?.release()
            player = null
        }

//        @RequiresApi(Build.VERSION_CODES.O)
        private fun startRecording() {
//            val intent = Intent() // Build the intent for the service
//            applicationContext.startForegroundService(intent)

            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setOutputFile(fileName)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

                try {
                    prepare()
                } catch (e: IOException) {
                    Log.e(LOG_TAG, "prepare() failed")
                }

                start()
            }
        }

        private fun stopRecording() {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
        }

        internal inner class RecordButton(ctx: Context) : Button(ctx) {

            var mStartRecording = true

            var clicker: OnClickListener = OnClickListener {
                onRecord(mStartRecording)
                text = when (mStartRecording) {
                    true -> "Stop recording"
                    false -> "Start recording"
                }
                mStartRecording = !mStartRecording
            }

            init {
                text = "Start recording"
                setOnClickListener(clicker)
            }
        }

        internal inner class PlayButton(ctx: Context) : Button(ctx) {
            var mStartPlaying = true
            var clicker: OnClickListener = OnClickListener {
                onPlay(mStartPlaying)
                text = when (mStartPlaying) {
                    true -> "Stop playing"
                    false -> "Start playing"
                }
                mStartPlaying = !mStartPlaying
            }

            init {
                text = "Start playing"
                setOnClickListener(clicker)
            }
        }

        override fun onCreate(icicle: Bundle?) {
            super.onCreate(icicle)

            wakeLock =
                (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                    newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::AudioRecordTest").apply {
                        acquire()
                        println("ACQUIREEEE")
                    }
                }

            println("wakelock:  $wakeLock")

            // Record to the external cache directory for visibility
            fileName = "${externalCacheDir!!.absolutePath}/audiorecordtest.3gp"

            ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION)

            recordButton = RecordButton(this)
            playButton = PlayButton(this)
            val ll = LinearLayout(this).apply {
                addView(recordButton,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0f))
                addView(playButton,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0f))
            }
            setContentView(ll)
        }

        override fun onStop() {
            super.onStop()
            recorder?.release()
            recorder = null
            player?.release()
            player = null
        }
    }