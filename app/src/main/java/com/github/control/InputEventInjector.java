package com.github.control;

import android.view.MotionEvent;

public interface InputEventInjector {
    boolean injectInputEvent(MotionEvent inputEvent, int displayId, int injectMode);
}
