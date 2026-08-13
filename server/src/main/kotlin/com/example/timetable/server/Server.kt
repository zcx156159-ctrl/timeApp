package com.example.timetable.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

// ---------- 数据模型（与客户端 Course 字段保持一致） ----------

@Serializable
data class Course(
    val id: String = "",
    val name: String,
    val teacher: String = "",
    val location: String = "",
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val colorIndex: Int = 0,
    val startWeek: Int? = null,
    val endWeek: Int? = null,
    val weekType: Int = 0,
    val note: String = "",
)

@Serializable
data class TimetablePayload(val courses: List<Course>)

@Serializable
data class CreateResponse(val code: String)

@Serializable
data class TimetableResponse(val code: String, val courses: List<Course>, val updatedAt: Long)

@Serializable
data class StoredTimetable(val courses: List<Course>, val updatedAt: Long)

@Serializable
data class ApiError(val error: String)

@Serializable
data class HealthResponse(val ok: Boolean)

@Serializable
data class RegisterRequest(val email: String? = null, val phone: String? = null, val password: String = "")

@Serializable
data class LoginRequest(val account: String = "", val password: String = "")

@Serializable
data class RefreshRequest(val refreshToken: String = "")

@Serializable
data class AuthResponse(val userId: Long, val accessToken: String, val refreshToken: String)

@Serializable
data class UserResponse(val userId: Long, val email: String? = null, val phone: String? = null)

@Serializable
data class ChangePasswordRequest(val oldPassword: String = "", val newPassword: String = "")

@Serializable
data class ApiMessage(val message: String)

private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

private const val CODE_CHARS = "23456789abcdefghjkmnpqrstuvwxyz"

// ---------- 文件存储：timetables.json ----------

private class Store(private val file: Path) {
    private val map = ConcurrentHashMap<String, StoredTimetable>()

    init {
        file.parent?.let { Files.createDirectories(it) }
        if (Files.exists(file)) {
            runCatching {
                map.putAll(json.decodeFromString<Map<String, StoredTimetable>>(Files.readString(file)))
            }
        }
    }

    fun create(courses: List<Course>): String {
        val code = generateCode()
        map[code] = StoredTimetable(courses, System.currentTimeMillis())
        persist()
        return code
    }

    fun get(code: String): StoredTimetable? = map[code]

    fun put(code: String, courses: List<Course>): StoredTimetable {
        val t = StoredTimetable(courses, System.currentTimeMillis())
        map[code] = t
        persist()
        return t
    }

    private fun generateCode(): String {
        while (true) {
            val code = (1..6).map { CODE_CHARS.random() }.joinToString("")
            if (!map.containsKey(code)) return code
        }
    }

    @Synchronized
    private fun persist() {
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(tmp, json.encodeToString<Map<String, StoredTimetable>>(map))
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
    }
}

// ---------- HTTP 工具 ----------

private fun cors(ex: HttpExchange) {
    val h = ex.responseHeaders
    h.set("Access-Control-Allow-Origin", "*")
    h.set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS")
    h.set("Access-Control-Allow-Headers", "Content-Type")
    h.set("Access-Control-Max-Age", "86400")
    // Skiko/Wasm 渲染需要跨域隔离（SharedArrayBuffer）
    h.set("Cross-Origin-Opener-Policy", "same-origin")
    h.set("Cross-Origin-Embedder-Policy", "require-corp")
}

private fun respond(ex: HttpExchange, code: Int, body: String, contentType: String = "application/json; charset=utf-8") {
    logRequest(ex, code)
    if (code == 204) {
        ex.sendResponseHeaders(204, -1)
        ex.close()
        return
    }
    val bytes = body.toByteArray(Charsets.UTF_8)
    respondBytes(ex, code, bytes, contentType)
}

private fun respondBytes(ex: HttpExchange, code: Int, bytes: ByteArray, contentType: String) {
    logRequest(ex, code)
    ex.responseHeaders.set("Content-Type", contentType)
    ex.sendResponseHeaders(code, bytes.size.toLong())
    ex.responseBody.use { it.write(bytes) }
    ex.close()
}

private fun logRequest(ex: HttpExchange, code: Int) {
    val line = "${ex.requestMethod} ${ex.requestURI.path} -> $code"
    println(line)
    runCatching {
        val log = Paths.get(System.getenv("LOG_FILE") ?: "server.log")
        synchronized(log) {
            Files.writeString(log, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }
    }
}

private fun readBody(ex: HttpExchange): String =
    ex.requestBody.use { it.readBytes().toString(Charsets.UTF_8) }

private fun resolveSafe(root: Path, path: String): Path? {
    val clean = path.trimStart('/')
    val target = root.resolve(clean).normalize()
    return if (target.startsWith(root.normalize())) target else null
}

private inline fun handle(ex: HttpExchange, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        runCatching { respond(ex, 500, json.encodeToString(ApiError(e.message ?: e.toString()))) }
    }
}

private inline fun apiCall(ex: HttpExchange, block: () -> Unit) {
    try {
        block()
    } catch (e: IllegalArgumentException) {
        runCatching { respond(ex, 400, json.encodeToString(ApiError(e.message ?: "bad request"))) }
    } catch (e: Exception) {
        runCatching { respond(ex, 500, json.encodeToString(ApiError(e.message ?: e.toString()))) }
    }
}

private fun requireUser(ex: HttpExchange): Long? {
    val auth = ex.requestHeaders.getFirst("Authorization") ?: ""
    val uid = JwtUtil.verify(auth.removePrefix("Bearer ").trim())?.toLongOrNull()
    if (uid == null) {
        respond(ex, 401, json.encodeToString(ApiError("unauthorized")))
    }
    return uid
}

private fun mimeOf(file: Path): String = when (file.fileName.toString().substringAfterLast('.', "").lowercase()) {
    "html" -> "text/html; charset=utf-8"
    "js" -> "application/javascript; charset=utf-8"
    "wasm" -> "application/wasm"
    "map" -> "application/json"
    "json" -> "application/json; charset=utf-8"
    "css" -> "text/css; charset=utf-8"
    "png" -> "image/png"
    "ico" -> "image/x-icon"
    "svg" -> "image/svg+xml"
    "txt" -> "text/plain; charset=utf-8"
    else -> "application/octet-stream"
}

// ---------- 主入口 ----------

fun main() {
    val server = startServer(
        port = System.getenv("PORT")?.toIntOrNull() ?: 8080,
        dataFile = Paths.get(System.getenv("DATA_FILE") ?: "timetables.json"),
        webRoot = System.getenv("WEB_ROOT")?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
            ?: autoDetectWebRoot(),
    )
    server.start()
    println("timetable-server listening on :${server.address.port}")
}

/** 未设置 WEB_ROOT 时，从当前目录自动找 Web 产物。 */
private fun autoDetectWebRoot(): Path? {
    val candidates = listOf(
        "composeApp/build/dist/wasmJs/productionExecutable",
        "web",
        "dist",
    )
    return candidates
        .map { Paths.get(it) }
        .firstOrNull { Files.isRegularFile(it.resolve("index.html")) }
}

/** 创建并配置 HTTP 服务（不启动），便于测试和复用。 */
fun startServer(port: Int, dataFile: Path, webRoot: Path?): HttpServer {
    val store = Store(dataFile)
    initDatabase()

    val server = HttpServer.create(InetSocketAddress(port), 0)
    server.executor = Executors.newFixedThreadPool(8)

    server.createContext("/api/health") { ex ->
        cors(ex)
        when (ex.requestMethod) {
            "OPTIONS" -> respond(ex, 204, "")
            "GET" -> respond(ex, 200, json.encodeToString(HealthResponse(true)))
            else -> respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
        }
    }

    server.createContext("/api/timetables") { ex ->
        handle(ex) {
            cors(ex)
            when (ex.requestMethod) {
                "OPTIONS" -> respond(ex, 204, "")
                "POST" -> {
                    val courses = json.decodeFromString<TimetablePayload>(readBody(ex)).courses
                    val code = store.create(courses)
                    respond(ex, 200, json.encodeToString(CreateResponse(code)))
                }
                else -> respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
            }
        }
    }

    server.createContext("/api/timetables/") { ex ->
        cors(ex)
        val code = ex.requestURI.path.removePrefix("/api/timetables/")
        when (ex.requestMethod) {
            "OPTIONS" -> respond(ex, 204, "")
            "GET" -> {
                val t = store.get(code)
                if (t == null) {
                    respond(ex, 404, json.encodeToString(ApiError("timetable not found: $code")))
                } else {
                    respond(ex, 200, json.encodeToString(TimetableResponse(code, t.courses, t.updatedAt)))
                }
            }
            "PUT" -> {
                val t = store.get(code)
                if (t == null) {
                    respond(ex, 404, json.encodeToString(ApiError("timetable not found: $code")))
                } else {
                    val courses = json.decodeFromString<TimetablePayload>(readBody(ex)).courses
                    val updated = store.put(code, courses)
                    respond(ex, 200, json.encodeToString(TimetableResponse(code, updated.courses, updated.updatedAt)))
                }
            }
            else -> respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
        }
    }

    server.createContext("/api/v1/auth/register") { ex ->
        cors(ex)
        handle(ex) {
            when (ex.requestMethod) {
                "OPTIONS" -> respond(ex, 204, "")
                "POST" -> apiCall(ex) {
                    val req = json.decodeFromString<RegisterRequest>(readBody(ex))
                    val r = AuthService.register(req.email, req.phone, req.password)
                    respond(ex, 200, json.encodeToString(AuthResponse(r.userId, r.accessToken, r.refreshToken)))
                }
                else -> respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
            }
        }
    }

    server.createContext("/api/v1/auth/login") { ex ->
        cors(ex)
        handle(ex) {
            when (ex.requestMethod) {
                "OPTIONS" -> respond(ex, 204, "")
                "POST" -> apiCall(ex) {
                    val req = json.decodeFromString<LoginRequest>(readBody(ex))
                    val r = AuthService.login(req.account, req.password)
                    respond(ex, 200, json.encodeToString(AuthResponse(r.userId, r.accessToken, r.refreshToken)))
                }
                else -> respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
            }
        }
    }

    server.createContext("/api/v1/auth/refresh") { ex ->
        cors(ex)
        handle(ex) {
            when (ex.requestMethod) {
                "OPTIONS" -> respond(ex, 204, "")
                "POST" -> apiCall(ex) {
                    val req = json.decodeFromString<RefreshRequest>(readBody(ex))
                    val r = AuthService.refresh(req.refreshToken)
                    respond(ex, 200, json.encodeToString(AuthResponse(r.userId, r.accessToken, r.refreshToken)))
                }
                else -> respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
            }
        }
    }

    server.createContext("/api/v1/me") { ex ->
        cors(ex)
        handle(ex) {
            when (ex.requestMethod) {
                "OPTIONS" -> respond(ex, 204, "")
                "GET" -> {
                    val uid = requireUser(ex) ?: return@handle
                    val user = transaction {
                        Users.selectAll().where { Users.id eq uid }.firstOrNull()
                    }
                    if (user == null) {
                        respond(ex, 404, json.encodeToString(ApiError("user not found")))
                    } else {
                        respond(ex, 200, json.encodeToString(UserResponse(uid, user[Users.email], user[Users.phone])))
                    }
                }
                "DELETE" -> {
                    val uid = requireUser(ex) ?: return@handle
                    AuthService.deleteUser(uid)
                    respond(ex, 200, json.encodeToString(ApiMessage("ok")))
                }
                else -> respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
            }
        }
    }

    server.createContext("/api/v1/me/password") { ex ->
        cors(ex)
        handle(ex) {
            when (ex.requestMethod) {
                "OPTIONS" -> respond(ex, 204, "")
                "PUT" -> {
                    val uid = requireUser(ex) ?: return@handle
                    apiCall(ex) {
                        val req = json.decodeFromString<ChangePasswordRequest>(readBody(ex))
                        AuthService.changePassword(uid, req.oldPassword, req.newPassword)
                        respond(ex, 200, json.encodeToString(ApiMessage("ok")))
                    }
                }
                else -> respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
            }
        }
    }

    server.createContext("/api/v1/timetables") { ex ->
        cors(ex)
        handle(ex) {
            when (ex.requestMethod) {
                "OPTIONS" -> respond(ex, 204, "")
                "GET" -> {
                    val uid = requireUser(ex) ?: return@handle
                    respond(ex, 200, json.encodeToString(TimetableService.list(uid)))
                }
                "POST" -> {
                    val uid = requireUser(ex) ?: return@handle
                    apiCall(ex) {
                        val req = json.decodeFromString<CreateTimetableRequest>(readBody(ex))
                        respond(ex, 200, json.encodeToString(TimetableService.create(uid, req)))
                    }
                }
                else -> respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
            }
        }
    }

    server.createContext("/api/v1/timetables/") { ex ->
        cors(ex)
        handle(ex) {
            if (ex.requestMethod == "OPTIONS") {
                respond(ex, 204, "")
                return@handle
            }
            val uid = requireUser(ex) ?: return@handle
            val path = ex.requestURI.path.removePrefix("/api/v1/timetables/").trim('/')
            val parts = path.split("/")
            val id = parts.firstOrNull()?.toLongOrNull()
            if (id == null) {
                respond(ex, 400, json.encodeToString(ApiError("invalid timetable id")))
                return@handle
            }
            when (parts.getOrNull(1)) {
                null -> when (ex.requestMethod) {
                    "GET" -> {
                        val d = TimetableService.detail(uid, id)
                        if (d == null) respond(ex, 404, json.encodeToString(ApiError("timetable not found")))
                        else respond(ex, 200, json.encodeToString(d))
                    }
                    "PUT" -> apiCall(ex) {
                        val req = json.decodeFromString<UpdateTimetableRequest>(readBody(ex))
                        if (TimetableService.update(uid, id, req)) respond(ex, 200, json.encodeToString(ApiMessage("ok")))
                        else respond(ex, 404, json.encodeToString(ApiError("timetable not found")))
                    }
                    "DELETE" -> {
                        if (TimetableService.delete(uid, id)) respond(ex, 200, json.encodeToString(ApiMessage("ok")))
                        else respond(ex, 404, json.encodeToString(ApiError("timetable not found")))
                    }
                    else -> respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
                }
                "periods" -> {
                    if (ex.requestMethod == "PUT") {
                        apiCall(ex) {
                            val req = json.decodeFromString<List<PeriodTimeIn>>(readBody(ex))
                            if (TimetableService.savePeriods(uid, id, req)) respond(ex, 200, json.encodeToString(ApiMessage("ok")))
                            else respond(ex, 404, json.encodeToString(ApiError("timetable not found")))
                        }
                    } else {
                        respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
                    }
                }
                "courses" -> {
                    if (ex.requestMethod == "PUT") {
                        apiCall(ex) {
                            val req = json.decodeFromString<List<CourseIn>>(readBody(ex))
                            if (TimetableService.replaceCourses(uid, id, req)) respond(ex, 200, json.encodeToString(ApiMessage("ok")))
                            else respond(ex, 404, json.encodeToString(ApiError("timetable not found")))
                        }
                    } else {
                        respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
                    }
                }
                "sync" -> {
                    if (ex.requestMethod == "POST") {
                        apiCall(ex) {
                            val req = json.decodeFromString<SyncRequest>(readBody(ex))
                            val resp = SyncService.sync(uid, id, req)
                            if (resp == null) {
                                respond(ex, 404, json.encodeToString(ApiError("timetable not found")))
                            } else {
                                respond(ex, 200, json.encodeToString(resp))
                            }
                        }
                    } else {
                        respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
                    }
                }
                else -> respond(ex, 404, json.encodeToString(ApiError("not found")))
            }
        }
    }

    server.createContext("/api/v1/shares") { ex ->
        cors(ex)
        handle(ex) {
            if (ex.requestMethod == "OPTIONS") {
                respond(ex, 204, "")
                return@handle
            }
            val uid = requireUser(ex) ?: return@handle
            when (ex.requestMethod) {
                "GET" -> respond(ex, 200, json.encodeToString(ShareService.listMine(uid)))
                "POST" -> apiCall(ex) {
                    val req = json.decodeFromString<ShareCreateRequest>(readBody(ex))
                    val resp = ShareService.create(uid, req)
                    if (resp == null) {
                        respond(ex, 404, json.encodeToString(ApiError("timetable not found")))
                    } else {
                        respond(ex, 200, json.encodeToString(resp))
                    }
                }
                else -> respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
            }
        }
    }

    server.createContext("/api/v1/shares/") { ex ->
        cors(ex)
        handle(ex) {
            if (ex.requestMethod == "OPTIONS") {
                respond(ex, 204, "")
                return@handle
            }
            val uid = requireUser(ex) ?: return@handle
            val token = ex.requestURI.path.removePrefix("/api/v1/shares/").trim('/')
            if (ex.requestMethod == "DELETE") {
                if (ShareService.revoke(uid, token)) {
                    respond(ex, 200, json.encodeToString(ApiMessage("ok")))
                } else {
                    respond(ex, 404, json.encodeToString(ApiError("share not found")))
                }
            } else {
                respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
            }
        }
    }

    server.createContext("/api/v1/public/timetables/") { ex ->
        cors(ex)
        handle(ex) {
            if (ex.requestMethod == "OPTIONS") {
                respond(ex, 204, "")
                return@handle
            }
            val path = ex.requestURI.path.removePrefix("/api/v1/public/timetables/").trim('/')
            val parts = path.split("/")
            val token = parts.firstOrNull().orEmpty()
            if (token.isBlank()) {
                respond(ex, 400, json.encodeToString(ApiError("missing token")))
                return@handle
            }
            when (parts.getOrNull(1)) {
                null -> {
                    if (ex.requestMethod == "GET") {
                        val data = ShareService.publicGet(token)
                        if (data == null) {
                            respond(ex, 404, json.encodeToString(ApiError("share not found or revoked")))
                        } else {
                            respond(ex, 200, json.encodeToString(data))
                        }
                    } else {
                        respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
                    }
                }
                "courses" -> {
                    if (ex.requestMethod == "PUT") {
                        apiCall(ex) {
                            val req = json.decodeFromString<List<CourseIn>>(readBody(ex))
                            if (ShareService.publicPutCourses(token, req)) {
                                respond(ex, 200, json.encodeToString(ApiMessage("ok")))
                            } else {
                                respond(ex, 403, json.encodeToString(ApiError("no write permission")))
                            }
                        }
                    } else {
                        respond(ex, 405, json.encodeToString(ApiError("method not allowed")))
                    }
                }
                else -> respond(ex, 404, json.encodeToString(ApiError("not found")))
            }
        }
    }

    if (webRoot != null) {
        server.createContext("/") { ex ->
            handle(ex) {
                cors(ex)
                if (ex.requestMethod == "OPTIONS") {
                    respond(ex, 204, "")
                    return@handle
                }
                val path = ex.requestURI.path.removePrefix("/")
                val resolved = resolveSafe(webRoot, path)
                if (resolved == null) {
                    respond(ex, 404, "not found", "text/plain; charset=utf-8")
                    return@handle
                }
                val target = if (Files.isDirectory(resolved)) resolved.resolve("index.html") else resolved
                if (!Files.isRegularFile(target)) {
                    respond(ex, 404, "not found", "text/plain; charset=utf-8")
                    return@handle
                }
                respondBytes(ex, 200, Files.readAllBytes(target), mimeOf(target))
            }
        }
        println("web root: ${webRoot.toAbsolutePath()}")
    } else {
        println("web root: 未找到，只提供 /api（可用 WEB_ROOT 指定网页目录）")
    }

    return server
}
