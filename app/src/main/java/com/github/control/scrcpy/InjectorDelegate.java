package com.github.control.scrcpy;

import android.view.MotionEvent;

public interface InjectorDelegate {
    boolean injectInputEvent(MotionEvent inputEvent, int displayId, int injectMode);
}
