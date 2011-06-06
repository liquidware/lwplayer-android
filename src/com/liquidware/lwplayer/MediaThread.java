package com.liquidware.lwplayer;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.util.Log;
import com.liquidware.lwplayer.MediaStatus;

public class MediaThread extends AsyncTask<Void, Integer, Void> {
	private static final String TAG = "Lwplay.MediaPlayer"; 
	
	private DataInputStream in;
	private MediaStatus status;
	private int PlayerStatus = MediaStatus.STATUS_STOPPED;
	static boolean ThreadInterrupted = false;
	
	private final int duration = 1; // seconds
	private final int sampleRate = 44100;
	private final int numBytesPerSample = 2;
	private final int numChans = 2; 
	private final int numSamples = numBytesPerSample * numChans * sampleRate * duration;
	
	AudioTrack audioTrack;
	
	public MediaThread(InputStream in) {
		this.in = new DataInputStream(in);
		avInit(); //init the codecs
		avOpen(); //open the codec
		audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
				sampleRate, AudioFormat.CHANNEL_CONFIGURATION_STEREO,
				AudioFormat.ENCODING_PCM_16BIT, numSamples, AudioTrack.MODE_STREAM);
	}
	
	public void addProgressListener(MediaStatus ms) {
		status = ms;
	}
	
	public void play() {
		this.execute(); 
	}

	public void stop() {
		try {
			in.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
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
		// TODO Auto-generated method stub

		int bytesinDecoded = 0;
		int bytesinPkt = 0;
		byte[] encodedBytes = new byte[avGetInBufSize()];
		byte[] rawDecodedBytes = new byte[avGetOutBufSize()];
		
		PlayerStatus = MediaStatus.STATUS_PLAY;
		
		while(!ThreadInterrupted)
		{
			if (PlayerStatus == MediaStatus.STATUS_PLAY) {
				Log.i(TAG, "M:Status: Requested Play");
				/* Initialize */
				bytesinPkt = 0;
				audioTrack.play();
				publishProgress(MediaStatus.PROGRESS_STARTED);
				PlayerStatus = MediaStatus.STATUS_PLAYING;
			} else if (PlayerStatus == MediaStatus.STATUS_PLAYING) {
				/* Read the aac file packets and attempt to refill the buffer */
				int filledPktCnt = (encodedBytes.length - bytesinPkt);

				Log.i(TAG, "M:Trying to read: " + filledPktCnt);
				try {
					in.readFully(encodedBytes, bytesinPkt, filledPktCnt);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					Log.e(TAG, "M:Error reading packet");
					PlayerStatus = MediaStatus.STATUS_ERROR;
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
				Log.d(TAG,"M:Got " + bytesinDecoded + " from decoder, " + bytesinPkt + " remainder in pkt");

				if ((bytesinDecoded < 0) || (bytesinPkt < 0)) {
					PlayerStatus = MediaStatus.STATUS_ERROR;
					continue;
				}
				audioTrack.write(rawDecodedBytes, 0, (int)bytesinDecoded);
				Log.d(TAG, "M:Wrote to AudioTrack");
				publishProgress((int)bytesinDecoded);
			} else if (PlayerStatus == MediaStatus.STATUS_RESYNC) {
				Log.i(TAG, "M:Status: Can't resync");
				PlayerStatus = MediaStatus.STATUS_ERROR;
			} else if (PlayerStatus == MediaStatus.STATUS_ERROR) {
				Log.i(TAG, "M:Status: Error stop");
				PlayerStatus = MediaStatus.STATUS_STOP;
			} else if (PlayerStatus == MediaStatus.STATUS_STOP) {
				Log.i(TAG, "M:Status: Requested Stop");
				audioTrack.stop();
				try { in.close(); } catch (IOException e) { }
				publishProgress(MediaStatus.PROGRESS_STOPPED);
				PlayerStatus = MediaStatus.STATUS_STOPPED;
			} else if (PlayerStatus == MediaStatus.STATUS_STOPPED) {
				break;
			}
		}
		return null;
	}
	
	/*
	 * Just in case the thread was force closed, notify the callback
	 */
    protected void onPostExecute(Long result) {
		if (status != null) {
			status.onProgressUpdate(MediaStatus.PROGRESS_STOPPED);
		}
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

	/*
	 * This is another native method declaration that is *not* implemented by
	 * 'hello-jni'. This is simply to show that you can declare as many native
	 * methods in your Java code as you want, their implementation is searched
	 * in the currently loaded native libraries only the first time you call
	 * them.
	 * 
	 * Trying to call this function will result in a
	 * java.lang.UnsatisfiedLinkError exception !
	 */
	public native String unimplementedStringFromJNI(); 

	/*
	 * this is used to load the 'hello-jni' library on application startup. The
	 * library has already been unpacked into
	 * /data/data/com.example.HelloJni/lib/libhello-jni.so at installation time
	 * by the package manager.
	 */
	static {
		System.loadLibrary("lw-player");                        
	}
}
