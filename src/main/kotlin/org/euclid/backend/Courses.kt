package org.euclid.backend

import java.util.UUID
import org.springframework.data.mongodb.core.mapping.DocumentReference

data class Course(
    val id: UUID,
    @DocumentReference
    val creator: User,
    @DocumentReference
    val users: Set<User>,
) {
    // To prevent infinite recursion
    override fun equals(other: Any?) = other != null && other is Course && other.id == id
    override fun hashCode(): Int = System.identityHashCode(id)
    override fun toString(): String = "Course $id"
}

