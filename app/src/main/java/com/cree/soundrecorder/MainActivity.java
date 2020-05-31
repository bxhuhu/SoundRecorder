package com.cree.soundrecorder;

import android.os.Bundle;
import android.widget.TextView;

import com.cree.soundrecorder.sound.RecorderController;
import com.nanyuweiyi.voiceline.VoiceLineView;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    RecorderController mRecorderController;
    private VoiceLineView mVoiceLineView;
    private TextView mTvDuration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRecorderController = new RecorderController(this);
        mRecorderController.requestPer();

        int[] ids = {R.id.bStartRecorder
                , R.id.bStopRecorder
                , R.id.bPauseRecorder
                , R.id.bInsertContent
                , R.id.bGetFileTime};
        for (int id : ids) {
            findViewById(id).setOnClickListener(mRecorderController);
        }
        mVoiceLineView = findViewById(R.id.vlvVoiceLine);
        mTvDuration = findViewById(R.id.tvDuration);
    }

    public void setVoiceLineView(double voice) {
        int newVoice = (int) (voice * 100);
        mVoiceLineView.setVolume(newVoice);
    }

    public void setDuration(String duration) {
        mTvDuration.setText(duration);
    }
}