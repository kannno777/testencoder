package jp.knnsyslab.testencoder;

import java.io.IOException;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.opengl.EGLContext;
import android.util.Log;
import android.view.Surface;

import com.serenegiant.glutilsOld.RenderHandler;

public class MediaVideoEncoder extends MediaEncoder {
    private static final String TAG = "KINOKO";
    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 25;
    private static final float BPP = 0.25f;

    private final int mWidth;
    private final int mHeight;
    private RenderHandler mRenderHandler;
    private Surface mSurface;

	public MediaVideoEncoder(final MediaMuxerWrapper muxer, final MediaEncoderListener listener, final int width, final int height) {
		super(muxer, listener);
		Log.i(TAG, "MediaVideoEncoder: ");
		mWidth = width;
		mHeight = height;
		mRenderHandler = RenderHandler.createHandler(TAG);
	}

	public boolean frameAvailableSoon(final float[] tex_matrix) {
		boolean result;
		if (result = super.frameAvailableSoon())
			mRenderHandler.draw(tex_matrix);
		return result;
	}

	public boolean frameAvailableSoon(final float[] tex_matrix, final float[] mvp_matrix) {
		boolean result;
		if (result = super.frameAvailableSoon())
			mRenderHandler.draw(tex_matrix, mvp_matrix);
		return result;
	}

	@Override
	public boolean frameAvailableSoon() {
		boolean result;
		if (result = super.frameAvailableSoon())
			mRenderHandler.draw(null);
		return result;
	}

	@Override
	protected void prepare() throws IOException {
        mTrackIndex = -1;
        mMuxerStarted = mIsEOS = false;

        final MediaCodecInfo videoCodecInfo = selectVideoCodec(MIME_TYPE);
        if (videoCodecInfo == null) {
            Log.e(TAG, "Unable to find codec for " + MIME_TYPE);
            return;
        }
	Log.i(TAG, "selected codec: " + videoCodecInfo.getName());

        final MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, calcBitRate());
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10);
	Log.i(TAG, "format: " + format);

        mMediaCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        mSurface = mMediaCodec.createInputSurface();
        mMediaCodec.start();
        Log.i(TAG, "prepare finishing");
        if (mListener != null) {
        	try {
        		mListener.onPrepared(this);
        	} catch (final Exception e) {
        		Log.e(TAG, "prepare:", e);
        	}
        }
	}

	public void setEglContext(final EGLContext shared_context, final int tex_id) {
		mRenderHandler.setEglContext(shared_context, tex_id, mSurface, true);
	}

	@Override
    protected void release() {
		Log.i(TAG, "release:");
		if (mSurface != null) {
			mSurface.release();
			mSurface = null;
		}
		if (mRenderHandler != null) {
			mRenderHandler.release();
			mRenderHandler = null;
		}
		super.release();
	}

	private int calcBitRate() {
		final int bitrate = (int)(BPP * FRAME_RATE * mWidth * mHeight);
		Log.i(TAG, String.format("bitrate=%5.2f[Mbps]", bitrate / 1024f / 1024f));
		return bitrate;
	}

    protected static final MediaCodecInfo selectVideoCodec(final String mimeType) {
    	Log.v(TAG, "selectVideoCodec:");

        final int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
        	final MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }

            final String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                	Log.i(TAG, "codec:" + codecInfo.getName() + ",MIME=" + types[j]);
            		final int format = selectColorFormat(codecInfo, mimeType);
                	if (format > 0) {
                		return codecInfo;
                	}
                }
            }
        }
        return null;
    }

    protected static final int selectColorFormat(final MediaCodecInfo codecInfo, final String mimeType) {
		Log.i(TAG, "selectColorFormat: ");
    	int result = 0;
    	final MediaCodecInfo.CodecCapabilities caps;
    	try {
    		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
    		caps = codecInfo.getCapabilitiesForType(mimeType);
    	} finally {
    		Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
    	}
        int colorFormat;
        for (int i = 0; i < caps.colorFormats.length; i++) {
        	colorFormat = caps.colorFormats[i];
            if (isRecognizedViewoFormat(colorFormat)) {
            	if (result == 0)
            		result = colorFormat;
                break;
            }
        }
        if (result == 0)
        	Log.e(TAG, "couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return result;
    }

    protected static int[] recognizedFormats;
	static {
		recognizedFormats = new int[] {
        	MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface,
		};
	}

    private static final boolean isRecognizedViewoFormat(final int colorFormat) {
		Log.i(TAG, "isRecognizedViewoFormat:colorFormat=" + colorFormat);
    	final int n = recognizedFormats != null ? recognizedFormats.length : 0;
    	for (int i = 0; i < n; i++) {
    		if (recognizedFormats[i] == colorFormat) {
    			return true;
    		}
    	}
    	return false;
    }

    @Override
    protected void signalEndOfInputStream() {
		Log.d(TAG, "sending EOS to encoder");
		mMediaCodec.signalEndOfInputStream();
		mIsEOS = true;
	}

}

