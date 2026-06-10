package com.yunjin.wanttoreadoriginals

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.selection.Selection
import androidx.pdf.selection.model.TextSelection
import androidx.pdf.view.PdfView
import androidx.pdf.viewer.fragment.PdfViewerFragment

class TextSelectablePdfFragment : PdfViewerFragment() {
    var onSelectedText: ((String) -> Unit)? = null
    var onPdfViewReady: ((PdfView) -> Unit)? = null
    var latestSelectionText: String = ""
        private set
    private var pdfViewRef: PdfView? = null

    @OptIn(ExperimentalPdfApi::class)
    override fun onPdfViewCreated(pdfView: PdfView) {
        super.onPdfViewCreated(pdfView)
        pdfViewRef = pdfView
        pdfView.addOnSelectionChangedListener { selection: Selection? ->
            val text = (selection as? TextSelection)?.text?.toString().orEmpty().trim()
            latestSelectionText = text
            if (text.isNotBlank()) onSelectedText?.invoke(text)
        }
        onPdfViewReady?.invoke(pdfView)
    }

    fun currentSelectedText(): String {
        val now = (pdfViewRef?.currentSelection as? TextSelection)?.text?.toString().orEmpty().trim()
        return now.ifBlank { latestSelectionText }
    }

    fun clearSelection() {
        pdfViewRef?.clearCurrentSelection()
    }

    companion object {
        fun create(uriString: String): TextSelectablePdfFragment {
            return TextSelectablePdfFragment().apply {
                arguments = Bundle().apply { putString("androidx.pdf.viewer.extra.PDF_URI", uriString) }
            }
        }
    }
}
