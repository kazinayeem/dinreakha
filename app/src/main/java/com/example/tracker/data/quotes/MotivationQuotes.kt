package com.example.tracker.data.quotes

import com.example.tracker.data.repo.QuoteCategory
import com.example.tracker.data.repo.QuoteLanguage

data class MotivationQuote(
    val id: Int,
    val text: String,
    val source: String,
    val category: QuoteCategory,
    val language: QuoteLanguage,
)

object MotivationQuotes {
    private val BASE = listOf(
        // ==================== Islamic / Ayat & Hadith (বাংলা) ====================
        MotivationQuote(
            id = 1,
            text = "নিশ্চয়ই কষ্টের সাথেই স্বস্তি আছে।",
            source = "সূরা আল-ইনশিরাহ (৯৪:৬)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 2,
            text = "যে ব্যক্তি আল্লাহর উপর ভরসা করে, তিনিই তার জন্য যথেষ্ট।",
            source = "সূরা আত-তালাক্ব (৬৫:৩)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 3,
            text = "তোমাদের মধ্যে সর্বোত্তম সে, যার চরিত্র সবচেয়ে সুন্দর।",
            source = "সহীহ বুখারী (৬০৩৫)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 4,
            text = "জ্ঞান অন্বেষণ করা প্রত্যেক মুসলিমের উপর ফরজ।",
            source = "সুনান ইবনে মাজাহ (২২৪)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 5,
            text = "তোমাদের মধ্যে সর্বোত্তম সেই ব্যক্তি, যে কুরআন শেখে এবং অন্যকে শেখায়।",
            source = "সহীহ বুখারী (৫০২৭)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 6,
            text = "ধৈর্য ধরুন, নিশ্চয়ই আল্লাহর প্রতিশ্রুতি সত্য।",
            source = "সূরা আর-রূম (৩০:৬০)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 7,
            text = "হে আমাদের পালনকর্তা, আমাদের জ্ঞান বৃদ্ধি করে দিন।",
            source = "সূরা ত্বা-হা (২০:১১৪)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 8,
            text = "আল্লাহর রহমত থেকে কখনও নিরাশ হয়ো না।",
            source = "সূরা ইউসুফ (১২:৮৭)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 9,
            text = "যে আল্লাহকে ভয় করে, আল্লাহ তার জন্য বের হওয়ার পথ খুলে দেন।",
            source = "সূরা আত-তালাক্ব (৬৫:২)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 10,
            text = "সময় থাকতে পাঁচটি জিনিসকে মূল্যবান মনে করো: যৌবন, স্বাস্থ্য, প্রাচুর্য, অবসর ও জীবন।",
            source = "মুসতাদরাক আল-হাকিম",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 11,
            text = "নিশ্চয়ই নামায অশ্লীল ও মন্দ কাজ থেকে বিরত রাখে।",
            source = "সূরা আল-আনকাবুত (২৯:৪৫)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 12,
            text = "বান্দা যতক্ষণ তার ভাইয়ের সাহায্যে থাকে, আল্লাহও ততক্ষণ তার সাহায্যে থাকেন।",
            source = "সহীহ মুসলিম (২৬৯৯)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 13,
            text = "আল্লাহ ধৈর্যশীলদের সাথে আছেন।",
            source = "সূরা আল-বাকারাহ (২:১৫৩)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 14,
            text = "সব কাজের ফলাফল নিয়তের উপর নির্ভরশীল।",
            source = "সহীহ বুখারী (১)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.BANGLA,
        ),

        // ==================== Islamic / Ayat & Hadith (English) ====================
        MotivationQuote(
            id = 15,
            text = "Indeed, with hardship comes ease.",
            source = "Surah Ash-Sharh (94:6)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 16,
            text = "And whoever relies upon Allah – then He is sufficient for him.",
            source = "Surah At-Talaq (65:3)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 17,
            text = "The best among you are those who have the best manners and character.",
            source = "Sahih al-Bukhari (6035)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 18,
            text = "Seeking knowledge is an obligation upon every Muslim.",
            source = "Sunan Ibn Majah (224)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 19,
            text = "The best of you are those who learn the Quran and teach it.",
            source = "Sahih al-Bukhari (5027)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 20,
            text = "So be patient. Indeed, the promise of Allah is truth.",
            source = "Surah Ar-Rum (30:60)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 21,
            text = "My Lord, increase me in knowledge.",
            source = "Surah Taha (20:114)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 22,
            text = "Never despair of the mercy of Allah.",
            source = "Surah Yusuf (12:87)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 23,
            text = "Take benefit of five before five: youth, health, wealth, free time, and life.",
            source = "Al-Hakim",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 24,
            text = "Actions are judged by intentions.",
            source = "Sahih al-Bukhari (1)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 25,
            text = "Indeed, Allah is with the patient.",
            source = "Surah Al-Baqarah (2:153)",
            category = QuoteCategory.ISLAMIC,
            language = QuoteLanguage.ENGLISH,
        ),

        // ==================== Productivity & Mindset (বাংলা) ====================
        MotivationQuote(
            id = 26,
            text = "ধারাবাহিক ছোট ছোট অভ্যাসই জীবনে অবিশ্বাস্য রূপান্তর ঘটায়।",
            source = "দৈনিক নিয়মানুবর্তিতা",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 27,
            text = "আজকের অলসতা কালকের সবচেয়ে বড় আফসোস।",
            source = "সময় ব্যবস্থাপনা",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 28,
            text = "সময়কে নিয়ন্ত্রণ করুন, নয়তো সময় আপনাকে নিয়ন্ত্রণ করবে।",
            source = "জীবন দর্শন",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 29,
            text = "গন্তব্যে পৌঁছানোর চেয়ে প্রতিদিন এক কদম এগিয়ে চলা বেশি গুরুত্বপূর্ণ।",
            source = "অভিযাত্রা",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 30,
            text = "গভীর মনোযোগই আধুনিক জগতের সবচেয়ে মূল্যবান দক্ষতা।",
            source = "ডিপ ওয়ার্ক",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 31,
            text = "শুরু করার জন্য মহান হতে হবে না, কিন্তু মহান হতে হলে শুরু করতে হবে।",
            source = "অনুপ্রেরণা",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 32,
            text = "যে নিজের সকালকে নিয়ন্ত্রণ করতে পারে, সে নিজের দিনকে জয় করতে পারে।",
            source = "দিনের সূচনা",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.BANGLA,
        ),
        MotivationQuote(
            id = 33,
            text = "পরিকল্পনাহীন লক্ষ্য কেবল একটি ইচ্ছামাত্র।",
            source = "লক্ষ্য নির্ধারণ",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.BANGLA,
        ),

        // ==================== Productivity & Mindset (English) ====================
        MotivationQuote(
            id = 34,
            text = "We are what we repeatedly do. Excellence, then, is not an act, but a habit.",
            source = "Will Durant",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 35,
            text = "Small daily improvements over time lead to stunning results.",
            source = "Atomic Habits",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 36,
            text = "Discipline is choosing between what you want now and what you want most.",
            source = "Abraham Lincoln",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 37,
            text = "The secret of getting ahead is getting started.",
            source = "Mark Twain",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 38,
            text = "Focus is a muscle. The more you protect it from distractions, the stronger it becomes.",
            source = "Deep Work",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 39,
            text = "Master your morning, master your day.",
            source = "Daily Rhythm",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 40,
            text = "Consistency beats talent when talent doesn't work hard.",
            source = "Tim Notke",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.ENGLISH,
        ),
        MotivationQuote(
            id = 41,
            text = "Do something today that your future self will thank you for.",
            source = "Sean Patrick Flanery",
            category = QuoteCategory.PRODUCTIVITY,
            language = QuoteLanguage.ENGLISH,
        ),
    )

    /** The 42 hand-picked splash quotes plus the big library (~580 in all). */
    val ALL_QUOTES: List<MotivationQuote> by lazy { BASE + QuoteLibrary.ALL }

    fun matching(category: QuoteCategory, language: QuoteLanguage): List<MotivationQuote> =
        ALL_QUOTES.filter { q ->
            (category == QuoteCategory.ALL || q.category == category) &&
                (language == QuoteLanguage.BOTH || q.language == language)
        }.ifEmpty { ALL_QUOTES }

    /**
     * Deterministic pick for a day + slot: the same reminder always shows the same quote, and
     * consecutive slots walk through the whole list (step is a prime, so no repeats until it wraps).
     */
    fun pick(epochDay: Long, slot: Int, category: QuoteCategory, language: QuoteLanguage): MotivationQuote {
        val list = matching(category, language)
        val n = list.size.toLong()
        val i = Math.floorMod((epochDay * 3 + slot) * 7919L, n)
        return list[i.toInt()]
    }

    fun getRandomQuote(category: QuoteCategory, language: QuoteLanguage): MotivationQuote {
        val candidates = ALL_QUOTES.filter { q ->
            (category == QuoteCategory.ALL || q.category == category) &&
            (language == QuoteLanguage.BOTH || q.language == language)
        }
        return candidates.randomOrNull() ?: ALL_QUOTES.random()
    }
}

