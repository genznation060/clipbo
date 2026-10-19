package com.clipvault.smartclipboard.keyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.clipvault.smartclipboard.R
import com.clipvault.smartclipboard.app
import com.clipvault.smartclipboard.data.db.CategoryEntity
import com.clipvault.smartclipboard.data.prefs.KeyboardSize
import com.clipvault.smartclipboard.data.prefs.NotesOrder
import com.clipvault.smartclipboard.data.prefs.UserPreferences
import com.clipvault.smartclipboard.databinding.KeyboardImeBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/**
 * Clip tray above a compact QWERTY [KeyboardView].
 * Tapping a note commits its body and, when the preference is on, returns to
 * the previously selected input method.
 */
@Suppress("DEPRECATION")
class ClipboardKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val categoryId = MutableStateFlow<Long?>(null)
    private val order = MutableStateFlow(NotesOrder.MANUAL)

    private var binding: KeyboardImeBinding? = null
    private var categories: List<CategoryEntity> = emptyList()
    private val adapter = KeyboardNoteAdapter { note -> commitNote(note.body) }

    override fun onCreate() {
        super.onCreate()
        order.value = UserPreferences(this).notesOrder()
        scope.launch {
            app().repository.observeCategories().collect { list ->
                categories = list
                if (categoryId.value != null && list.none { it.id == categoryId.value }) {
                    categoryId.value = null
                }
                renderCategories()
            }
        }
        scope.launch {
            categoryId.flatMapLatest { id ->
                order.flatMapLatest { sort ->
                    app().repository.observeKeyboardNotes(id, sort)
                }
            }.collect { notes ->
                adapter.submitList(notes)
                binding?.emptyKeyboard?.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onCreateInputView(): View {
        val inflated = KeyboardImeBinding.inflate(layoutInflater)
        binding = inflated
        inflated.notesList.layoutManager = LinearLayoutManager(this)
        inflated.notesList.adapter = adapter
        applySize(UserPreferences(this).keyboardSize(), inflated)
        val keyboard = Keyboard(this, R.xml.keyboard_qwerty)
        inflated.keyboardView.keyboard = keyboard
        inflated.keyboardView.setOnKeyboardActionListener(this)
        inflated.keyboardView.isPreviewEnabled = false
        inflated.sortButton.setOnClickListener { cycleSort() }
        inflated.settingsButton.setOnClickListener {
            // method.xml also points at SettingsActivity; this is the in-IME shortcut.
            val intent = android.content.Intent(this, com.clipvault.smartclipboard.ui.settings.SettingsActivity::class.java)
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        renderCategories()
        return inflated.root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        binding?.let { applySize(UserPreferences(this).keyboardSize(), it) }
    }

    override fun onDestroy() {
        scope.cancel()
        binding = null
        super.onDestroy()
    }

    private fun commitNote(body: String) {
        currentInputConnection?.commitText(body, 1)
        if (UserPreferences(this).keyboardAutoSwitch()) {
            switchToPreviousInputMethod()
        }
    }

    private fun cycleSort() {
        order.value = when (order.value) {
            NotesOrder.MANUAL -> NotesOrder.NEWEST
            NotesOrder.NEWEST -> NotesOrder.OLDEST
            NotesOrder.OLDEST -> NotesOrder.ALPHA
            NotesOrder.ALPHA -> NotesOrder.MANUAL
        }
        binding?.sortButton?.contentDescription = getString(R.string.sort_cd, order.value.name.lowercase())
    }

    private fun renderCategories() {
        val row = binding?.categoryRow ?: return
        row.removeAllViews()
        val all = chip(getString(R.string.category_all), categoryId.value == null) { categoryId.value = null }
        row.addView(all)
        categories.forEach { category ->
            row.addView(
                chip(category.name, categoryId.value == category.id) {
                    categoryId.value = category.id
                },
            )
        }
    }

    private fun chip(label: String, selected: Boolean, onClick: () -> Unit): View {
        val chip = com.google.android.material.chip.Chip(this)
        chip.text = label
        chip.isCheckable = true
        chip.isChecked = selected
        chip.setOnClickListener {
            onClick()
            renderCategories()
        }
        return chip
    }

    private fun applySize(size: KeyboardSize, target: KeyboardImeBinding) {
        val dp = when (size) {
            KeyboardSize.SMALL -> 120
            KeyboardSize.NORMAL -> 200
            KeyboardSize.LARGE -> 320
        }
        val px = (dp * resources.displayMetrics.density).toInt()
        target.notesList.layoutParams = target.notesList.layoutParams.apply { height = px }
            ?: LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px)
        target.notesList.layoutParams.height = px
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            Keyboard.KEYCODE_DONE, 10 -> {
                ic.commitText("\n", 1)
            }
            else -> ic.commitText(primaryCode.toChar().toString(), 1)
        }
    }

    override fun onPress(primaryCode: Int) = Unit
    override fun onRelease(primaryCode: Int) = Unit
    override fun onText(text: CharSequence?) {
        if (!text.isNullOrEmpty()) currentInputConnection?.commitText(text, 1)
    }
    override fun swipeLeft() = Unit
    override fun swipeRight() = Unit
    override fun swipeDown() = Unit
    override fun swipeUp() = Unit
}
