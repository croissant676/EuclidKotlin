package org.euclid.backend

import com.nimbusds.oauth2.sdk.util.date.SimpleDate
import java.security.SecureRandom
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.util.Base64Utils
import kotlin.reflect.KClass

@SpringBootApplication
class EuclidKotlinApplication

@Configuration
class MongoConfiguration: AbstractMongoClientConfiguration() {
    override fun getDatabaseName(): String = "euclid_kt"

    @Bean
    fun passwordEncoder(): PasswordEncoder = Argon2PasswordEncoder()

    @Bean
    fun runner(userController: UserController) = ApplicationRunner {
        userController.createUser(
            CreateUserObject(
                "Bob",
                "password",
                "bob@email.com",
                SimpleDate(2000, 1, 1)
            ).log()!!
        )
    }
}

fun main(args: Array<String>) {
    runApplication<EuclidKotlinApplication>(*args)
}

//

private val random = SecureRandom()

fun id(): SimpleID {
    val bytes = ByteArray(18)
    random.nextBytes(bytes)
    return SimpleID(bytes)
}

fun id(string: String): SimpleID? {
    return try {
        SimpleID(Base64Utils.decodeFromUrlSafeString(string))
    } catch (exception: Exception) {
        null
    }
}

data class SimpleID(private val bytes: ByteArray) {
    override fun equals(other: Any?) = other != null && other is SimpleID && bytes.contentEquals(other.bytes)
    override fun toString() = Base64Utils.encodeToString(bytes)
    override fun hashCode() = bytes.contentHashCode()
}

// Logging

private val mutableMap: MutableMap<Class<*>, Logger> = hashMapOf()

fun log(modelClass: KClass<*>): Logger {
    val clazz = modelClass.java
    if (clazz in mutableMap.keys) return mutableMap[clazz]!!
    val logger = LoggerFactory.getLogger(clazz)
    mutableMap[clazz] = logger
    return logger
}

operator fun Logger.invoke(string: String) = info(string)
operator fun Logger.invoke(string: String, vararg args: Any?) = info(string, *args)
operator fun Logger.invoke(string: String, throwable: Throwable) = error(string, throwable)
val appLogger: Logger = LoggerFactory.getLogger(EuclidKotlinApplication::class.java)

fun <T> T?.log(): T? {
    if (this == null) appLogger("Specified object is null.")
    else appLogger("Logging value $this")
    return this
}