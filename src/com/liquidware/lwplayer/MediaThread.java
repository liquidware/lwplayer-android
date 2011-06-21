/*
** LWPlayer - Streaming Media Player for Android
** Copyright (C) 2011 Liquidware http://www.liquidware.com
**  
** This program is free software; you can redistribute it and/or modify
** it under the terms of the GNU General Public License as published by
** the Free Software Foundation; either version 3 of the License, or
** (at your option) any later version.
** 
** This program is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
** GNU General Public License for more details.
** 
** You should have received a copy of the GNU General Public License
** along with this program. If not, see <http://www.gnu.org/licenses/>.
**/
package com.liquidware.lwplayer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import android.os.AsyncTask;
import android.util.Log;

import com.Ostermiller.util.CircularByteBuffer;
import com.liquidware.lwplayer.MediaStatus;

public class MediaThread extends AsyncTask<Void, Integer, Void> {
	private static final String TAG = "Lwplay.MediaPlayer"; 
	private MediaStatus status;
	private int PlayerStatus = MediaStatus.STATUS_STOPPED;
	boolean ThreadInterrupted = false;
	private File demuxOutputFile;
	private File streamOutputFile;
	private File audioInputFile;
	
	CircularByteBuffer cbb1;
	CircularByteBuffer cbb2;

	InputStream in1;
	OutputStream out1;
	InputStream in2;
	OutputStream out2;
	
	StreamThread streamThread; 
	DemuxThread demuxThread;
	AudioThread audioThread;
	
	String url;
	
	int noStreamData;
	
	public MediaThread() {
		demuxOutputFile = null;
		streamOutputFile = null;
		audioInputFile = null;
		noStreamData = 0;
	}
	
	public void addProgressListener(MediaStatus ms) {
		status = ms;
	}
	
	public void setUrl(String url) {
		this.url = url;
	}
	
	public InputStream getAsfInputStream() {
		return in2;
		
	}
	
	/**
	 * 
	 * @param file Optionally redirect the demuxed data (in aac format) to this file.
	 */
	public void setDemuxOutputFile(File file) {
		demuxOutputFile = file;
	}
	
	/**
	 * 
	 * @param file Optionally redirect the streamed data to this file.
	 */
	public void setStreamOutputFile(File file) {
		demuxOutputFile = file;
	}
	
	/**
	 * 
	 * @param file Optionally read this file into the audio decoder.
	 */
	public void setAudioInputFile(File file) {
		audioInputFile = file;
	}
	
	/**
	 * Plays a URL
	 * @param url
	 */
	public void play(String url) {
		this.url = url;
		audioInputFile = null;
		PlayerStatus = MediaStatus.STATUS_PLAY;
	}
	
	/**
	 * Plays a file
	 * @param file 
	 */
	public void play(File file) {
		url = null;
		audioInputFile = file;
		PlayerStatus = MediaStatus.STATUS_PLAY;
	}
	
	public void stop() {
		PlayerStatus = MediaStatus.STATUS_STOP;
		//
	}
	
	public int getPlayerStatus() {
		return PlayerStatus;
	}
	
	/**
	 * Callback to update the UI with progress
	 */
	protected void onProgressUpdate(Integer... progress) { 
		if (status != null) {
			status.onProgressUpdate(progress);
		}
	}

	@Override
	protected Void doInBackground(Void... params) {
		Log.d(TAG,"M:Thread started id=" + Thread.currentThread().getId());
		
		while(!ThreadInterrupted)
		{
			try {
			if (PlayerStatus == MediaStatus.STATUS_PLAY) {
				Log.i(TAG, "M:Status: Requested Play");
				/* Initialize */
				cbb1 = new CircularByteBuffer(50000, true);
				cbb2 = new CircularByteBuffer(50000, true);

				in1 = cbb1.getInputStream();
				out1 = cbb1.getOutputStream();
				in2 = cbb2.getInputStream();
				out2 = cbb2.getOutputStream(); 
				
				try {
					/* Save to file? */
					if (demuxOutputFile != null) {
						out2 = new FileOutputStream(demuxOutputFile);
					}
					
					/* Save to file? */
					if (streamOutputFile != null) {
						out1 = new FileOutputStream(streamOutputFile);
					}
					
					/* Read from file? */
					if (audioInputFile != null) {
						in2 = new FileInputStream(audioInputFile);
					}
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					break;
				}
				
				if (url != null) {
					Log.i(TAG, "M:Creating new stream thread");
					streamThread = new StreamThread(url, out1);
				}
				Log.i(TAG, "M:Creating new demux thread");
				demuxThread = new DemuxThread(in1, out2);
				//audioThread = new AudioThread(in2);
				
				Log.i(TAG, "M:trying execute stream thread");
				streamThread.execute();
				demuxThread.execute();
				//audioThread.execute();
				
				PlayerStatus = MediaStatus.STATUS_PLAYING; 
			} else if (PlayerStatus == MediaStatus.STATUS_PLAYING) {
				if ((streamThread.getAverageData() <= 0) && (demuxThread.getAverageData() <= 0)) {
					Log.d(TAG,"No data, thinking about killing media threads.");
					noStreamData+=100;
					if (noStreamData > 6000) {
						PlayerStatus = MediaStatus.STATUS_STOP;
					}
				}
			} else if (PlayerStatus == MediaStatus.STATUS_RESYNC) {
				Log.i(TAG, "M:Status: Can't resync");
				PlayerStatus = MediaStatus.STATUS_ERROR;
			} else if (PlayerStatus == MediaStatus.STATUS_ERROR) {
				Log.i(TAG, "M:Status: Error stop");
				PlayerStatus = MediaStatus.STATUS_STOP;
			} else if (PlayerStatus == MediaStatus.STATUS_STOP) {
				Log.i(TAG, "M:Status: Requested Stop");
				
				if (demuxThread != null) {
					demuxThread.stop();
					//demuxThread = null;
				}
				if (streamThread != null) {
					streamThread.stop();
					//streamThread = null;
				}

				//audioThread.stop();
				try { Thread.sleep(5000); } catch(Exception e) { }
				
				audioThread = null;
				PlayerStatus = MediaStatus.STATUS_STOPPED;
				ThreadInterrupted = true;
			} else if (PlayerStatus == MediaStatus.STATUS_STOPPED) {
				;
			}
			
			/* Sleep */
			try { 
				Thread.sleep(100); Thread.yield(); 
			} catch(Exception ex) { }		
			
			publishThreadProgress(PlayerStatus);
			} catch (Exception e) {
				e.printStackTrace();
				Log.i(TAG, "M:Exception in media thread, stopping");
				streamThread = null;
				demuxThread = null;
				audioThread = null;
				PlayerStatus = MediaStatus.STATUS_STOPPED;
			}
		}
		
		Log.d(TAG, "Thread closing.");
		return null;
	}
	
	/**
	 * Used to retrieve worker thread data rates and publish rates to listeners
	 * @param pStatus The status of this thread
	 */
	private void publishThreadProgress(int pStatus){
		int s = 0;
		int d = 0;
		int a = 0;
		
		if (streamThread != null) {
			s = streamThread.getAverageData();
		}
		if (demuxThread != null) {
			d = demuxThread.getAverageData();
		}
		if (audioThread != null) {
			a = audioThread.getAverageData();
		}
		
		/* Publish the status and data rates of all threads */
		publishProgress(pStatus,
						s,
						d,
						a);
		
	}
	
	/*
	 * Just in case the thread was force closed, notify the callback
	 */
    protected void onPostExecute(Long result) {
		if (status != null) {
			status.onProgressUpdate(MediaStatus.STATUS_STOPPED);
			status = null;
		}
    }
}
