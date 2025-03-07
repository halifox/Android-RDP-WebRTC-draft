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

fun sendIceCandidate(ctx: ChannelHandlerContext, iceCandidate: IceCandidate) {
    val buffer = PooledByteBufAllocator.DEFAULT.buffer(4 * Int.SIZE_BYTES + iceCandidate.sdpMid.length + iceCandidate.sdp.length)
    buffer.writeInt(1)
    buffer.writeInt(iceCandidate.sdpMid.length)
    buffer.writeCharSequence(iceCandidate.sdpMid, Charset.defaultCharset())
    buffer.writeInt(iceCandidate.sdpMLineIndex)
    buffer.writeInt(iceCandidate.sdp.length)
    buffer.writeCharSequence(iceCandidate.sdp, Charset.defaultCharset())
    ctx.writeAndFlush(buffer)
}

fun sendSessionDescription(ctx: ChannelHandlerContext, description: SessionDescription) {
    val buffer = PooledByteBufAllocator.DEFAULT.buffer(3 * Int.SIZE_BYTES + description.type.name.length + description.description.length)
    buffer.writeInt(2)
    buffer.writeInt(description.type.name.length)
    buffer.writeCharSequence(description.type.name, Charset.defaultCharset())
    buffer.writeInt(description.description.length)
    buffer.writeCharSequence(description.description, Charset.defaultCharset())
    ctx.writeAndFlush(buffer)
}


fun readSessionDescription(byteBuf: ByteBuf): SessionDescription {
    return SessionDescription(
        SessionDescription.Type.valueOf(
            byteBuf.readCharSequence(byteBuf.readInt(), Charset.defaultCharset())
                .toString()
        ),
        byteBuf.readCharSequence(byteBuf.readInt(), Charset.defaultCharset())
            .toString(),
    )
}

fun readIceCandidate(byteBuf: ByteBuf): IceCandidate {
    return IceCandidate(
        byteBuf.readCharSequence(byteBuf.readInt(), Charset.defaultCharset())
            .toString(),
        byteBuf.readInt(),
        byteBuf.readCharSequence(byteBuf.readInt(), Charset.defaultCharset())
            .toString(),
    )
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
