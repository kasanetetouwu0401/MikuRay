package com.v2ray.ang.util

import android.content.Context
import android.net.Uri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Utility to download banner images from various URLs ✨
 *
 * Architecture: each site has its own [SiteResolver] that knows how to find
 * the direct image URL + the required referer. Resolvers are tried in order 
 * of priority; the first one matching the domain is used.
 * If none match, it falls back to [GenericOgImageResolver] which attempts to 
 * read the og:image / twitter:image meta tags from any page.
 *
 * Specifically supported sites:
 * - Pixiv (artworks page → ajax API)
 * - Twitter / X (status page → og:image / twimg direct)
 * - Danbooru
 * - Gelbooru
 * - Safebooru
 * - Yande.re / Konachan (Moebooru)
 * - Zerochan
 * - e-shuushuu
 * - Anime-Pictures
 * - Imgur
 * - Reddit
 * - Tumblr
 * - Discord CDN (cdn.discordapp.com / media.discordapp.net)
 * - Bluesky (bsky.app)
 * - Pinterest
 * - Wikipedia / Wikimedia Commons
 *
 * Additionally, direct image URLs (.jpg/.png/.webp/etc.) are always supported as is.
 */

object BannerUrlDownloader {

    sealed class Result {
        data class Success(val localUri: Uri) : Result()
        data class Error(val message: String) : Result()
    }

    private data class ResolvedImage(val imageUrl: String, val referer: String? = null)

    private val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val resolvers: List<SiteResolver> = listOf(
        PixivResolver,
        TwitterResolver,
        DanbooruResolver,
        GelbooruResolver,
        SafebooruResolver,
        MoebooruResolver,
        ZerochanResolver,
        EShuushuuResolver,
        AnimePicturesResolver,
        ImgurResolver,
        RedditResolver,
        TumblrResolver,
        DiscordCdnResolver,
        BlueskyResolver,
        PinterestResolver,
        WikimediaResolver,
    )

    private val fallbackResolver = GenericOgImageResolver

    suspend fun download(context: Context, inputUrl: String, prefix: String = "banner_url_"): Result {
        return withContext(Dispatchers.IO) {
            try {
                var trimmed = inputUrl.trim()
                if (trimmed.isEmpty()) {
                    return@withContext Result.Error("Empty URL")
                }

                if (!trimmed.startsWith("http://", ignoreCase = true) &&
                    !trimmed.startsWith("https://", ignoreCase = true)
                ) {
                    trimmed = "https://$trimmed"
                }

                val resolved = resolve(trimmed)
                    ?: return@withContext Result.Error("Cannot find image from this URL")

                val loadTarget: Any = if (resolved.referer != null) {
                    GlideUrl(
                        resolved.imageUrl,
                        LazyHeaders.Builder()
                            .addHeader("Referer", resolved.referer)
                            .addHeader("User-Agent", USER_AGENT)
                            .build()
                    )
                } else {
                    resolved.imageUrl
                }

                val glideCacheFile = Glide.with(context.applicationContext)
                    .downloadOnly()
                    .load(loadTarget)
                    .submit()
                    .get()

                val tempFile = File(context.cacheDir, "${prefix}download_temp_${System.currentTimeMillis()}.jpg")
                glideCacheFile.copyTo(tempFile, overwrite = true)

                Result.Success(Uri.fromFile(tempFile))
            } catch (e: Exception) {
                e.printStackTrace()
                Result.Error("Glide failed to load image: ${e.localizedMessage ?: "Unknown Error"}")
            }
        }
    }

    private fun resolve(inputUrl: String): ResolvedImage? {
        if (HttpUtil2.isDirectImageUrl(inputUrl)) {
            return ResolvedImage(inputUrl, HttpUtil2.refererForKnownCdn(inputUrl))
        }

        val matchedResolver = resolvers.firstOrNull { it.matches(inputUrl) }
        if (matchedResolver != null) {
            try {
                matchedResolver.resolve(inputUrl)?.let { return it }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return try {
            fallbackResolver.resolve(inputUrl)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private interface SiteResolver {
        fun matches(url: String): Boolean
        fun resolve(url: String): ResolvedImage?
    }

    private object PixivResolver : SiteResolver {
        private val idPattern = Pattern.compile("""pixiv\.net/(?:[a-z]+/)?artworks/(\d+)""")

        override fun matches(url: String) = url.contains("pixiv.net", ignoreCase = true)

        override fun resolve(url: String): ResolvedImage? {
            val matcher = idPattern.matcher(url)
            if (!matcher.find()) {
                return GenericOgImageResolver.resolve(url, referer = "https://www.pixiv.net/")
            }
            val illustId = matcher.group(1) ?: return null

            val apiUrl = "https://www.pixiv.net/ajax/illust/$illustId"
            val json = HttpUtil2.fetchText(apiUrl, referer = "https://www.pixiv.net/")
                ?: return GenericOgImageResolver.resolve(url, referer = "https://www.pixiv.net/")

            return try {
                val root = JSONObject(json)
                val body = root.optJSONObject("body") ?: return null
                val urls = body.optJSONObject("urls")
                val imageUrl = urls?.optString("original")
                    ?.takeIf { it.isNotBlank() }
                    ?: urls?.optString("regular")
                    ?: urls?.optString("small")
                if (imageUrl.isNullOrBlank()) null
                else ResolvedImage(imageUrl, referer = "https://www.pixiv.net/")
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private object TwitterResolver : SiteResolver {
        override fun matches(url: String) =
            url.contains("twitter.com", ignoreCase = true) || url.contains("x.com", ignoreCase = true)

        override fun resolve(url: String): ResolvedImage? {
            val fixedUrl = url
                .replace("://twitter.com", "://api.vxtwitter.com")
                .replace("://x.com", "://api.vxtwitter.com")
            val json = HttpUtil2.fetchText(fixedUrl)
            
            if (json != null) {
                try {
                    val root = JSONObject(json)
                    val mediaArray = root.optJSONArray("media_extended")
                    if (mediaArray != null && mediaArray.length() > 0) {
                        val firstMedia = mediaArray.getJSONObject(0)
                        val mediaUrl = firstMedia.optString("url")
                        if (mediaUrl.isNotBlank()) return ResolvedImage(mediaUrl)
                    }
                } catch (_: Exception) {
                    // Ignore exception
                }
            }

            return GenericOgImageResolver.resolve(url)
        }
    }

    private object DanbooruResolver : SiteResolver {
        override fun matches(url: String) = url.contains("danbooru.donmai.us", ignoreCase = true)
        override fun resolve(url: String) = GenericOgImageResolver.resolve(url)
    }

    private object GelbooruResolver : SiteResolver {
        override fun matches(url: String) = url.contains("gelbooru.com", ignoreCase = true)
        override fun resolve(url: String) = GenericOgImageResolver.resolve(url)
    }

    private object SafebooruResolver : SiteResolver {
        override fun matches(url: String) = url.contains("safebooru.org", ignoreCase = true)
        override fun resolve(url: String) = GenericOgImageResolver.resolve(url)
    }

    private object MoebooruResolver : SiteResolver {
        override fun matches(url: String) =
            url.contains("yande.re", ignoreCase = true) || url.contains("konachan.com", ignoreCase = true)
        override fun resolve(url: String) = GenericOgImageResolver.resolve(url)
    }

    private object ZerochanResolver : SiteResolver {
        override fun matches(url: String) = url.contains("zerochan.net", ignoreCase = true)
        override fun resolve(url: String) = GenericOgImageResolver.resolve(url)
    }

    private object EShuushuuResolver : SiteResolver {
        override fun matches(url: String) = url.contains("e-shuushuu.net", ignoreCase = true)
        override fun resolve(url: String) = GenericOgImageResolver.resolve(url)
    }

    private object AnimePicturesResolver : SiteResolver {
        override fun matches(url: String) = url.contains("anime-pictures.net", ignoreCase = true)
        override fun resolve(url: String) = GenericOgImageResolver.resolve(url)
    }

    private object ImgurResolver : SiteResolver {
        private val idPattern = Pattern.compile("""imgur\.com/(?:a/|gallery/)?([a-zA-Z0-9]{5,})""")

        override fun matches(url: String) = url.contains("imgur.com", ignoreCase = true)

        override fun resolve(url: String): ResolvedImage? {
            if (HttpUtil2.isDirectImageUrl(url)) return ResolvedImage(url)
            val matcher = idPattern.matcher(url)
            if (matcher.find()) {
                val id = matcher.group(1)
                if (!id.isNullOrBlank()) {
                    val direct = "https://i.imgur.com/$id.jpg"
                    if (HttpUtil2.headOk(direct)) return ResolvedImage(direct)
                }
            }
            return GenericOgImageResolver.resolve(url)
        }
    }

    private object RedditResolver : SiteResolver {
        override fun matches(url: String) =
            url.contains("reddit.com", ignoreCase = true) || url.contains("redd.it", ignoreCase = true)
        override fun resolve(url: String) = GenericOgImageResolver.resolve(url)
    }

    private object TumblrResolver : SiteResolver {
        override fun matches(url: String) = url.contains("tumblr.com", ignoreCase = true)
        override fun resolve(url: String) = GenericOgImageResolver.resolve(url)
    }

    private object DiscordCdnResolver : SiteResolver {
        override fun matches(url: String) =
            url.contains("cdn.discordapp.com", ignoreCase = true) ||
                url.contains("media.discordapp.net", ignoreCase = true)
        override fun resolve(url: String) = ResolvedImage(url)
    }

    private object BlueskyResolver : SiteResolver {
        override fun matches(url: String) = url.contains("bsky.app", ignoreCase = true)
        override fun resolve(url: String) = GenericOgImageResolver.resolve(url)
    }

    private object PinterestResolver : SiteResolver {
        override fun matches(url: String) =
            url.contains("pinterest.com", ignoreCase = true) || url.contains("pin.it", ignoreCase = true)
        override fun resolve(url: String) = GenericOgImageResolver.resolve(url)
    }

    private object WikimediaResolver : SiteResolver {
        override fun matches(url: String) =
            url.contains("wikipedia.org", ignoreCase = true) || url.contains("wikimedia.org", ignoreCase = true)
        override fun resolve(url: String) = GenericOgImageResolver.resolve(url)
    }

    private object GenericOgImageResolver {
        fun resolve(url: String, referer: String? = null): ResolvedImage? {
            val html = HttpUtil2.fetchText(url, referer = referer) ?: return null
            val imageUrl = extractMetaImage(html) ?: return null
            val absoluteUrl = HttpUtil2.toAbsoluteUrl(imageUrl, url)
            return ResolvedImage(absoluteUrl, referer ?: HttpUtil2.refererForKnownCdn(absoluteUrl))
        }

        private fun extractMetaImage(html: String): String? {
            val patterns = listOf(
                """<meta[^>]+(?:property|name)\s*=\s*["'](?:og:image(?::secure_url)?|twitter:image(?::src)?)["'][^>]*content\s*=\s*["']([^"']+)["']""",
                """<meta[^>]+content\s*=\s*["']([^"']+)["'][^>]*(?:property|name)\s*=\s*["'](?:og:image(?::secure_url)?|twitter:image(?::src)?)["']"""
            )
            for (p in patterns) {
                val matcher = Pattern.compile(p, Pattern.CASE_INSENSITIVE).matcher(html)
                if (matcher.find()) {
                    val found = matcher.group(1)
                    if (!found.isNullOrBlank()) return found
                }
            }
            return null
        }
    }

    private object HttpUtil2 {

        fun isDirectImageUrl(url: String): Boolean {
            val lower = url.lowercase()
            val path = lower.substringBefore("?")
            return path.endsWith(".jpg") ||
                path.endsWith(".jpeg") ||
                path.endsWith(".png") ||
                path.endsWith(".webp") ||
                path.endsWith(".gif") ||
                path.endsWith(".avif") ||
                path.endsWith(".bmp") ||
                lower.contains("i.pximg.net") ||
                lower.contains("img3.gelbooru.com") ||
                lower.contains("img4.gelbooru.com") ||
                lower.contains("cdn.donmai.us") ||
                lower.contains("i.imgur.com") ||
                lower.contains("pbs.twimg.com") ||
                lower.contains("i.redd.it") ||
                lower.contains("media.tumblr.com") ||
                lower.contains("cdn.discordapp.com") ||
                lower.contains("media.discordapp.net") ||
                lower.contains("upload.wikimedia.org") ||
                lower.contains("static.zerochan.net")
        }

        fun refererForKnownCdn(imageUrl: String): String? {
            val lower = imageUrl.lowercase()
            return when {
                lower.contains("i.pximg.net") -> "https://www.pixiv.net/"
                lower.contains("pbs.twimg.com") -> "https://twitter.com/"
                lower.contains("static.zerochan.net") -> "https://www.zerochan.net/"
                else -> null
            }
        }

        fun toAbsoluteUrl(maybeRelative: String, pageUrl: String): String {
            return try {
                if (maybeRelative.startsWith("http://") || maybeRelative.startsWith("https://")) {
                    maybeRelative
                } else if (maybeRelative.startsWith("//")) {
                    "https:$maybeRelative"
                } else {
                    URL(URL(pageUrl), maybeRelative).toString()
                }
            } catch (e: Exception) {
                maybeRelative
            }
        }

        fun fetchText(url: String, referer: String? = null, maxChars: Int = 4096 * 12): String? {
            return try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json,*/*;q=0.9")
                if (referer != null) connection.setRequestProperty("Referer", referer)

                if (connection.responseCode !in 200..299) {
                    connection.disconnect()
                    return null
                }

                val text = connection.inputStream.bufferedReader().use { reader ->
                    val buf = CharArray(8192)
                    val sb = StringBuilder()
                    var total = 0
                    while (total < maxChars) {
                        val n = reader.read(buf, 0, minOf(buf.size, maxChars - total))
                        if (n < 0) break
                        sb.append(buf, 0, n)
                        total += n
                    }
                    sb.toString()
                }
                connection.disconnect()
                text
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        fun headOk(url: String): Boolean {
            return try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 6_000
                connection.readTimeout = 6_000
                connection.setRequestProperty("User-Agent", USER_AGENT)
                val ok = connection.responseCode in 200..299
                connection.disconnect()
                ok
            } catch (e: Exception) {
                false
            }
        }
    }
}
