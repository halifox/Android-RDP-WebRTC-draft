package com.github.control

import android.view.MotionEvent
import com.github.control.scrcpy.Controller
import com.github.control.scrcpy.Position
import io.netty.buffer.PooledByteBufAllocator
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ControlInboundHandler(
    private val controller: Controller,
) : SimpleChannelInboundHandler<ByteArray>() {
    companion object {
        const val TYPE_GLOBAL_ACTION = 0x101
        const val TYPE_MOTION_EVENT = 0x102
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteArray) {
        val byteBuf = PooledByteBufAllocator.DEFAULT.buffer(msg.size)
        byteBuf.writeBytes(msg)
        val type = byteBuf.readInt()
        when (type) {
            TYPE_MOTION_EVENT -> {
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
                controller.injectTouch(action, pointerId, position, pressure, actionButton, buttons)
            }

            TYPE_GLOBAL_ACTION -> {
                val action = byteBuf.readInt()
                controller.injectGlobalAction(action)
            }
        }
    }
}


sealed class Event
data object EmptyEvent : Event()
class TouchEvent(val event: MotionEvent, val w: Int, val h: Int) : Event()
class GlobalActionEvent(val action: Int) : Event()

class ControlOutboundHandler(private val flow: Flow<Event>) : SimpleChannelInboundHandler<ByteArray>() {
    private val scope = CoroutineScope(Job())
    override fun channelActive(ctx: ChannelHandlerContext) {
        flow
            .onEach {
                when (it) {
                    is GlobalActionEvent -> send(ctx, it.action)
                    is TouchEvent -> send(ctx, it.event, it.w, it.h)
                    EmptyEvent -> {}
                }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(scope)
    }

    override fun channelInactive(ctx: ChannelHandlerContext?) {
        scope.cancel()
    }

    override fun channelRead0(ctx: ChannelHandlerContext?, msg: ByteArray?) {

    }

    private fun send(ctx: ChannelHandlerContext, event: MotionEvent, w: Int, h: Int) {
        val buffer = PooledByteBufAllocator.DEFAULT.buffer(9 * Int.SIZE_BYTES + Float.SIZE_BYTES)

        buffer.writeInt(ControlInboundHandler.TYPE_MOTION_EVENT)
        buffer.writeInt(event.action)
        buffer.writeInt(event.getPointerId(event.actionIndex))
        buffer.writeInt(
            event.getX(event.actionIndex)
                .toInt()
        )
        buffer.writeInt(
            event.getY(event.actionIndex)
                .toInt()
        )
        buffer.writeInt(w)
        buffer.writeInt(h)
        buffer.writeFloat(event.pressure)
        buffer.writeInt(event.actionButton)
        buffer.writeInt(event.buttonState)

        ctx.writeAndFlush(buffer)
    }

    private fun send(ctx: ChannelHandlerContext, action: Int) {
        val buffer = PooledByteBufAllocator.DEFAULT.buffer(2 * Int.SIZE_BYTES)
        buffer.writeInt(ControlInboundHandler.TYPE_GLOBAL_ACTION)
        buffer.writeInt(action)
        ctx.writeAndFlush(buffer)
    }
}