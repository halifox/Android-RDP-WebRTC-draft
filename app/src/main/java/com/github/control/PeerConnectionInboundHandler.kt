package com.github.control

import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.util.ReferenceCountUtil
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import org.webrtc.VideoSink
import org.webrtc.VideoTrack

class PeerConnectionInboundHandler(
    val peerConnectionFactory: PeerConnectionFactory,
    val videoTrack: VideoTrack? = null,
    val videoSink: VideoSink? = null,
) : SimpleChannelInboundHandler<ByteArray>() {
    private val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
    private var peerConnection: PeerConnection? = null
    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : EmptyPeerConnectionObserver() {
            override fun onAddTrack(rtpReceiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                val track = rtpReceiver.track()
                when (track) {
                    is VideoTrack -> {
                        if (videoSink != null) {
                            track.addSink(videoSink)
                        }
                    }
                }
            }

            override fun onIceCandidate(iceCandidate: IceCandidate) {
                sendIceCandidate(ctx, iceCandidate)
            }
        })
        if (videoTrack != null) {
            peerConnection?.addTrack(videoTrack)
            peerConnection?.createOffer(object : EmptySdpObserver() {
                override fun onCreateSuccess(description: SessionDescription) {
                    peerConnection?.setLocalDescription(EmptySdpObserver(), description)
                    sendSessionDescription(ctx, description)
                }
            }, MediaConstraints())
        }


    }


    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        peerConnection?.dispose()
        peerConnection = null
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray) {
        ctx.fireChannelRead(ReferenceCountUtil.retain(msg))
        val byteBuf = PooledByteBufAllocator.DEFAULT.buffer(msg.size)
        byteBuf.writeBytes(msg)
        val type = byteBuf.readInt()
        when (type) {
            1 -> {
                val ice = readIceCandidate(byteBuf)
                peerConnection?.addIceCandidate(ice)
            }

            2 -> {
                val sdp = readSessionDescription(byteBuf)
                peerConnection?.setRemoteDescription(EmptySdpObserver(), sdp)
                peerConnection?.createAnswer(object : EmptySdpObserver() {
                    override fun onCreateSuccess(description: SessionDescription) {
                        peerConnection?.setLocalDescription(EmptySdpObserver(), description)
                        sendSessionDescription(ctx, description)
                    }
                }, MediaConstraints())
            }

            else -> {
            }
        }
    }
}

