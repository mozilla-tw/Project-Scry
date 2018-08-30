package org.mozilla.scryer.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.support.v4.content.ContextCompat
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.app.AlertDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.experimental.DefaultDispatcher
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import org.mozilla.scryer.R
import org.mozilla.scryer.persistence.CollectionModel
import org.mozilla.scryer.viewmodel.ScreenshotViewModel

class CollectionNameDialog(private val context: Context,
                           collections: List<CollectionModel>,
                           private val delegate: Delegate) {

    companion object {
        fun createNewCollection(context: Context, viewModel: ScreenshotViewModel,
                                callback: (collection: CollectionModel) -> Unit) {
            launch(UI) {
                createNewCollection(context, viewModel, getCollectionList(viewModel), callback)
            }
        }

        fun renameCollection(context: Context, viewModel: ScreenshotViewModel, collectionId: String?) {
            launch(UI) {
                val collections = getCollectionList(viewModel)
                collections.find { it.id == collectionId }?.let {
                    renameCollection(context, viewModel, it, collections)
                }
            }
        }

        private fun createNewCollection(context: Context, viewModel: ScreenshotViewModel,
                                        collections: List<CollectionModel>,
                                        callback: (collection: CollectionModel) -> Unit) {
            val dialog = CollectionNameDialog(context, collections, object : CollectionNameDialog.Delegate {
                override fun onPositiveAction(dialog: CollectionNameDialog.Interface) {
                    val color = findColorForNewCollection(context, collections)
                    val model = CollectionModel(dialog.getInputText(), System.currentTimeMillis(), color)
                    viewModel.addCollection(model)
                    callback(model)
                }

                override fun onNegativeAction(dialog: CollectionNameDialog.Interface) {}
            })

            dialog.title = context.resources.getText(R.string.dialogue_title_collection).toString()
            dialog.show()
        }

        private fun renameCollection(context: Context, viewModel: ScreenshotViewModel,
                                     collection: CollectionModel,
                                     collections: List<CollectionModel>) {

            val dialog = CollectionNameDialog(context, collections, object : CollectionNameDialog.Delegate {
                override fun onPositiveAction(dialog: CollectionNameDialog.Interface) {
                    collection.name = dialog.getInputText()
                    viewModel.updateCollection(collection)
                }

                override fun onNegativeAction(dialog: CollectionNameDialog.Interface) {}
            })

            dialog.initialCollectionName = collection.name
            dialog.title = context.resources.getText(R.string.dialogue_rename_title_rename).toString()
            dialog.show()
        }

        private fun findColorForNewCollection(context: Context, collections: List<CollectionModel>): Int {
            val lastColor = collections.last().color
            val defaultColor = ContextCompat.getColor(context, R.color.primaryTeal)

            var newColorIndex = 0
            val typedArray = context.resources.obtainTypedArray(R.array.collection_colors)
            val length = typedArray.length()

            for (i in 0 until length) {
                if (typedArray.getColor(i, defaultColor) == lastColor) {
                    newColorIndex = (i + 1) % length
                    break
                }
            }

            val color = typedArray.getColor(newColorIndex, defaultColor)
            typedArray.recycle()

            return color
        }

        private suspend fun getCollectionList(viewModel: ScreenshotViewModel): List<CollectionModel> {
            return withContext(DefaultDispatcher) {
                viewModel.getCollectionList()
            }
        }
    }

    var dialog: AlertDialog
    var title: String = ""

    private var editText: EditText
    private var dialogInterface: Interface

    var initialCollectionName = ""

    val validIcon: Drawable? by lazy {
        null
    }

    val invalidIcon: Drawable? by lazy {
        ContextCompat.getDrawable(context, R.drawable.error)?.let {
            val wrapped = DrawableCompat.wrap(it)
            DrawableCompat.setTint(wrapped, ContextCompat.getColor(context, R.color.errorRed))
            wrapped
        }
    }

    init {
        val view = View.inflate(context, R.layout.dialog_collection_name, null)
        val titleText = view.findViewById<TextView>(R.id.title)
        val editTextBar = view.findViewById<View>(R.id.edit_text_bar)

        val errorText = view.findViewById<TextView>(R.id.error_text)
        val errorIcon = view.findViewById<View>(R.id.edit_text_icon)

        editText = view.findViewById(R.id.edit_text)
        dialogInterface = object : Interface {
            override fun getInputText(): String {
                return editText.text.toString()
            }
        }

        dialog = AlertDialog.Builder(context, R.style.Theme_AppCompat_Light_Dialog_Alert)
                .setPositiveButton(R.string.dialogue_action_add) { _, _ ->
                    if (dialogInterface.getInputText() == initialCollectionName) {
                        delegate.onNegativeAction(dialogInterface)
                    } else {
                        delegate.onPositiveAction(dialogInterface)
                    }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    delegate.onNegativeAction(dialogInterface)
                }
                .setView(view)
                .create()

        val validator = InputValidator(context, object : InputValidator.ViewDelegate {
            override fun forbidContinue(forbid: Boolean) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !forbid
            }

            override fun onErrorStatusUpdate(errorMsg: String) {
                errorIcon.background = if (errorMsg.isEmpty()) { validIcon } else { invalidIcon }
                errorText.text = errorMsg

                editTextBar.setBackgroundColor(if (errorMsg.isEmpty()) {
                    ContextCompat.getColor(context, R.color.primaryTeal)
                } else {
                    ContextCompat.getColor(context, R.color.errorRed)
                })
            }

            override fun isCollectionExist(name: String): Boolean {
                return name.compareTo(initialCollectionName, true) != 0 &&
                        collections.any { name.compareTo(it.name, true) == 0 }
            }
        })

        dialog.setOnShowListener {
            titleText.text = title
            if (initialCollectionName.isNotEmpty()) {
                editText.setText(initialCollectionName)
                editText.setSelection(0, initialCollectionName.length)
            }
            validator.validate(dialogInterface.getInputText())
            val colors = ContextCompat.getColorStateList(context, R.color.primary_text_button)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(colors)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(colors)
        }

        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validator.validate(s.toString())
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    fun show() {
        showImmediately()
    }

    private fun showImmediately() {
        dialog.show()
        editText.requestFocus()
        dialog.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    class InputValidator(private val context: Context, private val viewDelegate: ViewDelegate) {
        private val lengthLimit = context.resources.getInteger(R.integer.collection_name_dialog_max_input_length)

        fun validate(input: String) {
            when {
                input.isEmpty() -> {
                    viewDelegate.forbidContinue(true)
                }

                input.length > lengthLimit -> {
                    viewDelegate.forbidContinue(true)
                    viewDelegate.onErrorStatusUpdate(context.getString(R.string.dialogue_rename_error_maximum))
                }

                viewDelegate.isCollectionExist(input) -> {
                    viewDelegate.forbidContinue(true)
                    viewDelegate.onErrorStatusUpdate(context.getString(R.string.dialogue_rename_error_duplicate))
                }

                else -> {
                    viewDelegate.forbidContinue(false)
                    viewDelegate.onErrorStatusUpdate("")
                }
            }
        }

        interface ViewDelegate {
            fun forbidContinue(forbid: Boolean)
            fun onErrorStatusUpdate(errorMsg: String)
            fun isCollectionExist(name: String): Boolean
        }
    }

    interface Interface {
        fun getInputText(): String
    }

    interface Delegate {
        fun onPositiveAction(dialog: Interface)
        fun onNegativeAction(dialog: Interface)
    }
}
