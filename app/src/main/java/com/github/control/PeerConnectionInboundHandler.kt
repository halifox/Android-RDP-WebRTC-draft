package com.github.control

import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.util.ReferenceCountUtil
import org.webrtc.AudioTrack
import org.webrtc.AudioTrackSink
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import org.webrtc.VideoSink
import org.webrtc.VideoTrack
import java.nio.charset.Charset

class PeerConnectionInboundHandler(
    val peerConnectionFactory: PeerConnectionFactory,
    val videoTrack: VideoTrack? = null,
    val audioTrack: AudioTrack? = null,
    val isOffer: Boolean = false,

    val videoSink: VideoSink? = null,
    val audioTrackSink: AudioTrackSink? = null,
) : SimpleChannelInboundHandler<ByteArray>() {
    companion object {
        private const val ICE = 201
        private const val SDP = 202
    }

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

                    is AudioTrack -> {
                        if (audioTrackSink != null) {
                            track.addSink(audioTrackSink)
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
        }
        if (audioTrack != null) {
            peerConnection?.addTrack(audioTrack)
        }
        if (isOffer) {
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
            ICE -> {
                val ice = readIceCandidate(byteBuf)
                peerConnection?.addIceCandidate(ice)
            }

            SDP -> {
                val sdp = readSessionDescription(byteBuf)
                peerConnection?.setRemoteDescription(EmptySdpObserver(), sdp)
                if (!isOffer) {
                    peerConnection?.createAnswer(object : EmptySdpObserver() {
                        override fun onCreateSuccess(description: SessionDescription) {
                            peerConnection?.setLocalDescription(EmptySdpObserver(), description)
                            sendSessionDescription(ctx, description)
                        }
                    }, MediaConstraints())
                }
            }
        }
    }


    fun sendIceCandidate(ctx: ChannelHandlerContext, iceCandidate: IceCandidate) {
        val buffer = PooledByteBufAllocator.DEFAULT.buffer(4 * Int.SIZE_BYTES + iceCandidate.sdpMid.length + iceCandidate.sdp.length)
        buffer.writeInt(ICE)
        buffer.writeInt(iceCandidate.sdpMid.length)
        buffer.writeCharSequence(iceCandidate.sdpMid, Charset.defaultCharset())
        buffer.writeInt(iceCandidate.sdpMLineIndex)
        buffer.writeInt(iceCandidate.sdp.length)
        buffer.writeCharSequence(iceCandidate.sdp, Charset.defaultCharset())
        ctx.writeAndFlush(buffer)
    }

    fun sendSessionDescription(ctx: ChannelHandlerContext, description: SessionDescription) {
        val buffer = PooledByteBufAllocator.DEFAULT.buffer(3 * Int.SIZE_BYTES + description.type.name.length + description.description.length)
        buffer.writeInt(SDP)
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

}

