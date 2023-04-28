package com.tans.tfiletransporter

object FileNameTest {

    @JvmStatic
    fun main(args: Array<String>) {
        val nameList = listOf<String>(
            "Hello World.text",
            "Hello World-100.text",
            "Hello World"
        )
        val nameIndexSuffixRegex = "((.|\\s)+)-(\\d+)(\\.(.|\\s)+)$".toRegex()
        val nameSuffix = "((.|\\s)+)(\\.(.|\\s)+)\$".toRegex()
        val nameIndex = "((.|\\s)+)-(\\d+)$".toRegex()
        for (n in nameList) {
            println("$n find result: ")
            when {
                nameIndexSuffixRegex.matches(n) -> {
                    val values = nameIndexSuffixRegex.find(n)?.groupValues ?: emptyList()
                    val name = values.getOrNull(1) ?: ""
                    val index = values.getOrNull(3) ?: ""
                    val suffix = values.getOrNull(4) ?: ""
                    println("name=$name, index=$index, suffix=$suffix")
                }
                nameSuffix.matches(n) -> {
                    val values = nameSuffix.find(n)?.groupValues ?: emptyList()
                    val name = values.getOrNull(1) ?: ""
                    val suffix = values.getOrNull(3) ?: ""
                    println("name=$name, suffix=$suffix")
                }
                nameIndex.matches(n) -> {
                    val values = nameIndexSuffixRegex.find(n)?.groupValues ?: emptyList()
                    val name = values.getOrNull(1) ?: ""
                    val index = values.getOrNull(2) ?: ""
                    println("name=$name, index=$index")
                }
                else -> {
                    println("name=$n")
                }
            }
            println("\n")
        }
    }
}