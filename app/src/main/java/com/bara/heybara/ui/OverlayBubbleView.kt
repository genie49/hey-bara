package com.bara.heybara.ui

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.bara.heybara.domain.session.SessionState

class OverlayBubbleView(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: FrameLayout? = null
    private var statusText: TextView? = null
    private var contentText: TextView? = null

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = 100
    }

    fun show() {
        if (overlayView != null) return

        overlayView = FrameLayout(context).apply {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 48, 48, 48)
                setBackgroundColor(0xFFFFFFFF.toInt())
                elevation = 16f

                statusText = TextView(context).apply {
                    text = "Hey Bara \u00B7 듣고 있어요"
                    textSize = 14f
                    setTextColor(0xFF1A1A1A.toInt())
                }
                addView(statusText)

                contentText = TextView(context).apply {
                    text = ""
                    textSize = 16f
                    setTextColor(0xFF1A1A1A.toInt())
                    setPadding(0, 16, 0, 0)
                }
                addView(contentText)
            }
            addView(card)
        }

        windowManager.addView(overlayView, layoutParams)
    }

    fun updateState(state: SessionState, text: String = "") {
        statusText?.text = when (state) {
            SessionState.LISTENING -> "Hey Bara \u00B7 듣고 있어요"
            SessionState.CONFIRMING -> "Hey Bara \u00B7 확인 대기"
            else -> "Hey Bara"
        }
        contentText?.text = text
    }

    fun dismiss() {
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }
}
