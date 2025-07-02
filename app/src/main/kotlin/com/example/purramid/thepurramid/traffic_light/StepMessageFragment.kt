// StepMessageFragment.kt
package com.example.purramid.thepurramid.traffic_light

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.example.purramid.thepurramid.R
import com.example.purramid.thepurramid.databinding.DialogStepMessageBinding
import com.example.purramid.thepurramid.traffic_light.viewmodel.MessageData
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar

class StepMessageFragment : DialogFragment() {

    private var _binding: DialogStepMessageBinding? = null
    private val binding get() = _binding!!
    
    private var currentMessage: MessageData = MessageData()
    private var onMessageSet: ((MessageData) -> Unit)? = null
    
    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handleImageSelected(it) }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogStepMessageBinding.inflate(LayoutInflater.from(context))
        
        arguments?.let {
            // Deserialize message if passed
            // For simplicity, we'll reconstruct from primitives
        }
        
        setupViews()
        
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Step Message")
            .setView(binding.root)
            .setPositiveButton("Set") { _, _ ->
                val finalMessage = currentMessage.copy(
                    text = binding.editTextMessage.text.toString()
                )
                onMessageSet?.invoke(finalMessage)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }
    
    private fun setupViews() {
        // Character limit
        binding.editTextMessage.filters = arrayOf(
            InputFilter.LengthFilter(MessageData.MAX_CHARACTERS)
        )
        
        // Update character count
        binding.editTextMessage.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val count = s?.length ?: 0
                binding.textCharacterCount.text = "$count/${MessageData.MAX_CHARACTERS}"
            }
        })
        
        // Initialize with current message
        binding.editTextMessage.setText(currentMessage.text)
        updateEmojiDisplay()
        updateImageDisplay()
        
        // Button listeners
        binding.buttonAddEmoji.setOnClickListener {
            showEmojiPicker()
        }
        
        binding.buttonAddImage.setOnClickListener {
            imagePicker.launch("image/*")
        }
        
        binding.buttonClearEmojis.setOnClickListener {
            currentMessage = currentMessage.copy(emojis = emptyList())
            updateEmojiDisplay()
        }
        
        binding.buttonClearImage.setOnClickListener {
            currentMessage = currentMessage.copy(imageUri = null)
            updateImageDisplay()
        }
    }
    
    private fun showEmojiPicker() {
        val emojis = listOf("😀", "😎", "🎉", "👍", "❤️", "⭐", "🔥", "✅", "🎯", "👏")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Emoji")
            .setItems(emojis.toTypedArray()) { _, which ->
                if (currentMessage.emojis.size < MessageData.MAX_EMOJIS) {
                    currentMessage = currentMessage.copy(
                        emojis = currentMessage.emojis + emojis[which]
                    )
                    updateEmojiDisplay()
                } else {
                    Snackbar.make(binding.root, "Maximum emojis reached", Snackbar.LENGTH_SHORT).show()
                }
            }
            .show()
    }
    
    private fun handleImageSelected(uri: Uri) {
        val size = getImageSize(uri)
        if (size > 3 * 1024 * 1024) { // 3MB limit
            Snackbar.make(
                binding.root,
                "Image too large. Maximum size is 3MB.",
                Snackbar.LENGTH_LONG
            ).show()
            return
        }
        
        currentMessage = currentMessage.copy(imageUri = uri)
        updateImageDisplay()
    }
    
    private fun updateEmojiDisplay() {
        val hasEmojis = currentMessage.emojis.isNotEmpty()
        binding.layoutEmojiDisplay.isVisible = hasEmojis
        binding.buttonClearEmojis.isVisible = hasEmojis
        
        if (hasEmojis) {
            binding.textEmojiDisplay.text = currentMessage.emojis.joinToString("")
            binding.textEmojiCount.text = "${currentMessage.emojis.size}/${MessageData.MAX_EMOJIS}"
        }
    }
    
    private fun updateImageDisplay() {
        val hasImage = currentMessage.imageUri != null
        binding.imagePreview.isVisible = hasImage
        binding.buttonClearImage.isVisible = hasImage
        
        currentMessage.imageUri?.let {
            binding.imagePreview.setImageURI(it)
        }
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
    
    fun setOnMessageSetListener(listener: (MessageData) -> Unit) {
        onMessageSet = listener
    }
    
    fun setInitialMessage(message: MessageData) {
        currentMessage = message
    }
    
    override fun onDestroyView() {
        super.
	
	override fun onDestroyView() {
       super.onDestroyView()
       _binding = null
   }
   
   companion object {
       const val TAG = "StepMessageDialog"
       
       fun newInstance(message: MessageData, onSet: (MessageData) -> Unit): StepMessageFragment {
           return StepMessageFragment().apply {
               setInitialMessage(message)
               setOnMessageSetListener(onSet)
           }
       }
   }
}