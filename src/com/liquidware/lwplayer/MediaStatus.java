package com.liquidware.lwplayer;

public interface MediaStatus {
	static final int STATUS_STOPPED = 0; 
	static final int STATUS_PLAY = 1;
	static final int STATUS_PLAYING = 2;
	static final int STATUS_RESYNC = 3;
	static final int STATUS_STOP = 4;
	static final int STATUS_ERROR = 5;

	static final int PROGRESS_STARTED = 0;
	static final int PROGRESS_STOPPED = -1;
	
	void onProgressUpdate(Integer... progress);
}
