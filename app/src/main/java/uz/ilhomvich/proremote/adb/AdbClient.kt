package uz.ilhomvich.proremote.adb

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

/** A small ADB-over-TCP host client. Supports classic adbd TCP sockets (usually port 5555). */
class AdbClient(private val keyDirectory: Path) : Closeable {
    @Volatile private var socket: Socket? = null
    @Volatile private var input: DataInputStream? = null
    @Volatile private var output: DataOutputStream? = null
    @Volatile private var connected = false
    @Volatile private var maxPayload = 4096
    private val streamIds = AtomicInteger(1)

    val isConnected: Boolean get() = connected && socket?.isConnected == true && socket?.isClosed == false

    @Synchronized
    fun connect(host: String, port: Int = 5555) {
        close()
        require(host.isNotBlank()) { "TV IP manzili kiritilmagan." }
        require(port in 1..65535) { "Port 1–65535 oralig‘ida bo‘lishi kerak." }

        val newSocket = Socket()
        newSocket.tcpNoDelay = true
        newSocket.keepAlive = true
        newSocket.connect(InetSocketAddress(host.trim(), port), 8_000)
        newSocket.soTimeout = 45_000
        socket = newSocket
        input = DataInputStream(newSocket.getInputStream())
        output = DataOutputStream(newSocket.getOutputStream())

        val pair = loadOrCreateKeyPair()
        val banner = "host::features=shell_v2,cmd,stat_v2,ls_v2,libusb,abb,remount_shell,track_app,sendrecv_v2\u0000"
            .toByteArray(StandardCharsets.UTF_8)
        sendPacket(CNXN, ADB_VERSION, 1024 * 1024, banner)

        var signatureSent = false
        var publicKeySent = false
        var handshakeDone = false
        var authResponses = 0
        while (!handshakeDone) {
            val packet = readPacket()
            when (packet.command) {
                CNXN -> {
                    maxPayload = packet.arg1.coerceIn(1024, 1024 * 1024)
                    connected = true
                    handshakeDone = true
                }
                AUTH -> {
                    authResponses++
                    when (packet.arg0) {
                        AUTH_TOKEN -> {
                            if (!signatureSent) {
                                sendPacket(AUTH, AUTH_SIGNATURE, 0, signToken(pair, packet.data))
                                signatureSent = true
                            } else if (!publicKeySent) {
                                sendPacket(AUTH, AUTH_RSAPUBLICKEY, 0, adbPublicKey(pair.public))
                                publicKeySent = true
                            } else {
                                throw IllegalStateException("TV ADB kalitini qabul qilmadi. TV ekranidagi ruxsat oynasini tekshiring.")
                            }
                        }
                        else -> throw IllegalStateException("Noma’lum ADB autentifikatsiya turi: ${packet.arg0}")
                    }
                    if (authResponses > 4) throw IllegalStateException("ADB autentifikatsiyasi yakunlanmadi.")
                }
                else -> throw IllegalStateException("ADB serverdan kutilmagan javob olindi.")
            }
        }
    }

    /** Runs one command through the legacy shell stream and returns combined stdout/stderr. */
    @Synchronized
    fun shell(command: String): String {
        check(isConnected) { "Avval TV’ga ADB orqali ulaning." }
        require(command.isNotBlank()) { "Buyruq bo‘sh bo‘lmasligi kerak." }
        val localId = streamIds.getAndIncrement().coerceAtLeast(1)
        val service = "shell:$command\u0000".toByteArray(StandardCharsets.UTF_8)
        sendPacket(OPEN, localId, 0, service)

        val bytes = ByteArrayOutputStream()
        var remoteId = 0
        var streamOpened = false
        while (true) {
            val packet = readPacket()
            when (packet.command) {
                OKAY -> {
                    if (packet.arg1 == localId) {
                        remoteId = packet.arg0
                        streamOpened = true
                    }
                }
                WRTE -> {
                    if (packet.arg1 == localId || remoteId == 0) {
                        if (remoteId == 0) remoteId = packet.arg0
                        bytes.write(packet.data)
                        sendPacket(OKAY, localId, remoteId, ByteArray(0))
                    }
                }
                CLSE -> {
                    if (packet.arg1 == localId || remoteId == 0 || packet.arg0 == remoteId) {
                        val closeRemote = if (remoteId == 0) packet.arg0 else remoteId
                        runCatching { sendPacket(CLSE, localId, closeRemote, ByteArray(0)) }
                        break
                    }
                }
                AUTH -> throw IllegalStateException("ADB sessiyasi qayta autentifikatsiya so‘radi.")
                else -> if (!streamOpened) {
                    // Ignore unrelated control packets while waiting for this stream.
                }
            }
        }
        return bytes.toByteArray().toString(StandardCharsets.UTF_8).replace("\u0000", "").trim()
    }

    @Synchronized
    override fun close() {
        connected = false
        runCatching { socket?.close() }
        socket = null
        input = null
        output = null
    }

    private fun loadOrCreateKeyPair(): KeyPair {
        Files.createDirectories(keyDirectory)
        val privateFile = keyDirectory.resolve("adbkey.pk8")
        val publicFile = keyDirectory.resolve("adbkey.pub")
        val factory = KeyFactory.getInstance("RSA")
        if (Files.exists(privateFile) && Files.exists(publicFile)) {
            val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(Files.readAllBytes(privateFile)))
            val publicKey = factory.generatePublic(X509EncodedKeySpec(Files.readAllBytes(publicFile)))
            return KeyPair(publicKey, privateKey)
        }
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        Files.write(privateFile, pair.private.encoded)
        Files.write(publicFile, pair.public.encoded)
        return pair
    }

    private fun signToken(pair: KeyPair, token: ByteArray): ByteArray {
        val signer = Signature.getInstance("SHA1withRSA")
        signer.initSign(pair.private)
        signer.update(token)
        return signer.sign()
    }

    /** Android adbd's base64 RSAPublicKey structure (including its 64-word length field). */
    private fun adbPublicKey(publicKey: java.security.PublicKey): ByteArray {
        val rsa = publicKey as? RSAPublicKey ?: error("RSA public key kutilmoqda.")
        val modulus = rsa.modulus
        val wordCount = 64
        val two32 = BigInteger.ONE.shiftLeft(32)
        val mask = two32.subtract(BigInteger.ONE)
        val lowWord = modulus.and(mask)
        val n0inv = two32.subtract(lowWord.modInverse(two32)).mod(two32)
        val rr = BigInteger.ONE.shiftLeft(4096).mod(modulus)

        val buffer = ByteBuffer.allocate(4 + 4 + wordCount * 4 + wordCount * 4 + 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(wordCount)
        buffer.putInt(n0inv.toInt())
        repeat(wordCount) { index ->
            buffer.putInt(modulus.shiftRight(index * 32).and(mask).toInt())
        }
        repeat(wordCount) { index ->
            buffer.putInt(rr.shiftRight(index * 32).and(mask).toInt())
        }
        buffer.putInt(rsa.publicExponent.toInt())

        val encoded = Base64.getEncoder().encodeToString(buffer.array())
        return "$encoded pro-remote@android\u0000".toByteArray(StandardCharsets.UTF_8)
    }

    private data class Packet(val command: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    private fun readPacket(): Packet {
        val stream = input ?: error("ADB socket yopilgan.")
        val header = ByteArray(24)
        stream.readFully(header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val length = buffer.int
        val checksum = buffer.int
        val magic = buffer.int
        require(magic == command.inv()) { "ADB paket sarlavhasi noto‘g‘ri." }
        require(length in 0..(1024 * 1024)) { "ADB paket hajmi chegaradan katta: $length" }
        val data = ByteArray(length)
        if (length > 0) stream.readFully(data)
        if (checksum != 0) {
            val actual = data.fold(0L) { sum, byte -> sum + (byte.toInt() and 0xff) }.toInt()
            require(actual == checksum) { "ADB paket checksum tekshiruvidan o‘tmadi." }
        }
        return Packet(command, arg0, arg1, data)
    }

    private fun sendPacket(command: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val stream = output ?: error("ADB socket yopilgan.")
        val checksum = data.fold(0L) { sum, byte -> sum + (byte.toInt() and 0xff) }.toInt()
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(command)
            .putInt(arg0)
            .putInt(arg1)
            .putInt(data.size)
            .putInt(checksum)
            .putInt(command.inv())
            .array()
        stream.write(header)
        if (data.isNotEmpty()) stream.write(data)
        stream.flush()
    }

    private fun commandId(value: String): Int {
        require(value.length == 4)
        return value[0].code or (value[1].code shl 8) or (value[2].code shl 16) or (value[3].code shl 24)
    }

    companion object {
        private const val ADB_VERSION = 0x01000001
        private const val AUTH_TOKEN = 1
        private const val AUTH_SIGNATURE = 2
        private const val AUTH_RSAPUBLICKEY = 3
        private val CNXN = "CNXN".let { it[0].code or (it[1].code shl 8) or (it[2].code shl 16) or (it[3].code shl 24) }
        private val AUTH = "AUTH".let { it[0].code or (it[1].code shl 8) or (it[2].code shl 16) or (it[3].code shl 24) }
        private val OPEN = "OPEN".let { it[0].code or (it[1].code shl 8) or (it[2].code shl 16) or (it[3].code shl 24) }
        private val OKAY = "OKAY".let { it[0].code or (it[1].code shl 8) or (it[2].code shl 16) or (it[3].code shl 24) }
        private val WRTE = "WRTE".let { it[0].code or (it[1].code shl 8) or (it[2].code shl 16) or (it[3].code shl 24) }
        private val CLSE = "CLSE".let { it[0].code or (it[1].code shl 8) or (it[2].code shl 16) or (it[3].code shl 24) }
    }
}
