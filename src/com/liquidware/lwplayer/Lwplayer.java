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
	private int playerBytesTotal = 0;    

	CircularByteBuffer cbb1;
	CircularByteBuffer cbb2;

	InputStream in1;
	OutputStream out1;
	InputStream in2;
	OutputStream out2;
	
	StreamThread streamThread; 
	DemuxThread demuxThread;
	MediaThread mediaThread = new MediaThread(in2);
	
	/** Called when the activity is first created. */ 
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.lwplay_activity);  

		/*
		 * Create a TextView and set its content. the text is retrieved by
		 * calling a native function.
		 */
		tv = (TextView) findViewById(R.id.textView1);
		tv.setMovementMethod(new ScrollingMovementMethod());
		et1 = (EditText) findViewById(R.id.editText1); 
		playb = ((Button) findViewById(R.id.button1));
		playb.setOnClickListener(mPlayListener);
		tv.setText("");
	}

	/** 
	 * A call-back for when the user presses the back button.
	 */
	OnClickListener mPlayListener = new OnClickListener() {
		public void onClick(View v) {
			if (mediaThread.getPlayerStatus() == MediaStatus.STATUS_PLAYING) {
				mediaThread.stop();
				demuxThread.cancel(true);
				streamThread.cancel(true);
			} else if (mediaThread.getPlayerStatus() == MediaStatus.STATUS_STOPPED){
				/* Connect and get the stream */

					cbb1 = new CircularByteBuffer(50000, true);
					cbb2 = new CircularByteBuffer(50000, true);

					in1 = cbb1.getInputStream();
					out1 = cbb1.getOutputStream();
					in2 = cbb2.getInputStream();
					out2 = cbb2.getOutputStream(); 
				
					String url = et1.getText().toString();
					streamThread = new StreamThread(url, out1);
					streamThread.execute();
					demuxThread = new DemuxThread(in1, out2);
					demuxThread.execute();
					mediaThread = new MediaThread(in2);
					mediaThread.addProgressListener(new MediaStatus() {

						@Override
						public void onProgressUpdate(Integer... progress) {
							if (progress[0] == MediaStatus.PROGRESS_STARTED) {
								//reset
								tv.setText("");
								playb.setText("Stop");
								playerBytesTotal = 0;
								return;
							} else if (progress[0] == MediaStatus.PROGRESS_STOPPED) {
								tv.setText(tv.getText() + " \nStopped.");
								playb.setText("Play");
							} else {
								playerBytesTotal += progress[0];
								tv.setText(tv.getText() + "\nwrote: " + progress[0] + " bytes, " + playerBytesTotal + " total");
							}
						}
					});
					mediaThread.execute();
			}  
		}
	};
}
