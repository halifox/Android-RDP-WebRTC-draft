package com.github.control.scrcpy;

import android.view.MotionEvent;

public interface ControllerDelegate {
    boolean injectInputEvent(MotionEvent inputEvent, int displayId, int injectMode);

    boolean injectGlobalAction(int action);
}
