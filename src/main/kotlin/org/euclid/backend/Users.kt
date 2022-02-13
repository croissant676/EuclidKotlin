package org.euclid.backend

import java.util.Optional
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.mapping.DocumentReference
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.hateoas.CollectionModel
import org.springframework.hateoas.EntityModel
import org.springframework.hateoas.server.mvc.linkTo
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsPasswordService
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
    var locked: Boolean = false,
    var credentialsExpired: Boolean = false,
    var accountExpired: Boolean = false,
    var enabled: Boolean = true
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
                "/dashboard/**",
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
    fun findByUsername(username: String): Optional<User>
}

@Component
class UserService(val userRepository: UserRepository, val passwordEncoder: PasswordEncoder) : UserDetailsService,
    UserDetailsPasswordService {
    class WrapperUserDetails(val user: User) : UserDetails {
        override fun getAuthorities(): MutableCollection<out GrantedAuthority> = mutableListOf()
        override fun getPassword(): String = user.password
        override fun getUsername() = user.username
        override fun isAccountNonExpired() = !user.accountExpired
        override fun isAccountNonLocked() = !user.locked
        override fun isCredentialsNonExpired() = !user.credentialsExpired
        override fun isEnabled() = user.enabled
    }

    override fun loadUserByUsername(username: String): UserDetails {
        val optional = userRepository.findByUsername(username)
        return WrapperUserDetails(optional.orElseThrow { UsernameNotFoundException("Could not find username: $username") })
    }

    override fun updatePassword(details: UserDetails, newPassword: String): UserDetails {
        if (details !is WrapperUserDetails) return details
        details.user.password = passwordEncoder.encode(newPassword)
        return details
    }
}

@RestController
class UserController(val userRepository: UserRepository) {
    @GetMapping("/internal/users")
    fun allUsers(): CollectionModel<User> = CollectionModel.of(
        userRepository.findAll(),
        linkTo<UserController> { allUsers() }.withRel("all"),
        linkTo<UserController> { allUsers() }.withSelfRel()
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
            linkTo<UserController> { allUsers() }.withSelfRel()
        )
    }

    @PutMapping("/internal/users/create")
    fun createUser(): SimpleID {
        return id()
    }
}