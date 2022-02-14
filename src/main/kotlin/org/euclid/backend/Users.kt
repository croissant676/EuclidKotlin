package org.euclid.backend

import com.nimbusds.oauth2.sdk.util.date.SimpleDate
import java.util.Optional
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.mapping.DocumentReference
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.hateoas.CollectionModel
import org.springframework.hateoas.EntityModel
import org.springframework.hateoas.server.mvc.linkTo
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsPasswordService
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController

val log = log(User::class)

data class User(
    val id: SimpleID = id(),
    var displayName: String,
    var username: String,
    var password: String,
    var email: String,
    @DocumentReference
    val courses: Set<Course> = HashSet(),
    val birthday: SimpleDate,
    var locked: Boolean = false,
    var credentialsExpired: Boolean = false,
    var accountExpired: Boolean = false,
    var enabled: Boolean = true,
    val authorities: Set<String> = setOf("ROLE_USER")
) {
    // To prevent infinite recursion
    override fun equals(other: Any?) = other != null && other is User && other.id == id
    override fun hashCode(): Int = System.identityHashCode(id)
    override fun toString(): String = "User $id"
}

@Configuration
class UserConfiguration(val userService: UserService) : WebSecurityConfigurerAdapter() {
    override fun configure(auth: AuthenticationManagerBuilder) {
        auth.userDetailsService(userService)
            .userDetailsPasswordManager(userService)
    }
    override fun configure(http: HttpSecurity) {
        http.authorizeRequests()
            .antMatchers(
                "/login/",
                "/stats."
            ).permitAll()
            .antMatchers(
                "/home/**",
                "/courses/**",
                "/course/**",
                "/assessment/**",
                "/assignment/**",
                "/org/**",
                "/page/**",
                "/item/**",
                "/external/**",
                "/content/**",
                "/internal/**"
            ).hasRole("USER")
            .antMatchers(
                "/admin/",
                "/actuator"
            ).hasRole("ADMIN")
            .and()
            .formLogin()
            .successForwardUrl("/dashboard/**")
    }
}

@Repository
interface UserRepository : MongoRepository<User, SimpleID> {
    fun findFirstByUsername(username: String): Optional<User>
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
}

@Component
class UserService(val userRepository: UserRepository, val passwordEncoder: PasswordEncoder) : UserDetailsService,
    UserDetailsPasswordService {
    data class WrapperUserDetails(val user: User) : UserDetails {
        override fun getAuthorities(): MutableCollection<out GrantedAuthority> =
            user.authorities.map { SimpleGrantedAuthority("ROLE_$it") }.toMutableList()

        override fun getPassword(): String = user.password
        override fun getUsername() = user.username
        override fun isAccountNonExpired() = !user.accountExpired
        override fun isAccountNonLocked() = !user.locked
        override fun isCredentialsNonExpired() = !user.credentialsExpired
        override fun isEnabled() = user.enabled
    }

    override fun loadUserByUsername(username: String): UserDetails {
        val optional = userRepository.findFirstByUsername(username)
        log("Euclid Test >> Loading $username")
        return WrapperUserDetails(optional.orElseThrow { UsernameNotFoundException("Could not find username: $username") })
    }

    override fun updatePassword(details: UserDetails, newPassword: String): UserDetails {
        if (details !is WrapperUserDetails) return details
        log("Euclid Test >> Loading $details to change password to $newPassword")
        details.user.password = passwordEncoder.encode(newPassword)
        return details
    }
}

data class CreateUserObject(
    val name: String,
    val password: String,
    val email: String,
    val birthday: SimpleDate
)

data class DeleteVerification(
    val id: SimpleID,
    val password: String,
    val email: String
)

@RestController
class UserController(val userRepository: UserRepository, val passwordEncoder: PasswordEncoder) {
    @GetMapping("/internal/users")
    fun allUsers(): CollectionModel<EntityModel<User>> = CollectionModel.of(
        userRepository.findAll().map {
            EntityModel.of(
                it,
                linkTo<UserController> { singleUser(it.id.toString()) }.withSelfRel()
            )
        },
        linkTo<UserController> { allUsers() }.withRel("all"),
    )

    @GetMapping("/internal/user/{id}")
    fun singleUser(@PathVariable id: String): EntityModel<User>? {
        val simpleID = id(id) ?: kotlin.run {
            log.warn("Could not decode id: $id, discarding request")
            return null
        }
        return EntityModel.of(
            userRepository.findById(simpleID)
                .orElseThrow { IllegalArgumentException("Could not find user with id: $id") },
            linkTo<UserController> { allUsers() }.withRel("all"),
            linkTo<UserController> { singleUser(simpleID.toString()) }.withSelfRel()
        )
    }

    @PostMapping("/internal/users/create")
    fun createUser(createUser: CreateUserObject): ResponseEntity<*> {
        if (userRepository.existsByUsername(createUser.name)) {
            return ResponseEntity("Username already exists.", HttpStatus.BAD_REQUEST)
        } else if (userRepository.existsByEmail(createUser.email)) {
            return ResponseEntity("User with given email already exists.", HttpStatus.BAD_REQUEST)
        }
        val user = User(
            displayName = createUser.name,
            username = createUser.name,
            password = passwordEncoder.encode(createUser.password),
            email = createUser.email,
            birthday = createUser.birthday
        )
        userRepository.save(user)
        return ResponseEntity.ok(user.id)
    }

    @PutMapping("/internal/users/update")
    fun updateUser(user: User): ResponseEntity<*> {
        val id = user.id
        val previousUser = userRepository.findById(id).orElseThrow {
            RuntimeException("Could not find user with given id: $id")
        }
        if (previousUser.locked ||
            previousUser.credentialsExpired ||
            previousUser.accountExpired ||
            !previousUser.enabled
        ) {
            return ResponseEntity.badRequest().body(
                "Account is inaccessible " +
                        "(locked, credentials expired, account expired, or disabled."
            )
        }
        previousUser.apply {
            displayName = user.displayName
            username = user.username
            password = passwordEncoder.encode(user.password)
            email = user.email
        }
        return ResponseEntity.ok(previousUser)
    }

    @DeleteMapping("/internal/users/delete")
    fun delete(deleteVerification: DeleteVerification): ResponseEntity<*> {
        val optional = userRepository.findById(deleteVerification.id)
        if (optional.isEmpty) return ResponseEntity.badRequest().body("No such user exists.")
        val user = optional.get()
        if (user.email != deleteVerification.email) return ResponseEntity.badRequest().body("Incorrect mail.")
        if (!passwordEncoder.matches(deleteVerification.password, user.password)) return ResponseEntity
            .badRequest().body("Incorrect password.")
        userRepository.delete(user)
        return ResponseEntity.ok("Successfully deleted item.")
    }
}