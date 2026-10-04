package com.duoc.banco_legacy.customer;
import com.duoc.banco_legacy.customer.registry.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc
class CustomerRegistryIntegrationTests extends JwtTestSupport {
    private static final UUID ID=UUID.fromString("00000000-0000-0000-0000-000000000001");
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;
    @Autowired CustomerRegistryService registry;
    @MockBean AccountRegistryClient accounts;
    @BeforeEach void isolate(){jdbc.update("DELETE FROM eft_customer");}
    private void register(UUID id) throws Exception {
        mvc.perform(put("/api/customers/"+id).with(bearer("DOMAIN_OPERATOR","customers.write"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Titular de prueba\"}"))
                .andExpect(status().isCreated());
    }
    @Test void createsAndReplaysWithoutDeduplicatingPeopleByName() throws Exception {
        register(ID);
        mvc.perform(put("/api/customers/"+ID).with(bearer("DOMAIN_OPERATOR","customers.write"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Titular de prueba\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(0));
        register(UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM eft_customer",Integer.class)).isEqualTo(2);
        mvc.perform(put("/api/customers/"+ID).with(bearer("DOMAIN_OPERATOR","customers.write"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Otro nombre\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/customers/"+ID).with(bearer("DOMAIN_OPERATOR","customers.read")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Titular de prueba"))
                .andExpect(jsonPath("$.rut").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist());
    }
    @Test void maintenanceUsesVersionAndPersistsNameOnly() throws Exception {
        register(ID);
        mvc.perform(patch("/api/customers/"+ID).with(bearer("DOMAIN_OPERATOR","customers.write"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nombre actualizado\",\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(patch("/api/customers/"+ID).with(bearer("DOMAIN_OPERATOR","customers.write"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"No debe persistir\",\"version\":0}"))
                .andExpect(status().isConflict());
        assertThat(registry.get(ID).name()).isEqualTo("Nombre actualizado");
    }
    @Test void accountsAreReadFromActualRemoteContractAndTokenIsRelayed() throws Exception {
        register(ID);
        when(accounts.accounts(eq(ID),eq(1),eq(2),anyString())).thenReturn(
                List.of(new AccountRegistryClient.Account(905,"ahorro","OPEN",0,List.of(ID))));
        mvc.perform(get("/api/customers/"+ID+"/accounts").param("limit","1").param("offset","2")
                .with(bearer("DOMAIN_OPERATOR","customers.read accounts.read"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountId").value(905));
        verify(accounts).accounts(eq(ID),eq(1),eq(2),startsWith("Bearer "));
    }
    @Test void unknownCustomerAndRemoteFailureNeverProduceFakeEmptyList() throws Exception {
        mvc.perform(get("/api/customers/"+ID+"/accounts").with(bearer("DOMAIN_OPERATOR","customers.read accounts.read")))
                .andExpect(status().isNotFound());verifyNoInteractions(accounts);
        register(ID);
        when(accounts.accounts(any(),anyInt(),anyInt(),anyString())).thenThrow(
                new RegistryException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"ACCOUNT_UNAVAILABLE","Unavailable"));
        mvc.perform(get("/api/customers/"+ID+"/accounts").with(bearer("DOMAIN_OPERATOR","customers.read accounts.read")))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ACCOUNT_UNAVAILABLE"));
    }
    @Test void badRequestsAndMissingRecordsHaveExplicitCodes() throws Exception {
        for(String body:List.of("{}", "{\"name\":\"  \"}","{\"name\":\""+"x".repeat(121)+"\"}")) {
            mvc.perform(put("/api/customers/"+ID).with(bearer("DOMAIN_OPERATOR","customers.write"))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/customers/bad").with(bearer("DOMAIN_OPERATOR","customers.read"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/customers/"+ID).with(bearer("DOMAIN_OPERATOR","customers.read"))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/customers/"+ID).with(bearer("DOMAIN_OPERATOR","customers.write"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Valid\",\"version\":-1}"))
                .andExpect(status().isBadRequest());
    }
    @Test void channelRolesAndPartialScopesCannotAdministerOrTraverseRelations() throws Exception {
        mvc.perform(get("/api/customers/"+ID)).andExpect(status().isUnauthorized());
        for(String role:List.of("WEB","MOBILE","ATM"))
            mvc.perform(put("/api/customers/"+ID).with(bearer(role)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Valid\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/customers/"+ID+"/accounts").with(bearer("DOMAIN_OPERATOR","customers.read")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/customers/"+ID).with(bearer("DOMAIN_OPERATOR","customers.read"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Valid\",\"version\":0}"))
                .andExpect(status().isForbidden());
    }
    @Test void concurrentProfileUpdatesDoNotLoseChanges() throws Exception {
        register(ID);
        var gate=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results=new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for(int i=0;i<2;i++) {
                final String name="Concurrent-"+i;
                results.add(executor.submit(()->{
                    gate.await();
                    try {registry.update(ID,name,0);return true;}
                    catch(RegistryException conflict) {
                        assertThat(conflict.status()).isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
                        return false;
                    }
                }));
            }
            gate.countDown();
            int successes=0;
            for(var result:results) if(result.get(10,java.util.concurrent.TimeUnit.SECONDS)) successes++;
            assertThat(successes).isEqualTo(1);
        }
        assertThat(registry.get(ID).version()).isEqualTo(1);
    }
    @Test void schemaReapplicationPreservesData() throws Exception {
        register(ID);
        new ResourceDatabasePopulator(new ClassPathResource("schema-eft-customer.sql")).execute(jdbc.getDataSource());
        assertThat(registry.get(ID).name()).isEqualTo("Titular de prueba");
    }
}