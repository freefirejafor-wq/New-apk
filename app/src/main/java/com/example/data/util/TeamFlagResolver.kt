package com.example.data.util

/**
 * Resolves high-resolution flag image URLs (via FlagCDN or official crest URLs),
 * country flag emojis, and standardized 3-letter codes for
 * International Cricket, Associate Nations, Domestic/Franchise Cricket,
 * International Football, Club Football, Boxing, and Motorsport.
 */
object TeamFlagResolver {

    data class TeamVisual(
        val code: String,
        val flagUrl: String,
        val emoji: String,
        val countryName: String
    )

    // Regex to reject non-senior matches (U17/U18, youth, academy, reserve, B-team, etc.)
    private val nonSeniorRegex = Regex(
        """\b(u-?14|u-?15|u-?16|u-?17|u-?18|u-?19|u-?20|u-?21|u-?22|u-?23|under[-\s]?\d+|sub[-\s]?\d+|youth|junior|juniors|juvenil|primavera|cadete|infantil|emerging|academy|academies|reserve|reserves|reserva|reservas|b-team|b\s+team|team\s+b|c-team|team\s+c|castilla|next\s+gen|futuro|jong|pl2|premier\s+league\s+2|development|eds)\b""",
        RegexOption.IGNORE_CASE
    )

    private val reserveOrBTeamSuffixRegex = Regex(
        """(?:\s+(?:II|III|B|C|U-?\d+|Reserves?|Academy|Youth|Junior|EDS)|^Jong\s+.+)$""",
        RegexOption.IGNORE_CASE
    )

    // Comprehensive map of National Teams, Associate Cricket Teams, Women/U19/A variants, Domestic & Franchise Teams, and Football Clubs
    private val nationalTeamMap: Map<String, TeamVisual> = mapOf(
        // Asian National & Associate Teams
        "bangladesh" to TeamVisual("BAN", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Bangladesh"),
        "ban" to TeamVisual("BAN", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Bangladesh"),
        "bd" to TeamVisual("BAN", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Bangladesh"),
        "bangladesh women" to TeamVisual("BAN-W", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Bangladesh Women"),
        "ban-w" to TeamVisual("BAN-W", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Bangladesh Women"),
        "bangladesh u19" to TeamVisual("BAN-U19", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Bangladesh U19"),
        "bangladesh a" to TeamVisual("BAN-A", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Bangladesh A"),
        "india" to TeamVisual("IND", "https://flagcdn.com/w160/in.png", "🇮🇳", "India"),
        "ind" to TeamVisual("IND", "https://flagcdn.com/w160/in.png", "🇮🇳", "India"),
        "india women" to TeamVisual("IND-W", "https://flagcdn.com/w160/in.png", "🇮🇳", "India Women"),
        "ind-w" to TeamVisual("IND-W", "https://flagcdn.com/w160/in.png", "🇮🇳", "India Women"),
        "india a" to TeamVisual("IND-A", "https://flagcdn.com/w160/in.png", "🇮🇳", "India A"),
        "india u19" to TeamVisual("IND-U19", "https://flagcdn.com/w160/in.png", "🇮🇳", "India U19"),
        "pakistan" to TeamVisual("PAK", "https://flagcdn.com/w160/pk.png", "🇵🇰", "Pakistan"),
        "pak" to TeamVisual("PAK", "https://flagcdn.com/w160/pk.png", "🇵🇰", "Pakistan"),
        "pakistan women" to TeamVisual("PAK-W", "https://flagcdn.com/w160/pk.png", "🇵🇰", "Pakistan Women"),
        "pak-w" to TeamVisual("PAK-W", "https://flagcdn.com/w160/pk.png", "🇵🇰", "Pakistan Women"),
        "pakistan a" to TeamVisual("PAK-A", "https://flagcdn.com/w160/pk.png", "🇵🇰", "Pakistan A"),
        "sri lanka" to TeamVisual("SL", "https://flagcdn.com/w160/lk.png", "🇱🇰", "Sri Lanka"),
        "sl" to TeamVisual("SL", "https://flagcdn.com/w160/lk.png", "🇱🇰", "Sri Lanka"),
        "sri" to TeamVisual("SL", "https://flagcdn.com/w160/lk.png", "🇱🇰", "Sri Lanka"),
        "sri lanka women" to TeamVisual("SL-W", "https://flagcdn.com/w160/lk.png", "🇱🇰", "Sri Lanka Women"),
        "sl-w" to TeamVisual("SL-W", "https://flagcdn.com/w160/lk.png", "🇱🇰", "Sri Lanka Women"),
        "sri lanka a" to TeamVisual("SL-A", "https://flagcdn.com/w160/lk.png", "🇱🇰", "Sri Lanka A"),
        "afghanistan" to TeamVisual("AFG", "https://flagcdn.com/w160/af.png", "🇦🇫", "Afghanistan"),
        "afg" to TeamVisual("AFG", "https://flagcdn.com/w160/af.png", "🇦🇫", "Afghanistan"),
        "afghanistan a" to TeamVisual("AFG-A", "https://flagcdn.com/w160/af.png", "🇦🇫", "Afghanistan A"),
        "afghanistan u19" to TeamVisual("AFG-U19", "https://flagcdn.com/w160/af.png", "🇦🇫", "Afghanistan U19"),
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
        "oma" to TeamVisual("OMA", "https://flagcdn.com/w160/om.png", "🇴🇲", "Oman"),
        "bahrain" to TeamVisual("BHR", "https://flagcdn.com/w160/bh.png", "🇧🇭", "Bahrain"),
        "bhr" to TeamVisual("BHR", "https://flagcdn.com/w160/bh.png", "🇧🇭", "Bahrain"),
        "kuwait" to TeamVisual("KUW", "https://flagcdn.com/w160/kw.png", "🇰🇼", "Kuwait"),
        "kuw" to TeamVisual("KUW", "https://flagcdn.com/w160/kw.png", "🇰🇼", "Kuwait"),
        "jordan" to TeamVisual("JOR", "https://flagcdn.com/w160/jo.png", "🇯🇴", "Jordan"),
        "china" to TeamVisual("CHN", "https://flagcdn.com/w160/cn.png", "🇨🇳", "China"),
        "indonesia" to TeamVisual("IDN", "https://flagcdn.com/w160/id.png", "🇮🇩", "Indonesia"),
        "malaysia" to TeamVisual("MAS", "https://flagcdn.com/w160/my.png", "🇲🇾", "Malaysia"),
        "mas" to TeamVisual("MAS", "https://flagcdn.com/w160/my.png", "🇲🇾", "Malaysia"),
        "singapore" to TeamVisual("SGP", "https://flagcdn.com/w160/sg.png", "🇸🇬", "Singapore"),
        "sgp" to TeamVisual("SGP", "https://flagcdn.com/w160/sg.png", "🇸🇬", "Singapore"),
        "thailand" to TeamVisual("THA", "https://flagcdn.com/w160/th.png", "🇹🇭", "Thailand"),
        "hong kong" to TeamVisual("HKG", "https://flagcdn.com/w160/hk.png", "🇭🇰", "Hong Kong"),
        "hkg" to TeamVisual("HKG", "https://flagcdn.com/w160/hk.png", "🇭🇰", "Hong Kong"),
        "bhutan" to TeamVisual("BHU", "https://flagcdn.com/w160/bt.png", "🇧🇹", "Bhutan"),
        "maldives" to TeamVisual("MDV", "https://flagcdn.com/w160/mv.png", "🇲🇻", "Maldives"),
        "myanmar" to TeamVisual("MYA", "https://flagcdn.com/w160/mm.png", "🇲🇲", "Myanmar"),
        "cambodia" to TeamVisual("CAM", "https://flagcdn.com/w160/kh.png", "🇰🇭", "Cambodia"),

        // Oceania National Teams
        "australia" to TeamVisual("AUS", "https://flagcdn.com/w160/au.png", "🇦🇺", "Australia"),
        "aus" to TeamVisual("AUS", "https://flagcdn.com/w160/au.png", "🇦🇺", "Australia"),
        "australia women" to TeamVisual("AUS-W", "https://flagcdn.com/w160/au.png", "🇦🇺", "Australia Women"),
        "aus-w" to TeamVisual("AUS-W", "https://flagcdn.com/w160/au.png", "🇦🇺", "Australia Women"),
        "australia a" to TeamVisual("AUS-A", "https://flagcdn.com/w160/au.png", "🇦🇺", "Australia A"),
        "new zealand" to TeamVisual("NZ", "https://flagcdn.com/w160/nz.png", "🇳🇿", "New Zealand"),
        "nz" to TeamVisual("NZ", "https://flagcdn.com/w160/nz.png", "🇳🇿", "New Zealand"),
        "new zealand women" to TeamVisual("NZ-W", "https://flagcdn.com/w160/nz.png", "🇳🇿", "New Zealand Women"),
        "nz-w" to TeamVisual("NZ-W", "https://flagcdn.com/w160/nz.png", "🇳🇿", "New Zealand Women"),
        "nzl" to TeamVisual("NZ", "https://flagcdn.com/w160/nz.png", "🇳🇿", "New Zealand"),
        "papua new guinea" to TeamVisual("PNG", "https://flagcdn.com/w160/pg.png", "🇵🇬", "Papua New Guinea"),
        "png" to TeamVisual("PNG", "https://flagcdn.com/w160/pg.png", "🇵🇬", "Papua New Guinea"),
        "vanuatu" to TeamVisual("VAN", "https://flagcdn.com/w160/vu.png", "🇻🇺", "Vanuatu"),
        "fiji" to TeamVisual("FIJ", "https://flagcdn.com/w160/fj.png", "🇫🇯", "Fiji"),
        "samoa" to TeamVisual("SAM", "https://flagcdn.com/w160/ws.png", "🇼🇸", "Samoa"),

        // European National Teams
        "england" to TeamVisual("ENG", "https://flagcdn.com/w160/gb-eng.png", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", "England"),
        "eng" to TeamVisual("ENG", "https://flagcdn.com/w160/gb-eng.png", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", "England"),
        "england women" to TeamVisual("ENG-W", "https://flagcdn.com/w160/gb-eng.png", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", "England Women"),
        "eng-w" to TeamVisual("ENG-W", "https://flagcdn.com/w160/gb-eng.png", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", "England Women"),
        "england lions" to TeamVisual("ENG-A", "https://flagcdn.com/w160/gb-eng.png", "🏴󠁧󠁢󠁥󠁮󠁧󠁿", "England Lions"),
        "united kingdom" to TeamVisual("UK", "https://flagcdn.com/w160/gb.png", "🇬🇧", "United Kingdom"),
        "great britain" to TeamVisual("GBR", "https://flagcdn.com/w160/gb.png", "🇬🇧", "Great Britain"),
        "scotland" to TeamVisual("SCO", "https://flagcdn.com/w160/gb-sct.png", "🏴󠁧󠁢󠁳󠁣󠁴󠁿", "Scotland"),
        "sco" to TeamVisual("SCO", "https://flagcdn.com/w160/gb-sct.png", "🏴󠁧󠁢󠁳󠁣󠁴󠁿", "Scotland"),
        "wales" to TeamVisual("WAL", "https://flagcdn.com/w160/gb-wls.png", "🏴󠁧󠁢󠁷󠁬󠁳󠁿", "Wales"),
        "wal" to TeamVisual("WAL", "https://flagcdn.com/w160/gb-wls.png", "🏴󠁧󠁢󠁷󠁬󠁳󠁿", "Wales"),
        "ireland" to TeamVisual("IRE", "https://flagcdn.com/w160/ie.png", "🇮🇪", "Ireland"),
        "republic of ireland" to TeamVisual("IRE", "https://flagcdn.com/w160/ie.png", "🇮🇪", "Ireland"),
        "ire" to TeamVisual("IRE", "https://flagcdn.com/w160/ie.png", "🇮🇪", "Ireland"),
        "irl" to TeamVisual("IRE", "https://flagcdn.com/w160/ie.png", "🇮🇪", "Ireland"),
        "ireland women" to TeamVisual("IRE-W", "https://flagcdn.com/w160/ie.png", "🇮🇪", "Ireland Women"),
        "jersey" to TeamVisual("JER", "https://flagcdn.com/w160/je.png", "🇯🇪", "Jersey"),
        "guernsey" to TeamVisual("GUE", "https://flagcdn.com/w160/gg.png", "🇬🇬", "Guernsey"),
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
        "west indies women" to TeamVisual("WI-W", "https://flagcdn.com/w160/jm.png", "🌴", "West Indies Women"),
        "wi-w" to TeamVisual("WI-W", "https://flagcdn.com/w160/jm.png", "🌴", "West Indies Women"),
        "bermuda" to TeamVisual("BER", "https://flagcdn.com/w160/bm.png", "🇧🇲", "Bermuda"),
        "costa rica" to TeamVisual("CRC", "https://flagcdn.com/w160/cr.png", "🇨🇷", "Costa Rica"),
        "panama" to TeamVisual("PAN", "https://flagcdn.com/w160/pa.png", "🇵🇦", "Panama"),
        "jamaica" to TeamVisual("JAM", "https://flagcdn.com/w160/jm.png", "🇯🇲", "Jamaica"),

        // African National Teams
        "south africa" to TeamVisual("SA", "https://flagcdn.com/w160/za.png", "🇿🇦", "South Africa"),
        "sa" to TeamVisual("SA", "https://flagcdn.com/w160/za.png", "🇿🇦", "South Africa"),
        "rsa" to TeamVisual("SA", "https://flagcdn.com/w160/za.png", "🇿🇦", "South Africa"),
        "south africa women" to TeamVisual("SA-W", "https://flagcdn.com/w160/za.png", "🇿🇦", "South Africa Women"),
        "sa-w" to TeamVisual("SA-W", "https://flagcdn.com/w160/za.png", "🇿🇦", "South Africa Women"),
        "south africa a" to TeamVisual("SA-A", "https://flagcdn.com/w160/za.png", "🇿🇦", "South Africa A"),
        "zimbabwe" to TeamVisual("ZIM", "https://flagcdn.com/w160/zw.png", "🇿🇼", "Zimbabwe"),
        "zim" to TeamVisual("ZIM", "https://flagcdn.com/w160/zw.png", "🇿🇼", "Zimbabwe"),
        "zimbabwe women" to TeamVisual("ZIM-W", "https://flagcdn.com/w160/zw.png", "🇿🇼", "Zimbabwe Women"),
        "namibia" to TeamVisual("NAM", "https://flagcdn.com/w160/na.png", "🇳🇦", "Namibia"),
        "nam" to TeamVisual("NAM", "https://flagcdn.com/w160/na.png", "🇳🇦", "Namibia"),
        "uganda" to TeamVisual("UGA", "https://flagcdn.com/w160/ug.png", "🇺🇬", "Uganda"),
        "uga" to TeamVisual("UGA", "https://flagcdn.com/w160/ug.png", "🇺🇬", "Uganda"),
        "kenya" to TeamVisual("KEN", "https://flagcdn.com/w160/ke.png", "🇰🇪", "Kenya"),
        "ken" to TeamVisual("KEN", "https://flagcdn.com/w160/ke.png", "🇰🇪", "Kenya"),
        "tanzania" to TeamVisual("TAN", "https://flagcdn.com/w160/tz.png", "🇹🇿", "Tanzania"),
        "rwanda" to TeamVisual("RWA", "https://flagcdn.com/w160/rw.png", "🇷🇼", "Rwanda"),
        "malawi" to TeamVisual("MWI", "https://flagcdn.com/w160/mw.png", "🇲🇼", "Malawi"),
        "botswana" to TeamVisual("BOT", "https://flagcdn.com/w160/bw.png", "🇧🇼", "Botswana"),
        "lesotho" to TeamVisual("LES", "https://flagcdn.com/w160/ls.png", "🇱🇸", "Lesotho"),
        "mozambique" to TeamVisual("MOZ", "https://flagcdn.com/w160/mz.png", "🇲🇿", "Mozambique"),
        "gambia" to TeamVisual("GAM", "https://flagcdn.com/w160/gm.png", "🇬🇲", "Gambia"),
        "sierra leone" to TeamVisual("SLE", "https://flagcdn.com/w160/sl.png", "🇸🇱", "Sierra Leone"),
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
        "côte d'ivoire" to TeamVisual("CIV", "https://flagcdn.com/w160/ci.png", "🇨🇮", "Ivory Coast"),

        // Domestic & Franchise Cricket Teams (mapped to their country flag + official 3/4 letter code)
        "dhaka division" to TeamVisual("DHK", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Dhaka Division"),
        "chattogram division" to TeamVisual("CTG", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Chattogram Division"),
        "sylhet division" to TeamVisual("SYL", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Sylhet Division"),
        "khulna division" to TeamVisual("KHL", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Khulna Division"),
        "rajshahi division" to TeamVisual("RAJ", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Rajshahi Division"),
        "rangpur division" to TeamVisual("RNG", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Rangpur Division"),
        "barishal division" to TeamVisual("BAR", "https://flagcdn.com/w160/bd.png", "🇧🇩", "Barishal Division"),
        "mumbai" to TeamVisual("MUM", "https://flagcdn.com/w160/in.png", "🇮🇳", "Mumbai"),
        "karnataka" to TeamVisual("KAR", "https://flagcdn.com/w160/in.png", "🇮🇳", "Karnataka"),
        "tamil nadu" to TeamVisual("TN", "https://flagcdn.com/w160/in.png", "🇮🇳", "Tamil Nadu"),
        "delhi" to TeamVisual("DEL", "https://flagcdn.com/w160/in.png", "🇮🇳", "Delhi"),
        "punjab" to TeamVisual("PUN", "https://flagcdn.com/w160/in.png", "🇮🇳", "Punjab"),
        "bengal" to TeamVisual("BEN", "https://flagcdn.com/w160/in.png", "🇮🇳", "Bengal"),
        "gujarat" to TeamVisual("GUJ", "https://flagcdn.com/w160/in.png", "🇮🇳", "Gujarat"),
        "maharashtra" to TeamVisual("MAH", "https://flagcdn.com/w160/in.png", "🇮🇳", "Maharashtra"),
        "uttar pradesh" to TeamVisual("UP", "https://flagcdn.com/w160/in.png", "🇮🇳", "Uttar Pradesh"),
        "hyderabad" to TeamVisual("HYD", "https://flagcdn.com/w160/in.png", "🇮🇳", "Hyderabad"),
        "kerala" to TeamVisual("KER", "https://flagcdn.com/w160/in.png", "🇮🇳", "Kerala"),
        "vidarbha" to TeamVisual("VID", "https://flagcdn.com/w160/in.png", "🇮🇳", "Vidarbha"),
        "baroda" to TeamVisual("BRD", "https://flagcdn.com/w160/in.png", "🇮🇳", "Baroda"),
        "haryana" to TeamVisual("HAR", "https://flagcdn.com/w160/in.png", "🇮🇳", "Haryana"),
        "assam" to TeamVisual("ASM", "https://flagcdn.com/w160/in.png", "🇮🇳", "Assam"),
        "goa" to TeamVisual("GOA", "https://flagcdn.com/w160/in.png", "🇮🇳", "Goa"),
        "sikkim" to TeamVisual("SIK", "https://flagcdn.com/w160/in.png", "🇮🇳", "Sikkim"),
        "new south wales" to TeamVisual("NSW", "https://flagcdn.com/w160/au.png", "🇦🇺", "New South Wales"),
        "victoria" to TeamVisual("VIC", "https://flagcdn.com/w160/au.png", "🇦🇺", "Victoria"),
        "queensland" to TeamVisual("QLD", "https://flagcdn.com/w160/au.png", "🇦🇺", "Queensland"),
        "western australia" to TeamVisual("WA", "https://flagcdn.com/w160/au.png", "🇦🇺", "Western Australia"),
        "south australia" to TeamVisual("SAU", "https://flagcdn.com/w160/au.png", "🇦🇺", "South Australia"),
        "tasmania" to TeamVisual("TAS", "https://flagcdn.com/w160/au.png", "🇦🇺", "Tasmania"),
        "sydney sixers women" to TeamVisual("SS-W", "https://flagcdn.com/w160/au.png", "🇦🇺", "Sydney Sixers W"),
        "sydney thunder women" to TeamVisual("ST-W", "https://flagcdn.com/w160/au.png", "🇦🇺", "Sydney Thunder W"),
        "melbourne stars women" to TeamVisual("MS-W", "https://flagcdn.com/w160/au.png", "🇦🇺", "Melbourne Stars W"),
        "melbourne renegades women" to TeamVisual("MR-W", "https://flagcdn.com/w160/au.png", "🇦🇺", "Melbourne Renegades W"),
        "perth scorchers women" to TeamVisual("PS-W", "https://flagcdn.com/w160/au.png", "🇦🇺", "Perth Scorchers W"),
        "adelaide strikers women" to TeamVisual("AS-W", "https://flagcdn.com/w160/au.png", "🇦🇺", "Adelaide Strikers W"),
        "brisbane heat women" to TeamVisual("BH-W", "https://flagcdn.com/w160/au.png", "🇦🇺", "Brisbane Heat W"),
        "hobart hurricanes women" to TeamVisual("HH-W", "https://flagcdn.com/w160/au.png", "🇦🇺", "Hobart Hurricanes W"),
        "dolphins" to TeamVisual("DOL", "https://flagcdn.com/w160/za.png", "🇿🇦", "Dolphins"),
        "titans" to TeamVisual("TIT", "https://flagcdn.com/w160/za.png", "🇿🇦", "Titans"),
        "lions" to TeamVisual("LIO", "https://flagcdn.com/w160/za.png", "🇿🇦", "Lions"),
        "warriors" to TeamVisual("WAR", "https://flagcdn.com/w160/za.png", "🇿🇦", "Warriors"),
        "knights" to TeamVisual("KNI", "https://flagcdn.com/w160/za.png", "🇿🇦", "Knights"),
        "rocks" to TeamVisual("ROC", "https://flagcdn.com/w160/za.png", "🇿🇦", "Rocks"),
        "western province" to TeamVisual("WP", "https://flagcdn.com/w160/za.png", "🇿🇦", "Western Province"),
        "north west" to TeamVisual("NW", "https://flagcdn.com/w160/za.png", "🇿🇦", "North West"),

        // Major European & International Football Clubs — strictly club crests (never national flags)
        "real madrid" to TeamVisual("RMA", "https://a.espncdn.com/i/teamlogos/soccer/500/86.png", "", "Real Madrid"),
        "barcelona" to TeamVisual("BAR", "https://a.espncdn.com/i/teamlogos/soccer/500/83.png", "", "Barcelona"),
        "fc barcelona" to TeamVisual("BAR", "https://a.espncdn.com/i/teamlogos/soccer/500/83.png", "", "Barcelona"),
        "atletico madrid" to TeamVisual("ATM", "https://a.espncdn.com/i/teamlogos/soccer/500/1068.png", "", "Atletico Madrid"),
        "atlético madrid" to TeamVisual("ATM", "https://a.espncdn.com/i/teamlogos/soccer/500/1068.png", "", "Atletico Madrid"),
        "manchester city" to TeamVisual("MCI", "https://a.espncdn.com/i/teamlogos/soccer/500/382.png", "", "Man City"),
        "man city" to TeamVisual("MCI", "https://a.espncdn.com/i/teamlogos/soccer/500/382.png", "", "Man City"),
        "manchester united" to TeamVisual("MUN", "https://a.espncdn.com/i/teamlogos/soccer/500/360.png", "", "Man United"),
        "man united" to TeamVisual("MUN", "https://a.espncdn.com/i/teamlogos/soccer/500/360.png", "", "Man United"),
        "liverpool" to TeamVisual("LIV", "https://a.espncdn.com/i/teamlogos/soccer/500/364.png", "", "Liverpool"),
        "arsenal" to TeamVisual("ARS", "https://a.espncdn.com/i/teamlogos/soccer/500/359.png", "", "Arsenal"),
        "chelsea" to TeamVisual("CHE", "https://a.espncdn.com/i/teamlogos/soccer/500/363.png", "", "Chelsea"),
        "tottenham" to TeamVisual("TOT", "https://a.espncdn.com/i/teamlogos/soccer/500/367.png", "", "Tottenham"),
        "tottenham hotspur" to TeamVisual("TOT", "https://a.espncdn.com/i/teamlogos/soccer/500/367.png", "", "Tottenham"),
        "bayern munich" to TeamVisual("BAY", "https://a.espncdn.com/i/teamlogos/soccer/500/132.png", "", "Bayern Munich"),
        "borussia dortmund" to TeamVisual("BVB", "https://a.espncdn.com/i/teamlogos/soccer/500/124.png", "", "Dortmund"),
        "bayer leverkusen" to TeamVisual("B04", "https://a.espncdn.com/i/teamlogos/soccer/500/131.png", "", "Leverkusen"),
        "paris saint-germain" to TeamVisual("PSG", "https://a.espncdn.com/i/teamlogos/soccer/500/160.png", "", "PSG"),
        "psg" to TeamVisual("PSG", "https://a.espncdn.com/i/teamlogos/soccer/500/160.png", "", "PSG"),
        "marseille" to TeamVisual("OM", "https://a.espncdn.com/i/teamlogos/soccer/500/176.png", "", "Marseille"),
        "inter milan" to TeamVisual("INT", "https://a.espncdn.com/i/teamlogos/soccer/500/110.png", "", "Inter Milan"),
        "internazionale" to TeamVisual("INT", "https://a.espncdn.com/i/teamlogos/soccer/500/110.png", "", "Inter Milan"),
        "ac milan" to TeamVisual("MIL", "https://a.espncdn.com/i/teamlogos/soccer/500/103.png", "", "AC Milan"),
        "juventus" to TeamVisual("JUV", "https://a.espncdn.com/i/teamlogos/soccer/500/111.png", "", "Juventus"),
        "napoli" to TeamVisual("NAP", "https://a.espncdn.com/i/teamlogos/soccer/500/114.png", "", "Napoli"),
        "as roma" to TeamVisual("ROM", "https://a.espncdn.com/i/teamlogos/soccer/500/104.png", "", "AS Roma")
    )

    fun isSeniorInternationalMatch(team1Name: String, team2Name: String, tournament: String = ""): Boolean {
        val t1 = team1Name.trim()
        val t2 = team2Name.trim()
        if (t1.isBlank() || t2.isBlank()) return false

        if (nonSeniorRegex.containsMatchIn(t1) ||
            nonSeniorRegex.containsMatchIn(t2) ||
            nonSeniorRegex.containsMatchIn(tournament)
        ) {
            return false
        }

        return isRecognizedNationalTeam(t1) && isRecognizedNationalTeam(t2)
    }

    fun isNonSeniorOrExcludedCompetition(text: String): Boolean {
        val lower = text.lowercase()
        if (nonSeniorRegex.containsMatchIn(lower)) return true
        val excludedKeywords = listOf(
            "esports", "e-sports", "virtual", "cyber", "amateur", "friendly club",
            "b-team", "b team", "reserve league", "youth league", "premier league 2"
        )
        return excludedKeywords.any { lower.contains(it) }
    }

    fun isNonSeniorFootballTeamName(teamName: String): Boolean {
        val trimmed = teamName.trim()
        if (trimmed.isBlank()) return false
        if (nonSeniorRegex.containsMatchIn(trimmed)) return true
        if (reserveOrBTeamSuffixRegex.containsMatchIn(trimmed)) return true
        return false
    }

    fun isRecognizedNationalTeam(teamName: String): Boolean {
        val clean = teamName.trim().lowercase()
        if (nonSeniorRegex.containsMatchIn(clean)) return false
        val visual = nationalTeamMap[clean] ?: return false
        return visual.flagUrl.contains("flagcdn.com", ignoreCase = true)
    }

    fun resolve(
        teamName: String,
        teamCode: String = "",
        existingFlag: String = "",
        isClubFootball: Boolean = false
    ): TeamVisual {
        val cleanName = teamName.trim().lowercase()
        val cleanCode = teamCode.trim().lowercase()
        val baseNameWithoutSuffix = cleanName
            .removeSuffix(" women")
            .removeSuffix(" w")
            .removeSuffix(" u19")
            .removeSuffix(" a")
            .removeSuffix(" xi")
            .trim()

        // Direct exact lookup (do NOT guess country flags by partial substring for unknown clubs)
        val matched = nationalTeamMap[cleanName]
            ?: nationalTeamMap[baseNameWithoutSuffix]
            ?: (if (!isClubFootball) nationalTeamMap[cleanCode] else null)

        val resolvedCode = when {
            matched != null && (teamCode.isBlank() || teamCode.startsWith("Team ", ignoreCase = true) || teamCode.length <= 2) -> matched.code
            teamCode.isNotBlank() && !teamCode.startsWith("Team ", ignoreCase = true) -> teamCode.trim().uppercase()
            matched != null -> matched.code
            cleanName.isNotBlank() && !cleanName.startsWith("team ") -> {
                val words = teamName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.size >= 2) {
                    words.take(3).joinToString("") { it.first().uppercase() }
                } else {
                    teamName.trim().take(3).uppercase()
                }
            }
            else -> teamCode.trim().ifBlank { "TBD" }.uppercase()
        }

        val validExistingUrl = existingFlag.trim().takeIf {
            it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
        }

        // Never assign a national flag (flagcdn.com) to a club football team!
        val resolvedFlagUrl = when {
            validExistingUrl != null -> {
                if (isClubFootball && validExistingUrl.contains("flagcdn.com", ignoreCase = true)) {
                    matched?.flagUrl?.takeIf { !it.contains("flagcdn.com", ignoreCase = true) }.orEmpty()
                } else {
                    validExistingUrl
                }
            }
            matched != null -> {
                if (isClubFootball && matched.flagUrl.contains("flagcdn.com", ignoreCase = true)) {
                    ""
                } else {
                    matched.flagUrl
                }
            }
            else -> ""
        }

        val resolvedEmoji = when {
            isClubFootball -> ""
            matched != null -> matched.emoji
            existingFlag.isNotBlank() && !existingFlag.startsWith("http", ignoreCase = true) -> existingFlag.trim()
            else -> ""
        }

        return TeamVisual(
            code = resolvedCode,
            flagUrl = resolvedFlagUrl,
            emoji = resolvedEmoji,
            countryName = matched?.countryName ?: teamName.trim()
        )
    }
}
