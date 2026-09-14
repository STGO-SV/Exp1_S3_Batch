package com.duoc.banco_legacy.core.service;
import com.duoc.banco_legacy.core.repository.LegacyAccountReadRepository;
import com.duoc.banco_legacy.core.model.AccountBalance;
import com.duoc.banco_legacy.core.exception.AccountNotFoundException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class LegacyAccountQueryServiceTests {
    final LegacyAccountReadRepository repository = mock(LegacyAccountReadRepository.class);
    final LegacyAccountQueryService service = new LegacyAccountQueryService(repository);
    @Test void dashboardReadsBalanceOnlyOnce() {
        when(repository.findLatestBalance(101)).thenReturn(Optional.of(
                new AccountBalance(101L,"Ana",BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.TEN,"ahorro")));
        service.getBalance(101);
        service.getMovementsForKnownAccount(101,20);
        service.getRecentAnomalies(10);
        verify(repository, times(1)).findLatestBalance(101);
        verify(repository).findRecentMovements(101,20);
        verify(repository).findRecentAnomalies(10);
        verifyNoMoreInteractions(repository);
    }
    @Test void missingAccountRejectsAllMovementProjectionsBeforeReading() {
        assertThatThrownBy(() -> service.getRecentMovements(999,20)).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> service.getCompactMovements(999,5)).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> service.getEssentialMovements(999,3)).isInstanceOf(AccountNotFoundException.class);
        verify(repository, times(3)).accountExists(999);
        verifyNoMoreInteractions(repository);
    }
    @Test void missingBalancesRemainNotFound() {
        assertThatThrownBy(() -> service.getBalance(999)).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> service.getSummary(999)).isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> service.getAvailableBalance(999)).isInstanceOf(AccountNotFoundException.class);
    }
    @Test void emptyMovementsOfExistingAccountAreValid() {
        when(repository.accountExists(101)).thenReturn(true);
        assertThat(service.getCompactMovements(101,5)).isEmpty();
        assertThat(service.getEssentialMovements(101,3)).isEmpty();
        verify(repository, never()).findLatestBalance(anyLong());
    }
}
