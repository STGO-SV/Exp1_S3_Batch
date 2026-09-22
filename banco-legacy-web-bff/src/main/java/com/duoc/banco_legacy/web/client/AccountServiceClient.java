package com.duoc.banco_legacy.web.client;

import com.duoc.banco_legacy.web.dto.WebAccountDashboard;

public interface AccountServiceClient {
    WebAccountDashboard getDashboard(long accountId, String authorization);
}
