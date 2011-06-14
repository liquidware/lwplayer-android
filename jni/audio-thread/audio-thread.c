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
#include <libavcodec/get_bits.h>
#include <libavcodec/aac.h>
#include <libavformat/avformat.h>
#include <libavutil/mathematics.h>
#include <libavutil/internal.h>

#define LOG_TAG "audio-thread"
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
    LOGI("A:audio_decode_init");

    /* find the audio decoder */
	codec = avcodec_find_decoder(CODEC_ID_AAC);
	if (!codec) {
		LOGE("A:Codec not found, err=%d", (int)codec);
		return;
    } else {
    	LOGI("A:Codec found.");
    }

    c= avcodec_alloc_context();

    /* open it */
    if (avcodec_open(c, codec) < 0) {
        LOGE("A:Could not open codec");
        return;
    }
}

int attribute_align_arg avcodec_decode_audio3_chris(AVCodecContext *avctx, int16_t *samples,
                         int *frame_size_ptr,
                         AVPacket *avpkt)
{
    int ret;

    avctx->pkt = avpkt;

    LOGI("ffmpeg: avcodec_decode_audio3_chris");

    if((avctx->codec->capabilities & CODEC_CAP_DELAY) || avpkt->size){
        //FIXME remove the check below _after_ ensuring that all audio check that the available space is enough
        if(*frame_size_ptr < AVCODEC_MAX_AUDIO_FRAME_SIZE){
            av_log(avctx, AV_LOG_ERROR, "buffer smaller than AVCODEC_MAX_AUDIO_FRAME_SIZE\n");
            return -1;
        }
        if(*frame_size_ptr < FF_MIN_BUFFER_SIZE ||
        *frame_size_ptr < avctx->channels * avctx->frame_size * sizeof(int16_t)){
            av_log(avctx, AV_LOG_ERROR, "buffer %d too small\n", *frame_size_ptr);
            return -1;
        }
        LOGI("ffmpeg: about to avctx->codec->decode");
        ret = avctx->codec->decode(avctx, samples, frame_size_ptr, avpkt);
        //ret = latm_decode_frame(avctx, samples, frame_size_ptr, avpkt);
        avctx->frame_number++;
    }else{
        ret= 0;
        *frame_size_ptr=0;
    }
    return ret;
}

/*
 * audio_decode_frame
 *
 * returns the number of decoded bytes, otherwise -1 in error
 *
 * avpkt.size is the amount of left over input data
 */
int audio_decode_frame() {
	//int err_cnt = 0;
    /* decode until end of packet */
    avpkt.data = inbuf;
    avpkt.size = AUDIO_INBUF_SIZE;
    outbuf_size = 0;


    while (avpkt.size > 0) {
        out_size = AVCODEC_MAX_AUDIO_FRAME_SIZE;
        len = avcodec_decode_audio3_chris(c, (short *)(outbuf+outbuf_size), &out_size, &avpkt);
        if (len < 0) {
            LOGE("A:Error while decoding. len=%d, avpkt.size=%d. Realigning", len, avpkt.size);
            return -1;
        }
        if (out_size > 0) {
            /* if a frame has been decoded, output it */
            //fwrite(outbuf, 1, out_size, outfile);
        	//LOGI("A:Frame decoded.");
        	outbuf_size+=out_size;
        } else {
        	LOGI("A:Frame not decoded");
        }
        //LOGI("A:avpkt.size=%d, len=%d", avpkt.size, len);
        avpkt.size -= len;
        avpkt.data += len;
        //LOGI("A:avpkt.size=%d, len=%d", avpkt.size, len);
        if (avpkt.size < 0) avpkt.size = 0;

        if (avpkt.size < AUDIO_REFILL_THRESH) {
            /* Refill the input buffer, to avoid trying to decode
             * incomplete frames. Instead of this, one could also use
             * a parser, or use a proper container format through
             * libavformat. */
        	//LOGI("A:moving avpkt to front, size=%d", avpkt.size);
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
Java_com_liquidware_lwplayer_AudioThread_avInit( JNIEnv* env,
                                            jobject thiz )
{
	LOGI("A:About to avcodec_init\n");
	//avcodec_init();

	LOGI("A:About to avcodec_register_all\n");
	//avcodec_register_all();

	LOGI("A:About to av_register_all\n");
	//av_register_all();

	return (*env)->NewStringUTF(env, avcodec_configuration());
}

/*
 * avOpen to prepare the codec
 */
jint
Java_com_liquidware_lwplayer_AudioThread_avOpen( JNIEnv* env,
                                            jobject thiz )
{
	LOGI("A:About to audio_decode_init\n");
	audio_decode_init();
}

/*
 * avClose is a placeholder
 */
jint
Java_com_liquidware_lwplayer_AudioThread_avClose(JNIEnv* env,
                                             jobject thiz)
{
	//fclose(outfile);
	//fclose(f);
	//free(outbuf);

	//avcodec_close(c);
	//av_free(c);
}

jint
Java_com_liquidware_lwplayer_AudioThread_avGetInBufSize(JNIEnv* env,
                                                  jobject thiz)
{
	jint size = AUDIO_INBUF_SIZE;
	return size;
}

jint
Java_com_liquidware_lwplayer_AudioThread_avGetOutBufSize(JNIEnv* env,
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
Java_com_liquidware_lwplayer_AudioThread_avDecode( JNIEnv* env,
                                                  jobject thiz,
                                                  jbyteArray encodedBytes, jbyteArray rawDecodedBytes)
{
	int decodedLen;
	int fillLen;
	int maxFillLen;
	int maxDecodedLen;
	char out[1024];

	maxDecodedLen = (*env)->GetArrayLength(env, rawDecodedBytes);
	maxFillLen = (*env)->GetArrayLength(env, encodedBytes);

	/* Get the encoded data */
	(*env)->GetByteArrayRegion(env, encodedBytes, 0, maxFillLen, inbuf);

	/* Do some work */
	decodedLen = audio_decode_frame();
	fillLen = avpkt.size;

	/* Error check */
	if (decodedLen < 0 ) {
		LOGE("A:Error on decoded length");
		return (*env)->NewStringUTF(env, "-1,-1");
	} else if (decodedLen > maxDecodedLen) {
		decodedLen = maxDecodedLen;
	}

	/* Error check */
	if (fillLen < 0 ) {
		LOGE("A:Error on packet length");
		return (*env)->NewStringUTF(env, "-1,-1");
	} else if (fillLen > maxFillLen) {
		fillLen = maxFillLen;
	}

	LOGI("A:Setting decodedLen:%d max:%d, fillLen:%d max:%d", decodedLen, maxDecodedLen, fillLen, maxFillLen);

	/* Store the Decoded Bytes */
	(*env)->SetByteArrayRegion(env, rawDecodedBytes, 0, decodedLen, outbuf);
	(*env)->SetByteArrayRegion(env, encodedBytes, 0, fillLen, inbuf);

	snprintf(out, 1023, "%d,%d", decodedLen, fillLen);
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
