package nl.giejay.android.tv.immich.shared.util

import java.util.LinkedList

class SizeLimitedQueue<E>(private val limit: Int) : LinkedList<E>() {
    // Override the method add() available 
    // in LinkedList class so that it allow 
    // addition  of element in queue till 
    // queue size is less than 
    // SizeLimitOfQueue otherwise it remove 
    // the front element of queue and add 
    // new element 
    override fun add(element: E): Boolean {
        // If queue size become greater
        // than SizeLimitOfQueue then 
        // front of queue will be removed 
        while (this.size == limit) {
            super.remove()
        }
        super.add(element)
        return true
    }
}