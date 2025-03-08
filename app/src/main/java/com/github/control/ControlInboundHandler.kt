package com.github.control

import android.view.MotionEvent
import com.github.control.scrcpy.Controller
import com.github.control.scrcpy.Position
import io.netty.buffer.ByteBuf
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.util.ReferenceCountUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach


sealed class Event
class TouchEvent(val event: MotionEvent, val screenWidth: Int, val screenHeight: Int) : Event()
class GlobalActionEvent(val action: Int) : Event()

class ControlInboundHandler(
    private val controller: Controller? = null,
    private val eventChannel: Channel<Event>? = null

) : SimpleChannelInboundHandler<ByteArray>() {
    companion object {
        private const val TYPE_GLOBAL_ACTION = 101
        private const val TYPE_MOTION_EVENT = 102
    }

    private var scope: CoroutineScope? = null
    override fun channelActive(ctx: ChannelHandlerContext) {
        super.channelActive(ctx)
        scope = CoroutineScope(Job())
        listenEventChannel(ctx, eventChannel, scope)
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        super.channelInactive(ctx)
        scope?.cancel()
        scope = null
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray) {
        ctx.fireChannelRead(ReferenceCountUtil.retain(msg))
        val byteBuf = PooledByteBufAllocator.DEFAULT.buffer(msg.size)
        byteBuf.writeBytes(msg)
        val type = byteBuf.readInt()
        when (type) {
            TYPE_MOTION_EVENT -> readTouchEvent(byteBuf)
            TYPE_GLOBAL_ACTION -> readGlobalActionEvent(byteBuf)
        }
    }


    private fun listenEventChannel(ctx: ChannelHandlerContext, eventChannel: Channel<Event>?, scope: CoroutineScope?) {
        if (scope != null && eventChannel != null) {
            eventChannel
                .consumeAsFlow()
                .onEach {
                    when (it) {
                        is GlobalActionEvent -> sendGlobalActionEvent(ctx, it.action)
                        is TouchEvent -> sendTouchEvent(ctx, it.event, it.screenWidth, it.screenHeight)
                    }
                }
                .flowOn(Dispatchers.IO)
                .launchIn(scope)
        }
    }

    private fun sendTouchEvent(ctx: ChannelHandlerContext, event: MotionEvent, screenWidth: Int, screenHeight: Int) {
        val buffer = PooledByteBufAllocator.DEFAULT.buffer(9 * Int.SIZE_BYTES + Float.SIZE_BYTES)
        val action = event.action
        val pointerId = event.getPointerId(event.actionIndex)
        val x = event.getX(event.actionIndex)
            .toInt()
        val y = event.getY(event.actionIndex)
            .toInt()
        val screenWidth = screenWidth
        val screenHeight = screenHeight
        val pressure = event.pressure
        val actionButton = event.actionButton
        val buttons = event.buttonState

        buffer.writeInt(TYPE_MOTION_EVENT)
        buffer.writeInt(action)
        buffer.writeInt(pointerId)
        buffer.writeInt(x)
        buffer.writeInt(y)
        buffer.writeInt(screenWidth)
        buffer.writeInt(screenHeight)
        buffer.writeFloat(pressure)
        buffer.writeInt(actionButton)
        buffer.writeInt(buttons)
        ctx.writeAndFlush(buffer)
    }

    private fun readTouchEvent(byteBuf: ByteBuf) {
        val action = byteBuf.readInt()
        val pointerId = byteBuf.readInt()
        val x = byteBuf.readInt()
        val y = byteBuf.readInt()
        val screenWidth = byteBuf.readInt()
        val screenHeight = byteBuf.readInt()
        val pressure = byteBuf.readFloat()
        val actionButton = byteBuf.readInt()
        val buttons = byteBuf.readInt()
        val position = Position(x, y, screenWidth, screenHeight)
        controller?.injectTouch(action, pointerId, position, pressure, actionButton, buttons)
    }

    private fun sendGlobalActionEvent(ctx: ChannelHandlerContext, action: Int) {
        val buffer = PooledByteBufAllocator.DEFAULT.buffer(2 * Int.SIZE_BYTES)
        buffer.writeInt(TYPE_GLOBAL_ACTION)
        buffer.writeInt(action)
        ctx.writeAndFlush(buffer)
    }

    private fun readGlobalActionEvent(byteBuf: ByteBuf) {
        val action = byteBuf.readInt()
        controller?.injectGlobalAction(action)
    }

}