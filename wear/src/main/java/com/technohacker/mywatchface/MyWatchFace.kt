package com.technohacker.mywatchface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Message
import androidx.palette.graphics.Palette
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.Gravity
import android.view.SurfaceHolder
import android.widget.Toast

import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

private const val SECOND_TICK_STROKE_WIDTH = 2f

/**
 * Googly eyed watch face.
 */
class MyWatchFace : CanvasWatchFaceService() {

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    inner class Engine : CanvasWatchFaceService.Engine() {

        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false
        private var mCenterX: Float = 0F
        private var mCenterY: Float = 0F
        private var mMinuteCenterX: Float = 0F
        private var mHourCenterX: Float = 0F

        private var sHandRadius: Float = 0F
        private var sEyeRadius: Float = 0F

        private lateinit var mHandPaint: Paint
        private lateinit var mEyePaint: Paint
        private lateinit var mTextPaint: Paint
        private lateinit var mTappedPaint: Paint

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        private var mNightTime: Boolean = false
        private var mTapped: Boolean = false

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(
                WatchFaceStyle.Builder(this@MyWatchFace)
                    .setAcceptsTapEvents(true)
                    .setStatusBarGravity(Gravity.CENTER_HORIZONTAL)
                    .setViewProtectionMode(WatchFaceStyle.PROTECT_STATUS_BAR + WatchFaceStyle.PROTECT_HOTWORD_INDICATOR)
                    .build()
            )

            mCalendar = Calendar.getInstance()

            initializeWatchFace()
        }

        private fun initializeWatchFace() {
            setNightTime()
            mHandPaint = Paint().apply {
                color = Color.BLACK
                strokeWidth = SECOND_TICK_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
            }

            mEyePaint = Paint().apply {
                color = Color.BLACK
                strokeWidth = SECOND_TICK_STROKE_WIDTH
                isAntiAlias = true
                style = Paint.Style.STROKE
            }

            mTextPaint = Paint().apply {
                color = Color.BLACK
                strokeWidth = SECOND_TICK_STROKE_WIDTH
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = 50f
            }

            mTappedPaint = Paint().apply {
                color = Color.BLACK
                strokeWidth = SECOND_TICK_STROKE_WIDTH
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = 160f
            }
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false
            )
            mBurnInProtection = properties.getBoolean(
                WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false
            )
        }

        override fun onTimeTick() {
            super.onTimeTick()
            setNightTime()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode
            if (mAmbient) {
                mTapped = false
            }

            setNightTime()
            updateWatchHandStyle()
        }

        private fun setNightTime() {
            mNightTime = mCalendar.get(Calendar.HOUR_OF_DAY) !in 6..17
        }

        private fun updateWatchHandStyle() {
            if (mAmbient) {
                mHandPaint.color = Color.WHITE
                mEyePaint.color = Color.WHITE
                mTextPaint.color = Color.WHITE
                mTappedPaint.color = Color.WHITE

                mHandPaint.style = Paint.Style.STROKE
                mEyePaint.style = Paint.Style.STROKE
                mTextPaint.style = Paint.Style.STROKE

                mHandPaint.isAntiAlias = false
                mEyePaint.isAntiAlias = false
                mTextPaint.isAntiAlias = false
            } else {
                mHandPaint.color = Color.BLACK
                if (mNightTime) {
                    mEyePaint.color = Color.WHITE
                    mEyePaint.style = Paint.Style.FILL_AND_STROKE
                    mTextPaint.color = Color.WHITE
                    mTappedPaint.color = Color.WHITE
                } else {
                    mEyePaint.color = Color.BLACK
                    mEyePaint.style = Paint.Style.STROKE
                    mTextPaint.color = Color.BLACK
                    mTappedPaint.color = Color.BLACK
                }

                mHandPaint.style = Paint.Style.FILL
                mTextPaint.style = Paint.Style.FILL

                mHandPaint.isAntiAlias = true
                mEyePaint.isAntiAlias = true
                mTextPaint.isAntiAlias = true
            }
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                mHandPaint.alpha = if (inMuteMode) 100 else 255
                mEyePaint.alpha = if (inMuteMode) 100 else 255
                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f
            mCenterY = height / 2f

            /*
             * Calculate positions based on watch screen size.
             */
            sEyeRadius = (mCenterX * 0.25).toFloat()
            sHandRadius = sEyeRadius * 0.6f

            mHourCenterX = mCenterX - sEyeRadius - 10f
            mMinuteCenterX = mCenterX + sEyeRadius + 10f
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP ->
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    mTapped = !mTapped
            }
            invalidate()
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            drawBackground(canvas)
            drawWatchFace(canvas)
        }

        private fun drawBackground(canvas: Canvas) {
            if (mAmbient || mNightTime) {
                canvas.drawColor(Color.BLACK)
            } else {
                canvas.drawColor(Color.WHITE)
            }
        }

        private fun drawWatchFace(canvas: Canvas) {
            if (!mTapped) {
                canvas.drawCircle(
                    mHourCenterX,
                    mCenterY,
                    sEyeRadius,
                    mEyePaint
                )

                canvas.drawCircle(
                    mMinuteCenterX,
                    mCenterY,
                    sEyeRadius,
                    mEyePaint
                )

                /*
                 * These calculations reflect the rotation in degrees per unit of time, e.g.,
                 * 360 / 60 = 6 and 360 / 12 = 30.
                 */
                val minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f

                val hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f
                val hoursRotation = mCalendar.get(Calendar.HOUR) * 30 + hourHandOffset

                /*
                 * Save the canvas state before we can begin to rotate it.
                 */
                canvas.save()

                canvas.rotate(hoursRotation, mHourCenterX, mCenterY)
                canvas.drawCircle(
                    mHourCenterX,
                    mCenterY - sHandRadius / 2,
                    sHandRadius,
                    mHandPaint
                )
                canvas.restore()

                canvas.save()

                canvas.rotate(minutesRotation, mMinuteCenterX, mCenterY)
                canvas.drawCircle(
                    mMinuteCenterX,
                    mCenterY - sHandRadius / 2,
                    sHandRadius,
                    mHandPaint
                )

                canvas.restore()
            } else {
                canvas.drawText(
                    "> <",
                    mCenterX,
                    mCenterY + 50f,
                    mTappedPaint
                )
            }
            canvas.drawText(
                SimpleDateFormat("HH:mm").format(mCalendar.time),
                mCenterX,
                mCenterY + sEyeRadius + 60f,
                mTextPaint
            )
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }
    }
}
