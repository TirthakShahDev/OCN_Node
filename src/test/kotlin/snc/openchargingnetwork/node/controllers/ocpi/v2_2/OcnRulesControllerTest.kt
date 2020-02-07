package snc.openchargingnetwork.node.controllers.ocpi.v2_2

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*


@WebMvcTest(OcnRulesController::class)
class OcnRulesControllerTest(@Autowired val mockMvc: MockMvc) {

    @Test
    fun getRules() {
        mockMvc.perform(get("/ocpi/receiver/2.2/ocnrules")
                .header("authorization", "Token token-c"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("\$.status_code").value(1000))
                .andExpect(jsonPath("\$.status_message").doesNotExist())
                .andExpect(jsonPath("\$.data.signatures").value(false))
                .andExpect(jsonPath("\$.data.whitelist.active").value(false))
                .andExpect(jsonPath("\$.data.blacklist.active").value(false))
                .andExpect(jsonPath("\$.timestamp").isString)
    }

}