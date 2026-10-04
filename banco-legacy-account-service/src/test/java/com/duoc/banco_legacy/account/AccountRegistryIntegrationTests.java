package com.duoc.banco_legacy.account;

import com.duoc.banco_legacy.account.registry.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
        "spring.datasource.url=jdbc:h2:mem:eft_account_registry;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always","spring.sql.init.schema-locations=classpath:schema-eft-account.sql"
})
@AutoConfigureMockMvc
class AccountRegistryIntegrationTests extends JwtTestSupport {
    private static final String SCOPES="accounts.read accounts.write customers.read";
    private static final UUID CUSTOMER=UUID.fromString("00000000-0000-0000-0000-000000000001");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountRegistryService registry;
    @MockBean CustomerRegistryClient customers;
    @BeforeEach
    void isolate() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS interes_procesado (cuenta_id BIGINT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS movimiento_anual_procesado (cuenta_id BIGINT)");
        jdbc.update("DELETE FROM eft_financial_outbox");jdbc.update("DELETE FROM eft_account_posting");jdbc.update("DELETE FROM eft_account_balance");jdbc.update("DELETE FROM eft_account_holder");jdbc.update("DELETE FROM eft_account");
        jdbc.update("DELETE FROM interes_procesado");jdbc.update("DELETE FROM movimiento_anual_procesado");
    }
    private String opening(String type) {
        return "{\"accountType\":\""+type+"\",\"customerIds\":[\""+CUSTOMER+"\"]}";
    }
    private void openAccount(long id) throws Exception {
        mvc.perform(put("/api/accounts/"+id).with(bearer("DOMAIN_OPERATOR",SCOPES))
                .contentType(MediaType.APPLICATION_JSON).content(opening("ahorro"))).andExpect(status().isCreated());
    }
    @Test
    void openingPersistsExplicitHolderAndIdempotentPutDoesNotDuplicate() throws Exception {
        openAccount(901);
        mvc.perform(put("/api/accounts/901").with(bearer("DOMAIN_OPERATOR",SCOPES))
                .contentType(MediaType.APPLICATION_JSON).content(opening("ahorro"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_account",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_account_holder",Integer.class)).isEqualTo(1);
        verify(customers,times(1)).require(eq(CUSTOMER),startsWith("Bearer "));
        mvc.perform(get("/api/accounts/901").with(bearer("DOMAIN_OPERATOR","accounts.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.customerIds[0]").value(CUSTOMER.toString()))
                .andExpect(jsonPath("$.balance").value(0));
        mvc.perform(put("/api/accounts/901").with(bearer("DOMAIN_OPERATOR",SCOPES))
                .contentType(MediaType.APPLICATION_JSON).content(opening("prestamo")))
                .andExpect(status().isConflict());
    }
    @Test
    void maintenanceAndClosureUseVersionsAndClosureIsIdempotent() throws Exception {
        openAccount(902);
        mvc.perform(patch("/api/accounts/902").with(bearer("DOMAIN_OPERATOR","accounts.write"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"accountType\":\"prestamo\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(patch("/api/accounts/902").with(bearer("DOMAIN_OPERATOR","accounts.write"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"accountType\":\"ahorro\",\"version\":0}"))
                .andExpect(status().isConflict());
        for(int i=0;i<2;i++)
            mvc.perform(post("/api/accounts/902/closure").with(bearer("DOMAIN_OPERATOR","accounts.write"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"))
                    .andExpect(jsonPath("$.version").value(2));
        mvc.perform(patch("/api/accounts/902").with(bearer("DOMAIN_OPERATOR","accounts.write"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"accountType\":\"ahorro\",\"version\":2}"))
                .andExpect(status().isConflict());
    }
    @Test
    void missingOrUnavailableCustomerLeavesNoPartialAccount() throws Exception {
        doThrow(RegistryException.missing("CUSTOMER_NOT_FOUND")).when(customers).require(any(),anyString());
        mvc.perform(put("/api/accounts/903").with(bearer("DOMAIN_OPERATOR",SCOPES))
                .contentType(MediaType.APPLICATION_JSON).content(opening("ahorro"))).andExpect(status().isNotFound());
        doThrow(new RegistryException(HttpStatus.SERVICE_UNAVAILABLE,"CUSTOMER_UNAVAILABLE","Unavailable"))
                .when(customers).require(any(),anyString());
        mvc.perform(put("/api/accounts/903").with(bearer("DOMAIN_OPERATOR",SCOPES))
                .contentType(MediaType.APPLICATION_JSON).content(opening("ahorro"))).andExpect(status().isServiceUnavailable());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_account",Integer.class)).isZero();
    }
    @Test
    void historicalKeysCannotBeSilentlyOpenedAsNewMaster() throws Exception {
        jdbc.update("INSERT INTO interes_procesado(cuenta_id) VALUES(101)");
        mvc.perform(put("/api/accounts/101").with(bearer("DOMAIN_OPERATOR",SCOPES))
                .contentType(MediaType.APPLICATION_JSON).content(opening("ahorro")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LEGACY_MIGRATION_REQUIRED"));
        jdbc.update("INSERT INTO movimiento_anual_procesado(cuenta_id) VALUES(102)");
        mvc.perform(put("/api/accounts/102").with(bearer("DOMAIN_OPERATOR",SCOPES))
                .contentType(MediaType.APPLICATION_JSON).content(opening("ahorro"))).andExpect(status().isConflict());
    }
    @Test
    void rollsBackMasterAndPreviousHolderIfLaterPersistenceFails() {
        assertThatThrownBy(()->registry.open(904,"ahorro",Arrays.asList(CUSTOMER,null)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_account",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_account_holder",Integer.class)).isZero();
    }
    @Test
    void queryingExplicitRelationshipsSupportsPaginationAndSeveralHolders() throws Exception {
        UUID second=UUID.fromString("00000000-0000-0000-0000-000000000002");
        mvc.perform(put("/api/accounts/905").with(bearer("DOMAIN_OPERATOR",SCOPES))
                .contentType(MediaType.APPLICATION_JSON).content(
                        "{\"accountType\":\"ahorro\",\"customerIds\":[\""+CUSTOMER+"\",\""+second+"\"]}"))
                .andExpect(status().isCreated());
        openAccount(906);
        mvc.perform(get("/api/accounts").param("customerId",CUSTOMER.toString()).param("limit","1").param("offset","1")
                .with(bearer("DOMAIN_OPERATOR","accounts.read"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].accountId").value(906));
        mvc.perform(get("/api/accounts").param("customerId",second.toString()).with(bearer("DOMAIN_OPERATOR","accounts.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].accountId").value(905));
    }
    @Test
    void additiveSchemaCanBeReappliedWithoutDestroyingRecords() throws Exception {
        openAccount(907);
        new ResourceDatabasePopulator(new ClassPathResource("schema-eft-account.sql")).execute(jdbc.getDataSource());
        assertThat(registry.get(907).customerIds()).containsExactly(CUSTOMER);
    }
    @Test
    void rejectsInvalidRequestsAndUnknownAccounts() throws Exception {
        for(String body:new String[]{"{}", "{\"accountType\":\"hipoteca\",\"customerIds\":[\""+CUSTOMER+"\"]}",
                "{\"accountType\":\"ahorro\",\"customerIds\":[]}",
                "{\"accountType\":\"ahorro\",\"customerIds\":[null]}",
                "{\"accountType\":\"ahorro\",\"customerIds\":[\""+CUSTOMER+"\",\""+CUSTOMER+"\"]}"}) {
            mvc.perform(put("/api/accounts/908").with(bearer("DOMAIN_OPERATOR",SCOPES))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/accounts/999").with(bearer("DOMAIN_OPERATOR","accounts.read"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/accounts/0").with(bearer("DOMAIN_OPERATOR","accounts.read"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/accounts").param("customerId","bad").with(bearer("DOMAIN_OPERATOR","accounts.read")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/accounts").param("customerId",CUSTOMER.toString()).param("limit","0")
                .with(bearer("DOMAIN_OPERATOR","accounts.read"))).andExpect(status().isBadRequest());
    }
    @Test
    void concurrentMaintenanceAllowsOnlyOneWriterForTheSameVersion() throws Exception {
        openAccount(910);
        var gate = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i=0;i<2;i++) results.add(executor.submit(() -> {
                gate.await();
                try { registry.update(910,"prestamo",0); return true; }
                catch (RegistryException conflict) {
                    assertThat(conflict.status()).isEqualTo(HttpStatus.CONFLICT);
                    return false;
                }
            }));
            gate.countDown();
            int successes=0;
            for(var result:results) if(result.get(10,java.util.concurrent.TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(registry.get(910).version()).isEqualTo(1);
    }
    @Test
    void channelRolesDoNotGrantRegistryAdministration() throws Exception {
        mvc.perform(get("/api/accounts/999")).andExpect(status().isUnauthorized());
        for(String role:List.of("WEB","MOBILE","ATM")) {
            mvc.perform(get("/api/accounts/999").with(bearer(role))).andExpect(status().isForbidden());
            mvc.perform(put("/api/accounts/909").with(bearer(role)).contentType(MediaType.APPLICATION_JSON)
                    .content(opening("ahorro"))).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/accounts/909").with(bearer("DOMAIN_OPERATOR","accounts.write"))
                .contentType(MediaType.APPLICATION_JSON).content(opening("ahorro"))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/accounts/909").with(bearer("DOMAIN_OPERATOR","accounts.read"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"accountType\":\"ahorro\",\"version\":0}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(customers);
    }
}