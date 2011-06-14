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
	
	
	public MediaThread() {
		demuxOutputFile = null;
		streamOutputFile = null;
		audioInputFile = null;
	}
	
	public void addProgressListener(MediaStatus ms) {
		status = ms;
	}
	
	public void setUrl(String url) {
		this.url = url;
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

		while(!ThreadInterrupted)
		{
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
					streamThread = new StreamThread(url, out1);
				}
				demuxThread = new DemuxThread(in1, out2);
				audioThread = new AudioThread(in2);
				
				streamThread.execute();
				demuxThread.execute();
				audioThread.execute();
				
				PlayerStatus = MediaStatus.STATUS_PLAYING; 
			} else if (PlayerStatus == MediaStatus.STATUS_PLAYING) {
				;
			} else if (PlayerStatus == MediaStatus.STATUS_RESYNC) {
				Log.i(TAG, "M:Status: Can't resync");
				PlayerStatus = MediaStatus.STATUS_ERROR;
			} else if (PlayerStatus == MediaStatus.STATUS_ERROR) {
				Log.i(TAG, "M:Status: Error stop");
				PlayerStatus = MediaStatus.STATUS_STOP;
			} else if (PlayerStatus == MediaStatus.STATUS_STOP) {
				Log.i(TAG, "M:Status: Requested Stop");
				streamThread.stop();
				demuxThread.stop();
				audioThread.stop();
				try { Thread.sleep(250); } catch(Exception e) { }
				streamThread = null;
				demuxThread = null;
				audioThread = null;
				PlayerStatus = MediaStatus.STATUS_STOPPED;
			} else if (PlayerStatus == MediaStatus.STATUS_STOPPED) {
				;
			}
			
			try {
				Thread.sleep(100);
				Thread.yield();
			} catch(Exception ex) {
			}		
			
			publishThreadProgress(PlayerStatus);
		}
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
		}
    }
}
