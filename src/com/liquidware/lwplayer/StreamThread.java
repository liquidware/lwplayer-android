package com.liquidware.lwplayer;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.Ostermiller.util.CircularByteBuffer;

import android.os.AsyncTask;
import android.util.Log;

public class StreamThread extends AsyncTask<Void, Integer, Void> {
	private static final String TAG = "StreamThread";
	private String urlStr;
	private OutputStream out;

	public StreamThread(String srcUrl, OutputStream out) {
		urlStr = srcUrl;
		this.out = out;
	}
	
	protected void onProgressUpdate(Integer... progress) {
		Log.d(TAG,"S:Http thread started id=" + Thread.currentThread().getId());
	}
	
	protected Void doInBackground(Void... params) {
		publishProgress(0);
		connect();
		return null;
	}

	public void connect() {
		byte bytes[] = new byte[1024];
		int inb =0;
		URL connectURL;
		String response;

		try {
			connectURL = new URL(urlStr);
			HttpURLConnection conn;
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
			response = getResponseOrig(conn);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private String getResponseOrig(HttpURLConnection conn)
	{
		DataInputStream is = null;

		try {
			is = new DataInputStream(conn.getInputStream());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return "error";
		} 

		// scoop up the reply from the server
		int ch; 
		int count = 0;
		int inBytes = 0;
		byte[] header = new byte[12];
		//StringBuffer sb = new StringBuffer(); 
		String sb = " ";

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

		while (inBytes != -1) {
			try {

				Log.i(TAG, "S:Scanning for packet");

				while (inBytes != -1) {
					if (is.available() > 12){
						is.readFully(header, 0,  1);
						if ( header[0] == 0x24) {
							is.readFully(header, 0,  1);
							if ( (header[0] == 0x44) || (header[0] == 0x48)) {
								is.readFully(header, 0,  10);
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
								break;
							}
						}
					}
					//Thread.sleep(1);
				}
				//Tricky
				chunk_length-=8;


				if (!asf_header_parsed) {
					Log.d(TAG, "S:Parsing asf header");
					asf_header_parsed = true;

					byte[] asf_header = new byte[chunk_length];
					is.readFully(asf_header, 0, chunk_length);
					/*
					if (inBytes != -1) {
						//const ff_asf_guid ff_asf_header = {
						//	    0x30, 0x26, 0xB2, 0x75, 0x8E, 0x66, 0xCF, 0x11, 0xA6, 0xD9, 0x00, 0xAA, 0x00, 0x62, 0xCE, 0x6C
						//	};
						byte[] ff_asf_header = {0x30, 0x26, (byte) 0xB2, 0x75, (byte) 0x8E, 0x66, (byte) 0xCF, 0x11, (byte) 0xA6, (byte) 0xD9, 0x00, (byte) 0xAA, 0x00, 0x62, (byte) 0xCE, 0x6C};
						byte[] temp = new byte[ff_asf_header.length];
						int p=0;

						System.arraycopy(asf_header, 0, temp, 0, ff_asf_header.length);

						//compare
						for(int x=0; x< ff_asf_header.length; x++) { if ( (int)temp[x] != (int)ff_asf_header[x]) temp = null; }

						if (temp != null) {
							Log.d(TAG, "asf_header matches guid");
							p+= ff_asf_header.length + 14;
							asf_packet_len =  ((int)asf_header[p + ff_asf_header.length * 2 + 64 + 3] << 24) + 
							((int)asf_header[p + ff_asf_header.length * 2 + 64 + 2] << 16) +
							((int)asf_header[p + ff_asf_header.length * 2 + 64 + 1] << 8) +
							((int)asf_header[p + ff_asf_header.length * 2 + 64 + 0]);
							Log.d(TAG, "asf_packet_len: " + asf_packet_len);
						} else {
							Log.e(TAG, "asf_header does not match guid");
						}
					}
					 */
				} else {
					//read and save the data chunks
					byte[] asf_data = new byte[chunk_length];
					Log.d(TAG,"S:Reading: " + chunk_length);
					is.readFully(asf_data, 0, chunk_length);
					out.write(asf_data);
					/*
					//read and save the data chunks
					byte[] asf_data = new byte[chunk_length];

					count = 0;
					while (count < chunk_length) {
						int max_bytes = (chunk_length - count);
						Log.d(TAG,"S:Reading max bytes from stream: " + max_bytes);
						is.readFully(asf_data, 0, max_bytes);
						Log.d(TAG,"S:Read: "+ inBytes);
						if (inBytes != -1) {
							//Write the stream data
							out.write(asf_data);
							count += inBytes;
							Log.d(TAG,"Bytes written to from stream: " + inBytes); 
						}
					}
					*/
				}
			} catch(Exception e) {
				e.printStackTrace();
				break;
			}
		}


		//while( (( ch = is.readFully() ) != -1) && (count < 1000) ) { 
		//    sb.append( (char)ch );
		//    count++;
		//} 
		Log.e(TAG,sb.toString());
		String header_str = "\nHeader:";
		String field = " ";
		int x = 0;

		while (field != null) {
			header_str = header_str + "\n" + field;
			field = conn.getHeaderField(x);
			x++;
		}

		return (header_str + "\nBody:\n" + sb.toString()); 
	}
	/*
	    catch(Exception e)
	    {
	       Log.e(TAG, "biffed it getting HTTPResponse");
	       e.printStackTrace();
	    }
	    finally 
	    {
	        try {
	        if (is != null)
	            is.close();
	        } catch (Exception e) {}
	    }
	 */
	//    return "";
	//}


}
