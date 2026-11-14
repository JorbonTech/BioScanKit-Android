package com.bioscankit.android.settings

import java.util.Locale

/**
 * 与 iOS `RelatedTaxon` 一致的产品级分类词表。
 *
 * `rawValue` 是 `RelatedRoutes.json` 的稳定键，改名即破坏路由文档。
 */
enum class RelatedTaxon(val rawValue: String) {
    Birds("birds"),
    Fish("fish"),
    Insects("insects"),
    Mammals("mammals"),
    Reptiles("reptiles"),
    Amphibians("amphibians"),
    Plants("plants"),
    Fungi("fungi"),
    Mineral("mineral"),

    /** 有分类层级但没命中任何生物类目：说明对象可能不是生物。 */
    Other("other"),

    /** 完全没有 taxonomy：宿主什么都没学到，任何路由都不应触发。 */
    Unclassified("unclassified"),
    ;

    companion object {
        private val byRawValue = entries.associateBy(RelatedTaxon::rawValue)

        fun fromRawValue(value: String): RelatedTaxon? = byRawValue[value.lowercase(Locale.ROOT)]

        /**
         * 解析 `kingdom > phylum > class > order > family > genus`。
         *
         * 证据缺失或冲突时返回 [Other]，而不是猜测，因为 `Other` 才是
         * “这可能不是生物”路由的触发条件。
         */
        fun classify(taxonomy: String): RelatedTaxon {
            val tokens = taxonomy
                .split(Regex("[>/|;,→\\n]"))
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it.isNotEmpty() }
                .toSet()
            if (tokens.isEmpty()) return Unclassified

            val matches = tokens.mapNotNull(markers::get).toMutableSet()
            if (tokens.any(fishOrders::contains)) matches.add(Fish)
            return if (matches.size == 1) matches.first() else Other
        }

        private val markers: Map<String, RelatedTaxon> = buildMap {
            fun add(taxon: RelatedTaxon, vararg names: String) {
                names.forEach { put(it, taxon) }
            }
            add(Birds, "aves", "birds", "鸟纲", "鸟类")
            add(
                Fish,
                "actinopterygii", "actinopteri", "chondrichthyes", "elasmobranchii",
                "holocephali", "myxini", "petromyzonti", "petromyzontida", "dipnoi",
                "coelacanthi", "鱼类", "辐鳍鱼纲", "软骨鱼纲",
            )
            add(Insects, "insecta", "昆虫纲", "昆虫")
            add(Mammals, "mammalia", "哺乳纲", "哺乳动物")
            add(
                Reptiles,
                "reptilia", "squamata", "testudines", "crocodylia", "crocodilia",
                "sphenodontia", "rhynchocephalia", "爬行纲", "爬行动物",
            )
            add(Amphibians, "amphibia", "两栖纲", "两栖动物")
            add(
                Plants,
                "plantae", "植物界", "植物", "magnoliopsida", "liliopsida",
                "polypodiopsida", "pinopsida", "bryopsida", "lycopodiopsida",
            )
            add(Fungi, "fungi", "真菌界", "真菌", "basidiomycota", "ascomycota")
            add(Mineral, "mineral", "minerals", "矿物", "矿物类")
        }

        // 这些目在随包数据库里可能缺失 class_name；不能只凭 Chordata 推断鱼类。
        private val fishOrders: Set<String> = setOf(
            "acipenseriformes", "albuliformes", "amiiformes", "anguilliformes", "atheriniformes",
            "aulopiformes", "batrachoidiformes", "beloniformes", "beryciformes", "characiformes",
            "clupeiformes", "cypriniformes", "cyprinodontiformes", "elopiformes", "esociformes",
            "gadiformes", "gasterosteiformes", "gobiesociformes", "gonorynchiformes", "gymnotiformes",
            "lepisosteiformes", "lophiiformes", "mugiliformes", "myctophiformes", "ophidiiformes",
            "osmeriformes", "osteoglossiformes", "perciformes", "percopsiformes", "pleuronectiformes",
            "polypteriformes", "salmoniformes", "scorpaeniformes", "siluriformes", "synbranchiformes",
            "syngnathiformes", "tetraodontiformes", "zeiformes",
        )
    }
}
