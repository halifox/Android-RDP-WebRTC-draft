package com.github.control.scrcpy;

import android.view.MotionEvent;

public interface ControllerDelegate {
    void nothing();

    boolean injectInputEvent(MotionEvent inputEvent, int displayId, int injectMode);
}
