package com.example.timetable.server

import at.favre.lib.crypto.bcrypt.BCrypt
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PasswordHasher {
    fun hash(password: String): String = BCrypt.withDefaults().hashToString(12, password.toCharArray())
    fun verify(password: String, hash: String): Boolean =
        BCrypt.verifyer().verify(password.toCharArray(), hash).verified
}

/** 轻量 JWT（HS256）：header.payload.signature。 */
object JwtUtil {
    private val secret: String = System.getenv("JWT_SECRET") ?: "dev-secret-change-me"
    private val mac: Mac = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    }

    fun create(subject: String, expiresAtMillis: Long): String {
        val header = b64("""{"alg":"HS256","typ":"JWT"}""")
        val payload = b64("""{"sub":"$subject","exp":$expiresAtMillis}""")
        val signature = b64b(sign("$header.$payload"))
        return "$header.$payload.$signature"
    }

    /** 校验并返回 subject（userId）；无效返回 null。 */
    fun verify(token: String): String? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        val expected = b64b(sign("${parts[0]}.${parts[1]}"))
        if (!MessageDigest.isEqual(expected.toByteArray(), parts[2].toByteArray())) return null
        val payloadJson = try {
            String(Base64.getUrlDecoder().decode(parts[1]))
        } catch (_: Exception) {
            return null
        }
        val exp = Regex("\"exp\":(\\d+)").find(payloadJson)?.groupValues?.get(1)?.toLongOrNull() ?: return null
        if (System.currentTimeMillis() >= exp) return null
        return Regex("\"sub\":\"([^\"]+)\"").find(payloadJson)?.groupValues?.get(1)
    }

    private fun sign(data: String): ByteArray = mac.doFinal(data.toByteArray(Charsets.UTF_8))
    private fun b64(text: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))
    private fun b64b(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

data class AuthResult(
    val userId: Long,
    val accessToken: String,
    val refreshToken: String,
)

object AuthService {
    private const val ACCESS_TTL = 60L * 60 * 1000
    private const val REFRESH_TTL = 30L * 24 * 3600 * 1000

    fun register(email: String?, phone: String?, password: String): AuthResult {
        val mail = email?.trim()?.takeIf { it.contains("@") && it.length <= 255 }
        val ph = phone?.trim()?.takeIf { it.length in 6..20 && it.all { c -> c.isDigit() } }
        require(mail != null || ph != null) { "请填写有效邮箱或手机号" }
        require(password.length >= 6) { "密码至少 6 位" }
        val now = System.currentTimeMillis()
        val userId = transaction {
            Users.insert {
                it[Users.email] = mail
                it[Users.phone] = ph
                it[passwordHash] = PasswordHasher.hash(password)
                it[createdAt] = now
                it[updatedAt] = now
            } get Users.id
        }
        return issueTokens(userId)
    }

    fun login(account: String, password: String): AuthResult {
        val a = account.trim()
        val user = transaction {
            Users.selectAll().where { Users.email eq a }.firstOrNull()
                ?: Users.selectAll().where { Users.phone eq a }.firstOrNull()
        } ?: throw IllegalArgumentException("账号或密码错误")
        if (!PasswordHasher.verify(password, user[Users.passwordHash])) {
            throw IllegalArgumentException("账号或密码错误")
        }
        return issueTokens(user[Users.id])
    }

    fun refresh(refreshToken: String): AuthResult {
        val tokenHash = sha256(refreshToken)
        val row = transaction {
            RefreshTokens.selectAll().where { RefreshTokens.tokenHash eq tokenHash }.firstOrNull()
        } ?: throw IllegalArgumentException("刷新令牌无效")
        if (row[RefreshTokens.revoked]) throw IllegalArgumentException("刷新令牌已吊销")
        if (System.currentTimeMillis() > row[RefreshTokens.expiresAt]) throw IllegalArgumentException("刷新令牌已过期")
        val userId = row[RefreshTokens.userId]
        transaction { RefreshTokens.deleteWhere { RefreshTokens.tokenHash eq tokenHash } }
        return issueTokens(userId)
    }

    fun changePassword(userId: Long, oldPassword: String, newPassword: String) {
        require(newPassword.length >= 6) { "新密码至少 6 位" }
        val row = transaction {
            Users.selectAll().where { Users.id eq userId }.firstOrNull()
        } ?: throw IllegalArgumentException("用户不存在")
        if (!PasswordHasher.verify(oldPassword, row[Users.passwordHash])) {
            throw IllegalArgumentException("原密码错误")
        }
        transaction {
            Users.update({ Users.id eq userId }) {
                it[passwordHash] = PasswordHasher.hash(newPassword)
                it[updatedAt] = System.currentTimeMillis()
            }
        }
    }

    fun deleteUser(userId: Long) {
        transaction {
            val timetableIds = Timetables.selectAll()
                .where { Timetables.ownerId eq userId }
                .map { it[Timetables.id] }
            timetableIds.forEach { id ->
                Courses.deleteWhere { Courses.timetableId eq id }
                PeriodTimes.deleteWhere { PeriodTimes.timetableId eq id }
                ShareTokens.deleteWhere { ShareTokens.timetableId eq id }
            }
            Timetables.deleteWhere { Timetables.ownerId eq userId }
            ShareTokens.deleteWhere { ShareTokens.createdBy eq userId }
            RefreshTokens.deleteWhere { RefreshTokens.userId eq userId }
            Users.deleteWhere { Users.id eq userId }
        }
    }

    private fun issueTokens(userId: Long): AuthResult {
        val now = System.currentTimeMillis()
        val access = JwtUtil.create(userId.toString(), now + ACCESS_TTL)
        val refresh = JwtUtil.create("r$userId", now + REFRESH_TTL)
        transaction {
            RefreshTokens.insert {
                it[RefreshTokens.userId] = userId
                it[tokenHash] = sha256(refresh)
                it[expiresAt] = now + REFRESH_TTL
                it[revoked] = false
            }
        }
        return AuthResult(userId, access, refresh)
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
