package com.pcremote.mobile

/** Preserve subpixel motion while batching a burst into bounded packets. */
class MotionAccumulator {
    private var x=0f; private var y=0f
    @Synchronized fun add(dx: Float,dy: Float) { if(dx.isFinite() && dy.isFinite()) { x=(x+dx).coerceIn(-2400f,2400f); y=(y+dy).coerceIn(-2400f,2400f) } }
    @Synchronized fun take(): Pair<Int,Int> {
        val dx=x.toInt().coerceIn(-600,600);val dy=y.toInt().coerceIn(-600,600)
        x-=dx;y-=dy;return dx to dy
    }
    @Synchronized fun clear() {x=0f;y=0f}
}
