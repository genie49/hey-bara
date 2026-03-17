package com.bara.heybara.data.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class UiNode(
    val index: Int,
    val className: String,
    val label: String,
    val isClickable: Boolean,
    val isLongClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isSelected: Boolean,
    val isChecked: Boolean,
    val nodeInfo: AccessibilityNodeInfo
)

class BaraAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        instance = this
        Log.d(TAG, "AccessibilityService 연결됨")
    }

    override fun onDestroy() {
        instance = null
        Log.d(TAG, "AccessibilityService 해제됨")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "BaraA11y"
        private var instance: BaraAccessibilityService? = null
        private var capturedNodes = mutableListOf<UiNode>()

        fun isEnabled(context: Context): Boolean {
            // 인스턴스가 연결되어 있으면 확실히 활성화
            if (instance != null) return true
            // 시스템 설정에서 확인
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val serviceName = "${context.packageName}/${BaraAccessibilityService::class.java.canonicalName}"
            return enabledServices.contains(serviceName) || enabledServices.contains(context.packageName)
        }

        fun captureUiTree(): String? {
            val service = instance ?: return null
            val root = service.rootInActiveWindow ?: return null
            capturedNodes.clear()

            val sb = StringBuilder()
            var index = 1
            val packageName = root.packageName?.toString() ?: "unknown"
            sb.appendLine("현재 앱: $packageName")

            fun traverse(node: AccessibilityNodeInfo) {
                val label = node.text?.toString()
                    ?: node.contentDescription?.toString()
                    ?: ""
                val className = node.className?.toString()?.substringAfterLast('.') ?: ""
                val isClickable = node.isClickable
                val isLongClickable = node.isLongClickable
                val isEditable = node.isEditable
                val isScrollable = node.isScrollable
                val isSelected = node.isSelected
                val isChecked = node.isChecked

                val hasInteraction = isClickable || isLongClickable || isEditable || isScrollable
                val hasLabel = label.isNotBlank()

                if (hasLabel || hasInteraction) {
                    val attrs = mutableListOf<String>()
                    if (isClickable) attrs.add("clickable")
                    if (isLongClickable) attrs.add("long-clickable")
                    if (isEditable) attrs.add("editable")
                    if (isScrollable) attrs.add("scrollable")
                    if (isSelected) attrs.add("selected")
                    if (isChecked) attrs.add("checked")
                    val attrStr = if (attrs.isNotEmpty()) " (${attrs.joinToString()})" else ""
                    val displayLabel = if (label.length > 50) label.take(50) + "..." else label

                    sb.appendLine("[$index] $className: '$displayLabel'$attrStr")
                    capturedNodes.add(
                        UiNode(index, className, label, isClickable, isLongClickable,
                            isEditable, isScrollable, isSelected, isChecked, node)
                    )
                    index++
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { traverse(it) }
                }
            }

            traverse(root)
            return sb.toString()
        }

        fun performClick(nodeIndex: Int): Boolean {
            val node = capturedNodes.find { it.index == nodeIndex } ?: return false
            return node.nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        fun performLongClick(nodeIndex: Int): Boolean {
            val node = capturedNodes.find { it.index == nodeIndex } ?: return false
            return node.nodeInfo.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        }

        fun performType(nodeIndex: Int, text: String): Boolean {
            val node = capturedNodes.find { it.index == nodeIndex } ?: return false
            // 먼저 포커스
            node.nodeInfo.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            // 기존 텍스트 지우기
            node.nodeInfo.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            })
            return true
        }

        fun performScroll(direction: String): Boolean {
            val service = instance ?: return false
            val root = service.rootInActiveWindow ?: return false

            fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (node.isScrollable) return node
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { child ->
                        findScrollable(child)?.let { return it }
                    }
                }
                return null
            }

            val scrollable = findScrollable(root) ?: return false
            val action = if (direction == "down")
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            return scrollable.performAction(action)
        }

        fun pressBack(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_BACK)
        }

        fun pressEnter(): Boolean {
            val service = instance ?: return false
            // IME_ACTION을 통한 엔터 입력은 AccessibilityService에서 직접 불가
            // 대신 dispatchGesture로는 안 되고, 키 이벤트를 보내야 함
            // 가장 간단한 방법: 현재 포커스된 노드에서 ACTION_IME_ENTER 사용 (API 30+)
            val root = service.rootInActiveWindow ?: return false
            fun findFocused(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (node.isFocused) return node
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { findFocused(it)?.let { return it } }
                }
                return null
            }
            val focused = findFocused(root)
            return if (focused != null && android.os.Build.VERSION.SDK_INT >= 30) {
                focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            } else {
                // Fallback: 포커스된 EditText에 "\n" 추가
                focused?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    val current = focused.text?.toString() ?: ""
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "$current\n")
                }) ?: false
            }
        }
    }
}
