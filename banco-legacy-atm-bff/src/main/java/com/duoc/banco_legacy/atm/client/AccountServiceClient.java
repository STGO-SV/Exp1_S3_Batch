package com.duoc.banco_legacy.atm.client;

import com.duoc.banco_legacy.atm.dto.AtmBalance;
import com.duoc.banco_legacy.atm.dto.AtmMovement;
import java.util.List;

public interface AccountServiceClient {
    AtmBalance getBalance(long accountId, String authorization);
    List<AtmMovement> getMovements(long accountId, String authorization);
}
