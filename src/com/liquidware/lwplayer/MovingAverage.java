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
