package com.yunjin.wanttoreadoriginals

import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var ink: InkOverlayView
    private lateinit var dictPopup: PopupWindow
    private val io = Executors.newSingleThreadExecutor()
    private val dictionary = DictionaryClient()
    private var activePdf: File? = null
    private var activeInk: File? = null
    private var pdfFragment: TextSelectablePdfFragment? = null
    private var latestSelectedText: String = ""
    private var lastStylusButtonUp = 0L

    private val openPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pdf = copyIntoLibrary(uri)
        openPdfFromLibrary(pdf)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        showLibrary()
    }

    override fun onPause() {
        super.onPause()
        saveInk()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return super.dispatchKeyEvent(event)

        // Primary Samsung Air Actions path: @xml/remote_actions maps S Pen double click to CTRL_LEFT+D.
        val isRemoteActionDictionary = event.keyCode == KeyEvent.KEYCODE_D && event.isCtrlPressed
        // Fallback for firmware that surfaces stylus side-button keycodes directly.
        val isPossibleRawStylusButton = event.keyCode in setOf(308, 309, KeyEvent.KEYCODE_BUTTON_1)

        if (isRemoteActionDictionary || isPossibleRawStylusButton) {
            if (isPossibleRawStylusButton) {
                val now = System.currentTimeMillis()
                val isDouble = now - lastStylusButtonUp < 360
                lastStylusButtonUp = now
                if (!isDouble) return true
            }
            openDictionaryFromSelectionOrClipboard()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun buildUi() {
        root = FrameLayout(this)
        val vertical = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8)
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(245, 245, 245))
        }
        content = FrameLayout(this).apply { id = View.generateViewId() }
        ink = InkOverlayView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            onTwoFingerDoubleTap = { undoAndSave() }
        }
        content.addView(ink, FrameLayout.LayoutParams(-1, -1))
        vertical.addView(toolbar, LinearLayout.LayoutParams(-1, dp(52)))
        vertical.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(vertical)
        setContentView(root)
        rebuildToolbar()
    }

    private fun rebuildToolbar() {
        toolbar.removeAllViews()
        button("열기") { openPdf.launch(arrayOf("application/pdf")) }
        button("라이브러리") { showLibrary() }
        button("펜") { ink.tool = InkOverlayView.Tool.PEN }
        button("형광") { ink.tool = InkOverlayView.Tool.HIGHLIGHTER }
        button("지우개") { ink.tool = InkOverlayView.Tool.ERASER }
        button("사전") { openDictionaryFromSelectionOrClipboard() }
        button("되돌리기") { undoAndSave() }
        button("내보내기") { exportCurrentPdfNotice() }
        colorButton("흰", Color.WHITE); colorButton("검", Color.BLACK); colorButton("파", Color.BLUE)
        colorButton("빨", Color.RED); colorButton("초", Color.rgb(0,160,0)); colorButton("노", Color.YELLOW)
        val seek = SeekBar(this).apply {
            max = 30; progress = 6
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { ink.strokeWidthPx = (p + 2).toFloat() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        toolbar.addView(seek, LinearLayout.LayoutParams(dp(150), -1))
    }

    private fun button(label: String, block: () -> Unit) {
        toolbar.addView(Button(this).apply { text = label; textSize = 12f; setOnClickListener { block() } }, LinearLayout.LayoutParams(-2, -1))
    }
    private fun colorButton(label: String, c: Int) { button(label) { ink.color = c } }

    private fun openDictionaryFromSelectionOrClipboard() {
        val selected = pdfFragment?.currentSelectedText().orEmpty().ifBlank { latestSelectedText }
        val clip = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        val word = selected.ifBlank { clip }.trim()
        if (word.isBlank()) {
            Toast.makeText(this, "단어를 선택한 다음 S펜 버튼을 두 번 눌러줘", Toast.LENGTH_SHORT).show()
            return
        }
        lookupAndShow(word)
    }

    private fun lookupAndShow(word: String) {
        io.execute {
            val result = try { dictionary.lookup(word) } catch (t: Throwable) { "사전 오류: ${t.message}" }
            runOnUiThread { showDictionaryPopup(result) }
        }
    }

    private fun showDictionaryPopup(text: String) {
        if (::dictPopup.isInitialized && dictPopup.isShowing) dictPopup.dismiss()
        val box = TextView(this).apply {
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.rgb(255, 255, 230))
            textSize = 15f
            setPadding(dp(14))
            this.text = text
        }
        dictPopup = PopupWindow(box, dp(360), WindowManager.LayoutParams.WRAP_CONTENT, true).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            showAtLocation(root, Gravity.CENTER, 0, 0)
        }
    }

    private fun openPdfFromLibrary(pdf: File) {
        saveInk()
        activePdf = pdf
        activeInk = File(pdf.parentFile, pdf.nameWithoutExtension + ".ink.json")
        val frag = TextSelectablePdfFragment.create(Uri.fromFile(pdf).toString()).also { f ->
            f.onSelectedText = { latestSelectedText = it }
        }
        pdfFragment = frag
        supportFragmentManager.beginTransaction()
            .replace(content.id, frag)
            .commitNowAllowingStateLoss()
        content.bringChildToFront(ink)
        ink.loadJson(activeInk?.takeIf { it.exists() }?.readText().orEmpty())
        Toast.makeText(this, "PDF 열림: ${pdf.name}", Toast.LENGTH_SHORT).show()
    }

    private fun copyIntoLibrary(uri: Uri): File {
        val name = queryDisplayName(uri).ifBlank { "book_${System.currentTimeMillis()}.pdf" }
            .replace(Regex("[^가-힣A-Za-z0-9._ -]"), "_")
        val dir = File(filesDir, "library/${name.removeSuffix(".pdf")}").apply { mkdirs() }
        val out = File(dir, name)
        contentResolver.openInputStream(uri)!!.use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return uri.lastPathSegment ?: "book.pdf"
    }

    private fun showLibrary() {
        val lib = File(filesDir, "library").apply { mkdirs() }
        val pdfs = lib.walkTopDown().filter { it.isFile && it.extension.lowercase() == "pdf" }.toList()
        val list = ListView(this)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, pdfs.map { it.parentFile?.name + " / " + it.name })
        list.setOnItemClickListener { _, _, pos, _ -> openPdfFromLibrary(pdfs[pos]) }
        content.removeAllViews()
        content.addView(list, FrameLayout.LayoutParams(-1, -1))
        content.addView(ink, FrameLayout.LayoutParams(-1, -1))
        Toast.makeText(this, if (pdfs.isEmpty()) "열기 버튼으로 PDF를 추가해줘" else "PDF를 선택해줘", Toast.LENGTH_SHORT).show()
    }

    private fun saveInk() {
        activeInk?.writeText(ink.toJson())
    }

    private fun undoAndSave() {
        ink.undo()
        saveInk()
    }

    private fun exportCurrentPdfNotice() {
        saveInk()
        Toast.makeText(this, "현재 버전은 원본 PDF + 필기 JSON 저장까지 구현됨. PDF로 굽기는 다음 파일에서 추가 예정", Toast.LENGTH_LONG).show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
