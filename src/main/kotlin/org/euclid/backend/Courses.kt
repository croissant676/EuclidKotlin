package org.euclid.backend

import java.util.Date
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.DocumentReference
import org.springframework.data.mongodb.repository.MongoRepository

data class Course(
    @Id
    val id: SimpleID = id(),
    @DocumentReference
    val creator: User,
    @DocumentReference
    val users: Set<CourseUser>,

    ) {
    // To prevent infinite recursion
    override fun equals(other: Any?) = other != null && other is Course && other.id == id
    override fun hashCode(): Int = System.identityHashCode(id)
    override fun toString(): String = "Course $id"
}

interface CourseRepository : MongoRepository<Course, SimpleID>

data class CourseUser(
    @DocumentReference
    val course: Course,
    @DocumentReference
    val user: User,

    )

abstract class CourseItem(
    @Id
    val id: SimpleID,
    val name: String,
    val creationDate: Date,
    @DocumentReference
    val creator: User,
    val type: String,
    @DocumentReference
    val parent: CourseItem?
)