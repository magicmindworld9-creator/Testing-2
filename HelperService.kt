package com.dp.formhelper

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Floating helper: [Capture] reads label/value pairs from the screen,
 * [Fill] types saved data into text boxes and picks dropdown options,
 * [Next] taps Next/Continue (Submit needs a second tap to confirm).
 * Nothing happens unless you tap one of the buttons.
 */
class HelperService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: LinearLayout? = null
    private var nextButton: Button? = null
    private var busy = false
    private var submitArmedUntil = 0L

    private data class Field(val node: AccessibilityNodeInfo, val label: String, val dropdown: Boolean)
    private data class Step(val labelNorm: String, val label: String, val value: String, val dropdown: Boolean)

    private val placeholder = Regex("^(select|choose|pick|--|ఎంచుకో).*", RegexOption.IGNORE_CASE)
    private val nextWords = listOf("next", "continue", "proceed", "savenext", "saveandnext", "తదుపరి", "ముందుకు")
    private val submitWords = listOf("submit", "finish", "save", "confirm", "సమర్పించు")

    // ---------------------------------------------------------------- lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        hideOverlay()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- overlay

    private fun showOverlay() {
        if (overlay != null) return
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xCC202020.toInt())
            setPadding(6, 6, 6, 6)
        }
        val actionButtons = ArrayList<Button>()
        fun add(label: String, isAction: Boolean, onClick: () -> Unit): Button {
            val b = Button(this).apply {
                text = label
                textSize = 12f
                transformationMethod = null
                minWidth = 0
                minimumWidth = 0
                setOnClickListener { onClick() }
            }
            box.addView(b, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            if (isAction) actionButtons.add(b)
            return b
        }
        add("Capture", true) { capture() }
        add("Fill", true) { fill() }
        nextButton = add("Next", true) { next() }
        add("⇆ Hide/Show", false) {
            val vis = if (actionButtons.first().visibility == View.VISIBLE) View.GONE else View.VISIBLE
            actionButtons.forEach { it.visibility = vis }
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        wm.addView(box, lp)
        overlay = box
    }

    private fun hideOverlay() {
        overlay?.let {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
        }
        overlay = null
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // ---------------------------------------------------------------- reading the screen

    private fun walkAll(n: AccessibilityNodeInfo?, depth: Int = 0, visit: (AccessibilityNodeInfo) -> Unit) {
        if (n == null || depth > 45 || !n.isVisibleToUser) return
        visit(n)
        for (i in 0 until n.childCount) walkAll(n.getChild(i), depth + 1, visit)
    }

    /** Finds text boxes and dropdowns on screen together with their labels. */
    private fun collect(root: AccessibilityNodeInfo): List<Field> {
        val out = ArrayList<Field>()
        var lastText = ""
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 45 || !n.isVisibleToUser) return
            val cls = n.className?.toString() ?: ""
            val text = n.text?.toString()?.trim() ?: ""
            val isDrop = !n.isEditable && (
                cls.contains("Spinner") || cls.contains("DropDown", true) ||
                    (n.isClickable && placeholder.matches(text)))
            if ((n.isEditable && !n.isPassword) || isDrop) {
                var label = n.hintText?.toString() ?: ""
                if (label.isBlank()) label = n.labeledBy?.text?.toString() ?: ""
                if (label.isBlank()) label = n.contentDescription?.toString() ?: ""
                if (label.isBlank()) label = lastText
                out.add(Field(n, label.trim(), isDrop))
                return
            }
            if (n.childCount == 0 && text.isNotEmpty() && !n.isClickable) lastText = text
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return out
    }

    private fun allRoots(): List<AccessibilityNodeInfo> {
        val roots = windows
            .filter { it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
            .sortedByDescending { it.layer }
            .mapNotNull { it.root }
        return if (roots.isNotEmpty()) roots else listOfNotNull(rootInActiveWindow)
    }

    private fun clickNode(n: AccessibilityNodeInfo): Boolean {
        var c: AccessibilityNodeInfo? = n
        var hops = 0
        while (c != null && hops < 6) {
            if (c.isClickable && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            c = c.parent
            hops++
        }
        return false
    }

    // ---------------------------------------------------------------- Capture

    private fun capture() {
        val root = rootInActiveWindow ?: return toast("Open the screen you want to read first")
        val found = LinkedHashMap<String, String>()
        val texts = ArrayList<String>()
        walkAll(root) { n ->
            val t = n.text?.toString()?.trim().orEmpty()
            if (t.isNotEmpty() && n.childCount == 0 && !n.isEditable && !n.isClickable && !n.isPassword) texts.add(t)
        }
        var i = 0
        while (i < texts.size) {
            val t = texts[i]
            val c = t.indexOf(':')
            if (c in 1 until t.length - 1) {
                val k = t.substring(0, c).trim()
                if (k.any { it.isLetter() }) found[k] = t.substring(c + 1).trim()
            } else if (t.endsWith(":") && i + 1 < texts.size) {
                val k = t.dropLast(1).trim()
                if (k.any { it.isLetter() }) found[k] = texts[i + 1]
                i++
            }
            i++
        }
        for (f in collect(root)) {
            val v = f.node.text?.toString()?.trim().orEmpty()
            if (f.label.isNotBlank() && v.isNotEmpty() && !placeholder.matches(v)) found[f.label] = v
        }
        if (found.isEmpty()) return toast("Nothing readable found on this screen")
        ProfileStore.merge(this, ProfileStore.CAPTURED, found)
        ProfileStore.setActive(this, ProfileStore.CAPTURED)
        toast("Captured ${found.size} items. Profile \"Captured\" is now active. Switch to the other form and tap Fill.")
    }

    // ---------------------------------------------------------------- Fill

    private fun fill() {
        if (busy) return
        val root = rootInActiveWindow ?: return toast("Open the form first")
        val entries = ProfileStore.entries(this)
        if (entries.isEmpty()) return toast("Active profile \"${ProfileStore.active(this)}\" has no data. Open Form Helper to add some.")

        val plan = ArrayList<Step>()
        val seen = HashSet<String>()
        val unmatched = ArrayList<String>()
        for (f in collect(root)) {
            val ln = ProfileStore.norm(f.label)
            val e = ProfileStore.find(entries, ln)
            if (e == null) {
                if (f.label.isNotBlank()) unmatched.add(f.label)
            } else if (seen.add(ln)) {
                plan.add(Step(ln, f.label, e.value, f.dropdown))
            }
        }
        if (plan.isEmpty()) return toast("No fields on screen match your saved labels")

        busy = true
        var filled = 0
        val failed = ArrayList<String>()

        fun finish(aborted: String? = null) {
            busy = false
            val sb = StringBuilder("Filled $filled of ${plan.size}.")
            if (aborted != null) sb.append(" Stopped: $aborted.")
            if (failed.isNotEmpty()) sb.append(" Could not set: ${failed.joinToString()}.")
            if (unmatched.isNotEmpty()) sb.append(" No data for: ${unmatched.joinToString()}.")
            sb.append(" Please check before continuing.")
            toast(sb.toString())
        }

        fun step(i: Int) {
            if (i >= plan.size) return finish()
            val s = plan[i]
            val r = rootInActiveWindow
            val f = r?.let { collect(it) }?.firstOrNull { ProfileStore.norm(it.label) == s.labelNorm }
            if (f == null) {
                failed.add(s.label)
                handler.postDelayed({ step(i + 1) }, 100)
                return
            }
            if (!s.dropdown) {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, s.value)
                }
                if (f.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) filled++ else failed.add(s.label)
                handler.postDelayed({ step(i + 1) }, 250)
            } else {
                if (ProfileStore.norm(f.node.text?.toString() ?: "") == ProfileStore.norm(s.value)) {
                    filled++
                    handler.postDelayed({ step(i + 1) }, 100)
                    return
                }
                clickNode(f.node)
                handler.postDelayed({
                    pickOption(s.value, 0) { ok ->
                        if (ok) {
                            filled++
                            handler.postDelayed({ step(i + 1) }, 700)
                        } else {
                            failed.add("${s.label} (option \"${s.value}\" not found)")
                            finish("a dropdown may still be open, pick the option yourself")
                        }
                    }
                }, 800)
            }
        }
        step(0)
    }

    /** Looks for the option text in the open dropdown, scrolling the list if needed, and taps it. */
    private fun pickOption(value: String, attempt: Int, done: (Boolean) -> Unit) {
        val v = ProfileStore.norm(value)
        var exact: AccessibilityNodeInfo? = null
        var partial: AccessibilityNodeInfo? = null
        var scroller: AccessibilityNodeInfo? = null
        for (r in allRoots()) walkAll(r) { n ->
            val t = ProfileStore.norm(n.text?.toString() ?: "")
            if (t.isNotEmpty() && !n.isEditable) {
                if (t == v && exact == null) exact = n
                else if (v.length >= 2 && t.contains(v) && partial == null) partial = n
            }
            if (n.isScrollable && scroller == null) scroller = n
        }
        val hit = exact ?: partial
        if (hit != null && clickNode(hit)) return done(true)
        val sc = scroller
        if (attempt < 8 && sc != null && sc.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            handler.postDelayed({ pickOption(value, attempt + 1, done) }, 450)
        } else {
            done(false)
        }
    }

    // ---------------------------------------------------------------- Next / Submit

    private fun findButton(root: AccessibilityNodeInfo, words: List<String>): AccessibilityNodeInfo? {
        var hit: AccessibilityNodeInfo? = null
        walkAll(root) { n ->
            if (hit == null) {
                val t = ProfileStore.norm(
                    (n.text?.toString() ?: n.contentDescription?.toString() ?: ""))
                if (t.isNotEmpty() && t.length <= 20 && words.any { t == it || t.startsWith(it) }) hit = n
            }
        }
        return hit
    }

    private fun next() {
        val root = rootInActiveWindow ?: return toast("Open the form first")
        val nxt = findButton(root, nextWords)
        if (nxt != null) {
            if (!clickNode(nxt)) toast("Could not tap Next")
            return
        }
        val sub = findButton(root, submitWords)
        if (sub == null) return toast("No Next or Submit button visible. Scroll down and try again.")
        val now = System.currentTimeMillis()
        if (now < submitArmedUntil) {
            submitArmedUntil = 0
            nextButton?.text = "Next"
            if (!clickNode(sub)) toast("Could not tap Submit")
        } else {
            submitArmedUntil = now + 6000
            nextButton?.text = "Tap again to submit"
            toast("Check the form carefully. Tap again within 6 seconds to submit.")
            handler.postDelayed({
                if (System.currentTimeMillis() >= submitArmedUntil) nextButton?.text = "Next"
            }, 6100)
        }
    }
}
