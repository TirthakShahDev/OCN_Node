/*
    Copyright 2019 Share&Charge Foundation

    This file is part of Open Charging Network Node.

    Open Charging Network Node is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Open Charging Network Node is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Open Charging Network Node.  If not, see <https://www.gnu.org/licenses/>.
*/

package snc.openchargingnetwork.node.controllers.ocn

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import snc.openchargingnetwork.node.services.RequestHandler
import snc.openchargingnetwork.node.services.RequestHandlerBuilder
import snc.openchargingnetwork.node.models.ocpi.OcpiResponse


@RestController
@RequestMapping("/ocn/message")
class MessageController(private val requestHandlerBuilder: RequestHandlerBuilder) {


    @PostMapping
    fun postMessage(@RequestHeader("X-Request-ID") requestID: String,
                    @RequestHeader("OCN-Signature") signature: String,
                    @RequestBody body: String): ResponseEntity<OcpiResponse<Any>> {

        val request: RequestHandler<Any> = requestHandlerBuilder.build(body)

        return request
                .validateOcnMessage(signature)
                .forwardRequest()
                .getResponseWithAllHeaders()
    }

}