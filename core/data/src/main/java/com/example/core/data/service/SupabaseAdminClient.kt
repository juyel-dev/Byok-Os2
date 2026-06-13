package com.example.core.data.service

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class SupabaseAdminClient(private val client: OkHttpClient) {

    fun extractProjectRef(url: String): String {
        var clean = url.trim()
        if (clean.startsWith("http://")) clean = clean.substring(7)
        if (clean.startsWith("https://")) clean = clean.substring(8)
        val firstSlash = clean.indexOf('/')
        if (firstSlash != -1) {
            clean = clean.substring(0, firstSlash)
        }
        val dotIndex = clean.indexOf('.')
        return if (dotIndex != -1) {
            clean.substring(0, dotIndex)
        } else {
            clean
        }
    }

    // Runs raw sql query on Supabase via the Management API query endpoint
    fun executeSql(url: String, pat: String, sql: String): JSONObject {
        val projectRef = extractProjectRef(url)
        val bodyJson = JSONObject().put("query", sql)
        val request = Request.Builder()
            .url("https://api.supabase.com/v1/projects/$projectRef/query")
            .header("Authorization", "Bearer $pat")
            .header("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw Exception("SQL query failed (HTTP ${response.code}): $body")
            }
            return try {
                if (body.trim().startsWith("[")) {
                     JSONObject().put("results", JSONArray(body))
                } else if (body.trim().startsWith("{")) {
                     JSONObject(body)
                } else {
                     JSONObject().put("message", body)
                }
            } catch (e: Exception) {
                JSONObject().put("message", body)
            }
        }
    }

    // PostgREST Fetch standard table rows
    fun fetchPostgrestRows(url: String, serviceRoleKey: String, tableName: String, queryParams: String = ""): JSONArray {
        var baseUrl = url.trim()
        if (!baseUrl.contains("://")) {
            baseUrl = "https://$baseUrl"
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.dropLast(1)
        }
        val targetUrl = "$baseUrl/rest/v1/$tableName$queryParams"

        val request = Request.Builder()
            .url(targetUrl)
            .header("apikey", serviceRoleKey)
            .header("Authorization", "Bearer $serviceRoleKey")
            .header("Content-Type", "application/json")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                throw Exception("Fetch failed on table '$tableName' (HTTP ${response.code}): $body")
            }
            return if (body.trim().startsWith("[")) {
                JSONArray(body)
            } else if (body.trim().startsWith("{")) {
                JSONArray().put(JSONObject(body))
            } else {
                JSONArray()
            }
        }
    }

    // PostgREST Upsert rows with Service Role Key
    fun upsertPostgrestRows(url: String, serviceRoleKey: String, tableName: String, rows: JSONArray) {
        if (rows.length() == 0) return
        var baseUrl = url.trim()
        if (!baseUrl.contains("://")) {
            baseUrl = "https://$baseUrl"
        }
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.dropLast(1)
        }
        val targetUrl = "$baseUrl/rest/v1/$tableName"

        val request = Request.Builder()
            .url(targetUrl)
            .header("apikey", serviceRoleKey)
            .header("Authorization", "Bearer $serviceRoleKey")
            .header("Content-Type", "application/json")
            .header("Prefer", "resolution=merge-duplicates")
            .post(rows.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val body = response.body?.string() ?: ""
                throw Exception("Upsert failed on table '$tableName' (HTTP ${response.code}): $body")
            }
        }
    }
}
