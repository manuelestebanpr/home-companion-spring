package com.homeserver.core.groceries.internal;

import com.homeserver.core.groceries.internal.dao.GroceryDao;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DedupCleanup {
  private final GroceryDao dao;

  public DedupCleanup(GroceryDao dao) {
    this.dao = dao;
  }

  @Scheduled(fixedDelay = 3600000)
  @Transactional
  public void expire() {
    dao.update("delete from idempotency_requests where expires_at <= CURRENT_TIMESTAMP");
  }
}
