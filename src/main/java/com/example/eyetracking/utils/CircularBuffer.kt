package com.example.eyetracking.utils

/**
 * A fixed-size circular buffer implementation for efficient memory management.
 * This prevents unbounded growth of lists during calibration.
 * 
 * @param T The type of elements stored in the buffer
 * @param capacity The maximum number of elements the buffer can hold
 */
class CircularBuffer<T>(private val capacity: Int) {
    private val buffer = ArrayList<T>(capacity)
    private var writePos = 0
    private var size = 0
    
    /**
     * Add an element to the buffer. If the buffer is full,
     * the oldest element will be overwritten.
     */
    fun add(element: T) {
        if (size < capacity) {
            buffer.add(element)
            size++
        } else {
            buffer[writePos] = element
        }
        writePos = (writePos + 1) % capacity
    }
    
    /**
     * Add all elements from a collection
     */
    fun addAll(elements: Collection<T>) {
        elements.forEach { add(it) }
    }
    
    /**
     * Get all elements in the buffer in the order they were added
     */
    fun getAll(): List<T> {
        if (size < capacity) {
            return buffer.toList()
        }
        
        // Return elements in correct order when buffer has wrapped
        val result = ArrayList<T>(capacity)
        var readPos = writePos
        for (i in 0 until size) {
            result.add(buffer[readPos])
            readPos = (readPos + 1) % capacity
        }
        return result
    }
    
    /**
     * Get all elements as an array (for compatibility with existing code)
     */
    fun toList(): List<T> = getAll()
    
    /**
     * Clear all elements from the buffer
     */
    fun clear() {
        buffer.clear()
        writePos = 0
        size = 0
    }
    
    /**
     * Get the current number of elements in the buffer
     */
    fun size(): Int = size
    
    /**
     * Check if the buffer is empty
     */
    fun isEmpty(): Boolean = size == 0
    
    /**
     * Check if the buffer is at full capacity
     */
    fun isFull(): Boolean = size == capacity
    
    /**
     * Get the capacity of the buffer
     */
    fun capacity(): Int = capacity
}
