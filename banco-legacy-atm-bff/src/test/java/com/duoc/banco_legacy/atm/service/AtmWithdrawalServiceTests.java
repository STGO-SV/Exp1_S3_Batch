package com.duoc.banco_legacy.atm.service;

import com.duoc.banco_legacy.core.exception.AccountNotFoundException;
import com.duoc.banco_legacy.core.model.LockedAccountBalance;
import com.duoc.banco_legacy.core.repository.LegacyAccountWithdrawalRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AtmWithdrawalServiceTests {
    final LegacyAccountWithdrawalRepository repository = mock(LegacyAccountWithdrawalRepository.class);
    final AtmWithdrawalService service = new AtmWithdrawalService(repository);

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "0.001", "100000000000000000"})
    void rejectsInvalidAmountsBeforeQuery(String amount) {
        assertThatThrownBy(() -> service.withdraw(101, amount == null ? null : new BigDecimal(amount)))
                .isInstanceOf(WithdrawalRejectedException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void completesWithdrawalAndPersistsInOrder() {
        when(repository.lockLatestBalance(101))
                .thenReturn(Optional.of(new LockedAccountBalance(7, new BigDecimal("1010"))));

        var response = service.withdraw(101, new BigDecimal("100"));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.balanceBefore()).isEqualByComparingTo("1010");
        assertThat(response.balanceAfter()).isEqualByComparingTo("910");
        var ordered = inOrder(repository);
        ordered.verify(repository).lockLatestBalance(101);
        ordered.verify(repository).updateBalance(7, new BigDecimal("910"));
        ordered.verify(repository).insertWithdrawalMovement(101, new BigDecimal("100"));
    }

    @Test
    void rejectsInsufficientFundsWithoutWriting() {
        when(repository.lockLatestBalance(101))
                .thenReturn(Optional.of(new LockedAccountBalance(7, new BigDecimal("50"))));

        assertThatThrownBy(() -> service.withdraw(101, new BigDecimal("100")))
                .isInstanceOf(WithdrawalRejectedException.class)
                .hasMessageContaining("Saldo insuficiente");

        var ordered = inOrder(repository);
        ordered.verify(repository).lockLatestBalance(101);
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void missingAccountIsReported() {
        when(repository.lockLatestBalance(999)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.withdraw(999, BigDecimal.ONE))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void updateFailureDoesNotReturnSuccess() {
        when(repository.lockLatestBalance(101))
                .thenReturn(Optional.of(new LockedAccountBalance(7, new BigDecimal("1010"))));
        doThrow(new DataAccessResourceFailureException("update failed"))
                .when(repository).updateBalance(7, new BigDecimal("910"));

        assertThatThrownBy(() -> service.withdraw(101, new BigDecimal("100")))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    void insertFailureDoesNotReturnSuccess() {
        when(repository.lockLatestBalance(101))
                .thenReturn(Optional.of(new LockedAccountBalance(7, new BigDecimal("1010"))));
        doThrow(new DataAccessResourceFailureException("insert failed"))
                .when(repository).insertWithdrawalMovement(101, new BigDecimal("100"));

        assertThatThrownBy(() -> service.withdraw(101, new BigDecimal("100")))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }
}
