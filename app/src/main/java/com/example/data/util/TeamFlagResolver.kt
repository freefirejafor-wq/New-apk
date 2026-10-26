package com.example.data.util

import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Resolves high-resolution flag image URLs (via FlagCDN),
 * country flag emojis, and standardized 3-letter codes for Senior National Teams,
 * while strictly preserving Club Crests/Logos (and never substituting national flags)
 * for Club Football matches.
 */
object TeamFlagResolver {

    val DHAKA_TIMEZONE: TimeZone = TimeZone.getTimeZone("Asia/Dhaka")

    data class TeamVisual(
        val code: String,
        val flagUrl: String,
        val emoji: String,
        val countryName: String,
        val isNationalFlag: Boolean = true
    )

    // Regex to reject any Under-17, Under-18, Under-19, Under-20, Under-21, Under-23, Youth,
    // Academy, Reserve, B-team, Amateur, Grassroots, Virtual, or Esports matches.
    private val youthOrReserveRegex = Regex(
        """(\b(u-?15|u-?16|u-?17|u-?18|u-?19|u-?20|u-?21|u-?22|u-?23|under[-\s]?\d+|youth|junior|juvenil|primavera|emerging|academy|reserve|reserves|b-team|amateur|grassroots|virtual|esports|e-sports|esoccer|cyber)\b|\b(india a|bangladesh a|pakistan a|sri lanka a|australia a|england a|south africa a|new zealand a|west indies a|afghanistan a|zimbabwe a|ireland a|nepal a|england lions|board xi|president's xi|pm xi)\b|\s+ii\b|\s+b\b)""",
        RegexOption.IGNORE_CASE
    )

    // Regex to reject domestic/franchise cricket tournaments so Cricket stays strictly International
    private val domesticOrFranchiseCricketRegex = Regex(
        """\b(ranji|duleep|irani|vijay hazare|syed mushtaq|deodhar|county|sheffield|plunket|ford trophy|marsh cup|one-day cup|vitality blast|the hundred|wpl|wbbl|ipl|bpl|psl|cpl|bbl|sa20|ilt20|lpl|mlc|pro20|super smash|t10|abu dhabi t10|emirates d10|emirates d20|ecs|european cricket|premier league|trophy|division|shield|legends|masters|veterans|women's big bash)\b""",
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
        "kuwait" to TeamVisual("KUW", "https://flagcdn.com/w160/kw.png", "🇰🇼", "Kuwait"),
        "kuw" to TeamVisual("KUW", "https://flagcdn.com/w160/kw.png", "🇰🇼", "Kuwait"),
        "uae" to TeamVisual("UAE", "https://flagcdn.com/w160/ae.png", "🇦🇪", "United Arab Emirates"),
        "united arab emirates" to TeamVisual("UAE", "https://flagcdn.com/w160/ae.png", "🇦🇪", "United Arab Emirates"),
        "iran" to TeamVisual("IRN", "https://flagcdn.com/w160/ir.png", "🇮🇷", "Iran"),
        "irn" to TeamVisual("IRN", "https://flagcdn.com/w160/ir.png", "🇮🇷", "Iran"),
        "iraq" to TeamVisual("IRQ", "https://flagcdn.com/w160/iq.png", "🇮🇶", "Iraq"),
        "uzbekistan" to TeamVisual("UZB", "https://flagcdn.com/w160/uz.png", "🇺🇿", "Uzbekistan"),
        "oman" to TeamVisual("OMA", "https://flagcdn.com/w160/om.png", "🇴🇲", "Oman"),
        "oma" to TeamVisual("OMA", "https://flagcdn.com/w160/om.png", "🇴🇲", "Oman"),
        "bahrain" to TeamVisual("BHR", "https://flagcdn.com/w160/bh.png", "🇧🇭", "Bahrain"),
        "jordan" to TeamVisual("JOR", "https://flagcdn.com/w160/jo.png", "🇯🇴", "Jordan"),
        "china" to TeamVisual("CHN", "https://flagcdn.com/w160/cn.png", "🇨🇳", "China"),
        "indonesia" to TeamVisual("IDN", "https://flagcdn.com/w160/id.png", "🇮🇩", "Indonesia"),
        "malaysia" to TeamVisual("MAS", "https://flagcdn.com/w160/my.png", "🇲🇾", "Malaysia"),
        "thailand" to TeamVisual("THA", "https://flagcdn.com/w160/th.png", "🇹🇭", "Thailand"),
        "hong kong" to TeamVisual("HKG", "https://flagcdn.com/w160/hk.png", "🇭🇰", "Hong Kong"),
        "singapore" to TeamVisual("SGP", "https://flagcdn.com/w160/sg.png", "🇸🇬", "Singapore"),

        // Oceania National Teams
        "australia" to TeamVisual("AUS", "https://flagcdn.com/w160/au.png", "🇦🇺", "Australia"),
        "aus" to TeamVisual("AUS", "https://flagcdn.com/w160/au.png", "🇦🇺", "Australia"),
        "new zealand" to TeamVisual("NZ", "https://flagcdn.com/w160/nz.png", "🇳🇿", "New Zealand"),
        "nz" to TeamVisual("NZ", "https://flagcdn.com/w160/nz.png", "🇳🇿", "New Zealand"),
        "nzl" to TeamVisual("NZ", "https://flagcdn.com/w160/nz.png", "🇳🇿", "New Zealand"),
        "papua new guinea" to TeamVisual("PNG", "https://flagcdn.com/w160/pg.png", "🇵🇬", "Papua New Guinea"),
        "png" to TeamVisual("PNG", "https://flagcdn.com/w160/pg.png", "🇵🇬", "Papua New Guinea"),

        // European National Teams
        "england" to TeamVisual("ENG", "https://flagcdn.com/w160/gb-eng.png", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", "England"),
        "eng" to TeamVisual("ENG", "https://flagcdn.com/w160/gb-eng.png", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", "England"),
        "scotland" to TeamVisual("SCO", "https://flagcdn.com/w160/gb-sct.png", "🏴󠁧󠁢󠁳󠁣󠁴󠁿", "Scotland"),
        "sco" to TeamVisual("SCO", "https://flagcdn.com/w160/gb-sct.png", "🏴󠁧󠁢󠁳󠁣󠁴󠁿", "Scotland"),
        "wales" to TeamVisual("WAL", "https://flagcdn.com/w160/gb-wls.png", "🏴󠁧󠁢󠁷󠁬󠁳󠁿", "Wales"),
        "wal" to TeamVisual("WAL", "https://flagcdn.com/w160/gb-wls.png", "🏴󠁧󠁢󠁷󠁬󠁳󠁿", "Wales"),
        "northern ireland" to TeamVisual("NIR", "https://flagcdn.com/w160/gb-nir.png", "🇬🇧", "Northern Ireland"),
        "ireland" to TeamVisual("IRE", "https://flagcdn.com/w160/ie.png", "🇮🇪", "Ireland"),
        "republic of ireland" to TeamVisual("IRE", "https://flagcdn.com/w160/ie.png", "🇮🇪", "Ireland"),
        "ire" to TeamVisual("IRE", "https://flagcdn.com/w160/ie.png", "🇮🇪", "Ireland"),
        "irl" to TeamVisual("IRE", "https://flagcdn.com/w160/ie.png", "🇮🇪", "Ireland"),
        "jersey" to TeamVisual("JER", "https://flagcdn.com/w160/je.png", "🇯🇪", "Jersey"),
        "jer" to TeamVisual("JER", "https://flagcdn.com/w160/je.png", "🇯🇪", "Jersey"),
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
        "finland" to TeamVisual("FIN", "https://flagcdn.com/w160/fi.png", "🇫🇮", "Finland"),
        "iceland" to TeamVisual("ISL", "https://flagcdn.com/w160/is.png", "🇮🇸", "Iceland"),
        "georgia" to TeamVisual("GEO", "https://flagcdn.com/w160/ge.png", "🇬🇪", "Georgia"),
        "albania" to TeamVisual("ALB", "https://flagcdn.com/w160/al.png", "🇦🇱", "Albania"),
        "bosnia and herzegovina" to TeamVisual("BIH", "https://flagcdn.com/w160/ba.png", "🇧🇦", "Bosnia and Herzegovina"),
        "north macedonia" to TeamVisual("MKD", "https://flagcdn.com/w160/mk.png", "🇲🇰", "North Macedonia"),
        "montenegro" to TeamVisual("MNE", "https://flagcdn.com/w160/me.png", "🇲🇪", "Montenegro"),
        "israel" to TeamVisual("ISR", "https://flagcdn.com/w160/il.png", "🇮🇱", "Israel"),
        "bulgaria" to TeamVisual("BUL", "https://flagcdn.com/w160/bg.png", "🇧🇬", "Bulgaria"),

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
        "honduras" to TeamVisual("HON", "https://flagcdn.com/w160/hn.png", "🇭🇳", "Honduras"),

        // African National Teams
        "south africa" to TeamVisual("SA", "https://flagcdn.com/w160/za.png", "🇿🇦", "South Africa"),
        "sa" to TeamVisual("SA", "https://flagcdn.com/w160/za.png", "🇿🇦", "South Africa"),
        "rsa" to TeamVisual("SA", "https://flagcdn.com/w160/za.png", "🇿🇦", "South Africa"),
        "zimbabwe" to TeamVisual("ZIM", "https://flagcdn.com/w160/zw.png", "🇿🇼", "Zimbabwe"),
        "zim" to TeamVisual("ZIM", "https://flagcdn.com/w160/zw.png", "🇿🇼", "Zimbabwe"),
        "namibia" to TeamVisual("NAM", "https://flagcdn.com/w160/na.png", "🇳🇦", "Namibia"),
        "nam" to TeamVisual("NAM", "https://flagcdn.com/w160/na.png", "🇳🇦", "Namibia"),
        "uganda" to TeamVisual("UGA", "https://flagcdn.com/w160/ug.png", "🇺🇬", "Uganda"),
        "uga" to TeamVisual("UGA", "https://flagcdn.com/w160/ug.png", "🇺🇬", "Uganda"),
        "kenya" to TeamVisual("KEN", "https://flagcdn.com/w160/ke.png", "🇰🇪", "Kenya"),
        "ken" to TeamVisual("KEN", "https://flagcdn.com/w160/ke.png", "🇰🇪", "Kenya"),
        "nigeria" to TeamVisual("NGA", "https://flagcdn.com/w160/ng.png", "🇳🇬", "Nigeria"),
        "nga" to TeamVisual("NGA", "https://flagcdn.com/w160/ng.png", "🇳🇬", "Nigeria"),
        "zambia" to TeamVisual("ZAM", "https://flagcdn.com/w160/zm.png", "🇿🇲", "Zambia"),
        "zam" to TeamVisual("ZAM", "https://flagcdn.com/w160/zm.png", "🇿🇲", "Zambia"),
        "lesotho" to TeamVisual("LES", "https://flagcdn.com/w160/ls.png", "🇱🇸", "Lesotho"),
        "mozambique" to TeamVisual("MOZ", "https://flagcdn.com/w160/mz.png", "🇲🇿", "Mozambique"),
        "gambia" to TeamVisual("GAM", "https://flagcdn.com/w160/gm.png", "🇬🇲", "Gambia"),
        "rwanda" to TeamVisual("RWA", "https://flagcdn.com/w160/rw.png", "🇷🇼", "Rwanda"),
        "tanzania" to TeamVisual("TAN", "https://flagcdn.com/w160/tz.png", "🇹🇿", "Tanzania"),
        "morocco" to TeamVisual("MAR", "https://flagcdn.com/w160/ma.png", "🇲🇦", "Morocco"),
        "mar" to TeamVisual("MAR", "https://flagcdn.com/w160/ma.png", "🇲🇦", "Morocco"),
        "senegal" to TeamVisual("SEN", "https://flagcdn.com/w160/sn.png", "🇸🇳", "Senegal"),
        "sen" to TeamVisual("SEN", "https://flagcdn.com/w160/sn.png", "🇸🇳", "Senegal"),
        "egypt" to TeamVisual("EGY", "https://flagcdn.com/w160/eg.png", "🇪🇬", "Egypt"),
        "egy" to TeamVisual("EGY", "https://flagcdn.com/w160/eg.png", "🇪🇬", "Egypt"),
        "ghana" to TeamVisual("GHA", "https://flagcdn.com/w160/gh.png", "🇬🇭", "Ghana"),
        "cameroon" to TeamVisual("CMR", "https://flagcdn.com/w160/cm.png", "🇨🇲", "Cameroon"),
        "algeria" to TeamVisual("ALG", "https://flagcdn.com/w160/dz.png", "🇩🇿", "Algeria"),
        "tunisia" to TeamVisual("TUN", "https://flagcdn.com/w160/tn.png", "🇹🇳", "Tunisia"),
        "ivory coast" to TeamVisual("CIV", "https://flagcdn.com/w160/ci.png", "🇨🇮", "Ivory Coast"),
        "côte d'ivoire" to TeamVisual("CIV", "https://flagcdn.com/w160/ci.png", "🇨🇮", "Ivory Coast"),
        "mali" to TeamVisual("MLI", "https://flagcdn.com/w160/ml.png", "🇲🇱", "Mali"),
        "burkina faso" to TeamVisual("BFA", "https://flagcdn.com/w160/bf.png", "🇧🇫", "Burkina Faso")
    )

    /**
     * Rejects U17, U18, U19, U20, U21, U23, Youth, Academy, Reserve, B-team,
     * Amateur, Grassroots, Virtual, or Esports matches.
     */
    fun isYouthOrNonSeniorEvent(team1Name: String, team2Name: String, tournament: String = ""): Boolean {
        return youthOrReserveRegex.containsMatchIn(team1Name) ||
            youthOrReserveRegex.containsMatchIn(team2Name) ||
            youthOrReserveRegex.containsMatchIn(tournament)
    }

    /**
     * Checks if a Cricket match is strictly an International Cricket match between
     * recognized Senior National Cricket Teams (excluding domestic, franchise, U19, A-team, or women's domestic).
     */
    fun isSeniorInternationalCricketMatch(
        team1Name: String,
        team2Name: String,
        tournament: String = "",
        format: String = ""
    ): Boolean {
        val t1 = team1Name.trim()
        val t2 = team2Name.trim()
        if (t1.isBlank() || t2.isBlank()) return false

        if (isYouthOrNonSeniorEvent(t1, t2, "$tournament $format")) {
            return false
        }
        if (domesticOrFranchiseCricketRegex.containsMatchIn(t1) ||
            domesticOrFranchiseCricketRegex.containsMatchIn(t2) ||
            domesticOrFranchiseCricketRegex.containsMatchIn(tournament)
        ) {
            return false
        }

        // Format check if provided: First Class (domestic), T10, etc. are not senior international unless "Test", "ODI", "T20I", "List A"
        val cleanFormat = format.trim().lowercase(Locale.US)
        if (cleanFormat == "first class" || cleanFormat == "t10" || cleanFormat == "hundred") {
            return false
        }

        return isRecognizedNationalTeam(t1) && isRecognizedNationalTeam(t2)
    }

    /**
     * Legacy helper kept for compatibility: checks if both teams are recognized senior national teams.
     */
    fun isSeniorInternationalMatch(team1Name: String, team2Name: String, tournament: String = ""): Boolean {
        return isSeniorInternationalCricketMatch(team1Name, team2Name, tournament)
    }

    fun isRecognizedNationalTeam(teamName: String): Boolean {
        val clean = teamName.trim().lowercase(Locale.US)
            .removeSuffix(" women")
            .removeSuffix("-w")
            .trim()
        if (youthOrReserveRegex.containsMatchIn(teamName)) return false
        return nationalTeamMap.containsKey(clean)
    }

    /**
     * Checks whether a given epoch timestamp falls within Yesterday, Today, or Tomorrow
     * in the `Asia/Dhaka` timezone.
     */
    fun isWithinYesterdayTodayTomorrowDhaka(
        matchEpochMs: Long,
        referenceNowEpochMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (matchEpochMs <= 0L) return false
        val (startMs, endMs) = getDhakaThreeDayWindowBounds(referenceNowEpochMs)
        return matchEpochMs in startMs..endMs
    }

    /**
     * Returns `(startOfYesterdayEpochMs, endOfTomorrowEpochMs)` in `Asia/Dhaka` timezone.
     */
    fun getDhakaThreeDayWindowBounds(referenceNowEpochMs: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val startCal = Calendar.getInstance(DHAKA_TIMEZONE, Locale.US).apply {
            timeInMillis = referenceNowEpochMs
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance(DHAKA_TIMEZONE, Locale.US).apply {
            timeInMillis = referenceNowEpochMs
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return startCal.timeInMillis to endCal.timeInMillis
    }

    /**
     * Resolves team visuals while respecting whether the match is a National Team match
     * or a Club Football match.
     * - For Club Football (`isNationalTeam == false`), NEVER substitute a country flag!
     *   Only use the club's own crest URL (`existingFlag` from ESPN `team.logo`) or a clean fallback.
     */
    fun resolveForMatch(
        teamName: String,
        teamCode: String = "",
        existingFlag: String = "",
        isNationalTeam: Boolean = true
    ): TeamVisual {
        if (!isNationalTeam) {
            val cleanLogoUrl = existingFlag.trim().takeIf {
                it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
            }.orEmpty()
            val code = teamCode.trim().ifBlank {
                teamName.trim().take(3).uppercase(Locale.US)
            }
            return TeamVisual(
                code = code,
                flagUrl = cleanLogoUrl,
                emoji = "", // Never show a country flag emoji for a club
                countryName = teamName.trim(),
                isNationalFlag = false
            )
        }
        return resolve(teamName, teamCode, existingFlag)
    }

    fun resolve(teamName: String, teamCode: String = "", existingFlag: String = ""): TeamVisual {
        val cleanName = teamName.trim().lowercase(Locale.US)
            .removeSuffix(" women")
            .removeSuffix("-w")
            .trim()
        val cleanCode = teamCode.trim().lowercase(Locale.US)
            .removeSuffix("-w")
            .trim()

        val matched = nationalTeamMap[cleanCode]
            ?: nationalTeamMap[cleanName]
            ?: nationalTeamMap.entries.firstOrNull { (k, _) ->
                k.length >= 4 && cleanName == k
            }?.value

        val resolvedCode = when {
            matched != null -> matched.code
            teamCode.isNotBlank() && teamCode.none { it.isDigit() } -> teamCode.trim().uppercase(Locale.US)
            else -> teamName.trim().take(3).uppercase(Locale.US).ifBlank { "INT" }
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
            countryName = matched?.countryName ?: teamName.trim(),
            isNationalFlag = true
        )
    }
}
