package com.liquidware.lwplayer;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.os.AsyncTask;
import android.util.Log;

public class StreamThread extends AsyncTask<Void, Integer, Void> {
	private static final String TAG = "StreamThread";
	private String urlStr;
	private OutputStream out;
	private boolean ThreadInterrupted;
	private HttpURLConnection conn;
	private MovingAverage avg;

	public StreamThread(String srcUrl, OutputStream out) {
		urlStr = srcUrl;
		this.out = out;
		ThreadInterrupted = false;
		avg = new MovingAverage("S:");
	}

	public void stop() {
		ThreadInterrupted = true;
		//conn.disconnect();
	}

	protected void onProgressUpdate(Integer... progress) {
		avg.update(progress[0]);
	}

	public int getAverageData() {
		return avg.getAverage();
	}

	protected Void doInBackground(Void... params) {
		Log.d(TAG,"S:Thread started id=" + Thread.currentThread().getId());
		connect();
		return null;
	}

	private void connect() {
		URL connectURL;

		try {
			connectURL = new URL(urlStr);
			conn = (HttpURLConnection)connectURL.openConnection();

			// do some setup
			conn.setDoInput(true); 
			conn.setDoOutput(true); 
			conn.setReadTimeout(10000);
			conn.setConnectTimeout(15000);
			conn.setRequestMethod("GET");
			conn.setUseCaches(false);
			conn.addRequestProperty("Accept", "*/*");
			conn.addRequestProperty("User-Agent", "NSPlayer/4.1.0.3856");
			conn.addRequestProperty("Host", "192.168.1.200:8080");
			conn.addRequestProperty("Pragma", "stream-offset=0:0,request-context=1,max-duration=0");
			conn.addRequestProperty("Pragma", "xClientGUID={c77e7400-738a-11d2-9add-0020af0a3278}");
			conn.addRequestProperty("Connection", "Close");

			conn.connect();

			//conn.getOutputStream().flush();
			// now fetch the results
			getResponse(conn);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void getResponse(HttpURLConnection conn)
	{
		DataInputStream in = null;

		try {
			in = new DataInputStream(conn.getInputStream());
		} catch (IOException e1) {
			e1.printStackTrace();
			return;
		} 

		// scoop up the reply from the server
		byte[] header = new byte[12];

		/*

			Headers are followed by actual content, separated into chunks. 
			However, these chunks are different from the ones described in previous sections.
			Field	Type	Size (bytes)
			Basic chunk type	UINT16	2
			Chunk length	UINT16	2
			Sequence number	UINT32	4
			Unknown	-	2
			Chunk length confirmation	UINT16	2
			Body data	-	Variable

			Chunk length corresponds to data that starts from sequence number field.
			Basic chunk type can be 0x4424 ( Data follows ), 0x4524 ( Transfer complete ) and 0x4824 ( ASF header chunk follows ).
			For type 0x4824 'body data' should be parsed according to the same rules as a local ASF file. It is arranged so that ASF recorder program would not need to leave any 'holes' in file while recording - this chunk includes all ASF content up to the beginning of first packet with compressed media.
			For type 0x4424 'body data' contains a complete packet ( for example, first byte of this data is usually 0x82 ). Network transmission may send chunks that are shorter than pktsize from ASF file header, by chopping off padding section.
			Some fields in ASF file header may be empty, especially for the live stream.
		 */
		int chunk_type=0;
		int chunk_length=0;
		int sequence_number = 0;
		int unknown;
		int chunk_length_confirm=0;
		boolean asf_header_parsed = false;
		int asf_packet_len = 0;

		while (!ThreadInterrupted) {
			try {
				
				in.readFully(header, 0,  1);
				if ( header[0] == 0x24) {
					in.readFully(header, 0,  1);
					if ( (header[0] == 0x44) || (header[0] == 0x48)) {
						in.readFully(header, 0,  10);
						//guess chunk_size
						chunk_type = 0;

						chunk_length =    ((header[1] & 0xFF) << 8) + 
						(header[0] & 0xFF);
						sequence_number = ((header[5] & 0xFF) << 24) + 
						((header[4] & 0xFF) << 16) +
						((header[3] & 0xFF) << 8) +
						(header[2] & 0xFF);
						unknown = ((header[7] & 0xFF) << 8) + 
						(header[6] & 0xFF);
						chunk_length_confirm = ((header[9] & 0xFF) << 8) + 
						(header[8] & 0xFF);

						if (chunk_length != chunk_length_confirm) { 
							Log.e(TAG, "S:Error, discarding packet");
							continue; 
						}

						//Log.d(TAG, "S:found next header");
						//Log.d(TAG, String.format("S:chunk_type: %x",chunk_type));
						//Log.d(TAG, String.format("S:chunk_length: %d",chunk_length));
						Log.d(TAG, String.format("S:sequence_number: %d",sequence_number));
						//Log.d(TAG, String.format("S:chunk_length_confirm: %d",chunk_length_confirm));
						
						//Tricky
						chunk_length-=8;
					} else {
						Log.e(TAG, "S:Error, discarding packet");
						continue;
					}
				} else {
					Log.e(TAG, "S:Error, discarding packet");
					continue;
				}

				
				byte[] asf_data = new byte[chunk_length];
				Log.d(TAG,"S:Reading: " + chunk_length);
				in.readFully(asf_data, 0, chunk_length);
				publishProgress(chunk_length);

				if (!asf_header_parsed) {
					Log.d(TAG, "S:Parsing asf header");
					asf_header_parsed = true;
				} else { 
					//write the data
					out.write(asf_data);
				}
			} catch(Exception e) {
				e.printStackTrace();
				break;
			}
		}
		
		publishProgress(0);
		Log.d(TAG, "S:Closing input stream");
		//try { in.close(); } catch (IOException e) { }
		try { conn.disconnect(); } catch (Exception e) { }
	}
}
