package com.example.purramid.thepurramid.traffic_light

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.FragmentAddMessagesBinding
import com.example.purramid.thepurramid.databinding.ItemMessageEditorBinding
import com.example.purramid.thepurramid.traffic_light.viewmodel.LightColor
import com.example.purramid.thepurramid.traffic_light.viewmodel.MessageData
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightMessages
import com.example.purramid.thepurramid.traffic_light.viewmodel.TrafficLightViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

class AddMessagesFragment : DialogFragment() {

    private var _binding: FragmentAddMessagesBinding? = null
    private val binding get() = _binding!!

    private lateinit var redMessageBinding: ItemMessageEditorBinding
    private lateinit var yellowMessageBinding: ItemMessageEditorBinding
    private lateinit var greenMessageBinding: ItemMessageEditorBinding

    private val viewModel: TrafficLightViewModel by activityViewModels()

    // Track temporary message states
    private var tempRedMessage = MessageData()
    private var tempYellowMessage = MessageData()
    private var tempGreenMessage = MessageData()

    // Image picker launchers
    private val imagePickerRed = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageSelected(LightColor.RED, it) }
    }

    private val imagePickerYellow = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageSelected(LightColor.YELLOW, it) }
    }

    private val imagePickerGreen = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageSelected(LightColor.GREEN, it) }
    }

    private var currentEmojiColor: LightColor? = null

    companion object {
        const val TAG = "AddMessagesDialog"
        private const val MAX_IMAGE_SIZE = 3 * 1024 * 1024 // 3MB

        fun newInstance(): AddMessagesFragment {
            return AddMessagesFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddMessagesBinding.inflate(inflater, container, false)

        redMessageBinding = ItemMessageEditorBinding.bind(binding.includeRedMessage.root)
        yellowMessageBinding = ItemMessageEditorBinding.bind(binding.includeYellowMessage.root)
        greenMessageBinding = ItemMessageEditorBinding.bind(binding.includeGreenMessage.root)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize temp messages from current state
        val currentMessages = viewModel.uiState.value.messages
        tempRedMessage = currentMessages.redMessage.copy()
        tempYellowMessage = currentMessages.yellowMessage.copy()
        tempGreenMessage = currentMessages.greenMessage.copy()

        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        // Set color indicators
        redMessageBinding.imageColorIndicator.apply {
            setImageResource(R.drawable.ic_circle_base)
            setColorFilter(0xFFFF0000.toInt(), PorterDuff.Mode.SRC_IN)
        }
        yellowMessageBinding.imageColorIndicator.apply {
            setImageResource(R.drawable.ic_circle_base)
            setColorFilter(0xFFFFFF00.toInt(), PorterDuff.Mode.SRC_IN)
        }
        greenMessageBinding.imageColorIndicator.apply {
            setImageResource(R.drawable.ic_circle_base)
            setColorFilter(0xFF00FF00.toInt(), PorterDuff.Mode.SRC_IN)
        }

        // Setup each message editor
        setupMessageEditor(redMessageBinding, LightColor.RED, imagePickerRed)
        setupMessageEditor(yellowMessageBinding, LightColor.YELLOW, imagePickerYellow)
        setupMessageEditor(greenMessageBinding, LightColor.GREEN, imagePickerGreen)

        // Save/Cancel buttons
        binding.buttonSaveMessages.setOnClickListener {
            saveMessages()
        }

        binding.buttonCancelMessages.setOnClickListener {
            dismiss()
        }
    }

    private fun setupMessageEditor(
        editorBinding: ItemMessageEditorBinding,
        color: LightColor,
        imagePicker: ActivityResultContracts.GetContent.() -> Unit
    ) {
        // Character limit
        editorBinding.editTextMessage.filters = arrayOf(
            InputFilter.LengthFilter(MessageData.MAX_CHARACTERS)
        )

        // Update character count
        editorBinding.editTextMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val count = s?.length ?: 0
                editorBinding.textCharacterCount.text = "$count/${MessageData.MAX_CHARACTERS}"

                // Update temp message
                updateTempMessageText(color, s.toString())
            }
        })

        // Emoji button
        editorBinding.buttonAddEmoji.setOnClickListener {
            currentEmojiColor = color
            showEmojiPicker()
        }

        // Image button
        editorBinding.buttonAddImage.setOnClickListener {
            when (color) {
                LightColor.RED -> imagePickerRed.launch("image/*")
                LightColor.YELLOW -> imagePickerYellow.launch("image/*")
                LightColor.GREEN -> imagePickerGreen.launch("image/*")
            }
        }

        // Clear image button
        editorBinding.buttonClearImage.setOnClickListener {
            clearImage(color)
        }

        // Initialize with current values
        val currentMessage = getTempMessage(color)
        editorBinding.editTextMessage.setText(currentMessage.text)
        updateEmojiDisplay(editorBinding, currentMessage)
        updateImageDisplay(editorBinding, currentMessage)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Only update if dialog is being opened fresh
                    if (!isResumed) {
                        tempRedMessage = state.messages.redMessage.copy()
                        tempYellowMessage = state.messages.yellowMessage.copy()
                        tempGreenMessage = state.messages.greenMessage.copy()

                        updateAllDisplays()
                    }
                }
            }
        }
    }

    private fun updateAllDisplays() {
        updateMessageDisplay(redMessageBinding, tempRedMessage)
        updateMessageDisplay(yellowMessageBinding, tempYellowMessage)
        updateMessageDisplay(greenMessageBinding, tempGreenMessage)
    }

    private fun updateMessageDisplay(
        editorBinding: ItemMessageEditorBinding,
        message: MessageData
    ) {
        if (editorBinding.editTextMessage.text.toString() != message.text) {
            editorBinding.editTextMessage.setText(message.text)
        }
        updateEmojiDisplay(editorBinding, message)
        updateImageDisplay(editorBinding, message)
    }

    private fun updateEmojiDisplay(
        editorBinding: ItemMessageEditorBinding,
        message: MessageData
    ) {
        editorBinding.textEmojiDisplay.text = message.emojis.joinToString("")
        editorBinding.textEmojiDisplay.isVisible = message.emojis.isNotEmpty()
        editorBinding.textEmojiCount.text = "${message.emojis.size}/${MessageData.MAX_EMOJIS}"
    }

    private fun updateImageDisplay(
        editorBinding: ItemMessageEditorBinding,
        message: MessageData
    ) {
        message.imageUri?.let { uri ->
            editorBinding.imagePreview.setImageURI(uri)
            editorBinding.imagePreview.isVisible = true
            editorBinding.buttonClearImage.isVisible = true
        } ?: run {
            editorBinding.imagePreview.isVisible = false
            editorBinding.buttonClearImage.isVisible = false
        }
    }

    private fun showEmojiPicker() {
        val emojis = listOf(
            "😀", "😎", "🎉", "👍", "❤️", "⭐", "🔥", "✅", "🎯", "👏",
            "🎈", "🎊", "💪", "🌟", "🏆", "💯", "👌", "🙌", "💫", "🎁"
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.select_emoji))
            .setItems(emojis.toTypedArray()) { _, which ->
                currentEmojiColor?.let { color ->
                    addEmoji(color, emojis[which])
                }
            }
            .show()
    }

    private fun addEmoji(color: LightColor, emoji: String) {
        val currentMessage = getTempMessage(color)

        if (currentMessage.emojis.size >= MessageData.MAX_EMOJIS) {
            Snackbar.make(binding.root, getString(R.string.max_emojis_reached), Snackbar.LENGTH_SHORT).show()
            return
        }

        val updatedMessage = currentMessage.copy(
            emojis = currentMessage.emojis + emoji
        )

        updateTempMessage(color, updatedMessage)
        updateMessageDisplay(getBindingForColor(color), updatedMessage)
    }

    private fun handleImageSelected(color: LightColor, uri: Uri) {
        // Check image size
        val size = getImageSize(uri)
        if (size > MAX_IMAGE_SIZE) {
            showImageOptimizeDialog(color, uri)
            return
        }

        val currentMessage = getTempMessage(color)
        val updatedMessage = currentMessage.copy(imageUri = uri)

        updateTempMessage(color, updatedMessage)
        updateMessageDisplay(getBindingForColor(color), updatedMessage)
    }

    private fun showImageOptimizeDialog(color: LightColor, uri: Uri) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.image_too_large_title)
            .setMessage(R.string.image_too_large_message)
            .setPositiveButton(R.string.optimize) { _, _ ->
                optimizeAndSaveImage(color, uri)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun optimizeAndSaveImage(color: LightColor, uri: Uri) {
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
            val optimized = compressBitmap(bitmap)

            // Save to internal storage
            val filename = "message_image_${color.name}_${System.currentTimeMillis()}.jpg"
            val file = requireContext().openFileOutput(filename, Activity.MODE_PRIVATE)
            file.use {
                optimized.compress(Bitmap.CompressFormat.JPEG, 85, it)
            }

            val internalUri = Uri.fromFile(requireContext().getFileStreamPath(filename))

            val currentMessage = getTempMessage(color)
            val updatedMessage = currentMessage.copy(imageUri = internalUri)

            updateTempMessage(color, updatedMessage)
            updateMessageDisplay(getBindingForColor(color), updatedMessage)

        } catch (e: Exception) {
            Snackbar.make(binding.root, R.string.compression_failed, Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun compressBitmap(bitmap: Bitmap): Bitmap {
        var quality = 100
        var streamLength = MAX_IMAGE_SIZE + 1
        val bmpStream = ByteArrayOutputStream()

        while (streamLength >= MAX_IMAGE_SIZE && quality > 5) {
            bmpStream.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bmpStream)
            streamLength = bmpStream.toByteArray().size
            quality -= 5
        }

        return bitmap
    }

    private fun clearImage(color: LightColor) {
        val currentMessage = getTempMessage(color)
        val updatedMessage = currentMessage.copy(imageUri = null)

        updateTempMessage(color, updatedMessage)
        updateMessageDisplay(getBindingForColor(color), updatedMessage)
    }

    private fun getTempMessage(color: LightColor): MessageData {
        return when (color) {
            LightColor.RED -> tempRedMessage
            LightColor.YELLOW -> tempYellowMessage
            LightColor.GREEN -> tempGreenMessage
        }
    }

    private fun updateTempMessage(color: LightColor, message: MessageData) {
        when (color) {
            LightColor.RED -> tempRedMessage = message
            LightColor.YELLOW -> tempYellowMessage = message
            LightColor.GREEN -> tempGreenMessage = message
        }
    }

    private fun updateTempMessageText(color: LightColor, text: String) {
        when (color) {
            LightColor.RED -> tempRedMessage = tempRedMessage.copy(text = text)
            LightColor.YELLOW -> tempYellowMessage = tempYellowMessage.copy(text = text)
            LightColor.GREEN -> tempGreenMessage = tempGreenMessage.copy(text = text)
        }
    }

    private fun getBindingForColor(color: LightColor): ItemMessageEditorBinding {
        return when (color) {
            LightColor.RED -> redMessageBinding
            LightColor.YELLOW -> yellowMessageBinding
            LightColor.GREEN -> greenMessageBinding
        }
    }

    private fun saveMessages() {
        val messages = TrafficLightMessages(
            redMessage = tempRedMessage,
            yellowMessage = tempYellowMessage,
            greenMessage = tempGreenMessage
        )

        viewModel.updateMessages(messages)
        viewModel.saveState()
        dismiss()
    }

    private fun getImageSize(uri: Uri): Long {
        return try {
            requireContext().contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}