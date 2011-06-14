package com.liquidware.lwplayer;

import java.io.DataInputStream;
import java.io.InputStream;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.util.Log;

public class AudioThread extends AsyncTask<Void, Integer, Void>{
	private static final String TAG = "Lwplay.AudioThread";
	private boolean ThreadInterrupted;

	private final int duration = 1; // seconds
	private final int sampleRate = 44100;
	private final int numBytesPerSample = 2;
	private final int numChans = 2; 
	private final int numSamples = numBytesPerSample * numChans * sampleRate * duration;
	private MovingAverage avg;

	private AudioTrack audioTrack;
	private DataInputStream in;

	public AudioThread(InputStream in) {
		ThreadInterrupted = false;
		this.in = new DataInputStream(in);
		avInit(); //init the codecs
		avOpen(); //open the codec
		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
				sampleRate, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
				AudioFormat.ENCODING_PCM_16BIT, numSamples, AudioTrack.MODE_STREAM);
		avg = new MovingAverage("A:");
	}

	public void play() {
		this.execute();
	}

	public void stop() {
		ThreadInterrupted = true;
		try { in.close(); } catch(Exception ex) { }
	}
	
	public int getAverageData() {
		return avg.getAverage();
	}

	protected void onProgressUpdate(Integer... progress) { 
		avg.update(progress[0]);
	}

	protected Void doInBackground(Void... params) {
		int bytesinDecoded = 0;
		int bytesinPkt = 0;
		byte[] encodedBytes = new byte[avGetInBufSize()];
		byte[] rawDecodedBytes = new byte[avGetOutBufSize()];

		bytesinPkt = 0;
		audioTrack.play();

		while(!ThreadInterrupted) {
			/* Read the aac file packets and attempt to refill the buffer */
			int filledPktCnt = (encodedBytes.length - bytesinPkt);
			try {
				if (in.available() < filledPktCnt+1000) {
					Log.i(TAG,"A:Sleeping for data");
					publishProgress(0);
					Thread.sleep(100);
					Thread.yield();
					continue;
				}
				Log.i(TAG, "A:Trying to read: " + filledPktCnt);
				in.readFully(encodedBytes, bytesinPkt, filledPktCnt);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				Log.e(TAG, "A:Error reading packet");
				ThreadInterrupted = true;
				continue;
			} 

			/* Decode the aac packet 
			 * Result s[0] : num of decoded bytes of media
			 * Result s[1] : num bytes remaining in the original packet. Must fill packet.
			 * 
			 * */ 
			String s[] = avDecode(encodedBytes, rawDecodedBytes).split(",");
			bytesinDecoded = Integer.parseInt(s[0]);
			bytesinPkt = Integer.parseInt(s[1]);
			Log.d(TAG,"A:Got " + bytesinDecoded + " from decoder, " + bytesinPkt + " remainder in pkt");

			if ((bytesinDecoded < 0) || (bytesinPkt < 0)) { 
				//PlayerStatus = MediaStatus.STATUS_ERROR;
				//continue;
				bytesinDecoded = 0;
				bytesinPkt = 0;
			}
			audioTrack.write(rawDecodedBytes, 0, (int)bytesinDecoded);
			Log.d(TAG, "A:Wrote to AudioTrack");
			publishProgress((int)bytesinDecoded);
		} //end while
		
		Log.d(TAG, "A:Thread closing");
		audioTrack.stop();
		audioTrack.release();
		//try { in.close(); } catch (IOException e) { }
		return null;
	}
	
	/*
	 * A native method that is implemented by the 'lw-player' native library,
	 * which is packaged with this application.
	 */
	public native String avInit();
	public native String avDecode(byte[] encodedBytes, byte[] rawDecodedBytes);
	public native int avOpen();
	public native int avClose();

	public native int avGetInBufSize();  
	public native int avGetOutBufSize();
	
	static {
		System.loadLibrary("audio-thread");                        
	}
}
