package com.wb.privateapi.constant

data class DestinationInfo(val ids: List<Int>) {
    val lastId: Int get() = ids.last()
}

data class GlobalDestinations(
    val astanaKz: DestinationInfo,
    val minskBy: DestinationInfo,
    val erevanAm: DestinationInfo,
    val tashkentUz: DestinationInfo,
    val dushanbeTj: DestinationInfo,
    val addisAbabaEt: DestinationInfo,
    val tbilisiGe: DestinationInfo
)

object Destinations {
    val KRASNODAR = DestinationInfo(listOf(-1059500, -108082, -269701, 12358062))
    val MOSCOW = DestinationInfo(listOf(-1029256, -102269, -2162196, -1257786))
    val KAZAN = DestinationInfo(listOf(-1075831, -79374, -367666, -2133462))
    val EKATERINBURG = DestinationInfo(listOf(-1113276, -79379, -1104258, 123589409))
    val NOVOSIBIRSK = DestinationInfo(listOf(-1221148, -140294, -1751445, -365403))
    val HABAROVSK = DestinationInfo(listOf(-1221185, -151223, -1782064, -1785058))

    val GLOBAL = GlobalDestinations(
        astanaKz = DestinationInfo(listOf(12358388, 12358412, -3479876, 85)),
        minskBy = DestinationInfo(listOf(12358386, 12358403, -70563, -8139704)),
        erevanAm = DestinationInfo(listOf(12358387, 12358400, -13404218, 36)),
        tashkentUz = DestinationInfo(listOf(12358390, 12358442, -2448072, 491)),
        dushanbeTj = DestinationInfo(listOf(-214626, -7328360, -11018181, 123589607)),
        addisAbabaEt = DestinationInfo(listOf(-192800, -1707699, -13422232, -11589495)),
        tbilisiGe = DestinationInfo(listOf(-28699, -1996871, -13438811, 123586302))
    )
}