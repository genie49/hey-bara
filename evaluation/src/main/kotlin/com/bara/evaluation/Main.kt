package com.bara.evaluation

import com.bara.evaluation.cli.buildCli
import com.github.ajalt.clikt.core.main

fun main(args: Array<String>) {
    buildCli().main(args)
}
