/*
    Copyright 2019-2020 eMobilify GmbH

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package snc.openchargingnetwork.node.config

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.web3j.crypto.Credentials

@Component
class NodeInfoLogger(private val properties: NodeProperties) {

    @EventListener(ApplicationReadyEvent::class)
    fun log() {
        val borderLength = calculateBorderLength(properties.url.length, properties.apikey.length)
        val border = "=".repeat(borderLength)
        val address = if (properties.privateKey != null) {
            Credentials.create(properties.privateKey).address
        } else {
            if (properties.dev) {
                "0x9bC1169Ca09555bf2721A5C9eC6D69c8073bfeB4"
            } else {
                ""
            }
        }
        println("\n$border\n" +
                "DEV        | ${properties.dev}\n" +
                "URL        | ${properties.url}\n" +
                "ADDRESS    | $address\n" +
                "APIKEY     | ${properties.apikey}\n" +
                "SIGNATURES | ${properties.signatures}" +
                "\n$border\n")
    }

    private fun calculateBorderLength(url: Int, apikey: Int): Int {
        val baseLength = 13
        val address = 42
        return baseLength + when {
            url >= apikey && url >= address -> url
            apikey >= url && apikey >= address -> apikey
            address >= url && address >= apikey -> address
            else -> 50
        }
    }

}