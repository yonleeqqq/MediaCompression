package com.compression.library;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Environment;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;

@TargetApi(18)
public class VideoResolutionChanger {

    private static final int TIMEOUT_USEC = 10000;

    private static final String OUTPUT_VIDEO_MIME_TYPE = "video/avc";
    private int OUTPUT_VIDEO_BIT_RATE = 500 * 1024;
    private static final int OUTPUT_VIDEO_FRAME_RATE = 30;
    private static final int OUTPUT_VIDEO_IFRAME_INTERVAL = 10;
    private static final int OUTPUT_VIDEO_COLOR_FORMAT =
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;

    private static final String OUTPUT_AUDIO_MIME_TYPE = "audio/mp4a-latm";
    private static final int OUTPUT_AUDIO_BIT_RATE = 128 * 1024;
    private static final int OUTPUT_AUDIO_AAC_PROFILE =
            MediaCodecInfo.CodecProfileLevel.AACObjectHE;

    private int mWidth = 0;
    private int mHeight = 0;

    private int longEdge;
    private int longEdgeFromServer;
    private int inputFileLongEdge;
    private String mOutputFile, mInputFile;

    private String destLocation;

    private ChangerWrapper changerWrapper;
    private String destFileName;

    public VideoResolutionChanger(String packageName, String destFileName, int longEdge, int bitrateInKbs){
        this.longEdge = longEdge;
        this.longEdgeFromServer = longEdge;
        OUTPUT_VIDEO_BIT_RATE = bitrateInKbs * 1024;
        destLocation = Environment.getExternalStorageDirectory() + "/Android/data/" + packageName;
        this.destFileName = destFileName;
        changerWrapper = new ChangerWrapper(this);
    }

    public String changeResolution(File f) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {

                mInputFile = f.getAbsolutePath();
                String[] splitByDot = mInputFile.split("\\.");
                String ext = "";
                if (splitByDot != null && splitByDot.length > 1)
                    ext = splitByDot[splitByDot.length - 1];

                final File directory = new File(destLocation, ".compressed");
                if (!directory.exists())
                    directory.mkdirs();


                final File outFile = new File(directory.getPath(), destFileName);
                if (!outFile.exists())
                    outFile.createNewFile();

                mOutputFile = outFile.getAbsolutePath();
                changerWrapper.changeResolutionInSeparatedThread(this);
                return mOutputFile;
            }
        }
        catch(Throwable th){
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                try {
                    if (this.longEdge == this.longEdgeFromServer && inputFileLongEdge >= 3840) {
                        this.longEdge = 960;
                    }
                    changerWrapper.changeResolutionInSeparatedThread(this);
                    return mOutputFile;
                } catch (Throwable throwable) {

                }
            }
        }
        return f.getPath().toString();
    }

    public void prepareAndChangeResolution() throws Exception{

        Exception exception = null;

        MediaCodecInfo videoCodecInfo = selectCodec(OUTPUT_VIDEO_MIME_TYPE);
        if (videoCodecInfo == null)
            return ;
        MediaCodecInfo audioCodecInfo = selectCodec(OUTPUT_AUDIO_MIME_TYPE);
        if (audioCodecInfo == null)
            return ;

        MediaExtractor videoExtractor = null;
        MediaExtractor audioExtractor = null;
        OutputSurface outputSurface = null;
        MediaCodec videoDecoder = null;
        MediaCodec audioDecoder = null;
        MediaCodec videoEncoder = null;
        MediaCodec audioEncoder = null;
        MediaMuxer muxer = null;
        InputSurface inputSurface = null;

        int inputWidth = -1;
        int inputHeight = -1;

        try {
            videoExtractor = createExtractor();
            int videoInputTrack = getAndSelectVideoTrackIndex(videoExtractor);
            MediaFormat inputFormat = videoExtractor.getTrackFormat(videoInputTrack);

            MediaMetadataRetriever m = new MediaMetadataRetriever();
            m.setDataSource(mInputFile);
            Bitmap thumbnail = m.getFrameAtTime();
            if(thumbnail == null){
                thumbnail = m.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            }
            if(thumbnail != null) {
                inputWidth = thumbnail.getWidth();
                inputHeight = thumbnail.getHeight();
            }else{
                inputWidth = Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                inputHeight = Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));

                int rotation =  Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
                if(rotation == 90 || rotation == 270){
                    inputWidth = inputWidth + inputHeight;
                    inputHeight = inputWidth - inputHeight;
                    inputWidth = inputWidth - inputHeight;
                }
            }

            mWidth = inputWidth;
            mHeight = inputHeight;

            int inputBitrate = Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
            double ratio = inputWidth>inputHeight?inputWidth*1.0/inputHeight:inputHeight*1.0/inputWidth;

            if(inputWidth>longEdge || inputHeight>longEdge ||  inputBitrate > OUTPUT_VIDEO_BIT_RATE){

                if(inputWidth>longEdge || inputHeight>longEdge) {
                    if (inputWidth > inputHeight) {
                        mWidth = longEdge;
                        mHeight = Double.valueOf(mWidth / ratio).intValue();
                        this.inputFileLongEdge = inputWidth;
                    } else {
                        mHeight = longEdge;
                        mWidth = Double.valueOf(mHeight / ratio).intValue();
                        this.inputFileLongEdge = inputHeight;
                    }
                }

                if(inputBitrate<OUTPUT_VIDEO_BIT_RATE){
                    OUTPUT_VIDEO_BIT_RATE = inputBitrate;
                }

                MediaFormat outputVideoFormat =
                        MediaFormat.createVideoFormat(OUTPUT_VIDEO_MIME_TYPE, mWidth, mHeight);
                outputVideoFormat.setInteger(
                        MediaFormat.KEY_COLOR_FORMAT, OUTPUT_VIDEO_COLOR_FORMAT);
                outputVideoFormat.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_VIDEO_BIT_RATE);
                outputVideoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, OUTPUT_VIDEO_FRAME_RATE);
                outputVideoFormat.setInteger(
                        MediaFormat.KEY_I_FRAME_INTERVAL, OUTPUT_VIDEO_IFRAME_INTERVAL);

                AtomicReference<Surface> inputSurfaceReference = new AtomicReference<Surface>();
                videoEncoder = createVideoEncoder(
                        videoCodecInfo, outputVideoFormat, inputSurfaceReference);
                inputSurface = new InputSurface(inputSurfaceReference.get());
                inputSurface.makeCurrent();

                outputSurface = new OutputSurface();
                videoDecoder = createVideoDecoder(inputFormat, outputSurface.getSurface());

                audioExtractor = createExtractor();
                int audioInputTrack = getAndSelectAudioTrackIndex(audioExtractor);
                MediaFormat inputAudioFormat = audioExtractor.getTrackFormat(audioInputTrack);
                MediaFormat outputAudioFormat =
                        MediaFormat.createAudioFormat(inputAudioFormat.getString(MediaFormat.KEY_MIME),
                                inputAudioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                                inputAudioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                outputAudioFormat.setInteger(MediaFormat.KEY_BIT_RATE, OUTPUT_AUDIO_BIT_RATE);
                outputAudioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, OUTPUT_AUDIO_AAC_PROFILE);

                audioEncoder = createAudioEncoder(audioCodecInfo, outputAudioFormat);
                audioDecoder = createAudioDecoder(inputAudioFormat);

                muxer = new MediaMuxer(mOutputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

                changeResolution(videoExtractor, audioExtractor,
                        videoDecoder, videoEncoder,
                        audioDecoder, audioEncoder,
                        muxer, inputSurface, outputSurface);
            }else{
                mOutputFile = mInputFile;
            }
        } finally {
            try {
                if (videoExtractor != null)
                    videoExtractor.release();
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (audioExtractor != null)
                    audioExtractor.release();
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (videoDecoder != null) {
                    videoDecoder.stop();
                    videoDecoder.release();
                }
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (outputSurface != null) {
                    outputSurface.release();
                }
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (videoEncoder != null) {
                    videoEncoder.stop();
                    videoEncoder.release();
                }
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (audioDecoder != null) {
                    audioDecoder.stop();
                    audioDecoder.release();
                }
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (audioEncoder != null) {
                    audioEncoder.stop();
                    audioEncoder.release();
                }
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (muxer != null) {
                    muxer.stop();
                    muxer.release();
                }
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
            try {
                if (inputSurface != null)
                    inputSurface.release();
            } catch(Exception e) {
                if (exception == null)
                    exception = e;
            }
        }
        if (exception != null)
            throw exception;
    }

    private MediaExtractor createExtractor() throws IOException {
        MediaExtractor extractor;
        extractor = new MediaExtractor();
        extractor.setDataSource(mInputFile);
        return extractor;
    }

    private MediaCodec createVideoDecoder(MediaFormat inputFormat, Surface surface) throws IOException {
        MediaCodec decoder = MediaCodec.createDecoderByType(getMimeTypeFor(inputFormat));
        decoder.configure(inputFormat, surface, null, 0);
        decoder.start();
        return decoder;
    }

    private MediaCodec createVideoEncoder(MediaCodecInfo codecInfo, MediaFormat format,
                                          AtomicReference<Surface> surfaceReference) throws IOException {
        MediaCodec encoder = MediaCodec.createByCodecName(codecInfo.getName());
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        surfaceReference.set(encoder.createInputSurface());
        encoder.start();
        return encoder;
    }

    private MediaCodec createAudioDecoder(MediaFormat inputFormat) throws IOException {
        MediaCodec decoder = MediaCodec.createDecoderByType(getMimeTypeFor(inputFormat));
        decoder.configure(inputFormat, null, null, 0);
        decoder.start();
        return decoder;
    }

    private MediaCodec createAudioEncoder(MediaCodecInfo codecInfo, MediaFormat format) throws IOException {
        MediaCodec encoder = MediaCodec.createByCodecName(codecInfo.getName());
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        return encoder;
    }

    private int getAndSelectVideoTrackIndex(MediaExtractor extractor) {
        for (int index = 0; index < extractor.getTrackCount(); ++index) {
            if (isVideoFormat(extractor.getTrackFormat(index))) {
                extractor.selectTrack(index);
                return index;
            }
        }
        return -1;
    }
    private int getAndSelectAudioTrackIndex(MediaExtractor extractor) {
        for (int index = 0; index < extractor.getTrackCount(); ++index) {
            if (isAudioFormat(extractor.getTrackFormat(index))) {
                extractor.selectTrack(index);
                return index;
            }
        }
        return -1;
    }

    private void changeResolution(MediaExtractor videoExtractor, MediaExtractor audioExtractor,
                                  MediaCodec videoDecoder, MediaCodec videoEncoder,
                                  MediaCodec audioDecoder, MediaCodec audioEncoder,
                                  MediaMuxer muxer,
                                  InputSurface inputSurface, OutputSurface outputSurface) {
        ByteBuffer[] videoDecoderInputBuffers = null;
        ByteBuffer[] videoDecoderOutputBuffers = null;
        ByteBuffer[] videoEncoderOutputBuffers = null;
        MediaCodec.BufferInfo videoDecoderOutputBufferInfo = null;
        MediaCodec.BufferInfo videoEncoderOutputBufferInfo = null;

        videoDecoderInputBuffers = videoDecoder.getInputBuffers();
        videoDecoderOutputBuffers = videoDecoder.getOutputBuffers();
        videoEncoderOutputBuffers = videoEncoder.getOutputBuffers();
        videoDecoderOutputBufferInfo = new MediaCodec.BufferInfo();
        videoEncoderOutputBufferInfo = new MediaCodec.BufferInfo();

        ByteBuffer[] audioDecoderInputBuffers = null;
        ByteBuffer[] audioDecoderOutputBuffers = null;
        ByteBuffer[] audioEncoderInputBuffers = null;
        ByteBuffer[] audioEncoderOutputBuffers = null;
        MediaCodec.BufferInfo audioDecoderOutputBufferInfo = null;
        MediaCodec.BufferInfo audioEncoderOutputBufferInfo = null;

        audioDecoderInputBuffers = audioDecoder.getInputBuffers();
        audioDecoderOutputBuffers =  audioDecoder.getOutputBuffers();
        audioEncoderInputBuffers = audioEncoder.getInputBuffers();
        audioEncoderOutputBuffers = audioEncoder.getOutputBuffers();
        audioDecoderOutputBufferInfo = new MediaCodec.BufferInfo();
        audioEncoderOutputBufferInfo = new MediaCodec.BufferInfo();

        MediaFormat decoderOutputVideoFormat = null;
        MediaFormat decoderOutputAudioFormat = null;
        MediaFormat encoderOutputVideoFormat = null;
        MediaFormat encoderOutputAudioFormat = null;
        int outputVideoTrack = -1;
        int outputAudioTrack = -1;

        boolean videoExtractorDone = false;
        boolean videoDecoderDone = false;
        boolean videoEncoderDone = false;

        boolean audioExtractorDone = false;
        boolean audioDecoderDone = false;
        boolean audioEncoderDone = false;

        int pendingAudioDecoderOutputBufferIndex = -1;
        boolean muxing = false;
        while ((!videoEncoderDone) || (!audioEncoderDone)) {
            while (!videoExtractorDone
                    && (encoderOutputVideoFormat == null || muxing)) {
                int decoderInputBufferIndex = videoDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
                    break;

                ByteBuffer decoderInputBuffer = videoDecoderInputBuffers[decoderInputBufferIndex];
                int size = videoExtractor.readSampleData(decoderInputBuffer, 0);
                long presentationTime = videoExtractor.getSampleTime();

                if (size >= 0) {
                    videoDecoder.queueInputBuffer(
                            decoderInputBufferIndex,
                            0,
                            size,
                            presentationTime,
                            videoExtractor.getSampleFlags());
                }
                videoExtractorDone = !videoExtractor.advance();
                if (videoExtractorDone)
                    videoDecoder.queueInputBuffer(decoderInputBufferIndex,
                            0, 0, 0,  MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                break;
            }

            while (!audioExtractorDone
                    && (encoderOutputAudioFormat == null || muxing)) {
                int decoderInputBufferIndex = audioDecoder.dequeueInputBuffer(TIMEOUT_USEC);
                if (decoderInputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
                    break;

                ByteBuffer decoderInputBuffer = audioDecoderInputBuffers[decoderInputBufferIndex];
                int size = audioExtractor.readSampleData(decoderInputBuffer, 0);
                long presentationTime = audioExtractor.getSampleTime();

                if (size >= 0)
                    audioDecoder.queueInputBuffer(decoderInputBufferIndex, 0, size,
                            presentationTime, audioExtractor.getSampleFlags());

                audioExtractorDone = !audioExtractor.advance();
                if (audioExtractorDone)
                    audioDecoder.queueInputBuffer(decoderInputBufferIndex, 0, 0,
                            0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

                break;
            }

            while (!videoDecoderDone
                    && (encoderOutputVideoFormat == null || muxing)) {
                int decoderOutputBufferIndex =
                        videoDecoder.dequeueOutputBuffer(
                                videoDecoderOutputBufferInfo, TIMEOUT_USEC);
                if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
                    break;

                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    videoDecoderOutputBuffers = videoDecoder.getOutputBuffers();
                    break;
                }
                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    decoderOutputVideoFormat = videoDecoder.getOutputFormat();
                    break;
                }

                ByteBuffer decoderOutputBuffer =
                        videoDecoderOutputBuffers[decoderOutputBufferIndex];
                if ((videoDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                        != 0) {
                    videoDecoder.releaseOutputBuffer(decoderOutputBufferIndex, false);
                    break;
                }

                boolean render = videoDecoderOutputBufferInfo.size != 0;
                videoDecoder.releaseOutputBuffer(decoderOutputBufferIndex, render);
                if (render) {
                    outputSurface.awaitNewImage();
                    outputSurface.drawImage();
                    inputSurface.setPresentationTime(
                            videoDecoderOutputBufferInfo.presentationTimeUs * 1000);
                    inputSurface.swapBuffers();
                }
                if ((videoDecoderOutputBufferInfo.flags
                        & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    videoDecoderDone = true;
                    videoEncoder.signalEndOfInputStream();
                }
                break;
            }

            while (!audioDecoderDone && pendingAudioDecoderOutputBufferIndex == -1
                    && (encoderOutputAudioFormat == null || muxing)) {
                int decoderOutputBufferIndex =
                        audioDecoder.dequeueOutputBuffer(
                                audioDecoderOutputBufferInfo, TIMEOUT_USEC);
                if (decoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
                    break;

                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    audioDecoderOutputBuffers = audioDecoder.getOutputBuffers();
                    break;
                }
                if (decoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    decoderOutputAudioFormat = audioDecoder.getOutputFormat();
                    break;
                }
                ByteBuffer decoderOutputBuffer =
                        audioDecoderOutputBuffers[decoderOutputBufferIndex];
                if ((audioDecoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                        != 0) {
                    audioDecoder.releaseOutputBuffer(decoderOutputBufferIndex, false);
                    break;
                }
                pendingAudioDecoderOutputBufferIndex = decoderOutputBufferIndex;
                break;
            }

            while (pendingAudioDecoderOutputBufferIndex != -1) {
                int encoderInputBufferIndex = audioEncoder.dequeueInputBuffer(TIMEOUT_USEC);
                ByteBuffer encoderInputBuffer = audioEncoderInputBuffers[encoderInputBufferIndex];
                int size = audioDecoderOutputBufferInfo.size;
                long presentationTime = audioDecoderOutputBufferInfo.presentationTimeUs;

                if (size >= 0) {
                    ByteBuffer decoderOutputBuffer =
                            audioDecoderOutputBuffers[pendingAudioDecoderOutputBufferIndex]
                                    .duplicate();
                    decoderOutputBuffer.position(audioDecoderOutputBufferInfo.offset);
                    decoderOutputBuffer.limit(audioDecoderOutputBufferInfo.offset + size);
                    encoderInputBuffer.position(0);
                    encoderInputBuffer.put(decoderOutputBuffer);
                    audioEncoder.queueInputBuffer(
                            encoderInputBufferIndex,
                            0,
                            size,
                            presentationTime,
                            audioDecoderOutputBufferInfo.flags);
                }
                audioDecoder.releaseOutputBuffer(pendingAudioDecoderOutputBufferIndex, false);
                pendingAudioDecoderOutputBufferIndex = -1;
                if ((audioDecoderOutputBufferInfo.flags
                        & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                    audioDecoderDone = true;

                break;
            }

            while (!videoEncoderDone
                    && (encoderOutputVideoFormat == null || muxing)) {
                int encoderOutputBufferIndex = videoEncoder.dequeueOutputBuffer(
                        videoEncoderOutputBufferInfo, TIMEOUT_USEC);
                if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
                    break;
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    videoEncoderOutputBuffers = videoEncoder.getOutputBuffers();
                    break;
                }
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    encoderOutputVideoFormat = videoEncoder.getOutputFormat();
                    break;
                }

                ByteBuffer encoderOutputBuffer =
                        videoEncoderOutputBuffers[encoderOutputBufferIndex];
                if ((videoEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                        != 0) {
                    videoEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
                    break;
                }
                if (videoEncoderOutputBufferInfo.size != 0) {
                    muxer.writeSampleData(
                            outputVideoTrack, encoderOutputBuffer, videoEncoderOutputBufferInfo);
                }
                if ((videoEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        != 0) {
                    videoEncoderDone = true;
                }
                videoEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
                break;
            }

            while (!audioEncoderDone
                    && (encoderOutputAudioFormat == null || muxing)) {
                int encoderOutputBufferIndex = audioEncoder.dequeueOutputBuffer(
                        audioEncoderOutputBufferInfo, TIMEOUT_USEC);
                if (encoderOutputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    break;
                }
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    audioEncoderOutputBuffers = audioEncoder.getOutputBuffers();
                    break;
                }
                if (encoderOutputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    encoderOutputAudioFormat = audioEncoder.getOutputFormat();
                    break;
                }

                ByteBuffer encoderOutputBuffer =
                        audioEncoderOutputBuffers[encoderOutputBufferIndex];
                if ((audioEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
                        != 0) {
                    audioEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false);
                    break;
                }
                if (audioEncoderOutputBufferInfo.size != 0)
                    muxer.writeSampleData(
                            outputAudioTrack, encoderOutputBuffer, audioEncoderOutputBufferInfo);
                if ((audioEncoderOutputBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        != 0)
                    audioEncoderDone = true;

                audioEncoder.releaseOutputBuffer(encoderOutputBufferIndex, false);

                break;
            }
            if (!muxing && (encoderOutputAudioFormat != null)
                    && (encoderOutputVideoFormat != null)) {
                outputVideoTrack = muxer.addTrack(encoderOutputVideoFormat);
                outputAudioTrack = muxer.addTrack(encoderOutputAudioFormat);
                muxer.start();
                muxing = true;
            }
        }
    }

    private boolean isVideoFormat(MediaFormat format) {
        return getMimeTypeFor(format).startsWith("video/");
    }
    private boolean isAudioFormat(MediaFormat format) {
        return getMimeTypeFor(format).startsWith("audio/");
    }
    private String getMimeTypeFor(MediaFormat format) {
        return format.getString(MediaFormat.KEY_MIME);
    }

    private MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }
}