package com.shevs.realtimechat.data

import com.shevs.realtimechat.models.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets

object DataStore {
    private val dataDir = File("data")
    private val usersFile = File(dataDir, "users.json")
    private val contactsFile = File(dataDir, "contacts.json")
    private val publicMessagesFile = File(dataDir, "public_messages.json")
    private val privateMessagesDir = File(dataDir, "private_messages")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        dataDir.mkdirs()
        privateMessagesDir.mkdirs()
        if (!usersFile.exists()) usersFile.writeText("[]", Charsets.UTF_8)
        if (!contactsFile.exists()) contactsFile.writeText("[]", Charsets.UTF_8)
        if (!publicMessagesFile.exists()) publicMessagesFile.writeText("[]", Charsets.UTF_8)
    }

    // Users
    fun loadUsers(): MutableList<User> {
        val content = usersFile.readText(Charsets.UTF_8)
        return if (content.isBlank()) mutableListOf()
        else json.decodeFromString(content)
    }

    fun saveUsers(users: List<User>) {
        usersFile.writeText(json.encodeToString(users), Charsets.UTF_8)
    }

    fun findUser(username: String): User? {
        return loadUsers().find { it.username == username }
    }

    // Contacts
    fun loadContacts(userId: String): List<Contact> {
        val all = loadAllContacts()
        return all.filter { it.userId == userId }
    }

    fun loadAllContacts(): MutableList<Contact> {
        val content = contactsFile.readText(Charsets.UTF_8)
        return if (content.isBlank()) mutableListOf()
        else json.decodeFromString(content)
    }

    fun saveContacts(contacts: List<Contact>) {
        contactsFile.writeText(json.encodeToString(contacts), Charsets.UTF_8)
    }

    fun addContact(userId: String, contactUsername: String) {
        val contacts = loadAllContacts()
        if (contacts.none { it.userId == userId && it.contactUsername == contactUsername }) {
            contacts.add(Contact(userId, contactUsername))
            saveContacts(contacts)
        }
    }

    // Public Messages
    fun loadPublicMessages(): MutableList<PublicMessage> {
        val content = publicMessagesFile.readText(Charsets.UTF_8)
        return if (content.isBlank()) mutableListOf()
        else json.decodeFromString(content)
    }

    fun savePublicMessages(messages: List<PublicMessage>) {
        val limited = messages.takeLast(1000)
        publicMessagesFile.writeText(json.encodeToString(limited), Charsets.UTF_8)
    }

    fun addPublicMessage(message: PublicMessage) {
        val messages = loadPublicMessages()
        messages.add(message)
        savePublicMessages(messages)
    }

    fun clearPublicHistory() {
        publicMessagesFile.writeText("[]")
    }

    // Private Messages
    private fun getPrivateMessagesFile(user1: String, user2: String): File {
        val names = listOf(user1, user2).sorted()
        return File(privateMessagesDir, "${names[0]}_${names[1]}.json")
    }

    fun loadPrivateMessages(user1: String, user2: String): MutableList<PrivateMessage> {
        val file = getPrivateMessagesFile(user1, user2)
        if (!file.exists()) return mutableListOf()
        val content = file.readText(Charsets.UTF_8)
        return if (content.isBlank()) mutableListOf() else json.decodeFromString(content)
    }

    fun savePrivateMessages(user1: String, user2: String, messages: List<PrivateMessage>) {
        val file = getPrivateMessagesFile(user1, user2)
        file.writeText(json.encodeToString(messages.takeLast(500)), Charsets.UTF_8)
    }

    fun addPrivateMessage(message: PrivateMessage) {
        val messages = loadPrivateMessages(message.from, message.to)
        messages.add(message)
        savePrivateMessages(message.from, message.to, messages)
    }

    fun clearPrivateHistory(user1: String, user2: String) {
        val file = getPrivateMessagesFile(user1, user2)
        file.delete()
    }
}