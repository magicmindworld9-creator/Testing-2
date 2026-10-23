package com.dp.formhelper

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var spinner: Spinner
    private lateinit var edit: EditText
    private var current = ""
    private var loading = false

    private val sample = """# One line per form field:  label|other name = value
# Put the label as it appears in the form. Add other spellings or languages after |
name|farmer name|applicant name = Sample Farmer
mobile|phone number = 9000000000
village = Example Village
district = Example District
gender = Male"""

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        fun label(t: String, bold: Boolean = false) = TextView(this).apply {
            text = t
            textSize = 15f
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        }

        col.addView(TextView(this).apply {
            text = "Form Helper"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
        })

        status = label("")
        col.addView(status)
        col.addView(Button(this).apply {
            text = "1. Turn on Form Helper (Accessibility)"
            transformationMethod = null
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })

        col.addView(label("2. Data profile", true))
        spinner = Spinner(this)
        col.addView(spinner)

        edit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP or Gravity.START
            minLines = 10
            textSize = 14f
            typeface = Typeface.MONOSPACE
            hint = sample
        }
        col.addView(edit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun rowBtn(t: String, onClick: () -> Unit) = Button(this).apply {
            text = t
            transformationMethod = null
            setOnClickListener { onClick() }
        }.also { row.addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)) }
        rowBtn("Save") { saveCurrent(); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show() }
        rowBtn("New profile") { newProfile() }
        rowBtn("Delete") { deleteCurrent() }
        col.addView(row)

        col.addView(label("3. How to use", true))
        col.addView(TextView(this).apply {
            textSize = 14f
            text = "• Open the other app and go to its form. A small panel (Capture / Fill / Next) floats on the right edge.\n" +
                "• Fill: types your saved values into matching text boxes and picks matching dropdown options.\n" +
                "• Capture: reads \"Label: value\" pairs and filled boxes from the screen into the profile \"Captured\". " +
                "Then open the next form and tap Fill.\n" +
                "• Next: taps Next/Continue. For Submit/Save it asks you to tap twice.\n" +
                "• Always check the form yourself before submitting."
        })

        setContentView(ScrollView(this).apply { addView(col) })

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (loading) return
                if (current.isNotEmpty()) saveCurrent()
                current = spinner.selectedItem as String
                ProfileStore.setActive(this@MainActivity, current)
                edit.setText(ProfileStore.text(this@MainActivity, current))
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        reloadSpinner(ProfileStore.active(this))
    }

    override fun onResume() {
        super.onResume()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        status.text = if (enabled) "Status: ON. The floating panel shows over other apps."
        else "Status: OFF. Tap the button below, open Installed apps / Downloaded apps, choose Form Helper and turn it on. " +
            "If Android says \"Restricted setting\", open Settings > Apps > Form Helper > ⋮ > Allow restricted settings, then try again."
        // pick up data captured while the app was in the background
        if (current.isNotEmpty() && ProfileStore.active(this) != current) reloadSpinner(ProfileStore.active(this))
        else if (current.isNotEmpty()) edit.setText(ProfileStore.text(this, current))
    }

    private fun reloadSpinner(select: String) {
        loading = true
        val names = ProfileStore.names(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        val idx = names.indexOf(select).coerceAtLeast(0)
        spinner.setSelection(idx)
        current = names[idx]
        ProfileStore.setActive(this, current)
        edit.setText(ProfileStore.text(this, current))
        loading = false
    }

    private fun saveCurrent() {
        if (current.isEmpty()) return
        ProfileStore.save(this, current, edit.text.toString())
        ProfileStore.setActive(this, current)
    }

    private fun newProfile() {
        val input = EditText(this).apply { hint = "Profile name, e.g. Farmer 1" }
        AlertDialog.Builder(this)
            .setTitle("New profile")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isNotEmpty()) {
                    saveCurrent()
                    ProfileStore.save(this, n, "")
                    reloadSpinner(n)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCurrent() {
        AlertDialog.Builder(this)
            .setTitle("Delete \"$current\"?")
            .setPositiveButton("Delete") { _, _ ->
                ProfileStore.delete(this, current)
                current = ""
                reloadSpinner(ProfileStore.active(this))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onPause() {
        saveCurrent()
        super.onPause()
    }
}
