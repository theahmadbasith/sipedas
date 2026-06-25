package com.sipedas.ponorogo.parser

data class ParsedReport(
    val namaDanru: String = "",
    val nomorSPT: String = "",
    val lokasi: String = "",
    val hari: String = "",
    val tanggal: String = "",
    val identitas: String = "",
    val personil: String = "",
    val danru: String = "",
    val keterangan: String = ""
)

object SipedasParser {
    fun parseLaporan(text: String): ParsedReport {
        val namaDanru = extractDanru(text)
        var nomorSPT = ""
        var lokasi = ""
        var hari = ""
        var tanggal = ""
        var identitas = ""
        var personil = ""
        val danru = namaDanru
        var keterangan = ""

        val lines = text.lines().map { it.trim().replace("*", "").replace("_", "") }.filter { it.isNotEmpty() }
        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase()
            
            when {
                lower.startsWith("spt") || lower.startsWith("no spt") || lower.startsWith("nomor spt") -> {
                    nomorSPT = getFieldVal(line, i, lines)
                }
                lower.startsWith("lokasi") || lower.startsWith("tempat") -> {
                    lokasi = getFieldVal(line, i, lines)
                }
                lower.startsWith("hari") -> {
                    hari = getFieldVal(line, i, lines)
                }
                lower.startsWith("tanggal") || lower.startsWith("tgl") -> {
                    tanggal = getFieldVal(line, i, lines)
                }
                lower.startsWith("identitas") || lower.startsWith("nama pelanggaran") -> {
                    identitas = getFieldVal(line, i, lines)
                }
                lower.startsWith("personil") || lower.startsWith("anggota") -> {
                    var pVal = getFieldVal(line, i, lines)
                    if (pVal.startsWith("(") && pVal.endsWith(")")) {
                        pVal = pVal.substring(1, pVal.length - 1).trim()
                    }
                    personil = pVal
                }
                lower.startsWith("keterangan") || lower.startsWith("ket") -> {
                    keterangan = getFieldVal(line, i, lines)
                }
            }
        }

        // Advanced Fallbacks if any crucial field remains empty
        if (nomorSPT.isEmpty()) {
            val sptRegex = Regex("(?i)(?:no(?:mor)?\\s+)?spt\\s*:\\s*([^\\n]+)")
            sptRegex.find(text)?.let { nomorSPT = it.groupValues[1].trim().replace("*", "").replace("_", "") }
        }
        if (lokasi.isEmpty()) {
            // If location is not matched with label, look for standard layout patterns, 
            // e.g. underneath "pelaksanaan kegiatan" (Mohon ijin Melaporkan Hasil Pelaksanaan Kegiatan)
            for (i in lines.indices) {
                val line = lines[i]
                val lower = line.lowercase()
                if (lower.contains("pelaksanaankegiatan") || lower.contains("pelaksanaan kegiatan")) {
                    if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1]
                        val nextLower = nextLine.lowercase()
                        val isHeader = nextLower.startsWith("spt") || nextLower.startsWith("no spt") || 
                                       nextLower.startsWith("lokasi") || nextLower.startsWith("tempat") ||
                                       nextLower.startsWith("hari") || nextLower.startsWith("tanggal") || 
                                       nextLower.startsWith("tgl") || nextLower.startsWith("identitas") ||
                                       nextLower.startsWith("personil") || nextLower.startsWith("anggota") ||
                                       nextLower.startsWith("keterangan") || nextLower.startsWith("ket") ||
                                       nextLower.startsWith("danru")
                        if (nextLine.isNotEmpty() && !isHeader && !nextLine.contains(":")) {
                            lokasi = nextLine
                            break
                        }
                    }
                }
            }
        }
        if (tanggal.isEmpty()) {
            val tglRegex = Regex("(?i)(?:tang|t)gal\\s*:\\s*([^\\n]+)")
            tglRegex.find(text)?.let { tanggal = it.groupValues[1].trim().replace("*", "").replace("_", "") }
        }

        return ParsedReport(
            namaDanru = namaDanru,
            nomorSPT = nomorSPT,
            lokasi = lokasi,
            hari = hari,
            tanggal = tanggal,
            identitas = identitas,
            personil = personil,
            danru = danru,
            keterangan = keterangan
        )
    }

    private fun getFieldVal(line: String, index: Int, lines: List<String>): String {
        val colonIndex = line.indexOf(":")
        if (colonIndex != -1) {
            val valAfter = line.substring(colonIndex + 1).trim()
            if (valAfter.isNotEmpty()) {
                return valAfter
            }
        }
        
        // Check next line if no colon or value is empty
        if (index + 1 < lines.size) {
            val nextLine = lines[index + 1]
            val nextLower = nextLine.lowercase()
            val isNextHeader = nextLower.startsWith("spt") || nextLower.startsWith("no spt") || 
                               nextLower.startsWith("lokasi") || nextLower.startsWith("tempat") ||
                               nextLower.startsWith("hari") || nextLower.startsWith("tanggal") || 
                               nextLower.startsWith("tgl") || nextLower.startsWith("identitas") ||
                               nextLower.startsWith("personil") || nextLower.startsWith("anggota") ||
                               nextLower.startsWith("keterangan") || nextLower.startsWith("ket") ||
                               nextLower.startsWith("danru")
            if (!isNextHeader) {
                return nextLine
            }
        }
        return ""
    }

    private fun extractDanru(text: String): String {
        // Clean text by removing asterisks (bold) and underscores
        val cleanText = text.replace("*", "").replace("_", "")
        val lines = cleanText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val parenRegex = Regex("\\(([^)]+)\\)")

        for (i in lines.indices) {
            val line = lines[i]
            val lower = line.lowercase()

            if (lower.contains("danru")) {
                // 1. Same-line parenthesis: e.g. "Danru 2 (basith)" or "*Danru 2* (basith)"
                val parenMatch = parenRegex.find(line)
                if (parenMatch != null) {
                    val candidate = parenMatch.groupValues[1].trim()
                    if (candidate.isNotEmpty() && !candidate.lowercase().contains("danru")) {
                        val cleaned = cleanName(candidate)
                        if (cleaned.isNotEmpty()) return cleaned
                    }
                }

                // 2. Colon match: e.g. "Danru : basith" or "Danru 2 : basith"
                if (line.contains(":")) {
                    val candidate = line.substringAfter(":").trim()
                    // Extract if wrapped in parenthesis after colon
                    val innerParenMatch = parenRegex.find(candidate)
                    val finalCandidate = if (innerParenMatch != null) {
                        innerParenMatch.groupValues[1].trim()
                    } else {
                        candidate
                    }
                    if (finalCandidate.isNotEmpty() && !finalCandidate.lowercase().contains("danru")) {
                        val cleaned = cleanName(finalCandidate)
                        if (cleaned.isNotEmpty()) return cleaned
                    }
                }

                // 3. Prefix match: e.g. "Danru basith" or "Danru 2 basith" or "Danru regu 2 basith"
                val prefixRegex = Regex("(?i)danru(?:\\s+\\d+)?(?:\\s+regu\\s+\\d+)?\\s+([A-Za-z\\s]+)$")
                val prefixMatch = prefixRegex.find(line)
                if (prefixMatch != null) {
                    val candidate = prefixMatch.groupValues[1].trim()
                    if (candidate.isNotEmpty() && !candidate.lowercase().contains("danru")) {
                        val cleaned = cleanName(candidate)
                        if (cleaned.isNotEmpty()) return cleaned
                    }
                }

                // 4. Look at the next non-empty lines!
                // We check up to 2 subsequent non-empty lines.
                for (j in 1..2) {
                    if (i + j < lines.size) {
                        val nextLine = lines[i + j]
                        val nextLower = nextLine.lowercase()

                        // If the next line is a separate header, stop scanning
                        if (nextLower.startsWith("spt:") || nextLower.startsWith("no spt:") || nextLower.startsWith("nomor spt:") ||
                            nextLower.startsWith("lokasi:") || nextLower.startsWith("tempat:") ||
                            nextLower.startsWith("hari:") || nextLower.startsWith("tanggal:") || nextLower.startsWith("tgl:") ||
                            nextLower.startsWith("identitas:") || nextLower.startsWith("personil:") || nextLower.startsWith("anggota:") ||
                            nextLower.startsWith("keterangan:") || nextLower.startsWith("ket:") || nextLower.contains("melaporkan") ||
                            nextLower.contains("hormat") || nextLower.contains("yth") || nextLower.contains("kepada")) {
                            break
                        }

                        // Check if it's wrapped in parentheses like "( basItH )"
                        val nextParenMatch = parenRegex.find(nextLine)
                        if (nextParenMatch != null) {
                            val candidate = nextParenMatch.groupValues[1].trim()
                            if (candidate.isNotEmpty() && !candidate.lowercase().contains("danru")) {
                                val cleaned = cleanName(candidate)
                                if (cleaned.isNotEmpty()) return cleaned
                            }
                        } else {
                            // Check if nextLine is a valid plain name
                            val isLetterOrSpace = nextLine.all { it.isLetter() || it.isWhitespace() }
                            if (isLetterOrSpace && nextLine.isNotEmpty() && nextLine.split("\\s+".toRegex()).size <= 4) {
                                val cleaned = cleanName(nextLine)
                                if (cleaned.isNotEmpty()) return cleaned
                            }
                        }
                    }
                }
            }
        }

        // Global fallback search
        val globalRegexes = listOf(
            Regex("(?i)danru\\s*:\\s*([^\\n]+)"),
            Regex("(?i)danru\\s+\\d+\\s*\\(([^)]+)\\)"),
            Regex("(?i)danru\\s+([^\\n\\s]+)")
        )
        for (regex in globalRegexes) {
            val match = regex.find(cleanText)
            if (match != null) {
                val candidate = match.groupValues[1].trim()
                if (candidate.isNotEmpty()) {
                    val cleaned = cleanName(candidate)
                    if (cleaned.isNotEmpty()) return cleaned
                }
            }
        }

        return ""
    }

    private fun cleanName(name: String): String {
        var cleaned = name.replace(Regex("[():]"), "").trim()
        if (cleaned.lowercase().startsWith("danru ")) {
            cleaned = cleaned.substring(6).trim()
        }
        return cleaned.split(" ")
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}
