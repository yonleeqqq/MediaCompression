package com.compression.library;

public class ChangerWrapper implements Runnable {

    private Throwable mThrowable;
    private VideoResolutionChanger mChanger;

    public ChangerWrapper(VideoResolutionChanger changer) {
        mChanger = changer;
    }

    @Override
    public void run() {
        try {
            mChanger.prepareAndChangeResolution();
        } catch (Throwable th) {
            mThrowable = th;
        }
    }

    public void changeResolutionInSeparatedThread(VideoResolutionChanger changer)
            throws Throwable {
        ChangerWrapper wrapper = new ChangerWrapper(changer);
        Thread th = new Thread(wrapper, ChangerWrapper.class.getSimpleName());
        th.start();
        th.join();
        if (wrapper.mThrowable != null)
            throw wrapper.mThrowable;
    }
}