data class SpinItem(
    val id: UUID = UUID.randomUUID(),
    val type: SpinItemType,
    private var _content: String, // Private backing property
    var backgroundColor: Int? = null
) {
    var content: String
        get() = _content
        set(value) {
            _content = if (value.length <= 27) {
                value
            } else {
                value.substring(0, 27) // Truncate to 27 characters
            }
        }

    init {
        content = _content // Enforce limit during initialization
    }
}