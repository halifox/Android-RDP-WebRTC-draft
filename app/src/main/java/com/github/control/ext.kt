package com.github.control

import android.graphics.Point
import android.util.Size
import android.view.MotionEvent
import android.view.View
import com.blankj.utilcode.util.ThreadUtils
import com.github.control.gesture.Controller
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.io.DataInputStream
import java.io.DataOutputStream

const val ACTION_EVENT = 101
const val TOUCH_EVENT = 102
const val ICE_CANDIDATE = 201
const val SESSION_DESCRIPTION = 202

fun sendGlobalActionEvent(outputStream: DataOutputStream, action: Int) {
    ThreadUtils.getSinglePool()
        .submit {
            outputStream.writeInt(ACTION_EVENT)
            outputStream.writeInt(action)
            outputStream.flush()
        }
}

fun receiveGlobalActionEvent(inputStream: DataInputStream, controller: Controller) {
    val action = inputStream.readInt()
    controller.injectGlobalAction(action)
}

fun sendTouchEvent(outputStream: DataOutputStream, event: MotionEvent, view: View) {
    ThreadUtils.getSinglePool()
        .submit {
            val action = event.action
            val pointerId = event.getPointerId(event.actionIndex)
            val x = (event.getX(event.actionIndex) - view.x).toInt()
            val y = (event.getY(event.actionIndex) - view.y).toInt()
            val screenWidth = view.width
            val screenHeight = view.height
            val pressure = event.pressure
            val actionButton = event.actionButton
            val buttons = event.buttonState

            outputStream.writeInt(TOUCH_EVENT)
            outputStream.writeInt(action)
            outputStream.writeInt(pointerId)
            outputStream.writeInt(x)
            outputStream.writeInt(y)
            outputStream.writeInt(screenWidth)
            outputStream.writeInt(screenHeight)
            outputStream.writeFloat(pressure)
            outputStream.writeInt(actionButton)
            outputStream.writeInt(buttons)
            outputStream.flush()
        }
}

fun receiveTouchEvent(inputStream: DataInputStream, controller: Controller) {
    val action = inputStream.readInt()
    val pointerId = inputStream.readInt()
    val x = inputStream.readInt()
    val y = inputStream.readInt()
    val screenWidth = inputStream.readInt()
    val screenHeight = inputStream.readInt()
    val pressure = inputStream.readFloat()
    val actionButton = inputStream.readInt()
    val buttons = inputStream.readInt()
    controller.injectTouch(action, pointerId, Point(x, y), Size(screenWidth, screenHeight), pressure, actionButton, buttons)
}


fun sendIceCandidate(outputStream: DataOutputStream, iceCandidate: IceCandidate) {
    outputStream.writeInt(ICE_CANDIDATE)
    outputStream.writeUTF(iceCandidate.sdpMid)
    outputStream.writeInt(iceCandidate.sdpMLineIndex)
    outputStream.writeUTF(iceCandidate.sdp)
    outputStream.flush()
}

fun receiveIceCandidate(inputStream: DataInputStream, peerConnection: PeerConnection) {
    val sdpMid = inputStream.readUTF()
    val sdpMLineIndex = inputStream.readInt()
    val sdp = inputStream.readUTF()
    val ice = IceCandidate(sdpMid, sdpMLineIndex, sdp)
    peerConnection.addIceCandidate(ice)

}

fun sendSessionDescription(outputStream: DataOutputStream, sessionDescription: SessionDescription) {
    outputStream.writeInt(SESSION_DESCRIPTION)
    outputStream.writeUTF(sessionDescription.type.name)
    outputStream.writeUTF(sessionDescription.description)
    outputStream.flush()

}

fun receiveSessionDescription(inputStream: DataInputStream, peerConnection: PeerConnection) {
    val type = inputStream.readUTF()
    val description = inputStream.readUTF()
    val sdp = SessionDescription(SessionDescription.Type.valueOf(type), description)
    peerConnection.setRemoteDescription(EmptySdpObserver(), sdp)
}

open class EmptySdpObserver : SdpObserver {

    override fun onSetFailure(str: String) {
    }

    override fun onSetSuccess() {
    }

    override fun onCreateSuccess(description: SessionDescription) {
    }

    override fun onCreateFailure(str: String) {
    }
}

open class EmptyPeerConnectionObserver : PeerConnection.Observer {
    override fun onSignalingChange(state: PeerConnection.SignalingState) {

    }

    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {

    }

    override fun onIceConnectionReceivingChange(b: Boolean) {

    }

    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {

    }

    override fun onIceCandidate(iceCandidate: IceCandidate) {

    }

    override fun onIceCandidatesRemoved(mediaStreams: Array<out IceCandidate>) {

    }

    override fun onAddStream(mediaStream: MediaStream) {

    }

    override fun onRemoveStream(mediaStream: MediaStream) {

    }

    override fun onDataChannel(dataChannel: DataChannel) {

    }

    override fun onRenegotiationNeeded() {

    }

    override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {

    }

}
