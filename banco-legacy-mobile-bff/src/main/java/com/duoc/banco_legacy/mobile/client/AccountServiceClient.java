package com.duoc.banco_legacy.mobile.client;

import com.duoc.banco_legacy.mobile.dto.MobileAccountSummary;
import com.duoc.banco_legacy.mobile.dto.MobileMovement;
import java.util.List;

public interface AccountServiceClient {
    MobileAccountSummary getSummary(long accountId, String authorization);
    List<MobileMovement> getMovements(long accountId, String authorization);
}
