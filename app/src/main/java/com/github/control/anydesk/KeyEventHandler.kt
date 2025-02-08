package com.github.control.anydesk

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
import android.view.KeyEvent

/**
 * KeyEventHandler 负责处理按键事件，并通过辅助功能服务执行相应的全局操作。
 *
 * @param accessibilityService 依赖的辅助功能服务，用于执行全局操作。
 */
class KeyEventHandler(
    private val accessibilityService: AccessibilityService,
) {
    /**
     * 处理按键事件，根据按键代码执行相应的系统操作。
     *
     * @param keyEvent 按键事件对象，包含按键的详细信息。
     */
    fun handleEvent(keyEvent: KeyEvent) {
        val action = keyEvent.action
        val keyCode = keyEvent.keyCode
        val isShiftPressed = keyEvent.isShiftPressed
        val isCtrlPressed = keyEvent.isCtrlPressed
        when (keyCode) {
            KeyEvent.KEYCODE_HOME        -> accessibilityService.performGlobalAction(GLOBAL_ACTION_HOME)
            KeyEvent.KEYCODE_BACK        -> accessibilityService.performGlobalAction(GLOBAL_ACTION_BACK)
            KeyEvent.KEYCODE_VOLUME_UP   -> {}
            KeyEvent.KEYCODE_VOLUME_DOWN -> {}
            KeyEvent.KEYCODE_POWER       -> {}
            KeyEvent.KEYCODE_SPACE       -> {}
            KeyEvent.KEYCODE_MENU        -> {}
            KeyEvent.KEYCODE_APP_SWITCH  -> accessibilityService.performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
    }
}