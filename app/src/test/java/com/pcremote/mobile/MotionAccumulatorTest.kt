package com.pcremote.mobile
import org.junit.Assert.*
import org.junit.Test
class MotionAccumulatorTest {
 @Test fun subpixelMotionIsNotLost() {
  val m=MotionAccumulator();repeat(4) {m.add(.25f,-.25f)}
  assertEquals(1 to -1,m.take());assertEquals(0 to 0,m.take())
 }
 @Test fun packetsPreserveBoundedRemainderAndRejectNaN() {
  val m=MotionAccumulator();m.add(900f,Float.NaN);assertEquals(0 to 0,m.take())
  m.add(900f,0f);assertEquals(600 to 0,m.take());assertEquals(300 to 0,m.take())
  m.add(10f,20f);m.clear();assertEquals(0 to 0,m.take())
 }
}
