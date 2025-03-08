package com.github.control

import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.charset.Charset


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
