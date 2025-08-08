package by.northdakota.markettracker.Core.Repository;

import by.northdakota.markettracker.Core.Entity.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {
}
