// AddMessagesFragment.kt
package com.example.purramid.thepurramid.traffic_light

import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
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

class AddMessagesFragment : DialogFragment() {

    private var _binding: FragmentAddMessagesBinding? = null
    private val binding get() = _binding!!

    private lateinit var redMessageBinding: ItemMessageEditorBinding
    private lateinit var yellowMessageBinding: ItemMessageEditorBinding
    private lateinit var greenMessageBinding: ItemMessageEditorBinding

    private val viewModel: TrafficLightViewModel by activityViewModels()

    // Image picker for each color
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

    // Emoji picker result (simplified - in real app would use emoji picker library)
    private var currentEmojiColor: LightColor? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddMessagesBinding.inflate(inflater, container, false)

        // Bind included layouts
        redMessageBinding = ItemMessageEditorBinding.bind(binding.includeRedMessage.root)
        yellowMessageBinding = ItemMessageEditorBinding.bind(binding.includeYellowMessage.root)
        greenMessageBinding = ItemMessageEditorBinding.bind(binding.includeGreenMessage.root)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews()
        observeViewModel()
    }

    private fun setupViews() {
        // Set color indicators using the base drawable with tint
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
        imagePicker: ActivityResultLauncher<String>
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
            }
        })

        // Emoji button
        editorBinding.buttonAddEmoji.setOnClickListener {
            currentEmojiColor = color
            showEmojiPicker()
        }

        // Image button
        editorBinding.buttonAddImage.setOnClickListener {
            imagePicker.launch("image/*")
        }

        // Clear image button
        editorBinding.buttonClearImage.setOnClickListener {
            clearImage(color)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateMessageDisplays(state.messages)
                }
            }
        }
    }

    private fun updateMessageDisplays(messages: TrafficLightMessages) {
        updateSingleMessageDisplay(redMessageBinding, messages.redMessage)
        updateSingleMessageDisplay(yellowMessageBinding, messages.yellowMessage)
        updateSingleMessageDisplay(greenMessageBinding, messages.greenMessage)
    }

    private fun updateSingleMessageDisplay(
        editorBinding: ItemMessageEditorBinding,
        message: MessageData
    ) {
        // Set text
        if (editorBinding.editTextMessage.text.toString() != message.text) {
            editorBinding.editTextMessage.setText(message.text)
        }

        // Show emojis
        editorBinding.textEmojiDisplay.text = message.emojis.joinToString("")
        editorBinding.textEmojiDisplay.isVisible = message.emojis.isNotEmpty()
        editorBinding.textEmojiCount.text = "${message.emojis.size}/${MessageData.MAX_EMOJIS}"

        // Show image
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
        // Simplified emoji picker - in production use emoji picker library
        val emojis = listOf("😀", "😎", "🎉", "👍", "❤️", "⭐", "🔥", "✅", "🎯", "👏")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Emoji")
            .setItems(emojis.toTypedArray()) { _, which ->
                currentEmojiColor?.let { color ->
                    addEmoji(color, emojis[which])
                }
            }
            .show()
    }

    private fun addEmoji(color: LightColor, emoji: String) {
        val currentMessages = viewModel.uiState.value.messages
        val currentMessage = currentMessages.getMessageForColor(color)

        if (currentMessage.emojis.size >= MessageData.MAX_EMOJIS) {
            Snackbar.make(binding.root, "Maximum emojis reached", Snackbar.LENGTH_SHORT).show()
            return
        }

        val updatedMessage = currentMessage.copy(
            emojis = currentMessage.emojis + emoji
        )

        updateMessageForColor(color, updatedMessage)
    }

    private fun handleImageSelected(color: LightColor, uri: Uri) {
        // Check image size
        val size = getImageSize(uri)
        if (size > 3 * 1024 * 1024) { // 3MB limit
            Snackbar.make(
                binding.root,
                getString(R.string.image_too_large_message),
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        val currentMessages = viewModel.uiState.value.messages
        val currentMessage = currentMessages.getMessageForColor(color)

        val updatedMessage = currentMessage.copy(imageUri = uri)
        updateMessageForColor(color, updatedMessage)
    }

    private fun clearImage(color: LightColor) {
        val currentMessages = viewModel.uiState.value.messages
        val currentMessage = currentMessages.getMessageForColor(color)

        val updatedMessage = currentMessage.copy(imageUri = null)
        updateMessageForColor(color, updatedMessage)
    }

    private fun updateMessageForColor(color: LightColor, message: MessageData) {
        val currentMessages = viewModel.uiState.value.messages
        val updatedMessages = when (color) {
            LightColor.RED -> currentMessages.copy(redMessage = message)
            LightColor.YELLOW -> currentMessages.copy(yellowMessage = message)
            LightColor.GREEN -> currentMessages.copy(greenMessage = message)
        }

        viewModel.updateMessages(updatedMessages)
    }

    private fun saveMessages() {
        // Collect all message data
        val redMessage = MessageData(
            text = redMessageBinding.editTextMessage.text.toString(),
            emojis = viewModel.uiState.value.messages.redMessage.emojis,
            imageUri = viewModel.uiState.value.messages.redMessage.imageUri
        )

        val yellowMessage = MessageData(
            text = yellowMessageBinding.editTextMessage.text.toString(),
            emojis = viewModel.uiState.value.messages.yellowMessage.emojis,
            imageUri = viewModel.uiState.value.messages.yellowMessage.imageUri
        )

        val greenMessage = MessageData(
            text = greenMessageBinding.editTextMessage.text.toString(),
            emojis = viewModel.uiState.value.messages.greenMessage.emojis,
            imageUri = viewModel.uiState.value.messages.greenMessage.imageUri
        )

        val messages = TrafficLightMessages(redMessage, yellowMessage, greenMessage)

        viewModel.updateMessages(messages)
        viewModel.saveState() // Trigger save
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

    companion object {
        const val TAG = "AddMessagesDialog"
        fun newInstance(): AddMessagesFragment {
            return AddMessagesFragment()
        }
    }
}