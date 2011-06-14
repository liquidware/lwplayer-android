package com.liquidware.lwplayer;

import android.util.Log;

public class MovingAverage {
	private long dataRate;
	private long dataPoints;
	private long dataSum;
	private long lastTime;
	private String TAG;
	
	MovingAverage(String tag) {
		dataRate = 0;
		dataPoints = 0;
		dataSum = 0;
		lastTime = 0;
		this.TAG = tag; 
	}
	
	public void update(int val) {
		long now = System.currentTimeMillis();
		
		if (val > 0) {
			dataSum+=val;
			dataPoints++;
		}
		
		if ((now - lastTime) > 1000) {
			/* In kBytes */
			dataRate = (dataSum / 1000);
			Log.d(TAG, TAG + dataRate + " KBytes/Sec");
			dataPoints = 0;
			dataSum = 0;
			lastTime = now;
		}
	}
	
	public int getAverage() {
		return (int)dataRate;
	}
}
