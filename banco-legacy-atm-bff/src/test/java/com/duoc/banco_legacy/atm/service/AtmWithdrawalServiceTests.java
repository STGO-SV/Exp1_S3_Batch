package com.duoc.banco_legacy.atm.service;
import com.duoc.banco_legacy.core.service.LegacyAccountQueryService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class AtmWithdrawalServiceTests {
    final LegacyAccountQueryService accounts = mock(LegacyAccountQueryService.class);
    final AtmWithdrawalService service = new AtmWithdrawalService(accounts);
    @ParameterizedTest @NullSource @ValueSource(strings={"0","-1","0.001","100000000000000000"})
    void rejectsInvalidAmountsBeforeQuery(String amount) {
        assertThatThrownBy(() -> service.simulate(101,amount==null?null:new BigDecimal(amount)))
                .isInstanceOf(WithdrawalRejectedException.class);
        verifyNoInteractions(accounts);
    }
    @Test void repeatedWithdrawalsRemainSimulation() {
        when(accounts.getAvailableBalance(101)).thenReturn(new BigDecimal("1010"));
        var first = service.simulate(101,new BigDecimal("100"));
        var second = service.simulate(101,new BigDecimal("100"));
        assertThat(first.status()).isEqualTo("SIMULATED");
        assertThat(first.projectedBalance()).isEqualByComparingTo("910");
        assertThat(second).isEqualTo(first);
        verify(accounts,times(2)).getAvailableBalance(101);
        verifyNoMoreInteractions(accounts);
    }
}
