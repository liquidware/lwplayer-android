/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.liquidware.lwplayer;

import java.io.*;
import android.app.Activity;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.os.Bundle;
import com.Ostermiller.util.CircularByteBuffer;
import com.liquidware.lwplayer.R;

public class Lwplayer extends Activity {
	private static final String TAG = "Lwplay";    

	TextView tv;
	EditText et1;
	Button playb; 
	MediaThread mediaThread;
	
	/** Called when the activity is first created. */ 
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.lwplay_activity);  

		/*
		 * Create a TextView and set its content. the text is retrieved by
		 * calling a native function.
		 */
		
		/* UI stuff */
		tv = (TextView) findViewById(R.id.textView1);
		tv.setMovementMethod(new ScrollingMovementMethod());
		et1 = (EditText) findViewById(R.id.editText1); 
		playb = ((Button) findViewById(R.id.button1)); 
		playb.setOnClickListener(mPlayListener);   
		tv.setText("");
		
		/* Create the media thread */
		mediaThread = new MediaThread();  
		mediaThread.addProgressListener(new MediaStatus() {
			
			public void onProgressUpdate(Integer... progress) {
				if (progress[0] == MediaStatus.STATUS_STOPPED) {
					playb.setText("Play");
				}
				tv.setText("Status:" + progress[0] + "," +
						"S:" + progress[1] + " KB/Sec," + 
						"D:" + progress[2] + " KB/Sec," +
						"A:" + progress[3] + " KB/Sec,");
			}
		});
		
		/* Redirect aac data to a file */
		mediaThread.setDemuxOutputFile(new File("/sdcard/demux-save.aac"));
		
		/* Read aac data from a file */
		//mediaThread.setAudioInputFile(new File("/sdcard/demux-save.aac"));
		mediaThread.execute();
	}

	/** 
	 * A call-back for when the user presses the back button.
	 */
	OnClickListener mPlayListener = new OnClickListener() {
		public void onClick(View v) {
			if (mediaThread.getPlayerStatus() == MediaStatus.STATUS_PLAYING) {
				mediaThread.stop();
			} else if (mediaThread.getPlayerStatus() == MediaStatus.STATUS_STOPPED){
				tv.setText("");
				playb.setText("Stop");
				
				/* Connect and get the stream. 
				 * Play also accepts a local file */
				mediaThread.play(et1.getText().toString());
			}  
		}
	};
}
