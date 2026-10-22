package com.example.data.util

/**
 * Resolves high-resolution flag image URLs (via FlagCDN),
 * country flag emojis, and standardized 3-letter codes strictly for
 * Senior National Teams in International Cricket and International Football.
 */
object TeamFlagResolver {

    data class TeamVisual(
        val code: String,
        val flagUrl: String,
        val emoji: String,
        val countryName: String
    )

    // Regex to reject any Under-17, Under-18, Under-19, Under-20, Under-21, Under-23, Youth, Emerging, A-Team, Academy, Reserve, Women, or Club/Domestic matches
    private val nonSeniorRegex = Regex(
        """\b(u-?15|u-?16|u-?17|u-?18|u-?19|u-?20|u-?21|u-?22|u-?23|under[-\s]?\d+|youth|junior|emerging|lions|academy|reserve|reserves|women|women's|womens|wom|female|ladies|girls|[a-z]{2,3}-w\b|india a|bangladesh a|pakistan a|sri lanka a|australia a|england a|south africa a|new zealand a|west indies a|afghanistan a|zimbabwe a|ireland a|nepal a|board xi|president's xi|pm xi|xi|legends|masters|veterans|ranji|duleep|irani|vijay hazare|syed mushtaq|county|sheffield|plunket|ford trophy|marsh cup|vitality blast|the hundred|wpl|wbbl|ipl|bpl|psl|cpl|bbl|sa20|ilt20|lpl|mlc|mls|champions league|premier league|laliga|la liga|serie a|bundesliga|ligue 1|saudi pro|club)\b""",
        RegexOption.IGNORE_CASE
    )

    // Senior National Main Teams (International Cricket & International Football)
    private val nationalTeamMap: Map<String, TeamVisual> = mapOf(
        // Asian National Teams
        "bangladesh" to TeamVisual("BAN", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Bangladesh"),
        "ban" to TeamVisual("BAN", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Bangladesh"),
        "bd" to TeamVisual("BAN", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Bangladesh"),
        "india" to TeamVisual("IND", "https://flagcdn.com/w160/in.png", "🇮🇳", "India"),
        "ind" to TeamVisual("IND", "https://flagcdn.com/w160/in.png", "🇮🇳", "India"),
        "pakistan" to TeamVisual("PAK", "https://flagcdn.com/w160/pk.png", "🇵🇰", "Pakistan"),
        "pak" to TeamVisual("PAK", "https://flagcdn.com/w160/pk.png", "🇵🇰", "Pakistan"),
        "sri lanka" to TeamVisual("SL", "https://flagcdn.com/w160/lk.png", "🇱🇰", "Sri Lanka"),
        "sl" to TeamVisual("SL", "https://flagcdn.com/w160/lk.png", "🇱🇰", "Sri Lanka"),
        "sri" to TeamVisual("SL", "https://flagcdn.com/w160/lk.png", "🇱🇰", "Sri Lanka"),
        "afghanistan" to TeamVisual("AFG", "https://flagcdn.com/w160/af.png", "🇦🇫", "Afghanistan"),
        "afg" to TeamVisual("AFG", "https://flagcdn.com/w160/af.png", "🇦🇫", "Afghanistan"),
        "nepal" to TeamVisual("NEP", "https://flagcdn.com/w160/np.png", "🇳🇵", "Nepal"),
        "nep" to TeamVisual("NEP", "https://flagcdn.com/w160/np.png", "🇳🇵", "Nepal"),
        "japan" to TeamVisual("JPN", "https://flagcdn.com/w160/jp.png", "🇯🇵", "Japan"),
        "jpn" to TeamVisual("JPN", "https://flagcdn.com/w160/jp.png", "🇯🇵", "Japan"),
        "south korea" to TeamVisual("KOR", "https://flagcdn.com/w160/kr.png", "🇰🇷", "South Korea"),
        "korea republic" to TeamVisual("KOR", "https://flagcdn.com/w160/kr.png", "🇰🇷", "South Korea"),
        "kor" to TeamVisual("KOR", "https://flagcdn.com/w160/kr.png", "🇰🇷", "South Korea"),
        "saudi arabia" to TeamVisual("KSA", "https://flagcdn.com/w160/sa.png", "🇸🇦", "Saudi Arabia"),
        "ksa" to TeamVisual("KSA", "https://flagcdn.com/w160/sa.png", "🇸🇦", "Saudi Arabia"),
        "qatar" to TeamVisual("QAT", "https://flagcdn.com/w160/qa.png", "🇶🇦", "Qatar"),
        "qat" to TeamVisual("QAT", "https://flagcdn.com/w160/qa.png", "🇶🇦", "Qatar"),
        "uae" to TeamVisual("UAE", "https://flagcdn.com/w160/ae.png", "🇦🇪", "United Arab Emirates"),
        "united arab emirates" to TeamVisual("UAE", "https://flagcdn.com/w160/ae.png", "🇦🇪", "United Arab Emirates"),
        "iran" to TeamVisual("IRN", "https://flagcdn.com/w160/ir.png", "🇮🇷", "Iran"),
        "irn" to TeamVisual("IRN", "https://flagcdn.com/w160/ir.png", "🇮🇷", "Iran"),
        "iraq" to TeamVisual("IRQ", "https://flagcdn.com/w160/iq.png", "🇮🇶", "Iraq"),
        "uzbekistan" to TeamVisual("UZB", "https://flagcdn.com/w160/uz.png", "🇺🇿", "Uzbekistan"),
        "oman" to TeamVisual("OMA", "https://flagcdn.com/w160/om.png", "🇴🇲", "Oman"),
        "bahrain" to TeamVisual("BHR", "https://flagcdn.com/w160/bh.png", "🇧🇭", "Bahrain"),
        "jordan" to TeamVisual("JOR", "https://flagcdn.com/w160/jo.png", "🇯🇴", "Jordan"),
        "china" to TeamVisual("CHN", "https://flagcdn.com/w160/cn.png", "🇨🇳", "China"),
        "indonesia" to TeamVisual("IDN", "https://flagcdn.com/w160/id.png", "🇮🇩", "Indonesia"),
        "malaysia" to TeamVisual("MAS", "https://flagcdn.com/w160/my.png", "🇲🇾", "Malaysia"),
        "thailand" to TeamVisual("THA", "https://flagcdn.com/w160/th.png", "🇹🇭", "Thailand"),
        "hong kong" to TeamVisual("HKG", "https://flagcdn.com/w160/hk.png", "🇭🇰", "Hong Kong"),

        // Oceania National Teams
        "australia" to TeamVisual("AUS", "https://flagcdn.com/w160/au.png", "🇦🇺", "Australia"),
        "aus" to TeamVisual("AUS", "https://flagcdn.com/w160/au.png", "🇦🇺", "Australia"),
        "aus-w" to TeamVisual("AUS-W", "https://flagcdn.com/w160/au.png", "🇦🇺", "Australia Women"),
        "new zealand" to TeamVisual("NZ", "https://flagcdn.com/w160/nz.png", "🇳🇿", "New Zealand"),
        "nz" to TeamVisual("NZ", "https://flagcdn.com/w160/nz.png", "🇳🇿", "New Zealand"),
        "nz-w" to TeamVisual("NZ-W", "https://flagcdn.com/w160/nz.png", "🇳🇿", "New Zealand Women"),
        "nzl" to TeamVisual("NZ", "https://flagcdn.com/w160/nz.png", "🇳🇿", "New Zealand"),

        // European National Teams
        "england" to TeamVisual("ENG", "https://flagcdn.com/w160/gb-eng.png", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", "England"),
        "eng" to TeamVisual("ENG", "https://flagcdn.com/w160/gb-eng.png", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", "England"),
        "scotland" to TeamVisual("SCO", "https://flagcdn.com/w160/gb-sct.png", "🏴󠁧󠁢󠁳󠁣󠁴󠁿", "Scotland"),
        "sco" to TeamVisual("SCO", "https://flagcdn.com/w160/gb-sct.png", "🏴󠁧󠁢󠁳󠁣󠁴󠁿", "Scotland"),
        "wales" to TeamVisual("WAL", "https://flagcdn.com/w160/gb-wls.png", "🏴󠁧󠁢󠁷󠁬󠁳󠁿", "Wales"),
        "wal" to TeamVisual("WAL", "https://flagcdn.com/w160/gb-wls.png", "🏴󠁧󠁢󠁷󠁬󠁳󠁿", "Wales"),
        "ireland" to TeamVisual("IRE", "https://flagcdn.com/w160/ie.png", "🇮🇪", "Ireland"),
        "republic of ireland" to TeamVisual("IRE", "https://flagcdn.com/w160/ie.png", "🇮🇪", "Ireland"),
        "ire" to TeamVisual("IRE", "https://flagcdn.com/w160/ie.png", "🇮🇪", "Ireland"),
        "irl" to TeamVisual("IRE", "https://flagcdn.com/w160/ie.png", "🇮🇪", "Ireland"),
        "france" to TeamVisual("FRA", "https://flagcdn.com/w160/fr.png", "🇫🇷", "France"),
        "fra" to TeamVisual("FRA", "https://flagcdn.com/w160/fr.png", "🇫🇷", "France"),
        "spain" to TeamVisual("ESP", "https://flagcdn.com/w160/es.png", "🇪🇸", "Spain"),
        "esp" to TeamVisual("ESP", "https://flagcdn.com/w160/es.png", "🇪🇸", "Spain"),
        "germany" to TeamVisual("GER", "https://flagcdn.com/w160/de.png", "🇩🇪", "Germany"),
        "ger" to TeamVisual("GER", "https://flagcdn.com/w160/de.png", "🇩🇪", "Germany"),
        "portugal" to TeamVisual("POR", "https://flagcdn.com/w160/pt.png", "🇵🇹", "Portugal"),
        "por" to TeamVisual("POR", "https://flagcdn.com/w160/pt.png", "🇵🇹", "Portugal"),
        "italy" to TeamVisual("ITA", "https://flagcdn.com/w160/it.png", "🇮🇹", "Italy"),
        "ita" to TeamVisual("ITA", "https://flagcdn.com/w160/it.png", "🇮🇹", "Italy"),
        "netherlands" to TeamVisual("NED", "https://flagcdn.com/w160/nl.png", "🇳🇱", "Netherlands"),
        "ned" to TeamVisual("NED", "https://flagcdn.com/w160/nl.png", "🇳🇱", "Netherlands"),
        "belgium" to TeamVisual("BEL", "https://flagcdn.com/w160/be.png", "🇧🇪", "Belgium"),
        "bel" to TeamVisual("BEL", "https://flagcdn.com/w160/be.png", "🇧🇪", "Belgium"),
        "croatia" to TeamVisual("CRO", "https://flagcdn.com/w160/hr.png", "🇭🇷", "Croatia"),
        "cro" to TeamVisual("CRO", "https://flagcdn.com/w160/hr.png", "🇭🇷", "Croatia"),
        "switzerland" to TeamVisual("SUI", "https://flagcdn.com/w160/ch.png", "🇨🇭", "Switzerland"),
        "sui" to TeamVisual("SUI", "https://flagcdn.com/w160/ch.png", "🇨🇭", "Switzerland"),
        "denmark" to TeamVisual("DEN", "https://flagcdn.com/w160/dk.png", "🇩🇰", "Denmark"),
        "den" to TeamVisual("DEN", "https://flagcdn.com/w160/dk.png", "🇩🇰", "Denmark"),
        "poland" to TeamVisual("POL", "https://flagcdn.com/w160/pl.png", "🇵🇱", "Poland"),
        "pol" to TeamVisual("POL", "https://flagcdn.com/w160/pl.png", "🇵🇱", "Poland"),
        "ukraine" to TeamVisual("UKR", "https://flagcdn.com/w160/ua.png", "🇺🇦", "Ukraine"),
        "ukr" to TeamVisual("UKR", "https://flagcdn.com/w160/ua.png", "🇺🇦", "Ukraine"),
        "turkey" to TeamVisual("TUR", "https://flagcdn.com/w160/tr.png", "🇹🇷", "Turkey"),
        "türkiye" to TeamVisual("TUR", "https://flagcdn.com/w160/tr.png", "🇹🇷", "Turkey"),
        "tur" to TeamVisual("TUR", "https://flagcdn.com/w160/tr.png", "🇹🇷", "Turkey"),
        "austria" to TeamVisual("AUT", "https://flagcdn.com/w160/at.png", "🇦🇹", "Austria"),
        "aut" to TeamVisual("AUT", "https://flagcdn.com/w160/at.png", "🇦🇹", "Austria"),
        "serbia" to TeamVisual("SRB", "https://flagcdn.com/w160/rs.png", "🇷🇸", "Serbia"),
        "srb" to TeamVisual("SRB", "https://flagcdn.com/w160/rs.png", "🇷🇸", "Serbia"),
        "norway" to TeamVisual("NOR", "https://flagcdn.com/w160/no.png", "🇳🇴", "Norway"),
        "nor" to TeamVisual("NOR", "https://flagcdn.com/w160/no.png", "🇳🇴", "Norway"),
        "sweden" to TeamVisual("SWE", "https://flagcdn.com/w160/se.png", "🇸🇪", "Sweden"),
        "swe" to TeamVisual("SWE", "https://flagcdn.com/w160/se.png", "🇸🇪", "Sweden"),
        "czechia" to TeamVisual("CZE", "https://flagcdn.com/w160/cz.png", "🇨🇿", "Czech Republic"),
        "czech republic" to TeamVisual("CZE", "https://flagcdn.com/w160/cz.png", "🇨🇿", "Czech Republic"),
        "greece" to TeamVisual("GRE", "https://flagcdn.com/w160/gr.png", "🇬🇷", "Greece"),
        "hungary" to TeamVisual("HUN", "https://flagcdn.com/w160/hu.png", "🇭🇺", "Hungary"),
        "romania" to TeamVisual("ROU", "https://flagcdn.com/w160/ro.png", "🇷🇴", "Romania"),
        "slovakia" to TeamVisual("SVK", "https://flagcdn.com/w160/sk.png", "🇸🇰", "Slovakia"),
        "slovenia" to TeamVisual("SVN", "https://flagcdn.com/w160/si.png", "🇸🇮", "Slovenia"),

        // South & North American National Teams
        "argentina" to TeamVisual("ARG", "https://flagcdn.com/w160/ar.png", "🇦🇷", "Argentina"),
        "arg" to TeamVisual("ARG", "https://flagcdn.com/w160/ar.png", "🇦🇷", "Argentina"),
        "brazil" to TeamVisual("BRA", "https://flagcdn.com/w160/br.png", "🇧🇷", "Brazil"),
        "bra" to TeamVisual("BRA", "https://flagcdn.com/w160/br.png", "🇧🇷", "Brazil"),
        "uruguay" to TeamVisual("URU", "https://flagcdn.com/w160/uy.png", "🇺🇾", "Uruguay"),
        "uru" to TeamVisual("URU", "https://flagcdn.com/w160/uy.png", "🇺🇾", "Uruguay"),
        "colombia" to TeamVisual("COL", "https://flagcdn.com/w160/co.png", "🇨🇴", "Colombia"),
        "col" to TeamVisual("COL", "https://flagcdn.com/w160/co.png", "🇨🇴", "Colombia"),
        "chile" to TeamVisual("CHI", "https://flagcdn.com/w160/cl.png", "🇨🇱", "Chile"),
        "chi" to TeamVisual("CHI", "https://flagcdn.com/w160/cl.png", "🇨🇱", "Chile"),
        "ecuador" to TeamVisual("ECU", "https://flagcdn.com/w160/ec.png", "🇪🇨", "Ecuador"),
        "ecu" to TeamVisual("ECU", "https://flagcdn.com/w160/ec.png", "🇪🇨", "Ecuador"),
        "peru" to TeamVisual("PER", "https://flagcdn.com/w160/pe.png", "🇵🇪", "Peru"),
        "per" to TeamVisual("PER", "https://flagcdn.com/w160/pe.png", "🇵🇪", "Peru"),
        "paraguay" to TeamVisual("PAR", "https://flagcdn.com/w160/py.png", "🇵🇾", "Paraguay"),
        "par" to TeamVisual("PAR", "https://flagcdn.com/w160/py.png", "🇵🇾", "Paraguay"),
        "venezuela" to TeamVisual("VEN", "https://flagcdn.com/w160/ve.png", "🇻🇪", "Venezuela"),
        "ven" to TeamVisual("VEN", "https://flagcdn.com/w160/ve.png", "🇻🇪", "Venezuela"),
        "bolivia" to TeamVisual("BOL", "https://flagcdn.com/w160/bo.png", "🇧🇴", "Bolivia"),
        "bol" to TeamVisual("BOL", "https://flagcdn.com/w160/bo.png", "🇧🇴", "Bolivia"),
        "mexico" to TeamVisual("MEX", "https://flagcdn.com/w160/mx.png", "🇲🇽", "Mexico"),
        "mex" to TeamVisual("MEX", "https://flagcdn.com/w160/mx.png", "🇲🇽", "Mexico"),
        "usa" to TeamVisual("USA", "https://flagcdn.com/w160/us.png", "🇺🇸", "United States"),
        "united states" to TeamVisual("USA", "https://flagcdn.com/w160/us.png", "🇺🇸", "United States"),
        "canada" to TeamVisual("CAN", "https://flagcdn.com/w160/ca.png", "🇨🇦", "Canada"),
        "can" to TeamVisual("CAN", "https://flagcdn.com/w160/ca.png", "🇨🇦", "Canada"),
        "west indies" to TeamVisual("WI", "https://flagcdn.com/w160/jm.png", "🌴", "West Indies"),
        "wi" to TeamVisual("WI", "https://flagcdn.com/w160/jm.png", "🌴", "West Indies"),
        "windies" to TeamVisual("WI", "https://flagcdn.com/w160/jm.png", "🌴", "West Indies"),
        "costa rica" to TeamVisual("CRC", "https://flagcdn.com/w160/cr.png", "🇨🇷", "Costa Rica"),
        "panama" to TeamVisual("PAN", "https://flagcdn.com/w160/pa.png", "🇵🇦", "Panama"),
        "jamaica" to TeamVisual("JAM", "https://flagcdn.com/w160/jm.png", "🇯🇲", "Jamaica"),

        // African National Teams
        "south africa" to TeamVisual("SA", "https://flagcdn.com/w160/za.png", "🇿🇦", "South Africa"),
        "sa" to TeamVisual("SA", "https://flagcdn.com/w160/za.png", "🇿🇦", "South Africa"),
        "rsa" to TeamVisual("SA", "https://flagcdn.com/w160/za.png", "🇿🇦", "South Africa"),
        "zimbabwe" to TeamVisual("ZIM", "https://flagcdn.com/w160/zw.png", "🇿🇼", "Zimbabwe"),
        "zim" to TeamVisual("ZIM", "https://flagcdn.com/w160/zw.png", "🇿🇼", "Zimbabwe"),
        "namibia" to TeamVisual("NAM", "https://flagcdn.com/w160/na.png", "🇳🇦", "Namibia"),
        "uganda" to TeamVisual("UGA", "https://flagcdn.com/w160/ug.png", "🇺🇬", "Uganda"),
        "morocco" to TeamVisual("MAR", "https://flagcdn.com/w160/ma.png", "🇲🇦", "Morocco"),
        "mar" to TeamVisual("MAR", "https://flagcdn.com/w160/ma.png", "🇲🇦", "Morocco"),
        "senegal" to TeamVisual("SEN", "https://flagcdn.com/w160/sn.png", "🇸🇳", "Senegal"),
        "sen" to TeamVisual("SEN", "https://flagcdn.com/w160/sn.png", "🇸🇳", "Senegal"),
        "egypt" to TeamVisual("EGY", "https://flagcdn.com/w160/eg.png", "🇪🇬", "Egypt"),
        "egy" to TeamVisual("EGY", "https://flagcdn.com/w160/eg.png", "🇪🇬", "Egypt"),
        "nigeria" to TeamVisual("NGA", "https://flagcdn.com/w160/ng.png", "🇳🇬", "Nigeria"),
        "nga" to TeamVisual("NGA", "https://flagcdn.com/w160/ng.png", "🇳🇬", "Nigeria"),
        "ghana" to TeamVisual("GHA", "https://flagcdn.com/w160/gh.png", "🇬🇭", "Ghana"),
        "cameroon" to TeamVisual("CMR", "https://flagcdn.com/w160/cm.png", "🇨🇲", "Cameroon"),
        "algeria" to TeamVisual("ALG", "https://flagcdn.com/w160/dz.png", "🇩🇿", "Algeria"),
        "tunisia" to TeamVisual("TUN", "https://flagcdn.com/w160/tn.png", "🇹🇳", "Tunisia"),
        "ivory coast" to TeamVisual("CIV", "https://flagcdn.com/w160/ci.png", "🇨🇮", "Ivory Coast"),
        "côte d'ivoire" to TeamVisual("CIV", "https://flagcdn.com/w160/ci.png", "🇨🇮", "Ivory Coast")
    )

    /**
     * Checks if a match is strictly between two Senior National Main Teams
     * with no Under-17/18/19/20/21/23, Youth, Academy, Reserve, Women, or Club teams.
     */
    fun isSeniorInternationalMatch(team1Name: String, team2Name: String, tournament: String = ""): Boolean {
        val t1 = team1Name.trim()
        val t2 = team2Name.trim()
        if (t1.isBlank() || t2.isBlank()) return false

        // Reject if any field contains U17, U18, U19, Youth, Women, Club, etc.
        if (nonSeniorRegex.containsMatchIn(t1) ||
            nonSeniorRegex.containsMatchIn(t2) ||
            nonSeniorRegex.containsMatchIn(tournament)
        ) {
            return false
        }

        // Verify both teams are recognized Senior National Main Teams
        return isRecognizedNationalTeam(t1) && isRecognizedNationalTeam(t2)
    }

    fun isRecognizedNationalTeam(teamName: String): Boolean {
        val clean = teamName.trim().lowercase()
        if (nonSeniorRegex.containsMatchIn(clean)) return false
        return nationalTeamMap.containsKey(clean) ||
            nationalTeamMap.keys.any { k -> k.length >= 4 && clean == k }
    }

    fun resolve(teamName: String, teamCode: String = "", existingFlag: String = ""): TeamVisual {
        val cleanName = teamName.trim().lowercase()
        val cleanCode = teamCode.trim().lowercase()

        // Direct lookup by code first, then name
        val matched = nationalTeamMap[cleanCode]
            ?: nationalTeamMap[cleanName]
            ?: nationalTeamMap.entries.firstOrNull { (k, _) ->
                k.length >= 4 && cleanName.contains(k)
            }?.value

        val resolvedCode = when {
            teamCode.isNotBlank() -> teamCode.trim()
            matched != null -> matched.code
            else -> teamName.trim().ifBlank { "INT" }
        }

        val resolvedFlagUrl = when {
            existingFlag.trim().startsWith("http://", ignoreCase = true) ||
                existingFlag.trim().startsWith("https://", ignoreCase = true) -> existingFlag.trim()
            matched != null -> matched.flagUrl
            else -> ""
        }

        val resolvedEmoji = when {
            matched != null -> matched.emoji
            existingFlag.isNotBlank() && !existingFlag.startsWith("http", ignoreCase = true) -> existingFlag.trim()
            else -> "🏳️"
        }

        return TeamVisual(
            code = resolvedCode,
            flagUrl = resolvedFlagUrl,
            emoji = resolvedEmoji,
            countryName = matched?.countryName ?: teamName.trim()
        )
    }
}
