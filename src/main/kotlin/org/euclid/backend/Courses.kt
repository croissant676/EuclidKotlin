package org.euclid.backend

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.Date
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.DocumentReference
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.hateoas.CollectionModel
import org.springframework.hateoas.EntityModel
import org.springframework.hateoas.server.mvc.linkTo
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

val courseLogger = log(Course::class)

data class Course(
    @Id
    val id: SimpleID = id(),
    @DocumentReference
    val creator: User,
    val users: MutableSet<CourseUser> = hashSetOf(),
    val name: String,
    val description: String
) {
    // To prevent infinite recursion
    override fun equals(other: Any?) = other != null && other is Course && other.id == id
    override fun hashCode(): Int = System.identityHashCode(id)
    override fun toString(): String = "Course $id"
}


@Repository
interface CourseRepository : MongoRepository<Course, SimpleID>

data class Role(
    val id: SimpleID,
    @DocumentReference
    val course: Course,
    val name: String,
    val color: String = "#ffffff",
    val priority: Int,
    val users: MutableSet<CourseUser>
)

data class CourseUser(
    @DocumentReference
    @JsonIgnore
    val course: Course,
    @DocumentReference
    @JsonIgnore
    val user: User,
    var nickname: String,

    ) {

}

@Repository
interface CourseItemRepository : MongoRepository<CourseItem, SimpleID>

abstract class CourseItem(
    @Id
    val id: SimpleID,
    @DocumentReference
    val course: Course,
    val name: String,
    val creationDate: Date,
    @DocumentReference
    val creator: User,
    val type: String,
    val visibleRole: Int,
    @DocumentReference
    val parent: CourseItem?,
    @DocumentReference
    val children: MutableSet<CourseItem> = hashSetOf()
)

@Document
class PageItem(
    id: SimpleID = id(),
    course: Course,
    name: String,
    creationDate: Date = Date(),
    creator: User,
    type: String = "Page",
    visibleRole: Int = -1000,
    parent: CourseItem? = null,
    children: MutableSet<CourseItem> = hashSetOf(),
    val text: TextContent = TextContent("/static/c_page/$id.md")
) : CourseItem(id, course, name, creationDate, creator, type, visibleRole, parent, children)

abstract class Submittable(
    id: SimpleID,
    course: Course,
    name: String,
    creationDate: Date,
    creator: User,
    type: String,
    visibleRole: Int,
    parent: CourseItem?,
    children: MutableSet<CourseItem>,
    var numberOfProblems: Int,

    var numberSubmissions: Int,
    var sumOfGrades: Double
) : CourseItem(id, course, name, creationDate, creator, type, visibleRole, parent, children)

abstract class Question(
    val text: TextContent,
    var points: Int
)

data class CreateCourseVerification(
    val id: SimpleID,
    val name: String,
    val description: String
)

@RestController
class CourseController(val courseRepository: CourseRepository, val userRepository: UserRepository) {
    @GetMapping("/internal/courses")
    fun allCourses(): CollectionModel<EntityModel<Course>> {
        return CollectionModel.of(courseRepository.findAll().map {
            EntityModel.of(
                it,
                linkTo<CourseController> { allCourses() }.withRel("all"),
                linkTo<CourseController> { singleCourse(it.id.toString()) }.withSelfRel()
            )
        })
    }

    @GetMapping("/internal/course/{id}")
    fun singleCourse(@PathVariable id: String): EntityModel<Course> {
        val courseId = id(id) ?: throw RuntimeException("Cannot parse id: $id")
        val course =
            courseRepository.findById(courseId).orElseThrow { NotFoundException(Course::class, "With id $id") }
        return EntityModel.of(
            course,
            linkTo<CourseController> { allCourses() }.withRel("all"),
            linkTo<CourseController> { singleCourse(course.id.toString()) }.withSelfRel()
        )
    }

    @PostMapping("/internal/course/create")
    fun createCourse(createCourseVerification: CreateCourseVerification, authentication: Authentication)
            : ResponseEntity<EntityModel<Course>> {
        val user = userRepository.findById(createCourseVerification.id).orElseThrow {
            NotFoundException(User::class, "With id ${createCourseVerification.id}")
        }
        val authUser = userRepository.findFirstByUsername(authentication.name).orElseThrow {
            NotFoundException(User::class, "With name ${authentication.name}")
        }
        if (user != authUser) return ResponseEntity(null, HttpStatus.FORBIDDEN)
        val course = Course(
            creator = user,
            name = createCourseVerification.name,
            description = createCourseVerification.description
        )
        course.users += CourseUser(course, user, user.displayName)
        user.courses += course
        return ResponseEntity.ok(
            EntityModel.of(
                course,
                linkTo<CourseController> { allCourses() }.withRel("all"),
                linkTo<CourseController> { singleCourse(course.id.toString()) }.withSelfRel()
            )
        )
    }
}