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
 *
 */
#include <string.h>
#include <jni.h>
#include <android/log.h>

#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavutil/mathematics.h>

#define LOG_TAG "lw-player"
#ifdef ANDROID
	# define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
	# define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
	# define QUOTEME_(x) #x
	# define QUOTEME(x) QUOTEME_(x)
	# define LOGI(...) printf("I/" LOG_TAG " (" __FILE__ ":" QUOTEME(__LINE__) "): " __VA_ARGS__)
	# define LOGE(...) printf("E/" LOG_TAG "(" ")" __VA_ARGS__)
#endif

//#define INBUF_SIZE 4096
#define AUDIO_INBUF_SIZE 20480
//#define AUDIO_REFILL_THRESH 4096
#define AUDIO_REFILL_THRESH 4096 +1024

char outmsg[100];

/*
 * Audio encoding example
 */
static void audio_encode_example(const char *filename)
{
    AVCodec *codec;
    AVCodecContext *c= NULL;
    int frame_size, i, j, out_size, outbuf_size;
    FILE *f;
    short *samples;
    float t, tincr;
    uint8_t *outbuf;

    sprintf(outmsg, "%s\n%s", outmsg, "Audio encoding");

    /* find the MP2 encoder */
    codec = avcodec_find_encoder(CODEC_ID_MP2);
    if (!codec) {
        sprintf(outmsg, "%s\n%s", outmsg, "codec not found");
        return;
    } else {
    	sprintf(outmsg, "%s\n%s", outmsg, "Codec found.");
    }

    c= avcodec_alloc_context();



    /* put sample parameters */
    c->bit_rate = 48000;
    c->sample_rate = 44100;
    c->channels = 2;

    /* open it */
    if (avcodec_open(c, codec) < 0) {
        sprintf(outmsg, "%s\n%s", outmsg, "could not open codec");
        return;
    } else {
    	sprintf(outmsg, "%s\n%s", outmsg, "Codec opened.");
	}


    /* the codec gives us the frame size, in samples */
    frame_size = c->frame_size;
    samples = malloc(frame_size * 2 * c->channels);
    outbuf_size = 10000;
    outbuf = malloc(outbuf_size);

    f = fopen(filename, "wb");
    if (!f) {
        sprintf(outmsg, "%s\n%s %s", outmsg, "could not open ", filename);
        return;
    } else {
    	sprintf(outmsg, "%s\n%s", outmsg, "File opened");
    }


    /* encode a single tone sound */
    t = 0;
    tincr = 2 * M_PI * 440.0 / c->sample_rate;
    for(i=0;i<200;i++) {
        for(j=0;j<frame_size;j++) {
            samples[2*j] = (int)(sin(t) * 10000);
            samples[2*j+1] = samples[2*j];
            t += tincr;
        }
        /* encode the samples */
        out_size = avcodec_encode_audio(c, outbuf, outbuf_size, samples);
        fwrite(outbuf, 1, out_size, f);
    }
    fclose(f);

    sprintf(outmsg, "%s\n%s", outmsg, "File Closed.");

    free(outbuf);
    free(samples);

    avcodec_close(c);
    av_free(c);
    sprintf(outmsg, "%s\n%s", outmsg, "Free complete.");
}

AVCodec *codec;
AVCodecContext *c= NULL;
int out_size, len;
int outbuf_size, inbuf_index;
FILE *f, *outfile;
uint8_t outbuf[AVCODEC_MAX_AUDIO_FRAME_SIZE];
uint8_t inbuf[AUDIO_INBUF_SIZE + FF_INPUT_BUFFER_PADDING_SIZE];
AVPacket avpkt;
int x;

/*
 * Audio decoding init
 */
static void audio_decode_init(void)
{
	c= NULL;

    av_init_packet(&avpkt);
    LOGI("audio_decode_init");

    /* find the audio decoder */
	codec = avcodec_find_decoder(CODEC_ID_AAC);
	if (!codec) {
		LOGE("Codec not found, err=%d", (int)codec);
		return;
    } else {
    	LOGI("Codec found.");
    }

    c= avcodec_alloc_context();

    /* open it */
    if (avcodec_open(c, codec) < 0) {
        LOGE("Could not open codec");
        return;
    }
}

/*
 * audio_decode_frame
 *
 * returns the number of decoded bytes, otherwise -1 in error
 *
 * avpkt.size is the amount of left over input data
 */
int audio_decode_frame() {
    /* decode until end of packet */
    avpkt.data = inbuf;
    avpkt.size = AUDIO_INBUF_SIZE;
    outbuf_size = 0;

    while (avpkt.size > 0) {
        out_size = AVCODEC_MAX_AUDIO_FRAME_SIZE;
        len = avcodec_decode_audio3(c, (short *)(outbuf+outbuf_size), &out_size, &avpkt);
        if (len < 0) {
            LOGE("Error while decoding. avpkt.size=%d", avpkt.size);
            return -1;
        }
        if (out_size > 0) {
            /* if a frame has been decoded, output it */
            //fwrite(outbuf, 1, out_size, outfile);
        	//LOGI("Frame decoded.");
        	outbuf_size+=out_size;
        } else {
        	LOGI("Frame not decoded");
        }
        //LOGI("avpkt.size=%d, len=%d", avpkt.size, len);
        avpkt.size -= len;
        avpkt.data += len;
        //LOGI("avpkt.size=%d, len=%d", avpkt.size, len);
        if (avpkt.size < 0) avpkt.size = 0;

        if (avpkt.size < AUDIO_REFILL_THRESH) {
            /* Refill the input buffer, to avoid trying to decode
             * incomplete frames. Instead of this, one could also use
             * a parser, or use a proper container format through
             * libavformat. */
        	//LOGI("moving avpkt to front, size=%d", avpkt.size);
            memmove(inbuf, avpkt.data, avpkt.size);
            /* go get more data */
            break;
#if 0
            avpkt.data = inbuf;
            len = fread(avpkt.data + avpkt.size, 1,
                        AUDIO_INBUF_SIZE - avpkt.size, f);
            if (len > 0)
                avpkt.size += len;
#endif
        }

    }
    return outbuf_size;
}

/**
 * Must be called first
 */
jstring
Java_com_liquidware_lwplayer_MediaThread_avInit( JNIEnv* env,
                                            jobject thiz )
{
	LOGI("About to avcodec_init\n");
	avcodec_init();

	LOGI("About to avcodec_register_all\n");
	avcodec_register_all();

	LOGI("About to av_register_all\n");
	av_register_all();

	return (*env)->NewStringUTF(env, avcodec_configuration());
}

/*
 * avOpen to prepare the codec
 */
jint
Java_com_liquidware_lwplayer_MediaThread_avOpen( JNIEnv* env,
                                            jobject thiz )
{
	LOGI("About to audio_decode_init\n");
	audio_decode_init();
}

/*
 * avClose is a placeholder
 */
jint
Java_com_liquidware_lwplayer_MediaThread_avClose(JNIEnv* env,
                                             jobject thiz)
{
	//fclose(outfile);
	//fclose(f);
	//free(outbuf);

	//avcodec_close(c);
	//av_free(c);
}

jint
Java_com_liquidware_lwplayer_MediaThread_avGetInBufSize(JNIEnv* env,
                                                  jobject thiz)
{
	jint size = AUDIO_INBUF_SIZE;
	return size;
}

jint
Java_com_liquidware_lwplayer_MediaThread_avGetOutBufSize(JNIEnv* env,
                                                  jobject thiz)
{
	return AVCODEC_MAX_AUDIO_FRAME_SIZE;
}

/* This is a trivial JNI example where we use a native method
 * to return a new VM String. See the corresponding Java source
 * file located at:
 *
 */
jstring
Java_com_liquidware_lwplayer_MediaThread_avDecode( JNIEnv* env,
                                                  jobject thiz,
                                                  jbyteArray encodedBytes, jbyteArray rawDecodedBytes)
{
	int len;
	char out[64];

	/* Get the encoded data */
	len = (*env)->GetArrayLength(env, encodedBytes);
	(*env)->GetByteArrayRegion(env, encodedBytes, 0, len, inbuf);
	//LOGI("Read %d bytes into jni\n", len);

	/* Do some work */
	len = audio_decode_frame();
	if (len < 0 ) {
		return (*env)->NewStringUTF(env, "-1,-1");
	}

	/* Store the output */
	(*env)->SetByteArrayRegion(env, rawDecodedBytes, 0, len, outbuf);
	//LOGI("Wrote %d decoded bytes from jni\n", len);

	(*env)->SetByteArrayRegion(env, encodedBytes, 0, avpkt.size, inbuf);
	//LOGI("Get %d more bytes\n", (AUDIO_INBUF_SIZE-avpkt.size) );

	sprintf(out, "%d,%d", len, avpkt.size);
	return (*env)->NewStringUTF(env, out);

	/*
	sprintf(outmsg, "%s\n%s", outmsg, "About to decode file");
	audio_decode_example("/sdcard/decoded.wav", "/sdcard/demuxed.aac");

	//sprintf(outmsg, "%s\n%s", outmsg, "About to encode file");
	//audio_encode_example("/sdcard/encoded.mp2");

    sprintf(outmsg, "%s\n%s", outmsg, "Done!\n");
    env->GetByteArrayRegion(array, 0, len, buffer);

    return (*env)->NewStringUTF(env, outmsg);
    */
}
